package dev.nire9734.epicfightposture;

/**
 * Network protocol identifier shared by both loaders. It names the first mod
 * version that speaks this protocol, so a loader's mismatch screen shows a
 * readable version ("3.3.0+") instead of an opaque number. Change it only
 * when the wire format changes, to the version introducing the new format.
 */
public final class NetworkProtocol {
    public static final String VERSION = "3.3.0+";

    private NetworkProtocol() {
    }
}
