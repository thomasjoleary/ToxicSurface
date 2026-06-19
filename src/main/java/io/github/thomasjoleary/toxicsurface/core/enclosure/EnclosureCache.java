// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.enclosure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Connected-component cache for sealed air pockets (DESIGN.md §2a, §8). Looking up
 * any cell inside a known sealed pocket returns the cached result, so the expensive
 * flood-fill runs once per pocket rather than once per entity per tick.
 *
 * <p>Only {@code SEALED} results are cached — they are bounded and well-defined.
 * Exposed results are cheap to leave uncached and are recomputed on a throttle. When
 * a block changes, {@link #invalidate} drops every pocket whose bounding box the
 * change touches, since that change may have breached a seal.
 *
 * <p>Not thread-safe; intended to be used from the server thread.
 */
public final class EnclosureCache {
    private final Map<Long, ScanResult> byCell = new HashMap<>();
    private final List<ScanResult> sealedPockets = new ArrayList<>();

    /** Cached result for the pocket containing this cell, or {@code null} on a miss. */
    public ScanResult get(int x, int y, int z) {
        return byCell.get(CellKey.pack(x, y, z));
    }

    /** Stores a sealed result, indexing every cell of its pocket. No-op for exposed results. */
    public void putSealed(ScanResult result) {
        if (!result.isSealed() || result.size() == 0) {
            return;
        }
        for (long cell : result.cells()) {
            byCell.put(cell, result);
        }
        sealedPockets.add(result);
    }

    /** Drops every cached pocket whose bounding box (grown by one) contains the changed block. */
    public void invalidate(int x, int y, int z) {
        Iterator<ScanResult> it = sealedPockets.iterator();
        while (it.hasNext()) {
            ScanResult pocket = it.next();
            if (pocket.boundingBoxTouches(x, y, z)) {
                for (long cell : pocket.cells()) {
                    byCell.remove(cell);
                }
                it.remove();
            }
        }
    }

    public void clear() {
        byCell.clear();
        sealedPockets.clear();
    }

    /** Number of distinct sealed pockets currently cached. */
    public int pocketCount() {
        return sealedPockets.size();
    }
}
