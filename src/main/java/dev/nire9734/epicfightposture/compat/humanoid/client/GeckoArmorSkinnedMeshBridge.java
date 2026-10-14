package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import dev.nire9734.epicfightposture.mixin.client.GeoArmorRendererInvoker;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import yesman.epicfight.api.client.model.Mesh;
import yesman.epicfight.api.client.model.MeshPartDefinition;
import yesman.epicfight.api.client.model.SingleGroupVertexBuilder;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec2f;
import yesman.epicfight.api.utils.math.Vec3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * Converts one GeckoLib armor body-part callback to an Epic Fight skinned mesh.
 *
 * <p>ItemArmorGeoLayer does two jobs before GeoArmorRenderer draws: it chooses a
 * source entity bone/ModelPart pair and fits that ModelPart to the source bone's
 * pivot/cube dimensions. The previous bridge skipped that fit and baked an entire
 * equipment slot at once. That made player-space armor bind geometry drift from
 * non-player mobs and also broke renderers such as Iron's that issue separate
 * body/right-sleeve/left-sleeve callbacks for one chest item.</p>
 *
 * <p>This bridge therefore keeps ItemArmorGeoLayer's callback granularity. The
 * mixin first runs GeckoLib's own prepModelPartForRender against the source bone's
 * bind rotation. We then let the concrete GeoArmorRenderer apply that fitted
 * ModelPart to its GeoBones, preserve applyBoneVisibilityByPart(), bake only the
 * selected semantic region, and skin it with the entity's existing EF armature.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class GeckoArmorSkinnedMeshBridge {
    private static final float EPSILON = 1.0E-6F;
    private static final float TRANSITION_SHORT_SEGMENT_FACTOR = 0.16F;
    private static final float MIN_TRANSITION_HALF_WIDTH = 0.025F;
    private static final float MAX_TRANSITION_HALF_WIDTH = 0.12F;

    private static final MeshPartDefinition PART = new RuntimeArmorPart("gecko_armor");
    private static final ThreadLocal<Frame> ACTIVE = new ThreadLocal<>();

    /** Weak baked-model keys naturally invalidate after Gecko resource reloads. */
    private static final Map<BakedGeoModel, Map<MeshCacheKey, BakedArmor>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static FrameScope openFrame(
            LivingEntity entity,
            Armature armature,
            HumanoidSkeletonDescriptor skeleton,
            PoseStack bodyPoseStack) {
        Frame previous = ACTIVE.get();
        if (entity == null || armature == null || skeleton == null || bodyPoseStack == null) {
            return new FrameScope(previous, false);
        }

        ACTIVE.set(new Frame(
                entity,
                armature,
                skeleton,
                new Matrix4f(bodyPoseStack.last().pose()),
                new Matrix3f(bodyPoseStack.last().normal())
        ));
        return new FrameScope(previous, true);
    }

    /** Returns true when the original ItemArmorGeoLayer callback should be cancelled. */
    public static boolean renderIfSupported(
            LivingEntity entity,
            GeoBone sourceBone,
            ItemStack armorStack,
            EquipmentSlot reportedSlot,
            HumanoidModel<?> armorModel,
            ModelPart armorPart,
            Vector3f fitScale,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay,
            float partialTick) {
        Frame frame = ACTIVE.get();
        if (frame == null
                || frame.entity != entity
                || sourceBone == null
                || armorStack == null
                || armorStack.isEmpty()
                || !(armorModel instanceof GeoArmorRenderer<?> armorRenderer)
                || armorPart == null
                || fitScale == null
                || bufferSource == null
                || !finitePositive(fitScale.x)
                || !finitePositive(fitScale.y)
                || !finitePositive(fitScale.z)) {
            return false;
        }

        // Custom armor render layers may add passes that cannot yet be represented
        // by a single EF mesh. Leave those on Gecko's original rendering path.
        if (armorRenderer.getRenderLayers() != null && !armorRenderer.getRenderLayers().isEmpty()) {
            return false;
        }

        EquipmentSlot actualSlot = actualArmorSlot(entity, armorStack, reportedSlot);
        if (actualSlot == null) {
            return false;
        }

        ArmorRegion selectedRegion = regionForModelPart(armorModel, armorPart);
        if (selectedRegion == null) {
            return false;
        }

        RenderKey renderKey = new RenderKey(
                armorRenderer,
                armorStack.getItem(),
                sourceBone,
                selectedRegion
        );
        if (!frame.rendered.add(renderKey)) {
            return true;
        }

        PreparedArmor prepared = prepareArmor(
                armorRenderer,
                entity,
                armorStack,
                reportedSlot,
                armorModel,
                armorPart,
                bufferSource,
                partialTick
        );
        if (prepared == null) {
            frame.rendered.remove(renderKey);
            return false;
        }

        PartTransformKey partTransform = PartTransformKey.of(armorPart, fitScale);
        MeshCacheKey cacheKey = new MeshCacheKey(
                frame.skeleton.key(),
                actualSlot,
                reportedSlot,
                armorRenderer.getClass(),
                sourceBone.getName(),
                selectedRegion,
                partTransform
        );
        BakedArmor bakedArmor = cachedArmor(
                prepared.bakedModel,
                cacheKey,
                armorRenderer,
                selectedRegion,
                frame.skeleton,
                fitScale
        );
        if (bakedArmor == null || bakedArmor.mesh == null) {
            frame.rendered.remove(renderKey);
            return false;
        }

        PoseStack armorPose = new PoseStack();
        armorPose.last().pose().set(frame.basePose);
        armorPose.last().normal().set(frame.baseNormal);

        Minecraft mc = Minecraft.getInstance();
        float alpha = mc.player != null && entity.isInvisibleTo(mc.player) ? 0.15F : 1.0F;

        bakedArmor.mesh.draw(
                armorPose,
                bufferSource,
                prepared.renderType,
                packedLight,
                1.0F,
                1.0F,
                1.0F,
                alpha,
                packedOverlay,
                frame.armature,
                frame.armature.getPoseMatrices()
        );

        if (armorStack.hasFoil()) {
            bakedArmor.mesh.draw(
                    armorPose,
                    bufferSource,
                    RenderType.armorEntityGlint(),
                    packedLight,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    packedOverlay,
                    frame.armature,
                    frame.armature.getPoseMatrices()
            );
        }

        if (mc.shouldEntityAppearGlowing(entity)) {
            bakedArmor.mesh.draw(
                    armorPose,
                    bufferSource,
                    RenderType.outline(prepared.texture),
                    packedLight,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F,
                    packedOverlay,
                    frame.armature,
                    frame.armature.getPoseMatrices()
            );
        }

        return true;
    }

    private static PreparedArmor prepareArmor(
            GeoArmorRenderer<?> armorRenderer,
            LivingEntity entity,
            ItemStack stack,
            EquipmentSlot reportedSlot,
            HumanoidModel<?> armorModel,
            ModelPart armorPart,
            MultiBufferSource bufferSource,
            float partialTick) {
        try {
            GeoArmorRenderer rawRenderer = (GeoArmorRenderer)armorRenderer;
            rawRenderer.prepForRender(entity, stack, reportedSlot, armorModel);

            GeoAnimatable armorAnimatable = (GeoAnimatable)rawRenderer.getAnimatable();
            GeoModel rawModel = (GeoModel)rawRenderer.getGeoModel();
            GeoRenderer rawGeoRenderer = (GeoRenderer)rawRenderer;

            ResourceLocation modelLocation = rawModel.getModelResource(armorAnimatable, rawGeoRenderer);
            BakedGeoModel bakedModel = rawModel.getBakedModel(modelLocation);
            if (bakedModel == null || bakedModel.topLevelBones() == null) {
                return null;
            }

            if (!(armorRenderer instanceof GeoArmorRendererInvoker invoker)) {
                return null;
            }

            // Remove any item animation left on the shared baked model, then
            // reproduce GeoArmorRenderer.preRender's base-model fitting without
            // running the armor's own animation controller.
            GeckoBindPoseTransforms.resetToInitial(bakedModel);
            invoker.efpi$applyBaseModel(armorModel);
            invoker.efpi$grabRelevantBones(bakedModel);
            invoker.efpi$applyBaseTransformations(armorModel);

            // This is the important distinction from the previous slot-wide
            // attempt: preserve the concrete ItemArmorGeoLayer callback's exact
            // ModelPart selection. Subclass visibility overrides remain active.
            armorRenderer.applyBoneVisibilityByPart(reportedSlot, armorPart, armorModel);

            ResourceLocation texture = rawGeoRenderer.getTextureLocation(armorAnimatable);
            RenderType renderType = rawGeoRenderer.getRenderType(
                    armorAnimatable,
                    texture,
                    bufferSource,
                    partialTick
            );
            if (renderType == null) {
                renderType = RenderType.armorCutoutNoCull(texture);
            }

            return new PreparedArmor(bakedModel, texture, renderType);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static BakedArmor cachedArmor(
            BakedGeoModel model,
            MeshCacheKey key,
            GeoArmorRenderer<?> renderer,
            ArmorRegion selectedRegion,
            HumanoidSkeletonDescriptor skeleton,
            Vector3f fitScale) {
        synchronized (CACHE) {
            Map<MeshCacheKey, BakedArmor> meshes =
                    CACHE.computeIfAbsent(model, ignored -> new HashMap<>());
            BakedArmor cached = meshes.get(key);
            if (cached != null) {
                return cached.supported ? cached : null;
            }

            BakedArmor baked = bakeArmor(
                    model,
                    renderer,
                    selectedRegion,
                    skeleton,
                    fitScale
            );
            meshes.put(key, baked);
            return baked.supported ? baked : null;
        }
    }

    private static BakedArmor bakeArmor(
            BakedGeoModel model,
            GeoArmorRenderer<?> renderer,
            ArmorRegion selectedRegion,
            HumanoidSkeletonDescriptor skeleton,
            Vector3f fitScale) {
        try {
            IdentityHashMap<GeoBone, ArmorRegion> explicitRegions = new IdentityHashMap<>();
            putRegion(explicitRegions, renderer.getHeadBone(), ArmorRegion.HEAD);
            putRegion(explicitRegions, renderer.getBodyBone(), ArmorRegion.BODY);
            putRegion(explicitRegions, renderer.getRightArmBone(), ArmorRegion.RIGHT_ARM);
            putRegion(explicitRegions, renderer.getLeftArmBone(), ArmorRegion.LEFT_ARM);
            putRegion(explicitRegions, renderer.getRightLegBone(), ArmorRegion.RIGHT_LEG);
            putRegion(explicitRegions, renderer.getLeftLegBone(), ArmorRegion.LEFT_LEG);
            putRegion(explicitRegions, renderer.getRightBootBone(), ArmorRegion.RIGHT_LEG);
            putRegion(explicitRegions, renderer.getLeftBootBone(), ArmorRegion.LEFT_LEG);

            List<SingleGroupVertexBuilder> vertices = new ArrayList<>();
            Map<MeshPartDefinition, IntList> indices = new LinkedHashMap<>();
            BakeState state = new BakeState();
            PoseStack stack = new PoseStack();
            stack.scale(fitScale.x, fitScale.y, fitScale.z);

            for (GeoBone root : model.topLevelBones()) {
                bakeBone(
                        root,
                        null,
                        selectedRegion,
                        stack,
                        explicitRegions,
                        skeleton,
                        vertices,
                        indices,
                        state
                );
            }

            if (state.selectedUnclassifiedGeometry || vertices.isEmpty()) {
                return BakedArmor.unsupported();
            }

            SkinnedMesh mesh = SingleGroupVertexBuilder.loadVertexInformation(vertices, indices);
            return mesh == null ? BakedArmor.unsupported() : BakedArmor.supported(mesh);
        } catch (Throwable ignored) {
            return BakedArmor.unsupported();
        }
    }

    private static void bakeBone(
            GeoBone bone,
            ArmorRegion inheritedRegion,
            ArmorRegion selectedRegion,
            PoseStack stack,
            IdentityHashMap<GeoBone, ArmorRegion> explicitRegions,
            HumanoidSkeletonDescriptor skeleton,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices,
            BakeState state) {
        if (bone == null) {
            return;
        }

        ArmorRegion region = explicitRegions.get(bone);
        if (region == null) {
            region = inferRegion(bone.getName(), inheritedRegion);
        }

        GeckoBindPoseTransforms.pushBone(stack, bone);

        if (!bone.isHidden() && bone.getCubes() != null && !bone.getCubes().isEmpty()) {
            if (region == null) {
                state.selectedUnclassifiedGeometry = true;
            } else if (region == selectedRegion) {
                for (GeoCube cube : bone.getCubes()) {
                    bakeCube(cube, region, stack, skeleton, vertices, indices);
                }
            }
        }

        if (!bone.isHidingChildren() && bone.getChildBones() != null) {
            for (GeoBone child : bone.getChildBones()) {
                bakeBone(
                        child,
                        region,
                        selectedRegion,
                        stack,
                        explicitRegions,
                        skeleton,
                        vertices,
                        indices,
                        state
                );
            }
        }

        stack.popPose();
    }

    private static void bakeCube(
            GeoCube cube,
            ArmorRegion region,
            PoseStack stack,
            HumanoidSkeletonDescriptor skeleton,
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) {
        if (cube == null || cube.quads() == null) {
            return;
        }

        GeckoBindPoseTransforms.pushCube(stack, cube);

        for (GeoQuad quad : cube.quads()) {
            if (!isRenderableQuad(quad)) {
                continue;
            }

            Vector3f normal = GeckoBindPoseTransforms.transformNormal(stack, quad.normal());
            int first = vertices.size();

            for (GeoVertex vertex : quad.vertices()) {
                SkeletonVec3 position = GeckoBindPoseTransforms.transformPosition(
                        stack,
                        new Vector3f(vertex.position())
                );
                HumanoidSkinWeightResolver.SkinWeights weights =
                        weightsFor(region, skeleton, position);

                vertices.add(new SingleGroupVertexBuilder()
                        .setPosition(new Vec3f(position.x(), position.y(), position.z()))
                        .setNormal(new Vec3f(normal.x(), normal.y(), normal.z()))
                        .setTextureCoordinate(new Vec2f(vertex.texU(), vertex.texV()))
                        .setEffectiveJointIDs(new Vec3f(
                                weights.firstJoint(),
                                weights.secondJoint(),
                                0.0F
                        ))
                        .setEffectiveJointWeights(new Vec3f(
                                weights.firstWeight(),
                                weights.secondWeight(),
                                0.0F
                        ))
                        .setEffectiveJointNumber(weights.count()));
            }

            triangulateQuad(indices, first);
        }

        stack.popPose();
    }

    private static HumanoidSkinWeightResolver.SkinWeights weightsFor(
            ArmorRegion region,
            HumanoidSkeletonDescriptor skeleton,
            SkeletonVec3 position) {
        return switch (region) {
            case HEAD -> HumanoidSkinWeightResolver.SkinWeights.rigid(
                    HumanoidSkinWeightResolver.HEAD);
            case BODY -> HumanoidSkinWeightResolver.torsoWeights(skeleton, position);
            case RIGHT_ARM -> chainWeights(
                    skeleton,
                    position,
                    HumanoidJoint.SHOULDER_RIGHT,
                    HumanoidJoint.ELBOW_RIGHT,
                    HumanoidJoint.WRIST_RIGHT,
                    HumanoidSkinWeightResolver.ARM_RIGHT,
                    HumanoidSkinWeightResolver.HAND_RIGHT
            );
            case LEFT_ARM -> chainWeights(
                    skeleton,
                    position,
                    HumanoidJoint.SHOULDER_LEFT,
                    HumanoidJoint.ELBOW_LEFT,
                    HumanoidJoint.WRIST_LEFT,
                    HumanoidSkinWeightResolver.ARM_LEFT,
                    HumanoidSkinWeightResolver.HAND_LEFT
            );
            case RIGHT_LEG -> chainWeights(
                    skeleton,
                    position,
                    HumanoidJoint.HIP_RIGHT,
                    HumanoidJoint.KNEE_RIGHT,
                    HumanoidJoint.ANKLE_RIGHT,
                    HumanoidSkinWeightResolver.THIGH_RIGHT,
                    HumanoidSkinWeightResolver.LEG_RIGHT
            );
            case LEFT_LEG -> chainWeights(
                    skeleton,
                    position,
                    HumanoidJoint.HIP_LEFT,
                    HumanoidJoint.KNEE_LEFT,
                    HumanoidJoint.ANKLE_LEFT,
                    HumanoidSkinWeightResolver.THIGH_LEFT,
                    HumanoidSkinWeightResolver.LEG_LEFT
            );
        };
    }

    private static HumanoidSkinWeightResolver.SkinWeights chainWeights(
            HumanoidSkeletonDescriptor skeleton,
            SkeletonVec3 position,
            HumanoidJoint startJoint,
            HumanoidJoint bendJoint,
            HumanoidJoint endJoint,
            int proximalJoint,
            int distalJoint) {
        SkeletonVec3 start = skeleton.bindModelPosition(startJoint);
        SkeletonVec3 bend = skeleton.bindModelPosition(bendJoint);
        SkeletonVec3 end = skeleton.bindModelPosition(endJoint);

        SkeletonVec3 axis = end.subtract(start);
        float denominator = axis.lengthSquared();
        if (denominator <= EPSILON) {
            return HumanoidSkinWeightResolver.SkinWeights.rigid(proximalJoint);
        }

        float t = dot(position.subtract(start), axis) / denominator;
        float bendT = clamp01(dot(bend.subtract(start), axis) / denominator);
        if (!Float.isFinite(t) || !Float.isFinite(bendT)) {
            return HumanoidSkinWeightResolver.SkinWeights.rigid(proximalJoint);
        }

        float upperLength = start.distanceTo(bend);
        float lowerLength = bend.distanceTo(end);
        float chainLength = upperLength + lowerLength;
        float halfWidth = chainLength <= EPSILON
                ? MIN_TRANSITION_HALF_WIDTH
                : clamp(
                        (Math.min(upperLength, lowerLength) / chainLength)
                                * TRANSITION_SHORT_SEGMENT_FACTOR,
                        MIN_TRANSITION_HALF_WIDTH,
                        MAX_TRANSITION_HALF_WIDTH
                );

        float lowerEdge = bendT - halfWidth;
        float upperEdge = bendT + halfWidth;
        float distalWeight;
        if (t <= lowerEdge) {
            distalWeight = 0.0F;
        } else if (t >= upperEdge) {
            distalWeight = 1.0F;
        } else {
            distalWeight = clamp01((t - lowerEdge) / Math.max(EPSILON, upperEdge - lowerEdge));
        }

        if (distalWeight <= EPSILON) {
            return HumanoidSkinWeightResolver.SkinWeights.rigid(proximalJoint);
        }
        if (distalWeight >= 1.0F - EPSILON) {
            return HumanoidSkinWeightResolver.SkinWeights.rigid(distalJoint);
        }
        return HumanoidSkinWeightResolver.SkinWeights.blend(
                proximalJoint,
                distalJoint,
                1.0F - distalWeight,
                distalWeight
        );
    }

    private static ArmorRegion regionForModelPart(HumanoidModel<?> model, ModelPart part) {
        if (part == model.head || part == model.hat) {
            return ArmorRegion.HEAD;
        }
        if (part == model.body) {
            return ArmorRegion.BODY;
        }
        if (part == model.rightArm) {
            return ArmorRegion.RIGHT_ARM;
        }
        if (part == model.leftArm) {
            return ArmorRegion.LEFT_ARM;
        }
        if (part == model.rightLeg) {
            return ArmorRegion.RIGHT_LEG;
        }
        if (part == model.leftLeg) {
            return ArmorRegion.LEFT_LEG;
        }
        return null;
    }

    private static ArmorRegion inferRegion(String rawName, ArmorRegion inherited) {
        String name = normalize(rawName);
        if (name.isEmpty()) {
            return inherited;
        }

        if (isRight(name) && containsAny(name,
                "arm", "hand", "sleeve", "shoulder", "elbow", "wrist", "forearm")) {
            return ArmorRegion.RIGHT_ARM;
        }
        if (isLeft(name) && containsAny(name,
                "arm", "hand", "sleeve", "shoulder", "elbow", "wrist", "forearm")) {
            return ArmorRegion.LEFT_ARM;
        }
        if (isRight(name) && containsAny(name,
                "leg", "foot", "boot", "thigh", "knee", "shin", "calf")) {
            return ArmorRegion.RIGHT_LEG;
        }
        if (isLeft(name) && containsAny(name,
                "leg", "foot", "boot", "thigh", "knee", "shin", "calf")) {
            return ArmorRegion.LEFT_LEG;
        }
        if (containsAny(name, "head", "helmet", "hat", "hood", "skull", "crown")) {
            return ArmorRegion.HEAD;
        }
        if (containsAny(name,
                "body", "torso", "chest", "spine", "trunk", "robe", "coat",
                "shirt", "cape", "cloak", "mantle", "back", "waist", "belt")) {
            return ArmorRegion.BODY;
        }
        return inherited;
    }

    private static EquipmentSlot actualArmorSlot(
            LivingEntity entity,
            ItemStack stack,
            EquipmentSlot reportedSlot) {
        EquipmentSlot[] armorSlots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };
        for (EquipmentSlot slot : armorSlots) {
            ItemStack equipped = entity.getItemBySlot(slot);
            if (equipped == stack
                    || (!equipped.isEmpty() && equipped.getItem() == stack.getItem())) {
                return slot;
            }
        }
        return reportedSlot != null && reportedSlot.getType() == EquipmentSlot.Type.ARMOR
                ? reportedSlot
                : null;
    }

    private static void putRegion(
            IdentityHashMap<GeoBone, ArmorRegion> regions,
            GeoBone bone,
            ArmorRegion region) {
        if (bone != null) {
            regions.put(bone, region);
        }
    }

    private static boolean isRenderableQuad(GeoQuad quad) {
        if (quad == null || quad.normal() == null) {
            return false;
        }
        GeoVertex[] vertices = quad.vertices();
        if (vertices == null || vertices.length != 4) {
            return false;
        }
        for (GeoVertex vertex : vertices) {
            if (vertex == null || vertex.position() == null) {
                return false;
            }
        }
        return true;
    }

    private static void triangulateQuad(Map<MeshPartDefinition, IntList> indices, int first) {
        IntList list = indices.computeIfAbsent(PART, ignored -> new IntArrayList());
        add3(list, first);
        add3(list, first + 1);
        add3(list, first + 3);
        add3(list, first + 3);
        add3(list, first + 1);
        add3(list, first + 2);
    }

    private static void add3(IntList list, int value) {
        list.add(value);
        list.add(value);
        list.add(value);
    }

    private static float dot(SkeletonVec3 a, SkeletonVec3 b) {
        return a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
    }

    private static float clamp01(float value) {
        return clamp(value, 0.0F, 1.0F);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean finitePositive(float value) {
        return Float.isFinite(value) && value > EPSILON;
    }

    private static boolean isRight(String name) {
        return name.contains("right")
                || name.startsWith("rarm")
                || name.startsWith("rhand")
                || name.startsWith("rleg")
                || name.startsWith("rfoot")
                || name.endsWith("r");
    }

    private static boolean isLeft(String name) {
        return name.contains("left")
                || name.startsWith("larm")
                || name.startsWith("lhand")
                || name.startsWith("lleg")
                || name.startsWith("lfoot")
                || name.endsWith("l");
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static final class FrameScope implements AutoCloseable {
        private final Frame previous;
        private final boolean installed;
        private boolean closed;

        private FrameScope(Frame previous, boolean installed) {
            this.previous = previous;
            this.installed = installed;
        }

        @Override
        public void close() {
            if (closed || !installed) {
                closed = true;
                return;
            }
            closed = true;
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    private enum ArmorRegion {
        HEAD,
        BODY,
        RIGHT_ARM,
        LEFT_ARM,
        RIGHT_LEG,
        LEFT_LEG
    }

    private static final class BakeState {
        private boolean selectedUnclassifiedGeometry;
    }

    private static final class Frame {
        private final LivingEntity entity;
        private final Armature armature;
        private final HumanoidSkeletonDescriptor skeleton;
        private final Matrix4f basePose;
        private final Matrix3f baseNormal;
        private final Set<RenderKey> rendered = new HashSet<>();

        private Frame(
                LivingEntity entity,
                Armature armature,
                HumanoidSkeletonDescriptor skeleton,
                Matrix4f basePose,
                Matrix3f baseNormal) {
            this.entity = entity;
            this.armature = armature;
            this.skeleton = skeleton;
            this.basePose = basePose;
            this.baseNormal = baseNormal;
        }
    }

    private record PreparedArmor(
            BakedGeoModel bakedModel,
            ResourceLocation texture,
            RenderType renderType) {
    }

    private record MeshCacheKey(
            SkeletonKey skeletonKey,
            EquipmentSlot actualSlot,
            EquipmentSlot reportedSlot,
            Class<?> rendererClass,
            String sourceBoneName,
            ArmorRegion region,
            PartTransformKey partTransform) {
    }

    private record PartTransformKey(
            int x,
            int y,
            int z,
            int xRot,
            int yRot,
            int zRot,
            int scaleX,
            int scaleY,
            int scaleZ) {
        private static PartTransformKey of(ModelPart part, Vector3f fitScale) {
            return new PartTransformKey(
                    Float.floatToIntBits(part.x),
                    Float.floatToIntBits(part.y),
                    Float.floatToIntBits(part.z),
                    Float.floatToIntBits(part.xRot),
                    Float.floatToIntBits(part.yRot),
                    Float.floatToIntBits(part.zRot),
                    Float.floatToIntBits(fitScale.x),
                    Float.floatToIntBits(fitScale.y),
                    Float.floatToIntBits(fitScale.z)
            );
        }
    }

    private record RenderKey(
            GeoArmorRenderer<?> renderer,
            Item item,
            GeoBone sourceBone,
            ArmorRegion region) {
    }

    private record BakedArmor(boolean supported, SkinnedMesh mesh) {
        private static BakedArmor supported(SkinnedMesh mesh) {
            return new BakedArmor(true, mesh);
        }

        private static BakedArmor unsupported() {
            return new BakedArmor(false, null);
        }
    }

    private record RuntimeArmorPart(String partName) implements MeshPartDefinition {
        @Override
        public Mesh.RenderProperties renderProperties() {
            return null;
        }

        @Override
        public Supplier<OpenMatrix4f> getModelPartAnimationProvider() {
            return () -> null;
        }
    }

    private GeckoArmorSkinnedMeshBridge() {
    }
}
