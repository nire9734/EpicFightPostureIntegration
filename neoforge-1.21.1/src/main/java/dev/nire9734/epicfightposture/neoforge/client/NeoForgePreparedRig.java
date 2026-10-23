package dev.nire9734.epicfightposture.neoforge.client;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.client.model.SkinnedMesh;
import java.util.Objects;
import dev.nire9734.epicfightposture.compat.humanoid.client.ModelPartAttachmentPlan;

/** Result of a high-confidence GeckoLib/Lionfish bind-pose model conversion. */
public record NeoForgePreparedRig(AssetAccessor<SkinnedMesh> mesh,
                                   HumanoidSkeletonDescriptor skeleton, String backend,
                                   ModelPartAttachmentPlan attachments) {
    public NeoForgePreparedRig(AssetAccessor<SkinnedMesh> mesh,
                               HumanoidSkeletonDescriptor skeleton, String backend) {
        this(mesh, skeleton, backend, null);
    }

    public NeoForgePreparedRig {
        Objects.requireNonNull(mesh);
        Objects.requireNonNull(skeleton);
        Objects.requireNonNull(backend);
    }
}
