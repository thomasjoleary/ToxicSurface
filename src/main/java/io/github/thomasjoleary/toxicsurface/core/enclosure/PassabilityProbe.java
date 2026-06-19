// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.enclosure;

/**
 * Tells the enclosure flood-fill whether toxic gas can move through a given block
 * position (i.e. the cell is open air, not a sealing solid).
 *
 * <p>Deliberately Minecraft-free so the flood-fill algorithm (DESIGN.md §2a) can be
 * unit-tested with plain in-memory grids. The Minecraft-backed implementation is
 * {@code LevelPassabilityProbe}.
 */
@FunctionalInterface
public interface PassabilityProbe {
    boolean isPassable(int x, int y, int z);
}
