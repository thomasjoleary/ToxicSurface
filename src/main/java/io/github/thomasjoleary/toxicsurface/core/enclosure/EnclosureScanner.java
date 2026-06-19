// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.enclosure;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Bounded flood-fill enclosure detection (DESIGN.md §2a). Starting from an entity's
 * head cell, it explores connected passable air. If the connected pocket closes off
 * within {@code budget} cells it is {@code SEALED}; if it reaches the budget without
 * closing it is {@code EXPOSED} to the toxic atmosphere.
 *
 * <p>Pure algorithm with no Minecraft dependencies so it can be exhaustively
 * unit-tested. This is the highest correctness risk in the mod — see the test suite.
 */
public final class EnclosureScanner {
    private static final int[][] NEIGHBORS = {
        {1, 0, 0}, {-1, 0, 0},
        {0, 1, 0}, {0, -1, 0},
        {0, 0, 1}, {0, 0, -1}
    };

    private EnclosureScanner() {}

    public static ScanResult scan(int startX, int startY, int startZ, PassabilityProbe probe, int budget) {
        if (budget < 1) {
            throw new IllegalArgumentException("budget must be >= 1, was " + budget);
        }

        // Head embedded in a solid block: no path to the atmosphere, treat as sealed.
        if (!probe.isPassable(startX, startY, startZ)) {
            return ScanResult.sealed(Collections.emptySet(), startX, startY, startZ, startX, startY, startZ);
        }

        Set<Long> visited = new HashSet<>();
        ArrayDeque<int[]> frontier = new ArrayDeque<>();
        visited.add(CellKey.pack(startX, startY, startZ));
        frontier.add(new int[] {startX, startY, startZ});

        int minX = startX, minY = startY, minZ = startZ;
        int maxX = startX, maxY = startY, maxZ = startZ;

        while (!frontier.isEmpty()) {
            int[] cell = frontier.poll();
            int x = cell[0], y = cell[1], z = cell[2];

            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;

            for (int[] d : NEIGHBORS) {
                int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                if (!probe.isPassable(nx, ny, nz)) {
                    continue;
                }
                if (visited.add(CellKey.pack(nx, ny, nz))) {
                    // Pocket grew past the budget without closing -> exposed.
                    if (visited.size() > budget) {
                        return ScanResult.exposed();
                    }
                    frontier.add(new int[] {nx, ny, nz});
                }
            }
        }

        return ScanResult.sealed(visited, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
