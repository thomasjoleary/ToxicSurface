// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Toxic sludge — a real custom fluid (DESIGN.md §3 Toxic sludge). Registers the
 * {@link FluidType} plus the still/flowing {@link BaseFlowingFluid} pair. Behaves
 * like water (flows, swimmable, drownable); contact damage, organic-item destruction
 * and Create fluid-handler integration are layered on in later increments.
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ToxicSurface.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, ToxicSurface.MODID);

    public static final Supplier<FluidType> SLUDGE_TYPE = FLUID_TYPES.register(
            "sludge",
            () -> new FluidType(FluidType.Properties.create()
                    .density(3000) // denser/heavier-feeling than water
                    .viscosity(6000) // flows sluggishly
                    .canSwim(true)
                    .canDrown(true)));

    public static final Supplier<FlowingFluid> SLUDGE =
            FLUIDS.register("sludge", () -> new BaseFlowingFluid.Source(ModFluids.PROPERTIES));
    public static final Supplier<FlowingFluid> SLUDGE_FLOWING =
            FLUIDS.register("flowing_sludge", () -> new BaseFlowingFluid.Flowing(ModFluids.PROPERTIES));

    // Suppliers keep this lazy so it can reference the fluids/block/bucket before they register.
    public static final BaseFlowingFluid.Properties PROPERTIES = new BaseFlowingFluid.Properties(
                    SLUDGE_TYPE, SLUDGE, SLUDGE_FLOWING)
            .slopeFindDistance(2)
            .levelDecreasePerBlock(2)
            .block(() -> ModBlocks.SLUDGE_BLOCK.get())
            .bucket(() -> ModItems.SLUDGE_BUCKET.get());

    private ModFluids() {}
}
