package dev.nire9734.epicfightposture;

import dev.nire9734.epicfightposture.sync.PostureSyncEvents;
import dev.nire9734.epicfightposture.sync.RuntimeAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.shelmarow.combat_evolution.ai.CEHumanoidPatch;
import net.shelmarow.combat_evolution.ai.iml.CustomExecuteEntity;
import net.shelmarow.combat_evolution.execution.ExecutionHandler;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.damagesource.StunType;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Owns server-authoritative posture state for eligible Epic Fight entity patches. */
public final class PostureManager {
    private static final float HURT_IMPACT_MULTIPLIER = 0.55F;
    private static final int REGEN_DELAY_TICKS = 150;
    private static final int MAX_POSTURE_REFRESH_INTERVAL_TICKS = 10;
    private static final int BREAK_RECOVERY_TICKS = 60;
    private static final float REGEN_FRACTION_PER_SECOND = 0.05F;

    private static final float EHP_REFERENCE_DAMAGE = 8.0F;
    private static final float EHP_REFERENCE_HEALTH = 20.0F;
    private static final float EHP_REFERENCE_POSTURE = 3.2F;
    private static final double EHP_EXPONENT = 0.7D;
    private static final float MAX_SAFE_POSTURE = 1_000_000.0F;

    private static final float PARRY_BASE_DAMAGE = 0.5F;
    private static final float PARRY_MAX_FRACTION = 0.08F;
    private static final float PARRY_DAMAGE_CAP = 2.0F;

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

    public static void onPlayerDamage(LivingEntity target, Player player, float damageAmount, DamageSource source) {
        if (target == null || damageAmount <= 0.0F || !RuntimeAccess.isServerEntity(target)) {
            return;
        }

        LivingEntityPatch<?> patch = getPatch(target);
        if (!isEligiblePostureEntity(target, patch)) {
            return;
        }

        float impact = CompatibilityDamage.calculateImpact(source, damageAmount);
        if (impact <= 0.0F || !Float.isFinite(impact)) {
            return;
        }

        damagePosture(target, patch, impact * HURT_IMPACT_MULTIPLIER);
    }

    public static void onParried(LivingEntity target, PlayerPatch<?> playerPatch) {
        if (target == null || !RuntimeAccess.isServerEntity(target)) {
            return;
        }

        LivingEntityPatch<?> patch = getPatch(target);
        if (!isEligiblePostureEntity(target, patch)) {
            return;
        }

        State state = state(target);
        if (state.isLocked()) {
            return;
        }

        float postureDamage = Math.min(PARRY_DAMAGE_CAP, PARRY_BASE_DAMAGE + state.max * PARRY_MAX_FRACTION);
        state.current = Math.max(0.0F, state.current - postureDamage);
        state.regenDelay = REGEN_DELAY_TICKS;

        if (state.current <= 0.0F) {
            breakPosture(target, patch, state);
        } else {
            try {
                patch.applyStun(StunType.LONG, 0.0F);
            } catch (Throwable ignored) {
            }
        }
        PostureSyncEvents.syncNow(target);
    }

    public static boolean tick(LivingEntity entity) {
        if (entity == null || !RuntimeAccess.isServerEntity(entity)) {
            return false;
        }

        State state = STATES.get(entity);
        if (state == null) {
            return false;
        }

        if (PostureServerConfig.isBlacklisted(entityTypeId(entity))) {
            forgetState(entity, true);
            return false;
        }

        // Spread attribute reads over ten server ticks and only recalculate when inputs changed.
        if (Math.floorMod(entity.tickCount + entity.getId(), MAX_POSTURE_REFRESH_INTERVAL_TICKS) == 0) {
            refreshMaxPosture(entity, state);
        }

        if (state.postureState == PostureState.BROKEN || state.postureState == PostureState.EXECUTING) {
            boolean executing = isExecuting(entity);
            if (executing) {
                if (state.postureState != PostureState.EXECUTING) {
                    state.postureState = PostureState.EXECUTING;
                    state.current = 0.0F;
                    PostureSyncEvents.syncNow(entity);
                } else {
                    state.current = 0.0F;
                }
                return true;
            }

            if (state.postureState == PostureState.EXECUTING) {
                startRecovering(state);
                PostureSyncEvents.syncNow(entity);
                return true;
            }

            LivingEntityPatch<?> patch = getPatch(entity);
            try {
                if (patch == null || !patch.isStunned()) {
                    startRecovering(state);
                    PostureSyncEvents.syncNow(entity);
                }
            } catch (Throwable ignored) {
                startRecovering(state);
                PostureSyncEvents.syncNow(entity);
            }
            return true;
        }

        if (state.postureState == PostureState.RECOVERING) {
            state.recoveryTicksRemaining--;
            if (state.recoveryTicksRemaining <= 0) {
                state.recoveryTicksRemaining = 0;
                state.current = state.max;
                state.postureState = PostureState.NORMAL;
                PostureSyncEvents.syncNow(entity);
            } else {
                state.current = state.max
                        * (BREAK_RECOVERY_TICKS - state.recoveryTicksRemaining)
                        / BREAK_RECOVERY_TICKS;
            }
            return true;
        }

        if (state.current >= state.max) {
            return true;
        }

        if (state.regenDelay > 0) {
            state.regenDelay--;
            return true;
        }

        float regenPerTick = state.max * REGEN_FRACTION_PER_SECOND / 20.0F;
        state.current = Math.min(state.max, state.current + regenPerTick);
        return true;
    }

    /** Called from Combat Evolution's execution-start event. */
    public static void onExecutionStart(LivingEntity target) {
        if (target == null || !RuntimeAccess.isServerEntity(target)) {
            return;
        }
        State state = STATES.get(target);
        if (state == null) {
            return;
        }
        stopUsingItem(target);
        state.postureState = PostureState.EXECUTING;
        state.current = 0.0F;
        state.regenDelay = 0;
        state.recoveryTicksRemaining = 0;
        PostureSyncEvents.syncNow(target);
    }

    /** Read-only state used by the network synchronizer. */
    public static SyncState syncState(LivingEntity entity, boolean forceCreate) {
        if (entity == null || !RuntimeAccess.isServerEntity(entity)) {
            return null;
        }
        if (PostureServerConfig.isBlacklisted(entityTypeId(entity))) {
            return null;
        }

        State state = STATES.get(entity);
        if (state == null && forceCreate) {
            LivingEntityPatch<?> patch = getPatch(entity);
            if (!isEligiblePostureEntity(entity, patch)) {
                return null;
            }
            state = state(entity);
        }
        return state == null ? null : new SyncState(state.current, state.max, state.isBrokenForHud());
    }

    public static void forgetState(LivingEntity entity, boolean notifyClients) {
        if (entity == null) {
            return;
        }
        State removed = STATES.remove(entity);
        if (removed != null && notifyClients && RuntimeAccess.isServerEntity(entity)) {
            PostureSyncEvents.removeNow(entity);
        }
    }

    private static void damagePosture(LivingEntity entity, LivingEntityPatch<?> patch, float damage) {
        if (damage <= 0.0F || !Float.isFinite(damage)) {
            return;
        }

        State state = state(entity);
        if (state.isLocked()) {
            return;
        }

        state.current = Math.max(0.0F, state.current - damage);
        state.regenDelay = REGEN_DELAY_TICKS;
        if (state.current <= 0.0F) {
            breakPosture(entity, patch, state);
        }
        PostureSyncEvents.syncNow(entity);
    }

    private static void breakPosture(LivingEntity entity, LivingEntityPatch<?> patch, State state) {
        if (state.isLocked()) {
            return;
        }

        state.current = 0.0F;
        state.regenDelay = REGEN_DELAY_TICKS;
        stopUsingItem(entity);

        boolean stunned = false;
        if (patch != null && hasNeutralizeAnimation(patch)) {
            try {
                stunned = patch.applyStun(StunType.NEUTRALIZE, 0.0F);
            } catch (Throwable ignored) {
            }
        }

        if (!stunned && patch != null) {
            try {
                stunned = patch.applyStun(StunType.LONG, 0.0F);
            } catch (Throwable ignored) {
            }
        }

        if (stunned) {
            state.postureState = PostureState.BROKEN;
        } else {
            // Never leave a zero-posture entity in NORMAL if both stun paths fail.
            startRecovering(state);
        }
    }

    private static boolean isEligiblePostureEntity(LivingEntity entity, LivingEntityPatch<?> targetPatch) {
        if (entity == null || entity instanceof Player || targetPatch == null) {
            return false;
        }
        if (PostureServerConfig.isBlacklisted(entityTypeId(entity))) {
            return false;
        }

        // Combat Evolution already owns posture for its native humanoid patch.
        if (targetPatch instanceof CEHumanoidPatch) {
            return false;
        }

        // Do not call CustomExecuteEntity.canBeExecuted here: executor context is unknown.
        if (targetPatch instanceof CustomExecuteEntity) {
            return true;
        }

        try {
            return targetPatch.getArmature() instanceof HumanoidArmature;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasNeutralizeAnimation(LivingEntityPatch<?> patch) {
        if (patch == null) {
            return false;
        }
        try {
            AssetAccessor<? extends StaticAnimation> neutralize = patch.getHitAnimation(StunType.NEUTRALIZE);
            if (neutralize == null || neutralize.isEmpty()) {
                return false;
            }
            AssetAccessor<? extends StaticAnimation> shortHit = patch.getHitAnimation(StunType.SHORT);
            AssetAccessor<? extends StaticAnimation> longHit = patch.getHitAnimation(StunType.LONG);
            return !sameAnimation(neutralize, shortHit) && !sameAnimation(neutralize, longHit);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean sameAnimation(
            AssetAccessor<? extends StaticAnimation> first,
            AssetAccessor<? extends StaticAnimation> second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        try {
            return Objects.equals(first.registryName(), second.registryName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static State state(LivingEntity entity) {
        State existing = STATES.get(entity);
        if (existing != null) {
            return existing;
        }
        State created = new State(calculateMaxPosture(entity));
        captureInputs(entity, created);
        STATES.put(entity, created);
        return created;
    }

    private static void refreshMaxPosture(LivingEntity entity, State state) {
        float maxHealth = finiteOr(entity.getMaxHealth(), EHP_REFERENCE_HEALTH);
        boolean useArmor = PostureServerConfig.useArmorScaling();
        float armor = useArmor ? finiteOr((float) entity.getAttributeValue(Attributes.ARMOR), 0.0F) : 0.0F;
        float toughness = useArmor ? finiteOr((float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0.0F) : 0.0F;
        float multiplier = finiteOr(PostureServerConfig.globalMultiplier(), 1.0F);
        float minimum = finiteOr(PostureServerConfig.minimumPosture(), 1.0F);

        if (state.inputsInitialized
                && Float.floatToIntBits(state.lastMaxHealth) == Float.floatToIntBits(maxHealth)
                && Float.floatToIntBits(state.lastArmor) == Float.floatToIntBits(armor)
                && Float.floatToIntBits(state.lastToughness) == Float.floatToIntBits(toughness)
                && Float.floatToIntBits(state.lastMultiplier) == Float.floatToIntBits(multiplier)
                && Float.floatToIntBits(state.lastMinimum) == Float.floatToIntBits(minimum)
                && state.lastUseArmorScaling == useArmor) {
            return;
        }

        captureInputs(maxHealth, armor, toughness, multiplier, minimum, useArmor, state);
        float newMax = calculateMaxPosture(maxHealth, armor, toughness, multiplier, minimum);
        if (Math.abs(newMax - state.max) < 0.01F) {
            return;
        }

        float ratio = state.max > 0.0F ? clamp(state.current / state.max, 0.0F, 1.0F) : 1.0F;
        state.max = newMax;
        if (!state.isLocked()) {
            state.current = newMax * ratio;
        }
    }

    private static void captureInputs(LivingEntity entity, State state) {
        boolean useArmor = PostureServerConfig.useArmorScaling();
        float maxHealth = finiteOr(entity.getMaxHealth(), EHP_REFERENCE_HEALTH);
        float armor = useArmor ? finiteOr((float) entity.getAttributeValue(Attributes.ARMOR), 0.0F) : 0.0F;
        float toughness = useArmor ? finiteOr((float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0.0F) : 0.0F;
        captureInputs(
                maxHealth,
                armor,
                toughness,
                finiteOr(PostureServerConfig.globalMultiplier(), 1.0F),
                finiteOr(PostureServerConfig.minimumPosture(), 1.0F),
                useArmor,
                state
        );
    }

    private static void captureInputs(
            float maxHealth,
            float armor,
            float toughness,
            float multiplier,
            float minimum,
            boolean useArmor,
            State state) {
        state.lastMaxHealth = maxHealth;
        state.lastArmor = armor;
        state.lastToughness = toughness;
        state.lastMultiplier = multiplier;
        state.lastMinimum = minimum;
        state.lastUseArmorScaling = useArmor;
        state.inputsInitialized = true;
    }

    private static void startRecovering(State state) {
        state.postureState = PostureState.RECOVERING;
        state.current = 0.0F;
        state.recoveryTicksRemaining = BREAK_RECOVERY_TICKS;
        state.regenDelay = 0;
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

    private static float calculateMaxPosture(LivingEntity entity) {
        boolean useArmor = PostureServerConfig.useArmorScaling();
        float maxHealth = finiteOr(entity.getMaxHealth(), EHP_REFERENCE_HEALTH);
        float armor = useArmor ? finiteOr((float) entity.getAttributeValue(Attributes.ARMOR), 0.0F) : 0.0F;
        float toughness = useArmor ? finiteOr((float) entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS), 0.0F) : 0.0F;
        return calculateMaxPosture(
                maxHealth,
                armor,
                toughness,
                finiteOr(PostureServerConfig.globalMultiplier(), 1.0F),
                finiteOr(PostureServerConfig.minimumPosture(), 1.0F)
        );
    }

    private static float calculateMaxPosture(
            float maxHealth,
            float armor,
            float toughness,
            float multiplier,
            float minimum) {
        if (maxHealth <= 0.0F) {
            maxHealth = EHP_REFERENCE_HEALTH;
        }
        float effectiveHealth = effectiveHealth(maxHealth, armor, toughness, EHP_REFERENCE_DAMAGE);
        if (!Float.isFinite(effectiveHealth) || effectiveHealth <= 0.0F) {
            effectiveHealth = EHP_REFERENCE_HEALTH;
        }
        float ratio = Math.max(0.01F, effectiveHealth / EHP_REFERENCE_HEALTH);
        float posture = EHP_REFERENCE_POSTURE * (float) Math.pow(ratio, EHP_EXPONENT);
        if (!Float.isFinite(posture) || posture <= 0.0F) {
            posture = EHP_REFERENCE_POSTURE;
        }
        float scaled = posture * multiplier;
        if (!Float.isFinite(scaled) || scaled <= 0.0F) {
            scaled = EHP_REFERENCE_POSTURE;
        }
        return clamp(Math.max(minimum, scaled), 0.1F, MAX_SAFE_POSTURE);
    }

    private static ResourceLocation entityTypeId(LivingEntity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
    }

    private static float effectiveHealth(float health, float armor, float toughness, float referenceDamage) {
        float fraction = damageFractionAfterArmor(referenceDamage, armor, toughness);
        if (fraction <= 1.0E-4F) {
            fraction = 1.0E-4F;
        }
        return health / fraction;
    }

    private static float damageFractionAfterArmor(float damage, float armor, float toughness) {
        float divisor = Math.max(0.01F, 2.0F + toughness / 4.0F);
        float armorPoints = clamp(armor - damage / divisor, armor * 0.2F, 20.0F);
        float reduction = armorPoints / 25.0F;
        return clamp(1.0F - reduction, 0.2F, 1.0F);
    }

    private static LivingEntityPatch<?> getPatch(LivingEntity entity) {
        try {
            return EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float finiteOr(float value, float fallback) {
        return Float.isFinite(value) ? value : fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private PostureManager() {
    }

    private static final class State {
        float max;
        float current;
        int regenDelay;
        int recoveryTicksRemaining;
        PostureState postureState = PostureState.NORMAL;

        boolean inputsInitialized;
        float lastMaxHealth;
        float lastArmor;
        float lastToughness;
        float lastMultiplier;
        float lastMinimum;
        boolean lastUseArmorScaling;

        State(float max) {
            this.max = max;
            this.current = max;
        }

        boolean isLocked() {
            return postureState == PostureState.BROKEN || postureState == PostureState.EXECUTING;
        }

        boolean isBrokenForHud() {
            return isLocked();
        }
    }

    private enum PostureState {
        NORMAL,
        BROKEN,
        EXECUTING,
        RECOVERING
    }

    public record SyncState(float current, float max, boolean broken) {
    }
}
