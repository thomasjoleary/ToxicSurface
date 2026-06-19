// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.gas;

/**
 * The virtual toxic-gas predicate (DESIGN.md §3 Toxic gas). Gas is never stored in
 * the world — a cell is toxic when all of the conditions below hold. Pure and
 * unit-testable; the Minecraft-side handler supplies the inputs.
 */
public final class GasModel {
    private GasModel() {}

    /**
     * @param toxicityActive    whether this dimension has crossed its start time
     * @param y                 the cell's Y
     * @param currentToxicY     current toxic ceiling (DESIGN.md §3 Escalation)
     * @param sealed            inside a sealed enclosure (DESIGN.md §2a)
     * @param inCleanserBubble  inside a cleanser's purge radius (DESIGN.md §3 Cleanser)
     */
    public static boolean isToxicGas(
            boolean toxicityActive, int y, int currentToxicY, boolean sealed, boolean inCleanserBubble) {
        return toxicityActive && y <= currentToxicY && !sealed && !inCleanserBubble;
    }
}
