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
 * toxic gas ({@code inGas}, drives the fog), their toxic air bar as a {@code 0..1} fraction
 * ({@code air}, drives the HUD bubble row), and whether they stand in toxic open air regardless of
 * protection ({@code inToxicArea}, drives the toxic-rain overlay so a masked player still sees it),
 * and the dimension's current toxic ceiling Y ({@code toxicCeilingY}, or {@code Integer.MIN_VALUE}
 * when not yet toxic) so the client can colour rain green below the gas line and blue above it.
 * {@code minFogDistance} is how far along the player's view direction actual exposed gas was found
 * (via {@link io.github.thomasjoleary.toxicsurface.effect.GasVisibilityRay}), so the volumetric fog
 * shader can skip hazing up a sealed room's own interior even though its walls sit below the ceiling.
 * All resolved server-side (ceiling + sealing + mask/suit state); the client only renders from them.
 */
public record GasStatePayload(boolean inGas, float air, boolean inToxicArea, int toxicCeilingY, float minFogDistance)
        implements CustomPacketPayload {
    public static final Type<GasStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "gas_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            GasStatePayload::inGas,
            ByteBufCodecs.FLOAT,
            GasStatePayload::air,
            ByteBufCodecs.BOOL,
            GasStatePayload::inToxicArea,
            ByteBufCodecs.INT,
            GasStatePayload::toxicCeilingY,
            ByteBufCodecs.FLOAT,
            GasStatePayload::minFogDistance,
            GasStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
