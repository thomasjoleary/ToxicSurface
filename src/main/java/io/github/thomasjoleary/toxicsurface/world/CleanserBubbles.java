// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.world;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Server-side registry of active Cleanser purge bubbles (DESIGN.md §3 Cleanser). A running
 * Cleanser keeps breathable air in a sphere; the gas predicate treats any cell inside a
 * live bubble as clean. Bubbles are refreshed every tick by the block entity while it
 * runs, so an entry that stops being refreshed (fuel out, chunk unloaded, block broken)
 * goes stale and is pruned on the next query.
 */
public final class CleanserBubbles {
    /** A bubble is ignored/pruned if not refreshed within this many ticks. */
    private static final long STALE_TICKS = 40;

    private static final Map<ResourceKey<Level>, Map<Long, Bubble>> BUBBLES = new HashMap<>();

    private CleanserBubbles() {}

    private record Bubble(int range, long lastTick) {}

    /** Registers or refreshes a bubble at {@code pos} with the given radius. */
    public static void update(ServerLevel level, BlockPos pos, int range) {
        BUBBLES.computeIfAbsent(level.dimension(), k -> new HashMap<>())
                .put(pos.asLong(), new Bubble(range, level.getGameTime()));
    }

    /** Removes a bubble (cleanser stopped or broken). */
    public static void remove(ServerLevel level, BlockPos pos) {
        Map<Long, Bubble> map = BUBBLES.get(level.dimension());
        if (map != null) {
            map.remove(pos.asLong());
            if (map.isEmpty()) {
                BUBBLES.remove(level.dimension());
            }
        }
    }

    /** True if the point is inside any live bubble; prunes stale entries as it goes. */
    public static boolean isInside(ServerLevel level, int x, int y, int z) {
        Map<Long, Bubble> map = BUBBLES.get(level.dimension());
        if (map == null || map.isEmpty()) {
            return false;
        }
        long now = level.getGameTime();
        boolean inside = false;
        Iterator<Map.Entry<Long, Bubble>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Bubble> entry = it.next();
            Bubble bubble = entry.getValue();
            if (now - bubble.lastTick() > STALE_TICKS) {
                it.remove();
                continue;
            }
            BlockPos pos = BlockPos.of(entry.getKey());
            long dx = x - pos.getX();
            long dy = y - pos.getY();
            long dz = z - pos.getZ();
            if (dx * dx + dy * dy + dz * dz <= (long) bubble.range() * bubble.range()) {
                inside = true;
            }
        }
        return inside;
    }
}
