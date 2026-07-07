// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import java.util.List;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Shared weave recipe table and matching used by both Weaver variants (DESIGN.md §3) — the
 * furnace-fuelled {@link WeaverBlockEntity} and the Create rotation-powered Mechanical Weaver.
 * The recipes (a small hard-coded table; datapack-driven recipes are a future enhancement) are
 * identical for both machines; only what powers them differs, so the table lives here once.
 * Free of any Create types so it compiles and runs in the standalone jar.
 */
public final class WeaverLogic {
    private WeaverLogic() {}

    private static final List<WeaveRecipe> RECIPES = buildRecipes();

    /** The full weave recipe table (read-only) — used by the recipe-viewer categories. */
    public static List<WeaveRecipe> recipes() {
        return RECIPES;
    }

    /** Returns the first recipe matching the two input slots, or {@code null} if none. */
    public static WeaveRecipe find(ItemStack slotA, ItemStack slotB) {
        for (WeaveRecipe recipe : RECIPES) {
            if (recipe.matches(slotA, slotB)) {
                return recipe;
            }
        }
        return null;
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
    public static void craft(ItemStackHandler items, int slotA, int slotB, int outputSlot, WeaveRecipe recipe) {
        recipe.consume(items.getStackInSlot(slotA), items.getStackInSlot(slotB));
        ItemStack out = items.getStackInSlot(outputSlot);
        if (out.isEmpty()) {
            items.setStackInSlot(outputSlot, recipe.result().copy());
        } else {
            out.grow(recipe.result().getCount());
        }
    }

    private static List<WeaveRecipe> buildRecipes() {
        return List.of(
                new WeaveRecipe(
                        Ingredient.of(Items.KELP),
                        1,
                        Ingredient.of(ItemTags.WOOL),
                        1,
                        new ItemStack(ModItems.HAZMAT_MATERIAL.get()),
                        200),
                new WeaveRecipe(
                        Ingredient.of(ItemTags.WOOL),
                        1,
                        Ingredient.EMPTY,
                        0,
                        new ItemStack(ModItems.CLEAN_AIR_FILTER.get()),
                        100),
                new WeaveRecipe(
                        Ingredient.of(Items.STRING),
                        2,
                        Ingredient.EMPTY,
                        0,
                        new ItemStack(ModItems.CLEAN_AIR_FILTER.get()),
                        100),
                new WeaveRecipe(
                        Ingredient.of(ModItems.CLEAN_AIR_FILTER.get()),
                        1,
                        Ingredient.of(Items.CHARCOAL, Items.COAL),
                        1,
                        new ItemStack(ModItems.CARBON_AIR_FILTER.get()),
                        150));
    }

    /** A two-input weave recipe (the second ingredient may be {@link Ingredient#EMPTY} for single-input recipes). */
    public record WeaveRecipe(Ingredient a, int aCount, Ingredient b, int bCount, ItemStack result, int time) {
        boolean singleInput() {
            return bCount == 0;
        }

        boolean matches(ItemStack slotA, ItemStack slotB) {
            if (singleInput()) {
                // exactly one slot filled, matching ingredient a
                if (!slotB.isEmpty() && !slotA.isEmpty()) {
                    return false;
                }
                ItemStack only = slotA.isEmpty() ? slotB : slotA;
                return !only.isEmpty() && a.test(only) && only.getCount() >= aCount;
            }
            return (matchPart(a, aCount, slotA) && matchPart(b, bCount, slotB))
                    || (matchPart(a, aCount, slotB) && matchPart(b, bCount, slotA));
        }

        public void consume(ItemStack slotA, ItemStack slotB) {
            if (singleInput()) {
                (slotA.isEmpty() ? slotB : slotA).shrink(aCount);
                return;
            }
            if (matchPart(a, aCount, slotA) && matchPart(b, bCount, slotB)) {
                slotA.shrink(aCount);
                slotB.shrink(bCount);
            } else {
                slotA.shrink(bCount);
                slotB.shrink(aCount);
            }
        }

        private static boolean matchPart(Ingredient ingredient, int count, ItemStack stack) {
            return !stack.isEmpty() && ingredient.test(stack) && stack.getCount() >= count;
        }
    }
}
