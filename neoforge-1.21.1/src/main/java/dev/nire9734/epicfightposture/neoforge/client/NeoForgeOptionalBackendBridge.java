package dev.nire9734.epicfightposture.neoforge.client;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nire9734.epicfightposture.neoforge.compat.NeoForgeLightweightHumanoidPatch;
import dev.nire9734.epicfightposture.compat.humanoid.skeleton.HumanoidSkeletonDescriptor;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.model.Armature;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Mob;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Keep optional API symbols out of the main registry and dedicated-server
 * classpath. A backend is loaded only when its mod is installed.
 */
public final class NeoForgeOptionalBackendBridge {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODELPART = "dev.nire9734.epicfightposture.neoforge.compat.models.NeoForgeModelPartBackend";
    private static final String GECKO = "dev.nire9734.epicfightposture.neoforge.compat.models.NeoForgeGeckoBackend";
    private static final String LIONFISH = "dev.nire9734.epicfightposture.neoforge.compat.models.NeoForgeLionfishBackend";
    private static final Map<String, Method> METHODS = new HashMap<>();
    private static Method geckoLayerMethod;
    private static boolean geckoLayerUnavailable;
    private static Method geckoPostRenderMethod;
    private static boolean geckoPostRenderUnavailable;
    private static Method lionfishLayerMethod;
    private static boolean lionfishLayerUnavailable;
    private static final java.util.Set<String> UNAVAILABLE = new java.util.HashSet<>();

    public static boolean mightBeGeckoRenderer(EntityRenderer<?> source) {
        if (!ModList.get().isLoaded("geckolib") || source == null) return false;
        for (Class<?> c = source.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals("software.bernie.geckolib.renderer.GeoEntityRenderer")) return true;
        }
        return false;
    }

    /**
     * Match Forge HumanoidBackendRegistry: accept exactly ONE prepared model.
     * An ambiguous result is not resolved by backend registration order.
     */
    public static NeoForgePreparedRig inspect(EntityType<?> type, EntityRenderer<?> source) {
        List<NeoForgePreparedRig> candidates = new ArrayList<>(3);
        NeoForgePreparedRig modelPart = invoke(MODELPART, type, source);
        if (modelPart != null) candidates.add(modelPart);
        if (ModList.get().isLoaded("geckolib")) {
            NeoForgePreparedRig geo = invoke(GECKO, type, source);
            if (geo != null) candidates.add(geo);
        }
        if (ModList.get().isLoaded("lionfishapi")) {
            NeoForgePreparedRig lionfish = invoke(LIONFISH, type, source);
            if (lionfish != null) candidates.add(lionfish);
        }
        if (candidates.size() > 1) {
            LOGGER.debug("EFPI rejected ambiguous humanoid renderer for {}: {}", type,
                    candidates.stream().map(NeoForgePreparedRig::backend).toList());
        }
        return NeoForgeBackendSelection.unique(candidates);
    }

    /** Replay GeckoLib held items / per-bone layers only for a verified Gecko renderer. */
    public static void renderGeckoAttachments(Mob entity, NeoForgeLightweightHumanoidPatch<Mob> patch,
                                               EntityRenderer<Mob> renderer, MultiBufferSource buffers,
                                               PoseStack stack, int light, int overlay, float partialTicks,
                                               HumanoidSkeletonDescriptor skeleton) {
        if (geckoLayerUnavailable || !ModList.get().isLoaded("geckolib")) return;
        try {
            Method method = geckoLayerMethod;
            if (method == null) {
                method = Class.forName(GECKO).getMethod("renderAttachments",
                        Mob.class, NeoForgeLightweightHumanoidPatch.class, EntityRenderer.class,
                        MultiBufferSource.class, PoseStack.class, int.class, int.class, float.class,
                        HumanoidSkeletonDescriptor.class);
                geckoLayerMethod = method;
            }
            method.invoke(null, entity, patch, renderer, buffers, stack, light, overlay,
                    partialTicks, skeleton);
        } catch (InvocationTargetException exception) {
            LOGGER.debug("EFPI Gecko attachment replay skipped {} after model-specific error",
                    entity.getType(), exception.getCause());
        } catch (ReflectiveOperationException | LinkageError exception) {
            geckoLayerUnavailable = true;
            LOGGER.warn("EFPI Gecko attachment replay unavailable (API mismatch)", exception);
        }
    }

    /** Queue the exact EF chest matrix for optional post-super Gecko capes. */
    public static void armGeckoPostRenderTorso(Mob entity, NeoForgeLightweightHumanoidPatch<Mob> patch,
                                               EntityRenderer<Mob> renderer) {
        if (geckoPostRenderUnavailable || !ModList.get().isLoaded("geckolib")) return;
        try {
            Method method = geckoPostRenderMethod;
            if (method == null) {
                method = Class.forName(GECKO).getMethod("armPostRenderTorso",
                        Mob.class, NeoForgeLightweightHumanoidPatch.class, EntityRenderer.class);
                geckoPostRenderMethod = method;
            }
            method.invoke(null, entity, patch, renderer);
        } catch (InvocationTargetException exception) {
            LOGGER.debug("EFPI Gecko post-render cape skipped {}", entity.getType(), exception.getCause());
        } catch (ReflectiveOperationException | LinkageError exception) {
            geckoPostRenderUnavailable = true;
            LOGGER.warn("EFPI Gecko post-render cape compatibility unavailable", exception);
        }
    }

    /** Keep all optional Lionfish types out of the base renderer classpath. */
    public static void renderLionfishLayers(Mob entity, EntityRenderer<Mob> renderer,
                                            MultiBufferSource buffers, PoseStack stack,
                                            int light, float partialTicks, Armature armature,
                                            SkinnedMesh mesh, HumanoidSkeletonDescriptor skeleton) {
        if (lionfishLayerUnavailable || !ModList.get().isLoaded("lionfishapi")) return;
        try {
            Method method = lionfishLayerMethod;
            if (method == null) {
                method = Class.forName(LIONFISH).getMethod("renderLayers", Mob.class,
                        EntityRenderer.class, MultiBufferSource.class, PoseStack.class,
                        int.class, float.class, Armature.class, SkinnedMesh.class,
                        HumanoidSkeletonDescriptor.class);
                lionfishLayerMethod = method;
            }
            method.invoke(null, entity, renderer, buffers, stack, light, partialTicks,
                    armature, mesh, skeleton);
        } catch (InvocationTargetException exception) {
            LOGGER.debug("EFPI Lionfish layer replay skipped {} after model-specific error",
                    entity.getType(), exception.getCause());
        } catch (ReflectiveOperationException | LinkageError exception) {
            lionfishLayerUnavailable = true;
            LOGGER.warn("EFPI Lionfish layer replay unavailable (API mismatch)", exception);
        }
    }

    public static void onRendererReload() {
        if (ModList.get().isLoaded("geckolib")) {
            try {
                Class.forName(GECKO).getMethod("clearArmorCaches").invoke(null);
            } catch (ReflectiveOperationException | LinkageError exception) {
                LOGGER.debug("EFPI Gecko armor caches could not be cleared", exception);
            }
        }
        if (!ModList.get().isLoaded("lionfishapi")) return;
        try {
            Class.forName(LIONFISH).getMethod("clearLayerCaches").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            LOGGER.debug("EFPI Lionfish render-layer caches could not be cleared", exception);
        }
    }

    private static NeoForgePreparedRig invoke(String backend, EntityType<?> type,
                                                EntityRenderer<?> source) {
        if (UNAVAILABLE.contains(backend)) return null;
        try {
            Method method = METHODS.get(backend);
            if (method == null) {
                method = Class.forName(backend).getMethod("inspect", EntityType.class, EntityRenderer.class);
                METHODS.put(backend, method);
            }
            Object result = method.invoke(null, type, source);
            return result instanceof NeoForgePreparedRig prepared ? prepared : null;
        } catch (InvocationTargetException exception) {
            // A single modded model can throw while its mesh is being baked.
            // Do not disable this backend for all subsequent entity types.
            LOGGER.debug("EFPI backend {} skipped {} after model-specific failure",
                    backend, type, exception.getCause());
            return null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            UNAVAILABLE.add(backend);
            LOGGER.warn("EFPI optional renderer backend {} disabled (API mismatch)", backend, exception);
            return null;
        }
    }

    private NeoForgeOptionalBackendBridge() {}
}
