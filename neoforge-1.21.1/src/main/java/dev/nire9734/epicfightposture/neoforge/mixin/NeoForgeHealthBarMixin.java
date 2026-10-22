package dev.nire9734.epicfightposture.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.client.ClientPostureStore;
import dev.nire9734.epicfightposture.client.PostureHudPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.Tags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import yesman.epicfight.client.gui.EntityUI;
import yesman.epicfight.client.gui.HealthBar;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.config.ClientConfig;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.effect.VisibleMobEffect;
import net.shelmarow.combat_evolution.ai.CEHumanoidPatch;
import net.shelmarow.combat_evolution.ai.StaminaStatus;
import net.shelmarow.combat_evolution.ai.util.CEPatchUtils;
import net.shelmarow.combat_evolution.execution.ExecutionHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Render the same CE-textured health/posture HUD as Forge on EFPI targets.
 * Leave Combat Evolution's CEHumanoidPatch HUD alone. Cancel Epic Fight's draw
 * on EFPI targets so bosses without a native health tracker render safely.
 */
@Mixin(value = HealthBar.class, remap = false, priority = 900)
public abstract class NeoForgeHealthBarMixin {
    @Unique
    private static final ResourceLocation EFPI$HEALTH_BAR = ResourceLocation.fromNamespaceAndPath(
            "combat_evolution", "textures/gui/bossbar/ce_health_bar.png");

    @Inject(method = "shouldDraw", at = @At("RETURN"), cancellable = true)
    private void efpi$showOnlyAfterMobIsHurt(LivingEntity entity, LivingEntityPatch<?> patch,
                                         LocalPlayerPatch playerPatch, float partialTicks,
                                         CallbackInfoReturnable<Boolean> result) {
        if (entity instanceof Player) return;

        ClientPostureStore.Entry state = ClientPostureStore.get(entity.getId(), entity.getUUID());
        boolean postureDamaged = state != null && (state.current < state.max || state.broken);
        boolean ceStaminaDamaged = patch instanceof CEHumanoidPatch<?> cePatch
                && efpi$hasDamagedCeStamina(cePatch);
        if (!PostureHudPolicy.hasCombatDamage(entity.getHealth(), entity.getMaxHealth(),
                entity.hurtTime, postureDamaged || ceStaminaDamaged)) {
            result.setReturnValue(false);
            return;
        }

        if (result.getReturnValueZ()) return;
        if (ClientConfig.healthBarVisibility == ClientConfig.HealthBarVisibility.NONE
                || entity.isRemoved()) return;

        Player player = playerPatch.getOriginal();
        if (entity.isInvisibleTo(player) || entity == player.getVehicle()) return;
        if (entity instanceof Player target && (target.isCreative() || target.isSpectator())) return;
        if (entity == player && playerPatch.getMaxStunShield() <= 0.0F) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCameraEntity() == null
                || entity.distanceToSqr(minecraft.getCameraEntity()) >= 400.0D) return;

        if (patch instanceof CEHumanoidPatch<?> cePatch) {
            if (!cePatch.shouldDisplayHealthBar()) return;
        } else if (entity.getType().is(Tags.EntityTypes.BOSSES)
                && (state == null || !efpi$eligible(patch, playerPatch))) {
            // Native HealthBar.draw() needs a tracker absent on boss-tagged mobs.
            return;
        }

        // Preserve the native HURT path when available, with a fallback for
        // posture-only damage and bosses managed by CE or EFPI.
        result.setReturnValue(true);
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void efpi$drawCeStyledPosture(LivingEntity entity, LivingEntityPatch<?> patch,
                                          LocalPlayerPatch playerPatch, PoseStack poseStack,
                                          MultiBufferSource buffers, float partialTicks,
                                          CallbackInfo ci) {
        if (!efpi$eligible(patch, playerPatch)) return;
        ClientPostureStore.Entry posture = ClientPostureStore.get(entity.getId(), entity.getUUID());
        if (posture == null) return;

        // For bosses, Epic Fight's shouldDraw() doesn't create its health
        // tracker, so its native draw() cannot be called after our override.
        ci.cancel();
        poseStack.pushPose();
        try {
            EntityUI.setupPoseStack(poseStack, entity,
                    0.0F, entity.getBbHeight() + 0.25F, 0.0F, true, partialTicks);
            efpi$drawEffects(entity, playerPatch, poseStack, buffers);

            float healthPercent = efpi$clamp(entity.getHealth() / Math.max(entity.getMaxHealth(), 1.0E-4F));
            float posturePercent = posture.percent();
            float scale = 1.0F;

            int textureLeft = 3;
            int textureRight = 83;
            int textureWidth = textureRight - textureLeft;
            int healthLeft = 6;
            int healthRight = 80;
            int healthWidthPx = healthRight - healthLeft;

            float healthBarHeight = scale * 20.0F / textureWidth;
            float healthBarX = -scale / 2.0F;
            float healthFillWidth = scale * (float) healthWidthPx / textureWidth * healthPercent;
            float healthFillOffset = scale * (float) (healthLeft - textureLeft) / textureWidth;
            float healthFillX = healthBarX + healthFillOffset;
            int healthFillEndU = (int) (healthLeft + healthWidthPx * healthPercent);

            EntityUI.drawUIAsLevelModel(poseStack.last(), EFPI$HEALTH_BAR, buffers,
                    healthBarX, -healthBarHeight / 2.0F,
                    healthBarX + scale, healthBarHeight / 2.0F,
                    textureLeft, 0, textureRight, 20, 256);
            EntityUI.drawUIAsLevelModel(poseStack.last(), EFPI$HEALTH_BAR, buffers,
                    healthFillX, -healthBarHeight / 2.0F,
                    healthFillX + healthFillWidth, healthBarHeight / 2.0F,
                    healthLeft, 21, healthFillEndU, 41, 256);

            int postureBgLeft = 6;
            int postureBgRight = 80;
            int postureBgWidthPx = postureBgRight - postureBgLeft;
            int postureFillLeft = 9;
            int postureFillRight = 77;
            int postureFillWidthPx = postureFillRight - postureFillLeft;

            float postureBarWidth = scale * (float) postureBgWidthPx / textureWidth;
            float postureBarHeight = postureBarWidth * 20.0F / postureBgWidthPx;
            float postureBarX = -postureBarWidth / 2.0F;
            float postureFillWidth = postureBarWidth * (float) postureFillWidthPx / postureBgWidthPx * posturePercent;
            float postureFillOffset = postureBarWidth * (float) (postureFillLeft - postureBgLeft) / postureBgWidthPx;
            float postureFillX = postureBarX + postureFillOffset;
            int postureFillEndU = (int) (postureFillLeft + postureFillWidthPx * posturePercent);

            EntityUI.drawUIAsLevelModel(poseStack.last(), EFPI$HEALTH_BAR, buffers,
                    postureBarX, -postureBarHeight / 2.0F,
                    postureBarX + postureBarWidth, postureBarHeight / 2.0F,
                    postureBgLeft, 42, postureBgRight, 62, 256);

            if (!posture.broken) {
                EntityUI.drawUIAsLevelModel(poseStack.last(), EFPI$HEALTH_BAR, buffers,
                        postureFillX, -postureBarHeight / 2.0F,
                        postureFillX + postureFillWidth, postureBarHeight / 2.0F,
                        postureFillLeft, 63, postureFillEndU, 83, 256);
            } else {
                float fullBrokenWidth = postureBarWidth * (float) postureFillWidthPx / postureBgWidthPx;
                EntityUI.drawUIAsLevelModel(poseStack.last(), EFPI$HEALTH_BAR, buffers,
                        postureFillX, -postureBarHeight / 2.0F,
                        postureFillX + fullBrokenWidth, postureBarHeight / 2.0F,
                        postureFillLeft, 84, postureFillRight, 104, 256);
            }
        } finally {
            poseStack.popPose();
        }
    }

    @Unique
    private static void efpi$drawEffects(LivingEntity entity, LocalPlayerPatch playerPatch,
                                         PoseStack poseStack, MultiBufferSource buffers) {
        if (entity == playerPatch.getOriginal()) return;
        List<MobEffectInstance> visibleEffects = new ArrayList<>();
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.showIcon()) visibleEffects.add(instance);
        }

        int count = visibleEffects.size();
        if (count == 0) return;
        int columnsMinusOne = count > 1 ? 1 : 0;
        int rowsMinusOne = (count - 1) / 2;
        float baseX = -0.8F - 0.3F * columnsMinusOne;
        float baseY = -0.15F + 0.15F * rowsMinusOne;

        for (int i = 0; i < count; i++) {
            MobEffectInstance instance = visibleEffects.get(i);
            MobEffect effect = instance.getEffect().value();
            ResourceLocation icon;
            if (effect instanceof VisibleMobEffect visibleMobEffect) {
                icon = visibleMobEffect.getIcon(instance);
            } else {
                ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
                if (key == null) continue;
                icon = ResourceLocation.fromNamespaceAndPath(
                        key.getNamespace(), "textures/mob_effect/" + key.getPath() + ".png");
            }
            if (icon == null) continue;
            float x = baseX + 0.3F * (i % 2);
            float y = baseY - 0.3F * (i / 2);
            EntityUI.drawUIAsLevelModel(poseStack.last(), icon, buffers,
                    x, y, x + 0.3F, y + 0.3F, 0, 0, 256, 256, 256);
        }
    }

    @Unique
    private static boolean efpi$hasDamagedCeStamina(CEHumanoidPatch<?> patch) {
        try {
            StaminaStatus status = patch.getStaminaStatus();
            if (status == StaminaStatus.BREAK || status == StaminaStatus.RECOVER) return true;
            float percent = CEPatchUtils.getStaminaPercent(patch);
            // CE may report zero before initial stamina synchronization.
            return status != StaminaStatus.EMPTY && percent > 0.0F && percent < 1.0F;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Unique
    private static boolean efpi$eligible(LivingEntityPatch<?> target, LocalPlayerPatch executor) {
        if (target == null || executor == null || target instanceof CEHumanoidPatch) return false;
        try {
            // A server snapshot confirms posture support; CE still decides if
            // this executor is compatible with the target's execution contract.
            return ExecutionHandler.isTargetSupported(executor, target);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Unique
    private static float efpi$clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
