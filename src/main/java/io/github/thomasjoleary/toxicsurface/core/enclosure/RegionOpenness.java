// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.enclosure;

import java.util.Arrays;

/**
 * Classifies every cell of a 3D region as exposed-to-atmosphere or sealed, mirroring
 * {@link EnclosureScanner}'s point semantics over a whole grid (DESIGN.md §2a, §3 gas visibility).
 * The toxic-fog renderer uses this so the <em>visual</em> gas matches the damage predicate cell for
 * cell: fog fills any air the atmosphere can reach (open ground, under overhangs, caves, a breached
 * room) and stays out of air it cannot (a sealed base).
 *
 * <p>A cell is <b>exposed</b> when its connected passable pocket either
 * <ul>
 *   <li>touches the region boundary — from inside the region we cannot prove it closes, which is the
 *       grid analogue of the scanner's "grew past the budget without closing", or
 *   <li>is larger than {@code maxSealedSize} — the scanner's budget rule itself, so a cavern too big
 *       to count as sealed reads as toxic here too.
 * </ul>
 * Every other passable cell belongs to a pocket that provably closes within the region and budget,
 * i.e. it is sealed; impassable cells are never exposed. Passability must come from the same probe
 * the server scans with so door/piston/collision rules never diverge.
 *
 * <p>Pure array logic with no Minecraft dependencies (unit-tested); the renderer supplies passability
 * as a bit set. Instances pre-allocate their BFS scratch and are meant to be reused; not thread-safe.
 */
public final class RegionOpenness {
    private final int sx;
    private final int sy;
    private final int sz;
    private final int cellCount;
    private final int[] queue;
    private final long[] visited;

    public RegionOpenness(int sx, int sy, int sz) {
        if (sx < 1 || sy < 1 || sz < 1) {
            throw new IllegalArgumentException("region dimensions must be >= 1");
        }
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.cellCount = sx * sy * sz;
        this.queue = new int[cellCount];
        this.visited = new long[(cellCount + 63) >> 6];
    }

    /** Cells in the region; bit sets passed to {@link #classify} must cover this many bits. */
    public int cellCount() {
        return cellCount;
    }

    /** Flat index of a cell; the same layout the caller must use for the bit sets. */
    public int index(int x, int y, int z) {
        return (y * sz + z) * sx + x;
    }

    /**
     * Fills {@code exposedOut} with the exposed cells of {@code passable} (both region-sized bit sets;
     * {@code exposedOut} is cleared first). See the class doc for what exposed means.
     */
    public void classify(long[] passable, int maxSealedSize, long[] exposedOut) {
        Arrays.fill(visited, 0L);
        Arrays.fill(exposedOut, 0L);
        int budget = Math.max(1, maxSealedSize);

        // Pass 1: flood inward from every passable boundary cell; everything reached is exposed.
        int tail = 0;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                if (y == 0 || y == sy - 1 || z == 0 || z == sz - 1) {
                    for (int x = 0; x < sx; x++) {
                        tail = seed(index(x, y, z), passable, exposedOut, tail);
                    }
                } else {
                    tail = seed(index(0, y, z), passable, exposedOut, tail);
                    tail = seed(index(sx - 1, y, z), passable, exposedOut, tail);
                }
            }
        }
        expand(0, tail, passable, exposedOut);

        // Pass 2: the remaining passable cells form interior pockets that provably close within the
        // region. A pocket within the budget is sealed (left unmarked); one over it is exposed, exactly
        // as the scanner would rule.
        for (int i = 0; i < cellCount; i++) {
            if (!get(passable, i) || get(visited, i)) {
                continue;
            }
            set(visited, i);
            queue[0] = i;
            int pocketSize = expand(0, 1, passable, null);
            if (pocketSize > budget) {
                for (int k = 0; k < pocketSize; k++) {
                    set(exposedOut, queue[k]);
                }
            }
        }
    }

    /** Enqueues a boundary cell as exposed if it is passable and unseen; returns the new tail. */
    private int seed(int idx, long[] passable, long[] exposedOut, int tail) {
        if (get(passable, idx) && !get(visited, idx)) {
            set(visited, idx);
            set(exposedOut, idx);
            queue[tail++] = idx;
        }
        return tail;
    }

    /**
     * BFS over passable cells from {@code queue[head..tail)}, marking visited (and exposed when
     * {@code exposedOut} is non-null) as it goes. Returns the total number of cells processed.
     */
    private int expand(int head, int tail, long[] passable, long[] exposedOut) {
        while (head < tail) {
            int idx = queue[head++];
            int x = idx % sx;
            int t = idx / sx;
            int z = t % sz;
            int y = t / sz;
            if (x > 0) tail = visit(idx - 1, passable, exposedOut, tail);
            if (x < sx - 1) tail = visit(idx + 1, passable, exposedOut, tail);
            if (z > 0) tail = visit(idx - sx, passable, exposedOut, tail);
            if (z < sz - 1) tail = visit(idx + sx, passable, exposedOut, tail);
            if (y > 0) tail = visit(idx - sx * sz, passable, exposedOut, tail);
            if (y < sy - 1) tail = visit(idx + sx * sz, passable, exposedOut, tail);
        }
        return tail;
    }

    private int visit(int idx, long[] passable, long[] exposedOut, int tail) {
        if (get(passable, idx) && !get(visited, idx)) {
            set(visited, idx);
            if (exposedOut != null) {
                set(exposedOut, idx);
            }
            queue[tail++] = idx;
        }
        return tail;
    }

    /** Reads bit {@code idx} of a region-sized bit set (layout per {@link #index}). */
    public static boolean get(long[] bits, int idx) {
        return (bits[idx >> 6] & (1L << idx)) != 0;
    }

    /** Sets bit {@code idx} of a region-sized bit set (layout per {@link #index}). */
    public static void set(long[] bits, int idx) {
        bits[idx >> 6] |= 1L << idx;
    }
}
