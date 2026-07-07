// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.jei;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.block.WeavingRecipe;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * JEI category for the Weaver's weave recipes (DESIGN.md §3): one or two item inputs woven into an
 * output over a process time, shared by the furnace-fuelled Weaver and the Create Mechanical Weaver.
 * The recipe data comes from the datapack-loaded {@code toxicsurface:weaving} recipes, so the
 * view always matches what the machines (and any pack overrides) actually run.
 */
public class WeavingCategory implements IRecipeCategory<WeavingRecipe> {
    public static final RecipeType<WeavingRecipe> TYPE =
            RecipeType.create(ToxicSurface.MODID, "weaving", WeavingRecipe.class);

    private static final int WIDTH = 116;
    private static final int HEIGHT = 54;

    private final IGuiHelper guiHelper;
    private final IDrawable icon;
    private final IDrawableStatic arrow;
    private final IDrawableStatic plus;

    public WeavingCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        this.icon = guiHelper.createDrawableItemLike(ModItems.WEAVER.get());
        this.arrow = guiHelper.getRecipeArrow();
        this.plus = guiHelper.getRecipePlusSign();
    }

    @Override
    public RecipeType<WeavingRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui." + ToxicSurface.MODID + ".category.weaving");
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
    public void setRecipe(IRecipeLayoutBuilder builder, WeavingRecipe recipe, IFocusGroup focuses) {
        boolean twoInputs = recipe.bCount() > 0;
        builder.addSlot(RecipeIngredientRole.INPUT, 5, 19)
                .setStandardSlotBackground()
                .addItemStacks(stacks(recipe.a(), recipe.aCount()));
        if (twoInputs) {
            // Slot B sits far enough right of slot A that the plus sign drawn between them in draw()
            // lands in a clear gap — JEI renders category.draw() *under* the slots, so a plus too
            // close to B would be half-covered by B's background and read as a stray arrowhead.
            builder.addSlot(RecipeIngredientRole.INPUT, 38, 19)
                    .setStandardSlotBackground()
                    .addItemStacks(stacks(recipe.b(), recipe.bCount()));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 91, 19)
                .setOutputSlotBackground()
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(WeavingRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        if (recipe.bCount() > 0) {
            plus.draw(graphics, 23, 21); // centred in the clear gap between slot A (5) and slot B (38)
        }
        arrow.draw(graphics, 60, 19);
        // weave time in seconds, centred under the arrow
        String secs = String.format("%.1fs", recipe.time() / 20.0f);
        Component label = Component.translatable("gui." + ToxicSurface.MODID + ".weaving.time", secs);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, label, 60 + (22 - font.width(label)) / 2, 38, 0xFF555555, false);
    }

    /** The ingredient's matching stacks at the recipe's required count (so JEI shows the number). */
    private static List<ItemStack> stacks(Ingredient ingredient, int count) {
        return List.of(ingredient.getItems()).stream()
                .map(s -> {
                    ItemStack c = s.copy();
                    c.setCount(Math.max(1, count));
                    return c;
                })
                .toList();
    }
}
