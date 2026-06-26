// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.emi;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.block.WeaverLogic.WeaveRecipe;
import net.minecraft.network.chat.Component;

/** EMI display of a single Weaver weave recipe (the EMI counterpart of {@code WeavingCategory}). */
public class WeavingEmiRecipe extends BasicEmiRecipe {
    private final boolean twoInputs;
    private final int time;

    public WeavingEmiRecipe(WeaveRecipe recipe, int index) {
        super(ToxicSurfaceEmiCategories.WEAVING, ToxicSurfaceEmiCategories.id("weaving/" + index), 116, 54);
        this.twoInputs = recipe.bCount() > 0;
        this.time = recipe.time();
        inputs.add(EmiIngredient.of(recipe.a(), recipe.aCount()));
        if (twoInputs) {
            inputs.add(EmiIngredient.of(recipe.b(), recipe.bCount()));
        }
        outputs.add(EmiStack.of(recipe.result()));
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(inputs.get(0), 5, 19);
        if (twoInputs) {
            // Slot B kept clear of the plus sign (see WeavingCategory): a plus tucked against B's
            // 18px background gets clipped and reads as a stray arrowhead between the two inputs.
            widgets.addTexture(EmiTexture.PLUS, 23, 21);
            widgets.addSlot(inputs.get(1), 38, 19);
        }
        widgets.addTexture(EmiTexture.FULL_ARROW, 60, 20);
        widgets.addSlot(outputs.get(0), 91, 19).recipeContext(this);
        Component label = Component.translatable(
                "gui." + ToxicSurface.MODID + ".weaving.time", String.format("%.1fs", time / 20.0f));
        widgets.addText(label, 60, 40, 0xFF555555, false);
    }
}
