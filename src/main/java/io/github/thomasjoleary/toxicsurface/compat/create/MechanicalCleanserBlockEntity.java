// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.thomasjoleary.toxicsurface.block.SludgeReclaimer;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.core.machine.CleanserRange;
import io.github.thomasjoleary.toxicsurface.world.CleanserBubbles;
import io.github.thomasjoleary.toxicsurface.world.CleanserVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Mechanical Cleanser (DESIGN.md §3 "Create variant") — the rotation-powered sibling of
 * the fuel {@link io.github.thomasjoleary.toxicsurface.block.CleanserBlockEntity}. Instead of
 * burning furnace fuel it consumes Create rotational force: the reclamation range scales with
 * the supplied RPM ({@link CleanserRange#rangeFromRpm}), and an over-stressed network or a
 * redstone signal halts it. The sludge-reversion sweep and the breathable bubble are the same
 * {@link SludgeReclaimer} pass the fuel variant runs, so the two machines behave identically
 * except for what powers them. This class extends Create's kinetic API and is therefore only
 * ever loaded when Create is present (registered via {@link CreateContent}).
 */
public class MechanicalCleanserBlockEntity extends KineticBlockEntity {
    /** Stress units consumed per RPM (Create's stress model); makes the machine a real load. */
    private static final float STRESS_IMPACT = 8f;

    /** Cells scanned per active tick for the reversion sweep — matches the fuel Cleanser. */
    private static final int SCAN_BUDGET = 4096;

    private int scanCursor;

    public MechanicalCleanserBlockEntity(BlockPos pos, BlockState state) {
        super(CreateContent.MECHANICAL_CLEANSER_BE.get(), pos, state);
    }

    @Override
    public float calculateStressApplied() {
        this.lastStressApplied = STRESS_IMPACT;
        return STRESS_IMPACT;
    }

    @Override
    public void tick() {
        super.tick(); // Create rotation/stress network bookkeeping (both sides)
        if (level == null || level.isClientSide) {
            return;
        }
        BlockPos pos = getBlockPos();

        // A redstone signal halts it (parity with the fuel machines); an over-stressed network
        // can't drive it; otherwise the radius scales with the supplied rotation speed.
        boolean halted = level.getBestNeighborSignal(pos) > 0 || isOverStressed();
        int range = halted
                ? 0
                : CleanserRange.rangeFromRpm(
                        getSpeed(), ToxicSurfaceConfig.CLEANSER_TIERS.get(), CleanserRange.MIN_RPM);
        range = Math.min(range, ToxicSurfaceConfig.CLEANSER_MAX_RANGE.get());

        if (range > 0 && SludgeReclaimer.canReclaim(level)) {
            scanCursor = SludgeReclaimer.revertSludge(level, pos, range, SCAN_BUDGET, scanCursor);
            CleanserBubbles.update((ServerLevel) level, pos, range); // keep breathable air in range
            CleanserVisual.tick((ServerLevel) level, pos, range); // green clean-air dome particles
        } else if (level instanceof ServerLevel sl) {
            CleanserBubbles.remove(sl, pos); // idle / unpowered: the bubble collapses
        }
    }
}
