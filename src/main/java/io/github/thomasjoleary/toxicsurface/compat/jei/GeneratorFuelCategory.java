// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.jei;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.compat.MachineFuel;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * JEI category for the toxic generators' fuels (DESIGN.md §7): a fuel item feeding a generator,
 * with the rotation, stress capacity and burn it produces. The output is rotational power (not an
 * item), so the right side is a stat readout rather than an output slot. Rows come from the shared
 * {@link MachineFuel} table, so this matches the generators' actual fuel handling.
 */
public class GeneratorFuelCategory implements IRecipeCategory<MachineFuel.Row> {
    public static final RecipeType<MachineFuel.Row> TYPE =
            RecipeType.create(ToxicSurface.MODID, "generator_fuel", MachineFuel.Row.class);

    private static final int WIDTH = 132;
    private static final int HEIGHT = 50;

    private final IDrawable icon;

    public GeneratorFuelCategory(IGuiHelper guiHelper) {
        // Icon: the first generator that exists (waste, else sludge), falling back to residue.
        var first = MachineFuel.rows().stream().findFirst();
        this.icon = guiHelper.createDrawableItemStack(first.map(MachineFuel.Row::machine)
                .orElseGet(() -> ModItems.TOXIC_RESIDUE.get().getDefaultInstance()));
    }

    @Override
    public RecipeType<MachineFuel.Row> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui." + ToxicSurface.MODID + ".category.generator_fuel");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MachineFuel.Row row, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 5, 17)
                .setStandardSlotBackground()
                .addItemStack(row.fuel());
        builder.addSlot(RecipeIngredientRole.CATALYST, 31, 17).addItemStack(row.machine());
    }

    @Override
    public void draw(MachineFuel.Row row, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        int x = 56;
        int y = 8;
        line(
                graphics,
                font,
                Component.translatable(
                        "gui." + ToxicSurface.MODID + ".fuel.rpm", row.tier().rpm()),
                x,
                y);
        line(
                graphics,
                font,
                Component.translatable("gui." + ToxicSurface.MODID + ".fuel.su", (int)
                        row.tier().capacity()),
                x,
                y + 11);
        Component third = row.continuous()
                ? Component.translatable("gui." + ToxicSurface.MODID + ".fuel.rate", row.mbPerTick())
                : Component.translatable(
                        "gui." + ToxicSurface.MODID + ".fuel.burn",
                        time(row.tier().burnTicks()));
        line(graphics, font, third, x, y + 22);
    }

    private static void line(GuiGraphics graphics, net.minecraft.client.gui.Font font, Component c, int x, int y) {
        graphics.drawString(font, c, x, y, 0xFF555555, false);
    }

    /** Burn ticks as a compact "Ns" / "Nm Ss" string. */
    private static String time(int ticks) {
        int s = ticks / 20;
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }
}
