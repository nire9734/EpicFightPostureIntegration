package dev.nire9734.epicfightposture;

/**
 * Network protocol identifier shared by both loaders. NeoForge prints it
 * verbatim on its mismatch screen, so it names the first mod version that
 * speaks this protocol in words a player can act on ("mod 3.3.0 or later").
 * Change it only when the wire format changes, to the version introducing
 * the new format; same-protocol releases keep it unchanged.
 */
public final class NetworkProtocol {
    public static final String VERSION = "mod 3.3.0 or later";

    private NetworkProtocol() {
    }
}
