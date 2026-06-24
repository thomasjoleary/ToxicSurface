// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.jei;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.compat.HintInfo;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;

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
    public void registerRecipes(IRecipeRegistration registration) {
        for (HintInfo.Entry entry : HintInfo.entries()) {
            registration.addIngredientInfo(entry.item(), HintInfo.text(entry.key()));
        }
    }
}
