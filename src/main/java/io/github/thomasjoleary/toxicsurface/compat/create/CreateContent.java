// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.registry.ModCreativeTabs;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Create-only content, registered ONLY when Create is present (DESIGN.md §9). The deferred
 * registers live in their own holders (not the base {@code ModBlocks}/{@code ModItems}) and are
 * attached to the mod bus by {@link #register}, which the entrypoint calls behind a
 * {@code CreateCompat.isLoaded()} gate. Because nothing else references this class, it — and the
 * kinetic-API-extending block/BE it points at — is never classloaded in the standalone jar, so
 * the "loads without Create" contract holds.
 */
public final class CreateContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ToxicSurface.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ToxicSurface.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ToxicSurface.MODID);

    public static final DeferredBlock<MechanicalCleanserBlock> MECHANICAL_CLEANSER = BLOCKS.register(
            "mechanical_cleanser",
            () -> new MechanicalCleanserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.METAL)));

    public static final Supplier<BlockEntityType<MechanicalCleanserBlockEntity>> MECHANICAL_CLEANSER_BE =
            BLOCK_ENTITIES.register("mechanical_cleanser", () -> BlockEntityType.Builder.of(
                            MechanicalCleanserBlockEntity::new, MECHANICAL_CLEANSER.get())
                    .build(null));

    public static final DeferredItem<BlockItem> MECHANICAL_CLEANSER_ITEM = ITEMS.register(
            "mechanical_cleanser", () -> new BlockItem(MECHANICAL_CLEANSER.get(), new Item.Properties()));

    public static final DeferredBlock<MechanicalWeaverBlock> MECHANICAL_WEAVER = BLOCKS.register(
            "mechanical_weaver",
            () -> new MechanicalWeaverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.METAL)));

    public static final Supplier<BlockEntityType<MechanicalWeaverBlockEntity>> MECHANICAL_WEAVER_BE =
            BLOCK_ENTITIES.register("mechanical_weaver", () -> BlockEntityType.Builder.of(
                            MechanicalWeaverBlockEntity::new, MECHANICAL_WEAVER.get())
                    .build(null));

    public static final DeferredItem<BlockItem> MECHANICAL_WEAVER_ITEM =
            ITEMS.register("mechanical_weaver", () -> new BlockItem(MECHANICAL_WEAVER.get(), new Item.Properties()));

    private CreateContent() {}

    /** Attaches the Create-only registries to the mod bus. Call only when Create is loaded. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(CreateContent::addToCreativeTab);
        modBus.addListener(CreateContent::registerCapabilities);
    }

    /** Exposes the Mechanical Weaver's inventory so hoppers/Create pipes can automate it. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, MECHANICAL_WEAVER_BE.get(), (weaver, side) -> weaver.getItemHandler());
    }

    /** Surfaces the Create machines in the mod's creative tab (only when Create is loaded). */
    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModCreativeTabs.MAIN.getKey()) {
            event.accept(MECHANICAL_WEAVER_ITEM.get());
            event.accept(MECHANICAL_CLEANSER_ITEM.get());
        }
    }
}
