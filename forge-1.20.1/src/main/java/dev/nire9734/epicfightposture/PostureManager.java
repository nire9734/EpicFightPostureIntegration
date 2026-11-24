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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.shelmarow.combat_evolution.ai.CEHumanoidPatch;
import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import net.shelmarow.combat_evolution.execution.ExecutionHandler;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Forge 1.20.1 adapter for the shared {@link PostureEngine}. The engine owns
 * every Posture state transition; this class supplies Forge, Epic Fight and
 * Combat Evolution access, and detects direct-hit damage the Forge way.
 */
public final class PostureManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final PostureEngine<LivingEntity> ENGINE = new PostureEngine<>(new ForgeHost());
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

        if (!playerHit || !Float.isFinite(eventDamage) || eventDamage <= 0.0F) {
            return;
        }
        float impact = CompatibilityDamage.calculateBaseImpact(source, eventDamage);
        if (!Float.isFinite(impact) || impact <= 0.0F || !ENGINE.acceptsAttack(target)) {
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
        if (pending == null) {
            return;
        }
        float actualLoss = PostureBalance.actualHealthLoss(pending.healthBefore(), target.getHealth());
        if (actualLoss <= 0.0F) {
            return;
        }
        // The engine refuses dead targets: a lethal hit must never start NEUTRALIZE.
        ENGINE.applyAttack(target, pending.healthBefore(), pending.maxHealth(), actualLoss, pending.impact());
    }

    public static void onParried(LivingEntity target, DamageSource source) {
        ENGINE.onParried(target, isProjectile(source));
    }

    /** Successful normal guard; caller already excluded parry and stamina guard-break. */
    public static void onGuarded(LivingEntity target, DamageSource source) {
        ENGINE.onGuarded(target, isProjectile(source));
    }

    /** Vanilla and modded ranged attacks: the projectile tag, or a projectile direct entity. */
    private static boolean isProjectile(DamageSource source) {
        return source != null && (source.is(DamageTypeTags.IS_PROJECTILE)
                || source.getDirectEntity() instanceof Projectile);
    }

    /** Returns true while the entity has a Posture state (for periodic sync). */
    public static boolean tick(LivingEntity entity) {
        if (entity == null || !RuntimeAccess.isServerEntity(entity)) {
            return false;
        }
        if (canReceivePosture(entity)) {
            // Damage listeners execute before vanilla subtracts HP. Flush at the
            // next entity tick, after all later listeners and HP changes have run.
            flushPendingDamage(entity);
        } else {
            // Never confirm a pending fatal hit as Posture damage.
            PENDING_ATTACKS.remove(entity);
        }
        return ENGINE.tick(entity);
    }

    /** Called from Combat Evolution's execution-start event. */
    public static void onExecutionStart(LivingEntity target) {
        ENGINE.onExecutionStart(target);
    }

    /** Snapshot for the network synchronizer; see {@link PostureEngine#snapshot}. */
    public static PostureEngine.SyncState syncState(LivingEntity entity, boolean includePristine) {
        return ENGINE.snapshot(entity, includePristine);
    }

    public static void forgetState(LivingEntity entity, boolean notifyClients) {
        if (entity == null) {
            return;
        }
        PENDING_ATTACKS.remove(entity);
        ENGINE.forget(entity, notifyClients);
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

    public static void stopUsingItem(LivingEntity entity) {
        if (entity != null && entity.isUsingItem()) {
            entity.stopUsingItem();
        }
    }

    private static boolean canReceivePosture(LivingEntity entity) {
        return entity != null && PostureDeathSafety.canReceivePosture(entity.isAlive(), entity.getHealth());
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
            return player != null && player.getRealAnimation() == neutralize;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private PostureManager() {
    }

    private record PendingAttack(float healthBefore, float maxHealth, float impact) {
    }

    /** Forge 1.20.1, Epic Fight and Combat Evolution access for the shared engine. */
    private static final class ForgeHost implements PostureHost<LivingEntity> {
        @Override
        public boolean isServerEntity(LivingEntity entity) {
            return RuntimeAccess.isServerEntity(entity);
        }

        @Override
        public boolean isAlive(LivingEntity entity) {
            return entity.isAlive();
        }

        @Override
        public float health(LivingEntity entity) {
            return entity.getHealth();
        }

        @Override
        public int tickCount(LivingEntity entity) {
            return entity.tickCount;
        }

        @Override
        public int entityId(LivingEntity entity) {
            return entity.getId();
        }

        @Override
        public boolean isBlacklisted(LivingEntity entity) {
            return PostureServerConfig.isBlacklisted(entityTypeId(entity));
        }

        @Override
        public boolean isEligible(LivingEntity entity) {
            if (entity instanceof Player) return false;
            LivingEntityPatch<?> targetPatch = getPatch(entity);
            PostureExecutionEligibility.Reason exclusion = null;
            if (!canReceivePosture(entity)) {
                exclusion = PostureExecutionEligibility.Reason.TARGET_NOT_ALIVE;
            } else if (isBlacklisted(entity)) {
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

        @Override
        public boolean isExecuting(LivingEntity entity) {
            try {
                return ExecutionHandler.isExecutingTarget(entity, entity);
            } catch (Throwable ignored) {
                return false;
            }
        }

        /** Live attribute inputs for maximum Posture, read through the 1.20.1 attribute API. */
        @Override
        public PostureInputs liveInputs(LivingEntity entity) {
            return PostureInputs.of(
                    entity.getMaxHealth(),
                    (float) entity.getAttributeValue(Attributes.ARMOR),
                    (float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
                    PostureTuning.MINIMUM_POSTURE,
                    true);
        }

        /**
         * EHP classification uses the EntityType's registered default attributes,
         * not mutable instance base values (which level mods may rewrite).
         */
        @Override
        public float innateEffectiveHealth(LivingEntity entity) {
            float health = baseAttribute(entity, Attributes.MAX_HEALTH, entity.getMaxHealth());
            float armor = baseAttribute(entity, Attributes.ARMOR, 0.0F);
            float toughness = baseAttribute(entity, Attributes.ARMOR_TOUGHNESS, 0.0F);
            return PostureBalance.referenceEffectiveHealth(health, armor, toughness);
        }

        @Override
        public void stopUsingItem(LivingEntity entity) {
            PostureManager.stopUsingItem(entity);
        }

        @Override
        public NeutralizeResult applyNeutralize(LivingEntity entity) {
            LivingEntityPatch<?> patch = getPatch(entity);
            if (patch == null) return NeutralizeResult.failure("PATCH_MISSING");
            if (!hasNeutralizeAnimation(patch)) return NeutralizeResult.failure("NEUTRALIZE_MISSING");
            try {
                boolean applied = patch.applyStun(StunType.NEUTRALIZE, 0.0F);
                if (applied && isActiveNeutralize(patch)) return NeutralizeResult.success();
                LOGGER.debug("EFPI neutralize unavailable for {} (applied={}, patch={})",
                        entityTypeId(entity), applied, patch.getClass().getName());
                return NeutralizeResult.failure(applied ? "ANIMATION_NOT_ACTIVE" : "STUN_NOT_APPLIED");
            } catch (Throwable failure) {
                LOGGER.warn("EFPI neutralize threw for {} ({}) using patch {}",
                        entityTypeId(entity), entity.getUUID(), patch.getClass().getName(), failure);
                return NeutralizeResult.failure("STUN_EXCEPTION:" + failure.getClass().getSimpleName());
            }
        }

        @Override
        public boolean isNeutralizeActive(LivingEntity entity) {
            return isActiveNeutralize(getPatch(entity));
        }

        @Override
        public void syncNow(LivingEntity entity) {
            PostureSyncEvents.syncNow(entity);
        }

        @Override
        public void syncRemove(LivingEntity entity) {
            PostureSyncEvents.removeNow(entity);
        }

        @Override
        public void observeExecution(LivingEntity entity, String stage) {
            ForgePostureCoverage.observeExecution(entity, stage);
        }

        @Override
        public void observeGuardBreak(LivingEntity entity, boolean active, String failureReason) {
            ForgePostureCoverage.observeGuardBreak(entity, active, failureReason);
        }

        @Override
        public PostureSettings settings() {
            return new PostureSettings(
                    PostureServerConfig.parryBaseMaxFraction(),
                    PostureServerConfig.parryBaseMinFraction(),
                    PostureServerConfig.postureBreakResistanceStep(),
                    PostureServerConfig.postureBreakResistanceMax(),
                    PostureBalance.regenDelayTicks(PostureServerConfig.postureRegenDelaySeconds()),
                    PostureServerConfig.postureRegenFractionPerSecond(),
                    PostureServerConfig.GUARD_POSTURE_DAMAGE_ENABLED.get(),
                    PostureServerConfig.GUARD_POSTURE_DAMAGE_MULTIPLIER.get().floatValue(),
                    PostureServerConfig.PROJECTILE_DEFENSE_POSTURE_MULTIPLIER.get().floatValue());
        }

        @Override
        public float attackCoefficient(LivingEntity entity) {
            return PostureServerConfig.attackPostureCoefficient(entityTypeId(entity));
        }

        @Override
        public float parryMultiplier(LivingEntity entity) {
            return PostureServerConfig.parryPostureDamageMultiplier(entityTypeId(entity));
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
    }
}
