package dev.nire9734.epicfightposture.compat.humanoid;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.spider.LightweightSpiderPatch;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.GlobalMobPatch;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.provider.EntityPatchProvider;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Attach EFPI-owned Epic Fight patches to mobs already present when an operator
 * confirms renderer compatibility. No entity is unloaded, replaced or respawned.
 *
 * <p>Forge normally attaches EntityPatchProvider during entity construction.
 * Existing entities have already passed that event. Epic Fight normally leaves
 * its generic GlobalMobPatch in their dispatcher before renderer approval, so
 * we replace only that fallback provider. If no patch exists we append our
 * provider, preserving all unrelated capabilities and serialized data.
 * Every unsuccessful per-mob installation is rolled back.</p>
 */
final class ForgeExistingPatchRefresh {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Field dispatcherField;
    private static Field providersField;
    private static boolean reflectionUnavailable;

    static RendererDiscoveryRuntime.LiveRefresh refresh(MinecraftServer server) {
        if (server == null || !server.isSameThread()) {
            LOGGER.warn("EFPI live Forge patch refresh skipped: not on the server thread");
            return new RendererDiscoveryRuntime.LiveRefresh(0, 1);
        }
        if (!resolveReflection()) {
            return new RendererDiscoveryRuntime.LiveRefresh(0, 1);
        }
        int updated = 0;
        int failed = 0;
        int loadedMobs = 0;
        int compatibleMobs = 0;
        int ownedMobs = 0;
        int alreadyPatched = 0;
        int replaceableFallbacks = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob) || mob.isRemoved()) {
                    continue;
                }
                loadedMobs++;
                if (!VanillaHumanoidCompat.isCompatibilityEnabled(mob.getType())) continue;
                compatibleMobs++;
                if (EpicFightPatchOwnership.state(mob.getType()) != EpicFightPatchOwnership.State.OWNED) continue;
                ownedMobs++;
                // Only Epic Fight's exact generic fallback may be replaced.
                // Keep genuine native/addon patches, including subclasses of GlobalMobPatch.
                EntityPatch<?> existing = EpicFightCapabilities.getEntityPatch(mob, EntityPatch.class);
                if (existing != null && existing.getClass() != GlobalMobPatch.class) {
                    alreadyPatched++;
                    LOGGER.debug("EFPI kept existing Epic Fight patch {} for {} ({})",
                            existing.getClass().getName(), mob.getType(), mob.getUUID());
                    continue;
                }
                if (existing != null) replaceableFallbacks++;
                try {
                    if (install(mob, level, existing)) {
                        updated++;
                    }
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    failed++;
                    LOGGER.warn("EFPI could not attach a live Epic Fight patch to {} ({})",
                            mob.getType(), mob.getUUID(), exception);
                }
            }
        }
        LOGGER.info("EFPI Forge live patch refresh: {} loaded mobs, {} compatibility-enabled, "
                        + "{} EFPI-owned, {} replaceable fallback patches, {} retained patches, {} updated, {} failed",
                loadedMobs, compatibleMobs, ownedMobs, replaceableFallbacks, alreadyPatched, updated, failed);
        return new RendererDiscoveryRuntime.LiveRefresh(updated, failed);
    }

    private static boolean install(Mob mob, ServerLevel level, EntityPatch<?> previousPatch)
            throws ReflectiveOperationException {
        EntityPatchProvider provider = new EntityPatchProvider(mob);
        EntityPatch<?> candidate = provider.get();
        if (!(candidate instanceof LightweightHumanoidPatch)
                && !(candidate instanceof LightweightSpiderPatch)) {
            // A changed provider registry must not cause a foreign patch to be inserted.
            throw new IllegalStateException("EFPI registry no longer produces its lightweight patch");
        }
        @SuppressWarnings("unchecked")
        LivingEntityPatch<net.minecraft.world.entity.LivingEntity> patch =
                (LivingEntityPatch<net.minecraft.world.entity.LivingEntity>) candidate;
        // Epic Fight's construction event calls this before adding the provider.
        patch.onConstructed(mob);

        CapabilityDispatcher previousDispatcher = (CapabilityDispatcher) dispatcherField.get(mob);
        ICapabilityProvider[] previousProviders = null;
        if (previousDispatcher == null) {
            CapabilityDispatcher appended = new CapabilityDispatcher(
                    Map.of(new ResourceLocation("epicfight_posture_integration", "live_entity_patch"), provider),
                    List.of());
            dispatcherField.set(mob, appended);
        } else {
            previousProviders = (ICapabilityProvider[]) providersField.get(previousDispatcher);
            if (previousProviders == null) {
                throw new IllegalStateException("Forge capability provider array unavailable");
            }
            int replacementIndex = -1;
            if (previousPatch != null) {
                for (int index = 0; index < previousProviders.length; index++) {
                    if (previousProviders[index] instanceof EntityPatchProvider previousProvider
                            && previousProvider.get() == previousPatch) {
                        if (replacementIndex != -1) {
                            throw new IllegalStateException("Multiple Epic Fight fallback providers found");
                        }
                        replacementIndex = index;
                    }
                }
                if (replacementIndex == -1) {
                    throw new IllegalStateException("Cannot identify the original Epic Fight fallback provider");
                }
            }
            ICapabilityProvider[] expanded = Arrays.copyOf(previousProviders,
                    previousProviders.length + (replacementIndex < 0 ? 1 : 0));
            if (replacementIndex >= 0) {
                expanded[replacementIndex] = provider;
            } else {
                expanded[previousProviders.length] = provider;
            }
            providersField.set(previousDispatcher, expanded);
        }

        try {
            if (EpicFightCapabilities.getEntityPatch(mob, EntityPatch.class) != candidate) {
                throw new IllegalStateException("New Epic Fight patch is not visible to capability lookups");
            }
            patch.onJoinWorld(mob, new EntityJoinLevelEvent(mob, level, true));
            if (!patch.isInitialized()) {
                throw new IllegalStateException("New Epic Fight patch did not initialize");
            }
            return true;
        } catch (RuntimeException | Error failure) {
            // Restore the original dispatcher/array, retaining all addon capability state.
            if (previousDispatcher == null) {
                dispatcherField.set(mob, null);
            } else {
                providersField.set(previousDispatcher, previousProviders);
            }
            throw failure;
        }
    }

    private static synchronized boolean resolveReflection() {
        if (reflectionUnavailable) return false;
        if (dispatcherField != null && providersField != null) return true;
        try {
            Field dispatcher = CapabilityProvider.class.getDeclaredField("capabilities");
            Field providers = CapabilityDispatcher.class.getDeclaredField("caps");
            dispatcher.setAccessible(true);
            providers.setAccessible(true);
            dispatcherField = dispatcher;
            providersField = providers;
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            reflectionUnavailable = true;
            LOGGER.warn("EFPI cannot safely update existing Forge mobs without capability dispatcher access", exception);
            return false;
        }
    }

    private ForgeExistingPatchRefresh() {}
}
