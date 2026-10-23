package dev.nire9734.epicfightposture.compat.humanoid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Portable, deterministic hash of mod-id/version pairs. */
public final class ModSetFingerprint {
    public static String fromEntries(List<String> modVersions) {
        String source = String.join("\n", modVersions.stream().sorted().toList());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ModSetFingerprint() { }
}
