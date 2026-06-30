// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Small, dark-green ground splash for toxic rain (DESIGN.md §3) — the toxic counterpart to vanilla's
 * blue rain drop, spawned by {@link ToxicWeatherEffects#tickRain}. Tuned to be a fine scattered fleck
 * rather than a big bright dot: small quad, short life, a touch of horizontal scatter, dark toxic tint.
 * All look knobs are the constants below.
 */
public class ToxicRainSplashParticle extends TextureSheetParticle {
    private static final float TINT_R = 0.16f; // dark toxic green
    private static final float TINT_G = 0.40f;
    private static final float TINT_B = 0.12f;
    private static final float SIZE = 0.035f; // quad half-size; vanilla rain drops are notably larger
    private static final float SCATTER = 0.6f; // horizontal spread of the fleck

    protected ToxicRainSplashParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.gravity = 0.04F;
        this.xd *= SCATTER;
        this.zd *= SCATTER;
        this.yd = Math.random() * 0.04; // tiny pop off the ground
        this.quadSize = SIZE * (this.random.nextFloat() * 0.5F + 0.75F);
        this.lifetime = 4 + this.random.nextInt(5);
        this.rCol = TINT_R;
        this.gCol = TINT_G;
        this.bCol = TINT_B;
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
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
            return new ToxicRainSplashParticle(level, x, y, z, this.sprites);
        }
    }
}
