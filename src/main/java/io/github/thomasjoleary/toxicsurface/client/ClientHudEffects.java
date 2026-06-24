// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

/**
 * Transient client-side screen-effect cues (DESIGN.md §3). Currently the filter-expire HUD flash:
 * the server sends a cue the moment protection drops, and {@code ScreenEffectsOverlay} fades a red
 * vignette over the next {@link #FLASH_MILLIS}. Wall-clock based so no client tick hook is needed.
 * Free of client-only types so the common network registration can reference it safely.
 */
public final class ClientHudEffects {
    private static final long FLASH_MILLIS = 600;

    private static volatile long flashEndMillis = 0;

    private ClientHudEffects() {}

    /** Starts (or restarts) the filter-expiry flash. */
    public static void triggerFilterExpiryFlash() {
        flashEndMillis = System.currentTimeMillis() + FLASH_MILLIS;
    }

    /** Current flash strength, 0 (none) to 1 (just fired), decaying over {@link #FLASH_MILLIS}. */
    public static float flashAlpha() {
        long remaining = flashEndMillis - System.currentTimeMillis();
        if (remaining <= 0) {
            return 0f;
        }
        return Math.min(1f, (float) remaining / FLASH_MILLIS);
    }
}
