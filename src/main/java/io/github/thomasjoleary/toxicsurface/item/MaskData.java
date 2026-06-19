// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data Component stored on a face mask (DESIGN.md §3 Filters & masks): the remaining
 * active filter time in ticks. An installed clean filter starts full; {@code 0} means
 * the filter is spent (no protection). Shown as the item's durability bar.
 */
public record MaskData(int remainingTicks) {
    public static final Codec<MaskData> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(Codec.INT.fieldOf("remaining_ticks").forGetter(MaskData::remainingTicks))
                    .apply(instance, MaskData::new));

    public static final StreamCodec<ByteBuf, MaskData> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, MaskData::remainingTicks, MaskData::new);
}
