// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * Renders the toxic-gas haze as a real per-pixel volumetric effect (DESIGN.md §3 Client rendering,
 * gas visibility), instead of the plain screen fog in {@link ToxicFogHandler} (which only ever shows
 * while the <em>camera's own cell</em> is exposed — so it goes fully transparent the moment you're
 * sealed, cleansed, or above the ceiling, even while looking straight at gas-covered ground through
 * a window or from above).
 *
 * <p>Each frame this reconstructs every pixel's world-space Y from the depth buffer (via the inverse
 * of the current projection * model-view matrix) and blends in green haze, scaled by distance from
 * the camera, wherever that reconstructed point sits at or below the dimension's toxic ceiling — with
 * no regard for whether the <em>camera</em> is sealed/cleansed/above it. That's what makes gas outside
 * a cleanser bubble, outside a sealed room's window, or on the ground far below visible.
 *
 * <p>Depth alone can't tell a sealed room's own (safe) far wall from exposed exterior ground at the
 * same distance — both are just "a solid surface below the ceiling Y." {@code ClientGasState}'s
 * {@code minFogDistance} (from {@link io.github.thomasjoleary.toxicsurface.effect.GasVisibilityRay}
 * walking the same rules that gate real exposure/damage along the player's view direction) holds the
 * haze back until that confirmed-safe distance, so a sealed interior stays clear even at range.
 *
 * <p>Bows out under an active Iris/Oculus shader pack (its own pipeline doesn't expect us drawing
 * directly against {@code RenderSystem}), matching {@link ToxicWeatherEffects}. The shader is
 * optional/soft-fail: if it doesn't compile (e.g. a driver quirk), the effect silently disables
 * itself instead of crashing the client.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID, value = Dist.CLIENT)
public final class ToxicVolumetricFog {
    // Thick enough to read as near-opaque looking straight down at gassy ground from altitude
    // (~30+ blocks), while staying a lighter haze up close. Tune here if it still reads too thin/thick.
    private static final float FOG_DENSITY = 0.08f;
    private static final float FOG_MAX_ALPHA = 0.92f;
    private static final float FOG_R = 0.32f;
    private static final float FOG_G = 0.45f;
    private static final float FOG_B = 0.16f;

    /** Set by {@link ClientModBusEvents#registerShaders} once the core shader compiles. */
    static ShaderInstance shader;

    private static boolean loggedFailure;

    private ToxicVolumetricFog() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        if (shader == null || ClientGasState.toxicCeilingY() == Integer.MIN_VALUE || ShaderState.shadersActive()) {
            return;
        }
        float intensity = (float) (double) ToxicSurfaceClientConfig.FOG_INTENSITY.get();
        if (intensity <= 0f) {
            return;
        }

        try {
            render(event, intensity);
        } catch (RuntimeException e) {
            if (!loggedFailure) {
                loggedFailure = true;
                ToxicSurface.LOGGER.error("Toxic volumetric fog draw failed; disabling the effect", e);
                shader = null;
            }
        }
    }

    private static void render(RenderLevelStageEvent event, float intensity) {
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        Matrix4f invViewProj = new Matrix4f(event.getProjectionMatrix())
                .mul(event.getModelViewMatrix())
                .invert();
        float cameraY = (float) event.getCamera().getPosition().y;

        // ShaderInstance.setSampler binds a RenderTarget's *color* texture, so the depth texture id
        // must be passed as a plain Integer instead.
        shader.setSampler("DepthSampler", mainTarget.getDepthTextureId());
        shader.safeGetUniform("InvViewProj").set(invViewProj);
        shader.safeGetUniform("CameraY").set(cameraY);
        shader.safeGetUniform("CeilingY").set((float) ClientGasState.toxicCeilingY());
        shader.safeGetUniform("MinFogDistance").set(ClientGasState.minFogDistance());
        shader.safeGetUniform("FogColor").set(FOG_R, FOG_G, FOG_B);
        shader.safeGetUniform("FogDensity").set(FOG_DENSITY);
        shader.safeGetUniform("FogMaxAlpha").set(FOG_MAX_ALPHA * intensity);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
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
        RenderSystem.disableBlend();
    }
}
