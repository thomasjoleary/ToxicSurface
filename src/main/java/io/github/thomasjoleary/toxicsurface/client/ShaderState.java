// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

/**
 * Detects whether an Iris/Oculus shader pack is currently active, without a compile-time dependency
 * on Iris. Our custom toxic-rain renderer drives vanilla {@code RenderSystem} directly, which a
 * shader pipeline doesn't account for — so when shaders are on we bow out and let the shader draw
 * weather its own way. Reflection is resolved once and cached; if Iris isn't present this is a
 * cheap constant {@code false}.
 */
public final class ShaderState {
    private static final Method IS_SHADERPACK_IN_USE = resolve();

    private ShaderState() {}

    private static Method resolve() {
        if (!ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus")) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Method inUse = api.getMethod("isShaderPackInUse");
            // Stash the bound instance via a tiny holder isn't needed — invoke statically below.
            SHADER_API = instance;
            return inUse;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object SHADER_API;

    /** True when a shader pack is actively rendering; false if Iris is absent or no pack is on. */
    public static boolean shadersActive() {
        if (IS_SHADERPACK_IN_USE == null) {
            return false;
        }
        try {
            return (boolean) IS_SHADERPACK_IN_USE.invoke(SHADER_API);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
