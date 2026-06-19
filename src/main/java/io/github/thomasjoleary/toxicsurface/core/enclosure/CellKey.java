// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.enclosure;

/**
 * Packs a block coordinate into a single {@code long} for use as a set/map key.
 *
 * <p>Layout mirrors vanilla {@code BlockPos} bit budgets (26 bits X, 26 bits Z,
 * 12 bits Y), which uniquely covers the entire world volume — collisions are not
 * possible for in-range coordinates. Only used as an identity key; values are
 * never unpacked.
 */
public final class CellKey {
    private static final long X_MASK = 0x3FFFFFF; // 26 bits
    private static final long Z_MASK = 0x3FFFFFF; // 26 bits
    private static final long Y_MASK = 0xFFF; // 12 bits

    private CellKey() {}

    public static long pack(int x, int y, int z) {
        return ((x & X_MASK) << 38) | ((z & Z_MASK) << 12) | (y & Y_MASK);
    }
}
