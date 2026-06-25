// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.compat.MachineFuel;
import net.minecraft.network.chat.Component;

/** EMI display of a single toxic-generator fuel row (the EMI counterpart of {@code GeneratorFuelCategory}). */
public class GeneratorFuelEmiRecipe extends BasicEmiRecipe {
    private final MachineFuel.Row row;

    public GeneratorFuelEmiRecipe(MachineFuel.Row row, int index) {
        super(
                ToxicSurfaceEmiCategories.GENERATOR_FUEL,
                ToxicSurfaceEmiCategories.id("generator_fuel/" + index),
                132,
                50);
        this.row = row;
        inputs.add(EmiStack.of(row.fuel()));
        catalysts.add(EmiStack.of(row.machine()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(EmiStack.of(row.fuel()), 5, 17);
        widgets.addSlot(EmiStack.of(row.machine()), 31, 17).catalyst(true);
        int x = 56;
        widgets.addText(
                Component.translatable(
                        "gui." + ToxicSurface.MODID + ".fuel.rpm", row.tier().rpm()),
                x,
                8,
                0xFF555555,
                false);
        widgets.addText(
                Component.translatable("gui." + ToxicSurface.MODID + ".fuel.su", (int)
                        row.tier().capacity()),
                x,
                19,
                0xFF555555,
                false);
        Component third = row.continuous()
                ? Component.translatable("gui." + ToxicSurface.MODID + ".fuel.rate", row.mbPerTick())
                : Component.translatable(
                        "gui." + ToxicSurface.MODID + ".fuel.burn",
                        time(row.tier().burnTicks()));
        widgets.addText(third, x, 30, 0xFF555555, false);
    }

    private static String time(int ticks) {
        int s = ticks / 20;
        return s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s";
    }
}
