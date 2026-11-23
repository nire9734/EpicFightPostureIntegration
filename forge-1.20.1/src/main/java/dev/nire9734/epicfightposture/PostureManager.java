package dev.nire9734.epicfightposture;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.MissingHumanoidNeutralize;
import dev.nire9734.epicfightposture.compat.spider.LightweightSpiderPatch;
import dev.nire9734.epicfightposture.compat.spider.SpiderExecutionSupport;
import dev.nire9734.epicfightposture.sync.ForgePostureCoverage;
import dev.nire9734.epicfightposture.sync.PostureSyncEvents;
import dev.nire9734.epicfightposture.sync.RuntimeAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.shelmarow.combat_evolution.ai.CEHumanoidPatch;
import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import net.shelmarow.combat_evolution.execution.ExecutionHandler;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

import java.util.Map;
import java.util.WeakHashMap;
import org.slf4j.Logger;

/** Owns server-authoritative posture state for eligible Epic Fight entity patches. */
public final class PostureManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<LivingEntity, PostureRuntimeState> STATES = new WeakHashMap<>();
    private static final Map<LivingEntity, Integer> PENDING_EXECUTION_START = new WeakHashMap<>();
    /** One in-flight LivingDamageEvent per target, resolved after vanilla actually changes HP. */
    private static final Map<LivingEntity, PendingAttack> PENDING_ATTACKS = new WeakHashMap<>();

    /**
     * Called at the lowest Forge event priority. Another mod (including a KubeJS
     * LOWEST handler) may still change/cancel this event. Store attack metadata and
     * calculate posture only when the real health change can be observed.
     *
     * A new damage event closes the previous one; the ordinary entity tick closes
     * the last attack. This also handles multiple hits in the same server tick.
     */
    public static void observeDamageEvent(
            LivingEntity target, float eventDamage, DamageSource source, boolean playerHit) {
        if (target == null || !RuntimeAccess.isServerEntity(target)) {
            return;
        }
        flushPendingDamage(target);

        if (!canReceivePosture(target)
                || !playerHit || !Float.isFinite(eventDamage) || eventDamage <= 0.0F) {
            return;
        }
        LivingEntityPatch<?> patch = getPatch(target);
        if (!isEligiblePostureEntity(target, patch)) {
            return;
        }

        float impact = CompatibilityDamage.calculateBaseImpact(source, eventDamage);
        if (!Float.isFinite(impact) || impact <= 0.0F || !state(target).acceptsPostureDamage()) {
            return;
        }

        PENDING_ATTACKS.put(target, new PendingAttack(
                target.getHealth(), target.getMaxHealth(), impact));
    }

    /**
     * Uses observed HP loss instead of the LivingDamageEvent amount so a later
     * same-priority damage cap/cancellation cannot produce phantom posture.
     */
    private static void flushPendingDamage(LivingEntity target) {
        PendingAttack pending = PENDING_ATTACKS.remove(target);
        // A lethal hit may have started the Epic Fight death animation after
        // the damage event but before this delayed health-delta callback.
        // Never apply NEUTRALIZE after death, even when actual HP loss > 0.
        if (pending == null || !canReceivePosture(target)) {
            return;
        }
        float actualLoss = PostureBalance.actualHealthLoss(pending.healthBefore(), target.getHealth());
        if (actualLoss <= 0.0F || !RuntimeAccess.isServerEntity(target)) {
            return;
        }
        LivingEntityPatch<?> patch = getPatch(target);
        if (!isEligiblePostureEntity(target, patch)) {
            return;
        }
        float postureDamage = PostureBalance.attackPostureDamage(
                liveInputs(target).basePosture(),
                pending.maxHealth(),
                pending.healthBefore(),
                actualLoss,
                pending.impact(),
                PostureServerConfig.attackPostureCoefficient(entityTypeId(target))
        );
        damagePosture(target, patch, postureDamage);
    }

    public static void onParried(LivingEntity target) {
        applyDefensivePostureDamage(target, 1.0F);
    }

    /** Successful normal guard; caller already excluded parry and stamina guard-break. */
    public static void onGuarded(LivingEntity target) {
        if (!PostureServerConfig.GUARD_POSTURE_DAMAGE_ENABLED.get()) return;
        applyDefensivePostureDamage(target,
                PostureServerConfig.GUARD_POSTURE_DAMAGE_MULTIPLIER.get().floatValue());
    }

    private static void applyDefensivePostureDamage(LivingEntity target, float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier <= 0.0F) return;
        if (!canReceivePosture(target) || !RuntimeAccess.isServerEntity(target)) {
            return;
        }

        LivingEntityPatch<?> patch = getPatch(target);
        if (!isEligiblePostureEntity(target, patch)) {
            return;
        }

        PostureRuntimeState state = state(target);
        if (!state.acceptsPostureDamage()) {
            return;
        }

        // EntityType defaults keep parry percentages stable across level mods,
        // including those that mutate a mob's AttributeInstance base values.
        float innateEhp = baseEffectiveHealth(target);
        float postureDamage = PostureBalance.parryDamage(
                state.max,
                PostureServerConfig.parryBaseMaxFraction(),
                PostureServerConfig.parryBaseMinFraction(),
                innateEhp,
                PostureTuning.EHP_REFERENCE_HEALTH,
                (float) PostureTuning.EHP_EXPONENT
        );
        postureDamage *= PostureServerConfig.parryPostureDamageMultiplier(entityTypeId(target));
        postureDamage = PostureBalance.guardDamage(postureDamage, multiplier);
        float resistance = breakResistanceMultiplier(state);
        float previousPosture = state.current;
        boolean broken = state.applyDamage(postureDamage, resistance, regenDelayTicks());
        if (!(state.current < previousPosture)) {
            return;
        }
        if (broken) {
            breakPosture(target, patch, state);
        }
        PostureSyncEvents.syncNow(target);
    }

    public static boolean tick(LivingEntity entity) {
        if (entity == null || !RuntimeAccess.isServerEntity(entity)) {
            return false;
        }
        if (!canReceivePosture(entity)) {
            // Do not confirm a pending fatal hit as posture damage. A genuine
            // Combat Evolution execution may intentionally outlive the killing
            // blow, so retain its current special-render state until CE finishes.
            PENDING_ATTACKS.remove(entity);
            if (PostureDeathSafety.shouldForgetDeadState(
                    entity.isAlive(), entity.getHealth(), isExecuting(entity))) {
                forgetState(entity, true);
            }
            return false;
        }

        // Damage listeners execute before vanilla subtracts HP. Flush at the
        // next entity tick, after all later listeners and HP changes have run.
        flushPendingDamage(entity);
        PostureRuntimeState state = STATES.get(entity);
        if (state == null) {
            return false;
        }

        if (PostureServerConfig.isBlacklisted(entityTypeId(entity))) {
            forgetState(entity, true);
            return false;
        }

        // Spread attribute reads over ten server ticks and only recalculate when inputs changed.
        if (Math.floorMod(entity.tickCount + entity.getId(),
                PostureTuning.MAX_POSTURE_REFRESH_INTERVAL_TICKS) == 0) {
            state.refreshMaximum(liveInputs(entity));
        }

        if (state.postureState == PostureState.BROKEN || state.postureState == PostureState.EXECUTING) {
            boolean executing = isExecuting(entity);
            if (executing) {
                boolean pendingExecution = PENDING_EXECUTION_START.remove(entity) != null;
                if (pendingExecution || state.postureState != PostureState.EXECUTING) {
                    ForgePostureCoverage.observeExecution(entity, "CONFIRMED");
                }
                if (state.postureState != PostureState.EXECUTING) {
                    state.enterExecution();
                    PostureSyncEvents.syncNow(entity);
                } else {
                    state.current = 0.0F;
                }
                return true;
            }

            if (state.postureState == PostureState.EXECUTING) {
                if (PostureRuntimeState.waitingForExecutionTask(
                        entity.tickCount, PENDING_EXECUTION_START.get(entity))) {
                    return true;
                }
                if (PENDING_EXECUTION_START.remove(entity) != null) {
                    ForgePostureCoverage.observeExecution(entity, "TIMEOUT");
                }
                startRecovering(state);
                PostureSyncEvents.syncNow(entity);
                return true;
            }

            LivingEntityPatch<?> patch = getPatch(entity);
            boolean guardBreakActive = isActiveNeutralize(patch);
            if (!guardBreakActive) {
                startRecovering(state);
                PostureSyncEvents.syncNow(entity);
            } else {
                state.brokenTicks++;
            }
            return true;
        }

        if (state.postureState == PostureState.RECOVERING) {
            if (state.advanceRecovery(PostureTuning.BREAK_RECOVERY_TICKS)) {
                PostureSyncEvents.syncNow(entity);
            }
            return true;
        }
        state.advanceRegeneration(PostureServerConfig.postureRegenFractionPerSecond());
        return true;
    }

    /** Called from Combat Evolution's execution-start event. */
    public static void onExecutionStart(LivingEntity target, LivingEntityPatch<?> patch) {
        if (target == null || !RuntimeAccess.isServerEntity(target)
                || !isEligiblePostureEntity(target, patch)) {
            return;
        }
        // A rear assassination can begin before the target has ever taken Posture damage.
        PostureRuntimeState state = state(target);
        stopUsingItem(target);
        state.startExecution();
        ForgePostureCoverage.observeExecution(target, "STARTED");
        PENDING_EXECUTION_START.put(target,
                target.tickCount + PostureRuntimeState.EXECUTION_START_GRACE_TICKS);
        PostureSyncEvents.syncNow(target);
    }

    /**
     * Read-only state used by the network synchronizer. When a client starts
     * tracking an eligible, unhurt mob, send a pristine snapshot without
     * allocating a combat state that would need ticking for its entire life.
     */
    public static SyncState syncState(LivingEntity entity, boolean includePristine) {
        if (entity == null || !RuntimeAccess.isServerEntity(entity)) {
            return null;
        }
        if (PostureServerConfig.isBlacklisted(entityTypeId(entity))) {
            return null;
        }

        PostureRuntimeState state = STATES.get(entity);
        if (state == null && includePristine) {
            LivingEntityPatch<?> patch = getPatch(entity);
            if (!isEligiblePostureEntity(entity, patch)) {
                return null;
            }
            float max = liveInputs(entity).maximumPosture();
            return new SyncState(max, max, false);
        }
        return state == null ? null : new SyncState(state.current, state.max, state.isBrokenForHud());
    }

    public static void forgetState(LivingEntity entity, boolean notifyClients) {
        if (entity == null) {
            return;
        }
        PENDING_ATTACKS.remove(entity);
        PENDING_EXECUTION_START.remove(entity);
        PostureRuntimeState removed = STATES.remove(entity);
        if (removed != null && notifyClients && RuntimeAccess.isServerEntity(entity)) {
            PostureSyncEvents.removeNow(entity);
        }
    }

    /** Final posture damage from direct hits. Successive breaks reduce the applied damage. */
    private static float breakResistanceMultiplier(PostureRuntimeState state) {
        return PostureBalance.breakResistanceMultiplier(
                state.completedBreaks,
                PostureServerConfig.postureBreakResistanceStep(),
                PostureServerConfig.postureBreakResistanceMax()
        );
    }

    private static void damagePosture(LivingEntity entity, LivingEntityPatch<?> patch, float damage) {
        if (!canReceivePosture(entity) || damage <= 0.0F || !Float.isFinite(damage)) {
            return;
        }

        PostureRuntimeState state = state(entity);
        if (!state.acceptsPostureDamage()) {
            return;
        }

        float previousPosture = state.current;
        boolean broken = state.applyDamage(damage, breakResistanceMultiplier(state), regenDelayTicks());
        if (!(state.current < previousPosture)) {
            return;
        }
        if (broken) {
            breakPosture(entity, patch, state);
        }
        PostureSyncEvents.syncNow(entity);
    }

    private static void breakPosture(LivingEntity entity, LivingEntityPatch<?> patch, PostureRuntimeState state) {
        if (!canReceivePosture(entity) || !state.acceptsPostureDamage()) {
            return;
        }

        state.prepareBreak(regenDelayTicks());
        stopUsingItem(entity);

        boolean applied = false;
        boolean active = false;
        String failureReason = patch == null ? "PATCH_MISSING" : "NEUTRALIZE_MISSING";
        if (patch != null && hasNeutralizeAnimation(patch)) {
            try {
                applied = patch.applyStun(StunType.NEUTRALIZE, 0.0F);
                active = applied && isActiveNeutralize(patch);
                if (!active) failureReason = applied ? "ANIMATION_NOT_ACTIVE" : "STUN_NOT_APPLIED";
            } catch (Throwable failure) {
                failureReason = "STUN_EXCEPTION:" + failure.getClass().getSimpleName();
                LOGGER.warn("EFPI neutralize threw for {} ({}) using patch {}",
                        entityTypeId(entity), entity.getUUID(), patch.getClass().getName(), failure);
            }
        }

        ForgePostureCoverage.observeGuardBreak(entity, active, active ? "none" : failureReason);

        if (active) {
            // CE checks the *NEUTRALIZE* animation identity, not arbitrary LONG stuns.
            state.confirmBreak();
        } else {
            // A transiently rejected stun does not justify removing posture tracking.
            LOGGER.debug("EFPI neutralize unavailable for {} (applied={}, patch={})",
                    entityTypeId(entity), applied,
                    patch == null ? "none" : patch.getClass().getName());
            startRecovering(state);
        }
    }

    private static boolean isEligiblePostureEntity(LivingEntity entity, LivingEntityPatch<?> targetPatch) {
        if (entity instanceof Player) return false;
        PostureExecutionEligibility.Reason exclusion = null;
        if (!canReceivePosture(entity)) {
            exclusion = PostureExecutionEligibility.Reason.TARGET_NOT_ALIVE;
        } else if (PostureServerConfig.isBlacklisted(entityTypeId(entity))) {
            exclusion = PostureExecutionEligibility.Reason.ENTITY_BLACKLISTED;
        } else if (targetPatch instanceof CEHumanoidPatch) {
            exclusion = PostureExecutionEligibility.Reason.CE_OWNED_PATCH;
        }
        try {
            if (exclusion == null && targetPatch != null) {
                MissingHumanoidNeutralize.installIfNeeded(targetPatch);
            }
            boolean spiderCompatible = !(targetPatch instanceof LightweightSpiderPatch)
                    || SpiderExecutionSupport.isCompatible(targetPatch);
            PostureExecutionEligibility.Result result = PostureExecutionEligibility.evaluate(
                    targetPatch, targetPatch != null && targetPatch.isInitialized(),
                    targetPatch instanceof CustomExecuteEntity, exclusion, spiderCompatible);
            try {
                ForgePostureCoverage.observe(entity, targetPatch, result);
            } catch (RuntimeException | LinkageError ignored) {
                // Diagnostics must not change gameplay eligibility.
            }
            return result.supported();
        } catch (Throwable ignored) {
            try {
                ForgePostureCoverage.observeEligibilityFailure(entity, targetPatch);
            } catch (Throwable diagnosticFailure) {
                // Never let report failures change combat behavior.
            }
            return false;
        }
    }

    /**
     * Whether the patch advertises an independent neutralize animation.
     * Necessary for guard-break executions but not sufficient: applyStun may
     * still reject the animation, or a mob may immediately replace it.
     */
    public static boolean hasNeutralizeAnimation(LivingEntityPatch<?> patch) {
        if (patch == null) {
            return false;
        }
        // Repair the Epic Fight datapack provider before testing eligibility.
        // The provider, not a return-value Mixin, is the canonical source read
        // by applyStun and Combat Evolution.
        MissingHumanoidNeutralize.installIfNeeded(patch);
        return PostureExecutionEligibility.hasDistinctNeutralize(patch);
    }

    private static boolean isActiveNeutralize(LivingEntityPatch<?> patch) {
        if (patch == null || !hasNeutralizeAnimation(patch)) {
            return false;
        }
        try {
            var player = patch.getAnimator().getPlayerFor(null);
            AssetAccessor<? extends StaticAnimation> neutralize = patch.getHitAnimation(StunType.NEUTRALIZE);
            // Combat Evolution's isTargetGuardBreak compares these accessor
            // identities directly. isStunned() is a separate state-spectrum
            // predicate and need not agree during an animation transition.
            // Requiring both caused false-negative breaks/recovery.
            return player != null && player.getRealAnimation() == neutralize;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static PostureRuntimeState state(LivingEntity entity) {
        PostureRuntimeState existing = STATES.get(entity);
        if (existing != null) {
            return existing;
        }
        PostureRuntimeState created = new PostureRuntimeState(liveInputs(entity));
        STATES.put(entity, created);
        return created;
    }

    private static void startRecovering(PostureRuntimeState state) {
        state.beginRecovery(PostureTuning.BREAK_RECOVERY_TICKS);
    }

    public static void stopUsingItem(LivingEntity entity) {
        if (entity != null && entity.isUsingItem()) {
            entity.stopUsingItem();
        }
    }

    private static boolean isExecuting(LivingEntity entity) {
        try {
            return ExecutionHandler.isExecutingTarget(entity, entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * EHP classification uses the EntityType's registered default attributes,
     * not mutable instance base values (which level mods may rewrite).
     * If no supplier/attribute exists, fall back to the original instance base.
     */
    private static float baseEffectiveHealth(LivingEntity entity) {
        float health = baseAttribute(entity, Attributes.MAX_HEALTH, entity.getMaxHealth());
        boolean useArmor = PostureServerConfig.useArmorScaling();
        float armor = useArmor ? baseAttribute(entity, Attributes.ARMOR, 0.0F) : 0.0F;
        float toughness = useArmor ? baseAttribute(entity, Attributes.ARMOR_TOUGHNESS, 0.0F) : 0.0F;
        return PostureBalance.referenceEffectiveHealth(health, armor, toughness);
    }

    @SuppressWarnings("unchecked")
    private static float baseAttribute(LivingEntity entity, Attribute attribute, float fallback) {
        AttributeInstance instance = entity.getAttribute(attribute);
        float instanceBase = instance == null ? Float.NaN : (float) instance.getBaseValue();
        float typeDefault = Float.NaN;
        try {
            EntityType<? extends LivingEntity> type = (EntityType<? extends LivingEntity>) entity.getType();
            if (DefaultAttributes.hasSupplier(type)) {
                AttributeSupplier defaults = DefaultAttributes.getSupplier(type);
                if (defaults != null && defaults.hasAttribute(attribute)) {
                    typeDefault = (float) defaults.getBaseValue(attribute);
                }
            }
        } catch (RuntimeException ignored) {
            // Non-standard entity registrations must not break parry handling.
        }
        return PostureBalance.innateAttributeValue(typeDefault, instanceBase, fallback);
    }

    /** Live attribute inputs for maximum Posture, read through the 1.20.1 attribute API. */
    private static PostureInputs liveInputs(LivingEntity entity) {
        boolean useArmor = PostureServerConfig.useArmorScaling();
        return PostureInputs.of(
                entity.getMaxHealth(),
                useArmor ? (float) entity.getAttributeValue(Attributes.ARMOR) : 0.0F,
                useArmor ? (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS) : 0.0F,
                PostureServerConfig.minimumPosture(),
                useArmor);
    }

    /** Terminal health states must not schedule or play a new posture-break animation. */
    private static boolean canReceivePosture(LivingEntity entity) {
        return entity != null && PostureDeathSafety.canReceivePosture(
                entity.isAlive(), entity.getHealth());
    }

    private static ResourceLocation entityTypeId(LivingEntity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    private static LivingEntityPatch<?> getPatch(LivingEntity entity) {
        try {
            return EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int regenDelayTicks() {
        return PostureBalance.regenDelayTicks(PostureServerConfig.postureRegenDelaySeconds());
    }

    private PostureManager() {
    }

    private record PendingAttack(float healthBefore, float maxHealth, float impact) {
    }

    public record SyncState(float current, float max, boolean broken) {
    }
}
