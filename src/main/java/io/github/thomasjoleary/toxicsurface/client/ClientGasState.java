// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

/**
 * Client-side cache of the local player's toxic-gas exposure and air bar, set from
 * {@link io.github.thomasjoleary.toxicsurface.network.GasStatePayload}. Deliberately free of
 * client-only types so it is safe to reference from the common network registration (it is only
 * ever mutated on the client).
 */
public final class ClientGasState {
    private static volatile boolean inGas = false;
    /** Toxic air bar as a 0..1 fraction; starts full so the HUD bubble row is hidden by default. */
    private static volatile float air = 1.0f;

    private ClientGasState() {}

    public static void set(boolean inGasValue, float airValue) {
        inGas = inGasValue;
        air = airValue;
    }

    public static boolean isInGas() {
        return inGas;
    }

    /** The local player's toxic air bar, 0 (empty) to 1 (full). */
    public static float air() {
        return air;
    }
}
