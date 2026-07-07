// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.thomasjoleary.toxicsurface.block.SludgeReclaimer;
import io.github.thomasjoleary.toxicsurface.compat.jade.JadeReadout;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.core.machine.CleanserRange;
import io.github.thomasjoleary.toxicsurface.world.CleanserBubbles;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
public class MechanicalCleanserBlockEntity extends KineticBlockEntity implements JadeReadout {
    /** Stress units consumed per RPM (Create's stress model); makes the machine a real load. */
    private static final float STRESS_IMPACT = 8f;

    private int scanCursor;
    /** Last reclamation range driven this tick, surfaced to Jade (parity with the fuel Cleanser). */
    private int effectiveRange;

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
        effectiveRange = range; // surfaced to Jade

        if (range > 0 && SludgeReclaimer.canReclaim(level)) {
            scanCursor = SludgeReclaimer.tickActive((ServerLevel) level, pos, range, scanCursor);
        } else if (level instanceof ServerLevel sl) {
            CleanserBubbles.remove(sl, pos); // idle / unpowered: the bubble collapses
        }
    }

    @Override
    public void appendJadeData(CompoundTag tag) {
        tag.putInt("tsRange", effectiveRange);
        tag.putBoolean("tsActive", effectiveRange > 0);
        int rpm = (int) Math.abs(getSpeed());
        if (rpm > 0) {
            tag.putInt("tsRpm", rpm);
        }
    }
}
