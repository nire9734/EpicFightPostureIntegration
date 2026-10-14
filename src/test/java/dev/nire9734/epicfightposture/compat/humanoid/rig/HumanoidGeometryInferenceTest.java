package dev.nire9734.epicfightposture.compat.humanoid.rig;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanoidGeometryInferenceTest {
    private static final float EPSILON = 1.0E-6F;

    @Test
    void distalCenterUsesFaceCenterInsteadOfBoundingBoxCorner() {
        BodyBounds leg = new BodyBounds(
                new SkeletonVec3(-0.25F, 0.0F, -0.20F),
                new SkeletonVec3(0.25F, 1.0F, 0.20F)
        );

        SkeletonVec3 terminal = HumanoidGeometryInference.distalCenter(
                leg,
                new SkeletonVec3(0.0F, 1.0F, 0.0F)
        );

        assertEquals(0.0F, terminal.x(), EPSILON);
        assertEquals(0.0F, terminal.y(), EPSILON);
        assertEquals(0.0F, terminal.z(), EPSILON);
    }

    @Test
    void distalCenterFollowsHorizontalDominantAxisForArms() {
        BodyBounds arm = new BodyBounds(
                new SkeletonVec3(0.0F, -0.2F, -0.2F),
                new SkeletonVec3(2.0F, 0.2F, 0.2F)
        );

        SkeletonVec3 terminal = HumanoidGeometryInference.distalCenter(
                arm,
                SkeletonVec3.ZERO
        );

        assertEquals(2.0F, terminal.x(), EPSILON);
        assertEquals(0.0F, terminal.y(), EPSILON);
        assertEquals(0.0F, terminal.z(), EPSILON);
    }

    @Test
    void subtreeBoundsExcludeAttachmentGeometry() {
        Map<String, SourceNodeSnapshot> nodes = new LinkedHashMap<>();
        nodes.put("leg", new SourceNodeSnapshot(
                "leg", "left_leg", null, RigidTransform.IDENTITY,
                new BodyBounds(new SkeletonVec3(-0.25F, 0.0F, -0.25F),
                        new SkeletonVec3(0.25F, 1.0F, 0.25F)), true));
        nodes.put("foot", new SourceNodeSnapshot(
                "foot", "left_foot", "leg", RigidTransform.IDENTITY, null, false));
        nodes.put("weapon", new SourceNodeSnapshot(
                "weapon", "sword_attachment", "leg", RigidTransform.IDENTITY,
                new BodyBounds(new SkeletonVec3(50.0F, 50.0F, 50.0F),
                        new SkeletonVec3(60.0F, 60.0F, 60.0F)), true));

        SourceRigSnapshot snapshot = new SourceRigSnapshot(
                new SkeletonKey(new ResourceLocation("test", "humanoid"), "geometry-test"),
                nodes,
                new BodyBounds(new SkeletonVec3(-1.0F, 0.0F, -1.0F),
                        new SkeletonVec3(60.0F, 60.0F, 60.0F)),
                false
        );

        BodyBounds bounds = HumanoidGeometryInference.subtreeBounds(snapshot, "leg").orElseThrow();
        assertEquals(0.25F, bounds.max().x(), EPSILON);
        assertEquals(1.0F, bounds.max().y(), EPSILON);
        assertTrue(HumanoidGeometryInference.subtreeBounds(snapshot, "weapon").isEmpty());
    }
}
