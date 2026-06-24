// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client mod-bus setup (DESIGN.md §3, §4). */
@EventBusSubscriber(modid = ToxicSurface.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModBusEvents {
    private ClientModBusEvents() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "equipment_hud"),
                EquipmentHudOverlay::render);
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "toxic_air_bar"), AirBarOverlay::render);
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "screen_effects"),
                ScreenEffectsOverlay::render);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Make the client accessibility options editable from the Mods list (DESIGN.md §3).
        ModList.get()
                .getModContainerById(ToxicSurface.MODID)
                .ifPresent(container -> container.registerExtensionPoint(
                        IConfigScreenFactory.class,
                        (modContainer, parent) -> new ConfigurationScreen(modContainer, parent)));
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.HAZMAT_CHEST.get(), HazmatChestScreen::new);
        event.register(ModMenus.WEAVER.get(), WeaverScreen::new);
        event.register(ModMenus.CLEANSER.get(), CleanserScreen::new);
    }
}
