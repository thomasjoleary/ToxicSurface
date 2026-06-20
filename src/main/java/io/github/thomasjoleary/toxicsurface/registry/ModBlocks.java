// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.block.CleanserBlock;
import io.github.thomasjoleary.toxicsurface.block.WeaverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block registry: the sludge liquid block and machine blocks (DESIGN.md §3). */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ToxicSurface.MODID);

    public static final DeferredBlock<LiquidBlock> SLUDGE_BLOCK = BLOCKS.register(
            "sludge",
            () -> new LiquidBlock(
                    ModFluids.SLUDGE.get(),
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GREEN)
                            .replaceable()
                            .noCollission()
                            .strength(100.0F)
                            .pushReaction(PushReaction.DESTROY)
                            .noLootTable()
                            .liquid()));

    public static final DeferredBlock<WeaverBlock> WEAVER = BLOCKS.register(
            "weaver",
            () -> new WeaverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<CleanserBlock> CLEANSER = BLOCKS.register(
            "cleanser",
            () -> new CleanserBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F)
                    .sound(SoundType.METAL)));

    private ModBlocks() {}
}
