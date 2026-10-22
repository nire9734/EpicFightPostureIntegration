package dev.nire9734.epicfightposture.neoforge.compat.models;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * Temporarily prepares a source ModelPart or Lionfish model for dynamic
 * attachment and layer replay, then restores its complete mutable state.
 *
 * Never runs arbitrary source model animation without a restoration scope.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class NeoForgeSourceModelPreparation {
    private static final String LIONFISH_MODEL_CLASS =
            "com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel";

    public static Scope open(LivingEntity entity, EntityRenderer<LivingEntity> renderer,
                             float partialTicks) {
        if (!(renderer instanceof LivingEntityRenderer living)
                || living.getModel() == null) {
            return Scope.NOOP;
        }
        EntityModel source = living.getModel();
        Scope scope = snapshot(source);
        if (!scope.active()) return scope;
        try {
            if (source instanceof HierarchicalModel<?> hierarchical) {
                hierarchical.root().getAllParts().forEach(ModelPart::resetPose);
            }
            boolean riding = entity.isPassenger() && entity.getVehicle() != null
                    && entity.getVehicle().shouldRiderSit();
            source.riding = riding;
            source.young = entity.isBaby();
            source.attackTime = entity.getAttackAnim(partialTicks);

            float bodyYaw = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
            float headYaw = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
            float netHeadYaw = headYaw - bodyYaw;
            // Match Forge's LivingEntityRenderer pose preparation when the
            // source mob is riding another living entity: its body faces the
            // mount and its head turn is clamped around that new body yaw.
            if (riding && entity.getVehicle() instanceof LivingEntity vehicle) {
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
            float limbAmount = !riding && entity.isAlive()
                    ? Math.min(1.0F, entity.walkAnimation.speed(partialTicks)) : 0.0F;
            float limb = !riding && entity.isAlive()
                    ? entity.walkAnimation.position() - entity.walkAnimation.speed() * (1.0F - partialTicks)
                    : 0.0F;
            if (entity.isBaby()) limb *= 3.0F;
            source.prepareMobModel(entity, limb, limbAmount, partialTicks);
            source.setupAnim(entity, limb, limbAmount, entity.tickCount + partialTicks,
                    netHeadYaw, headPitch);
            return scope;
        } catch (RuntimeException | Error failure) {
            try { scope.close(); } catch (RuntimeException | Error restoreError) {
                failure.addSuppressed(restoreError);
            }
            throw failure;
        }
    }

    private static Scope snapshot(EntityModel<?> model) {
        List<Runnable> states = new ArrayList<>();
        if (model instanceof HierarchicalModel<?> hierarchical) {
            hierarchical.root().getAllParts().forEach(part -> states.add(new PartState(
                    part, part.storePose(), part.visible, part.skipDraw,
                    part.xScale, part.yScale, part.zScale)));
        } else if (isLionfishModel(model.getClass())) {
            // The common renderer must not load an optional API on clients
            // without Lionfish. If capture is unavailable, do not animate.
            List<Runnable> captured = captureLionfishState(model);
            if (captured == null || captured.isEmpty()) return Scope.NOOP;
            states.addAll(captured);
        } else {
            return Scope.NOOP;
        }
        return new Scope(model, model.riding, model.young, model.attackTime, states);
    }

    private static boolean isLionfishModel(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (LIONFISH_MODEL_CLASS.equals(current.getName())) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<Runnable> captureLionfishState(EntityModel<?> model) {
        try {
            Class<?> helper = Class.forName(
                    "dev.nire9734.epicfightposture.neoforge.compat.models.LionfishSourceModelState");
            Method capture = helper.getDeclaredMethod("capture", EntityModel.class);
            capture.setAccessible(true);
            Object result = capture.invoke(null, model);
            if (!(result instanceof List<?> list)
                    || list.stream().anyMatch(value -> !(value instanceof Runnable))) {
                return null;
            }
            return (List<Runnable>) list;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            return null;
        }
    }

    private record PartState(ModelPart part, PartPose pose, boolean visible, boolean skipDraw,
                             float sx, float sy, float sz) implements Runnable {
        @Override public void run() {
            part.loadPose(pose);
            part.visible = visible;
            part.skipDraw = skipDraw;
            part.xScale = sx;
            part.yScale = sy;
            part.zScale = sz;
        }
    }

    public static final class Scope implements AutoCloseable {
        static final Scope NOOP = new Scope(null, false, false, 0.0F, List.of());
        private final EntityModel<?> model;
        private final boolean riding, young;
        private final float attackTime;
        private final List<Runnable> saved;
        private boolean closed;

        private Scope(EntityModel<?> model, boolean riding, boolean young,
                      float attackTime, List<Runnable> saved) {
            this.model = model;
            this.riding = riding;
            this.young = young;
            this.attackTime = attackTime;
            this.saved = List.copyOf(saved);
        }
        public boolean active() { return model != null; }
        @Override public void close() {
            if (closed || model == null) return;
            closed = true;
            Throwable failure = null;
            for (Runnable state : saved) {
                try { state.run(); }
                catch (Throwable thrown) {
                    if (failure == null) failure = thrown;
                    else if (thrown != failure) failure.addSuppressed(thrown);
                }
            }
            model.riding = riding;
            model.young = young;
            model.attackTime = attackTime;
            if (failure != null) throw new IllegalStateException(
                    "Could not restore source model after EFPI layer replay", failure);
        }
    }

    private NeoForgeSourceModelPreparation() {}
}
