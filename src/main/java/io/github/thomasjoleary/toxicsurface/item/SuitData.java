// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data Component on a hazmat chestpiece (DESIGN.md §3 Hazmat suit): the number of
 * whole filters loaded and the ticks left on the one currently burning.
 */
public record SuitData(int filters, int activeTicks) {
    public static final Codec<SuitData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("filters").forGetter(SuitData::filters),
                    Codec.INT.fieldOf("active_ticks").forGetter(SuitData::activeTicks))
            .apply(instance, SuitData::new));

    public static final StreamCodec<ByteBuf, SuitData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SuitData::filters, ByteBufCodecs.VAR_INT, SuitData::activeTicks, SuitData::new);
}
