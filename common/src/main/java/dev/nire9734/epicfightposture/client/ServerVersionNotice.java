package dev.nire9734.epicfightposture.client;

/**
 * Loader-independent server-version check. The server sends its mod version
 * once at login; the client compares it with its own and shows one chat line
 * when they differ. Mismatched network protocols never get here: the loader
 * refuses those connections and names the versions on its own screen.
 *
 * Plain Java, so the packet callback is safe to link on a dedicated server.
 */
public final class ServerVersionNotice {
    /** Chat line: server version, then the client's version. */
    public static final String TRANSLATION_KEY = "epicfight_posture_integration.server_version_mismatch";
    public static final int MAX_VERSION_LENGTH = 64;

    private static volatile String pendingServerVersion;

    /** Network callback: remember the server's version until the client can show it. */
    public static void receive(String serverVersion) {
        pendingServerVersion = serverVersion;
    }

    /** Cheap per-tick guard before the client looks up its own version. */
    public static boolean hasPending() {
        return pendingServerVersion != null;
    }

    /**
     * Returns the server version at most once per login when it differs from
     * the local version; otherwise null.
     */
    public static synchronized String takeMismatch(String localVersion) {
        String server = pendingServerVersion;
        if (server == null) return null;
        pendingServerVersion = null;
        return differs(server, localVersion) ? server : null;
    }

    public static void clear() {
        pendingServerVersion = null;
    }

    static boolean differs(String serverVersion, String localVersion) {
        return serverVersion != null && localVersion != null
                && !serverVersion.isBlank() && !localVersion.isBlank()
                && !serverVersion.equals(localVersion);
    }

    private ServerVersionNotice() {
    }
}
