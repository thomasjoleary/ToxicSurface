// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.block.WeavingRecipe;
import io.github.thomasjoleary.toxicsurface.item.FanContaminatingRecipe;
import io.github.thomasjoleary.toxicsurface.item.FilterWashRecipe;
import io.github.thomasjoleary.toxicsurface.item.MaskRefillRecipe;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom recipe types and serializers (DESIGN.md §3, §7). */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ToxicSurface.MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, ToxicSurface.MODID);

    public static final Supplier<RecipeSerializer<MaskRefillRecipe>> MASK_REFILL = RECIPE_SERIALIZERS.register(
            "mask_refill", () -> new SimpleCraftingRecipeSerializer<>(MaskRefillRecipe::new));

    public static final Supplier<RecipeSerializer<FilterWashRecipe>> FILTER_WASH = RECIPE_SERIALIZERS.register(
            "filter_wash", () -> new SimpleCraftingRecipeSerializer<>(FilterWashRecipe::new));

    /**
     * Weaving: one or two counted inputs woven into a result over a process time (DESIGN.md §3).
     * Datapack-driven so packs can add/remove/rebalance the weave table as JSON; run by both the
     * furnace-fuelled Weaver and (with Create) the Mechanical Weaver.
     */
    public static final Supplier<RecipeType<WeavingRecipe>> WEAVING_TYPE =
            RECIPE_TYPES.register("weaving", () -> new RecipeType<>() {});

    public static final Supplier<RecipeSerializer<WeavingRecipe>> WEAVING =
            RECIPE_SERIALIZERS.register("weaving", WeavingRecipe.Serializer::new);

    /**
     * Fan-contaminating: an item carried through a fan's sludge airflow is transformed (DESIGN.md
     * §7). Base content so recipes load standalone; only the Create fan process consumes them.
     */
    public static final Supplier<RecipeType<FanContaminatingRecipe>> FAN_CONTAMINATING_TYPE =
            RECIPE_TYPES.register("fan_contaminating", () -> new RecipeType<>() {});

    public static final Supplier<RecipeSerializer<FanContaminatingRecipe>> FAN_CONTAMINATING =
            RECIPE_SERIALIZERS.register("fan_contaminating", FanContaminatingRecipe.Serializer::new);

    private ModRecipes() {}
}
