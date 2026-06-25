// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.block.WeaverLogic;
import io.github.thomasjoleary.toxicsurface.compat.HintInfo;
import io.github.thomasjoleary.toxicsurface.compat.MachineFuel;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * EMI integration (DESIGN.md §5 Phase 8) — the EMI counterpart of {@code compat.jei}. EMI has its
 * own API and does not read JEI plugins, so this registers EMI <b>info recipes</b> (the same
 * "what is this for" pages) for the same {@link HintInfo} entries the JEI plugin uses, keeping the
 * two viewers in lock-step. The terse {@code HintTooltips} lines already show in EMI natively (it
 * renders the item tooltip); these add the fuller description panels.
 *
 * <p>Soft dependency on the same contract as JEI/Create: compiled against the EMI {@code :api} only
 * and loaded by EMI exclusively when present (it scans for {@link EmiEntrypoint}), so the class is
 * never touched in the standalone jar.
 */
@EmiEntrypoint
public class ToxicSurfaceEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        for (HintInfo.Entry entry : HintInfo.entries()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "info/" + entry.key());
            registry.addRecipe(new EmiInfoRecipe(
                    List.<EmiIngredient>of(EmiStack.of(entry.item())), List.of(HintInfo.text(entry.key())), id));
        }

        // Weaving category: the Weaver (and the Mechanical Weaver when Create is present) as workstations.
        registry.addCategory(ToxicSurfaceEmiCategories.WEAVING);
        registry.addWorkstation(ToxicSurfaceEmiCategories.WEAVING, EmiStack.of(ModItems.WEAVER.get()));
        workstationById(registry, ToxicSurfaceEmiCategories.WEAVING, "mechanical_weaver");
        int wi = 0;
        for (WeaverLogic.WeaveRecipe recipe : WeaverLogic.recipes()) {
            registry.addRecipe(new WeavingEmiRecipe(recipe, wi++));
        }

        // Generator-fuel category: only when the generators exist (Create present → non-empty rows).
        List<MachineFuel.Row> fuels = MachineFuel.rows();
        if (!fuels.isEmpty()) {
            registry.addCategory(ToxicSurfaceEmiCategories.GENERATOR_FUEL);
            int fi = 0;
            for (MachineFuel.Row row : fuels) {
                registry.addWorkstation(ToxicSurfaceEmiCategories.GENERATOR_FUEL, EmiStack.of(row.machine()));
                registry.addRecipe(new GeneratorFuelEmiRecipe(row, fi++));
            }
        }
    }

    /** Add a workstation by registry id, skipped when the (Create-gated) machine isn't present. */
    private static void workstationById(
            EmiRegistry registry, dev.emi.emi.api.recipe.EmiRecipeCategory category, String path) {
        net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, path))
                .ifPresent(item -> registry.addWorkstation(category, EmiStack.of(item)));
    }
}
