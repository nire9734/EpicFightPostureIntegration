package dev.nire9734.epicfightposture.compat.humanoid.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Prepares a source model for EFPI attachment/layer replay, restoring its original
 * pose, visibility and EntityModel animation fields on every exit path.
 *
 * <p>Lionfish support is loaded only for actual AdvancedEntityModel instances:
 * this class must also load on clients without the optional Lionfish API.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class SourceModelPreparation {
    private static final String LIONFISH_MODEL_CLASS =
            "com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel";

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

        return openPrepared(model, () -> prepare(entity, model, partialTicks));
    }

    /**
     * Capture first and prepare second. A failure during preparation must restore
     * the model even though try-with-resources at the caller has no Scope yet.
     */
    static Scope openPrepared(EntityModel<?> model, Runnable preparation) {
        Scope scope = snapshot(model);
        try {
            preparation.run();
            return scope;
        } catch (Throwable failure) {
            try {
                scope.close();
            } catch (Throwable restoreFailure) {
                if (restoreFailure != failure) {
                    failure.addSuppressed(restoreFailure);
                }
            }
            throw failure;
        }
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

    static Scope snapshot(EntityModel<?> model) {
        if (model == null) {
            return Scope.NOOP;
        }

        List<Runnable> saved = new ArrayList<>();
        if (model instanceof HierarchicalModel<?> hierarchical) {
            saveModelParts(List.of(hierarchical.root()), saved);
        } else if (model instanceof ListModel<?> listed) {
            saveModelParts(listed.parts(), saved);
        } else if (isLionfishModel(model.getClass())) {
            // Do not resolve optional Lionfish classes in the common render path.
            saved.addAll(LionfishSourceModelState.capture(model));
        }
        return new Scope(model, model.riding, model.young, model.attackTime, saved);
    }

    private static boolean isLionfishModel(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (LIONFISH_MODEL_CLASS.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    private static void saveModelParts(Iterable<ModelPart> roots, List<Runnable> saved) {
        Set<ModelPart> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelPart root : roots) {
            if (root == null) {
                continue;
            }
            root.getAllParts().forEach(part -> {
                if (visited.add(part)) {
                    saved.add(new PartState(
                            part, part.storePose(), part.visible, part.skipDraw,
                            part.xScale, part.yScale, part.zScale
                    ));
                }
            });
        }
    }

    private record PartState(ModelPart part, PartPose pose, boolean visible, boolean skipDraw,
                             float scaleX, float scaleY, float scaleZ) implements Runnable {
        @Override
        public void run() {
            part.loadPose(pose);
            // Minecraft 1.20.1 PartPose stores translation/rotation, not scale.
            part.xScale = scaleX;
            part.yScale = scaleY;
            part.zScale = scaleZ;
            part.visible = visible;
            part.skipDraw = skipDraw;
        }
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope NOOP = new Scope(null, false, false, 0.0F, List.of());

        private final EntityModel<?> model;
        private final boolean riding;
        private final boolean young;
        private final float attackTime;
        private final List<Runnable> saved;
        private boolean closed;

        private Scope(EntityModel<?> model, boolean riding, boolean young, float attackTime,
                      List<Runnable> saved) {
            this.model = model;
            this.riding = riding;
            this.young = young;
            this.attackTime = attackTime;
            this.saved = List.copyOf(saved);
        }

        public boolean active() {
            return model != null;
        }

        @Override
        public void close() {
            if (model == null || closed) {
                return;
            }
            closed = true;
            Throwable failure = null;
            for (Runnable state : saved) {
                try {
                    state.run();
                } catch (Throwable restoreFailure) {
                    if (failure == null) {
                        failure = restoreFailure;
                    } else if (restoreFailure != failure) {
                        failure.addSuppressed(restoreFailure);
                    }
                }
            }
            // Restore flags even if an individual part failed to restore.
            model.riding = riding;
            model.young = young;
            model.attackTime = attackTime;
            if (failure instanceof Error error) {
                throw error;
            }
            if (failure instanceof RuntimeException exception) {
                throw exception;
            }
            if (failure != null) {
                throw new IllegalStateException("Unable to restore source model state", failure);
            }
        }
    }

    private SourceModelPreparation() {
    }
}
