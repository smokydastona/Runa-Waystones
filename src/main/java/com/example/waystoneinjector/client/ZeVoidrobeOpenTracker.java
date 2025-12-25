package com.example.waystoneinjector.client;

/**
 * Tracks when the user explicitly requested opening the Ze Voidrobe.
 *
 * This avoids relying on server-provided screen titles for identifying the correct ChestScreen
 * to reskin (older server jars may use older titles).
 */
public final class ZeVoidrobeOpenTracker {
    private static final long WINDOW_MS = 4000L;

    private static volatile long lastRequestMs = 0L;

    private ZeVoidrobeOpenTracker() {
    }

    public static void markRequested() {
        lastRequestMs = System.currentTimeMillis();
    }

    public static boolean wasRecentlyRequested() {
        long age = System.currentTimeMillis() - lastRequestMs;
        return age >= 0L && age <= WINDOW_MS;
    }

    public static void clear() {
        lastRequestMs = 0L;
    }
}
