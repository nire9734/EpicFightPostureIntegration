package dev.nire9734.epicfightposture.mixin.client;

import dev.nire9734.epicfightposture.compat.humanoid.client.RuntimeMeshOwnership;
import it.unimi.dsi.fastutil.ints.IntList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import yesman.epicfight.api.client.model.MeshPartDefinition;
import yesman.epicfight.api.client.model.SingleGroupVertexBuilder;
import yesman.epicfight.api.client.model.SkinnedMesh;

import java.util.List;
import java.util.Map;

/** Marks ModelPart body and attachment runtime mesh construction as EFPI-owned. */
@Mixin(targets = "dev.nire9734.epicfightposture.compat.humanoid.client.ModelPartHumanoidMeshAdapter", remap = false)
public abstract class ModelPartRuntimeMeshOwnershipMixin {
    @Redirect(
            method = {"bakeBody", "bakeAttachments"},
            at = @At(
                    value = "INVOKE",
                    target = "Lyesman/epicfight/api/client/model/SingleGroupVertexBuilder;loadVertexInformation(Ljava/util/List;Ljava/util/Map;)Lyesman/epicfight/api/client/model/SkinnedMesh;"
            )
    )
    private static SkinnedMesh efpi$buildOwnedRuntimeMesh(
            List<SingleGroupVertexBuilder> vertices,
            Map<MeshPartDefinition, IntList> indices) {
        return RuntimeMeshOwnership.buildOwned(vertices, indices);
    }
}
