// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data Component stored on a face mask (DESIGN.md §3 Filters & masks): the remaining
 * active filter time in ticks and the full lifetime of the installed filter ({@code
 * maxTicks}) so the durability bar scales correctly for longer-life carbon filters. An
 * installed filter starts full; {@code 0} remaining means it is spent (no protection).
 * {@code maxTicks} of {@code 0} means "use the configured plain-filter duration".
 */
public record MaskData(int remainingTicks, int maxTicks) {
    public static final Codec<MaskData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.fieldOf("remaining_ticks").forGetter(MaskData::remainingTicks),
                    Codec.INT.optionalFieldOf("max_ticks", 0).forGetter(MaskData::maxTicks))
            .apply(instance, MaskData::new));

    public static final StreamCodec<ByteBuf, MaskData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MaskData::remainingTicks, ByteBufCodecs.VAR_INT, MaskData::maxTicks, MaskData::new);
}
