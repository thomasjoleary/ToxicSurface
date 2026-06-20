// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.menu.CleanserMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen for the Cleanser (DESIGN.md §3). Textureless: a plain panel with the fuel slot,
 * a fuel flame, the current/effective range, and -8/-1/+1/+8 range stepper buttons that
 * drive the menu's range control.
 */
public class CleanserScreen extends AbstractContainerScreen<CleanserMenu> {
    private static final int PANEL = 0xFFC6C6C6;
    private static final int SLOT_BORDER = 0xFF373737;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int FLAME_TRACK = 0xFF555555;
    private static final int FLAME_FILL = 0xFFE0902A;
    private static final int TEXT = 0xFF202020;

    public CleanserScreen(CleanserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        addRenderableWidget(rangeButton(x + 8, y + 30, 20, "-8", CleanserMenu.BUTTON_RANGE_DOWN_8));
        addRenderableWidget(rangeButton(x + 30, y + 30, 18, "-1", CleanserMenu.BUTTON_RANGE_DOWN_1));
        addRenderableWidget(rangeButton(x + 130, y + 30, 18, "+1", CleanserMenu.BUTTON_RANGE_UP_1));
        addRenderableWidget(rangeButton(x + 150, y + 30, 20, "+8", CleanserMenu.BUTTON_RANGE_UP_8));
    }

    private Button rangeButton(int x, int y, int w, String label, int buttonId) {
        return Button.builder(Component.literal(label), b -> {
                    Minecraft mc = this.minecraft;
                    if (mc != null && mc.gameMode != null) {
                        mc.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
                    }
                })
                .bounds(x, y, w, 18)
                .build();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);

        drawSlot(graphics, x + 80, y + 53);

        // fuel flame above the fuel slot
        int flameX = x + 80 + 3;
        int flameY = y + 53 - 13;
        graphics.fill(flameX, flameY, flameX + 10, flameY + 11, FLAME_TRACK);
        if (this.menu.isLit()) {
            graphics.fill(flameX, flameY, flameX + 10, flameY + 11, FLAME_FILL);
        }

        // player inventory + hotbar
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(graphics, x + 8 + col * 18, y + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(graphics, x + 8 + col * 18, y + 142);
        }
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, SLOT_FILL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;
        String range = "Range: " + this.menu.getMenuRange();
        String effective = "Active: " + this.menu.getEffectiveRange();
        graphics.drawString(this.font, range, x + 56, y + 16, TEXT, false);
        graphics.drawString(this.font, effective, x + 56, y + 52, TEXT, false);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
