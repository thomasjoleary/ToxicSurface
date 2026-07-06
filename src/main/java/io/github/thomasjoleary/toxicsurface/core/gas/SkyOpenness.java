// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.gas;

/**
 * Turns a per-column height map into a per-column <em>sky-openness floor</em>: the lowest Y at which a
 * column's air still connects horizontally to the open sky, respecting walls (DESIGN.md §3 gas
 * visibility). This is what lets the toxic-fog renderer match {@link GasModel} — gas fills any unsealed
 * air, so fog must appear under an overhang (open to the side) yet stay out of a walled room — without a
 * true 3D flood fill.
 *
 * <p>Each column is described by two heights:
 * <ul>
 *   <li>{@code coverTop} — the top of the highest sealing cube (roof, ground, or an overhang lip);
 *       above it the column sees open sky directly.
 *   <li>{@code airFloor} — the floor of the air pocket beneath that cover. A solid wall or plain ground
 *       has {@code airFloor == coverTop} (no pocket); an overhang or a room has a low {@code airFloor}
 *       (open air reaches well below the cover).
 * </ul>
 *
 * <p>Openness floods outward from every column's own open sky (each is open at/above its {@code coverTop})
 * to its four neighbours: air can enter a neighbour at height {@code y} only if the neighbour has air
 * there ({@code y >= airFloor}). So a wall ({@code airFloor} high) blocks the flood, while an overhang
 * ({@code airFloor} low) lets it pass under. The result {@code skyFloor[c]} is the minimax height of the
 * cheapest path from column {@code c} to any open sky — i.e. the lowest Y at which {@code c} is exposed.
 * The fog shader then treats a cell as gas exactly when {@code y >= skyFloor} (and below the ceiling),
 * the same test it already applied to the raw terrain top.
 *
 * <p>Pure and unit-tested; the Minecraft-side renderer supplies the two height grids. Uses a bucketed
 * (Dial's) shortest-path over integer heights, so it is linear in columns plus the height range.
 */
public final class SkyOpenness {
    private SkyOpenness() {}

    /**
     * Computes {@code skyFloor} for a {@code width}×{@code height} row-major column grid. {@code coverTop}
     * and {@code airFloor} are indexed {@code z * width + x} and are clamped into {@code [minY, maxY]};
     * a column that sees no sky (fully walled off) keeps its {@code coverTop} as its floor.
     *
     * @return a new array of the same length; {@code skyFloor[c]} is the lowest exposed Y for column {@code c}
     */
    public static int[] compute(int width, int height, int[] coverTop, int[] airFloor, int minY, int maxY) {
        int n = width * height;
        int range = maxY - minY + 1;
        int[] sky = new int[n];
        // Dial's algorithm: a bucket per integer height, chained through nextInBucket to avoid per-node
        // allocation. A node may be enqueued several times as it relaxes lower; stale entries (whose height
        // no longer matches the bucket) are skipped when popped, and finalized nodes are settled once.
        int[] nextInBucket = new int[n];
        int[] bucketHead = new int[range];
        java.util.Arrays.fill(bucketHead, -1);
        boolean[] settled = new boolean[n];

        for (int i = 0; i < n; i++) {
            int start = clamp(coverTop[i], minY, maxY); // every column is open at/above its own cover
            sky[i] = start;
            int b = start - minY;
            nextInBucket[i] = bucketHead[b];
            bucketHead[b] = i;
        }

        for (int level = 0; level < range; level++) {
            int y = minY + level;
            while (bucketHead[level] != -1) {
                int i = bucketHead[level];
                bucketHead[level] = nextInBucket[i]; // pop
                if (settled[i] || sky[i] != y) {
                    continue; // stale duplicate from an earlier, higher-bucket enqueue
                }
                settled[i] = true;
                int x = i % width;
                int z = i / width;
                if (x > 0) {
                    relax(i - 1, y, sky, airFloor, minY, maxY, nextInBucket, bucketHead, settled);
                }
                if (x < width - 1) {
                    relax(i + 1, y, sky, airFloor, minY, maxY, nextInBucket, bucketHead, settled);
                }
                if (z > 0) {
                    relax(i - width, y, sky, airFloor, minY, maxY, nextInBucket, bucketHead, settled);
                }
                if (z < height - 1) {
                    relax(i + width, y, sky, airFloor, minY, maxY, nextInBucket, bucketHead, settled);
                }
            }
        }
        return sky;
    }

    /**
     * Openness at height {@code fromY} tries to enter neighbour {@code nb}: it can only reach as low as the
     * neighbour's air floor, so the connecting height is {@code max(fromY, airFloor[nb])}. If that is lower
     * than the neighbour's current best, record it and enqueue the neighbour in that height's bucket.
     */
    private static void relax(
            int nb,
            int fromY,
            int[] sky,
            int[] airFloor,
            int minY,
            int maxY,
            int[] nextInBucket,
            int[] bucketHead,
            boolean[] settled) {
        if (settled[nb]) {
            return;
        }
        int cand = Math.max(fromY, clamp(airFloor[nb], minY, maxY));
        if (cand < sky[nb]) {
            sky[nb] = cand;
            int b = cand - minY;
            nextInBucket[nb] = bucketHead[b];
            bucketHead[b] = nb;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
