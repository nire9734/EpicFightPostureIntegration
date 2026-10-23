package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.rig.ConservativeHumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSemanticAnalysis;
import dev.nire9734.epicfightposture.compat.humanoid.rig.HumanoidSkeletonResolver;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SemanticJointCandidate;
import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceRigSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * One resolved Gecko bind rig shared by body baking and Gecko-specific secondary rendering.
 *
 * <p>The cache is keyed by baked model identity and the same portable SkeletonKey used by
 * body conversion. Resource reloads replace BakedGeoModel instances, allowing the weak
 * outer key to invalidate the old resolution naturally.</p>
 */
public final class GeckoResolvedHumanoidRig {
    private static final Map<BakedGeoModel, Map<SkeletonKey, GeckoResolvedHumanoidRig>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final SourceRigSnapshot snapshot;
    private final HumanoidSemanticAnalysis analysis;
    private final HumanoidSkeletonDescriptor descriptor;
    private final HumanoidSkinWeightResolver weights;
    private final Map<String, GeoBone> sourceNodes;
    private final IdentityHashMap<GeoBone, Integer> deformationJointIds;

    public static GeckoResolvedHumanoidRig resolve(
            BakedGeoModel model, SkeletonKey key, GeckoBindPoseOperations operations) {
        if (model == null) {
            throw new IllegalArgumentException("Gecko model cannot be null");
        }
        if (key == null) {
            throw new IllegalArgumentException("Gecko skeleton key cannot be null");
        }

        synchronized (CACHE) {
            Map<SkeletonKey, GeckoResolvedHumanoidRig> byKey =
                    CACHE.computeIfAbsent(model, ignored -> new HashMap<>());
            GeckoResolvedHumanoidRig cached = byKey.get(key);
            if (cached != null) {
                return cached;
            }

            GeckoSourceRigAdapter.Capture capture = GeckoSourceRigAdapter.capture(model, key, operations);
            HumanoidSemanticAnalysis analysis =
                    ConservativeHumanoidSemanticAnalyzer.INSTANCE.analyze(capture.snapshot());
            HumanoidSkeletonDescriptor descriptor =
                    HumanoidSkeletonResolver.resolve(capture.snapshot(), analysis);
            HumanoidSkinWeightResolver weights =
                    new HumanoidSkinWeightResolver(capture.snapshot(), analysis, descriptor);

            GeckoResolvedHumanoidRig created = new GeckoResolvedHumanoidRig(
                    capture.snapshot(),
                    analysis,
                    descriptor,
                    weights,
                    capture.sourceNodes()
            );
            byKey.put(key, created);
            return created;
        }
    }

    public static SkeletonKey keyFor(
            LivingEntity entity,
            GeoModel<?> geoModel,
            ResourceLocation modelLocation) {
        ResourceLocation entityId = entity == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (entityId == null) {
            throw new IllegalArgumentException("Gecko humanoid entity has no registry id");
        }
        if (geoModel == null) {
            throw new IllegalArgumentException("Gecko humanoid renderer has no GeoModel");
        }
        return new SkeletonKey(
                entityId.toString(),
                geoModel.getClass().getName() + "|" + String.valueOf(modelLocation)
        );
    }

    private GeckoResolvedHumanoidRig(
            SourceRigSnapshot snapshot,
            HumanoidSemanticAnalysis analysis,
            HumanoidSkeletonDescriptor descriptor,
            HumanoidSkinWeightResolver weights,
            Map<String, GeoBone> sourceNodes) {
        this.snapshot = snapshot;
        this.analysis = analysis;
        this.descriptor = descriptor;
        this.weights = weights;
        this.sourceNodes = Map.copyOf(sourceNodes);

        IdentityHashMap<GeoBone, HumanoidJoint> directOwners = new IdentityHashMap<>();
        for (HumanoidJoint joint : HumanoidJoint.values()) {
            SemanticJointCandidate candidate = analysis.candidate(joint).orElse(null);
            if (candidate == null || candidate.origin() != JointOrigin.SOURCE) {
                continue;
            }
            candidate.sourceNodeId().ifPresent(sourceId -> {
                GeoBone bone = sourceNodes.get(sourceId);
                if (bone != null) {
                    directOwners.putIfAbsent(bone, joint);
                }
            });
        }

        this.deformationJointIds = new IdentityHashMap<>();
        sourceNodes.forEach((nodeId, bone) -> {
            HumanoidJoint directOwner = directOwners.get(bone);
            if (directOwner != null) {
                this.deformationJointIds.put(
                        bone,
                        HumanoidSkinWeightResolver.deformationJointId(directOwner)
                );
                return;
            }

            int jointId = HumanoidSkinWeightResolver.ROOT;
            try {
                HumanoidSkinWeightResolver.SkinWeights skin = weights.weights(
                        nodeId,
                        snapshot.node(nodeId).bindModelTransform().position()
                );
                jointId = skin.count() == 1 || skin.firstWeight() >= skin.secondWeight()
                        ? skin.firstJoint()
                        : skin.secondJoint();
            } catch (Throwable ignored) {
                // Keep Root as the conservative fallback for an unresolved source bone.
            }
            this.deformationJointIds.put(bone, jointId);
        });
    }

    public HumanoidSkeletonDescriptor descriptor() {
        return descriptor;
    }

    public HumanoidSkinWeightResolver weights() {
        return weights;
    }

    public Optional<GeoBone> sourceBone(HumanoidJoint joint) {
        return analysis.candidate(joint)
                .flatMap(SemanticJointCandidate::sourceNodeId)
                .map(sourceNodes::get);
    }

    public int deformationJointId(GeoBone bone) {
        return deformationJointIds.getOrDefault(bone, HumanoidSkinWeightResolver.ROOT);
    }
}
