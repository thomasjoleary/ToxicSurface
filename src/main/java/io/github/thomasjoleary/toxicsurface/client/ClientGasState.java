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
    /** In toxic open air regardless of protection — drives the toxic-rain overlay. */
    private static volatile boolean inToxicArea = false;
    /** Dimension's current toxic ceiling Y, or {@link Integer#MIN_VALUE} when not yet toxic. */
    private static volatile int toxicCeilingY = Integer.MIN_VALUE;
    /** How far along the view direction actual exposed gas was found; drives the volumetric haze. */
    private static volatile float minFogDistance = 0f;

    private ClientGasState() {}

    public static void set(
            boolean inGasValue,
            float airValue,
            boolean inToxicAreaValue,
            int toxicCeilingYValue,
            float minFogDistanceValue) {
        inGas = inGasValue;
        air = airValue;
        inToxicArea = inToxicAreaValue;
        toxicCeilingY = toxicCeilingYValue;
        minFogDistance = minFogDistanceValue;
    }

    /** The dimension's toxic ceiling Y; {@link Integer#MIN_VALUE} means not toxic (rain stays normal). */
    public static int toxicCeilingY() {
        return toxicCeilingY;
    }

    /** Distance along the player's view direction confirmed safe of exposed gas (see {@code GasVisibilityRay}). */
    public static float minFogDistance() {
        return minFogDistance;
    }

    public static boolean isInGas() {
        return inGas;
    }

    /** The local player's toxic air bar, 0 (empty) to 1 (full). */
    public static float air() {
        return air;
    }

    /** Whether the local player is in toxic open air (protection-independent). */
    public static boolean isInToxicArea() {
        return inToxicArea;
    }
}
