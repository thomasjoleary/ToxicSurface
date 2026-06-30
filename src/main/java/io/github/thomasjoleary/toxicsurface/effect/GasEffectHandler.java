// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.effect;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig.MaskTickMode;
import io.github.thomasjoleary.toxicsurface.core.equipment.MaskFilter;
import io.github.thomasjoleary.toxicsurface.core.gas.AirBarModel;
import io.github.thomasjoleary.toxicsurface.core.gas.GasModel;
import io.github.thomasjoleary.toxicsurface.item.FaceMaskItem;
import io.github.thomasjoleary.toxicsurface.item.HazmatSuit;
import io.github.thomasjoleary.toxicsurface.network.FilterExpiryPayload;
import io.github.thomasjoleary.toxicsurface.network.GasStatePayload;
import io.github.thomasjoleary.toxicsurface.registry.ModDamageTypes;
import io.github.thomasjoleary.toxicsurface.registry.ModSounds;
import io.github.thomasjoleary.toxicsurface.world.CleanserBubbles;
import io.github.thomasjoleary.toxicsurface.world.SmogClouds;
import io.github.thomasjoleary.toxicsurface.world.ToxicityTicker;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * <p>Air state is kept transiently per player (cleared on logout) and synced to the client each
 * cycle as a 0..1 fraction, which {@code AirBarOverlay} renders as the HUD bubble row.
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

        boolean inGas = isInToxicGasAtHead(level, player);
        boolean isProtected = updateProtection(level, player, inGas);
        boolean exposed = inGas && !isProtected;

        int drain = ToxicSurfaceConfig.AIR_BAR_DRAIN_TICKS.get();
        int refill = ToxicSurfaceConfig.AIR_BAR_REFILL_TICKS.get();
        int air = AIR.getOrDefault(player.getUUID(), AirBarModel.fullAir(drain));
        air = AirBarModel.step(air, exposed, drain, refill, THROTTLE_TICKS);
        AIR.put(player.getUUID(), air);

        if (exposed) {
            applyExposureEffects(player, air);
        }

        // Sync exposure (fog) + air bar (HUD bubble row) + toxic-area (rain overlay) + the dimension's
        // toxic ceiling Y (rain colour below vs above the gas line) (DESIGN.md §3, §4).
        if (player instanceof ServerPlayer serverPlayer) {
            float airFraction = Mth.clamp((float) air / AirBarModel.fullAir(drain), 0f, 1f);
            int ceiling = ToxicityTicker.currentToxicY(level);
            PacketDistributor.sendToPlayer(serverPlayer, new GasStatePayload(exposed, airFraction, inGas, ceiling));
        }
    }

    /** Toxic gas at the player's head, ignoring protection (creative/spectator are never exposed). */
    private static boolean isInToxicGasAtHead(ServerLevel level, Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return false;
        }
        int x = Mth.floor(player.getX());
        int y = Mth.floor(player.getEyeY());
        int z = Mth.floor(player.getZ());

        // A running toxic generator's smog poisons the air independent of the world toxicity,
        // so it counts here even in an unaffected dimension or above the ceiling (DESIGN.md §7).
        boolean inSmog = SmogClouds.isInside(level, x, y, z);
        int ceiling = ToxicityTicker.currentToxicY(level);
        boolean active = ToxicityTicker.isAffected(level) && ceiling != ToxicityTicker.NOT_TOXIC;
        boolean ambient = active && y <= ceiling;

        // Head underwater (clean water or sludge) isn't breathing gas — sludge has its own hazard,
        // and vanilla drowning covers water (DESIGN.md §3). Skips the flood-fill in that case too.
        boolean submerged = !level.getFluidState(new BlockPos(x, y, z)).isEmpty();
        // Only pay for the flood-fill when there's actually something toxic to be sealed from;
        // the per-dimension cache reuses a pocket's result across players/ticks (DESIGN.md §2a, §8).
        boolean sealed = false;
        if (!submerged && (ambient || inSmog)) {
            sealed = EnclosureCacheHandler.isSealed(
                    level, x, y, z, ToxicSurfaceConfig.ENCLOSURE_FLOOD_FILL_BUDGET.get());
        }
        boolean inCleanser = CleanserBubbles.isInside(level, x, y, z);
        return GasModel.isToxicGas(active, y, ceiling, sealed, inCleanser, inSmog, submerged);
    }

    /**
     * Resolves gas protection (DESIGN.md §3). A hazmat chestpiece with filter charge
     * takes priority (bigger capacity, half-rate consumption); otherwise a worn face
     * mask is used. Returns whether the player is currently protected.
     */
    private static boolean updateProtection(ServerLevel level, Player player, boolean inGas) {
        // The suit only protects (and burns filters) with BOTH helmet and chest worn.
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (HazmatSuit.hasSuitCore(player) && HazmatSuit.usableFilterCount(chest) > 0) {
            return updateSuitAndIsProtected(level, player, chest, inGas);
        }
        return updateMaskAndIsProtected(level, player, inGas);
    }

    /** Burns the chest's filters at half the mask rate while protecting; warns on the last one. */
    private static boolean updateSuitAndIsProtected(ServerLevel level, Player player, ItemStack chest, boolean inGas) {
        int usableBefore = HazmatSuit.usableFilterCount(chest);
        if (usableBefore <= 0) {
            return false;
        }
        boolean tickNow = ToxicSurfaceConfig.MASK_TICK_MODE.get() == MaskTickMode.ALWAYS || inGas;
        if (!tickNow) {
            return true; // protected but not burning (e.g. IN_GAS_ONLY out of gas)
        }
        int active = HazmatSuit.activeTicks(chest);
        if (active <= 0) {
            active = HazmatSuit.nextFilterLifetime(chest); // start burning the next filter (carbon-aware)
        }
        int delta = Math.max(1, (int) Math.round(THROTTLE_TICKS * ToxicSurfaceConfig.SUIT_CONSUME_RATE_FACTOR.get()));
        active -= delta;

        int usableAfter = usableBefore;
        if (active <= 0) {
            HazmatSuit.burnOneFilter(chest); // turns the spent filter into a plain used one
            usableAfter = usableBefore - 1;
            active = usableAfter > 0 ? HazmatSuit.nextFilterLifetime(chest) : 0;
        }
        HazmatSuit.setActiveTicks(chest, active);

        if (inGas && usableAfter <= 0) {
            playFilterExpiryWarning(level, player);
        }
        return usableAfter > 0;
    }

    /**
     * Ticks down the worn mask's filter and reports whether it currently protects the
     * player (DESIGN.md §3). The filter drains only in gas ({@code IN_GAS_ONLY}) or
     * always ({@code ALWAYS}); a cough warns when it runs out mid-exposure.
     */
    private static boolean updateMaskAndIsProtected(ServerLevel level, Player player, boolean inGas) {
        ItemStack mask = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(mask.getItem() instanceof FaceMaskItem)) {
            return false;
        }
        int before = FaceMaskItem.remaining(mask);
        boolean tickNow = ToxicSurfaceConfig.MASK_TICK_MODE.get() == MaskTickMode.ALWAYS || inGas;
        if (MaskFilter.isActive(before) && tickNow) {
            int after = MaskFilter.consume(before, THROTTLE_TICKS);
            FaceMaskItem.setRemaining(mask, after);
            if (inGas && MaskFilter.justExpired(before, after)) {
                playFilterExpiryWarning(level, player);
            }
            return MaskFilter.isActive(after);
        }
        return MaskFilter.isActive(before);
    }

    private static void playFilterExpiryWarning(ServerLevel level, Player player) {
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                ModSounds.COUGH.get(),
                SoundSource.PLAYERS,
                0.7F,
                1.0F);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, FilterExpiryPayload.INSTANCE); // HUD flash cue
        }
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
            player.hurt(ModDamageTypes.toxic(player.level()), damage);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        AIR.remove(event.getEntity().getUUID());
    }
}
