// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.world;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.core.toxicity.ToxicityModel;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Server-authoritative toxicity clock (DESIGN.md §3, §4). Each tick, on affected
 * dimensions, it activates toxicity once the world reaches the configured time and
 * exposes the derived ceiling Y for the rest of the hazard systems.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID)
public final class ToxicityTicker {
    /** Returned by {@link #currentToxicY} when a dimension is not yet toxic. */
    public static final int NOT_TOXIC = Integer.MIN_VALUE;

    private ToxicityTicker() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isAffected(level)) {
            return;
        }
        ToxicityState state = ToxicityState.get(level);
        if (!state.hasStarted() && level.getGameTime() >= ToxicSurfaceConfig.TIME_TO_TOXIC_TICKS.get()) {
            state.startNow(level.getGameTime());
            ToxicSurface.LOGGER.info(
                    "The air has turned toxic in {}", level.dimension().location());
            // TODO Phase 3: fire "The Air Has Turned" advancement + pre-toxicity telegraph.
        }
    }

    public static boolean isAffected(ServerLevel level) {
        return ToxicSurfaceConfig.AFFECTED_DIMENSIONS
                .get()
                .contains(level.dimension().location().toString());
    }

    /** Current toxic ceiling Y for the level, or {@link #NOT_TOXIC} if not yet active. */
    public static int currentToxicY(ServerLevel level) {
        ToxicityState state = ToxicityState.get(level);
        if (!state.hasStarted()) {
            return NOT_TOXIC;
        }
        long elapsed = level.getGameTime() - state.startTick();
        return ToxicityModel.currentToxicY(
                elapsed,
                ToxicSurfaceConfig.TOXIC_START_Y.get(),
                ToxicSurfaceConfig.ESCALATION_SPEED_PER_DAY.get(),
                ToxicSurfaceConfig.ESCALATION_MAX_Y.get());
    }
}
