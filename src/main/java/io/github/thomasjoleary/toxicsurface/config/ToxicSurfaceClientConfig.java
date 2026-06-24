// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side accessibility options (DESIGN.md §3 Accessibility). Purely visual, per-player, and
 * never synced — each player tunes how intense the immersive effects are without changing gameplay.
 * Covers the toxic-haze density, the hazmat visor vignette, the filter-expire flash, and the
 * toxic-rain overlay opacity, each of which can be dialled down to a thin hint or off.
 */
public final class ToxicSurfaceClientConfig {
    public static final ModConfigSpec SPEC;

    /** Toxic-haze density when in gas: 1 = full pulled-in fog, 0 = thin tint only. */
    public static final ModConfigSpec.DoubleValue FOG_INTENSITY;

    /** Hazmat-visor vignette overlay (edge darkening + fog-up immersion). */
    public static final ModConfigSpec.BooleanValue VISOR_OVERLAY_ENABLED;

    /** Strength of the red filter-expire HUD flash: 1 = full, 0 = off. */
    public static final ModConfigSpec.DoubleValue FILTER_FLASH_INTENSITY;

    /** Opacity of the toxic-rain screen overlay: 1 = full, 0 = off. */
    public static final ModConfigSpec.DoubleValue TOXIC_RAIN_OPACITY;

    private ToxicSurfaceClientConfig() {}

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("accessibility");
        FOG_INTENSITY = b.comment("Toxic-haze density while in gas (1.0 = full fog, 0.0 = thin tint only).")
                .defineInRange("fogIntensity", 1.0, 0.0, 1.0);
        VISOR_OVERLAY_ENABLED =
                b.comment("Show the hazmat-suit visor vignette overlay.").define("visorOverlayEnabled", true);
        FILTER_FLASH_INTENSITY = b.comment("Strength of the filter-expire screen flash (1.0 = full, 0.0 = off).")
                .defineInRange("filterFlashIntensity", 1.0, 0.0, 1.0);
        TOXIC_RAIN_OPACITY = b.comment("Opacity of the toxic-rain screen overlay (1.0 = full, 0.0 = off).")
                .defineInRange("toxicRainOpacity", 1.0, 0.0, 1.0);
        b.pop();

        SPEC = b.build();
    }
}
