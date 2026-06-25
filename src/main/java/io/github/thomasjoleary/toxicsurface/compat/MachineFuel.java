// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.core.generator.GeneratorFuel;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Shared source of the toxic-generator fuel rows shown by the JEI ({@code compat.jei}) and EMI
 * ({@code compat.emi}) recipe categories, so the two viewers stay in lock-step. Each {@link Row}
 * pairs a fuel item with the {@link GeneratorFuel} power tier it drives and the generator it feeds.
 *
 * <p>Free of any JEI/EMI types so it lives in the base mod. The generator block is the Create-gated
 * machine the fuel belongs in; it is resolved by registry <b>id</b> (not a class reference) and a
 * row is dropped when that generator isn't registered, so this never classloads {@code compat.create}
 * and yields no rows in the standalone jar (where the generators don't exist).
 */
public final class MachineFuel {
    private MachineFuel() {}

    /**
     * One fuel row.
     *
     * @param fuel the item (or filled bucket) inserted as fuel
     * @param machine the generator block-item this fuel feeds (the "made in" catalyst)
     * @param tier the rotation/capacity tier this fuel drives
     * @param continuous true for the sludge generator (steady drain) vs. a per-unit solid burn
     * @param mbPerTick sludge consumed per tick while running (continuous fuels only)
     */
    public record Row(ItemStack fuel, ItemStack machine, GeneratorFuel.Fuel tier, boolean continuous, int mbPerTick) {}

    public static List<Row> rows() {
        List<Row> list = new ArrayList<>();
        ItemStack wasteGen = byId("waste_generator");
        ItemStack sludgeGen = byId("sludge_generator");
        if (!wasteGen.isEmpty()) {
            add(list, ModItems.TOXIC_RESIDUE.get(), wasteGen, GeneratorFuel.RESIDUE, false, 0);
            add(list, ModItems.TOXIC_WASTE_BLOCK.get(), wasteGen, GeneratorFuel.WASTE_BLOCK, false, 0);
        }
        if (!sludgeGen.isEmpty()) {
            add(
                    list,
                    ModItems.SLUDGE_BUCKET.get(),
                    sludgeGen,
                    GeneratorFuel.SLUDGE,
                    true,
                    GeneratorFuel.SLUDGE_MB_PER_TICK);
        }
        return list;
    }

    private static void add(
            List<Row> list, ItemLike fuel, ItemStack machine, GeneratorFuel.Fuel tier, boolean continuous, int mb) {
        list.add(new Row(new ItemStack(fuel), machine, tier, continuous, mb));
    }

    /** The generator block-item by registry id, or {@link ItemStack#EMPTY} when Create is absent. */
    private static ItemStack byId(String path) {
        return BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, path))
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
    }
}
