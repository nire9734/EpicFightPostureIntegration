package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.neoforge.NeoForgePostureConfig;
import dev.nire9734.epicfightposture.neoforge.NeoForgePostureCoverage;
import dev.nire9734.epicfightposture.neoforge.NeoForgeServerSettings;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeLightweightSpiderPatch;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeSpiderExecutionSupport;
import dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.shelmarow.combat_evolution.ai.CEHumanoidPatch;
import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import net.shelmarow.combat_evolution.api.event.OnExecutionStartEvent;
import net.shelmarow.combat_evolution.execution.ExecutionHandler;
import org.slf4j.Logger;
import yesman.epicfight.skill.guard.GuardSkill;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

/**
 * NeoForge 1.21.1 adapter for the shared {@link PostureEngine}. The engine owns
 * every Posture state transition; this class supplies NeoForge, Epic Fight and
 * Combat Evolution access, and reads direct-hit damage from NeoForge's
 * post-damage event.
 */
public final class NeoForgePostureServerEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PostureEngine<LivingEntity> ENGINE = new PostureEngine<>(new NeoForgeHost());
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        // Every server-side hook runs behind HookGuard: a failure skips Posture
        // for that event instead of crashing the server tick.
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class,
                event -> HookGuard.run("damage", () -> onDamage(event)));
        NeoForge.EVENT_BUS.addListener(EntityTickEvent.Post.class,
                event -> HookGuard.run("tick", () -> onTick(event)));
        NeoForge.EVENT_BUS.addListener(OnExecutionStartEvent.class,
                event -> HookGuard.run("execution start", () -> onExecutionStart(event)));
        NeoForgePostureSync.register();
        NeoForgePostureCoverage.register();
    }

    /** NeoForge fires Post only after applying health loss, so the event amount is the real loss. */
    private static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity mob = event.getEntity();
        if (mob == null || !(mob.level() instanceof ServerLevel)) return;
        Entity offender = GuardSkill.getOffender(event.getSource());
        if (!(offender instanceof Player) || offender == mob) return;
        float loss = event.getNewDamage();
        if (!Float.isFinite(loss) || loss <= 0.0F) return;
        float impact = NeoForgeCompatibilityDamage.calculateBaseImpact(event.getSource(), loss);
        if (!Float.isFinite(impact) || impact <= 0.0F) return;

        float maxHealth = mob.getMaxHealth();
        float before = Math.min(maxHealth, mob.getHealth() + loss);
        ENGINE.applyAttack(mob, before, maxHealth, loss, impact);
    }

    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity mob) || !(mob.level() instanceof ServerLevel)) return;
        if (ENGINE.tick(mob)) {
            NeoForgePostureSync.syncPeriodic(mob, stateForSync(mob));
        }
    }

    /** Called exactly once by the Epic Fight parry event's setParried(true) hook. */
    public static void onParried(LivingEntity attacker, DamageSource source) {
        ENGINE.onParried(attacker, isProjectile(source));
    }

    /** Successful normal guard; caller already excluded parry and stamina guard-break. */
    public static void onGuarded(LivingEntity attacker, DamageSource source) {
        ENGINE.onGuarded(attacker, isProjectile(source));
    }

    /** Vanilla and modded ranged attacks: the projectile tag, or a projectile direct entity. */
    private static boolean isProjectile(DamageSource source) {
        return source != null && (source.is(DamageTypeTags.IS_PROJECTILE)
                || source.getDirectEntity() instanceof Projectile);
    }

    private static void onExecutionStart(OnExecutionStartEvent event) {
        if (event.isCanceled()) return;
        if (event.getTarget().getOriginal() instanceof LivingEntity mob) {
            ENGINE.onExecutionStart(mob);
        }
    }

    /** Current snapshot, or null for blacklisted mobs and mobs without a state. */
    public static PostureEngine.SyncState stateForSync(LivingEntity mob) {
        return ENGINE.snapshot(mob, false);
    }

    /**
     * Snapshot for a player that starts tracking the mob: its current state, or
     * a full bar for an eligible mob that has not been hit yet.
     */
    public static PostureEngine.SyncState stateForTracking(LivingEntity mob) {
        return ENGINE.snapshot(mob, true);
    }

    /** Clear per-mob posture state when the entity leaves the server level. */
    public static void forget(LivingEntity mob) {
        ENGINE.forget(mob, false);
    }

    private static boolean canReceive(LivingEntity mob) {
        return PostureDeathSafety.canReceivePosture(mob.isAlive(), mob.getHealth());
    }

    private static ResourceLocation entityTypeId(LivingEntity mob) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
    }

    private static LivingEntityPatch<?> getPatch(LivingEntity mob) {
        try {
            return EpicFightCapabilities.getEntityPatch(mob, LivingEntityPatch.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasDistinctNeutralize(LivingEntityPatch<?> patch) {
        if (patch == null) return false;
        NeoForgeMissingHumanoidNeutralize.installIfNeeded(patch);
        return PostureExecutionEligibility.hasDistinctNeutralize(patch);
    }

    private static boolean isActiveNeutralize(LivingEntityPatch<?> patch) {
        if (!hasDistinctNeutralize(patch)) return false;
        try {
            var current = patch.getAnimator().getPlayerFor(null);
            return current != null && current.getRealAnimation() == patch.getHitAnimation(StunType.NEUTRALIZE);
        } catch (Throwable exception) {
            return false;
        }
    }

    private NeoForgePostureServerEvents() {}

    /** NeoForge 1.21.1, Epic Fight and Combat Evolution access for the shared engine. */
    private static final class NeoForgeHost implements PostureHost<LivingEntity> {
        @Override
        public boolean isServerEntity(LivingEntity mob) {
            return mob.level() instanceof ServerLevel;
        }

        @Override
        public boolean isAlive(LivingEntity mob) {
            return mob.isAlive();
        }

        @Override
        public float health(LivingEntity mob) {
            return mob.getHealth();
        }

        @Override
        public int tickCount(LivingEntity mob) {
            return mob.tickCount;
        }

        @Override
        public int entityId(LivingEntity mob) {
            return mob.getId();
        }

        @Override
        public boolean isBlacklisted(LivingEntity mob) {
            return NeoForgeServerSettings.localSnapshot().blocksPosture(
                    entityTypeId(mob), NeoForgeAutoCompatibility.isOwnedByEfpi(mob.getType()));
        }

        /** Delegate animation, armature and Animator checks to the shared policy. */
        @Override
        public boolean isEligible(LivingEntity mob) {
            if (mob instanceof Player) return false;
            LivingEntityPatch<?> patch = getPatch(mob);
            PostureExecutionEligibility.Reason exclusion = null;
            if (!canReceive(mob)) {
                exclusion = PostureExecutionEligibility.Reason.TARGET_NOT_ALIVE;
            } else if (isBlacklisted(mob)) {
                exclusion = PostureExecutionEligibility.Reason.ENTITY_BLACKLISTED;
            } else if (patch instanceof CEHumanoidPatch) {
                exclusion = PostureExecutionEligibility.Reason.CE_OWNED_PATCH;
            }
            try {
                if (exclusion == null && patch != null) {
                    NeoForgeMissingHumanoidNeutralize.installIfNeeded(patch);
                }
                boolean spiderCompatible = !(patch instanceof NeoForgeLightweightSpiderPatch<?>)
                        || NeoForgeSpiderExecutionSupport.isCompatible(patch);
                PostureExecutionEligibility.Result result = PostureExecutionEligibility.evaluate(
                        patch, patch != null && !patch.uninitialized(),
                        patch instanceof CustomExecuteEntity, exclusion, spiderCompatible);
                try {
                    NeoForgePostureCoverage.observe(mob, patch, result);
                } catch (RuntimeException | LinkageError ignored) {
                    // Diagnostics must not change gameplay eligibility.
                }
                return result.supported();
            } catch (Throwable ignored) {
                try {
                    NeoForgePostureCoverage.observeEligibilityFailure(mob, patch);
                } catch (Throwable diagnosticFailure) {
                    // Never let report failures change combat behavior.
                }
                return false;
            }
        }

        @Override
        public boolean isExecuting(LivingEntity mob) {
            try {
                return ExecutionHandler.isExecutingTarget(mob, mob);
            } catch (Throwable ignored) {
                return false;
            }
        }

        @Override
        public PostureInputs liveInputs(LivingEntity mob) {
            return NeoForgeEntityAttributeAdapter.liveInputs(mob);
        }

        @Override
        @SuppressWarnings("unchecked")
        public float innateEffectiveHealth(LivingEntity mob) {
            AttributeSupplier defaults = null;
            try {
                EntityType<? extends LivingEntity> type = (EntityType<? extends LivingEntity>) mob.getType();
                if (DefaultAttributes.hasSupplier(type)) defaults = DefaultAttributes.getSupplier(type);
            } catch (RuntimeException ignored) {
                // Some modded entity registrations have no usable default attributes.
            }
            float hp = baseline(mob, defaults, Attributes.MAX_HEALTH, mob.getMaxHealth());
            float armor = baseline(mob, defaults, Attributes.ARMOR, 0.0F);
            float toughness = baseline(mob, defaults, Attributes.ARMOR_TOUGHNESS, 0.0F);
            return PostureBalance.referenceEffectiveHealth(hp, armor, toughness);
        }

        @Override
        public void stopUsingItem(LivingEntity mob) {
            if (mob.isUsingItem()) mob.stopUsingItem();
        }

        @Override
        public NeutralizeResult applyNeutralize(LivingEntity mob) {
            LivingEntityPatch<?> patch = getPatch(mob);
            if (patch == null) return NeutralizeResult.failure("PATCH_MISSING");
            if (!hasDistinctNeutralize(patch)) return NeutralizeResult.failure("NEUTRALIZE_MISSING");
            try {
                boolean applied = patch.applyStun(StunType.NEUTRALIZE, 0.0F);
                if (applied && isActiveNeutralize(patch)) return NeutralizeResult.success();
                return NeutralizeResult.failure(applied ? "ANIMATION_NOT_ACTIVE" : "STUN_NOT_APPLIED");
            } catch (Throwable failure) {
                LOGGER.warn("EFPI neutralize threw for {} ({}) using patch {}",
                        entityTypeId(mob), mob.getUUID(), patch.getClass().getName(), failure);
                return NeutralizeResult.failure("STUN_EXCEPTION:" + failure.getClass().getSimpleName());
            }
        }

        @Override
        public boolean isNeutralizeActive(LivingEntity mob) {
            return isActiveNeutralize(getPatch(mob));
        }

        @Override
        public void syncNow(LivingEntity mob) {
            NeoForgePostureSync.syncNow(mob, stateForSync(mob));
        }

        @Override
        public void syncRemove(LivingEntity mob) {
            NeoForgePostureSync.removeNow(mob);
        }

        @Override
        public void observeExecution(LivingEntity mob, String stage) {
            NeoForgePostureCoverage.observeExecution(mob, stage);
        }

        @Override
        public void observeGuardBreak(LivingEntity mob, boolean active, String failureReason) {
            NeoForgePostureCoverage.observeGuardBreak(mob, active, failureReason);
        }

        @Override
        public PostureSettings settings() {
            return new PostureSettings(
                    NeoForgePostureConfig.PARRY_BASE_MAX_PERCENT.get().floatValue() / 100.0F,
                    NeoForgePostureConfig.PARRY_BASE_MIN_PERCENT.get().floatValue() / 100.0F,
                    NeoForgePostureConfig.POSTURE_BREAK_RESISTANCE_STEP.get().floatValue(),
                    NeoForgePostureConfig.POSTURE_BREAK_RESISTANCE_MAX.get().floatValue(),
                    PostureBalance.regenDelayTicks(NeoForgePostureConfig.REGEN_DELAY_SECONDS.get().floatValue()),
                    NeoForgePostureConfig.REGEN_PERCENT_PER_SECOND.get().floatValue() / 100.0F,
                    NeoForgePostureConfig.GUARD_POSTURE_DAMAGE_ENABLED.get(),
                    NeoForgePostureConfig.GUARD_POSTURE_DAMAGE_MULTIPLIER.get().floatValue(),
                    NeoForgePostureConfig.PROJECTILE_DEFENSE_POSTURE_MULTIPLIER.get().floatValue());
        }

        @Override
        public float attackCoefficient(LivingEntity mob) {
            return PostureTuning.BASE_ATTACK_POSTURE_COEFFICIENT
                    * NeoForgeServerSettings.attackPostureDamageMultiplier(entityTypeId(mob));
        }

        @Override
        public float parryMultiplier(LivingEntity mob) {
            return NeoForgeServerSettings.parryPostureDamageMultiplier(entityTypeId(mob));
        }

        private static float baseline(LivingEntity mob, AttributeSupplier defaults,
                                      Holder<Attribute> attribute, float fallback) {
            AttributeInstance current = mob.getAttribute(attribute);
            float originalBase = current == null ? Float.NaN : (float) current.getBaseValue();
            float typeDefault = defaults != null && defaults.hasAttribute(attribute)
                    ? (float) defaults.getBaseValue(attribute) : Float.NaN;
            return PostureBalance.innateAttributeValue(typeDefault, originalBase, fallback);
        }
    }
}
