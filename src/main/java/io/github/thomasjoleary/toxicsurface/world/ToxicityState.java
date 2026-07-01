// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-dimension toxicity state (DESIGN.md §3). Persists the world tick at which the
 * surface first turned toxic; everything else (current ceiling Y, sludge depth) is
 * derived on demand via {@code ToxicityModel} so config changes take effect live.
 */
public final class ToxicityState extends SavedData {
    public static final long NOT_STARTED = -1L;

    /** Sentinel for {@link #ceilingOverride()}: no override, use the computed model (DESIGN.md §12). */
    public static final int NO_OVERRIDE = Integer.MIN_VALUE;

    private static final String NAME = "toxicsurface_toxicity";
    private static final String KEY_START_TICK = "startTick";
    private static final String KEY_POLLUTION = "pollutionTicks";
    private static final String KEY_TELEGRAPH_STAGE = "telegraphStage";
    private static final String KEY_CEILING_OVERRIDE = "ceilingOverride";
    private static final String KEY_SUPPRESSED = "suppressed";

    private long startTick = NOT_STARTED;

    /** Accumulated generator pollution; added to elapsed time so escalation runs faster (§7). */
    private long pollutionTicks = 0L;

    /** How many pre-toxicity telegraph thresholds have already fired (DESIGN.md §3). */
    private int telegraphStage = 0;

    /** A testing-command override forcing the ceiling to a fixed Y regardless of the model (§12). */
    private int ceilingOverride = NO_OVERRIDE;

    /**
     * Set by {@code /toxicsurface toxicity off} to stop the natural clock from immediately
     * re-triggering: {@link ToxicityTicker#onLevelTick} compares the world's current tick against
     * the absolute {@code timeToToxicTicks} threshold, which stays crossed forever once real game
     * time has passed it, so simply un-setting the start tick isn't enough (DESIGN.md §12).
     */
    private boolean suppressed = false;

    public static ToxicityState get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(ToxicityState::new, ToxicityState::load), NAME);
    }

    private static ToxicityState load(CompoundTag tag, HolderLookup.Provider registries) {
        ToxicityState state = new ToxicityState();
        state.startTick = tag.contains(KEY_START_TICK) ? tag.getLong(KEY_START_TICK) : NOT_STARTED;
        state.pollutionTicks = tag.getLong(KEY_POLLUTION); // absent → 0
        state.telegraphStage = tag.getInt(KEY_TELEGRAPH_STAGE); // absent → 0
        state.ceilingOverride = tag.contains(KEY_CEILING_OVERRIDE) ? tag.getInt(KEY_CEILING_OVERRIDE) : NO_OVERRIDE;
        state.suppressed = tag.getBoolean(KEY_SUPPRESSED); // absent → false
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(KEY_START_TICK, startTick);
        tag.putLong(KEY_POLLUTION, pollutionTicks);
        tag.putInt(KEY_TELEGRAPH_STAGE, telegraphStage);
        tag.putInt(KEY_CEILING_OVERRIDE, ceilingOverride);
        tag.putBoolean(KEY_SUPPRESSED, suppressed);
        return tag;
    }

    public int telegraphStage() {
        return telegraphStage;
    }

    /** Records that telegraph warnings up to {@code stage} have fired. */
    public void setTelegraphStage(int stage) {
        if (stage != telegraphStage) {
            this.telegraphStage = stage;
            setDirty();
        }
    }

    public boolean hasStarted() {
        return startTick != NOT_STARTED;
    }

    public long startTick() {
        return startTick;
    }

    /** Records the start tick the first time toxicity activates; no-op afterwards. */
    public void startNow(long worldTick) {
        if (!hasStarted()) {
            this.startTick = worldTick;
            setDirty();
        }
    }

    /** Accumulated generator pollution, in escalation ticks (DESIGN.md §7). */
    public long pollutionTicks() {
        return pollutionTicks;
    }

    /** Adds generator pollution; the escalation model treats it as extra elapsed time. */
    public void addPollution(long ticks) {
        if (ticks <= 0) {
            return;
        }
        pollutionTicks += ticks;
        setDirty();
    }

    /** The forced ceiling Y, or {@link #NO_OVERRIDE} if the model's computed value should be used. */
    public int ceilingOverride() {
        return ceilingOverride;
    }

    /** Forces the ceiling to {@code y} regardless of the escalation model (testing command, §12). */
    public void setCeilingOverride(int y) {
        if (y != ceilingOverride) {
            ceilingOverride = y;
            setDirty();
        }
    }

    /** Resumes the escalation model's computed ceiling. */
    public void clearCeilingOverride() {
        setCeilingOverride(NO_OVERRIDE);
    }

    /** Reverts this dimension to pre-toxicity: clears the start tick, pollution, telegraph, override. */
    public void reset() {
        startTick = NOT_STARTED;
        pollutionTicks = 0L;
        telegraphStage = 0;
        ceilingOverride = NO_OVERRIDE;
        setDirty();
    }

    /** Whether the natural clock is held back from re-triggering (see {@link #suppressed}). */
    public boolean isSuppressed() {
        return suppressed;
    }

    public void setSuppressed(boolean value) {
        if (value != suppressed) {
            suppressed = value;
            setDirty();
        }
    }
}
