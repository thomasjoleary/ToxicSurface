// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.core.enclosure.EnclosureScanner;
import io.github.thomasjoleary.toxicsurface.core.enclosure.ScanResult;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Create-only implementation of {@link io.github.thomasjoleary.toxicsurface.effect.ContraptionSeal}:
 * seals a player inside a moving contraption by running the same enclosure flood-fill against the
 * contraption's local block grid (DESIGN.md §9). Installed into the soft hook from
 * {@link CreateContent#register}; never classloaded without Create.
 *
 * <p>A contraption entity's bounding box is its whole structure (see {@code ControlledContraptionEntity}),
 * so the entity query already returns only contraptions the player is physically inside — we scan each
 * and let the flood-fill decide; a contraption the player isn't enclosed in simply won't seal.
 */
public final class ContraptionSealing {
    // Temporary: logs the local mapping + scan result once per cycle while diagnosing in-world.
    private static final boolean DEBUG = true;

    private ContraptionSealing() {}

    public static boolean isSealedInContraption(ServerLevel level, Player player, int budget) {
        Vec3 eye = player.getEyePosition();
        List<AbstractContraptionEntity> contraptions = level.getEntitiesOfClass(
                AbstractContraptionEntity.class, player.getBoundingBox().inflate(0.5));
        for (AbstractContraptionEntity entity : contraptions) {
            Contraption contraption = entity.getContraption();
            if (contraption == null || contraption.getBlocks().isEmpty()) {
                continue;
            }
            // World eye position → the contraption's local block grid (the getBlocks() key space).
            Vec3 local = entity.toLocalVector(eye, 1.0f);
            int lx = Mth.floor(local.x);
            int ly = Mth.floor(local.y);
            int lz = Mth.floor(local.z);
            ScanResult result = EnclosureScanner.scan(lx, ly, lz, new ContraptionPassabilityProbe(contraption), budget);
            if (DEBUG) {
                ToxicSurface.LOGGER.info(
                        "ContraptionSeal: local=({},{},{}) blocks={} startCellHasBlock={} cellBelowHasBlock={} scan={} pocket={}",
                        lx,
                        ly,
                        lz,
                        contraption.getBlocks().size(),
                        contraption.getBlocks().containsKey(new BlockPos(lx, ly, lz)),
                        contraption.getBlocks().containsKey(new BlockPos(lx, ly - 1, lz)),
                        result.isSealed() ? "SEALED" : "EXPOSED",
                        result.size());
            }
            if (result.isSealed()) {
                return true;
            }
        }
        return false;
    }
}
