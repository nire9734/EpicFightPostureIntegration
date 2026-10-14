package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidJoint;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.JointOrigin;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonQuat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanoidSkeletonResolverTest {
    private static final float EPSILON = 1.0E-5F;

    @Test
    void recoversCollapsedFootHelpersFromParentLegGeometry() {
        Map<String, SourceNodeSnapshot> nodes = new LinkedHashMap<>();
        nodes.put("root", node("root", "root", null, 0.0F, 0.0F, 0.0F, null));
        nodes.put("body", node("body", "body", "root", 0.0F, 1.25F, 0.0F,
                bounds(-0.25F, 0.75F, -0.15F, 0.25F, 1.55F, 0.15F)));
        nodes.put("head", node("head", "head", "body", 0.0F, 1.75F, 0.0F,
                bounds(-0.25F, 1.55F, -0.25F, 0.25F, 2.05F, 0.25F)));
        nodes.put("left_arm", node("left_arm", "left_arm", "body", -0.35F, 1.40F, 0.0F,
                bounds(-0.80F, 1.30F, -0.10F, -0.30F, 1.50F, 0.10F)));
        nodes.put("right_arm", node("right_arm", "right_arm", "body", 0.35F, 1.40F, 0.0F,
                bounds(0.30F, 1.30F, -0.10F, 0.80F, 1.50F, 0.10F)));
        nodes.put("left_hand", node("left_hand", "left_hand", "left_arm", -0.75F, 1.40F, 0.0F,
                bounds(-0.85F, 1.30F, -0.10F, -0.70F, 1.50F, 0.10F)));
        nodes.put("right_hand", node("right_hand", "right_hand", "right_arm", 0.75F, 1.40F, 0.0F,
                bounds(0.70F, 1.30F, -0.10F, 0.85F, 1.50F, 0.10F)));
        nodes.put("left_leg", node("left_leg", "left_leg", "root", -0.125F, 0.75F, 0.0F,
                bounds(-0.25F, 0.0F, -0.12F, 0.0F, 0.75F, 0.12F)));
        nodes.put("right_leg", node("right_leg", "right_leg", "root", 0.125F, 0.75F, 0.0F,
                bounds(0.0F, 0.0F, -0.12F, 0.25F, 0.75F, 0.12F)));
        // Empty helpers deliberately share the hip pivots, matching the Iron's failure mode.
        nodes.put("left_foot", node("left_foot", "left_foot", "left_leg", -0.125F, 0.75F, 0.0F, null));
        nodes.put("right_foot", node("right_foot", "right_foot", "right_leg", 0.125F, 0.75F, 0.0F, null));

        SourceRigSnapshot snapshot = new SourceRigSnapshot(
                new SkeletonKey(new ResourceLocation("test", "collapsed_feet"), "collapsed-feet"),
                nodes,
                bounds(-0.85F, 0.0F, -0.25F, 0.85F, 2.05F, 0.25F),
                false
        );

        EnumMap<HumanoidJoint, SemanticJointCandidate> candidates = new EnumMap<>(HumanoidJoint.class);
        source(candidates, snapshot, HumanoidJoint.CHEST, "body");
        source(candidates, snapshot, HumanoidJoint.HEAD, "head");
        source(candidates, snapshot, HumanoidJoint.SHOULDER_LEFT, "left_arm");
        source(candidates, snapshot, HumanoidJoint.SHOULDER_RIGHT, "right_arm");
        source(candidates, snapshot, HumanoidJoint.WRIST_LEFT, "left_hand");
        source(candidates, snapshot, HumanoidJoint.WRIST_RIGHT, "right_hand");
        source(candidates, snapshot, HumanoidJoint.HIP_LEFT, "left_leg");
        source(candidates, snapshot, HumanoidJoint.HIP_RIGHT, "right_leg");
        source(candidates, snapshot, HumanoidJoint.ANKLE_LEFT, "left_foot");
        source(candidates, snapshot, HumanoidJoint.ANKLE_RIGHT, "right_foot");

        HumanoidSkeletonDescriptor descriptor = HumanoidSkeletonResolver.resolve(
                snapshot,
                new HumanoidSemanticAnalysis(candidates)
        );

        SkeletonVec3 leftHip = descriptor.bindModelPosition(HumanoidJoint.HIP_LEFT);
        SkeletonVec3 leftKnee = descriptor.bindModelPosition(HumanoidJoint.KNEE_LEFT);
        SkeletonVec3 leftAnkle = descriptor.bindModelPosition(HumanoidJoint.ANKLE_LEFT);

        assertEquals(-0.125F, leftAnkle.x(), EPSILON);
        assertEquals(0.0F, leftAnkle.y(), EPSILON);
        assertEquals(0.0F, leftAnkle.z(), EPSILON);
        assertTrue(leftHip.distanceTo(leftKnee) > EPSILON);
        assertTrue(leftKnee.distanceTo(leftAnkle) > EPSILON);
        assertEquals(0.375F, leftKnee.y(), EPSILON);
    }

    private static SourceNodeSnapshot node(
            String id,
            String name,
            String parent,
            float x,
            float y,
            float z,
            BodyBounds geometry) {
        return new SourceNodeSnapshot(
                id,
                name,
                parent,
                new RigidTransform(new SkeletonVec3(x, y, z), SkeletonQuat.IDENTITY),
                geometry,
                geometry != null
        );
    }

    private static BodyBounds bounds(
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ) {
        return new BodyBounds(
                new SkeletonVec3(minX, minY, minZ),
                new SkeletonVec3(maxX, maxY, maxZ)
        );
    }

    private static void source(
            EnumMap<HumanoidJoint, SemanticJointCandidate> candidates,
            SourceRigSnapshot snapshot,
            HumanoidJoint joint,
            String sourceNodeId) {
        candidates.put(joint, SemanticJointCandidate.fromSource(
                snapshot,
                joint,
                sourceNodeId,
                JointOrigin.SOURCE,
                1.0F
        ));
    }
}
