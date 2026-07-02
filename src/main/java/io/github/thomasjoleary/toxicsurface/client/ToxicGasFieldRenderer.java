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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Renders the toxic-gas haze as real, depth-tested world-space geometry (DESIGN.md §3 gas
 * visibility) instead of a screen-space post-process. A prior depth-reconstruction shader approach
 * worked in principle but needed a server-throttled, single-ray "how far until real gas" heuristic
 * to keep sealed rooms clear — and that heuristic snapped/flashed as the ray's sampled result
 * changed, since it only refreshed twice a second and covered one screen-wide value for the whole
 * view. This is simpler and doesn't flash: translucent boxes are placed in world space over columns
 * the mod judges "exposed" (nothing solid reaches the toxic ceiling there), and the GPU's ordinary
 * depth test — the same one that already correctly hides water behind a wall — does the rest. A
 * sealed room's own roof/walls occlude the fog automatically and instantly; there's no server round
 * trip or per-frame ray march involved at all.
 *
 * <p>The exposure classification (which columns get fog geometry) is a coarse, periodically-rebuilt
 * grid using vanilla's already-maintained heightmap (whether anything solid reaches the ceiling's
 * height in that column) — cheap, and it naturally treats natural terrain (being inside a mountain)
 * the same as a player-built roof. It does <em>not</em> know about Cleanser bubbles (those aren't
 * bounded by real blocks, so depth-test occlusion can't help there); standing in one may still show
 * nearby fog geometry until it's carved out in a follow-up.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID, value = Dist.CLIENT)
public final class ToxicGasFieldRenderer {
    /** Horizontal footprint of one grid cell. */
    private static final int CELL_SIZE = 4;

    /** Grid extends this many cells each direction from the player (±64 blocks at CELL_SIZE=4). */
    private static final int GRID_RADIUS_CELLS = 16;

    /** How far below the ceiling the walls/caps extend. */
    private static final int WALL_HEIGHT = 40;

    /** Rebuild at least this often so block changes (a new hole in a roof) eventually show up. */
    private static final long REBUILD_INTERVAL_TICKS = 20;

    /** Rebuild early if the player has moved this far from where the grid was last centred. */
    private static final double REBUILD_MOVE_THRESHOLD_SQ = (CELL_SIZE * 3.0) * (CELL_SIZE * 3.0);

    private static final float FOG_R = 0.32f;
    private static final float FOG_G = 0.45f;
    private static final float FOG_B = 0.16f;

    /** Top caps are usually viewed from above at range, so they can read near-opaque. */
    private static final float TOP_ALPHA = 0.9f;

    /**
     * Per-wall alpha. Walls are now drawn on <em>every</em> cell-to-cell boundary within an exposed
     * region, not just its outer silhouette (see {@link #buildGrid}) — looking across open ground
     * crosses one every {@link #CELL_SIZE} blocks, so a low per-wall alpha accumulates into a
     * believably thickening haze with distance instead of a single wall right at the field's edge.
     */
    private static final float WALL_ALPHA = 0.14f;

    private static List<Quad> cachedQuads = List.of();
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
        LocalPlayer player = mc.player;
        if (level == null || player == null || ShaderState.shadersActive()) {
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

        maybeRebuild(level, player, ceilingY);
        if (cachedQuads.isEmpty()) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
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

    private static void maybeRebuild(ClientLevel level, LocalPlayer player, int ceilingY) {
        long now = level.getGameTime();
        double dx = player.getX() - lastBuildX;
        double dz = player.getZ() - lastBuildZ;
        boolean moved = Double.isNaN(lastBuildX) || dx * dx + dz * dz > REBUILD_MOVE_THRESHOLD_SQ;
        boolean stale = now - lastRebuildTick >= REBUILD_INTERVAL_TICKS;
        boolean ceilingChanged = ceilingY != lastCeilingY;
        if (!moved && !stale && !ceilingChanged) {
            return;
        }
        lastRebuildTick = now;
        lastBuildX = player.getX();
        lastBuildZ = player.getZ();
        lastCeilingY = ceilingY;
        cachedQuads = buildGrid(level, player, ceilingY);
    }

    /**
     * Samples a grid of columns around the player: a column is "exposed" if nothing solid reaches
     * the ceiling's height there (so ordinary terrain or a roof both correctly count as sealing it).
     * Emits a top cap for every exposed cell, plus a wall at <em>every</em> cell-to-cell boundary
     * touching an exposed cell — including boundaries between two exposed cells, not just the outer
     * silhouette. Without interior walls, a large open exposed area (a field, the outdoors past a
     * sealed base) reads as a hollow shell: haze only at its outer edge and a thin cap far overhead,
     * with nothing visible once you're looking into its middle — exactly the "only see it at the
     * edges" bug reported from a screenshot. Each shared boundary is drawn exactly once (only the
     * west/north walls are unconditional; east/south only fire at a true silhouette edge) so two
     * neighbouring exposed cells never double-draw the same plane.
     *
     * <p>Each cell's geometry is floored at <em>that cell's own</em> solid height, not a single
     * ceiling-relative constant. A column only reads "exposed" because nothing reaches the ceiling's
     * exact height there — a base whose own roof sits lower than the (possibly since-risen) ceiling
     * still passes that check, so without this clamp the wall band would dip below the roof and
     * straight into the room's own interior air, which nothing then occludes (camera and geometry
     * share the same open pocket) — exactly the "fog inside a sealed room" regression a screenshot
     * caught. Flooring at the column's own height keeps the geometry at or above any real roof, so
     * the room's own solid ceiling correctly occludes it from within.
     */
    private static List<Quad> buildGrid(ClientLevel level, LocalPlayer player, int ceilingY) {
        int diameter = GRID_RADIUS_CELLS * 2 + 1;
        boolean[][] exposed = new boolean[diameter][diameter];
        int[][] floorY = new int[diameter][diameter];
        int originCellX = Math.floorDiv((int) Math.floor(player.getX()), CELL_SIZE) - GRID_RADIUS_CELLS;
        int originCellZ = Math.floorDiv((int) Math.floor(player.getZ()), CELL_SIZE) - GRID_RADIUS_CELLS;
        int bandBottom = ceilingY - WALL_HEIGHT;

        for (int cz = 0; cz < diameter; cz++) {
            for (int cx = 0; cx < diameter; cx++) {
                int blockX = (originCellX + cx) * CELL_SIZE + CELL_SIZE / 2;
                int blockZ = (originCellZ + cz) * CELL_SIZE + CELL_SIZE / 2;
                if (!level.hasChunk(blockX >> 4, blockZ >> 4)) {
                    continue; // treat unloaded columns as not-exposed rather than force-loading them
                }
                int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
                exposed[cx][cz] = topY <= ceilingY;
                floorY[cx][cz] = Math.max(topY, bandBottom); // never dip below this column's own surface
            }
        }

        List<Quad> quads = new ArrayList<>();
        for (int cz = 0; cz < diameter; cz++) {
            for (int cx = 0; cx < diameter; cx++) {
                if (!exposed[cx][cz]) {
                    continue;
                }
                float x0 = (originCellX + cx) * CELL_SIZE;
                float z0 = (originCellZ + cz) * CELL_SIZE;
                float x1 = x0 + CELL_SIZE;
                float z1 = z0 + CELL_SIZE;
                float bottomY = floorY[cx][cz];

                quads.add(new Quad(x0, ceilingY, z0, x1, ceilingY, z0, x1, ceilingY, z1, x0, ceilingY, z1, TOP_ALPHA));
                quads.add(
                        new Quad(x0, bottomY, z0, x0, bottomY, z1, x1, bottomY, z1, x1, bottomY, z0, TOP_ALPHA * 0.5f));

                // West/north walls are unconditional: this cell always owns the boundary it shares
                // with its west/north neighbour, whether that neighbour is exposed (an interior
                // partition) or not (the true silhouette edge). East/south only fire when that
                // specific neighbour isn't exposed, so the boundary with an exposed east/south
                // neighbour is left for THAT cell's own (unconditional) west/north wall to draw —
                // every shared plane gets exactly one wall, never two.
                quads.add(new Quad(x0, bottomY, z0, x1, bottomY, z0, x1, ceilingY, z0, x0, ceilingY, z0, WALL_ALPHA));
                quads.add(new Quad(x0, bottomY, z1, x0, bottomY, z0, x0, ceilingY, z0, x0, ceilingY, z1, WALL_ALPHA));
                if (!isExposed(exposed, cx, cz + 1, diameter)) { // south wall
                    quads.add(
                            new Quad(x1, bottomY, z1, x0, bottomY, z1, x0, ceilingY, z1, x1, ceilingY, z1, WALL_ALPHA));
                }
                if (!isExposed(exposed, cx + 1, cz, diameter)) { // east wall
                    quads.add(
                            new Quad(x1, bottomY, z0, x1, bottomY, z1, x1, ceilingY, z1, x1, ceilingY, z0, WALL_ALPHA));
                }
            }
        }
        return quads;
    }

    private static boolean isExposed(boolean[][] exposed, int cx, int cz, int diameter) {
        if (cx < 0 || cz < 0 || cx >= diameter || cz >= diameter) {
            return false; // out of grid range: treat as a boundary so the edge of coverage gets a wall
        }
        return exposed[cx][cz];
    }
}
