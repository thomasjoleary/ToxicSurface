// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.generator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Tests for the generator power tiers (DESIGN.md §7, §10). */
class GeneratorFuelTest {

    @Test
    void wasteBlockOutpowersResidue() {
        // The whole point of compacting residue into a waste block: more power.
        assertTrue(GeneratorFuel.WASTE_BLOCK.rpm() > GeneratorFuel.RESIDUE.rpm());
        assertTrue(GeneratorFuel.WASTE_BLOCK.capacity() > GeneratorFuel.RESIDUE.capacity());
        assertTrue(GeneratorFuel.WASTE_BLOCK.burnTicks() > GeneratorFuel.RESIDUE.burnTicks());
    }

    @Test
    void realFuelGeneratesButIdleDoesNot() {
        assertTrue(GeneratorFuel.RESIDUE.generates());
        assertTrue(GeneratorFuel.WASTE_BLOCK.generates());
        assertTrue(GeneratorFuel.SLUDGE.generates());
        assertFalse(GeneratorFuel.NONE.generates());
    }

    @Test
    void sludgeIsConsumedWhileRunning() {
        assertTrue(GeneratorFuel.SLUDGE_MB_PER_TICK > 0);
    }
}
