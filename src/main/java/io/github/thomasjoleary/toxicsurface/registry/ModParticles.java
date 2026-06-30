// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Particle registry. {@link #TOXIC_RAIN_SPLASH} is the green counterpart to vanilla's rain splash,
 * spawned by the toxic weather renderer for players below the gas line (DESIGN.md §3). Registered
 * unconditionally (base content); the client-side sprite provider is wired in {@code ClientModBusEvents}.
 */
public final class ModParticles {
    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, ToxicSurface.MODID);

    /** A green rain splash; behaves like vanilla's rain drop but uses the toxic-green sprite. */
    public static final java.util.function.Supplier<SimpleParticleType> TOXIC_RAIN_SPLASH =
            PARTICLE_TYPES.register("toxic_rain_splash", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
