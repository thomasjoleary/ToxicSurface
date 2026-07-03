// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.world;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Server-side registry of active toxic smog clouds (DESIGN.md §7 Toxic generators). This is
 * the exact inverse of {@link CleanserBubbles}: a running toxic generator vents poison, so the
 * gas predicate treats any cell inside a live smog sphere as toxic — <em>even in a dimension or
 * area the world toxicity hasn't reached yet</em>. Burning waste for power is never free.
 *
 * <p>Clouds are refreshed every tick by the running generator; an entry that stops being
 * refreshed (fuel out, chunk unloaded, block broken) goes stale and is pruned on the next query.
 * A sealed enclosure or an overlapping cleanser bubble still wins (you can wall the smog out, or
 * clean it back up), exactly as they do for the ambient gas.
 */
public final class SmogClouds {
    /** A cloud is ignored/pruned if not refreshed within this many ticks. */
    private static final long STALE_TICKS = 40;

    private static final Map<ResourceKey<Level>, Map<Long, Cloud>> CLOUDS = new HashMap<>();

    private SmogClouds() {}

    private record Cloud(int range, long lastTick) {}

    /** Registers or refreshes a smog cloud at {@code pos} with the given radius. */
    public static void update(ServerLevel level, BlockPos pos, int range) {
        CLOUDS.computeIfAbsent(level.dimension(), k -> new HashMap<>())
                .put(pos.asLong(), new Cloud(range, level.getGameTime()));
    }

    /** Removes a cloud (generator stopped or broken). */
    public static void remove(ServerLevel level, BlockPos pos) {
        Map<Long, Cloud> map = CLOUDS.get(level.dimension());
        if (map != null) {
            map.remove(pos.asLong());
            if (map.isEmpty()) {
                CLOUDS.remove(level.dimension());
            }
        }
    }

    /** True if the dimension currently has any smog cloud (cheap gate before the per-cell test). */
    public static boolean hasAny(ServerLevel level) {
        Map<Long, Cloud> map = CLOUDS.get(level.dimension());
        return map != null && !map.isEmpty();
    }

    /**
     * Appends {@code {centerX, centerY, centerZ, radius}} for each live smog cloud whose sphere reaches
     * within {@code reach} blocks (horizontally) of {@code (cx, cz)} into {@code out}, up to {@code max}
     * entries — used to sync nearby clouds to the client so the fog shader adds haze inside them.
     * Prunes stale entries as it goes.
     */
    public static void collectNear(ServerLevel level, double cx, double cz, double reach, List<float[]> out, int max) {
        Map<Long, Cloud> map = CLOUDS.get(level.dimension());
        if (map == null || map.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<Long, Cloud>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Cloud> entry = it.next();
            Cloud cloud = entry.getValue();
            if (now - cloud.lastTick() > STALE_TICKS) {
                it.remove();
                continue;
            }
            if (out.size() >= max) {
                continue;
            }
            BlockPos pos = BlockPos.of(entry.getKey());
            double dx = (pos.getX() + 0.5) - cx;
            double dz = (pos.getZ() + 0.5) - cz;
            if (Math.sqrt(dx * dx + dz * dz) - cloud.range() <= reach) {
                out.add(new float[] {pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, cloud.range()});
            }
        }
    }

    /** True if the point is inside any live smog cloud; prunes stale entries as it goes. */
    public static boolean isInside(ServerLevel level, int x, int y, int z) {
        Map<Long, Cloud> map = CLOUDS.get(level.dimension());
        if (map == null || map.isEmpty()) {
            return false;
        }
        long now = level.getGameTime();
        boolean inside = false;
        Iterator<Map.Entry<Long, Cloud>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Cloud> entry = it.next();
            Cloud cloud = entry.getValue();
            if (now - cloud.lastTick() > STALE_TICKS) {
                it.remove();
                continue;
            }
            BlockPos pos = BlockPos.of(entry.getKey());
            long dx = x - pos.getX();
            long dy = y - pos.getY();
            long dz = z - pos.getZ();
            if (dx * dx + dy * dy + dz * dz <= (long) cloud.range() * cloud.range()) {
                inside = true;
            }
        }
        return inside;
    }
}
