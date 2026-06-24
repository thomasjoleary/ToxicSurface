// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.effect;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.core.enclosure.EnclosureScanner;
import io.github.thomasjoleary.toxicsurface.core.enclosure.LevelPassabilityProbe;
import io.github.thomasjoleary.toxicsurface.core.gas.GasModel;
import io.github.thomasjoleary.toxicsurface.world.SmogClouds;
import io.github.thomasjoleary.toxicsurface.world.ToxicityTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Shared server-side test for whether a point is in unsealed toxic gas (DESIGN.md
 * §2a, §3, §7). Used by the mob-death and (future) other hazard handlers; the player air
 * bar keeps its own copy because it also skips creative/spectator players. Toxic generator
 * smog ({@link SmogClouds}) counts as gas here too, so it kills nearby passive mobs even
 * where the world hasn't otherwise turned toxic.
 */
public final class GasExposure {
    private GasExposure() {}

    public static boolean isInToxicGas(ServerLevel level, double x, double headY, double z) {
        int bx = Mth.floor(x);
        int by = Mth.floor(headY);
        int bz = Mth.floor(z);

        boolean inSmog = SmogClouds.isInside(level, bx, by, bz);
        int ceiling = ToxicityTicker.currentToxicY(level);
        boolean active = ceiling != ToxicityTicker.NOT_TOXIC;
        boolean ambient = active && by <= ceiling;
        if (!ambient && !inSmog) {
            return false; // nothing toxic at this cell — skip the flood-fill entirely
        }

        // Airborne gas can't fill a liquid cell: a swimmer in clean water is safe, and a sludge cell
        // is the sludge hazard's job. Skips the flood-fill too (DESIGN.md §3 Toxic gas vs sludge).
        boolean submerged = !level.getFluidState(new BlockPos(bx, by, bz)).isEmpty();
        boolean sealed = !submerged
                && EnclosureScanner.scan(
                                bx,
                                by,
                                bz,
                                new LevelPassabilityProbe(level),
                                ToxicSurfaceConfig.ENCLOSURE_FLOOD_FILL_BUDGET.get())
                        .isSealed();
        return GasModel.isToxicGas(active, by, ceiling, sealed, false, inSmog, submerged);
    }
}
