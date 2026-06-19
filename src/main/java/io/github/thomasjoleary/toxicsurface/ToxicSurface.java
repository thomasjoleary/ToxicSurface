// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface;

import com.mojang.logging.LogUtils;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.registry.ModArmorMaterials;
import io.github.thomasjoleary.toxicsurface.registry.ModAttachments;
import io.github.thomasjoleary.toxicsurface.registry.ModBlocks;
import io.github.thomasjoleary.toxicsurface.registry.ModCreativeTabs;
import io.github.thomasjoleary.toxicsurface.registry.ModDataComponents;
import io.github.thomasjoleary.toxicsurface.registry.ModFluids;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import io.github.thomasjoleary.toxicsurface.registry.ModRecipes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Mod entrypoint. This is Phase 1 scaffolding (DESIGN.md §5) — registries and the
 * server config spec are wired up; gameplay systems land in later phases.
 */
@Mod(ToxicSurface.MODID)
public final class ToxicSurface {
    public static final String MODID = "toxicsurface";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ToxicSurface(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModArmorMaterials.ARMOR_MATERIALS.register(modEventBus);
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);

        // Server config — server-authoritative and synced in multiplayer (DESIGN.md §3, §4).
        modContainer.registerConfig(ModConfig.Type.SERVER, ToxicSurfaceConfig.SPEC);

        LOGGER.info("ToxicSurface initializing — see DESIGN.md for the full spec");
    }
}
