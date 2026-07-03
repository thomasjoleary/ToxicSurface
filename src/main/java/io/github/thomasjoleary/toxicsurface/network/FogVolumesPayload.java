// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.network;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client sync of the spherical fog volumes near the receiving player (DESIGN.md §3, §7): the
 * Cleanser bubbles that must be <em>carved out</em> of the toxic haze, and the toxic-generator smog
 * clouds that must be <em>added</em> to it even in otherwise-clean air. These regions aren't bounded
 * by real blocks, so the fog shader's ordinary depth/terrain tests can't see them — the volumetric
 * renderer feeds these spheres into the raymarch instead. Sent each gas-tick cycle alongside
 * {@link GasStatePayload}; capped so a pathological count can't bloat the packet or the shader loop.
 */
public record FogVolumesPayload(List<Sphere> cleansers, List<Sphere> smog) implements CustomPacketPayload {
    /** The most spheres of each kind synced/rendered; extras beyond this are dropped. */
    public static final int MAX = 16;

    /** A fog volume: world-space centre and radius (blocks). */
    public record Sphere(float x, float y, float z, float r) {
        static final StreamCodec<ByteBuf, Sphere> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT,
                Sphere::x,
                ByteBufCodecs.FLOAT,
                Sphere::y,
                ByteBufCodecs.FLOAT,
                Sphere::z,
                ByteBufCodecs.FLOAT,
                Sphere::r,
                Sphere::new);
    }

    public static final Type<FogVolumesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "fog_volumes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FogVolumesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, Sphere.STREAM_CODEC, MAX),
            FogVolumesPayload::cleansers,
            ByteBufCodecs.collection(ArrayList::new, Sphere.STREAM_CODEC, MAX),
            FogVolumesPayload::smog,
            FogVolumesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
