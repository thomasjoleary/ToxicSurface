// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.menu.WeaverMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Weaver (DESIGN.md §3). Textureless: draws a plain panel, slot
 * backgrounds, a fuel flame, and a progress arrow so it works before any art exists.
 */
public class WeaverScreen extends AbstractContainerScreen<WeaverMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int ARROW_TRACK = 0xFF8B8B8B;
    private static final int ARROW_FILL = 0xFF5FA85F;
    private static final int FLAME_TRACK = 0xFF555555;
    private static final int FLAME_FILL = 0xFFE0902A;

    public WeaverScreen(WeaverMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);

        // machine slots (input A/B, fuel, output)
        drawSlot(graphics, x + 44, y + 17);
        drawSlot(graphics, x + 62, y + 17);
        drawSlot(graphics, x + 53, y + 53);
        drawSlot(graphics, x + 116, y + 35);

        // player inventory + hotbar
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(graphics, x + 8 + col * 18, y + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(graphics, x + 8 + col * 18, y + 142);
        }

        // fuel flame (under the fuel slot), filling from the bottom up
        int flameX = x + 53 + 3;
        int flameY = y + 53 - 13;
        graphics.fill(flameX, flameY, flameX + 10, flameY + 11, FLAME_TRACK);
        int lit = this.menu.getLitScaled(11);
        if (lit > 0) {
            graphics.fill(flameX, flameY + (11 - lit), flameX + 10, flameY + 11, FLAME_FILL);
        }

        // progress arrow between inputs and output
        int arrowX = x + 80;
        int arrowY = y + 38;
        graphics.fill(arrowX, arrowY, arrowX + 24, arrowY + 6, ARROW_TRACK);
        int progress = this.menu.getProgressScaled(24);
        if (progress > 0) {
            graphics.fill(arrowX, arrowY, arrowX + progress, arrowY + 6, ARROW_FILL);
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
