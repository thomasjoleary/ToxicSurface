// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.block.CleanserBlockEntity;
import io.github.thomasjoleary.toxicsurface.block.WeaverBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity types (DESIGN.md §3 Machines). */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ToxicSurface.MODID);

    public static final Supplier<BlockEntityType<WeaverBlockEntity>> WEAVER = BLOCK_ENTITIES.register(
            "weaver", () -> BlockEntityType.Builder.of(WeaverBlockEntity::new, ModBlocks.WEAVER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<CleanserBlockEntity>> CLEANSER = BLOCK_ENTITIES.register(
            "cleanser", () -> BlockEntityType.Builder.of(CleanserBlockEntity::new, ModBlocks.CLEANSER.get())
                    .build(null));

    private ModBlockEntities() {}
}
