package dev.nire9734.epicfightposture.compat.humanoid.client;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceModelPreparationTest {
    @Test
    void restoresHierarchicalPartPoseVisibilityAndModelFlags() {
        ModelPart child = part();
        ModelPart root = new ModelPart(List.of(), Map.of("child", child));
        root.x = 2.0F;
        child.yRot = 0.4F;
        child.xScale = 1.2F;
        child.visible = false;
        child.skipDraw = true;
        DummyHierarchical model = new DummyHierarchical(root);
        model.riding = true;
        model.young = false;
        model.attackTime = 0.75F;

        try (SourceModelPreparation.Scope scope = SourceModelPreparation.openPrepared(model, () -> {
            root.x = 15.0F;
            child.yRot = 2.3F;
            child.xScale = 0.5F;
            child.visible = true;
            child.skipDraw = false;
            model.riding = false;
            model.young = true;
            model.attackTime = 0.2F;
        })) {
            assertTrue(scope.active());
            assertEquals(15.0F, root.x);
        }

        assertEquals(2.0F, root.x);
        assertEquals(0.4F, child.yRot);
        assertEquals(1.2F, child.xScale);
        assertFalse(child.visible);
        assertTrue(child.skipDraw);
        assertTrue(model.riding);
        assertFalse(model.young);
        assertEquals(0.75F, model.attackTime);
    }

    @Test
    void restoresAfterPreparationThrowsBeforeScopeIsReturned() {
        ModelPart root = part();
        root.zRot = 0.7F;
        DummyHierarchical model = new DummyHierarchical(root);
        model.attackTime = 0.6F;

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> SourceModelPreparation.openPrepared(model, () -> {
                    root.zRot = 2.1F;
                    root.visible = false;
                    model.attackTime = 0.3F;
                    throw new IllegalArgumentException("setupAnim failure");
                }));

        assertEquals("setupAnim failure", failure.getMessage());
        assertEquals(0.7F, root.zRot);
        assertTrue(root.visible);
        assertEquals(0.6F, model.attackTime);
    }

    @Test
    void restoresListModelPartsIncludingChildren() {
        ModelPart child = part();
        ModelPart root = new ModelPart(List.of(), Map.of("child", child));
        root.xRot = 1.1F;
        child.z = 4.0F;
        DummyListModel model = new DummyListModel(root);

        SourceModelPreparation.Scope scope = SourceModelPreparation.openPrepared(model, () -> {
            root.xRot = 0.0F;
            child.z = 12.0F;
            model.young = true;
        });
        scope.close();
        scope.close();

        assertEquals(1.1F, root.xRot);
        assertEquals(4.0F, child.z);
        assertFalse(model.young);
    }

    private static ModelPart part() {
        return new ModelPart(List.of(), Map.of());
    }

    private static final class DummyHierarchical extends HierarchicalModel<LivingEntity> {
        private final ModelPart root;

        private DummyHierarchical(ModelPart root) {
            this.root = root;
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
        }
    }

    private static final class DummyListModel extends ListModel<LivingEntity> {
        private final ModelPart root;

        private DummyListModel(ModelPart root) {
            this.root = root;
        }

        @Override
        public Iterable<ModelPart> parts() {
            return List.of(root);
        }

        @Override
        public void setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
        }
    }
}
