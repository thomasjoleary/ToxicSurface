// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import io.github.thomasjoleary.toxicsurface.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Washing filters with a bucket, both directions (DESIGN.md §3 Filters & masks):
 *
 * <ul>
 *   <li><b>Wash:</b> {@code used filter + water bucket → clean filter}; the rinse water
 *       carries the dirt away, so a <b>sludge bucket</b> is returned.
 *   <li><b>Reverse:</b> {@code clean filter + sludge bucket → water bucket}; the filter
 *       scrubs the sludge clean and is itself spent, returning a <b>used filter</b>.
 * </ul>
 *
 * A custom recipe is required because the returned bucket isn't the vanilla
 * empty-bucket remainder, and the dirty/clean filter swaps sides.
 */
public class FilterWashRecipe extends CustomRecipe {
    public FilterWashRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack filter = ItemStack.EMPTY;
        ItemStack bucket = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (isFilter(stack)) {
                if (!filter.isEmpty()) {
                    return false;
                }
                filter = stack;
            } else if (isBucket(stack)) {
                if (!bucket.isEmpty()) {
                    return false;
                }
                bucket = stack;
            } else {
                return false;
            }
        }
        if (filter.isEmpty() || bucket.isEmpty()) {
            return false;
        }
        boolean wash = filter.is(ModItems.USED_AIR_FILTER.get()) && bucket.is(Items.WATER_BUCKET);
        boolean reverse = filter.is(ModItems.CLEAN_AIR_FILTER.get()) && bucket.is(ModItems.SLUDGE_BUCKET.get());
        return wash || reverse;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        // Water present -> we're cleaning a used filter; otherwise emptying a sludge bucket.
        return containsItem(input, Items.WATER_BUCKET)
                ? new ItemStack(ModItems.CLEAN_AIR_FILTER.get())
                : new ItemStack(Items.WATER_BUCKET);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(Items.WATER_BUCKET)) {
                remaining.set(i, new ItemStack(ModItems.SLUDGE_BUCKET.get())); // dirt rinsed into the water
            } else if (stack.is(ModItems.CLEAN_AIR_FILTER.get())) {
                remaining.set(i, new ItemStack(ModItems.USED_AIR_FILTER.get())); // filter spent scrubbing sludge
            }
            // used filter and sludge bucket are fully consumed (left EMPTY).
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.FILTER_WASH.get();
    }

    private static boolean isFilter(ItemStack stack) {
        return stack.is(ModItems.USED_AIR_FILTER.get()) || stack.is(ModItems.CLEAN_AIR_FILTER.get());
    }

    private static boolean isBucket(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET) || stack.is(ModItems.SLUDGE_BUCKET.get());
    }

    private static boolean containsItem(CraftingInput input, net.minecraft.world.item.Item item) {
        for (int i = 0; i < input.size(); i++) {
            if (input.getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }
}
