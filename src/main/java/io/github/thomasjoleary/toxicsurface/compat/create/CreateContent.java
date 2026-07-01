// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.registry.ModCreativeTabs;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

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

    // --- Toxic generators (DESIGN.md §7): Create kinetic sources that burn toxic waste/sludge. ---

    public static final DeferredBlock<WasteGeneratorBlock> WASTE_GENERATOR = BLOCKS.register(
            "waste_generator",
            () -> new WasteGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(3.5F)
                    .sound(SoundType.METAL)));

    public static final Supplier<BlockEntityType<WasteGeneratorBlockEntity>> WASTE_GENERATOR_BE =
            BLOCK_ENTITIES.register("waste_generator", () -> BlockEntityType.Builder.of(
                            WasteGeneratorBlockEntity::new, WASTE_GENERATOR.get())
                    .build(null));

    public static final DeferredItem<BlockItem> WASTE_GENERATOR_ITEM =
            ITEMS.register("waste_generator", () -> new BlockItem(WASTE_GENERATOR.get(), new Item.Properties()));

    public static final DeferredBlock<SludgeGeneratorBlock> SLUDGE_GENERATOR = BLOCKS.register(
            "sludge_generator",
            () -> new SludgeGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GREEN)
                    .strength(3.5F)
                    .sound(SoundType.METAL)));

    public static final Supplier<BlockEntityType<SludgeGeneratorBlockEntity>> SLUDGE_GENERATOR_BE =
            BLOCK_ENTITIES.register("sludge_generator", () -> BlockEntityType.Builder.of(
                            SludgeGeneratorBlockEntity::new, SLUDGE_GENERATOR.get())
                    .build(null));

    public static final DeferredItem<BlockItem> SLUDGE_GENERATOR_ITEM =
            ITEMS.register("sludge_generator", () -> new BlockItem(SLUDGE_GENERATOR.get(), new Item.Properties()));

    private CreateContent() {}

    /** Attaches the Create-only registries to the mod bus. Call only when Create is loaded. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(CreateContent::addToCreativeTab);
        modBus.addListener(CreateContent::registerCapabilities);
        modBus.addListener(CreateContent::registerFanProcessing);
        // Let the gas check seal rooms inside moving contraptions (DESIGN.md §9).
        io.github.thomasjoleary.toxicsurface.effect.ContraptionSeal.setImpl(ContraptionSealing::isSealedInContraption);
        // Client-only rendering (the Mechanical Weaver's in-world depot/sticks). Gated on dist so the
        // renderer classes never load on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            CreateClientContent.registerClient(modBus);
        }
    }

    /**
     * Registers the sludge fan-processing type into Create's built-in registry: fans blowing
     * through sludge re-dirty clean filters (the inverse of water splashing). Done via
     * {@link RegisterEvent} so we don't need a typed DeferredRegister for Create's registry.
     */
    private static void registerFanProcessing(RegisterEvent event) {
        event.register(
                CreateBuiltInRegistries.FAN_PROCESSING_TYPE.key(),
                ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "sludge_contaminating"),
                SludgeFanProcessingType::new);
    }

    /**
     * Exposes machine I/O so hoppers/Create pipes can automate them: the Mechanical Weaver's and
     * Waste Generator's item inventories, and the Sludge Generator's fluid tank (so Create pumps
     * fill it directly). The Weaver exposes its restricted external handler here — automation can
     * insert into the inputs but only extract the finished output, not the in-progress inputs
     * (DESIGN.md §7, matches Create's own processing machines).
     */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                MECHANICAL_WEAVER_BE.get(),
                (weaver, side) -> weaver.getExternalItemHandler());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, WASTE_GENERATOR_BE.get(), (gen, side) -> gen.getItemHandler());
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK, SLUDGE_GENERATOR_BE.get(), (gen, side) -> gen.getFluidHandler());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, SLUDGE_GENERATOR_BE.get(), (gen, side) -> gen.getItemHandler());
    }

    /** Surfaces the Create machines in the mod's creative tab (only when Create is loaded). */
    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ModCreativeTabs.MAIN.getKey()) {
            event.accept(MECHANICAL_WEAVER_ITEM.get());
            event.accept(MECHANICAL_CLEANSER_ITEM.get());
            event.accept(WASTE_GENERATOR_ITEM.get());
            event.accept(SLUDGE_GENERATOR_ITEM.get());
        }
    }
}
