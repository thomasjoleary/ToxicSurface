// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.jade;

import net.minecraft.nbt.CompoundTag;

/**
 * Implemented by a machine block entity that exposes live state to the Jade in-world tooltip
 * (DESIGN.md §5 Phase 8). The block entity writes a few primitive keys into {@code tag} server-side;
 * Jade syncs them to the client where {@link MachineReadoutProvider} renders the lines.
 *
 * <p>Deliberately <b>free of any Jade API types</b> so the Create-gated generator block entities can
 * implement it without coupling {@code compat.create} to Jade, and so loading this interface never
 * pulls in the optional Jade classes when Jade is absent.
 */
public interface JadeReadout {
    /** Writes this machine's current state into Jade's per-block server-data tag. */
    void appendJadeData(CompoundTag tag);
}
