// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import io.github.thomasjoleary.toxicsurface.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Loads clean filters into a hazmat chestpiece (DESIGN.md §3): {@code chestpiece +
 * N clean filters → chestpiece with N more filters}, capped at the configured
 * capacity so filters are never wasted. A custom recipe because the result carries
 * over the chest's existing filter charge.
 */
public class SuitRefillRecipe extends CustomRecipe {
    public SuitRefillRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack chest = ItemStack.EMPTY;
        int filters = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.HAZMAT_CHESTPLATE.get())) {
                if (!chest.isEmpty()) {
                    return false;
                }
                chest = stack;
            } else if (stack.is(ModItems.CLEAN_AIR_FILTER.get())) {
                filters++;
            } else {
                return false;
            }
        }
        if (chest.isEmpty() || filters == 0) {
            return false;
        }
        int current = HazmatSuit.filterCount(chest);
        int capacity = ToxicSurfaceConfig.SUIT_FILTER_CAPACITY.get();
        return current < capacity && current + filters <= capacity;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack chest = ItemStack.EMPTY;
        int added = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(ModItems.HAZMAT_CHESTPLATE.get())) {
                chest = stack;
            } else if (stack.is(ModItems.CLEAN_AIR_FILTER.get())) {
                added++;
            }
        }
        ItemStack result = chest.copy();
        result.setCount(1);
        SuitData current = HazmatSuit.data(result);
        int currentFilters = current == null ? 0 : current.filters();
        int activeTicks = currentFilters > 0 ? current.activeTicks() : ToxicSurfaceConfig.MASK_DURATION_TICKS.get();
        HazmatSuit.setData(result, new SuitData(currentFilters + added, activeTicks));
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.SUIT_REFILL.get();
    }
}
