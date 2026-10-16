package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.client.FrameSpikeDiagnostics;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Prepares a source LivingEntityRenderer model once for all EFPI attachment/layer
 * replay work in one special-state render, then restores the original ModelPart state.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class SourceModelPreparation {
    public static Scope open(
            LivingEntity entity,
            EntityRenderer<LivingEntity> renderer,
            float partialTicks) {
        if (!(renderer instanceof LivingEntityRenderer livingRenderer)) {
            return Scope.NOOP;
        }

        EntityModel model = livingRenderer.getModel();
        if (model == null) {
            return Scope.NOOP;
        }

        List<PartState> saved = save(model);
        long startedNs = FrameSpikeDiagnostics.startTimedSection();
        try {
            prepare(entity, model, partialTicks);
        } finally {
            FrameSpikeDiagnostics.finishSourceModelPrepare(startedNs);
        }
        return new Scope(saved, true);
    }

    private static void prepare(LivingEntity entity, EntityModel model, float partialTicks) {
        if (model instanceof HierarchicalModel<?> hierarchical) {
            hierarchical.root().getAllParts().forEach(ModelPart::resetPose);
        }

        boolean shouldSit = entity.isPassenger()
                && entity.getVehicle() != null
                && entity.getVehicle().shouldRiderSit();
        model.riding = shouldSit;
        model.young = entity.isBaby();

        float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        float netHeadYaw = headYaw - bodyYaw;
        if (shouldSit && entity.getVehicle() instanceof LivingEntity vehicle) {
            bodyYaw = Mth.rotLerp(partialTicks, vehicle.yBodyRotO, vehicle.yBodyRot);
            netHeadYaw = headYaw - bodyYaw;
            float wrapped = Mth.clamp(Mth.wrapDegrees(netHeadYaw), -85.0F, 85.0F);
            bodyYaw = headYaw - wrapped;
            if (wrapped * wrapped > 2500.0F) {
                bodyYaw += wrapped * 0.2F;
            }
            netHeadYaw = headYaw - bodyYaw;
        }

        float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        if (LivingEntityRenderer.isEntityUpsideDown(entity)) {
            headPitch *= -1.0F;
            netHeadYaw *= -1.0F;
        }

        float limbSwingAmount = 0.0F;
        float limbSwing = 0.0F;
        if (!shouldSit && entity.isAlive()) {
            limbSwingAmount = Math.min(1.0F, entity.walkAnimation.speed(partialTicks));
            limbSwing = entity.walkAnimation.position() - entity.walkAnimation.speed() * (1.0F - partialTicks);
            if (entity.isBaby()) {
                limbSwing *= 3.0F;
            }
        }

        float ageInTicks = entity.tickCount + partialTicks;
        model.attackTime = entity.getAttackAnim(partialTicks);
        model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
        model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }

    private static List<PartState> save(EntityModel<?> model) {
        if (!(model instanceof HierarchicalModel<?> hierarchical)) {
            return List.of();
        }

        List<PartState> saved = new ArrayList<>();
        hierarchical.root().getAllParts().forEach(part -> saved.add(new PartState(
                part,
                part.storePose(),
                part.visible,
                part.skipDraw
        )));
        return saved;
    }

    private static void restore(List<PartState> saved) {
        for (PartState state : saved) {
            state.part().loadPose(state.pose());
            state.part().visible = state.visible();
            state.part().skipDraw = state.skipDraw();
        }
    }

    private record PartState(ModelPart part, PartPose pose, boolean visible, boolean skipDraw) {
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope(List.of(), false);

        private final List<PartState> saved;
        private final boolean active;
        private boolean closed;

        private Scope(List<PartState> saved, boolean active) {
            this.saved = saved;
            this.active = active;
        }

        public boolean active() {
            return this.active;
        }

        @Override
        public void close() {
            if (!this.active || this.closed) {
                return;
            }
            this.closed = true;
            restore(this.saved);
        }
    }

    private SourceModelPreparation() {
    }
}
