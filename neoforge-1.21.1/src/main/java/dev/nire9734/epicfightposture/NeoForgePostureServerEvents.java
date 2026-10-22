package dev.nire9734.epicfightposture;

import java.util.Map;
import java.util.WeakHashMap;
import dev.nire9734.epicfightposture.neoforge.NeoForgePostureConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import net.shelmarow.combat_evolution.api.event.OnExecutionStartEvent;
import net.shelmarow.combat_evolution.execution.ExecutionHandler;

/**
 * Stage 2 NeoForge server adapter. No HUD, networking, parry or Combat
 * Evolution execution listeners; not safe for release.
 */
public final class NeoForgePostureServerEvents {
    private static final int RECOVERY_TICKS = 60;
    // Combat Evolution schedules its ExecutionTask after OnExecutionStartEvent.
    // Allow the task to become active before deciding an assassination ended.
    private static final int EXECUTION_START_GRACE_TICKS = 5;
    private static final Map<Mob, PostureRuntimeState> STATES = new WeakHashMap<>();
    private static final Map<Mob, Integer> PENDING_EXECUTION_START = new WeakHashMap<>();
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(NeoForgePostureServerEvents::onDamage);
        NeoForge.EVENT_BUS.addListener(NeoForgePostureServerEvents::onTick);
        NeoForge.EVENT_BUS.addListener(NeoForgePostureServerEvents::onExecutionStart);
        dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.register();
    }

    // NeoForge fires Post only after applying health loss.
    private static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel)
                || !canReceive(mob) || isBlacklisted(mob)) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof Player player) || event.getSource().getDirectEntity() != player) return;
        float loss = event.getNewDamage();
        if (!Float.isFinite(loss) || loss <= 0.0F) return;

        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, LivingEntityPatch.class);
        if (patch instanceof CEHumanoidPatch || !hasDistinctNeutralize(patch)) return;
        PostureRuntimeState state = STATES.computeIfAbsent(mob,
                target -> new PostureRuntimeState(maximumPosture(target)));
        if (!state.acceptsPostureDamage()) return;

        float impact = 3.0F;
        LivingEntityPatch<?> playerPatch = EpicFightCapabilities.getEntityPatch(player, LivingEntityPatch.class);
        if (playerPatch != null) impact = playerPatch.getImpact(InteractionHand.MAIN_HAND);
        float maxHealth = mob.getMaxHealth();
        float before = Math.min(maxHealth, mob.getHealth() + loss);
        float pressure = PostureBalance.attackPostureDamage(
                basePosture(mob), maxHealth, before, loss, impact,
                .54F * NeoForgePostureConfig.POSTURE_DAMAGE_MULTIPLIER.get().floatValue());
        if (!Float.isFinite(pressure) || pressure <= 0.0F) return;

        float resistance = PostureBalance.breakResistanceMultiplier(
                state.completedBreaks,
                NeoForgePostureConfig.POSTURE_BREAK_RESISTANCE_STEP.get().floatValue(),
                NeoForgePostureConfig.POSTURE_BREAK_RESISTANCE_MAX.get().floatValue());
        int delay = PostureBalance.regenDelayTicks(
                NeoForgePostureConfig.REGEN_DELAY_SECONDS.get().floatValue());
        if (state.applyDamage(pressure, resistance, delay)) {
            state.prepareBreak(delay);
            boolean applied = patch.applyStun(StunType.NEUTRALIZE, 0.0F);
            if (applied && isActiveNeutralize(patch)) state.confirmBreak();
            else state.beginRecovery(RECOVERY_TICKS);
        }
        dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncNow(mob, stateForSync(mob));
    }

    private static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel)) return;
        PostureRuntimeState state = STATES.get(mob);
        if (state == null) return;
        if (!canReceive(mob) || isBlacklisted(mob)) {
            STATES.remove(mob);
            PENDING_EXECUTION_START.remove(mob);
            dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.removeNow(mob);
            return;
        }
        if (mob.tickCount % 10 == 0) {
            float max = maximumPosture(mob);
            if (Math.abs(max - state.max) >= .01F) state.updateMaximum(max);
        }
        if (state.postureState == PostureState.BROKEN || state.postureState == PostureState.EXECUTING) {
            if (ExecutionHandler.isExecutingTarget(mob, mob)) {
                PENDING_EXECUTION_START.remove(mob);
                if (state.postureState != PostureState.EXECUTING) state.enterExecution();
                state.current = 0.0F;
                dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncPeriodic(mob, stateForSync(mob));
                return;
            }
            if (state.postureState == PostureState.EXECUTING) {
                Integer activationDeadline = PENDING_EXECUTION_START.get(mob);
                if (activationDeadline != null && mob.tickCount <= activationDeadline) {
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
            if (isActiveNeutralize(patch)) state.brokenTicks++;
            else state.beginRecovery(RECOVERY_TICKS);
        } else if (state.postureState == PostureState.RECOVERING) {
            state.advanceRecovery(RECOVERY_TICKS);
        } else if (state.postureState == PostureState.NORMAL) {
            state.advanceRegeneration(NeoForgePostureConfig.REGEN_PERCENT_PER_SECOND.get().floatValue() / 100.0F);
        }
        dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncPeriodic(mob, stateForSync(mob));
    }


    /** Called exactly once by the Epic Fight parry event's setParried(true) hook. */
    public static void onParried(LivingEntity attacker) {
        if (!(attacker instanceof Mob mob) || !(mob.level() instanceof ServerLevel)
                || !canReceive(mob) || isBlacklisted(mob)) return;
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, LivingEntityPatch.class);
        if (patch instanceof CEHumanoidPatch || !hasDistinctNeutralize(patch)) return;

        PostureRuntimeState state = STATES.computeIfAbsent(mob,
                target -> new PostureRuntimeState(maximumPosture(target)));
        if (!state.acceptsPostureDamage()) return;
        float innateEhp = innateEhp(mob);
        float damage = PostureBalance.parryDamage(state.max,
                NeoForgePostureConfig.PARRY_BASE_MAX_PERCENT.get().floatValue() / 100.0F,
                NeoForgePostureConfig.PARRY_BASE_MIN_PERCENT.get().floatValue() / 100.0F,
                innateEhp, 20.0F, .7F);
        float resistance = PostureBalance.breakResistanceMultiplier(
                state.completedBreaks,
                NeoForgePostureConfig.POSTURE_BREAK_RESISTANCE_STEP.get().floatValue(),
                NeoForgePostureConfig.POSTURE_BREAK_RESISTANCE_MAX.get().floatValue());
        int delay = PostureBalance.regenDelayTicks(
                NeoForgePostureConfig.REGEN_DELAY_SECONDS.get().floatValue());
        if (state.applyDamage(damage, resistance, delay)) {
            state.prepareBreak(delay);
            boolean applied = patch.applyStun(StunType.NEUTRALIZE, 0.0F);
            if (applied && isActiveNeutralize(patch)) state.confirmBreak();
            else state.beginRecovery(RECOVERY_TICKS);
        }
        dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncNow(mob, stateForSync(mob));
    }

    private static void onExecutionStart(OnExecutionStartEvent event) {
        Object original = event.getTarget().getOriginal();
        if (!(original instanceof Mob mob) || !(mob.level() instanceof ServerLevel)
                || event.isCanceled() || !canReceive(mob) || isBlacklisted(mob)) return;

        LivingEntityPatch<?> patch = event.getTarget();
        if (patch instanceof CEHumanoidPatch || !hasDistinctNeutralize(patch)) return;

        // Assassinations start on a healthy, never-hit mob. The old get() gate
        // skipped those targets entirely, so no EXECUTING snapshot reached
        // the client and the special renderer stayed disabled.
        PostureRuntimeState state = STATES.computeIfAbsent(mob,
                target -> new PostureRuntimeState(maximumPosture(target)));
        mob.stopUsingItem();
        state.startExecution();
        PENDING_EXECUTION_START.put(mob, mob.tickCount + EXECUTION_START_GRACE_TICKS);
        dev.nire9734.epicfightposture.neoforge.network.NeoForgePostureSync.syncNow(mob, stateForSync(mob));
    }

    @SuppressWarnings("unchecked")
    private static float innateEhp(Mob mob) {
        EntityType<? extends LivingEntity> type = (EntityType<? extends LivingEntity>) mob.getType();
        AttributeSupplier defaults = DefaultAttributes.hasSupplier(type)
                ? DefaultAttributes.getSupplier(type) : null;
        float hp = baseline(mob, defaults, Attributes.MAX_HEALTH, 20.0F);
        float armor = baseline(mob, defaults, Attributes.ARMOR, 0.0F);
        float toughness = baseline(mob, defaults, Attributes.ARMOR_TOUGHNESS, 0.0F);
        return PostureBalance.effectiveHealth(hp > 0.0F ? hp : 20.0F, armor, toughness, 8.0F);
    }

    private static float baseline(Mob mob, AttributeSupplier defaults,
                                  net.minecraft.core.Holder<Attribute> attribute, float fallback) {
        AttributeInstance current = mob.getAttribute(attribute);
        float originalBase = current == null ? Float.NaN : (float) current.getBaseValue();
        float typeDefault = defaults != null && defaults.hasAttribute(attribute)
                ? (float) defaults.getBaseValue(attribute) : Float.NaN;
        return PostureBalance.innateAttributeValue(typeDefault, originalBase, fallback);
    }

    /** Used by StartTracking to send the authoritative state to new viewers. */
    public record SyncState(float current, float max, boolean broken) {}

    public static SyncState stateForSync(Mob mob) {
        PostureRuntimeState state = STATES.get(mob);
        return state == null ? null : new SyncState(state.current, state.max, state.isBrokenForHud());
    }

    /** Clear per-mob posture state when the entity leaves the server level. */
    public static void forget(Mob mob) {
        STATES.remove(mob);
        PENDING_EXECUTION_START.remove(mob);
    }

    private static boolean canReceive(LivingEntity target) {
        return PostureDeathSafety.canReceivePosture(target.isAlive(), target.getHealth());
    }

    private static boolean isBlacklisted(Mob mob) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return id != null && NeoForgePostureConfig.ENTITY_BLACKLIST.get().contains(id.toString());
    }

    private static float basePosture(Mob mob) {
        float ehp = NeoForgeEntityAttributeAdapter.liveEffectiveHealth(mob);
        float ratio = Math.max(.01F, ehp / 20.0F);
        return Math.max(.1F, Math.min(1_000_000.0F,
                3.2F * (float) Math.pow(ratio, .7D)));
    }

    private static float maximumPosture(Mob mob) {
        float multiplier = NeoForgePostureConfig.MAXIMUM_POSTURE_MULTIPLIER.get().floatValue();
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (id != null) {
            String prefix = id.toString() + "=";
            for (String entry : NeoForgePostureConfig.POSTURE_MULTIPLIERS.get()) {
                if (!entry.startsWith(prefix)) continue;
                try {
                    float value = Float.parseFloat(entry.substring(prefix.length()));
                    if (Float.isFinite(value) && value >= .01F && value <= 100.0F) multiplier = value;
                } catch (NumberFormatException ignored) {
                    // Invalid overrides are ignored.
                }
            }
        }
        return Math.max(1.0F, Math.min(1_000_000.0F, basePosture(mob) * multiplier));
    }

    private static boolean hasDistinctNeutralize(LivingEntityPatch<?> patch) {
        if (patch == null) return false;
        NeoForgeMissingHumanoidNeutralize.installIfNeeded(patch);
        try {
            AssetAccessor<? extends StaticAnimation> neutral = patch.getHitAnimation(StunType.NEUTRALIZE);
            return neutral != null && !neutral.isEmpty()
                    && !sameAnimation(neutral, patch.getHitAnimation(StunType.SHORT))
                    && !sameAnimation(neutral, patch.getHitAnimation(StunType.LONG));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean isActiveNeutralize(LivingEntityPatch<?> patch) {
        if (!hasDistinctNeutralize(patch)) return false;
        try {
            var current = patch.getAnimator().getPlayerFor(null);
            return current != null && current.getRealAnimation() == patch.getHitAnimation(StunType.NEUTRALIZE);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean sameAnimation(AssetAccessor<?> first, AssetAccessor<?> second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        ResourceLocation a = first.registryName(), b = second.registryName();
        return a != null && a.equals(b);
    }

    private NeoForgePostureServerEvents() {}
}
