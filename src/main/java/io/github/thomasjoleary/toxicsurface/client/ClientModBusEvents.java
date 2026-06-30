// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.registry.ModMenus;
import io.github.thomasjoleary.toxicsurface.registry.ModParticles;
import net.minecraft.client.particle.WaterDropParticle;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Client mod-bus setup (DESIGN.md §3, §4). */
@EventBusSubscriber(modid = ToxicSurface.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModBusEvents {
    private ClientModBusEvents() {}

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        // Visor sits *below* the hotbar so the player's HUD draws on top of it (it frames the view,
        // it must not hide the hotbar).
        event.registerBelow(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "hazmat_visor"),
                EquipmentHudOverlay::renderVisor);
        // Filter gauge text stays above everything so it's always legible.
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "equipment_gauge"),
                EquipmentHudOverlay::renderGauge);
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

    /** Replace the overworld weather effects so rain renders green below the toxic ceiling. */
    @SubscribeEvent
    public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(BuiltinDimensionTypes.OVERWORLD_EFFECTS, new ToxicWeatherEffects());
    }

    /** The green rain-splash particle reuses vanilla's water-drop behaviour with our sprite. */
    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.TOXIC_RAIN_SPLASH.get(), WaterDropParticle.Provider::new);
    }
}
