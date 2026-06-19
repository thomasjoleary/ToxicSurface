// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.enclosure;

import java.util.Collections;
import java.util.Set;

/**
 * Outcome of an enclosure flood-fill (DESIGN.md §2a).
 *
 * <ul>
 *   <li>{@code SEALED} — the air pocket containing the start cell is fully enclosed
 *       within the block budget. The pocket's cells and bounding box are retained so
 *       the result can be cached and invalidated when a block inside that box changes.
 *   <li>{@code EXPOSED} — the pocket reached the budget without closing (open to the
 *       toxic atmosphere, or too large to confirm sealed). No pocket is retained.
 * </ul>
 */
public final class ScanResult {
    public enum Status {
        SEALED,
        EXPOSED
    }

    private static final ScanResult EXPOSED = new ScanResult(Status.EXPOSED, Collections.emptySet(), 0, 0, 0, 0, 0, 0);

    private final Status status;
    private final Set<Long> cells;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private ScanResult(Status status, Set<Long> cells, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.status = status;
        this.cells = cells;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public static ScanResult exposed() {
        return EXPOSED;
    }

    public static ScanResult sealed(Set<Long> cells, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new ScanResult(Status.SEALED, Collections.unmodifiableSet(cells), minX, minY, minZ, maxX, maxY, maxZ);
    }

    public Status status() {
        return status;
    }

    public boolean isSealed() {
        return status == Status.SEALED;
    }

    /** Number of air cells in the sealed pocket (0 for an exposed result). */
    public int size() {
        return cells.size();
    }

    /** The packed cells of the sealed pocket; empty for an exposed result. */
    public Set<Long> cells() {
        return cells;
    }

    public boolean contains(int x, int y, int z) {
        return cells.contains(CellKey.pack(x, y, z));
    }

    /**
     * True if the given position lies within the pocket's bounding box grown by one
     * block — i.e. a change there could breach the seal (it is either inside the
     * pocket or one of its surrounding walls).
     */
    public boolean boundingBoxTouches(int x, int y, int z) {
        return x >= minX - 1 && x <= maxX + 1 && y >= minY - 1 && y <= maxY + 1 && z >= minZ - 1 && z <= maxZ + 1;
    }
}
