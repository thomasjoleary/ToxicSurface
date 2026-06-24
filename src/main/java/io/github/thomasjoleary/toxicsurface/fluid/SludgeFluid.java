// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Toxic sludge fluid (DESIGN.md §3) — a {@link BaseFlowingFluid} that <b>floats on water</b>. Sludge
 * is denser-feeling toxic muck that sits on the surface, so it must never sink into or displace a
 * water cell: {@link #canSpreadTo} refuses any target that already holds water (down or sideways),
 * which keeps the converted surface skin sitting on top of clean water instead of flowing through
 * it. Over air/land it flows like a normal liquid. Both the source and flowing forms share the rule.
 */
public final class SludgeFluid {
    private SludgeFluid() {}

    /** True if sludge may spread into the target cell: anything but a water-filled one. */
    private static boolean floatsOver(FluidState toFluidState) {
        return !toFluidState.is(FluidTags.WATER);
    }

    public static final class Source extends BaseFlowingFluid.Source {
        public Source(BaseFlowingFluid.Properties properties) {
            super(properties);
        }

        @Override
        protected boolean canSpreadTo(
                BlockGetter level,
                BlockPos fromPos,
                BlockState fromState,
                Direction direction,
                BlockPos toPos,
                BlockState toState,
                FluidState toFluidState,
                Fluid fluid) {
            return floatsOver(toFluidState)
                    && super.canSpreadTo(level, fromPos, fromState, direction, toPos, toState, toFluidState, fluid);
        }
    }

    public static final class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(BaseFlowingFluid.Properties properties) {
            super(properties);
        }

        @Override
        protected boolean canSpreadTo(
                BlockGetter level,
                BlockPos fromPos,
                BlockState fromState,
                Direction direction,
                BlockPos toPos,
                BlockState toState,
                FluidState toFluidState,
                Fluid fluid) {
            return floatsOver(toFluidState)
                    && super.canSpreadTo(level, fromPos, fromState, direction, toPos, toState, toFluidState, fluid);
        }
    }
}
