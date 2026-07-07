// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.registry.ModRecipes;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Shared weave lookup and slot mechanics used by both Weaver variants (DESIGN.md §3) — the
 * furnace-fuelled {@link WeaverBlockEntity} and the Create rotation-powered Mechanical Weaver.
 * The recipes themselves are datapack-driven ({@link WeavingRecipe}, shipped under
 * {@code data/toxicsurface/recipe/weaving/}) so packs can add, remove, or rebalance them as
 * JSON; this class keeps the machine-side mechanics — matching the two input slots and merging
 * results into the output — in one place. Free of any Create types so it compiles and runs in
 * the standalone jar.
 */
public final class WeaverLogic {
    private WeaverLogic() {}

    /** Returns the weave recipe matching the two input slots, or {@code null} if none. */
    public static WeavingRecipe find(Level level, ItemStack slotA, ItemStack slotB) {
        return level.getRecipeManager()
                .getRecipeFor(ModRecipes.WEAVING_TYPE.get(), new WeavingRecipe.Input(slotA, slotB), level)
                .map(RecipeHolder::value)
                .orElse(null);
    }

    /** The full weave recipe table, with ids — used by the recipe-viewer categories. */
    public static List<RecipeHolder<WeavingRecipe>> recipes(RecipeManager recipes) {
        return recipes.getAllRecipesFor(ModRecipes.WEAVING_TYPE.get());
    }

    /** True when {@code result} fits in the output slot: empty, or the same item with room to stack. */
    public static boolean canOutput(ItemStackHandler items, int outputSlot, ItemStack result) {
        ItemStack out = items.getStackInSlot(outputSlot);
        if (out.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    /** Consumes the recipe's inputs from the two input slots and merges its result into the output slot. */
    public static void craft(ItemStackHandler items, int slotA, int slotB, int outputSlot, WeavingRecipe recipe) {
        recipe.consume(items.getStackInSlot(slotA), items.getStackInSlot(slotB));
        ItemStack out = items.getStackInSlot(outputSlot);
        if (out.isEmpty()) {
            items.setStackInSlot(outputSlot, recipe.result().copy());
        } else {
            out.grow(recipe.result().getCount());
        }
    }
}
