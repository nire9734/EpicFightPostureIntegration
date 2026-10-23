package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.compat.humanoid.retarget.HumanoidSkinWeightResolver;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.RenderUtils;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * One-shot render-thread bridge for Gecko renderers that draw attachments after
 * {@code GeoEntityRenderer.render()} returns.
 *
 * <p>Normal Gecko render layers are handled directly by {@link GeckoAttachmentBridge}.
 * A few renderers (notably Iron's Spells' HumanoidRenderer cape path) later call
 * {@code RenderUtils.prepMatrixForBone} themselves. For those calls we preserve
 * the attachment's native Gecko local bind transform, but prepend the exact Epic
 * Fight skin matrix used by the body:</p>
 *
 * <pre>
 * postRender = (poseMatrix * joint.toOrigin) * nativeGeoBoneLocalBind
 * </pre>
 *
 * <p>The source anchor comes from the same semantic Gecko resolution used by body
 * baking and GeoRenderLayer replay; no literal source bone name is required.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class GeckoPostRenderMatrixOverride {
    private static final ThreadLocal<Entry> NEXT = new ThreadLocal<>();

    public static void clear() {
        NEXT.remove();
    }

    /**
     * Arms an exact one-shot Chest-source matrix for renderer-specific post-super
     * attachments such as Iron's ArmorCapeLayer.
     */
    public static void armTorsoFromEpicFight(
            LivingEntity entity,
            LivingEntityPatch<LivingEntity> entityPatch,
            EntityRenderer<LivingEntity> sourceRenderer) {
        clear();

        if (!(sourceRenderer instanceof GeoEntityRenderer geoRenderer)
                || !(entity instanceof GeoAnimatable animatable)) {
            return;
        }

        try {
            GeoModel geoModel = geoRenderer.getGeoModel();
            ResourceLocation modelLocation = geoModel.getModelResource(animatable);
            BakedGeoModel bakedModel = geoModel.getBakedModel(modelLocation);
            if (bakedModel == null || bakedModel.topLevelBones() == null) {
                return;
            }

            GeckoResolvedHumanoidRig resolved = GeckoResolvedHumanoidRig.resolve(
                    bakedModel,
                    GeckoResolvedHumanoidRig.keyFor(entity, geoModel, modelLocation),
                    GeckoBindPoseTransforms.OPERATIONS
            );
            GeoBone chestSource = resolved.sourceBone(HumanoidJoint.CHEST).orElse(null);
            if (chestSource == null) {
                return;
            }

            // GeckoAttachmentBridge may have synchronized the live source bone
            // for generic fallback post-render code. The exact cape path must
            // instead start from the authored local bind state that the source
            // renderer would normally feed to RenderUtils.prepMatrixForBone.
            resetToInitial(chestSource);

            PoseStack localBindStack = new PoseStack();
            RenderUtils.prepMatrixForBone(localBindStack, chestSource);
            Matrix4f localBind = new Matrix4f(localBindStack.last().pose());

            Armature armature = entityPatch.getArmature();
            OpenMatrix4f[] poseMatrices = armature.getPoseMatrices();
            int chestJointId = HumanoidSkinWeightResolver.CHEST;
            if (poseMatrices == null
                    || chestJointId < 0
                    || chestJointId >= poseMatrices.length
                    || poseMatrices[chestJointId] == null) {
                return;
            }

            Joint chest = armature.searchJointById(chestJointId);
            if (chest == null) {
                return;
            }

            // Mirror SkinnedMesh.drawPosed exactly: pose * inverse bind.
            OpenMatrix4f skin = new OpenMatrix4f(poseMatrices[chestJointId]);
            skin.mulBack(chest.getToOrigin());
            Matrix4f skinMatrix = OpenMatrix4f.exportToMojangMatrix(skin);

            arm(chestSource, new Matrix4f(skinMatrix).mul(localBind));
        } catch (Throwable ignored) {
            // Optional compatibility path. If a renderer/model differs from the
            // expected Gecko humanoid layout, leave its normal call untouched.
            clear();
        }
    }

    public static void arm(CoreGeoBone bone, Matrix4f matrix) {
        if (bone == null || matrix == null) {
            NEXT.remove();
            return;
        }

        NEXT.set(new Entry(bone, new Matrix4f(matrix)));
    }

    /**
     * Applies and consumes the pending matrix only for the exact GeoBone that
     * armed it. Returning false means the caller should execute GeckoLib's
     * normal {@code prepMatrixForBone} implementation.
     */
    public static boolean applyIfPresent(PoseStack poseStack, CoreGeoBone bone) {
        Entry entry = NEXT.get();
        if (entry == null || entry.bone() != bone) {
            return false;
        }

        NEXT.remove();
        poseStack.mulPoseMatrix(new Matrix4f(entry.matrix()));
        return true;
    }

    private static void resetToInitial(GeoBone bone) {
        BoneSnapshot snapshot = bone.getInitialSnapshot();
        if (snapshot == null) {
            return;
        }

        bone.setPosX(snapshot.getOffsetX());
        bone.setPosY(snapshot.getOffsetY());
        bone.setPosZ(snapshot.getOffsetZ());
        bone.setRotX(snapshot.getRotX());
        bone.setRotY(snapshot.getRotY());
        bone.setRotZ(snapshot.getRotZ());
        bone.setScaleX(snapshot.getScaleX());
        bone.setScaleY(snapshot.getScaleY());
        bone.setScaleZ(snapshot.getScaleZ());
    }

    private record Entry(CoreGeoBone bone, Matrix4f matrix) {
    }

    private GeckoPostRenderMatrixOverride() {
    }
}
