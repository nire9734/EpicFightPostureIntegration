package dev.nire9734.epicfightposture;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared Forge/NeoForge parsing for entity-specific server configuration.
 * Invalid entries are reported during config loading, never during mob ticks.
 */
public final class PostureConfigEntries {
    public static Set<ResourceLocation> entityBlacklist(
            List<? extends String> entries, Consumer<String> invalidEntry) {
        Set<ResourceLocation> parsed = new HashSet<>();
        for (String raw : entries) {
            ResourceLocation id = ResourceLocation.tryParse(raw == null ? "" : raw.trim());
            if (id == null) {
                invalidEntry.accept(raw);
            } else {
                parsed.add(id);
            }
        }
        return Set.copyOf(parsed);
    }

    public static Map<ResourceLocation, Float> entityMultipliers(
            List<? extends String> entries, Consumer<String> invalidEntry) {
        Map<ResourceLocation, Float> parsed = new HashMap<>();
        for (String raw : entries) {
            String entry = raw == null ? "" : raw.trim();
            int separator = entry.lastIndexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                invalidEntry.accept(raw);
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(entry.substring(0, separator).trim());
            float multiplier;
            try {
                multiplier = Float.parseFloat(entry.substring(separator + 1).trim());
            } catch (NumberFormatException exception) {
                multiplier = Float.NaN;
            }
            if (id == null || !Float.isFinite(multiplier)
                    || multiplier < 0.01F || multiplier > 100.0F) {
                invalidEntry.accept(raw);
                continue;
            }
            parsed.put(id, multiplier);
        }
        return Map.copyOf(parsed);
    }

    private PostureConfigEntries() {}
}
