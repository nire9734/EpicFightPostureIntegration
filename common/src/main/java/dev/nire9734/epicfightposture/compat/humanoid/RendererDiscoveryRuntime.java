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

    private RendererDiscoveryRuntime() {
    }
}
