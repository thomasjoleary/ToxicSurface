// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface;

import io.github.thomasjoleary.toxicsurface.registry.ModBlockEntities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/** Mod-bus setup that isn't tied to a registry (DESIGN.md §3 Machines). */
@EventBusSubscriber(modid = ToxicSurface.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModBusEvents {
    private ModBusEvents() {}

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Expose the Weaver's inventory so hoppers/pipes can automate it.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.WEAVER.get(),
                (weaver, side) -> weaver.getItemHandler());
        // Expose the Cleanser's fuel slot for hopper automation.
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.CLEANSER.get(),
                (cleanser, side) -> cleanser.getItemHandler());
    }
}
