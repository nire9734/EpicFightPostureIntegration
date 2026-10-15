package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.rig.SourceNodeSnapshot;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.BodyBounds;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.RigidTransform;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonQuat;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonVec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayerPoseOverrideContextTest {
    @Test
    void secondaryPartUsesGeometryCenterInsteadOfParentSidePivot() {
        SkeletonVec3 pivot = new SkeletonVec3(0.0F, 0.0F, 0.0F);
        BodyBounds geometry = new BodyBounds(
                new SkeletonVec3(-0.1F, 0.7F, -0.1F),
                new SkeletonVec3(0.1F, 0.9F, 0.1F)
        );
        SourceNodeSnapshot node = new SourceNodeSnapshot(
                "arm/decor",
                "decor",
                "arm",
                new RigidTransform(pivot, SkeletonQuat.IDENTITY),
                geometry,
                true
        );

        assertEquals(
                geometry.center(),
                LayerPoseOverrideContext.rigidAnchorPosition(node, false)
        );
    }

    @Test
    void directSemanticPartKeepsAuthoredPivotAsRigidAnchor() {
        SkeletonVec3 pivot = new SkeletonVec3(0.0F, 0.0F, 0.0F);
        BodyBounds geometry = new BodyBounds(
                new SkeletonVec3(-0.1F, 0.4F, -0.1F),
                new SkeletonVec3(0.1F, 1.0F, 0.1F)
        );
        SourceNodeSnapshot node = new SourceNodeSnapshot(
                "arm",
                "right_arm",
                "body",
                new RigidTransform(pivot, SkeletonQuat.IDENTITY),
                geometry,
                true
        );

        assertEquals(
                pivot,
                LayerPoseOverrideContext.rigidAnchorPosition(node, true)
        );
    }

    @Test
    void secondaryPartWithoutGeometryFallsBackToPivot() {
        SkeletonVec3 pivot = new SkeletonVec3(0.2F, 0.6F, -0.1F);
        SourceNodeSnapshot node = new SourceNodeSnapshot(
                "arm/empty",
                "empty",
                "arm",
                new RigidTransform(pivot, SkeletonQuat.IDENTITY),
                null,
                false
        );

        assertEquals(
                pivot,
                LayerPoseOverrideContext.rigidAnchorPosition(node, false)
        );
    }
}
