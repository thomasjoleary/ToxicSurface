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
     * @param inSmog            inside a running toxic generator's smog cloud (DESIGN.md §7)
     * @param submerged         the cell is filled by a liquid (water or sludge); airborne gas can't
     *     occupy it, so a swimmer/aquatic plant in clean water is safe and a sludge cell is the
     *     sludge hazard's domain, not the gas's
     */
    public static boolean isToxicGas(
            boolean toxicityActive,
            int y,
            int currentToxicY,
            boolean sealed,
            boolean inCleanserBubble,
            boolean inSmog,
            boolean submerged) {
        // Sealing yourself off, a cleanser, or simply being underwater all keep gas out of the cell.
        if (sealed || inCleanserBubble || submerged) {
            return false;
        }
        // Either the ambient apocalypse reaches this cell, or a generator is venting smog into it.
        boolean ambientToxic = toxicityActive && y <= currentToxicY;
        return ambientToxic || inSmog;
    }
}
