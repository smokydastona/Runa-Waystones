package com.example.waystoneinjector.client.serverside;

/**
 * Client-side cache for the server icon bytes.
 *
 * The server mod sends server-icon.png bytes to modded clients. This cache exists so
 * other client features (future UI, buttons, overlays) can use it without needing
 * direct dependencies on the server mod.
 */
public final class ServerIconCache {

    private static volatile byte[] lastPngBytes;

    private ServerIconCache() {
    }

    public static void set(byte[] pngBytes) {
        lastPngBytes = pngBytes;
    }

    public static byte[] get() {
        return lastPngBytes;
    }
}
