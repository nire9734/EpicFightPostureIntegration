package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedEntityModel;
import com.github.L_Ender.lionfishapi.client.model.tools.AdvancedModelBox;
import com.github.L_Ender.lionfishapi.client.model.tools.BasicModelPart;
import com.github.L_Ender.lionfishapi.client.model.tools.LionfishModelRenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.SecondaryDeformationPolicy;
import dev.nire9734.epicfightposture.compat.humanoid.rig.ConservativeHumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SemanticJointCandidate;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.slf4j.Logger;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/** Temporary diagnostic-only logging for Lionfish humanoid retargeting. */
public final class LionfishRetargetDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();
    private static final Map<LivingEntity, Integer> ENTITY_FRAMES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Boolean> DUMPED_MODELS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final int[] SAMPLE_FRAMES = {1, 3, 6, 10, 15, 20};

    public static Scope open(
            LivingEntity entity,
            EntityRenderer<LivingEntity> renderer,
            HumanoidSkeletonDescriptor descriptor,
            Armature armature,
            float partialTicks) {
        Frame previous = ACTIVE.get();
        if (entity == null || renderer == null || armature == null
                || !(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)) {
            return new Scope(previous, false);
        }
        EntityModel<?> sourceModel = livingRenderer.getModel();
        if (!(sourceModel instanceof AdvancedEntityModel<?> model)) {
            return new Scope(previous, false);
        }

        int number;
        synchronized (ENTITY_FRAMES) {
            number = ENTITY_FRAMES.getOrDefault(entity, 0) + 1;
            ENTITY_FRAMES.put(entity, number);
        }
        if (!sample(number)) {
            return new Scope(previous, false);
        }

        Frame frame = new Frame(entity, model, descriptor, armature, number);
        ACTIVE.set(frame);
        LOGGER.info("[EFPI-DIAG] FRAME_BEGIN sample={} tick={} partial={} type={} class={} uuid={} renderer={} model={} modelId={} armatureId={} descriptor={}",
                number, entity.tickCount, partialTicks, EntityType.getKey(entity.getType()),
                entity.getClass().getName(), entity.getUUID(), renderer.getClass().getName(),
                model.getClass().getName(), id(model), id(armature),
                descriptor == null ? "null" : descriptor.key());

        dumpDescriptor(frame);
        dumpArmature(frame);
        boolean staticDump;
        synchronized (DUMPED_MODELS) {
            staticDump = DUMPED_MODELS.putIfAbsent(model, Boolean.TRUE) == null;
        }
        if (staticDump) {
            dumpSource(frame);
        }
        return new Scope(previous, true);
    }

    public static void bodyPass(PoseStack stack, RenderType type) {
        Frame f = ACTIVE.get();
        if (f == null || stack == null) return;
        f.bodyPose = new Matrix4f(stack.last().pose());
        LOGGER.info("[EFPI-DIAG] BODY_PASS sample={} renderType={} mode={} pose={}",
                f.sample, type, type == null ? "null" : type.mode(), mat(f.bodyPose));
    }

    public static void layerStart(Object layer) {
        Frame f = ACTIVE.get();
        if (f == null) return;
        f.layer = layer == null ? "null" : layer.getClass().getName();
        LOGGER.info("[EFPI-DIAG] LAYER_BEGIN sample={} layer={}", f.sample, f.layer);
    }

    public static void layerEnd(Object layer) {
        Frame f = ACTIVE.get();
        if (f == null) return;
        LOGGER.info("[EFPI-DIAG] LAYER_END sample={} layer={}", f.sample,
                layer == null ? "null" : layer.getClass().getName());
        f.layer = null;
    }

    public static void primaryBridge(Object model, String outcome, String detail, PoseStack stack) {
        bridge("PRIMARY_BRIDGE", model, outcome, detail, stack);
    }

    public static void secondaryBridge(Object model, String outcome, String detail, PoseStack stack) {
        bridge("SECONDARY_BRIDGE", model, outcome, detail, stack);
    }

    private static void bridge(String kind, Object model, String outcome, String detail, PoseStack stack) {
        Frame f = ACTIVE.get();
        if (f == null) return;
        Matrix4f pose = stack == null ? null : new Matrix4f(stack.last().pose());
        LOGGER.info("[EFPI-DIAG] {} sample={} layer={} outcome={} model={} modelId={} detail={} pose={} diffFromBody={}",
                kind, f.sample, f.layer, outcome,
                model == null ? "null" : model.getClass().getName(), id(model), detail,
                pose == null ? "null" : mat(pose),
                pose == null || f.bodyPose == null ? "n/a" : diff(pose, f.bodyPose));
    }

    public static void sourceFallback(Object model, PoseStack stack) {
        Frame f = ACTIVE.get();
        if (f == null) return;
        LOGGER.info("[EFPI-DIAG] SOURCE_MODEL_RENDER sample={} layer={} model={} modelId={} pose={}",
                f.sample, f.layer, model == null ? "null" : model.getClass().getName(), id(model),
                stack == null ? "null" : mat(stack.last().pose()));
    }

    public static void partOverride(Object part, boolean applied) {
        Frame f = ACTIVE.get();
        if (f == null || part == null || !f.parts.add(part)) return;
        LOGGER.info("[EFPI-DIAG] PART_OVERRIDE sample={} layer={} part={} partId={} applied={}",
                f.sample, f.layer, part.getClass().getName(), id(part), applied);
    }

    public static void renderTarget(Object consumer, RenderType type) {
        Frame f = ACTIVE.get();
        if (f == null) return;
        LOGGER.info("[EFPI-DIAG] RENDER_TARGET sample={} layer={} consumerId={} renderType={} mode={}",
                f.sample, f.layer, id(consumer), type, type == null ? "null" : type.mode());
    }

    private static void dumpDescriptor(Frame f) {
        if (f.descriptor == null) {
            LOGGER.info("[EFPI-DIAG] DESCRIPTOR sample={} null", f.sample);
            return;
        }
        HumanoidJoint[] joints = {
                HumanoidJoint.ROOT, HumanoidJoint.SPINE, HumanoidJoint.CHEST,
                HumanoidJoint.SHOULDER_RIGHT, HumanoidJoint.ELBOW_RIGHT, HumanoidJoint.WRIST_RIGHT,
                HumanoidJoint.SHOULDER_LEFT, HumanoidJoint.ELBOW_LEFT, HumanoidJoint.WRIST_LEFT,
                HumanoidJoint.HIP_RIGHT, HumanoidJoint.KNEE_RIGHT, HumanoidJoint.ANKLE_RIGHT,
                HumanoidJoint.HIP_LEFT, HumanoidJoint.KNEE_LEFT, HumanoidJoint.ANKLE_LEFT
        };
        for (HumanoidJoint joint : joints) {
            try {
                LOGGER.info("[EFPI-DIAG] DESCRIPTOR_JOINT sample={} joint={} pos={}",
                        f.sample, joint, vec(f.descriptor.bindModelPosition(joint)));
            } catch (Throwable t) {
                LOGGER.info("[EFPI-DIAG] DESCRIPTOR_JOINT sample={} joint={} unavailable={}",
                        f.sample, joint, t.getClass().getSimpleName());
            }
        }
    }

    private static void dumpArmature(Frame f) {
        int[] ids = {0, 1, 2, 4, 5, 8, 11, 12, 16, 17};
        OpenMatrix4f[] poses = f.armature.getPoseMatrices();
        for (int jointId : ids) {
            if (poses == null || jointId < 0 || jointId >= poses.length || poses[jointId] == null) {
                LOGGER.info("[EFPI-DIAG] ARMATURE_JOINT sample={} id={} unavailable", f.sample, jointId);
                continue;
            }
            Joint joint = f.armature.searchJointById(jointId);
            OpenMatrix4f skin = new OpenMatrix4f(poses[jointId]);
            if (joint != null) skin.mulBack(joint.getToOrigin());
            LOGGER.info("[EFPI-DIAG] ARMATURE_JOINT sample={} id={} joint={} pose={} skin={}",
                    f.sample, jointId, String.valueOf(joint),
                    mat(OpenMatrix4f.exportToMojangMatrix(poses[jointId])),
                    mat(OpenMatrix4f.exportToMojangMatrix(skin)));
        }
    }

    private static void dumpSource(Frame f) {
        if (f.descriptor == null) {
            LOGGER.info("[EFPI-DIAG] SOURCE_DUMP_SKIPPED reason=no_descriptor");
            return;
        }
        try {
            LionfishSourceRigAdapter.Capture capture = LionfishSourceRigAdapter.capture(f.model, f.descriptor.key());
            SourceRigSnapshot snapshot = capture.snapshot();
            HumanoidSemanticAnalysis analysis = ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(snapshot);
            HumanoidSkinWeightResolver resolver = new HumanoidSkinWeightResolver(snapshot, analysis, f.descriptor);
            Map<String, HumanoidJoint> owners = directOwners(analysis);
            IdentityHashMap<AdvancedModelBox, String> ids = new IdentityHashMap<>();
            capture.sourceNodes().forEach((nodeId, part) -> ids.put(part, nodeId));

            LOGGER.info("[EFPI-DIAG] SOURCE_MODEL model={} modelId={} nodes={} bounds={} directOwners={}",
                    f.model.getClass().getName(), id(f.model), snapshot.nodes().size(), snapshot.bounds(), owners);
            for (SourceNodeSnapshot node : snapshot.nodes().values()) {
                LOGGER.info("[EFPI-DIAG] SOURCE_NODE id={} name={} parent={} directOwner={} nearestOwner={} pivot={} bounds={} renderable={}",
                        node.id(), node.semanticName(), node.parentId().orElse("<root>"), owners.get(node.id()),
                        nearest(snapshot, owners, node.id()), vec(node.bindModelTransform().position()),
                        node.geometryBounds().map(Object::toString).orElse("none"), node.renderable());
            }

            PoseStack stack = LionfishSourceRigAdapter.canonicalModelStack();
            Set<AdvancedModelBox> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            int[] globalCube = {0};
            for (AdvancedModelBox part : f.model.getAllParts()) {
                if (part != null && part.getParent() == null) {
                    dumpPart(part, stack, ids, snapshot, owners, resolver, f, visited, globalCube);
                }
            }
        } catch (Throwable t) {
            LOGGER.info("[EFPI-DIAG] SOURCE_DUMP_FAILED type={} message={}",
                    t.getClass().getName(), t.getMessage());
        }
    }

    private static void dumpPart(
            AdvancedModelBox part,
            PoseStack stack,
            IdentityHashMap<AdvancedModelBox, String> ids,
            SourceRigSnapshot snapshot,
            Map<String, HumanoidJoint> owners,
            HumanoidSkinWeightResolver resolver,
            Frame f,
            Set<AdvancedModelBox> visited,
            int[] globalCube) {
        if (part == null || !visited.add(part)) return;
        String nodeId = ids.get(part);
        LionfishSourceRigAdapter.BindValues bind = LionfishSourceRigAdapter.bindValues(part);
        stack.pushPose();
        try {
            stack.translate(bind.positionX() / 16.0F, bind.positionY() / 16.0F, bind.positionZ() / 16.0F);
            stack.mulPose(new Quaternionf().rotationZYX(bind.rotationZ(), bind.rotationY(), bind.rotationX()));
            stack.scale(bind.scaleX(), bind.scaleY(), bind.scaleZ());
            HumanoidJoint owner = nodeId == null ? null : nearest(snapshot, owners, nodeId);
            if (nodeId != null && interesting(owner) && part.cubeList != null) {
                int local = 0;
                for (LionfishModelRenderUtils.ModelBox cube : part.cubeList) {
                    if (cube != null) dumpCube(cube, nodeId, owner, local++, globalCube[0]++, stack, resolver, f);
                }
            }
            if (part.childModels != null) {
                for (BasicModelPart child : part.childModels) {
                    if (child instanceof AdvancedModelBox box) {
                        dumpPart(box, stack, ids, snapshot, owners, resolver, f, visited, globalCube);
                    }
                }
            }
        } finally {
            stack.popPose();
        }
    }

    private static void dumpCube(
            LionfishModelRenderUtils.ModelBox cube,
            String nodeId,
            HumanoidJoint owner,
            int local,
            int global,
            PoseStack stack,
            HumanoidSkinWeightResolver resolver,
            Frame f) {
        float[] xs = {cube.posX1, cube.posX2};
        float[] ys = {cube.posY1, cube.posY2};
        float[] zs = {cube.posZ1, cube.posZ2};
        List<SkeletonVec3> points = new ArrayList<>();
        for (float x : xs) for (float y : ys) for (float z : zs) {
            SkeletonVec3 point = modelPoint(stack, x, y, z);
            if (!points.contains(point)) points.add(point);
        }
        List<HumanoidSkinWeightResolver.SkinWeights> skins = new ArrayList<>();
        for (SkeletonVec3 point : points) skins.add(resolver.weights(nodeId, point));
        Optional<SecondaryDeformationPolicy.Decision> decision = SecondaryDeformationPolicy.classify(skins);

        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) detail.append(" | ");
            SkeletonVec3 finalPoint = skinned(points.get(i), skins.get(i), f.armature);
            detail.append(i).append(':').append(vec(points.get(i))).append(' ')
                    .append(weight(skins.get(i))).append(" -> ")
                    .append(finalPoint == null ? "unavailable" : vec(finalPoint));
        }
        LOGGER.info("[EFPI-DIAG] CUBE global={} local={} node={} owner={} localBounds=({},{},{})..({},{},{}) decision={} vertices={}",
                global, local, nodeId, owner,
                cube.posX1, cube.posY1, cube.posZ1, cube.posX2, cube.posY2, cube.posZ2,
                decision.map(d -> d.rigid() ? d.mode() + "@" + d.rigidJointId() : d.mode().toString())
                        .orElse("AMBIGUOUS"), detail);
    }

    private static SkeletonVec3 skinned(
            SkeletonVec3 p,
            HumanoidSkinWeightResolver.SkinWeights weights,
            Armature armature) {
        OpenMatrix4f[] poses = armature.getPoseMatrices();
        if (poses == null) return null;
        Vec4f source = new Vec4f(p.x(), p.y(), p.z(), 1.0F);
        float x = 0.0F, y = 0.0F, z = 0.0F;
        for (int i = 0; i < weights.count(); i++) {
            int id = i == 0 ? weights.firstJoint() : weights.secondJoint();
            float w = i == 0 ? weights.firstWeight() : weights.secondWeight();
            if (id < 0 || id >= poses.length || poses[id] == null) return null;
            Joint joint = armature.searchJointById(id);
            if (joint == null) return null;
            OpenMatrix4f skin = new OpenMatrix4f(poses[id]);
            skin.mulBack(joint.getToOrigin());
            Vec4f out = OpenMatrix4f.transform(skin, source, new Vec4f());
            x += out.x * w; y += out.y * w; z += out.z * w;
        }
        return new SkeletonVec3(x, y, z);
    }

    private static Map<String, HumanoidJoint> directOwners(HumanoidSemanticAnalysis analysis) {
        Map<String, HumanoidJoint> result = new LinkedHashMap<>();
        for (SemanticJointCandidate candidate : analysis.candidates().values()) {
            if (candidate.origin() == JointOrigin.SOURCE) {
                candidate.sourceNodeId().ifPresent(id -> result.putIfAbsent(id, candidate.joint()));
            }
        }
        return result;
    }

    private static HumanoidJoint nearest(SourceRigSnapshot snapshot, Map<String, HumanoidJoint> owners, String id) {
        SourceNodeSnapshot node = snapshot.node(id);
        while (true) {
            HumanoidJoint owner = owners.get(node.id());
            if (owner != null) return owner;
            if (node.parentId().isEmpty()) return null;
            node = snapshot.node(node.parentId().orElseThrow());
        }
    }

    private static boolean interesting(HumanoidJoint owner) {
        if (owner == null) return false;
        return switch (owner) {
            case ROOT, SPINE, CHEST,
                    SHOULDER_RIGHT, ELBOW_RIGHT, WRIST_RIGHT,
                    SHOULDER_LEFT, ELBOW_LEFT, WRIST_LEFT,
                    HIP_RIGHT, KNEE_RIGHT, ANKLE_RIGHT,
                    HIP_LEFT, KNEE_LEFT, ANKLE_LEFT -> true;
            default -> false;
        };
    }

    private static SkeletonVec3 modelPoint(PoseStack stack, float x, float y, float z) {
        Vector4f v = new Vector4f(x / 16.0F, y / 16.0F, z / 16.0F, 1.0F);
        v.mul(stack.last().pose());
        return new SkeletonVec3(v.x(), v.y(), v.z());
    }

    private static String weight(HumanoidSkinWeightResolver.SkinWeights w) {
        return w.count() == 1
                ? "J" + w.firstJoint() + "=" + w.firstWeight()
                : "J" + w.firstJoint() + "=" + w.firstWeight()
                + ",J" + w.secondJoint() + "=" + w.secondWeight();
    }

    private static String id(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(o));
    }

    private static String vec(SkeletonVec3 v) {
        return String.format(java.util.Locale.ROOT, "(%.6f,%.6f,%.6f)", v.x(), v.y(), v.z());
    }

    private static String mat(Matrix4f m) {
        return String.format(java.util.Locale.ROOT,
                "[%.6f,%.6f,%.6f,%.6f;%.6f,%.6f,%.6f,%.6f;%.6f,%.6f,%.6f,%.6f;%.6f,%.6f,%.6f,%.6f]",
                m.m00(),m.m01(),m.m02(),m.m03(),m.m10(),m.m11(),m.m12(),m.m13(),
                m.m20(),m.m21(),m.m22(),m.m23(),m.m30(),m.m31(),m.m32(),m.m33());
    }

    private static float diff(Matrix4f a, Matrix4f b) {
        float[] x = {a.m00(),a.m01(),a.m02(),a.m03(),a.m10(),a.m11(),a.m12(),a.m13(),
                a.m20(),a.m21(),a.m22(),a.m23(),a.m30(),a.m31(),a.m32(),a.m33()};
        float[] y = {b.m00(),b.m01(),b.m02(),b.m03(),b.m10(),b.m11(),b.m12(),b.m13(),
                b.m20(),b.m21(),b.m22(),b.m23(),b.m30(),b.m31(),b.m32(),b.m33()};
        float max = 0.0F;
        for (int i = 0; i < x.length; i++) max = Math.max(max, Math.abs(x[i] - y[i]));
        return max;
    }

    private static boolean sample(int number) {
        for (int value : SAMPLE_FRAMES) if (number == value) return true;
        return false;
    }

    public static final class Scope implements AutoCloseable {
        private final Frame previous;
        private final boolean installed;
        private boolean closed;

        private Scope(Frame previous, boolean installed) {
            this.previous = previous;
            this.installed = installed;
        }

        @Override
        public void close() {
            if (closed || !installed) { closed = true; return; }
            closed = true;
            Frame f = ACTIVE.get();
            if (f != null) {
                LOGGER.info("[EFPI-DIAG] FRAME_END sample={} tick={} type={} uuid={}",
                        f.sample, f.entity.tickCount, EntityType.getKey(f.entity.getType()), f.entity.getUUID());
            }
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }

    private static final class Frame {
        private final LivingEntity entity;
        private final AdvancedEntityModel<?> model;
        private final HumanoidSkeletonDescriptor descriptor;
        private final Armature armature;
        private final int sample;
        private final Set<Object> parts = Collections.newSetFromMap(new IdentityHashMap<>());
        private String layer;
        private Matrix4f bodyPose;

        private Frame(LivingEntity entity, AdvancedEntityModel<?> model,
                      HumanoidSkeletonDescriptor descriptor, Armature armature, int sample) {
            this.entity = entity;
            this.model = model;
            this.descriptor = descriptor;
            this.armature = armature;
            this.sample = sample;
        }
    }

    private LionfishRetargetDiagnostics() {}
}
