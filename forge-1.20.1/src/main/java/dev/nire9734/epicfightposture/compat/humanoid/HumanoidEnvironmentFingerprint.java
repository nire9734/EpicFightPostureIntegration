package dev.nire9734.epicfightposture.compat.humanoid;

import net.minecraftforge.fml.ModList;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Stable hash of the currently loaded mod IDs and versions. */
public final class HumanoidEnvironmentFingerprint {
    public static String current() {
        List<String> entries = ModList.get().getMods().stream()
                .map(info -> info.getModId() + "@" + info.getVersion())
                .sorted()
                .toList();
        String source = String.join("\n", entries);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private HumanoidEnvironmentFingerprint() {
    }
}
