package dev.nire9734.epicfightposture.neoforge.compat;

import java.util.List;

/**
 * Pure policy matching the Forge server settings. Vanilla candidates bypass
 * the mod allow/deny list, while the explicit entity blacklist always wins.
 */
public final class AutoCompatibilityRules {
    public static boolean allows(String entityId, boolean automaticEnabled,
                                 List<? extends String> entityBlacklist,
                                 List<? extends String> modAllowlist,
                                 List<? extends String> modDenylist) {
        if (!automaticEnabled || entityId == null || !entityId.contains(":")
                || entityBlacklist.contains(entityId)) return false;
        String namespace = entityId.substring(0, entityId.indexOf(':'));
        if ("minecraft".equals(namespace)) return true;
        if (modDenylist.contains(namespace)) return false;
        return modAllowlist.isEmpty() || modAllowlist.contains(namespace);
    }

    private AutoCompatibilityRules() {}
}
