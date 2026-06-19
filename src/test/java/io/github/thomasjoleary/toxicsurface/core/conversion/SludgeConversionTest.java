// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thomasjoleary.toxicsurface.core.conversion.SludgeConversion.Band;
import org.junit.jupiter.api.Test;

/** Tests for the surface-anchored sludge conversion band (DESIGN.md §2b, §10). */
class SludgeConversionTest {

    @Test
    void fullBand_fromUnconvertedSurface() {
        Band band = SludgeConversion.bandToConvert(62, 63, 8, 0);
        assertTrue(band.present());
        assertEquals(55, band.low());
        assertEquals(62, band.high());
        assertEquals(8, band.count());
    }

    @Test
    void incrementalBand_whenAlreadyPartlyApplied() {
        // Deepened from 4 to 8: only the next 4 blocks below the prior band convert.
        Band band = SludgeConversion.bandToConvert(62, 63, 8, 4);
        assertTrue(band.present());
        assertEquals(55, band.low());
        assertEquals(58, band.high());
        assertEquals(4, band.count());
    }

    @Test
    void noBand_whenDepthNotDeepened() {
        assertFalse(SludgeConversion.bandToConvert(62, 63, 8, 8).present());
    }

    @Test
    void noBand_whenSurfaceAboveCeiling() {
        assertFalse(SludgeConversion.bandToConvert(70, 63, 8, 0).present());
    }

    @Test
    void none_hasZeroCount() {
        assertEquals(0, Band.NONE.count());
    }
}
