package dev.nire9734.epicfightposture.compat.humanoid.client;

import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.SkinnedMesh;

import java.util.Objects;
import java.util.Optional;

/** Immutable prepared output shared by every model-library humanoid backend. */
public record PreparedHumanoidModel(
        String backendId,
        EntityType<?> entityType,
        ResourceLocation entityId,
        AssetAccessor<SkinnedMesh> mesh,
        Optional<HumanoidSkeletonDescriptor> skeleton) {

    public PreparedHumanoidModel {
        backendId = Objects.requireNonNull(backendId, "backendId");
        entityType = Objects.requireNonNull(entityType, "entityType");
        entityId = Objects.requireNonNull(entityId, "entityId");
        mesh = Objects.requireNonNull(mesh, "mesh");
        skeleton = skeleton == null ? Optional.empty() : skeleton;
    }
}
