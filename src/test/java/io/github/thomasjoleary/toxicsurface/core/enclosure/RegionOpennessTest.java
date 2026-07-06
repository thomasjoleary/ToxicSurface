// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.enclosure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for the region-wide exposure classifier behind the fog renderer (DESIGN.md §3 gas
 * visibility). The invariant under test: fog appears exactly where the atmosphere reaches — under
 * overhangs, in caves, and through a breached wall — while a sealed room's air stays unmarked.
 */
class RegionOpennessTest {
    private static final int S = 12;
    private static final int BUDGET = 4096;

    private static long[] emptyRegion(RegionOpenness region) {
        return new long[(region.cellCount() + 63) >> 6];
    }

    /** All-air region with a hollow box: solid shell spanning {@code [lo..hi]}³, air inside. */
    private static long[] hollowBox(RegionOpenness region, int lo, int hi) {
        long[] passable = emptyRegion(region);
        for (int y = 0; y < S; y++) {
            for (int z = 0; z < S; z++) {
                for (int x = 0; x < S; x++) {
                    boolean inShellBounds = x >= lo && x <= hi && y >= lo && y <= hi && z >= lo && z <= hi;
                    boolean interior = x > lo && x < hi && y > lo && y < hi && z > lo && z < hi;
                    if (!inShellBounds || interior) {
                        RegionOpenness.set(passable, region.index(x, y, z));
                    }
                }
            }
        }
        return passable;
    }

    @Test
    void openRegion_isFullyExposed() {
        RegionOpenness region = new RegionOpenness(S, S, S);
        long[] passable = emptyRegion(region);
        for (int i = 0; i < region.cellCount(); i++) {
            RegionOpenness.set(passable, i);
        }
        long[] exposed = emptyRegion(region);
        region.classify(passable, BUDGET, exposed);
        assertTrue(RegionOpenness.get(exposed, region.index(5, 5, 5)));
        assertTrue(RegionOpenness.get(exposed, region.index(0, 0, 0)));
    }

    @Test
    void sealedRoom_isNotExposed_butOutsideIs() {
        RegionOpenness region = new RegionOpenness(S, S, S);
        long[] passable = hollowBox(region, 3, 8);
        long[] exposed = emptyRegion(region);
        region.classify(passable, BUDGET, exposed);
        assertFalse(RegionOpenness.get(exposed, region.index(5, 5, 5)), "sealed interior must stay clear");
        assertTrue(RegionOpenness.get(exposed, region.index(1, 1, 1)), "outside air must be exposed");
        assertFalse(RegionOpenness.get(exposed, region.index(3, 5, 5)), "solid shell is never exposed");
    }

    @Test
    void breachedRoom_floodsWithExposure() {
        // The regression that motivated this class: knock one block out of a sealed room's wall and
        // the whole interior must read exposed (fog floods in), exactly as the damage scanner rules.
        RegionOpenness region = new RegionOpenness(S, S, S);
        long[] passable = hollowBox(region, 3, 8);
        RegionOpenness.set(passable, region.index(3, 5, 5)); // the breach
        long[] exposed = emptyRegion(region);
        region.classify(passable, BUDGET, exposed);
        assertTrue(RegionOpenness.get(exposed, region.index(5, 5, 5)), "breached interior must be exposed");
        assertTrue(RegionOpenness.get(exposed, region.index(7, 7, 7)), "exposure reaches the far corner");
    }

    @Test
    void pocketTouchingRegionBoundary_readsExposed() {
        // A box missing the wall on the region's edge: its air touches the boundary, so we cannot
        // prove it closes — treated exposed, the grid analogue of the scanner's budget bail-out.
        RegionOpenness region = new RegionOpenness(S, S, S);
        long[] passable = hollowBox(region, 0, 5); // shell at x/y/z 0 is the region boundary itself
        for (int y = 1; y < 5; y++) {
            for (int z = 1; z < 5; z++) {
                RegionOpenness.set(passable, region.index(0, y, z)); // open face flush with the boundary
            }
        }
        long[] exposed = emptyRegion(region);
        region.classify(passable, BUDGET, exposed);
        assertTrue(RegionOpenness.get(exposed, region.index(2, 2, 2)));
    }

    @Test
    void oversizedPocket_readsExposed_smallOneStaysSealed() {
        // Interior of the 3..8 hollow box is 4x4x4 = 64 cells: sealed under a generous budget, but a
        // budget below 64 must flip it exposed — mirroring EnclosureScanner's budget rule.
        RegionOpenness region = new RegionOpenness(S, S, S);
        long[] passable = hollowBox(region, 3, 8);
        long[] exposed = emptyRegion(region);
        region.classify(passable, 63, exposed);
        assertTrue(RegionOpenness.get(exposed, region.index(5, 5, 5)), "over-budget pocket is exposed");
        region.classify(passable, 64, exposed);
        assertFalse(RegionOpenness.get(exposed, region.index(5, 5, 5)), "within-budget pocket stays sealed");
    }
}
