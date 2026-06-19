// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

/**
 * Client-side cache of the local player's toxic-gas exposure, set from
 * {@link io.github.thomasjoleary.toxicsurface.network.GasStatePayload}. Deliberately
 * free of client-only types so it is safe to reference from the common network
 * registration (it is only ever mutated on the client).
 */
public final class ClientGasState {
    private static volatile boolean inGas = false;

    private ClientGasState() {}

    public static void set(boolean value) {
        inGas = value;
    }

    public static boolean isInGas() {
        return inGas;
    }
}
