// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.thomasjoleary.toxicsurface.registry.ModRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * A data-driven weave recipe (DESIGN.md §3): one or two counted ingredients woven into a result
 * over {@code time} ticks, run by both Weaver variants (the furnace-fuelled Weaver and the Create
 * Mechanical Weaver). Datapack-driven so packs can add, remove, or rebalance recipes as JSON:
 *
 * <pre>{@code
 * { "type": "toxicsurface:weaving",
 *   "input_a": { "item": "minecraft:kelp" },        // count_a defaults to 1
 *   "input_b": { "tag": "minecraft:wool" },         // optional; omit for single-input recipes
 *   "result": { "id": "toxicsurface:hazmat_material" },
 *   "time": 200 }                                   // weave ticks at the base rate (default 200)
 * }</pre>
 *
 * <p>The two machine slots are unordered — a recipe's inputs match in either arrangement — and a
 * single-input recipe matches only while exactly one slot is filled, so a stray second item never
 * silently rides along.
 *
 * @param bCount normalised to 0 whenever {@code b} is empty, so {@code bCount() > 0} is the
 *     "two-input recipe" test everywhere (machine, JEI, EMI)
 */
public record WeavingRecipe(Ingredient a, int aCount, Ingredient b, int bCount, ItemStack result, int time)
        implements Recipe<WeavingRecipe.Input> {

    public WeavingRecipe {
        aCount = Math.max(1, aCount);
        bCount = b.isEmpty() ? 0 : Math.max(1, bCount);
    }

    /** The Weaver's two input slots as a {@link RecipeInput} for recipe-manager lookups. */
    public record Input(ItemStack slotA, ItemStack slotB) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return index == 0 ? slotA : slotB;
        }

        @Override
        public int size() {
            return 2;
        }
    }

    boolean singleInput() {
        return bCount == 0;
    }

    @Override
    public boolean matches(Input input, Level level) {
        return matches(input.slotA(), input.slotB());
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

    /** Shrinks the matched inputs out of the two slots (in whichever arrangement they matched). */
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

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
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
        return ModRecipes.WEAVING.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.WEAVING_TYPE.get();
    }

    /** Serializer for the JSON shape documented on {@link WeavingRecipe}. */
    public static final class Serializer implements RecipeSerializer<WeavingRecipe> {
        private static final int DEFAULT_TIME = 200;

        private static final MapCodec<WeavingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("input_a").forGetter(WeavingRecipe::a),
                        Codec.INT.optionalFieldOf("count_a", 1).forGetter(WeavingRecipe::aCount),
                        Ingredient.CODEC
                                .optionalFieldOf("input_b", Ingredient.EMPTY)
                                .forGetter(WeavingRecipe::b),
                        Codec.INT.optionalFieldOf("count_b", 1).forGetter(WeavingRecipe::bCount),
                        ItemStack.CODEC.fieldOf("result").forGetter(WeavingRecipe::result),
                        Codec.INT.optionalFieldOf("time", DEFAULT_TIME).forGetter(WeavingRecipe::time))
                .apply(instance, WeavingRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, WeavingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC,
                WeavingRecipe::a,
                ByteBufCodecs.VAR_INT,
                WeavingRecipe::aCount,
                Ingredient.CONTENTS_STREAM_CODEC,
                WeavingRecipe::b,
                ByteBufCodecs.VAR_INT,
                WeavingRecipe::bCount,
                ItemStack.STREAM_CODEC,
                WeavingRecipe::result,
                ByteBufCodecs.VAR_INT,
                WeavingRecipe::time,
                WeavingRecipe::new);

        @Override
        public MapCodec<WeavingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, WeavingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
