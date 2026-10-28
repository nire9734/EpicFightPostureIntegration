package dev.nire9734.epicfightposture.neoforge.compat;

import com.mojang.logging.LogUtils;
import dev.nire9734.epicfightposture.compat.humanoid.RendererDiscoveryRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.attachment.AttachmentType;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import yesman.epicfight.registry.entries.EpicFightAttachmentTypes;
import yesman.epicfight.world.capabilities.entitypatch.EntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.GlobalMobPatch;
import yesman.epicfight.world.capabilities.provider.AttachmentEntityPatchProvider;

/**
 * Refresh the Epic Fight attachment on EFPI-owned, already-loaded mobs.
 * Only an empty attachment or Epic Fight's exact generic GlobalMobPatch fallback
 * may be replaced; all native/addon patches remain untouched.
 */
public final class NeoForgeExistingPatchRefresh {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static RendererDiscoveryRuntime.LiveRefresh refresh(MinecraftServer server,
                                                               Set<ResourceLocation> affectedTypes) {
        if (server == null || !server.isSameThread()) {
            LOGGER.warn("EFPI live NeoForge patch refresh skipped: not on the server thread");
            return new RendererDiscoveryRuntime.LiveRefresh(0, 1);
        }
        if (affectedTypes.isEmpty()) {
            return new RendererDiscoveryRuntime.LiveRefresh(0, 0);
        }
        int updated = 0;
        int failed = 0;
        Set<EntityType<?>> targets = new HashSet<>();
        for (ResourceLocation id : affectedTypes) {
            BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(targets::add);
        }
        AttachmentType<AttachmentEntityPatchProvider> attachment = EpicFightAttachmentTypes.ENTITY_PATCH.get();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob) || mob.isRemoved()
                        || !targets.contains(mob.getType())
                        || !NeoForgeAutoCompatibility.isOwnedByEfpi(mob.getType())
                        || !NeoForgeAutoCompatibility.isEnabled(mob.getType())) {
                    continue;
                }
                try {
                    if (refreshMob(mob, level, attachment)) {
                        updated++;
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    LOGGER.warn("EFPI could not initialize a live Epic Fight patch for {} ({})",
                            mob.getType(), mob.getUUID(), exception);
                }
            }
        }
        if (updated > 0 || failed > 0) {
            LOGGER.info("EFPI NeoForge live patch refresh: {} existing mobs updated, {} failed", updated, failed);
        }
        return new RendererDiscoveryRuntime.LiveRefresh(updated, failed);
    }

    private static boolean refreshMob(Mob mob, ServerLevel level,
                                      AttachmentType<AttachmentEntityPatchProvider> attachment) {
        AttachmentEntityPatchProvider previous = mob.getData(attachment);
        EntityPatch<?> current = previous.getCapability();
        if (current != null && current.getClass() != GlobalMobPatch.class) {
            // Only finish an EFPI-owned patch that already exists but was never initialized.
            if (!isOurPatch(current) || !current.uninitialized()) return false;
            initialize(current, mob, level);
            return true;
        }

        AttachmentEntityPatchProvider fresh = new AttachmentEntityPatchProvider(mob);
        EntityPatch<?> patch = fresh.getCapability();
        if (!isOurPatch(patch)) {
            throw new IllegalStateException("EFPI registry no longer produces its lightweight patch");
        }
        mob.setData(attachment, fresh);
        try {
            initialize(patch, mob, level);
            return true;
        } catch (RuntimeException | Error exception) {
            mob.setData(attachment, previous);
            throw exception;
        }
    }

    private static boolean isOurPatch(EntityPatch<?> patch) {
        return patch instanceof NeoForgeLightweightHumanoidPatch<?>
                || patch instanceof NeoForgeLightweightSpiderPatch<?>;
    }

    @SuppressWarnings("unchecked")
    private static void initialize(EntityPatch<?> patch, Mob mob, ServerLevel level) {
        EntityPatch<Mob> typed = (EntityPatch<Mob>) patch;
        typed.onConstructed(mob);
        typed.onJoinWorld(mob, level, true);
        if (typed.uninitialized()) {
            throw new IllegalStateException("New Epic Fight patch did not initialize");
        }
    }

    private NeoForgeExistingPatchRefresh() {}
}
