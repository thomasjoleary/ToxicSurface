// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * The toxic air bar HUD (DESIGN.md §3 Toxic air bar) — a drowning-style row of green-tinted bubbles
 * above the hotbar that drains while the local player is unprotected in toxic gas and refills in
 * clean/protected air. Driven entirely by the {@link ClientGasState#air()} fraction the server
 * syncs; reuses the vanilla air-bubble sprites with a green tint so it reads as "your breath, but
 * toxic." Hidden when the bar is full, mirroring vanilla air. Registered as a GUI layer by
 * {@code ClientModBusEvents}.
 */
public final class AirBarOverlay {
    private static final ResourceLocation AIR = ResourceLocation.withDefaultNamespace("hud/air");
    private static final ResourceLocation AIR_BURSTING = ResourceLocation.withDefaultNamespace("hud/air_bursting");

    private AirBarOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui || player.isSpectator() || player.isCreative()) {
            return;
        }
        float fraction = ClientGasState.air();
        if (fraction >= 0.999f) {
            return; // full bar: hidden, like the vanilla air row
        }

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int right = width / 2 + 91;
        // Sit on the vanilla air row, nudged up if vanilla air is also showing (a sludge dive).
        boolean vanillaAirShowing = player.getAirSupply() < player.getMaxAirSupply();
        int top = height - 49 - (vanillaAirShowing ? 10 : 0);

        int fullBubbles = (int) (fraction * 10.0f);
        boolean bursting = (fraction * 10.0f - fullBubbles) > 0.0f && fullBubbles < 10;

        graphics.setColor(0.45f, 1.0f, 0.5f, 1.0f); // green tint over the white bubble sprite
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            if (i < fullBubbles) {
                graphics.blitSprite(AIR, x, top, 9, 9);
            } else if (i == fullBubbles && bursting) {
                graphics.blitSprite(AIR_BURSTING, x, top, 9, 9);
            }
        }
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
