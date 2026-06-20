// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.item.FaceMaskItem;
import io.github.thomasjoleary.toxicsurface.item.HazmatSuit;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * HUD gauge + visor overlay for masks and the hazmat suit (DESIGN.md §3 HUD gauge,
 * visor immersion). Reads the local player's worn equipment directly (item components
 * are synced for your own inventory), so no extra networking is needed. Registered as
 * a GUI layer by {@code ClientModBusEvents}.
 */
public final class EquipmentHudOverlay {
    private static final int BAR_GREEN = 0x55FF55;
    private static final int VISOR_TINT = 0xAA0A140A; // translucent dark green-black

    private EquipmentHudOverlay() {}

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);

        if (head.is(ModItems.HAZMAT_HELMET.get())) {
            drawVisor(graphics);
        }
        drawFilterGauge(graphics, mc, head, chest);
    }

    private static void drawFilterGauge(GuiGraphics graphics, Minecraft mc, ItemStack head, ItemStack chest) {
        String text = null;
        if (HazmatSuit.isChestpiece(chest)) {
            int clean = HazmatSuit.cleanFilterCount(chest);
            text = "Filters " + clean + "/" + HazmatSuit.CAPACITY + "  " + formatTime(HazmatSuit.activeTicks(chest));
        } else if (head.getItem() instanceof FaceMaskItem && FaceMaskItem.remaining(head) > 0) {
            text = "Filter " + formatTime(FaceMaskItem.remaining(head));
        }
        if (text == null) {
            return;
        }
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int x = width / 2 + 95; // to the right of the hotbar
        int y = height - 40;
        graphics.drawString(mc.font, text, x, y, BAR_GREEN, true);
    }

    private static void drawVisor(GuiGraphics graphics) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int border = Math.max(8, Math.min(width, height) / 8);
        graphics.fill(0, 0, width, border, VISOR_TINT); // top
        graphics.fill(0, height - border, width, height, VISOR_TINT); // bottom
        graphics.fill(0, 0, border, height, VISOR_TINT); // left
        graphics.fill(width - border, 0, width, height, VISOR_TINT); // right
    }

    private static String formatTime(int ticks) {
        int totalSeconds = Math.max(0, ticks) / 20;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
