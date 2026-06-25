// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.compat.MachineFuel;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import net.minecraft.resources.ResourceLocation;

/**
 * EMI recipe categories — the EMI counterparts of the JEI {@code WeavingCategory} /
 * {@code GeneratorFuelCategory}. Each category's display name comes from the
 * {@code emi.category.toxicsurface.<path>} lang key (EMI's convention). The icons use base-mod items
 * (the Weaver, or a generator resolved by id), so this never classloads {@code compat.create}.
 */
public final class ToxicSurfaceEmiCategories {
    private ToxicSurfaceEmiCategories() {}

    public static final EmiRecipeCategory WEAVING =
            new EmiRecipeCategory(id("weaving"), EmiStack.of(ModItems.WEAVER.get()));

    public static final EmiRecipeCategory GENERATOR_FUEL = new EmiRecipeCategory(id("generator_fuel"), generatorIcon());

    private static EmiStack generatorIcon() {
        return MachineFuel.rows().stream()
                .findFirst()
                .map(row -> EmiStack.of(row.machine()))
                .orElseGet(() -> EmiStack.of(ModItems.TOXIC_RESIDUE.get()));
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, path);
    }
}
