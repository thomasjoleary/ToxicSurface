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
 * Toxic sludge fluid (DESIGN.md §3) — a {@link BaseFlowingFluid} that <b>floats on water</b> and
 * treats it like a solid surface. Sludge and water are made mutually impermeable so the converted
 * surface skin sits on top of clean water as a stable sheet instead of either fluid eating the
 * other:
 *
 * <ul>
 *   <li>{@link #canSpreadTo} refuses any water-filled target — sludge never sinks into or displaces
 *       water (vanilla would let it fall straight down, since water is replaceable downward).
 *   <li>{@link #canBeReplacedWith} refuses water as the incoming fluid — water can never flow into
 *       or erase the sludge layer (vanilla water replaces a sludge cell from above, which made the
 *       skin "disappear").
 * </ul>
 *
 * <p>Over air and land it flows like a normal liquid. Both the source and flowing forms share the
 * two rules via the static helpers; the rules are intentionally one-directional toward water only,
 * so sludge's own flow (sludge replacing sludge) is untouched.
 */
public final class SludgeFluid {
    private SludgeFluid() {}

    /** Sludge may spread into a target cell only if it is not water-filled. */
    private static boolean canFlowInto(FluidState toFluidState) {
        return !toFluidState.is(FluidTags.WATER);
    }

    /** Sludge may be replaced by an incoming fluid only if that fluid is not water. */
    private static boolean canYieldTo(Fluid incoming) {
        return !incoming.is(FluidTags.WATER);
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
            return canFlowInto(toFluidState)
                    && super.canSpreadTo(level, fromPos, fromState, direction, toPos, toState, toFluidState, fluid);
        }

        @Override
        protected boolean canBeReplacedWith(
                FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
            return canYieldTo(fluid) && super.canBeReplacedWith(state, level, pos, fluid, direction);
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
            return canFlowInto(toFluidState)
                    && super.canSpreadTo(level, fromPos, fromState, direction, toPos, toState, toFluidState, fluid);
        }

        @Override
        protected boolean canBeReplacedWith(
                FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
            return canYieldTo(fluid) && super.canBeReplacedWith(state, level, pos, fluid, direction);
        }
    }
}
