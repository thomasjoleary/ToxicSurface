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

    // Vertical layout (relative to topPos): title 6, Range label 18, button/slot row 34, Active 56.
    private static final int ROW_BUTTONS = 34;
    private static final int LABEL_RANGE_Y = 18;
    private static final int LABEL_ACTIVE_Y = 56;
    private static final int SLOT_X = 80;

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        addRenderableWidget(rangeButton(x + 8, y + ROW_BUTTONS, 20, "-8", CleanserMenu.BUTTON_RANGE_DOWN_8));
        addRenderableWidget(rangeButton(x + 30, y + ROW_BUTTONS, 18, "-1", CleanserMenu.BUTTON_RANGE_DOWN_1));
        addRenderableWidget(rangeButton(x + 128, y + ROW_BUTTONS, 18, "+1", CleanserMenu.BUTTON_RANGE_UP_1));
        addRenderableWidget(rangeButton(x + 148, y + ROW_BUTTONS, 20, "+8", CleanserMenu.BUTTON_RANGE_UP_8));
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

        // fuel slot in the centre of the button row
        drawSlot(graphics, x + SLOT_X, y + ROW_BUTTONS);

        // fuel flame just left of the fuel slot (between it and the -1 button)
        int flameX = x + SLOT_X - 14;
        int flameY = y + ROW_BUTTONS + 4;
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
        // Centre both labels in the panel so they don't collide with the buttons or the fuel slot.
        graphics.drawString(
                this.font, range, x + (this.imageWidth - this.font.width(range)) / 2, y + LABEL_RANGE_Y, TEXT, false);
        graphics.drawString(
                this.font,
                effective,
                x + (this.imageWidth - this.font.width(effective)) / 2,
                y + LABEL_ACTIVE_Y,
                TEXT,
                false);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
