// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import io.github.thomasjoleary.toxicsurface.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Refilling a face mask: {@code face mask + clean air filter → full face mask}, and
 * the spent filter is ejected as a used (dirty) filter for washing (DESIGN.md §3
 * Filters & masks). A custom recipe is needed because vanilla crafting yields a single
 * output — the dirty filter comes back as a remaining grid item.
 */
public class MaskRefillRecipe extends CustomRecipe {
    public MaskRefillRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        boolean mask = false;
        boolean filter = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof FaceMaskItem) {
                if (mask) {
                    return false;
                }
                mask = true;
            } else if (stack.is(ModItems.CLEAN_AIR_FILTER.get())) {
                if (filter) {
                    return false;
                }
                filter = true;
            } else {
                return false;
            }
        }
        return mask && filter;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack mask = new ItemStack(ModItems.FACE_MASK.get());
        FaceMaskItem.setRemaining(mask, ToxicSurfaceConfig.MASK_DURATION_TICKS.get());
        return mask;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            // A spent mask gives back a dirty filter to wash and reuse.
            if (stack.getItem() instanceof FaceMaskItem && FaceMaskItem.remaining(stack) <= 0) {
                remaining.set(i, new ItemStack(ModItems.USED_AIR_FILTER.get()));
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.MASK_REFILL.get();
    }
}
