// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import com.mojang.serialization.Codec;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.item.MaskData;
import io.github.thomasjoleary.toxicsurface.item.SuitData;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom Data Components (DESIGN.md §3). */
public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(ToxicSurface.MODID);

    public static final Supplier<DataComponentType<MaskData>> MASK_DATA = DATA_COMPONENTS.registerComponentType(
            "mask_data", builder -> builder.persistent(MaskData.CODEC).networkSynchronized(MaskData.STREAM_CODEC));

    public static final Supplier<DataComponentType<SuitData>> SUIT_DATA = DATA_COMPONENTS.registerComponentType(
            "suit_data", builder -> builder.persistent(SuitData.CODEC).networkSynchronized(SuitData.STREAM_CODEC));

    /**
     * Remaining clean-burn ticks on an industrial filter (DESIGN.md §7). Absent on a fresh filter,
     * which reads as full; counts down while scrubbing a generator and drives its durability bar.
     */
    public static final Supplier<DataComponentType<Integer>> INDUSTRIAL_FILTER_LIFE =
            DATA_COMPONENTS.registerComponentType("industrial_filter_life", builder -> builder.persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {}
}
