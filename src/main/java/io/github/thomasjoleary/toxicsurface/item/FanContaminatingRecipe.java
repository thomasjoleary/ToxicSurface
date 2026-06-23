// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.thomasjoleary.toxicsurface.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * A data-driven "fan contaminating" recipe (DESIGN.md §7): an item carried through a fan's toxic
 * sludge airflow (see {@code SludgeFanProcessingType}) is transformed into the result — e.g. a
 * clean air filter is soiled into a used one. This is the sludge counterpart to Create's own
 * water "splashing" recipes, so packs can add their own contaminating transforms as JSON.
 *
 * <p>The recipe type/serializer are <b>base content</b> (registered with or without Create), so
 * the recipes always load; only the fan process that consumes them is gated behind Create.
 */
public record FanContaminatingRecipe(Ingredient ingredient, ItemStack result) implements Recipe<SingleRecipeInput> {

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.getItem(0));
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.FAN_CONTAMINATING.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.FAN_CONTAMINATING_TYPE.get();
    }

    /** Serializer for {@code { "ingredient": <ingredient>, "result": <item stack> }}. */
    public static final class Serializer implements RecipeSerializer<FanContaminatingRecipe> {
        private static final MapCodec<FanContaminatingRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                                Ingredient.CODEC_NONEMPTY
                                        .fieldOf("ingredient")
                                        .forGetter(FanContaminatingRecipe::ingredient),
                                ItemStack.CODEC.fieldOf("result").forGetter(FanContaminatingRecipe::result))
                        .apply(instance, FanContaminatingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, FanContaminatingRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC,
                        FanContaminatingRecipe::ingredient,
                        ItemStack.STREAM_CODEC,
                        FanContaminatingRecipe::result,
                        FanContaminatingRecipe::new);

        @Override
        public MapCodec<FanContaminatingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FanContaminatingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
