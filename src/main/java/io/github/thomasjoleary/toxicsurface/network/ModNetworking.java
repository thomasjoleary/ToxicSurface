// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.network;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.client.ClientGasState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers ToxicSurface's network payloads (DESIGN.md §4). */
@EventBusSubscriber(modid = ToxicSurface.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetworking {
    private static final String PROTOCOL_VERSION = "1";

    private ModNetworking() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        // The handler only runs on the receiving client; ClientGasState carries no
        // client-only types, so referencing it here is server-safe.
        registrar.playToClient(
                GasStatePayload.TYPE,
                GasStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientGasState.set(payload.inGas())));
    }
}
