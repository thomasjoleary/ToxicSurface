// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.item.HazmatSuit;
import io.github.thomasjoleary.toxicsurface.menu.HazmatChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the hazmat chestpiece filter inventory (DESIGN.md §3). Textureless: draws
 * a plain panel + slot backgrounds so it works before any art exists.
 */
public class HazmatChestScreen extends AbstractContainerScreen<HazmatChestMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;

    public HazmatChestScreen(HazmatChestMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 196;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        for (int i = 0; i < HazmatSuit.CAPACITY; i++) {
            drawSlot(graphics, x + 8 + i * 18, y + 20);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(graphics, x + 8 + col * 18, y + 51 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(graphics, x + 8 + col * 18, y + 109);
        }
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
