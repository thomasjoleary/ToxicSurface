// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.gas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the virtual toxic-gas predicate (DESIGN.md §3, §7, §10). */
class GasModelTest {

    @Test
    void toxic_whenActiveBelowCeilingUnsealedUncleansed() {
        assertTrue(GasModel.isToxicGas(true, 60, 63, false, false, false, false));
        assertTrue(GasModel.isToxicGas(true, 63, 63, false, false, false, false)); // at the ceiling
    }

    @Test
    void notToxic_whenInactive() {
        assertFalse(GasModel.isToxicGas(false, 60, 63, false, false, false, false));
    }

    @Test
    void notToxic_whenAboveCeiling() {
        assertFalse(GasModel.isToxicGas(true, 64, 63, false, false, false, false));
    }

    @Test
    void notToxic_whenSealed() {
        assertFalse(GasModel.isToxicGas(true, 60, 63, true, false, false, false));
    }

    @Test
    void notToxic_whenInCleanserBubble() {
        assertFalse(GasModel.isToxicGas(true, 60, 63, false, true, false, false));
    }

    @Test
    void notToxic_whenSubmerged() {
        // Airborne gas can't fill a liquid cell — a swimmer/plant in clean water (or in sludge,
        // which is the sludge hazard's domain) is not in toxic gas, even below the ceiling or in smog.
        assertFalse(GasModel.isToxicGas(true, 60, 63, false, false, false, true));
        assertFalse(GasModel.isToxicGas(false, 60, 63, false, false, true, true));
    }

    @Test
    void toxic_inSmog_evenWhenWorldNotYetToxic() {
        // Generator smog poisons the air with no ambient toxicity at all (any dimension/altitude).
        assertTrue(GasModel.isToxicGas(false, 200, 63, false, false, true, false));
        assertTrue(GasModel.isToxicGas(true, 64, 63, false, false, true, false)); // above the ceiling
    }

    @Test
    void notToxic_inSmog_whenSealedOrCleansed() {
        // Walling the smog out, or a cleanser scrubbing it, still protects you.
        assertFalse(GasModel.isToxicGas(false, 60, 63, true, false, true, false));
        assertFalse(GasModel.isToxicGas(false, 60, 63, false, true, true, false));
    }
}
