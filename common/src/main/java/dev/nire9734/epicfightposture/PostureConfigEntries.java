package dev.nire9734.epicfightposture;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared Forge/NeoForge parsing for entity-specific server configuration.
 * Invalid entries are reported during config loading, never during mob ticks.
 */
public final class PostureConfigEntries {
    public static <T> Set<T> entityBlacklist(
            List<? extends String> entries, Function<String, T> idParser,
            Consumer<String> invalidEntry) {
        Set<T> parsed = new HashSet<>();
        for (String raw : entries) {
            String normalized = raw == null ? "" : raw.trim();
            T id = normalized.isEmpty() ? null : idParser.apply(normalized);
            if (id == null) {
                invalidEntry.accept(raw);
            } else {
                parsed.add(id);
            }
        }
        return Set.copyOf(parsed);
    }

    public static <T> Map<T, Float> entityMultipliers(
            List<? extends String> entries, Function<String, T> idParser,
            Consumer<String> invalidEntry) {
        Map<T, Float> parsed = new HashMap<>();
        for (String raw : entries) {
            String entry = raw == null ? "" : raw.trim();
            int separator = entry.lastIndexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                invalidEntry.accept(raw);
                continue;
            }

            String rawId = entry.substring(0, separator).trim();
            T id = rawId.isEmpty() ? null : idParser.apply(rawId);
            float multiplier;
            try {
                multiplier = Float.parseFloat(entry.substring(separator + 1).trim());
            } catch (NumberFormatException exception) {
                multiplier = Float.NaN;
            }
            if (id == null || !Float.isFinite(multiplier)
                    || multiplier < 0.0F || multiplier > 100.0F) {
                invalidEntry.accept(raw);
                continue;
            }
            parsed.put(id, multiplier);
        }
        return Map.copyOf(parsed);
    }

    /** A mob-specific damage multiplier replaces its corresponding global setting. */
    public static <T> float multiplierFor(T entityId, Map<T, Float> overrides, float global) {
        Float override = entityId == null ? null : overrides.get(entityId);
        return override != null ? override : global;
    }

    private PostureConfigEntries() {}
}
