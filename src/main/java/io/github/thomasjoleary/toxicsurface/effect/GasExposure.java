// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.effect;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.core.enclosure.EnclosureScanner;
import io.github.thomasjoleary.toxicsurface.core.enclosure.LevelPassabilityProbe;
import io.github.thomasjoleary.toxicsurface.core.gas.GasModel;
import io.github.thomasjoleary.toxicsurface.world.ToxicityTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Shared server-side test for whether a point is in unsealed toxic gas (DESIGN.md
 * §2a, §3). Used by the mob-death and (future) other hazard handlers; the player air
 * bar keeps its own copy because it also skips creative/spectator players.
 */
public final class GasExposure {
    private GasExposure() {}

    public static boolean isInToxicGas(ServerLevel level, double x, double headY, double z) {
        int ceiling = ToxicityTicker.currentToxicY(level);
        if (ceiling == ToxicityTicker.NOT_TOXIC) {
            return false;
        }
        int bx = Mth.floor(x);
        int by = Mth.floor(headY);
        int bz = Mth.floor(z);
        if (by > ceiling) {
            return false;
        }
        boolean sealed = EnclosureScanner.scan(
                        bx,
                        by,
                        bz,
                        new LevelPassabilityProbe(level),
                        ToxicSurfaceConfig.ENCLOSURE_FLOOD_FILL_BUDGET.get())
                .isSealed();
        return GasModel.isToxicGas(true, by, ceiling, sealed, false);
    }
}
