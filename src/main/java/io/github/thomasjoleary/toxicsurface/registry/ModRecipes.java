// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.item.FilterWashRecipe;
import io.github.thomasjoleary.toxicsurface.item.MaskRefillRecipe;
import io.github.thomasjoleary.toxicsurface.item.SuitRefillRecipe;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom recipe serializers (DESIGN.md §3). */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ToxicSurface.MODID);

    public static final Supplier<RecipeSerializer<MaskRefillRecipe>> MASK_REFILL = RECIPE_SERIALIZERS.register(
            "mask_refill", () -> new SimpleCraftingRecipeSerializer<>(MaskRefillRecipe::new));

    public static final Supplier<RecipeSerializer<FilterWashRecipe>> FILTER_WASH = RECIPE_SERIALIZERS.register(
            "filter_wash", () -> new SimpleCraftingRecipeSerializer<>(FilterWashRecipe::new));

    public static final Supplier<RecipeSerializer<SuitRefillRecipe>> SUIT_REFILL = RECIPE_SERIALIZERS.register(
            "suit_refill", () -> new SimpleCraftingRecipeSerializer<>(SuitRefillRecipe::new));

    private ModRecipes() {}
}
