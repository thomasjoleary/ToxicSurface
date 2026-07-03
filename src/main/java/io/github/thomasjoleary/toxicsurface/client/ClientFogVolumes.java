// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.network.FogVolumesPayload;
import java.util.List;

/**
 * Client-side cache of the nearby Cleanser bubbles and generator smog clouds, set from
 * {@link FogVolumesPayload}. Stored as flat {@code {x, y, z, r}} float arrays so
 * {@link ToxicGasFogRenderer} can hand them straight to the fog shader's uniform arrays without
 * per-frame allocation. Free of client-only types so the common network registration can reference it.
 */
public final class ClientFogVolumes {
    /** Packed {@code [x0,y0,z0,r0, x1,...]} of length {@code 4 * FogVolumesPayload.MAX}. */
    private static volatile float[] cleanser = new float[FogVolumesPayload.MAX * 4];

    private static volatile int cleanserCount = 0;
    private static volatile float[] smog = new float[FogVolumesPayload.MAX * 4];
    private static volatile int smogCount = 0;

    private ClientFogVolumes() {}

    public static void set(FogVolumesPayload payload) {
        cleanser = pack(payload.cleansers());
        cleanserCount = Math.min(payload.cleansers().size(), FogVolumesPayload.MAX);
        smog = pack(payload.smog());
        smogCount = Math.min(payload.smog().size(), FogVolumesPayload.MAX);
    }

    private static float[] pack(List<FogVolumesPayload.Sphere> spheres) {
        float[] out = new float[FogVolumesPayload.MAX * 4];
        int n = Math.min(spheres.size(), FogVolumesPayload.MAX);
        for (int i = 0; i < n; i++) {
            FogVolumesPayload.Sphere s = spheres.get(i);
            out[i * 4] = s.x();
            out[i * 4 + 1] = s.y();
            out[i * 4 + 2] = s.z();
            out[i * 4 + 3] = s.r();
        }
        return out;
    }

    public static float[] cleanser() {
        return cleanser;
    }

    public static int cleanserCount() {
        return cleanserCount;
    }

    public static float[] smog() {
        return smog;
    }

    public static int smogCount() {
        return smogCount;
    }
}
