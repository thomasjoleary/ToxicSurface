// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceClientConfig;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Renders the toxic-gas haze (DESIGN.md §3 Client rendering). Whenever toxic gas is present at the
 * local player's head, fog is pulled in tight and tinted sickly green. This is driven off
 * {@link ClientGasState#isInToxicArea()} — protection-independent — so a player in a mask or hazmat
 * suit still <em>sees</em> (and is obstructed by) the gas they're shielded from, and can tell where
 * it is. It still respects the server's sealing/ceiling/cleanser decision (no fog in a sealed base).
 *
 * <p>The {@code fogIntensity} accessibility slider (DESIGN.md §3) scales how far the fog is pulled
 * in — at {@code 0} the distance narrowing is dropped entirely and only a thin green tint remains.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID, value = Dist.CLIENT)
public final class ToxicFogHandler {
    private static final float FOG_NEAR = 0.5F;
    private static final float FOG_FAR = 12.0F;

    private ToxicFogHandler() {}

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!ClientGasState.isInToxicArea()) {
            return;
        }
        float intensity = (float) (double) ToxicSurfaceClientConfig.FOG_INTENSITY.get();
        if (intensity <= 0f) {
            return; // thin-tint-only mode: keep the green color, drop the distance narrowing
        }
        // Lerp the pulled-in planes toward the vanilla ones as intensity drops.
        float near = Mth.lerp(intensity, event.getNearPlaneDistance(), FOG_NEAR);
        float far = Mth.lerp(intensity, event.getFarPlaneDistance(), FOG_FAR);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), near));
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), far));
        event.setCanceled(true); // apply our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!ClientGasState.isInToxicArea()) {
            return;
        }
        // Always keep at least a thin tint; the slider blends from a hint toward the full toxic color.
        float t = 0.35f + 0.65f * (float) (double) ToxicSurfaceClientConfig.FOG_INTENSITY.get();
        event.setRed(Mth.lerp(t, event.getRed(), 0.32F));
        event.setGreen(Mth.lerp(t, event.getGreen(), 0.45F));
        event.setBlue(Mth.lerp(t, event.getBlue(), 0.16F));
    }
}
