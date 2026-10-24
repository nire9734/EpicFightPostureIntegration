package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.Map;
import java.util.WeakHashMap;
import dev.nire9734.epicfightposture.neoforge.NeoForgePostureConfig;
import dev.nire9734.epicfightposture.neoforge.NeoForgePostureCoverage;
import dev.nire9734.epicfightposture.neoforge.NeoForgeServerSettings;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeAutoCompatibility;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;
import net.shelmarow.combat_evolution.ai.CEHumanoidPatch;
import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import net.shelmarow.combat_evolution.api.event.OnExecutionStartEvent;
import net.shelmarow.combat_evolution.execution.ExecutionHandler;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.skill.guard.GuardSkill;

/** NeoForge server adapter for the shared posture balance and runtime state. */
public final class NeoForgePostureServerEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RECOVERY_TICKS = 60;
    private static final Map<LivingEntity, PostureRuntimeState> STATES = new WeakHashMap<>();
    private static final Map<LivingEntity, Integer> PENDING_EXECUTION_START = new WeakHashMap<>();
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(NeoForgePostureServerEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(NeoForgePostureServerEvents::onTick);
        NeoForge.EVENT_BUS.addListener(NeoForgePostureServerEvents::onExecutionStart);
        dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.register();
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
                target -> new PostureRuntimeState(maximumPosture(target)));
        if (!state.acceptsPostureDamage()) return;

        float maxHealth = mob.getMaxHealth();
        float before = Math.min(maxHealth, mob.getHealth() + loss);
        float pressure = PostureBalance.attackPostureDamage(
                basePosture(mob), maxHealth, before, loss, impact,
                .75F * NeoForgeServerSettings.attackPostureDamageMultiplier(
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
        dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncNow(mob, stateForSync(mob));
    }

    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity mob) || !(mob.level() instanceof ServerLevel)) return;
        if (!canReceive(mob)) {
            // As in Forge 3.0.2, retain an active CE execution during death,
            // but do not keep an orphaned posture HUD state after ordinary death.
            if (PostureDeathSafety.shouldForgetDeadState(
                    mob.isAlive(), mob.getHealth(), isExecuting(mob))) {
                if (STATES.remove(mob) != null) {
                    dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.removeNow(mob);
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
            dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.removeNow(mob);
            return;
        }
        // Distribute the ten-tick refresh across entity IDs, as in Forge.
        if (Math.floorMod(mob.tickCount + mob.getId(), 10) == 0) {
            float max = maximumPosture(mob);
            if (Math.abs(max - state.max) >= .01F) state.updateMaximum(max);
        }
        if (state.postureState == PostureState.BROKEN || state.postureState == PostureState.EXECUTING) {
            if (isExecuting(mob)) {
                PENDING_EXECUTION_START.remove(mob);
                if (state.postureState != PostureState.EXECUTING) {
                    state.enterExecution();
                    dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncNow(mob, stateForSync(mob));
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
                PENDING_EXECUTION_START.remove(mob);
                state.beginRecovery(RECOVERY_TICKS);
                dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncNow(mob, stateForSync(mob));
                return;
            }
            LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, LivingEntityPatch.class);
            if (isActiveNeutralize(patch)) {
                state.brokenTicks++;
            } else {
                state.beginRecovery(RECOVERY_TICKS);
                dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncNow(mob, stateForSync(mob));
                return;
            }
        } else if (state.postureState == PostureState.RECOVERING) {
            if (state.advanceRecovery(RECOVERY_TICKS)) {
                dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncNow(mob, stateForSync(mob));
                return;
            }
        } else if (state.postureState == PostureState.NORMAL) {
            state.advanceRegeneration(NeoForgePostureConfig.REGEN_PERCENT_PER_SECOND.get().floatValue() / 100.0F);
        }
        dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncPeriodic(mob, stateForSync(mob));
    }


    /** Called exactly once by the Epic Fight parry event's setParried(true) hook. */
    public static void onParried(LivingEntity attacker) {
        if (attacker == null || !(attacker.level() instanceof ServerLevel)
                || attacker instanceof Player
                || !canReceive(attacker) || isBlacklisted(attacker)) return;
        LivingEntity mob = attacker;
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, LivingEntityPatch.class);
        if (!isEligiblePostureEntity(mob, patch)) return;

        PostureRuntimeState state = STATES.computeIfAbsent(mob,
                target -> new PostureRuntimeState(maximumPosture(target)));
        if (!state.acceptsPostureDamage()) return;
        float innateEhp = innateEhp(mob);
        float damage = PostureBalance.parryDamage(state.max,
                NeoForgePostureConfig.PARRY_BASE_MAX_PERCENT.get().floatValue() / 100.0F,
                NeoForgePostureConfig.PARRY_BASE_MIN_PERCENT.get().floatValue() / 100.0F,
                innateEhp, 20.0F, .7F);
        damage *= NeoForgeServerSettings.parryPostureDamageMultiplier(
                BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
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
        dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncNow(mob, stateForSync(mob));
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
        if (patch != null && hasDistinctNeutralize(patch)) {
            try {
                active = patch.applyStun(StunType.NEUTRALIZE, 0.0F)
                        && isActiveNeutralize(patch);
            } catch (Throwable failure) {
                LOGGER.warn("EFPI neutralize threw for {} ({}) using patch {}",
                        BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()),
                        mob.getUUID(), patch.getClass().getName(), failure);
            }
        }

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
                target -> new PostureRuntimeState(maximumPosture(target)));
        mob.stopUsingItem();
        state.startExecution();
        PENDING_EXECUTION_START.put(mob,
                mob.tickCount + PostureRuntimeState.EXECUTION_START_GRACE_TICKS);
        dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncNow(mob, stateForSync(mob));
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
        return PostureBalance.effectiveHealth(hp > 0.0F ? hp : 20.0F, armor, toughness, 8.0F);
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
        if (!(mob.level() instanceof ServerLevel) || !canReceive(mob)) {
            return null;
        }
        SyncState existing = stateForSync(mob);
        if (existing != null) return existing;
        if (isBlacklisted(mob)) return null;

        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, LivingEntityPatch.class);
        if (!isEligiblePostureEntity(mob, patch)) return null;

        float max = maximumPosture(mob);
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
            boolean spiderCompatible = !(patch instanceof dev.nire9734.epicfightposture.neoforge.compat.NeoForgeLightweightSpiderPatch<?>)
                    || dev.nire9734.epicfightposture.neoforge.compat.NeoForgeSpiderExecutionSupport.isCompatible(patch);
            PostureExecutionEligibility.Comparison comparison = PostureExecutionEligibility.compare(
                    patch, patch != null && !patch.uninitialized(),
                    patch instanceof CustomExecuteEntity, exclusion, spiderCompatible);
            try {
                NeoForgePostureCoverage.observe(mob, patch, comparison);
            } catch (RuntimeException | LinkageError ignored) {
                // Diagnostics must not change gameplay eligibility.
            }
            return comparison.current().supported();
        } catch (Throwable ignored) {
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

    private static float basePosture(LivingEntity mob) {
        float ehp = NeoForgeEntityAttributeAdapter.liveEffectiveHealth(mob);
        float ratio = Math.max(.01F, ehp / 20.0F);
        return Math.max(.1F, Math.min(1_000_000.0F,
                3.2F * (float) Math.pow(ratio, .7D)));
    }

    private static float maximumPosture(LivingEntity mob) {
        return Math.max(1.0F, basePosture(mob));
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

    private static boolean sameAnimation(AssetAccessor<?> first, AssetAccessor<?> second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        try {
            ResourceLocation a = first.registryName(), b = second.registryName();
            return a != null && a.equals(b);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private NeoForgePostureServerEvents() {}
}
