// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceClientConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Renders the toxic-gas haze as real, depth-tested world-space geometry (DESIGN.md §3 gas
 * visibility): translucent cell boxes below the toxic ceiling, drawn with vanilla's own
 * {@link RenderType#debugQuads()} so the GPU's ordinary depth test hides them behind real
 * walls/roofs — no shader, no ray-march, no per-tick network payload, no flashing.
 *
 * <p>The load-bearing rule is how a cell's fog <em>floor</em> is chosen ({@link #scanCell}): every
 * column is sampled, columns poking above the ceiling are ignored, and the floor is the highest of
 * the remaining (open) columns. A cell is skipped only when <em>every</em> column is covered — a
 * fully roofed pocket (a sealed room's interior, or terrain wholly above the ceiling). The floor thus
 * sits at an open surface at/below the ceiling, so fog stays above it; a fully-roofed cell is skipped,
 * so a sealed room's interior stays clear; and a lone pillar/tree/wall no longer clears the fog around
 * it. Two earlier rules failed here: sampling only the centre column sliced fog through hillside
 * rooms, and taking the max of <em>all</em> columns let one tall block above the ceiling blank its cell.
 *
 * <p>The trade-offs of heightmap-based exposure: fog conservatively skips air under overhangs, and a
 * covered column sharing a cell with an open one (a roof overhanging open ground with no wall between)
 * can get a thin fog leak — normal sealed rooms don't, since their solid walls occlude edge fog and
 * their interior cells are fully covered. Fog sits on top of tree canopies
 * (leaves count as cover in {@code MOTION_BLOCKING} — the type used deliberately, since it is one of
 * the two heightmaps vanilla actually syncs to clients). It also does not know about Cleanser
 * bubbles (not bounded by real blocks); carving those out needs range sync and is a follow-up.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID, value = Dist.CLIENT)
public final class ToxicGasFieldRenderer {
    /** Horizontal footprint of one grid cell. Cells are grid-aligned, so they never straddle chunks. */
    private static final int CELL_SIZE = 4;

    /** Grid extends this many cells each direction from the camera (±64 blocks at CELL_SIZE=4). */
    private static final int GRID_RADIUS_CELLS = 16;

    /** Fog band thickness: geometry extends at most this far down from the ceiling over open ground. */
    private static final int WALL_HEIGHT = 40;

    /** Rebuild at least this often so block changes (a new hole in a roof) eventually show up. */
    private static final long REBUILD_INTERVAL_TICKS = 20;

    /** Rebuild early if the camera has moved this far from where the grid was last centred. */
    private static final double REBUILD_MOVE_THRESHOLD_SQ = (CELL_SIZE * 3.0) * (CELL_SIZE * 3.0);

    /** Sentinel in the per-cell floor grid: this cell gets no fog geometry at all. */
    private static final int NOT_EXPOSED = Integer.MIN_VALUE;

    /**
     * Inset applied to wall planes and caps so they never sit exactly on a block-grid plane:
     * player-built walls land on those planes constantly, and a coplanar translucent quad would
     * z-fight with the block face it rests against.
     */
    private static final float EPS = 0.01f;

    private static final float FOG_R = 0.32f;
    private static final float FOG_G = 0.45f;
    private static final float FOG_B = 0.16f;

    /** Top caps are usually viewed from above at range, so they can read near-opaque. */
    private static final float TOP_ALPHA = 0.9f;

    /**
     * Per-wall alpha. Interior boundaries between exposed cells each carry one wall, so looking
     * across open ground crosses one every {@link #CELL_SIZE} blocks — a low per-wall alpha
     * accumulates into a haze that thickens with distance instead of reading as discrete panes.
     */
    private static final float WALL_ALPHA = 0.14f;

    private static List<Quad> cachedQuads = List.of();
    private static ClientLevel lastLevel;
    private static long lastRebuildTick = Long.MIN_VALUE;
    private static double lastBuildX = Double.NaN;
    private static double lastBuildZ = Double.NaN;
    private static int lastCeilingY = Integer.MIN_VALUE;

    private ToxicGasFieldRenderer() {}

    private record Quad(
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float alpha) {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || ShaderState.shadersActive()) {
            return;
        }
        int ceilingY = ClientGasState.toxicCeilingY();
        if (ceilingY == Integer.MIN_VALUE) {
            cachedQuads = List.of();
            return;
        }
        float intensity = (float) (double) ToxicSurfaceClientConfig.FOG_INTENSITY.get();
        if (intensity <= 0f) {
            return;
        }

        // Centre on the camera (not the player) so spectator/freecam views still get coverage.
        Vec3 cam = event.getCamera().getPosition();
        maybeRebuild(level, cam, ceilingY);
        if (cachedQuads.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cam.x, -cam.y, -cam.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        for (Quad q : cachedQuads) {
            float a = q.alpha() * intensity;
            addVertex(consumer, pose, q.x0(), q.y0(), q.z0(), a);
            addVertex(consumer, pose, q.x1(), q.y1(), q.z1(), a);
            addVertex(consumer, pose, q.x2(), q.y2(), q.z2(), a);
            addVertex(consumer, pose, q.x3(), q.y3(), q.z3(), a);
        }
        bufferSource.endBatch(RenderType.debugQuads());

        poseStack.popPose();
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float a) {
        consumer.addVertex(pose, x, y, z).setColor(FOG_R, FOG_G, FOG_B, a);
    }

    private static void maybeRebuild(ClientLevel level, Vec3 cam, int ceilingY) {
        long now = level.getGameTime();
        // A level change (dimension switch, rejoin) invalidates everything immediately — the cached
        // quads describe the previous world's terrain.
        boolean levelChanged = level != lastLevel;
        double dx = cam.x - lastBuildX;
        double dz = cam.z - lastBuildZ;
        boolean moved = Double.isNaN(lastBuildX) || dx * dx + dz * dz > REBUILD_MOVE_THRESHOLD_SQ;
        boolean stale = now - lastRebuildTick >= REBUILD_INTERVAL_TICKS;
        if (!levelChanged && !moved && !stale && ceilingY == lastCeilingY) {
            return;
        }
        lastLevel = level;
        lastRebuildTick = now;
        lastBuildX = cam.x;
        lastBuildZ = cam.z;
        lastCeilingY = ceilingY;
        cachedQuads = buildGrid(level, cam, ceilingY);
    }

    /**
     * Builds the fog geometry for the grid around the camera. Per exposed cell: a near-opaque top cap
     * at the ceiling, a bottom cap only where the band's bottom hangs in open air (a cap resting on
     * flat terrain would z-fight the ground and paint it green underfoot), and translucent walls.
     * Wall ownership: each cell unconditionally draws its west/north wall — dropped to the lower of
     * the two adjoining floors, so the vertical step between differing floors is covered too — while
     * east/south are only drawn at a true silhouette edge (a neighbour with no fog at all). Every
     * shared plane thus gets exactly one wall.
     */
    private static List<Quad> buildGrid(ClientLevel level, Vec3 cam, int ceilingY) {
        int diameter = GRID_RADIUS_CELLS * 2 + 1;
        int originCellX = Math.floorDiv(Mth.floor(cam.x), CELL_SIZE) - GRID_RADIUS_CELLS;
        int originCellZ = Math.floorDiv(Mth.floor(cam.z), CELL_SIZE) - GRID_RADIUS_CELLS;
        int bandBottom = ceilingY - WALL_HEIGHT;

        int[][] floors = new int[diameter][diameter];
        for (int cz = 0; cz < diameter; cz++) {
            for (int cx = 0; cx < diameter; cx++) {
                floors[cx][cz] = scanCell(
                        level, (originCellX + cx) * CELL_SIZE, (originCellZ + cz) * CELL_SIZE, ceilingY, bandBottom);
            }
        }

        List<Quad> quads = new ArrayList<>();
        float capY = ceilingY - EPS;
        for (int cz = 0; cz < diameter; cz++) {
            for (int cx = 0; cx < diameter; cx++) {
                int floor = floors[cx][cz];
                if (floor == NOT_EXPOSED) {
                    continue;
                }
                float x0 = (originCellX + cx) * CELL_SIZE;
                float z0 = (originCellZ + cz) * CELL_SIZE;
                float x1 = x0 + CELL_SIZE;
                float z1 = z0 + CELL_SIZE;

                if (floor < capY) {
                    quads.add(new Quad(x0, capY, z0, x1, capY, z0, x1, capY, z1, x0, capY, z1, TOP_ALPHA));
                    if (floor <= bandBottom) {
                        float y = floor + EPS;
                        quads.add(new Quad(x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, TOP_ALPHA * 0.5f));
                    }
                }

                float westLo = Math.min(floor, floorOr(floors, cx - 1, cz, floor));
                if (westLo < capY) {
                    float x = x0 + EPS;
                    quads.add(new Quad(x, westLo, z0, x, westLo, z1, x, capY, z1, x, capY, z0, WALL_ALPHA));
                }
                float northLo = Math.min(floor, floorOr(floors, cx, cz - 1, floor));
                if (northLo < capY) {
                    float z = z0 + EPS;
                    quads.add(new Quad(x0, northLo, z, x1, northLo, z, x1, capY, z, x0, capY, z, WALL_ALPHA));
                }
                if (!hasFog(floors, cx + 1, cz) && floor < capY) {
                    float x = x1 - EPS;
                    quads.add(new Quad(x, floor, z0, x, floor, z1, x, capY, z1, x, capY, z0, WALL_ALPHA));
                }
                if (!hasFog(floors, cx, cz + 1) && floor < capY) {
                    float z = z1 - EPS;
                    quads.add(new Quad(x0, floor, z, x1, floor, z, x1, capY, z, x0, capY, z, WALL_ALPHA));
                }
            }
        }
        return quads;
    }

    /**
     * Classifies one cell: {@link #NOT_EXPOSED} if unloaded or anything in it reaches above the
     * ceiling, otherwise the fog floor: the highest <em>open</em> column (one at or below the
     * ceiling), clamped up to the band bottom. Columns poking above the ceiling are ignored so a lone
     * tall block cannot blank the cell; the cell is skipped only when every column is covered.
     * {@code MOTION_BLOCKING} rather than {@code MOTION_BLOCKING_NO_LEAVES}: only the former is
     * synced to clients — the NO_LEAVES map would be lazily recomputed per chunk on first touch.
     */
    private static int scanCell(ClientLevel level, int baseX, int baseZ, int ceilingY, int bandBottom) {
        // CELL_SIZE divides 16 and cells are grid-aligned, so the whole cell is in one chunk.
        if (!level.hasChunk(baseX >> 4, baseZ >> 4)) {
            return NOT_EXPOSED;
        }
        // Floor at the highest OPEN column (surface at or below the ceiling). Columns that poke above
        // the ceiling -- a pillar, a tree, a wall, raised terrain -- are skipped, not counted toward
        // the floor, so a single tall block no longer blanks out its whole cell (the old max-of-all
        // rule did exactly that: the big fog-free holes seen around pillars). A cell is only skipped
        // when EVERY column is covered -- a fully roofed pocket (a sealed room's interior, or terrain
        // wholly above the ceiling) -- which is what should genuinely suppress fog.
        int maxOpen = Integer.MIN_VALUE;
        for (int dz = 0; dz < CELL_SIZE; dz++) {
            for (int dx = 0; dx < CELL_SIZE; dx++) {
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, baseX + dx, baseZ + dz);
                if (top <= ceilingY) {
                    maxOpen = Math.max(maxOpen, top);
                }
            }
        }
        return maxOpen == Integer.MIN_VALUE ? NOT_EXPOSED : Math.max(maxOpen, bandBottom);
    }

    /** The neighbour's floor, or {@code fallback} when it is outside the grid or has no fog. */
    private static int floorOr(int[][] floors, int cx, int cz, int fallback) {
        if (cx < 0 || cz < 0 || cx >= floors.length || cz >= floors.length) {
            return fallback;
        }
        int floor = floors[cx][cz];
        return floor == NOT_EXPOSED ? fallback : floor;
    }

    private static boolean hasFog(int[][] floors, int cx, int cz) {
        return cx >= 0 && cz >= 0 && cx < floors.length && cz < floors.length && floors[cx][cz] != NOT_EXPOSED;
    }
}
