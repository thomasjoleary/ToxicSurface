// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceClientConfig;
import io.github.thomasjoleary.toxicsurface.core.gas.SkyOpenness;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Renders the toxic-gas haze as a per-pixel screen-space effect (DESIGN.md §3 gas visibility). After
 * the level draws, a fullscreen pass reconstructs each surface pixel's world position from the depth
 * buffer and adds green haze scaled by distance — but only where that surface is genuinely exposed
 * toxic air (at/below the ceiling, and at/above its column's sky-openness floor). That floor is baked by
 * a wall-respecting flood ({@link SkyOpenness}) into a small {@link #heightTexture per-column texture}
 * rebuilt around the camera, so — matching {@link io.github.thomasjoleary.toxicsurface.core.gas.GasModel}
 * — fog fills open air and seeps under overhangs, yet a genuinely walled room stays clear. Exact per
 * pixel, and it updates smoothly.
 *
 * <p>This is deliberately view-dependent, which hard world-space geometry could not be: the haze
 * reads dense looking straight down from altitude (long ray through the layer) yet soft near the
 * ceiling, with no cell seams, no flat "fog sea" top plane, no holes stepping over hills, and full
 * view-distance coverage — the failure modes that geometry boxes kept hitting. It bows out under an
 * active Iris/Oculus shader pack (its pipeline owns the frame) and soft-fails to disabled, logged
 * once, if the core shader ever fails to compile, rather than taking the client down.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID, value = Dist.CLIENT)
public final class ToxicGasFogRenderer {
    // The fog's horizontal coverage and ray-march reach follow the player's render distance, so the haze
    // fills out to (roughly) as far as they can see rather than a fixed radius — clamped to keep the
    // per-column height map cheap enough to rebuild each refresh at very high render distances.
    /** Smallest coverage radius in blocks (8 chunks) — keeps the effect sensible at tiny render distances. */
    private static final int MIN_RADIUS = 128;

    /** Largest coverage radius in blocks (16 chunks) — a bigger height map gets costly to rebuild. */
    private static final int MAX_RADIUS = 256;

    /** Target world blocks per ray-march step; the step count is {@code maxDist / this}, clamped below. */
    private static final float BLOCKS_PER_STEP = 6f;

    private static final int MIN_STEPS = 24;

    /** Loop bound; must match {@code MAX_STEPS} in toxic_fog.fsh. */
    private static final int MAX_STEPS = 64;

    /** Side length (blocks/texels, 1 block per texel) of the per-column height map — {@code 2 * radius}. */
    private static int mapSize = 2 * MIN_RADIUS;

    private static int mapHalf = MIN_RADIUS;

    /** Ray-march cap and step count for this frame, derived from render distance; read in {@link #draw}. */
    private static float maxDist = MIN_RADIUS;

    private static int steps = MIN_STEPS;

    /** Rebuild the height map when the camera drifts this far from where it was last centred. */
    private static final int REBUILD_MOVE_THRESHOLD = 16;

    /** Rebuild at least this often so block changes (a new hole in a roof) eventually show up. */
    private static final long REBUILD_INTERVAL_TICKS = 20;

    /** Encoded top for an unloaded/unknown column: huge, so the shader's under-cover test always skips it. */
    private static final int UNKNOWN_TOP = 30000;

    /** How far below a non-full top block to look for a real sealing cube before treating a column as open. */
    private static final int MAX_ROOF_SCAN = 16;

    /** How far below a cover to look for the air-pocket floor; a deeper pocket is treated as open all the way. */
    private static final int MAX_POCKET_SCAN = 32;

    /** Reusable per-column scratch for the sky-openness flood, sized to {@code mapSize * mapSize}. */
    private static int[] coverBuf = new int[0];

    private static int[] floorBuf = new int[0];

    private static final float FOG_R = 0.24f;
    private static final float FOG_G = 0.34f;
    private static final float FOG_B = 0.12f;
    private static final float FOG_DENSITY = 0.35f;
    private static final float FOG_MAX_ALPHA = 1.0f;

    /** Set by {@link ClientModBusEvents#registerShaders} once the core shader compiles. */
    static ShaderInstance shader;

    private static DynamicTexture heightTexture;
    /**
     * A standalone copy of the scene depth. Sampling the main render target's own depth texture while
     * it is still attached as that target's depth buffer is a GL feedback loop (undefined behaviour) —
     * it left a stable garbage region of the screen reading as far-plane, so the fog there behaved as
     * if it were open sky. We blit depth into this un-attached target and sample the copy instead.
     */
    private static TextureTarget depthCopy;

    private static int mapOriginX;
    private static int mapOriginZ;
    private static long lastRebuildTick = Long.MIN_VALUE;
    private static int lastCeilingY = Integer.MIN_VALUE;
    private static ClientLevel lastLevel;
    private static boolean loggedFailure;

    private ToxicGasFogRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (shader == null || level == null || ShaderState.shadersActive()) {
            return;
        }
        // No ambient gas layer (dimension not toxic, or not toxic yet) — but a running generator's
        // smog still hazes its own sphere, so only bail if there is neither a ceiling nor any smog.
        int ceilingY = ClientGasState.toxicCeilingY();
        if (ceilingY == Integer.MIN_VALUE && ClientFogVolumes.smogCount() == 0) {
            return;
        }
        float intensity = (float) (double) ToxicSurfaceClientConfig.FOG_INTENSITY.get();
        if (intensity <= 0f) {
            return;
        }

        try {
            updateCoverage(mc);
            Vec3 cam = event.getCamera().getPosition();
            rebuildIfNeeded(level, cam, ceilingY);
            draw(event, mc, cam, ceilingY, intensity);
        } catch (RuntimeException e) {
            if (!loggedFailure) {
                loggedFailure = true;
                ToxicSurface.LOGGER.error("Toxic fog draw failed; disabling the effect", e);
                shader = null;
            }
        }
    }

    /**
     * Sizes the height map, march cap, and step count to the player's effective render distance (clamped
     * to [{@link #MIN_RADIUS}, {@link #MAX_RADIUS}]) so the fog reaches about as far as they can see. Cheap
     * to call every frame; the actual (costly) height-map rebuild only happens when the size or centre
     * changes, via {@link #rebuildIfNeeded}.
     */
    private static void updateCoverage(Minecraft mc) {
        int renderBlocks = mc.options.getEffectiveRenderDistance() * 16;
        int radius = Mth.clamp(renderBlocks, MIN_RADIUS, MAX_RADIUS);
        mapSize = radius * 2;
        mapHalf = radius;
        maxDist = radius;
        steps = Mth.clamp(Math.round(maxDist / BLOCKS_PER_STEP), MIN_STEPS, MAX_STEPS);
    }

    private static void draw(RenderLevelStageEvent event, Minecraft mc, Vec3 cam, int ceilingY, float intensity) {
        Matrix4f invViewProj = new Matrix4f(event.getProjectionMatrix())
                .mul(event.getModelViewMatrix())
                .invert();

        // Copy the scene depth into a separate target and sample that, to avoid the feedback loop of
        // reading the main target's depth while it is still attached (see depthCopy field). copyDepthFrom
        // leaves framebuffer 0 bound, so re-bind the main target before drawing the fog quad into it.
        RenderTarget main = mc.getMainRenderTarget();
        if (depthCopy == null || depthCopy.width != main.width || depthCopy.height != main.height) {
            if (depthCopy == null) {
                depthCopy = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
            } else {
                depthCopy.resize(main.width, main.height, Minecraft.ON_OSX);
            }
        }
        depthCopy.copyDepthFrom(main);
        main.bindWrite(false);

        shader.setSampler("DepthSampler", depthCopy.getDepthTextureId());
        shader.setSampler("HeightSampler", heightTexture.getId());
        shader.safeGetUniform("InvViewProj").set(invViewProj);
        shader.safeGetUniform("CameraPos").set((float) cam.x, (float) cam.y, (float) cam.z);
        shader.safeGetUniform("CeilingY").set((float) ceilingY);
        shader.safeGetUniform("HeightOrigin").set((float) mapOriginX, (float) mapOriginZ);
        shader.safeGetUniform("HeightWorldSize").set((float) mapSize);
        shader.safeGetUniform("MaxDist").set(maxDist);
        shader.safeGetUniform("Steps").set(steps);
        shader.safeGetUniform("FogColor").set(FOG_R, FOG_G, FOG_B);
        shader.safeGetUniform("FogDensity").set(FOG_DENSITY);
        shader.safeGetUniform("FogMaxAlpha").set(FOG_MAX_ALPHA * intensity);
        shader.safeGetUniform("CleanserCount").set(ClientFogVolumes.cleanserCount());
        shader.safeGetUniform("CleanserData").set(ClientFogVolumes.cleanser());
        shader.safeGetUniform("SmogCount").set(ClientFogVolumes.smogCount());
        shader.safeGetUniform("SmogData").set(ClientFogVolumes.smog());

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        shader.apply();

        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1f, -1f, 0f);
        buffer.addVertex(1f, -1f, 0f);
        buffer.addVertex(1f, 1f, 0f);
        buffer.addVertex(-1f, 1f, 0f);
        BufferUploader.draw(buffer.buildOrThrow());

        shader.clear();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void rebuildIfNeeded(ClientLevel level, Vec3 cam, int ceilingY) {
        long now = level.getGameTime();
        int desiredOriginX = Mth.floor(cam.x) - mapHalf;
        int desiredOriginZ = Mth.floor(cam.z) - mapHalf;
        boolean firstOrLevelChange = heightTexture == null || level != lastLevel || resized();
        boolean drifted = Math.abs(desiredOriginX - mapOriginX) > REBUILD_MOVE_THRESHOLD
                || Math.abs(desiredOriginZ - mapOriginZ) > REBUILD_MOVE_THRESHOLD;
        boolean stale = now - lastRebuildTick >= REBUILD_INTERVAL_TICKS;
        if (!firstOrLevelChange && !drifted && !stale && ceilingY == lastCeilingY) {
            return;
        }
        lastLevel = level;
        lastRebuildTick = now;
        lastCeilingY = ceilingY;
        mapOriginX = desiredOriginX;
        mapOriginZ = desiredOriginZ;
        buildHeightTexture(level);
    }

    /** True if the height texture exists but no longer matches the current {@link #mapSize}. */
    private static boolean resized() {
        NativeImage pixels = heightTexture == null ? null : heightTexture.getPixels();
        return pixels != null && pixels.getWidth() != mapSize;
    }

    private static void buildHeightTexture(ClientLevel level) {
        if (heightTexture == null || resized()) {
            if (heightTexture != null) {
                heightTexture.close(); // render distance changed: drop the old-sized texture and reallocate
            }
            heightTexture = new DynamicTexture(mapSize, mapSize, false);
            heightTexture.setFilter(false, false); // nearest, no mipmap: encoded bytes must not be blended
        }
        NativeImage pixels = heightTexture.getPixels();
        if (pixels == null) {
            return;
        }
        int n = mapSize * mapSize;
        if (coverBuf.length != n) {
            coverBuf = new int[n];
            floorBuf = new int[n];
        }
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int tz = 0; tz < mapSize; tz++) {
            for (int tx = 0; tx < mapSize; tx++) {
                scanColumn(level, cursor, mapOriginX + tx, mapOriginZ + tz, minY, tz * mapSize + tx);
            }
        }
        // Flood sky-openness across the columns so covered-but-open air (overhangs) exposes low while walls
        // keep a room's air sealed — matching GasModel, which only exempts genuinely sealed space (DESIGN §3).
        int[] sky = SkyOpenness.compute(mapSize, mapSize, coverBuf, floorBuf, minY, maxY);
        for (int tz = 0; tz < mapSize; tz++) {
            for (int tx = 0; tx < mapSize; tx++) {
                pixels.setPixelRGBA(tx, tz, encodeTop(sky[tz * mapSize + tx]));
            }
        }
        heightTexture.upload();
    }

    /**
     * Records this column's two heights for the sky-openness flood into {@link #coverBuf}/{@link #floorBuf}:
     * <ul>
     *   <li>{@code cover} — the top of the highest sealing cube (roof, ground, or overhang lip). As in the
     *       halo fix, thin non-full blocks (a Create shaft, a fence, a torch) are skipped so they don't read
     *       as a roof; if none is found near the motion-blocking top the column is fully open.
     *   <li>{@code floor} — the floor of the air pocket beneath that cover. Solid directly under the cover
     *       (a wall or plain ground) means no pocket, so {@code floor == cover} and the flood can't pass
     *       under it; an air gap (an overhang) gives a low floor the flood flows through.
     * </ul>
     */
    private static void scanColumn(
            ClientLevel level, BlockPos.MutableBlockPos cursor, int worldX, int worldZ, int minY, int idx) {
        if (!level.hasChunk(worldX >> 4, worldZ >> 4)) {
            coverBuf[idx] = UNKNOWN_TOP; // unloaded: acts as a wall (clamped high) so openness can't leak through
            floorBuf[idx] = UNKNOWN_TOP;
            return;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
        int coverLimit = Math.max(minY, top - 1 - MAX_ROOF_SCAN);
        int coverY = Integer.MIN_VALUE;
        for (int y = top - 1; y >= coverLimit; y--) {
            cursor.set(worldX, y, worldZ);
            if (level.getBlockState(cursor).isCollisionShapeFullBlock(level, cursor)) {
                coverY = y;
                break;
            }
        }
        if (coverY == Integer.MIN_VALUE) {
            // Only thin blocks near the top (or nothing): fully open, so a lone structure casts no shadow.
            coverBuf[idx] = minY;
            floorBuf[idx] = minY;
            return;
        }
        int cover = coverY + 1;
        coverBuf[idx] = cover;

        // Is there an air pocket right under the cover (overhang), or solid (wall/ground)?
        int belowY = coverY - 1;
        cursor.set(worldX, belowY, worldZ);
        if (belowY < minY || level.getBlockState(cursor).isCollisionShapeFullBlock(level, cursor)) {
            floorBuf[idx] = cover; // solid beneath the cover: no pocket, the flood is blocked here
            return;
        }
        // Descend through the pocket to the first full cube below; that surface is the air floor.
        int floorLimit = Math.max(minY, belowY - MAX_POCKET_SCAN);
        for (int y = belowY - 1; y >= floorLimit; y--) {
            cursor.set(worldX, y, worldZ);
            if (level.getBlockState(cursor).isCollisionShapeFullBlock(level, cursor)) {
                floorBuf[idx] = y + 1;
                return;
            }
        }
        floorBuf[idx] = minY; // pocket deeper than the scan cap: treat as open to the bottom
    }

    /**
     * Packs a column top (in blocks) into an ABGR int for {@link NativeImage#setPixelRGBA}: the R byte
     * (low 8 bits of the int) holds the high byte of {@code top + 64}, the G byte the low byte, so the
     * shader recovers a 16-bit height as {@code R*256 + G - 64}. Alpha is forced opaque; blue unused.
     */
    private static int encodeTop(int top) {
        int h = Mth.clamp(top + 64, 0, 0xFFFF);
        int hi = (h >> 8) & 0xFF;
        int lo = h & 0xFF;
        return (0xFF << 24) | (lo << 8) | hi;
    }
}
