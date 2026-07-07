// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.jei;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.block.WeaverLogic;
import io.github.thomasjoleary.toxicsurface.compat.HintInfo;
import io.github.thomasjoleary.toxicsurface.compat.MachineFuel;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * JEI integration (DESIGN.md §5 Phase 8). The toxic generators and the industrial-filter cleaning
 * cycle involve mechanics with <b>no recipe view</b> — what fuels a generator, that an industrial
 * filter clogs from <em>use</em> (not a recipe), that scrubbing avoids the smog — so this plugin
 * registers JEI <b>ingredient-info pages</b> (the "i" tab) describing them. The standard crafting,
 * smelting/blasting, and Create recipes are surfaced by JEI / Create's own plugin already, so they
 * need nothing here.
 *
 * <p>Soft dependency: compiled against the JEI <em>API</em> only and loaded by JEI exclusively when
 * JEI is present (it scans for {@link JeiPlugin}), so the class is never touched in the standalone
 * jar. The Create-gated generator block items are looked up by registry id rather than referenced,
 * so this plugin never classloads {@code compat.create} — when Create is absent they simply aren't
 * registered and are skipped.
 */
@JeiPlugin
public class ToxicSurfaceJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new WeavingCategory(gui), new GeneratorFuelCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (HintInfo.Entry entry : HintInfo.entries()) {
            registration.addIngredientInfo(entry.item(), HintInfo.text(entry.key()));
        }
        // JEI registers plugins with a level in hand (it reloads per world join), so the recipe
        // manager here is the one the server synced — pack-added weave recipes show up too.
        registration.addRecipes(
                WeavingCategory.TYPE,
                WeaverLogic.recipes(Minecraft.getInstance().level.getRecipeManager()).stream()
                        .map(RecipeHolder::value)
                        .toList());
        registration.addRecipes(GeneratorFuelCategory.TYPE, MachineFuel.rows());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // The Weaver and (when Create is present) the Mechanical Weaver both run weave recipes.
        registration.addRecipeCatalyst(ModItems.WEAVER.get(), WeavingCategory.TYPE);
        catalystById(registration, "mechanical_weaver", WeavingCategory.TYPE);
        // Generators are listed as a catalyst per fuel row's machine; also link both at category level.
        for (MachineFuel.Row row : MachineFuel.rows()) {
            registration.addRecipeCatalysts(GeneratorFuelCategory.TYPE, row.machine());
        }
    }

    private static void catalystById(
            IRecipeCatalystRegistration registration, String path, mezz.jei.api.recipe.RecipeType<?> type) {
        BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, path))
                .ifPresent(item -> registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(item), type));
    }
}
