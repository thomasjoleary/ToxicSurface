// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.effect;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.core.enclosure.EnclosureScanner;
import io.github.thomasjoleary.toxicsurface.core.enclosure.LevelPassabilityProbe;
import io.github.thomasjoleary.toxicsurface.core.enclosure.ScanResult;
import io.github.thomasjoleary.toxicsurface.core.gas.AirBarModel;
import io.github.thomasjoleary.toxicsurface.core.gas.GasModel;
import io.github.thomasjoleary.toxicsurface.network.GasStatePayload;
import io.github.thomasjoleary.toxicsurface.world.ToxicityTicker;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Drives the toxic air bar for players in toxic gas (DESIGN.md §3). Server-side and
 * throttled to {@link #THROTTLE_TICKS} (DESIGN.md §8): each cycle it resolves whether
 * the player's head is in unsealed toxic gas, steps the air bar, and applies nausea
 * (while draining) or lethal toxic damage (once empty).
 *
 * <p>Air state is kept transiently per player for now; persistence + client sync for
 * the HUD bubble row land alongside the rendering work later in Phase 2.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID)
public final class GasEffectHandler {
    /** Effect checks run on this cadence, not every tick (DESIGN.md §8). */
    private static final int THROTTLE_TICKS = 10;

    private static final Map<UUID, Integer> AIR = new HashMap<>();

    private GasEffectHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.tickCount % THROTTLE_TICKS != 0) {
            return;
        }

        boolean exposed = ToxicityTicker.isAffected(level) && isExposedToGas(level, player);

        int drain = ToxicSurfaceConfig.AIR_BAR_DRAIN_TICKS.get();
        int refill = ToxicSurfaceConfig.AIR_BAR_REFILL_TICKS.get();
        int air = AIR.getOrDefault(player.getUUID(), AirBarModel.fullAir(drain));
        air = AirBarModel.step(air, exposed, drain, refill, THROTTLE_TICKS);
        AIR.put(player.getUUID(), air);

        if (exposed) {
            applyExposureEffects(player, air);
        }

        // Sync exposure to the client so it can render fog (DESIGN.md §3, §4).
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new GasStatePayload(exposed));
        }
    }

    private static boolean isExposedToGas(ServerLevel level, Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        int ceiling = ToxicityTicker.currentToxicY(level);
        boolean active = ceiling != ToxicityTicker.NOT_TOXIC;

        int x = Mth.floor(player.getX());
        int y = Mth.floor(player.getEyeY());
        int z = Mth.floor(player.getZ());

        // Only pay for the flood-fill when the head is actually under the ceiling.
        boolean sealed = false;
        if (active && y <= ceiling) {
            ScanResult result = EnclosureScanner.scan(
                    x, y, z, new LevelPassabilityProbe(level), ToxicSurfaceConfig.ENCLOSURE_FLOOD_FILL_BUDGET.get());
            sealed = result.isSealed();
        }
        boolean inCleanser = false; // TODO Phase 6: cleanser purge bubbles.
        return GasModel.isToxicGas(active, y, ceiling, sealed, inCleanser) && !isProtected(player);
    }

    private static void applyExposureEffects(Player player, int air) {
        if (air > 0) {
            // Bar still draining: nausea so the player is pushed to escape (DESIGN.md §3).
            if (ToxicSurfaceConfig.NAUSEA_WHILE_DRAINING.get()) {
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, THROTTLE_TICKS + 40, 0, false, false));
            }
        } else {
            // Bar empty: real, lethal toxic damage (not capped Poison).
            float damage = (float) (ToxicSurfaceConfig.TOXIC_DAMAGE_PER_SECOND.get() * THROTTLE_TICKS / 20.0);
            // TODO Phase 2 polish: custom datapack DamageType for "toxic"; vanilla source for now.
            player.hurt(player.damageSources().magic(), damage);
        }
    }

    /** Mask / hazmat-suit protection lands in Phases 4–5; unprotected for now. */
    private static boolean isProtected(Player player) {
        return false;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        AIR.remove(event.getEntity().getUUID());
    }
}
