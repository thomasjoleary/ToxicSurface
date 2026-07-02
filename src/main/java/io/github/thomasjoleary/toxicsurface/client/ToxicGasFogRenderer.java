// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
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
 * toxic air (at/below the ceiling, and at the top of its own column rather than under a roof). The
 * "is this column open / is this pixel under cover" test reads a small {@link #heightTexture per-column
 * terrain-top texture} rebuilt around the camera, so it is exact per pixel and updates smoothly.
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
    /** Side length (blocks and texels — 1 block per texel) of the per-column height map around the camera. */
    private static final int MAP_SIZE = 256;

    private static final int MAP_HALF = MAP_SIZE / 2;

    /** Rebuild the height map when the camera drifts this far from where it was last centred. */
    private static final int REBUILD_MOVE_THRESHOLD = 16;

    /** Rebuild at least this often so block changes (a new hole in a roof) eventually show up. */
    private static final long REBUILD_INTERVAL_TICKS = 20;

    /** Encoded top for an unloaded/unknown column: huge, so the shader's under-cover test always skips it. */
    private static final int UNKNOWN_TOP = 30000;

    private static final float FOG_R = 0.32f;
    private static final float FOG_G = 0.45f;
    private static final float FOG_B = 0.16f;
    private static final float FOG_DENSITY = 0.04f;
    private static final float FOG_MAX_ALPHA = 0.85f;

    /** Set by {@link ClientModBusEvents#registerShaders} once the core shader compiles. */
    static ShaderInstance shader;

    private static DynamicTexture heightTexture;
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
        int ceilingY = ClientGasState.toxicCeilingY();
        if (ceilingY == Integer.MIN_VALUE) {
            return;
        }
        float intensity = (float) (double) ToxicSurfaceClientConfig.FOG_INTENSITY.get();
        if (intensity <= 0f) {
            return;
        }

        try {
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

    private static void draw(RenderLevelStageEvent event, Minecraft mc, Vec3 cam, int ceilingY, float intensity) {
        Matrix4f invViewProj = new Matrix4f(event.getProjectionMatrix())
                .mul(event.getModelViewMatrix())
                .invert();

        shader.setSampler("DepthSampler", mc.getMainRenderTarget().getDepthTextureId());
        shader.setSampler("HeightSampler", heightTexture.getId());
        shader.safeGetUniform("InvViewProj").set(invViewProj);
        shader.safeGetUniform("CameraPos").set((float) cam.x, (float) cam.y, (float) cam.z);
        shader.safeGetUniform("CeilingY").set((float) ceilingY);
        shader.safeGetUniform("HeightOrigin").set((float) mapOriginX, (float) mapOriginZ);
        shader.safeGetUniform("HeightWorldSize").set((float) MAP_SIZE);
        shader.safeGetUniform("FogColor").set(FOG_R, FOG_G, FOG_B);
        shader.safeGetUniform("FogDensity").set(FOG_DENSITY);
        shader.safeGetUniform("FogMaxAlpha").set(FOG_MAX_ALPHA * intensity);

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
        int desiredOriginX = Mth.floor(cam.x) - MAP_HALF;
        int desiredOriginZ = Mth.floor(cam.z) - MAP_HALF;
        boolean firstOrLevelChange = heightTexture == null || level != lastLevel;
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

    private static void buildHeightTexture(ClientLevel level) {
        if (heightTexture == null) {
            heightTexture = new DynamicTexture(MAP_SIZE, MAP_SIZE, false);
            heightTexture.setFilter(false, false); // nearest, no mipmap: encoded bytes must not be blended
        }
        NativeImage pixels = heightTexture.getPixels();
        if (pixels == null) {
            return;
        }
        for (int tz = 0; tz < MAP_SIZE; tz++) {
            for (int tx = 0; tx < MAP_SIZE; tx++) {
                int worldX = mapOriginX + tx;
                int worldZ = mapOriginZ + tz;
                int top = level.hasChunk(worldX >> 4, worldZ >> 4)
                        ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ)
                        : UNKNOWN_TOP;
                pixels.setPixelRGBA(tx, tz, encodeTop(top));
            }
        }
        heightTexture.upload();
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
