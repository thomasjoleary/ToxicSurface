// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.world;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.core.toxicity.ToxicityModel;
import java.util.Comparator;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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

    private static final ResourceLocation AIR_HAS_TURNED =
            ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "the_air_has_turned");

    private ToxicityTicker() {}

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isAffected(level)) {
            return;
        }
        ToxicityState state = ToxicityState.get(level);
        if (state.hasStarted()) {
            return;
        }
        // Generator pollution counts toward the clock, so heavy waste-burning can bring the
        // world's first turn toxic forward (DESIGN.md §7 "worsens the apocalypse").
        long remaining = ToxicSurfaceConfig.TIME_TO_TOXIC_TICKS.get() - (level.getGameTime() + state.pollutionTicks());
        if (remaining <= 0) {
            state.startNow(level.getGameTime());
            ToxicSurface.LOGGER.info(
                    "The air has turned toxic in {}", level.dimension().location());
            awardActivationAdvancement(level);
        } else {
            maybeTelegraph(level, state, remaining);
        }
    }

    /** Fires a telegraph warning the first tick the countdown crosses each configured threshold. */
    private static void maybeTelegraph(ServerLevel level, ToxicityState state, long remaining) {
        if (!ToxicSurfaceConfig.TELEGRAPH_ENABLED.get()) {
            return;
        }
        List<Integer> thresholds = ToxicSurfaceConfig.TELEGRAPH_WARNING_TICKS.get().stream()
                .map(Integer::intValue)
                .sorted(Comparator.reverseOrder())
                .toList();
        int crossed = 0;
        for (int threshold : thresholds) {
            if (remaining <= threshold) {
                crossed++;
            }
        }
        if (crossed > state.telegraphStage()) {
            // Announce once with the true remaining time (collapses any multi-threshold jump).
            ToxicityTelegraph.warn(level, (int) Math.min(Integer.MAX_VALUE, remaining));
            state.setTelegraphStage(crossed);
        }
    }

    private static void awardActivationAdvancement(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            awardTo(player);
        }
    }

    /** Grants the activation advancement to a single player; a no-op if already earned. */
    private static void awardTo(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        AdvancementHolder advancement = player.getServer().getAdvancements().get(AIR_HAS_TURNED);
        if (advancement != null) {
            player.getAdvancements().award(advancement, "activated");
        }
    }

    /** Retroactively grants the advancement to a player who joins/enters an already-toxic dimension. */
    private static void grantIfToxic(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level
                && isAffected(level)
                && ToxicityState.get(level).hasStarted()) {
            awardTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            grantIfToxic(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            grantIfToxic(player);
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
        // Pollution accelerates the rising ceiling: it reads as extra elapsed time (DESIGN.md §7).
        long elapsed = level.getGameTime() - state.startTick() + state.pollutionTicks();
        return ToxicityModel.currentToxicY(
                elapsed,
                ToxicSurfaceConfig.TOXIC_START_Y.get(),
                ToxicSurfaceConfig.ESCALATION_SPEED_PER_DAY.get(),
                ToxicSurfaceConfig.ESCALATION_MAX_Y.get());
    }
}
