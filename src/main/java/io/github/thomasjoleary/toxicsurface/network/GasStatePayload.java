// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.network;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client state for the receiving player (DESIGN.md §3, §4): whether they are exposed to
 * toxic gas ({@code inGas}, drives the fog) and their toxic air bar as a {@code 0..1} fraction
 * ({@code air}, drives the HUD bubble row). Both are resolved server-side (ceiling + sealing +
 * mask/suit state); the client only renders from them.
 */
public record GasStatePayload(boolean inGas, float air) implements CustomPacketPayload {
    public static final Type<GasStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "gas_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            GasStatePayload::inGas,
            ByteBufCodecs.FLOAT,
            GasStatePayload::air,
            GasStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
