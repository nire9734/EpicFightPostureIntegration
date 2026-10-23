package dev.nire9734.epicfightposture.compat.humanoid;

/** Loader-independent renderer discovery cache name and operator notices. */
public final class RendererDiscoveryShared {
    public static final String CACHE_FILE_NAME = "renderer-discovery.json";

    public static String restartRequiredMessage(
            int vanillaHumanoids,
            int extendedHumanoids,
            int vanillaSpiders,
            int conflicts) {
        int confirmed = vanillaHumanoids + extendedHumanoids + vanillaSpiders;
        return "EFPI: renderer discovery: "
                + confirmed + " confirmed ("
                + vanillaHumanoids + " vanilla humanoid, "
                + extendedHumanoids + " extended humanoid, "
                + vanillaSpiders + " vanilla SpiderModel), "
                + conflicts + " conflict(s)."
                + " Restart the server to apply the pending compatibility list.";
    }

    private RendererDiscoveryShared() {
    }
}
