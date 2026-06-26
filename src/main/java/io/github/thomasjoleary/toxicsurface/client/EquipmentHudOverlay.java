// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceClientConfig;
import io.github.thomasjoleary.toxicsurface.item.FaceMaskItem;
import io.github.thomasjoleary.toxicsurface.item.HazmatSuit;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
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

    /** Full-screen hazmat visor overlay (256x256, transparent viewport + tinted glass + dark frame). */
    private static final ResourceLocation VISOR =
            ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "textures/misc/hazmat_visor.png");

    private EquipmentHudOverlay() {}

    /**
     * The full-screen visor overlay. Registered <b>below the hotbar</b> GUI layer so the player's
     * hotbar and HUD draw on top of it (the visor frames the world view, it shouldn't hide the HUD).
     */
    public static void renderVisor(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.HAZMAT_HELMET.get())
                && ToxicSurfaceClientConfig.VISOR_OVERLAY_ENABLED.get()) {
            drawVisor(graphics);
        }
    }

    /** The filter-time gauge text. Registered above the HUD so it's always legible. */
    public static void renderGauge(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        drawFilterGauge(
                graphics, mc, player.getItemBySlot(EquipmentSlot.HEAD), player.getItemBySlot(EquipmentSlot.CHEST));
    }

    private static void drawFilterGauge(GuiGraphics graphics, Minecraft mc, ItemStack head, ItemStack chest) {
        String text = null;
        if (HazmatSuit.isChestpiece(chest)) {
            int usable = HazmatSuit.usableFilterCount(chest);
            text = "Filters " + usable + "/" + HazmatSuit.CAPACITY + "  " + formatTime(HazmatSuit.activeTicks(chest));
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
        // Stretch the square visor texture to fill the screen (the vanilla pumpkin-overlay approach):
        // its transparent rounded centre keeps the view clear while the tinted glass and dark rubber
        // frame ring the edges.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(VISOR, 0, 0, width, height, 0.0F, 0.0F, 256, 256, 256, 256);
        RenderSystem.disableBlend();
    }

    private static String formatTime(int ticks) {
        int totalSeconds = Math.max(0, ticks) / 20;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
