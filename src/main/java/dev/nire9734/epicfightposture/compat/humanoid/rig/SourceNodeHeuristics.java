package dev.nire9734.epicfightposture.compat.humanoid.rig;

import java.util.Locale;
import java.util.Set;

/** Shared conservative heuristics for separating deforming body nodes from attachments. */
public final class SourceNodeHeuristics {
    private static final Set<String> ATTACHMENT_TOKENS = Set.of(
            "weapon", "held", "item", "tool",
            "bow", "mace", "halberd", "sword", "shield", "spear", "axe", "hammer",
            "anchor", "chain", "particle", "trail", "effect", "socket", "marker"
    );

    public static boolean likelyAttachment(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }

        String separated = name
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
        String[] tokens = separated.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (ATTACHMENT_TOKENS.contains(token)) {
                return true;
            }
        }

        // Some model exporters flatten names such as rightWeapon or trail2.
        // Avoid suffix matching for "bow" because ordinary anatomy names such
        // as "elbow" would otherwise become false positives.
        String normalized = separated.replaceAll("[^a-z0-9]", "");
        for (String token : ATTACHMENT_TOKENS) {
            if ("bow".equals(token)) {
                if (normalized.equals(token) || normalized.startsWith(token)) {
                    return true;
                }
            } else if (normalized.equals(token)
                    || normalized.startsWith(token)
                    || normalized.endsWith(token)) {
                return true;
            }
        }
        return false;
    }

    public static boolean pathHasAttachment(Iterable<String> names) {
        if (names == null) {
            return false;
        }
        for (String name : names) {
            if (likelyAttachment(name)) {
                return true;
            }
        }
        return false;
    }

    private SourceNodeHeuristics() {
    }
}
