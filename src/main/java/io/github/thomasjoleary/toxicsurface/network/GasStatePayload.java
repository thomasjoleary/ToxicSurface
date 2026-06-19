// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.network;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: whether the receiving player is currently in toxic gas
 * (DESIGN.md §3, §4). Exposure is resolved server-side (ceiling + sealing, and
 * later mask/suit state); the client just renders fog from this flag.
 */
public record GasStatePayload(boolean inGas) implements CustomPacketPayload {
    public static final Type<GasStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "gas_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasStatePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, GasStatePayload::inGas, GasStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
