// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.effect;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.registry.ModDamageTypes;
import io.github.thomasjoleary.toxicsurface.world.SmogClouds;
import io.github.thomasjoleary.toxicsurface.world.ToxicityTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * All passive mobs die in toxic gas (DESIGN.md §3) — animals, tamed, name-tagged and
 * villagers alike, no exceptions. Hostile mobs (anything {@link Enemy}) are immune for
 * now (mutant mobs are a future addition). Throttled and server-side.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID)
public final class PassiveMobDeathHandler {
    private static final int THROTTLE_TICKS = 20; // ~once per second

    private PassiveMobDeathHandler() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob instanceof Enemy) {
            return;
        }
        if (!(mob.level() instanceof ServerLevel level) || mob.tickCount % THROTTLE_TICKS != 0) {
            return;
        }
        // Affected dimensions can have ambient gas; any dimension can have generator smog.
        if (!ToxicityTicker.isAffected(level) && !SmogClouds.hasAny(level)) {
            return;
        }
        if (GasExposure.isInToxicGas(level, mob.getX(), mob.getEyeY(), mob.getZ())) {
            mob.hurt(ModDamageTypes.toxic(mob.level()), Float.MAX_VALUE);
        }
    }
}
