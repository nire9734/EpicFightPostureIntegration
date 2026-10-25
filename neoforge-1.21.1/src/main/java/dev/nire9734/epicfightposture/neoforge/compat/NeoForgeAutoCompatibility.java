package dev.nire9734.epicfightposture.neoforge.compat;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryRuntime;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryStore;
import dev.nire9734.epicfightposture.neoforge.NeoForgeServerSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import yesman.epicfight.api.event.EpicFightEventHooks;
import yesman.epicfight.api.event.types.registry.EntityPatchRegistryEvent;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.world.capabilities.provider.CommonEntityPatchProvider;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.function.Function;
import net.minecraft.world.entity.Entity;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import dev.nire9734.epicfightposture.neoforge.discovery.NeoForgeRendererDiscoveryServer;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryReportProcessor;


/**
 * Low-priority ownership-aware Epic Fight 21.17 registry extension.
 *
 * Verified client renderers and trusted operator reports expand this registry
 * without taking ownership of existing Epic Fight or datapack providers.
 */
public final class NeoForgeAutoCompatibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<EntityType<?>> OWNED = new HashSet<>();
    private static final Set<EntityType<?>> SPIDER_OWNED = new HashSet<>();
    private static final Set<EntityType<?>> RUNTIME_REJECTED = new HashSet<>();
    private static final Set<EntityType<?>> EXTERNALLY_PATCHED = new HashSet<>();
    /** Dedicated-server registrations requiring a currently conflict-free OP observation. */
    private static final Map<EntityType<?>, RendererDiscoveryRuntime.Kind> TRUSTED_DEDICATED = new IdentityHashMap<>();
    // Exact factories installed by EFPI, retained across integrated-server
    // world switches. This distinguishes our patches from addon replacements.
    private static final Map<EntityType<?>, Set<Object>> PREVIOUS_FACTORIES = new IdentityHashMap<>();
    private static final Map<EntityType<?>, AssetAccessor<? extends Armature>> RUNTIME_ARMATURES = new IdentityHashMap<>();
    private static boolean subscribed;

    public static synchronized void register() {
        if (subscribed) return;
        subscribed = true;
        EpicFightEventHooks.Registry.ENTITY_PATCH.registerEvent(
                NeoForgeAutoCompatibility::onPatchRegistry,
                "epicfight_posture_integration:auto_compatibility", -10000);
    }

    @SuppressWarnings("deprecation")
    private static synchronized void onPatchRegistry(EntityPatchRegistryEvent event) {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            RendererDiscoveryReportProcessor.invalidateRuntimeValidation();
        }
        Set<EntityType<?>> previousOwned = new HashSet<>(OWNED);
        Set<EntityType<?>> previousSpiders = new HashSet<>(SPIDER_OWNED);
        Set<EntityType<?>> previousRejected = new HashSet<>(RUNTIME_REJECTED);
        Map<EntityType<?>, RendererDiscoveryRuntime.Kind> previousTrusted =
                new IdentityHashMap<>(TRUSTED_DEDICATED);
        OWNED.clear();
        SPIDER_OWNED.clear();
        RUNTIME_REJECTED.clear();
        EXTERNALLY_PATCHED.clear();
        TRUSTED_DEDICATED.clear();
        EXTERNALLY_PATCHED.addAll(event.getTypeEntry().keySet());
        for (EntityType<?> type : previousOwned) {
            if (isOurFactory(type, event.getTypeEntry().get(type))) {
                OWNED.add(type);
                if (previousSpiders.contains(type)) SPIDER_OWNED.add(type);
                if (previousTrusted.containsKey(type)) {
                    TRUSTED_DEDICATED.put(type, previousTrusted.get(type));
                }
                EXTERNALLY_PATCHED.remove(type);
            }
        }
        if (!NeoForgeServerSettings.hasRegistryCandidates()) return;

        // Respect datapack-owned providers even if they are not exposed by
        // getTypeEntry(). Failure to inspect ownership disables this feature.
        Map<?, ?> datapackOverrides;
        try {
            Field field = CommonEntityPatchProvider.class.getDeclaredField("datapackCapabilities");
            field.setAccessible(true);
            Object value = field.get(CommonEntityPatchProvider.INSTANCE);
            if (!(value instanceof Map<?, ?> map)) {
                LOGGER.warn("EFPI NeoForge compatibility disabled: datapack provider registry is not a Map");
                return;
            }
            datapackOverrides = map;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("EFPI NeoForge compatibility disabled: cannot inspect existing datapack patch ownership",
                    exception);
            return;
        }

        // A new integrated-server world may dispatch this registry event
        // without dispatching another client renderer scan. Retain previously
        // verified targets so the client login hook can restore missing
        // physical providers. Never retain one taken over by an addon.
        for (EntityType<?> type : previousOwned) {
            if (OWNED.contains(type) || event.getTypeEntry().containsKey(type)
                    || datapackOverrides.containsKey(type)
                    || !NeoForgeServerSettings.canRegister(BuiltInRegistries.ENTITY_TYPE.getKey(type))) continue;
            try {
                Map<EntityType<?>, Function<Entity, EntityPatch<?>>> providers = providerMap("capabilities");
                if (providers == null || (providers.containsKey(type)
                        && !isOurFactory(type, providers.get(type)))) continue;
                OWNED.add(type);
                if (previousSpiders.contains(type)) SPIDER_OWNED.add(type);
                if (previousTrusted.containsKey(type)) {
                    TRUSTED_DEDICATED.put(type, previousTrusted.get(type));
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                LOGGER.debug("EFPI could not preserve renderer patch ownership for {}", type, exception);
            }
        }

        // These two models use the normal humanoid skeletal layout in vanilla
        // 1.21.1; they are not in Epic Fight's built-in patch registration list.
        registerCandidate(event, EntityType.BOGGED, datapackOverrides);
        registerCandidate(event, EntityType.GIANT, datapackOverrides);
        // These are previous-start, conflict-free observations from a
        // permissioned operator; the model is not inferred on the server.
        java.util.Set<ResourceLocation> approved = new java.util.HashSet<>();
        approved.addAll(NeoForgeRendererDiscoveryServer.activeHumanoids());
        approved.addAll(NeoForgeRendererDiscoveryServer.activeExtended());
        approved.addAll(NeoForgeRendererDiscoveryServer.activeSpiders());
        for (ResourceLocation id : approved) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null || type == EntityType.PLAYER
                    || OWNED.contains(type) || EXTERNALLY_PATCHED.contains(type)
                    || event.getTypeEntry().containsKey(type) || datapackOverrides.containsKey(type)
                    || !NeoForgeServerSettings.canRegister(id)) continue;
            boolean spider = NeoForgeRendererDiscoveryServer.activeSpiders().contains(id);
            if (spider) {
                Armatures.registerEntityTypeArmature(type, Armatures.SPIDER);
                event.getTypeEntry().put(type, entity -> entity instanceof LivingEntity mob
                        ? new NeoForgeLightweightSpiderPatch<>(mob) : null);
                SPIDER_OWNED.add(type);
            } else {
                Armatures.registerEntityTypeArmature(type, Armatures.BIPED);
                event.getTypeEntry().put(type, entity -> entity instanceof LivingEntity mob
                        ? new NeoForgeLightweightHumanoidPatch<>(mob) : null);
            }
            rememberFactory(type, event.getTypeEntry().get(type));
            OWNED.add(type);
            if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
                TRUSTED_DEDICATED.put(type, spider ? RendererDiscoveryRuntime.Kind.VANILLA_SPIDER
                        : NeoForgeRendererDiscoveryServer.activeExtended().contains(id)
                            ? RendererDiscoveryRuntime.Kind.EXTENDED_HUMANOID
                            : RendererDiscoveryRuntime.Kind.VANILLA_HUMANOID);
            }
            LOGGER.info("EFPI activated trusted dedicated-server {} renderer target {}",
                    spider ? "spider" : "humanoid", id);
        }
        RUNTIME_REJECTED.addAll(previousRejected);
        LOGGER.info("EFPI registered {} NeoForge automatic compatibility target(s)", OWNED.size());
    }

    @SuppressWarnings("deprecation")
    private static <T extends Mob> void registerCandidate(EntityPatchRegistryEvent event,
                                                           EntityType<T> type, Map<?, ?> overrides) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (!NeoForgeServerSettings.canRegister(id)) return;
        if (event.getTypeEntry().containsKey(type) || overrides.containsKey(type)) {
            LOGGER.debug("EFPI skipped {} because another Epic Fight patch already owns it", id);
            return;
        }
        Armatures.registerEntityTypeArmature(type, Armatures.BIPED);
        event.registerEntityPatch(type, NeoForgeLightweightHumanoidPatch::new);
        rememberFactory(type, event.getTypeEntry().get(type));
        OWNED.add(type);
        LOGGER.debug("EFPI registered non-invasive NeoForge compatibility for {}", id);
    }

    /** Hot-disable the EFPI-owned patch when config/blacklist changes. */
    public static synchronized boolean isEnabled(EntityType<?> type) {
        ResourceLocation id = type == null ? null : BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return type != null && OWNED.contains(type) && !RUNTIME_REJECTED.contains(type)
                && (FMLEnvironment.dist != Dist.DEDICATED_SERVER
                    || !TRUSTED_DEDICATED.containsKey(type)
                    || RendererDiscoveryStore.isRuntimeApproved(id, TRUSTED_DEDICATED.get(type)))
                && NeoForgeServerSettings.allows(id);
    }

    /** Applies an authenticated, conflict-free discovery on the server without replacing addon patches. */
    public static synchronized boolean registerTrustedDedicatedTarget(
            ResourceLocation id, RendererDiscoveryRuntime.Kind kind) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || id == null || kind == null
                || !RendererDiscoveryStore.isRuntimeApproved(id, kind)
                || !NeoForgeServerSettings.canRegister(id)) return false;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null || type == EntityType.PLAYER) return false;
        RendererDiscoveryRuntime.Kind previous = TRUSTED_DEDICATED.get(type);
        if (previous != null && previous != kind) return false;
        boolean registered = kind == RendererDiscoveryRuntime.Kind.VANILLA_SPIDER
                ? registerVerifiedRuntimeSpider(type)
                : registerVerifiedRuntimeHumanoid(type);
        if (registered) TRUSTED_DEDICATED.put(type, kind);
        return registered;
    }

    /** Only EFPI-owned registrations are subject to the automatic-policy gate. */
    public static synchronized boolean isOwnedByEfpi(EntityType<?> type) {
        return type != null && OWNED.contains(type) && !EXTERNALLY_PATCHED.contains(type);
    }

    /** Snapshot from the registry event before EFPI added any patches. */
    public static synchronized boolean isExternallyPatched(EntityType<?> type) {
        return type == null || EXTERNALLY_PATCHED.contains(type);
    }

    /** Invalidate previous renderer verifications on model/resource reload. */
    public static synchronized void beginClientRendererScan() {
        RUNTIME_REJECTED.addAll(OWNED);
    }

    /**
     * Renderer discovery runs during initial resource loading, before a server
     * can send its policy. Prepare a client-side superset at that point; the
     * actual patch and rendering gates still use the authoritative policy in
     * {@link #isEnabled(EntityType)}. This also avoids requiring a resource
     * reload and a second world join to discover modded renderers.
     */
    public static synchronized boolean mayPrepareRenderer(EntityType<?> type) {
        if (type == null || type == EntityType.PLAYER) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return NeoForgeServerSettings.canRegister(id);
    }

    /** Only called on client after verifying renderer ownership and skin mesh. */
    public static synchronized boolean registerVerifiedRuntimeHumanoid(EntityType<?> type) {
        return registerVerifiedRuntimeHumanoid(type, null);
    }

    public static synchronized boolean registerVerifiedRuntimeHumanoid(
            EntityType<?> type, AssetAccessor<? extends Armature> armatureOverride) {
        if (!mayPrepareRenderer(type) || EXTERNALLY_PATCHED.contains(type)) return false;
        try {
            Map<EntityType<?>, Function<Entity, EntityPatch<?>>> providers =
                    providerMap("capabilities");
            Map<EntityType<?>, Function<Entity, EntityPatch<?>>> datapacks =
                    providerMap("datapackCapabilities");
            if (providers == null || datapacks == null || datapacks.containsKey(type)) return false;
            if (SPIDER_OWNED.contains(type)) return false;
            if (providers.containsKey(type)) {
                if (!isOurFactory(type, providers.get(type))) {
                    RUNTIME_REJECTED.add(type);
                    return false;
                }
            } else {
                // A renderer reload is not guaranteed when the integrated
                // server rebuilds Epic Fight's provider registry.
                Function<Entity, EntityPatch<?>> factory = entity -> entity instanceof LivingEntity mob
                        ? new NeoForgeLightweightHumanoidPatch<>(mob) : null;
                providers.put(type, factory);
                rememberFactory(type, factory);
            }
            OWNED.add(type);
            Armatures.registerEntityTypeArmature(type,
                    armatureOverride == null ? Armatures.BIPED : armatureOverride);
            if (armatureOverride != null) {
                RUNTIME_ARMATURES.put(type, armatureOverride);
            }
            RUNTIME_REJECTED.remove(type);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            RUNTIME_REJECTED.add(type);
            LOGGER.warn("EFPI client runtime patch registration skipped for {}", type, exception);
            return false;
        }
    }

    public static synchronized boolean registerVerifiedRuntimeSpider(EntityType<?> type) {
        if (!mayPrepareRenderer(type) || EXTERNALLY_PATCHED.contains(type)) return false;
        try {
            Map<EntityType<?>, Function<Entity, EntityPatch<?>>> providers = providerMap("capabilities");
            Map<EntityType<?>, Function<Entity, EntityPatch<?>>> datapacks = providerMap("datapackCapabilities");
            if (providers == null || datapacks == null || datapacks.containsKey(type)) return false;
            if (OWNED.contains(type) && !SPIDER_OWNED.contains(type)) return false;
            if (providers.containsKey(type)) {
                if (!isOurFactory(type, providers.get(type))) {
                    RUNTIME_REJECTED.add(type);
                    return false;
                }
            } else {
                Function<Entity, EntityPatch<?>> factory = entity -> entity instanceof LivingEntity mob
                        ? new NeoForgeLightweightSpiderPatch<>(mob) : null;
                providers.put(type, factory);
                rememberFactory(type, factory);
            }
            Armatures.registerEntityTypeArmature(type, Armatures.SPIDER);
            OWNED.add(type);
            SPIDER_OWNED.add(type);
            RUNTIME_REJECTED.remove(type);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.warn("EFPI spider patch registration skipped {}", type, exception);
            RUNTIME_REJECTED.add(type);
            return false;
        }
    }

    public static synchronized void rejectRuntimeCompatibility(EntityType<?> type) {
        if (type != null && OWNED.contains(type)) RUNTIME_REJECTED.add(type);
    }

    /** Restore only missing EFPI providers on a subsequent world join. */
    public static synchronized void recoverClientRegistrationsOnWorldJoin() {
        int recovered = 0;
        for (EntityType<?> type : new HashSet<>(OWNED)) {
            if (RUNTIME_REJECTED.contains(type)) continue;
            try {
                Map<EntityType<?>, Function<Entity, EntityPatch<?>>> providers = providerMap("capabilities");
                Map<EntityType<?>, Function<Entity, EntityPatch<?>>> datapacks = providerMap("datapackCapabilities");
                if (providers == null || datapacks == null || datapacks.containsKey(type)) {
                    RUNTIME_REJECTED.add(type);
                    continue;
                }
                if (providers.containsKey(type)) {
                    if (!isOurFactory(type, providers.get(type))) {
                        RUNTIME_REJECTED.add(type);
                    } else if (SPIDER_OWNED.contains(type)) {
                        registerVerifiedRuntimeSpider(type);
                    } else {
                        registerVerifiedRuntimeHumanoid(type, RUNTIME_ARMATURES.get(type));
                    }
                    continue;
                }
                boolean restored = SPIDER_OWNED.contains(type)
                        ? registerVerifiedRuntimeSpider(type)
                        : registerVerifiedRuntimeHumanoid(type, RUNTIME_ARMATURES.get(type));
                if (restored) recovered++;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                RUNTIME_REJECTED.add(type);
                LOGGER.warn("EFPI could not revalidate the patch provider for {} after world join", type, exception);
            }
        }
        if (recovered > 0) {
            LOGGER.info("EFPI recovered {} NeoForge compatibility provider(s) after world join", recovered);
        }
    }

    /**
     * Saved entities can enter the integrated-server level before client login.
     * Epic Fight's AttachmentEntityPatchProvider captures its EntityPatch on
     * first access, so restore missing EFPI providers before normal join hooks
     * have a chance to request that attachment.
     */
    public static synchronized void recoverClientProviderBeforeEntityJoin(EntityType<?> type) {
        if (type == null || !OWNED.contains(type) || RUNTIME_REJECTED.contains(type)) return;
        try {
            Map<EntityType<?>, Function<Entity, EntityPatch<?>>> providers = providerMap("capabilities");
            Map<EntityType<?>, Function<Entity, EntityPatch<?>>> datapacks = providerMap("datapackCapabilities");
            if (providers == null || datapacks == null || datapacks.containsKey(type)) {
                RUNTIME_REJECTED.add(type);
                return;
            }
            if (providers.containsKey(type)) {
                if (!isOurFactory(type, providers.get(type))) RUNTIME_REJECTED.add(type);
                return;
            }
            boolean restored = SPIDER_OWNED.contains(type)
                    ? registerVerifiedRuntimeSpider(type)
                    : registerVerifiedRuntimeHumanoid(type, RUNTIME_ARMATURES.get(type));
            if (restored) {
                LOGGER.info("EFPI recovered NeoForge provider before saved entity joined: {}", type);
            } else {
                RUNTIME_REJECTED.add(type);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            RUNTIME_REJECTED.add(type);
            LOGGER.warn("EFPI could not recover provider before saved entity joined: {}", type, exception);
        }
    }

    private static void rememberFactory(EntityType<?> type, Object factory) {
        if (type == null || factory == null) return;
        PREVIOUS_FACTORIES.computeIfAbsent(type,
                unused -> Collections.newSetFromMap(new IdentityHashMap<>())).add(factory);
    }

    private static boolean isOurFactory(EntityType<?> type, Object factory) {
        return factory != null && PREVIOUS_FACTORIES.containsKey(type)
                && PREVIOUS_FACTORIES.get(type).contains(factory);
    }

    @SuppressWarnings("unchecked")
    private static Map<EntityType<?>, Function<Entity, EntityPatch<?>>> providerMap(String name)
            throws ReflectiveOperationException {
        Field field = CommonEntityPatchProvider.class.getDeclaredField(name);
        field.setAccessible(true);
        Object object = field.get(CommonEntityPatchProvider.INSTANCE);
        return object instanceof Map<?, ?>
                ? (Map<EntityType<?>, Function<Entity, EntityPatch<?>>>) object : null;
    }

    private NeoForgeAutoCompatibility() {}
}
