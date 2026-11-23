package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.Map;
import java.util.WeakHashMap;
import dev.nire9734.epicfightposture.neoforge.NeoForgePostureConfig;
import dev.nire9734.epicfightposture.neoforge.NeoForgePostureCoverage;
import dev.nire9734.epicfightposture.neoforge.NeoForgeServerSettings;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeLightweightSpiderPatch;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeSpiderExecutionSupport;
import dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;
import net.shelmarow.combat_evolution.ai.CEHumanoidPatch;
import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import net.shelmarow.combat_evolution.api.event.OnExecutionStartEvent;
import net.shelmarow.combat_evolution.execution.ExecutionHandler;
import yesman.epicfight.skill.guard.GuardSkill;

/** NeoForge server adapter for the shared posture balance and runtime state. */
public final class NeoForgePostureServerEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RECOVERY_TICKS = PostureTuning.BREAK_RECOVERY_TICKS;
    private static final Map<LivingEntity, PostureRuntimeState> STATES = new WeakHashMap<>();
    private static final Map<LivingEntity, Integer> PENDING_EXECUTION_START = new WeakHashMap<>();
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(NeoForgePostureServerEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(NeoForgePostureServerEvents::onTick);
        NeoForge.EVENT_BUS.addListener(NeoForgePostureServerEvents::onExecutionStart);
        NeoForgePostureSync.register();
        NeoForgePostureCoverage.register();
    }

    // NeoForge fires Post only after applying health loss.
    private static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity().level() instanceof ServerLevel)) return;
        LivingEntity mob = event.getEntity();
        if (mob == null
                || !canReceive(mob) || isBlacklisted(mob)) return;
        Entity offender = GuardSkill.getOffender(event.getSource());
        if (!(offender instanceof Player) || offender == mob) return;
        float loss = event.getNewDamage();
        if (!Float.isFinite(loss) || loss <= 0.0F) return;

        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, LivingEntityPatch.class);
        if (!isEligiblePostureEntity(mob, patch)) return;
        float impact = NeoForgeCompatibilityDamage.calculateBaseImpact(event.getSource(), loss);
        if (!Float.isFinite(impact) || impact <= 0.0F) return;
        PostureRuntimeState state = STATES.computeIfAbsent(mob,
                target -> new PostureRuntimeState(liveInputs(target)));
        if (!state.acceptsPostureDamage()) return;

        float maxHealth = mob.getMaxHealth();
        float before = Math.min(maxHealth, mob.getHealth() + loss);
        float pressure = PostureBalance.attackPostureDamage(
                liveInputs(mob).basePosture(), maxHealth, before, loss, impact,
                PostureTuning.BASE_ATTACK_POSTURE_COEFFICIENT * NeoForgeServerSettings.attackPostureDamageMultiplier(
                        BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType())));
        if (!Float.isFinite(pressure) || pressure <= 0.0F) return;

        float resistance = PostureBalance.breakResistanceMultiplier(
                state.completedBreaks,
                NeoForgePostureConfig.POSTURE_BREAK_RESISTANCE_STEP.get().floatValue(),
                NeoForgePostureConfig.POSTURE_BREAK_RESISTANCE_MAX.get().floatValue());
        int delay = PostureBalance.regenDelayTicks(
                NeoForgePostureConfig.REGEN_DELAY_SECONDS.get().floatValue());
        float previousPosture = state.current;
        if (state.applyDamage(pressure, resistance, delay)) {
            breakPosture(mob, patch, state, delay);
        }
        if (!(state.current < previousPosture)) return;
        NeoForgePostureSync.syncNow(mob, stateForSync(mob));
    }

    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity mob) || !(mob.level() instanceof ServerLevel)) return;
        if (!canReceive(mob)) {
            // As in Forge 3.0.2, retain an active CE execution during death,
            // but do not keep an orphaned posture HUD state after ordinary death.
            if (PostureDeathSafety.shouldForgetDeadState(
                    mob.isAlive(), mob.getHealth(), isExecuting(mob))) {
                if (STATES.remove(mob) != null) {
                    NeoForgePostureSync.removeNow(mob);
                }
                PENDING_EXECUTION_START.remove(mob);
            }
            return;
        }
        PostureRuntimeState state = STATES.get(mob);
        if (state == null) return;
        if (isBlacklisted(mob)) {
            STATES.remove(mob);
            PENDING_EXECUTION_START.remove(mob);
            NeoForgePostureSync.removeNow(mob);
            return;
        }
        // Distribute the ten-tick refresh across entity IDs, and recalculate
        // only when the attribute inputs changed, as in Forge.
        if (Math.floorMod(mob.tickCount + mob.getId(),
                PostureTuning.MAX_POSTURE_REFRESH_INTERVAL_TICKS) == 0) {
            state.refreshMaximum(liveInputs(mob));
        }
        if (state.postureState == PostureState.BROKEN || state.postureState == PostureState.EXECUTING) {
            if (isExecuting(mob)) {
                boolean pendingExecution = PENDING_EXECUTION_START.remove(mob) != null;
                if (pendingExecution || state.postureState != PostureState.EXECUTING) {
                    NeoForgePostureCoverage.observeExecution(mob, "CONFIRMED");
                }
                if (state.postureState != PostureState.EXECUTING) {
                    state.enterExecution();
                    NeoForgePostureSync.syncNow(mob, stateForSync(mob));
                } else {
                    state.current = 0.0F;
                }
                return;
            }
            if (state.postureState == PostureState.EXECUTING) {
                Integer activationDeadline = PENDING_EXECUTION_START.get(mob);
                if (PostureRuntimeState.waitingForExecutionTask(mob.tickCount, activationDeadline)) {
                    // OnExecutionStartEvent precedes ExecutionTask.onStart().
                    // Do not return to the vanilla renderer during this gap.
                    return;
                }
                if (PENDING_EXECUTION_START.remove(mob) != null) {
                    NeoForgePostureCoverage.observeExecution(mob, "TIMEOUT");
                }
                state.beginRecovery(RECOVERY_TICKS);
                NeoForgePostureSync.syncNow(mob, stateForSync(mob));
                return;
            }
            LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, LivingEntityPatch.class);
            if (isActiveNeutralize(patch)) {
                state.brokenTicks++;
            } else {
                state.beginRecovery(RECOVERY_TICKS);
                NeoForgePostureSync.syncNow(mob, stateForSync(mob));
                return;
            }
        } else if (state.postureState == PostureState.RECOVERING) {
            if (state.advanceRecovery(RECOVERY_TICKS)) {
                NeoForgePostureSync.syncNow(mob, stateForSync(mob));
                return;
            }
        } else if (state.postureState == PostureState.NORMAL) {
            state.advanceRegeneration(NeoForgePostureConfig.REGEN_PERCENT_PER_SECOND.get().floatValue() / 100.0F);
            if (state.isDisposable()) {
                // As in Forge: send the final full snapshot, then stop ticking.
                // The client keeps the full bar; the next hit recreates a fresh state.
                NeoForgePostureSync.syncNow(mob, stateForSync(mob));
                STATES.remove(mob);
                return;
            }
        }
        NeoForgePostureSync.syncPeriodic(mob, stateForSync(mob));
    }


    /** Called exactly once by the Epic Fight parry event's setParried(true) hook. */
    public static void onParried(LivingEntity attacker, DamageSource source) {
        applyDefensivePostureDamage(attacker, defenseMultiplier(1.0F, source));
    }

    /** Successful normal guard; caller already excluded parry and stamina guard-break. */
    public static void onGuarded(LivingEntity attacker, DamageSource source) {
        if (!NeoForgePostureConfig.GUARD_POSTURE_DAMAGE_ENABLED.get()) return;
        applyDefensivePostureDamage(attacker, defenseMultiplier(
                NeoForgePostureConfig.GUARD_POSTURE_DAMAGE_MULTIPLIER.get().floatValue(), source));
    }

    private static float defenseMultiplier(float multiplier, DamageSource source) {
        return GuardPosturePolicy.defenseMultiplier(multiplier, isProjectile(source),
                NeoForgePostureConfig.PROJECTILE_DEFENSE_POSTURE_MULTIPLIER.get().floatValue());
    }

    /** Vanilla and modded ranged attacks: the projectile tag, or a projectile direct entity. */
    private static boolean isProjectile(DamageSource source) {
        return source != null && (source.is(DamageTypeTags.IS_PROJECTILE)
                || source.getDirectEntity() instanceof Projectile);
    }

    private static void applyDefensivePostureDamage(LivingEntity attacker, float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier <= 0.0F) return;
        if (attacker == null || !(attacker.level() instanceof ServerLevel)
                || attacker instanceof Player
                || !canReceive(attacker) || isBlacklisted(attacker)) return;
        LivingEntity mob = attacker;
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, LivingEntityPatch.class);
        if (!isEligiblePostureEntity(mob, patch)) return;

        PostureRuntimeState state = STATES.computeIfAbsent(mob,
                target -> new PostureRuntimeState(liveInputs(target)));
        if (!state.acceptsPostureDamage()) return;
        float innateEhp = innateEhp(mob);
        float damage = PostureBalance.parryDamage(state.max,
                NeoForgePostureConfig.PARRY_BASE_MAX_PERCENT.get().floatValue() / 100.0F,
                NeoForgePostureConfig.PARRY_BASE_MIN_PERCENT.get().floatValue() / 100.0F,
                innateEhp, PostureTuning.EHP_REFERENCE_HEALTH, (float) PostureTuning.EHP_EXPONENT);
        damage *= NeoForgeServerSettings.parryPostureDamageMultiplier(
                BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
        damage = PostureBalance.guardDamage(damage, multiplier);
        float resistance = PostureBalance.breakResistanceMultiplier(
                state.completedBreaks,
                NeoForgePostureConfig.POSTURE_BREAK_RESISTANCE_STEP.get().floatValue(),
                NeoForgePostureConfig.POSTURE_BREAK_RESISTANCE_MAX.get().floatValue());
        int delay = PostureBalance.regenDelayTicks(
                NeoForgePostureConfig.REGEN_DELAY_SECONDS.get().floatValue());
        float previousPosture = state.current;
        if (state.applyDamage(damage, resistance, delay)) {
            breakPosture(mob, patch, state, delay);
        }
        if (!(state.current < previousPosture)) return;
        NeoForgePostureSync.syncNow(mob, stateForSync(mob));
    }

    /**
     * Keep the guard-break transition aligned with Forge 3.0.2.
     * An interrupted attack must release its currently used item before EF's stun.
     */
    private static void breakPosture(LivingEntity mob, LivingEntityPatch<?> patch,
                                     PostureRuntimeState state, int regenDelayTicks) {
        if (!canReceive(mob) || !state.acceptsPostureDamage()) return;

        state.prepareBreak(regenDelayTicks);
        if (mob.isUsingItem()) mob.stopUsingItem();

        boolean active = false;
        String failureReason = patch == null ? "PATCH_MISSING" : "NEUTRALIZE_MISSING";
        if (patch != null && hasDistinctNeutralize(patch)) {
            try {
                boolean applied = patch.applyStun(StunType.NEUTRALIZE, 0.0F);
                active = applied && isActiveNeutralize(patch);
                if (!active) failureReason = applied ? "ANIMATION_NOT_ACTIVE" : "STUN_NOT_APPLIED";
            } catch (Throwable failure) {
                failureReason = "STUN_EXCEPTION:" + failure.getClass().getSimpleName();
                LOGGER.warn("EFPI neutralize threw for {} ({}) using patch {}",
                        BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()),
                        mob.getUUID(), patch.getClass().getName(), failure);
            }
        }

        NeoForgePostureCoverage.observeGuardBreak(mob, active, active ? "none" : failureReason);

        if (active) state.confirmBreak();
        else state.beginRecovery(RECOVERY_TICKS);
    }

    private static void onExecutionStart(OnExecutionStartEvent event) {
        Object original = event.getTarget().getOriginal();
        if (!(original instanceof LivingEntity mob) || !(mob.level() instanceof ServerLevel)
                || event.isCanceled() || !canReceive(mob) || isBlacklisted(mob)) return;

        LivingEntityPatch<?> patch = event.getTarget();
        if (!isEligiblePostureEntity(mob, patch)) return;

        // Assassinations start on a healthy, never-hit mob. The old get() gate
        // skipped those targets entirely, so no EXECUTING snapshot reached
        // the client and the special renderer stayed disabled.
        PostureRuntimeState state = STATES.computeIfAbsent(mob,
                target -> new PostureRuntimeState(liveInputs(target)));
        mob.stopUsingItem();
        state.startExecution();
        NeoForgePostureCoverage.observeExecution(mob, "STARTED");
        PENDING_EXECUTION_START.put(mob,
                mob.tickCount + PostureRuntimeState.EXECUTION_START_GRACE_TICKS);
        NeoForgePostureSync.syncNow(mob, stateForSync(mob));
    }

    @SuppressWarnings("unchecked")
    private static float innateEhp(LivingEntity mob) {
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

    private static float baseline(LivingEntity mob, AttributeSupplier defaults,
                                  net.minecraft.core.Holder<Attribute> attribute, float fallback) {
        AttributeInstance current = mob.getAttribute(attribute);
        float originalBase = current == null ? Float.NaN : (float) current.getBaseValue();
        float typeDefault = defaults != null && defaults.hasAttribute(attribute)
                ? (float) defaults.getBaseValue(attribute) : Float.NaN;
        return PostureBalance.innateAttributeValue(typeDefault, originalBase, fallback);
    }

    /** Used by StartTracking to send the authoritative state to new viewers. */
    public record SyncState(float current, float max, boolean broken) {}

    public static SyncState stateForSync(LivingEntity mob) {
        if (isBlacklisted(mob)) return null;
        PostureRuntimeState state = STATES.get(mob);
        return state == null ? null : new SyncState(state.current, state.max, state.isBrokenForHud());
    }

    /**
     * Give newly tracking clients a full posture bar before the first hit.
     * Do not add the pristine mob to STATES: passive mobs should not acquire
     * per-tick posture updates merely because their HUD is visible.
     */
    public static SyncState stateForTracking(LivingEntity mob) {
        if (!(mob.level() instanceof ServerLevel)) {
            return null;
        }
        // An existing state is sent even during death, as in Forge: a Combat
        // Evolution execution may outlive the killing blow, and a viewer that
        // starts tracking mid-execution still needs the EXECUTING snapshot.
        SyncState existing = stateForSync(mob);
        if (existing != null) return existing;
        if (!canReceive(mob) || isBlacklisted(mob)) return null;

        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, LivingEntityPatch.class);
        if (!isEligiblePostureEntity(mob, patch)) return null;

        float max = liveInputs(mob).maximumPosture();
        return new SyncState(max, max, false);
    }

    /** Clear per-mob posture state when the entity leaves the server level. */
    public static void forget(LivingEntity mob) {
        STATES.remove(mob);
        PENDING_EXECUTION_START.remove(mob);
    }

    private static boolean canReceive(LivingEntity target) {
        return PostureDeathSafety.canReceivePosture(target.isAlive(), target.getHealth());
    }

    /** Delegate animation, armature and Animator checks to the shared policy. */
    private static boolean isEligiblePostureEntity(LivingEntity mob, LivingEntityPatch<?> patch) {
        if (mob instanceof Player) return false;
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

    private static boolean isExecuting(LivingEntity mob) {
        try {
            return ExecutionHandler.isExecutingTarget(mob, mob);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isBlacklisted(LivingEntity mob) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return NeoForgeServerSettings.localSnapshot().blocksPosture(
                id, NeoForgeAutoCompatibility.isOwnedByEfpi(mob.getType()));
    }

    private static PostureInputs liveInputs(LivingEntity mob) {
        return NeoForgeEntityAttributeAdapter.liveInputs(mob);
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
}
