// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.network;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client cue fired the moment a mask/suit filter runs out while the player is in toxic gas
 * (DESIGN.md §3 filter-expire warning). A bare marker — the client plays the cough and flashes the
 * HUD; the cough is also played server-side as a positional sound.
 */
public record FilterExpiryPayload() implements CustomPacketPayload {
    public static final FilterExpiryPayload INSTANCE = new FilterExpiryPayload();

    public static final Type<FilterExpiryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "filter_expiry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterExpiryPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
