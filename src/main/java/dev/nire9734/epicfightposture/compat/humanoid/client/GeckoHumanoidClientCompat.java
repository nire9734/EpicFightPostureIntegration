package dev.nire9734.epicfightposture.compat.humanoid.client;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.EpicFightPostureIntegration;
import dev.nire9734.epicfightposture.compat.humanoid.rig.ConservativeHumanoidSemanticAnalyzer;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.SkeletonKey;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.asset.SelfAccessor;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.SkinnedMesh;

import java.util.Optional;

/** Optional GeckoLib 4.x humanoid backend. Loaded only when GeckoLib is present. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class GeckoHumanoidClientCompat implements HumanoidRenderBackend {
    public static final GeckoHumanoidClientCompat INSTANCE = new GeckoHumanoidClientCompat();
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String id() {
        return "geckolib";
    }

    @Override
    public Inspection inspect(EntityType<?> entityType, EntityRenderer<?> sourceRenderer) {
        if (!(sourceRenderer instanceof GeoEntityRenderer<?> geoRenderer)) {
            return Inspection.unmatched();
        }

        ResourceLocation entityId = EntityType.getKey(entityType);
        if (entityId == null) {
            return Inspection.rejected();
        }

        try {
            GeoModel geoModel = geoRenderer.getGeoModel();
            ResourceLocation modelLocation = geoModel.getModelResource(null);
            BakedGeoModel bakedModel = geoModel.getBakedModel(modelLocation);

            HumanoidSkeletonDescriptor descriptor = null;
            try {
                SkeletonKey key = new SkeletonKey(
                        entityId,
                        geoModel.getClass().getName() + "|" + String.valueOf(modelLocation)
                );
                descriptor = GeckoSourceRigAdapter.resolve(
                        bakedModel,
                        key,
                        ConservativeHumanoidSemanticAnalyzer.INSTANCE
                ).descriptor();
            } catch (Throwable retargetFailure) {
                LOGGER.debug(
                        "EFPI humanoid rig resolver rejected Gecko model {}: {}",
                        entityId,
                        retargetFailure.getMessage()
                );
            }

            // Keep the proven Gecko mesh/topology path authoritative while the
            // framework-neutral rig resolver is being broadened across model libraries.
            GeckoHumanoidModelAdapter.ProbeResult probe = GeckoHumanoidModelAdapter.probe(bakedModel);
            if (!probe.supported()) {
                LOGGER.debug("EFPI Gecko humanoid probe rejected {}: {}", entityId, probe.reason());
                return Inspection.rejected();
            }

            GeckoHumanoidTopologyValidator.ValidationResult topology =
                    GeckoHumanoidTopologyValidator.validate(bakedModel);
            if (!topology.supported()) {
                LOGGER.debug("EFPI Gecko humanoid topology rejected {}: {}", entityId, topology.reason());
                return Inspection.rejected();
            }

            GeckoHumanoidModelAdapter.Result result = GeckoHumanoidModelAdapter.analyseAndBake(bakedModel);
            if (!result.supported() || result.mesh() == null) {
                LOGGER.warn(
                        "EFPI detected Gecko humanoid {} but could not prepare its special-state mesh: {}",
                        entityId,
                        result.reason()
                );
                return Inspection.rejected();
            }

            ResourceLocation runtimeMeshId = new ResourceLocation(
                    EpicFightPostureIntegration.MODID,
                    "runtime_gecko/" + entityId.getNamespace() + "/" + entityId.getPath()
            );
            AssetAccessor<SkinnedMesh> mesh = SelfAccessor.create(runtimeMeshId, result.mesh());
            if (descriptor == null) {
                LOGGER.debug(
                        "EFPI Gecko compatibility accepted {} without a target skeleton; canonical BIPED will be used",
                        entityId
                );
            }

            return Inspection.supported(new PreparedHumanoidModel(
                    id(),
                    entityType,
                    entityId,
                    mesh,
                    Optional.ofNullable(descriptor)
            ));
        } catch (Throwable throwable) {
            LOGGER.warn("EFPI could not inspect GeckoLib model for {}; skipping automatic compatibility", entityId, throwable);
            return Inspection.rejected();
        }
    }

    @Override
    public void addPatchedRenderer(PatchedRenderersEvent.Add event, PreparedHumanoidModel prepared) {
        event.addPatchedEntityRenderer(
                prepared.entityType(),
                type -> new SpecialStateGeckoRenderer(prepared.mesh(), event.getContext())
        );
    }

    private GeckoHumanoidClientCompat() {
    }
}
