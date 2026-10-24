package dev.nire9734.epicfightposture.compat.humanoid;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.function.BiPredicate;

/** Shared, conservative dispatch of trusted discoveries to each loader's provider registry. */
public final class RendererDiscoveryRuntime {
    public enum Kind {
        VANILLA_HUMANOID, EXTENDED_HUMANOID, VANILLA_SPIDER
    }

    public record Result(int ready, int skipped) {
    }

    public record ActivationUpdate(int ready, int skipped, int newlyReady, int newlySkipped) {
    }

    /** Result of activating patches on entities that predate an operator report. */
    public record LiveRefresh(int updated, int failed) {
    }

    /** Returns ready only after the loader confirms ownership of the active provider. */
    public static Result activate(RendererDiscoveryStore.ActiveTargets targets,
                                  BiPredicate<ResourceLocation, Kind> register) {
        int ready = 0;
        int skipped = 0;
        for (Kind kind : Kind.values()) {
            Set<ResourceLocation> ids = switch (kind) {
                case VANILLA_HUMANOID -> targets.vanillaHumanoid();
                case EXTENDED_HUMANOID -> targets.extendedHumanoid();
                case VANILLA_SPIDER -> targets.vanillaSpider();
            };
            for (ResourceLocation id : ids) {
                if (register.test(id, kind)) {
                    ready++;
                } else {
                    skipped++;
                }
            }
        }
        return new Result(ready, skipped);
    }

    /** Count newly activated targets separately from repeat OP reports. */
    public static ActivationUpdate activateTrackingChanges(
            RendererDiscoveryStore.ActiveTargets previous,
            RendererDiscoveryStore.ActiveTargets targets,
            BiPredicate<ResourceLocation, Kind> register) {
        int ready = 0;
        int skipped = 0;
        int newlyReady = 0;
        int newlySkipped = 0;
        for (Kind kind : Kind.values()) {
            Set<ResourceLocation> priorIds = idsFor(previous, kind);
            for (ResourceLocation id : idsFor(targets, kind)) {
                boolean isNew = !priorIds.contains(id);
                if (register.test(id, kind)) {
                    ready++;
                    if (isNew) newlyReady++;
                } else {
                    skipped++;
                    if (isNew) newlySkipped++;
                }
            }
        }
        return new ActivationUpdate(ready, skipped, newlyReady, newlySkipped);
    }

    public static int withdrawnTargets(
            RendererDiscoveryStore.ActiveTargets previous,
            RendererDiscoveryStore.ActiveTargets current) {
        int withdrawn = 0;
        for (Kind kind : Kind.values()) {
            Set<ResourceLocation> remaining = idsFor(current, kind);
            for (ResourceLocation id : idsFor(previous, kind)) {
                if (!remaining.contains(id)) withdrawn++;
            }
        }
        return withdrawn;
    }

    private static Set<ResourceLocation> idsFor(RendererDiscoveryStore.ActiveTargets targets, Kind kind) {
        if (targets == null) return Set.of();
        return switch (kind) {
            case VANILLA_HUMANOID -> targets.vanillaHumanoid();
            case EXTENDED_HUMANOID -> targets.extendedHumanoid();
            case VANILLA_SPIDER -> targets.vanillaSpider();
        };
    }

    private RendererDiscoveryRuntime() {
    }
}
