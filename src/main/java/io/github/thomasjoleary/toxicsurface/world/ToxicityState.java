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

    private long startTick = NOT_STARTED;

    public static ToxicityState get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(ToxicityState::new, ToxicityState::load), NAME);
    }

    private static ToxicityState load(CompoundTag tag, HolderLookup.Provider registries) {
        ToxicityState state = new ToxicityState();
        state.startTick = tag.contains(KEY_START_TICK) ? tag.getLong(KEY_START_TICK) : NOT_STARTED;
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(KEY_START_TICK, startTick);
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
}
