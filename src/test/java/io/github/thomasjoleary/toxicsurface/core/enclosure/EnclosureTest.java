// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.enclosure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for the enclosure flood-fill and cache (DESIGN.md §2a, §10). All cases run on
 * in-memory grids with no Minecraft dependency.
 */
class EnclosureTest {

    /** A probe where everything is passable except an explicit set of solid cells. */
    private static final class GridProbe implements PassabilityProbe {
        private final Set<Long> solid = new HashSet<>();

        void setSolid(int x, int y, int z) {
            solid.add(CellKey.pack(x, y, z));
        }

        /** Builds the six faces of an inclusive [min,max] cube as solid walls. */
        void hollowBox(int min, int max) {
            for (int x = min; x <= max; x++) {
                for (int y = min; y <= max; y++) {
                    for (int z = min; z <= max; z++) {
                        boolean shell = x == min || x == max || y == min || y == max || z == min || z == max;
                        if (shell) {
                            setSolid(x, y, z);
                        }
                    }
                }
            }
        }

        @Override
        public boolean isPassable(int x, int y, int z) {
            return !solid.contains(CellKey.pack(x, y, z));
        }
    }

    // A hollow box over [0,4] has a 3x3x3 = 27-cell air interior centered at (2,2,2).
    private static final int INTERIOR_CELLS = 27;

    @Test
    void sealedRoom_isSealed() {
        GridProbe probe = new GridProbe();
        probe.hollowBox(0, 4);

        ScanResult result = EnclosureScanner.scan(2, 2, 2, probe, 4096);

        assertTrue(result.isSealed());
        assertEquals(INTERIOR_CELLS, result.size());
        assertTrue(result.contains(1, 1, 1));
    }

    @Test
    void roomWithHole_isExposed() {
        GridProbe probe = new GridProbe();
        probe.hollowBox(0, 4);
        // Punch a hole in a wall so the interior connects to the infinite outside.
        probe.solid.remove(CellKey.pack(0, 2, 2));

        ScanResult result = EnclosureScanner.scan(2, 2, 2, probe, 1000);

        assertFalse(result.isSealed());
        assertEquals(0, result.size());
    }

    @Test
    void budgetBoundary_sealedAtExactSizeExposedJustUnder() {
        GridProbe probe = new GridProbe();
        probe.hollowBox(0, 4);

        // Budget exactly equal to the pocket size still reads sealed.
        assertTrue(EnclosureScanner.scan(2, 2, 2, probe, INTERIOR_CELLS).isSealed());
        // One under the pocket size reads exposed (can't confirm the seal in budget).
        assertFalse(EnclosureScanner.scan(2, 2, 2, probe, INTERIOR_CELLS - 1).isSealed());
    }

    @Test
    void headInSolid_isSealed() {
        GridProbe probe = new GridProbe();
        probe.setSolid(5, 5, 5);

        ScanResult result = EnclosureScanner.scan(5, 5, 5, probe, 4096);

        assertTrue(result.isSealed());
        assertEquals(0, result.size());
    }

    @Test
    void cache_hitForPocketMembers_missElsewhere() {
        GridProbe probe = new GridProbe();
        probe.hollowBox(0, 4);
        ScanResult sealed = EnclosureScanner.scan(2, 2, 2, probe, 4096);

        EnclosureCache cache = new EnclosureCache();
        cache.putSealed(sealed);

        assertSame(sealed, cache.get(1, 1, 1)); // a member cell hits
        assertSame(sealed, cache.get(3, 3, 3)); // another member cell hits
        assertNull(cache.get(100, 100, 100)); // far cell misses
        assertEquals(1, cache.pocketCount());
    }

    @Test
    void cache_invalidatedWhenBoundingBoxChanges() {
        GridProbe probe = new GridProbe();
        probe.hollowBox(0, 4);
        ScanResult sealed = EnclosureScanner.scan(2, 2, 2, probe, 4096);

        EnclosureCache cache = new EnclosureCache();
        cache.putSealed(sealed);
        assertNotNull(cache.get(2, 2, 2));

        // Changing a wall cell (just outside the air pocket) must drop the pocket.
        cache.invalidate(0, 2, 2);

        assertNull(cache.get(2, 2, 2));
        assertEquals(0, cache.pocketCount());
    }

    /** A one-cell sealed pocket at a distinct coordinate, for cache-capacity tests. */
    private static ScanResult singleCellPocket(int x, int y, int z) {
        Set<Long> cells = new HashSet<>();
        cells.add(CellKey.pack(x, y, z));
        return ScanResult.sealed(cells, x, y, z, x, y, z);
    }

    @Test
    void cache_evictsLeastRecentlyUsedBeyondCapacity() {
        EnclosureCache cache = new EnclosureCache(2);
        ScanResult a = singleCellPocket(0, 0, 0);
        ScanResult b = singleCellPocket(10, 0, 0);
        ScanResult c = singleCellPocket(20, 0, 0);

        cache.putSealed(a);
        cache.putSealed(b);
        cache.get(0, 0, 0); // touch A so B becomes the least-recently used
        cache.putSealed(c); // over capacity -> evict the LRU (B)

        assertEquals(2, cache.pocketCount());
        assertNotNull(cache.get(0, 0, 0)); // A retained (recently used)
        assertNotNull(cache.get(20, 0, 0)); // C retained (just added)
        assertNull(cache.get(10, 0, 0)); // B evicted
    }

    @Test
    void cache_unrelatedChangeKeepsPocket() {
        GridProbe probe = new GridProbe();
        probe.hollowBox(0, 4);
        ScanResult sealed = EnclosureScanner.scan(2, 2, 2, probe, 4096);

        EnclosureCache cache = new EnclosureCache();
        cache.putSealed(sealed);

        // A change far from the pocket's bounding box leaves it cached.
        cache.invalidate(50, 50, 50);

        assertNotNull(cache.get(2, 2, 2));
        assertEquals(1, cache.pocketCount());
    }

    @Test
    void cache_expiresPocketPastMaxAge() {
        ScanResult sealed = singleCellPocket(0, 0, 0);
        EnclosureCache cache = new EnclosureCache(EnclosureCache.DEFAULT_CAPACITY, 100); // TTL 100 ticks
        cache.putSealed(sealed, 1_000L);

        assertSame(sealed, cache.get(0, 0, 0, 1_050L)); // within TTL -> hit
        assertNull(cache.get(0, 0, 0, 1_200L)); // past TTL -> miss (self-heal for untracked changes)
        assertEquals(0, cache.pocketCount()); // and the stale pocket is dropped
    }
}
