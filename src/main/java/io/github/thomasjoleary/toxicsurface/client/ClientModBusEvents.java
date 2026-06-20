// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Client mod-bus setup (DESIGN.md §3, §4). */
@EventBusSubscriber(modid = ToxicSurface.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModBusEvents {
    private ClientModBusEvents() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "equipment_hud"),
                EquipmentHudOverlay::render);
    }
}
