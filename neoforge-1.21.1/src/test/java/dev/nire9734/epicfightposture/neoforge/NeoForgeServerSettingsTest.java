package dev.nire9734.epicfightposture.neoforge;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Server policy must match Forge without disabling third-party patches. */
class NeoForgeServerSettingsTest {
    private static final ResourceLocation ZOMBIE = ResourceLocation.tryParse("minecraft:zombie");
    private static final ResourceLocation MODDED = ResourceLocation.tryParse("cataclysm:ignis");

    private static NeoForgeServerSettings.Snapshot snapshot(
            boolean automatic, Set<ResourceLocation> blacklist, Set<String> allow, Set<String> deny) {
        return new NeoForgeServerSettings.Snapshot(1.0D, 1.0D, true,
                automatic, automatic, automatic, blacklist, allow, deny);
    }

    @Test
    void disablingAutomaticCompatibilityOnlyExcludesEfpiOwnedMobs() {
        var settings = snapshot(false, Set.of(), Set.of(), Set.of());
        assertTrue(settings.blocksPosture(MODDED, true));
        assertFalse(settings.blocksPosture(MODDED, false));
        assertTrue(settings.blocksPosture(ZOMBIE, true));
        assertFalse(settings.blocksPosture(ZOMBIE, false));
    }

    @Test
    void explicitBlacklistExcludesBothOwnedAndExistingPatches() {
        var settings = snapshot(true, Set.of(MODDED), Set.of(), Set.of());
        assertTrue(settings.blocksPosture(MODDED, true));
        assertTrue(settings.blocksPosture(MODDED, false));
        assertFalse(settings.blocksPosture(ZOMBIE, true));
    }

    @Test
    void modFiltersOnlyExcludeEfpiOwnedAndNeverExcludeVanilla() {
        var settings = snapshot(true, Set.of(), Set.of("cataclysm"), Set.of("cataclysm"));
        assertTrue(settings.blocksPosture(MODDED, true));
        assertFalse(settings.blocksPosture(MODDED, false));
        assertFalse(settings.blocksPosture(ZOMBIE, true));
    }
}
