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
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.core.enclosure.LevelPassabilityProbe;
import io.github.thomasjoleary.toxicsurface.core.enclosure.RegionOpenness;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
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
 * toxic air (at/below the ceiling, and reachable by the atmosphere). Exposure is answered by two tiers:
 * near the camera, a {@link #volumeTexture 3D air-connectivity volume} classified with the <em>same</em>
 * passability rules the damage scanner uses ({@link LevelPassabilityProbe} + {@link RegionOpenness}),
 * so fog floods a breached room, fills unsealed caves and overhangs, and stays out of sealed bases —
 * exactly where the gas would hurt. Beyond the volume, a {@link #heightTexture per-column roof-test
 * height map} approximates the same answer (distant interiors are barely visible anyway), and the
 * shader blends the two softly at the volume's edge.
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

    // Near-field exposure volume: a 3D air-connectivity grid around the camera, classified by the same
    // passability rules the damage scanner uses (LevelPassabilityProbe + RegionOpenness). It is what lets
    // fog flood a breached room, fill unsealed caves/overhangs, and stay out of sealed bases — per-column
    // height data cannot see an opening under a roof line. Outside the volume the shader falls back to
    // the height-map roof test, blended softly at the volume's edge.
    /** Volume cell dimensions; must match {@code VOL_*} in toxic_fog.fsh. */
    private static final int VOL_SX = 96;

    private static final int VOL_SY = 48;
    private static final int VOL_SZ = 96;

    /** Y-slices per atlas row when packing the volume into a 2D texture; must match toxic_fog.fsh. */
    private static final int VOL_ATLAS_COLS = 8;

    /** Columns of the volume scanned per frame, so a rebuild never spikes one frame (amortized ~12 frames). */
    private static final int VOL_COLUMNS_PER_FRAME = 768;

    /** Restart the volume scan when the camera drifts this far from the last scan's centre. */
    private static final int VOL_REBUILD_MOVE_THRESHOLD = 12;

    /** ABGR pixels for {@link NativeImage}: R=255 marks a fog-able (exposed air, not fluid) cell. */
    private static final int VOL_PIXEL_EXPOSED = 0xFF0000FF;

    private static final int VOL_PIXEL_CLEAR = 0xFF000000;

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

    // Near-field volume state. The scan builds passable/fluid bit sets a slice of columns per frame
    // (against scanOrigin*); on completion the flood classifies them and the result is packed into
    // volumeTexture, which the shader reads against the *active* origin until the next build lands.
    private static DynamicTexture volumeTexture;

    private static RegionOpenness volumeFlood;
    private static long[] volPassable;
    private static long[] volFluid;
    private static long[] volExposed;
    /** Next column (of {@code VOL_SX * VOL_SZ}) to scan, or -1 when no scan is in progress. */
    private static int volScanColumn = -1;

    private static int scanOriginX;
    private static int scanOriginY;
    private static int scanOriginZ;
    private static int volOriginX;
    private static int volOriginY;
    private static int volOriginZ;
    private static boolean volumeReady;
    private static long lastVolumeBuildTick = Long.MIN_VALUE;
    private static ClientLevel volumeLevel;

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
            updateVolume(level, cam, ceilingY);
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

    /**
     * Drives the near-field volume rebuild a slice at a time. A scan reads passability/fluid state for
     * {@link #VOL_COLUMNS_PER_FRAME} columns per frame (so no single frame pays the whole region), and on
     * the final slice runs the {@link RegionOpenness} flood and swaps the packed result in. Between
     * builds the previous volume stays active, so the fog lags a block change by at most a scan plus the
     * rebuild interval (~1–2s), same as the height map.
     */
    private static void updateVolume(ClientLevel level, Vec3 cam, int ceilingY) {
        long now = level.getGameTime();
        if (level != volumeLevel) {
            volumeLevel = level;
            volumeReady = false;
            volScanColumn = -1;
        }
        if (volScanColumn < 0) {
            // When flying above the gas, anchor the volume just under the ceiling instead of the camera:
            // that is where the structures worth resolving (bases, breaches) actually are.
            int centerY = ceilingY == Integer.MIN_VALUE
                    ? Mth.floor(cam.y)
                    : Math.min(Mth.floor(cam.y), ceilingY + VOL_SY / 3);
            int desiredX = Mth.floor(cam.x) - VOL_SX / 2;
            int desiredZ = Mth.floor(cam.z) - VOL_SZ / 2;
            int desiredY =
                    Mth.clamp(centerY - VOL_SY / 2, level.getMinBuildHeight(), level.getMaxBuildHeight() - VOL_SY);
            boolean drifted = !volumeReady
                    || Math.abs(desiredX - volOriginX) > VOL_REBUILD_MOVE_THRESHOLD
                    || Math.abs(desiredY - volOriginY) > VOL_REBUILD_MOVE_THRESHOLD
                    || Math.abs(desiredZ - volOriginZ) > VOL_REBUILD_MOVE_THRESHOLD;
            boolean stale = now - lastVolumeBuildTick >= REBUILD_INTERVAL_TICKS;
            if (!drifted && !stale) {
                return;
            }
            if (volumeFlood == null) {
                volumeFlood = new RegionOpenness(VOL_SX, VOL_SY, VOL_SZ);
                int words = (volumeFlood.cellCount() + 63) >> 6;
                volPassable = new long[words];
                volFluid = new long[words];
                volExposed = new long[words];
            }
            Arrays.fill(volPassable, 0L);
            Arrays.fill(volFluid, 0L);
            scanOriginX = desiredX;
            scanOriginY = desiredY;
            scanOriginZ = desiredZ;
            volScanColumn = 0;
        }

        scanVolumeSlice(level);
        if (volScanColumn >= VOL_SX * VOL_SZ) {
            volScanColumn = -1;
            lastVolumeBuildTick = now;
            volumeFlood.classify(volPassable, ToxicSurfaceConfig.ENCLOSURE_FLOOD_FILL_BUDGET.get(), volExposed);
            packAndUploadVolume();
            volOriginX = scanOriginX;
            volOriginY = scanOriginY;
            volOriginZ = scanOriginZ;
            volumeReady = true;
        }
    }

    /** Reads passability and fluid state for the next {@link #VOL_COLUMNS_PER_FRAME} columns of the scan. */
    private static void scanVolumeSlice(ClientLevel level) {
        LevelPassabilityProbe probe = new LevelPassabilityProbe(level);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int end = Math.min(volScanColumn + VOL_COLUMNS_PER_FRAME, VOL_SX * VOL_SZ);
        for (int col = volScanColumn; col < end; col++) {
            int cx = col % VOL_SX;
            int cz = col / VOL_SX;
            int worldX = scanOriginX + cx;
            int worldZ = scanOriginZ + cz;
            if (!level.hasChunk(worldX >> 4, worldZ >> 4)) {
                continue; // unloaded columns stay impassable: no fog claims, and no leaks through them
            }
            for (int cy = 0; cy < VOL_SY; cy++) {
                int worldY = scanOriginY + cy;
                if (!probe.isPassable(worldX, worldY, worldZ)) {
                    continue;
                }
                int idx = volumeFlood.index(cx, cy, cz);
                RegionOpenness.set(volPassable, idx);
                // Gas can flow through a fluid cell (matching the damage scan) but never renders in one:
                // submerged cells are clean per GasModel, so they are exposed for connectivity yet fog-free.
                if (!level.getFluidState(cursor.set(worldX, worldY, worldZ)).isEmpty()) {
                    RegionOpenness.set(volFluid, idx);
                }
            }
        }
        volScanColumn = end;
    }

    /** Packs exposed-and-not-fluid cells into the Y-slice atlas texture the shader reads. */
    private static void packAndUploadVolume() {
        if (volumeTexture == null) {
            volumeTexture = new DynamicTexture(VOL_SX * VOL_ATLAS_COLS, VOL_SZ * (VOL_SY / VOL_ATLAS_COLS), false);
            volumeTexture.setFilter(false, false); // nearest: cells are hard data, never blend texels
        }
        NativeImage pixels = volumeTexture.getPixels();
        if (pixels == null) {
            return;
        }
        for (int cy = 0; cy < VOL_SY; cy++) {
            int baseX = (cy % VOL_ATLAS_COLS) * VOL_SX;
            int baseY = (cy / VOL_ATLAS_COLS) * VOL_SZ;
            for (int cz = 0; cz < VOL_SZ; cz++) {
                for (int cx = 0; cx < VOL_SX; cx++) {
                    int idx = volumeFlood.index(cx, cy, cz);
                    boolean fog = RegionOpenness.get(volExposed, idx) && !RegionOpenness.get(volFluid, idx);
                    pixels.setPixelRGBA(baseX + cx, baseY + cz, fog ? VOL_PIXEL_EXPOSED : VOL_PIXEL_CLEAR);
                }
            }
        }
        volumeTexture.upload();
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
        if (volumeTexture != null) {
            shader.setSampler("VolumeSampler", volumeTexture.getId());
        }
        shader.safeGetUniform("VolOrigin").set((float) volOriginX, (float) volOriginY, (float) volOriginZ);
        shader.safeGetUniform("VolReady").set(volumeReady ? 1 : 0);
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
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int tz = 0; tz < mapSize; tz++) {
            for (int tx = 0; tx < mapSize; tx++) {
                int worldX = mapOriginX + tx;
                int worldZ = mapOriginZ + tz;
                pixels.setPixelRGBA(tx, tz, encodeTop(sealingTop(level, cursor, worldX, worldZ)));
            }
        }
        heightTexture.upload();
    }

    /**
     * The Y at/above which this column is genuinely open toxic air — i.e. the top of the highest block
     * that actually <em>seals</em> the column (a full cube: solid ground, a glass skylight, a roof), not
     * merely the highest block that blocks motion. The raw {@code MOTION_BLOCKING} height also picks up
     * thin non-full blocks — a Create shaft, a fence, a torch — and treating one as a roof would clear a
     * fog-free column around it (the pale halo the player noticed). So if the motion-blocking top block
     * is not a full cube, we look a short way down for one that is; finding none nearby, the column reads
     * as open (a lone thin structure casts no fog shadow) rather than roofed.
     */
    private static int sealingTop(ClientLevel level, BlockPos.MutableBlockPos cursor, int worldX, int worldZ) {
        if (!level.hasChunk(worldX >> 4, worldZ >> 4)) {
            return UNKNOWN_TOP;
        }
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
        int limit = Math.max(level.getMinBuildHeight(), top - 1 - MAX_ROOF_SCAN);
        for (int y = top - 1; y >= limit; y--) {
            cursor.set(worldX, y, worldZ);
            BlockState state = level.getBlockState(cursor);
            // A fluid surface seals like ground: gas sits on water, never inside it (GasModel's
            // submerged rule). Without this, deep water — no full cube within the scan window — read
            // as air open to bedrock, and the far field filled the ocean with fog.
            if (state.isCollisionShapeFullBlock(level, cursor)
                    || !state.getFluidState().isEmpty()) {
                return y + 1; // top surface of the sealing cube/fluid is the fog floor
            }
        }
        // No sealing cube within the scan window: treat as open ground so a thin block/structure sitting
        // above a gap doesn't mask the air around it. Void level keeps p.y >= ground true up the column.
        return level.getMinBuildHeight();
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
