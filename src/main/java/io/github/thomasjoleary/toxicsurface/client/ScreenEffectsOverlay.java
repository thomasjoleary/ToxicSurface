// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Full-screen client effects (DESIGN.md §3): the <b>toxic-rain</b> overlay — faint green wash and
 * falling green streaks while it rains and the player stands in toxic open air — and the
 * <b>filter-expire flash</b> — a red top/bottom vignette pulse the instant gas protection drops.
 * Both are purely cosmetic and driven by synced {@link ClientGasState} / {@link ClientHudEffects}.
 * Registered as a GUI layer by {@code ClientModBusEvents}.
 */
public final class ScreenEffectsOverlay {
    private static final int RAIN_WASH = 0x0C2A4A18; // faint toxic-green screen wash
    private static final int RAIN_STREAK = 0x3366CC44; // green falling streak
    private static final int FLASH_RGB = 0xC81010; // red warning vignette

    private ScreenEffectsOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        if (ToxicSurfaceConfig.TOXIC_RAIN_ENABLED.get()
                && mc.level != null
                && mc.level.isRaining()
                && ClientGasState.isInToxicArea()) {
            drawToxicRain(graphics, width, height);
        }

        float flash = ClientHudEffects.flashAlpha();
        if (flash > 0f) {
            drawExpiryFlash(graphics, width, height, flash);
        }
    }

    private static void drawToxicRain(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, RAIN_WASH);
        long now = System.currentTimeMillis();
        int streaks = 48;
        for (int i = 0; i < streaks; i++) {
            int x = (i * 97 + 13) % width;
            int period = 600 + (i * 53) % 500; // ms per fall cycle (varied so they don't march in step)
            int length = 16 + (i * 11) % 18;
            int span = height + length;
            int y = (int) (((now % period) / (float) period) * span + (i * 40L)) % span - length;
            graphics.fill(x, y, x + 1, y + length, RAIN_STREAK);
        }
    }

    private static void drawExpiryFlash(GuiGraphics graphics, int width, int height, float alpha) {
        int a = (int) (alpha * 140f) & 0xFF;
        int solid = (a << 24) | FLASH_RGB;
        int clear = FLASH_RGB; // alpha 0
        int band = Math.max(12, Math.min(width, height) / 6);
        graphics.fillGradient(0, 0, width, band, solid, clear); // top edge fades down
        graphics.fillGradient(0, height - band, width, height, clear, solid); // bottom edge fades up
    }
}
