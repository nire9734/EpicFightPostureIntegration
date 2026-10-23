package dev.nire9734.epicfightposture.compat.humanoid.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** Shared vanilla animation inputs for temporarily replayed source model layers. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class SourceModelAnimationPose {
    public static void apply(LivingEntity entity, EntityModel model, float partialTicks) {
        boolean riding = entity.isPassenger() && entity.getVehicle() != null
                && entity.getVehicle().shouldRiderSit();
        model.riding = riding;
        model.young = entity.isBaby();

        float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = headYaw - bodyYaw;
        if (riding && entity.getVehicle() instanceof LivingEntity vehicle) {
            bodyYaw = Mth.rotLerp(partialTicks, vehicle.yBodyRotO, vehicle.yBodyRot);
            netHeadYaw = headYaw - bodyYaw;
            float wrapped = Mth.clamp(Mth.wrapDegrees(netHeadYaw), -85.0F, 85.0F);
            bodyYaw = headYaw - wrapped;
            if (wrapped * wrapped > 2500.0F) bodyYaw += wrapped * 0.2F;
            netHeadYaw = headYaw - bodyYaw;
        }

        float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        if (LivingEntityRenderer.isEntityUpsideDown(entity)) {
            pitch *= -1.0F;
            netHeadYaw *= -1.0F;
        }

        float limbAmount = 0.0F;
        float limb = 0.0F;
        if (!riding && entity.isAlive()) {
            limbAmount = Math.min(1.0F, entity.walkAnimation.speed(partialTicks));
            limb = entity.walkAnimation.position()
                    - entity.walkAnimation.speed() * (1.0F - partialTicks);
            if (entity.isBaby()) limb *= 3.0F;
        }

        model.attackTime = entity.getAttackAnim(partialTicks);
        model.prepareMobModel(entity, limb, limbAmount, partialTicks);
        model.setupAnim(entity, limb, limbAmount, entity.tickCount + partialTicks,
                netHeadYaw, pitch);
    }

    private SourceModelAnimationPose() { }
}
