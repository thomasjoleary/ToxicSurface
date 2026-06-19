// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.gas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the virtual toxic-gas predicate (DESIGN.md §3, §10). */
class GasModelTest {

    @Test
    void toxic_whenActiveBelowCeilingUnsealedUncleansed() {
        assertTrue(GasModel.isToxicGas(true, 60, 63, false, false));
        assertTrue(GasModel.isToxicGas(true, 63, 63, false, false)); // at the ceiling
    }

    @Test
    void notToxic_whenInactive() {
        assertFalse(GasModel.isToxicGas(false, 60, 63, false, false));
    }

    @Test
    void notToxic_whenAboveCeiling() {
        assertFalse(GasModel.isToxicGas(true, 64, 63, false, false));
    }

    @Test
    void notToxic_whenSealed() {
        assertFalse(GasModel.isToxicGas(true, 60, 63, true, false));
    }

    @Test
    void notToxic_whenInCleanserBubble() {
        assertFalse(GasModel.isToxicGas(true, 60, 63, false, true));
    }
}
