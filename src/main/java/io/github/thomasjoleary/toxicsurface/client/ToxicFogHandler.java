// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Renders the toxic-gas haze (DESIGN.md §3 Client rendering). When the server says
 * the local player is in gas, fog is pulled in tight and tinted sickly green. Driven
 * entirely off {@link ClientGasState}, so it matches the server's sealing/exposure
 * decision without a client-side flood-fill.
 *
 * <p>TODO Phase 2 polish: accessibility sliders for haze density / tint (DESIGN.md §3
 * Accessibility).
 */
@EventBusSubscriber(modid = ToxicSurface.MODID, value = Dist.CLIENT)
public final class ToxicFogHandler {
    private static final float FOG_NEAR = 0.5F;
    private static final float FOG_FAR = 12.0F;

    private ToxicFogHandler() {}

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!ClientGasState.isInGas()) {
            return;
        }
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), FOG_NEAR));
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), FOG_FAR));
        event.setCanceled(true); // apply our distances
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!ClientGasState.isInGas()) {
            return;
        }
        event.setRed(0.32F);
        event.setGreen(0.45F);
        event.setBlue(0.16F);
    }
}
