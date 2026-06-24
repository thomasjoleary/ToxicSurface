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

    private static final String NAME = "toxicsurface_toxicity";
    private static final String KEY_START_TICK = "startTick";
    private static final String KEY_POLLUTION = "pollutionTicks";

    private long startTick = NOT_STARTED;

    /** Accumulated generator pollution; added to elapsed time so escalation runs faster (§7). */
    private long pollutionTicks = 0L;

    public static ToxicityState get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(ToxicityState::new, ToxicityState::load), NAME);
    }

    private static ToxicityState load(CompoundTag tag, HolderLookup.Provider registries) {
        ToxicityState state = new ToxicityState();
        state.startTick = tag.contains(KEY_START_TICK) ? tag.getLong(KEY_START_TICK) : NOT_STARTED;
        state.pollutionTicks = tag.getLong(KEY_POLLUTION); // absent → 0
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(KEY_START_TICK, startTick);
        tag.putLong(KEY_POLLUTION, pollutionTicks);
        return tag;
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
}
