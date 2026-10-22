package dev.nire9734.epicfightposture.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.client.ClientPostureStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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
import net.shelmarow.combat_evolution.ai.CEHumanoidPatch;
import net.shelmarow.combat_evolution.execution.ExecutionHandler;

/**
 * Preserve Epic Fight's native overhead HP HUD, then append a posture strip
 * to that same world-space UI. Never replace Combat Evolution's owned HUD.
 */
@Mixin(value = HealthBar.class, remap = false, priority = 900)
public abstract class NeoForgeHealthBarMixin {
    @Inject(method = "shouldDraw", at = @At("RETURN"), cancellable = true)
    private void efpi$showDamagedPosture(LivingEntity entity, LivingEntityPatch<?> patch,
                                         LocalPlayerPatch playerPatch, float partialTicks,
                                         CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ() || !efpi$eligible(entity, patch, playerPatch)) return;
        if (ClientConfig.healthBarVisibility == ClientConfig.HealthBarVisibility.NONE
                || entity.isRemoved() || entity.getType().is(Tags.EntityTypes.BOSSES)) return;

        Player player = playerPatch.getOriginal();
        if (entity.isInvisibleTo(player) || entity == player.getVehicle()) return;
        if (entity instanceof Player target && (target.isCreative() || target.isSpectator())) return;
        if (entity == player && playerPatch.getMaxStunShield() <= 0.0F) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCameraEntity() == null
                || entity.distanceToSqr(minecraft.getCameraEntity()) >= 400.0D) return;

        ClientPostureStore.Entry state = ClientPostureStore.get(entity.getId(), entity.getUUID());
        if (state != null && (state.current < state.max || state.broken)) {
            result.setReturnValue(true);
        }
    }

    @Inject(method = "draw", at = @At("TAIL"))
    private void efpi$drawPosture(LivingEntity entity, LivingEntityPatch<?> patch,
                                  LocalPlayerPatch playerPatch, PoseStack poseStack,
                                  MultiBufferSource buffers, float partialTicks, CallbackInfo ci) {
        if (!efpi$eligible(entity, patch, playerPatch)) return;
        ClientPostureStore.Entry posture = ClientPostureStore.get(entity.getId(), entity.getUUID());
        if (posture == null) return;

        poseStack.pushPose();
        EntityUI.setupPoseStack(poseStack, entity,
                0.0F, entity.getBbHeight() + 0.25F, 0.0F, true, partialTicks);

        final float left = -0.5F;
        final float right = 0.5F;
        final float bottom = -0.135F;
        final float top = -0.102F;
        EntityUI.drawColoredQuadAsLevelModel(poseStack.last(), buffers,
                left, bottom, right, top, 0xC0202026);

        float fraction = posture.broken ? 1.0F : posture.percent();
        if (fraction > 0.0F) {
            float fillLeft = left + 0.012F;
            float fillRight = fillLeft + (right - left - 0.024F) * fraction;
            EntityUI.drawColoredQuadAsLevelModel(poseStack.last(), buffers,
                    fillLeft, bottom + 0.006F, fillRight, top - 0.006F,
                    posture.broken ? 0xFFE24B49 : 0xFFD9B168);
        }
        poseStack.popPose();
    }

    @Unique
    private static boolean efpi$eligible(LivingEntity entity, LivingEntityPatch<?> target,
                                         LocalPlayerPatch executor) {
        if (entity == null || target == null || executor == null
                || target instanceof CEHumanoidPatch) return false;
        try {
            // A server snapshot confirms posture support; CE still decides if
            // this executor is compatible with the target's execution contract.
            return ExecutionHandler.isTargetSupported(executor, target);
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
