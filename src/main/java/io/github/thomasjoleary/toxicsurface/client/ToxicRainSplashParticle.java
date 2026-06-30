// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.WaterDropParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * The toxic-rain ground splash (DESIGN.md §3): identical to vanilla's rain drop in every way —
 * size, gravity, lifetime, bounce/remove behaviour — it simply extends {@link WaterDropParticle}
 * and draws the green-recoloured splash frames instead of the blue ones (the colour lives in the
 * sprite, so no tint is applied). Spawned by {@link ToxicWeatherEffects#tickRain} below the gas line.
 */
public class ToxicRainSplashParticle extends WaterDropParticle {
    private ToxicRainSplashParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    /** Sprite-set provider, registered in {@code ClientModBusEvents}. */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xs,
                double ys,
                double zs) {
            ToxicRainSplashParticle particle = new ToxicRainSplashParticle(level, x, y, z);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}
