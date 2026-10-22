package dev.nire9734.epicfightposture.compat.humanoid.retarget;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidTopology;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigQuality;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecondaryDeformationPolicyTest {
    @Test
    void allArmSamplesRemainRigid() {
        var arm = HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.ARM_RIGHT);
        var decision = SecondaryDeformationPolicy.classify(List.of(arm, arm, arm)).orElseThrow();

        assertEquals(SecondaryDeformationPolicy.Mode.RIGID_ATTACHMENT, decision.mode());
        assertEquals(HumanoidSkinWeightResolver.ARM_RIGHT, decision.rigidJointId());
    }

    @Test
    void armToHandTransitionIsDeformable() {
        var decision = SecondaryDeformationPolicy.classify(List.of(
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.ARM_RIGHT),
                HumanoidSkinWeightResolver.SkinWeights.blend(
                        HumanoidSkinWeightResolver.ARM_RIGHT,
                        HumanoidSkinWeightResolver.HAND_RIGHT,
                        0.5F,
                        0.5F),
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.HAND_RIGHT)
        )).orElseThrow();

        assertEquals(SecondaryDeformationPolicy.Mode.DEFORMABLE, decision.mode());
    }

    @Test
    void thinArmSurfaceUsesSegmentedAttachmentAtElbow() {
        var decision = SecondaryDeformationPolicy.classifySurfaceAttachment(List.of(
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.ARM_RIGHT),
                HumanoidSkinWeightResolver.SkinWeights.blend(
                        HumanoidSkinWeightResolver.ARM_RIGHT,
                        HumanoidSkinWeightResolver.HAND_RIGHT,
                        0.09F,
                        0.91F),
                HumanoidSkinWeightResolver.SkinWeights.blend(
                        HumanoidSkinWeightResolver.ARM_RIGHT,
                        HumanoidSkinWeightResolver.HAND_RIGHT,
                        0.04F,
                        0.96F)
        )).orElseThrow();

        assertEquals(SecondaryDeformationPolicy.Mode.SEGMENTED_ATTACHMENT, decision.mode());
    }

    @Test
    void thinLegSurfaceUsesSegmentedAttachmentAtKnee() {
        var decision = SecondaryDeformationPolicy.classifySurfaceAttachment(List.of(
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.THIGH_RIGHT),
                HumanoidSkinWeightResolver.SkinWeights.blend(
                        HumanoidSkinWeightResolver.THIGH_RIGHT,
                        HumanoidSkinWeightResolver.LEG_RIGHT,
                        0.25F,
                        0.75F)
        )).orElseThrow();

        assertEquals(SecondaryDeformationPolicy.Mode.SEGMENTED_ATTACHMENT, decision.mode());
    }

    @Test
    void thinTorsoSurfaceStillDeformsContinuously() {
        var decision = SecondaryDeformationPolicy.classifySurfaceAttachment(List.of(
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.ROOT),
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.CHEST)
        )).orElseThrow();

        assertEquals(SecondaryDeformationPolicy.Mode.DEFORMABLE, decision.mode());
    }

    @Test
    void rootToChestTransitionIsDeformable() {
        var decision = SecondaryDeformationPolicy.classify(List.of(
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.ROOT),
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.CHEST)
        )).orElseThrow();

        assertEquals(SecondaryDeformationPolicy.Mode.DEFORMABLE, decision.mode());
    }

    @Test
    void thighToLegTransitionIsDeformable() {
        var decision = SecondaryDeformationPolicy.classify(List.of(
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.THIGH_RIGHT),
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.LEG_RIGHT)
        )).orElseThrow();

        assertEquals(SecondaryDeformationPolicy.Mode.DEFORMABLE, decision.mode());
    }

    @Test
    void smallHandLocalAccessoryRemainsRigid() {
        var decision = SecondaryDeformationPolicy.classify(List.of(
                HumanoidSkinWeightResolver.SkinWeights.blend(
                        HumanoidSkinWeightResolver.ARM_RIGHT,
                        HumanoidSkinWeightResolver.HAND_RIGHT,
                        0.2F,
                        0.8F),
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.HAND_RIGHT)
        )).orElseThrow();

        assertEquals(SecondaryDeformationPolicy.Mode.RIGID_ATTACHMENT, decision.mode());
        assertEquals(HumanoidSkinWeightResolver.HAND_RIGHT, decision.rigidJointId());
    }

    @Test
    void oneBoneSleeveUsesBodyArmTransition() {
        HumanoidSkeletonDescriptor descriptor = descriptor();
        var shoulder = HumanoidSkinWeightResolver.semanticWeights(
                descriptor,
                HumanoidJoint.SHOULDER_RIGHT,
                new SkeletonVec3(0.5F, 2.0F, 0.0F));
        var elbow = HumanoidSkinWeightResolver.semanticWeights(
                descriptor,
                HumanoidJoint.SHOULDER_RIGHT,
                new SkeletonVec3(0.5F, 1.5F, 0.0F));
        var wrist = HumanoidSkinWeightResolver.semanticWeights(
                descriptor,
                HumanoidJoint.SHOULDER_RIGHT,
                new SkeletonVec3(0.5F, 1.0F, 0.0F));

        assertEquals(HumanoidSkinWeightResolver.ARM_RIGHT, SecondaryDeformationPolicy.dominantJoint(shoulder));
        assertEquals(2, elbow.count());
        assertEquals(0.5F, elbow.firstWeight(), 1.0E-5F);
        assertEquals(0.5F, elbow.secondWeight(), 1.0E-5F);
        assertEquals(HumanoidSkinWeightResolver.HAND_RIGHT, SecondaryDeformationPolicy.dominantJoint(wrist));
        assertEquals(
                SecondaryDeformationPolicy.Mode.DEFORMABLE,
                SecondaryDeformationPolicy.classify(List.of(shoulder, elbow, wrist)).orElseThrow().mode()
        );
    }

    @Test
    void unrelatedBodyChainsFailClosed() {
        assertTrue(SecondaryDeformationPolicy.classify(List.of(
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.ARM_RIGHT),
                HumanoidSkinWeightResolver.SkinWeights.rigid(HumanoidSkinWeightResolver.CHEST)
        )).isEmpty());
    }

    private static HumanoidSkeletonDescriptor descriptor() {
        EnumMap<HumanoidJoint, JointDescriptor> joints = new EnumMap<>(HumanoidJoint.class);
        put(joints, HumanoidJoint.ROOT, 0.0F, 0.0F, 0.0F);
        put(joints, HumanoidJoint.SPINE, 0.0F, 1.0F, 0.0F);
        put(joints, HumanoidJoint.CHEST, 0.0F, 2.0F, 0.0F);
        put(joints, HumanoidJoint.NECK, 0.0F, 2.4F, 0.0F);
        put(joints, HumanoidJoint.HEAD, 0.0F, 2.8F, 0.0F);

        put(joints, HumanoidJoint.SHOULDER_RIGHT, 0.5F, 2.0F, 0.0F);
        put(joints, HumanoidJoint.ELBOW_RIGHT, 0.5F, 1.5F, 0.0F);
        put(joints, HumanoidJoint.WRIST_RIGHT, 0.5F, 1.0F, 0.0F);
        put(joints, HumanoidJoint.SHOULDER_LEFT, -0.5F, 2.0F, 0.0F);
        put(joints, HumanoidJoint.ELBOW_LEFT, -0.5F, 1.5F, 0.0F);
        put(joints, HumanoidJoint.WRIST_LEFT, -0.5F, 1.0F, 0.0F);

        put(joints, HumanoidJoint.HIP_RIGHT, 0.2F, 0.0F, 0.0F);
        put(joints, HumanoidJoint.KNEE_RIGHT, 0.2F, -0.5F, 0.0F);
        put(joints, HumanoidJoint.ANKLE_RIGHT, 0.2F, -1.0F, 0.0F);
        put(joints, HumanoidJoint.HIP_LEFT, -0.2F, 0.0F, 0.0F);
        put(joints, HumanoidJoint.KNEE_LEFT, -0.2F, -0.5F, 0.0F);
        put(joints, HumanoidJoint.ANKLE_LEFT, -0.2F, -1.0F, 0.0F);

        assertEquals(HumanoidTopology.REQUIRED.size(), joints.size());
        return new HumanoidSkeletonDescriptor(
                new SkeletonKey("test:secondary", "secondary-policy"),
                joints,
                new BodyBounds(
                        new SkeletonVec3(-1.0F, -1.0F, -0.25F),
                        new SkeletonVec3(1.0F, 3.0F, 0.25F)),
                new RigQuality(1.0F, joints.size(), 0, 0, false, false)
        );
    }

    private static void put(
            EnumMap<HumanoidJoint, JointDescriptor> joints,
            HumanoidJoint joint,
            float x,
            float y,
            float z) {
        joints.put(joint, new JointDescriptor(
                joint,
                new SkeletonVec3(x, y, z),
                JointOrigin.SOURCE,
                1.0F
        ));
    }
}
