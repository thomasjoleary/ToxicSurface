// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.world;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.registry.ModTags;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Surface foliage withers in toxic gas (DESIGN.md §3). A throttled, sky-exposed
 * decay: each tick it samples columns near players and breaks sky-lit foliage at or
 * below the ceiling. Toxic rain multiplies the rate. Using sky exposure (rather than
 * a per-block flood-fill) keeps it cheap and naturally spares covered/sealed plants.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID)
public final class FoliageDecayHandler {
    private static final int SAMPLE_RADIUS = 48;

    private FoliageDecayHandler() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ToxicityTicker.isAffected(level)) {
            return;
        }
        int ceiling = ToxicityTicker.currentToxicY(level);
        if (ceiling == ToxicityTicker.NOT_TOXIC) {
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return;
        }

        int budget = ToxicSurfaceConfig.FOLIAGE_DECAY_BLOCKS_PER_TICK.get();
        if (ToxicSurfaceConfig.TOXIC_RAIN_ENABLED.get() && level.isRaining()) {
            budget = (int) Math.round(budget * ToxicSurfaceConfig.TOXIC_RAIN_DECAY_MULTIPLIER.get());
        }

        RandomSource rng = level.getRandom();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int span = 2 * SAMPLE_RADIUS + 1;
        for (int i = 0; i < budget; i++) {
            ServerPlayer player = players.get(rng.nextInt(players.size()));
            int x = player.getBlockX() + rng.nextInt(span) - SAMPLE_RADIUS;
            int z = player.getBlockZ() + rng.nextInt(span) - SAMPLE_RADIUS;
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (y > ceiling) {
                continue;
            }
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.is(ModTags.Blocks.FOLIAGE) && level.canSeeSky(pos) && !inCleanWater(level, pos, state)) {
                level.destroyBlock(pos, false); // withers, no drops
            }
        }
    }

    /**
     * Aquatic plants in clean water are spared — the gas is airborne, so only air-exposed (or
     * sludge-bound) foliage withers. A plant counts as in clean water if it is itself water-filled
     * (waterlogged/aquatic) or sits directly on a water block (e.g. a lily pad). Sludge is not water,
     * so foliage in toxic sludge still dies.
     */
    private static boolean inCleanWater(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getFluidState().is(FluidTags.WATER)
                || level.getFluidState(pos.below()).is(FluidTags.WATER);
    }
}
