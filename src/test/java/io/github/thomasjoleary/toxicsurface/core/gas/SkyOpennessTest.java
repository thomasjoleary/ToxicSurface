// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.gas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests the sky-openness flood (DESIGN.md §3 gas visibility): fog must reach under an overhang that is
 * open to the side, yet stay out of a walled room. Columns are {@code {coverTop, airFloor}}; a wall has
 * {@code airFloor == coverTop} (no pocket) and blocks the flood, an overhang has a low {@code airFloor}.
 */
class SkyOpennessTest {
    private static final int MIN_Y = 0;
    private static final int MAX_Y = 256;

    /** Solves a 1-row line of columns given as {coverTop, airFloor} pairs. */
    private static int[] line(int[]... columns) {
        int w = columns.length;
        int[] cover = new int[w];
        int[] floor = new int[w];
        for (int i = 0; i < w; i++) {
            cover[i] = columns[i][0];
            floor[i] = columns[i][1];
        }
        return SkyOpenness.compute(w, 1, cover, floor, MIN_Y, MAX_Y);
    }

    @Test
    void openTerrain_isExposedToItsGround() {
        int[] sky = line(new int[] {64, 64}, new int[] {64, 64}, new int[] {64, 64});
        assertEquals(64, sky[0]);
        assertEquals(64, sky[1]);
        assertEquals(64, sky[2]);
    }

    @Test
    void overhang_floodsUnderTheLipFromOpenAir() {
        // open | overhang lip at 100 with air down to 64 | open  ->  the covered middle exposes to 64.
        int[] sky = line(new int[] {64, 64}, new int[] {100, 64}, new int[] {64, 64});
        assertEquals(64, sky[1]);
    }

    @Test
    void deepOverhang_floodsAllTheWayThrough() {
        // Three covered-but-open columns bridged by open sky at both ends stay exposed to the ground.
        int[] sky = line(
                new int[] {64, 64}, new int[] {100, 64}, new int[] {100, 64}, new int[] {100, 64}, new int[] {64, 64});
        assertEquals(64, sky[1]);
        assertEquals(64, sky[2]);
        assertEquals(64, sky[3]);
    }

    @Test
    void sealedRoom_staysClearBehindWalls() {
        // wall (solid to the roof) | room interior (roof at 100, floor at 64) | wall  ->  the interior only
        // opens at/above its own roof, so it is never exposed below 100 (fog stays out).
        int[] sky = line(new int[] {100, 100}, new int[] {100, 64}, new int[] {100, 100});
        assertEquals(100, sky[1]);
    }

    @Test
    void wallBlocksTheFlood_pocketBehindItIsNotReached() {
        // open | overhang | WALL | overhang: the last pocket is walled off from the open air and stays high.
        int[] sky = line(new int[] {64, 64}, new int[] {100, 64}, new int[] {100, 100}, new int[] {100, 64});
        assertEquals(64, sky[1]); // reached from the open air on its left
        assertEquals(100, sky[2]); // the wall itself
        assertEquals(100, sky[3]); // behind the wall — no path to sky below its own roof
    }

    @Test
    void openDoorway_letsFogSeepIn() {
        // A gap in the wall (a doorway column: roofed, but open air down to the floor) connects the interior
        // to the outside, so the interior exposes low — matching the model, where an unsealed room is toxic.
        int[] sky = line(
                new int[] {64, 64}, // outside ground
                new int[] {100, 64}, // doorway: roof over an open air column
                new int[] {100, 64}, // interior next to the doorway
                new int[] {100, 100}); // back wall
        assertEquals(64, sky[1]);
        assertEquals(64, sky[2]);
        assertEquals(100, sky[3]);
    }
}
