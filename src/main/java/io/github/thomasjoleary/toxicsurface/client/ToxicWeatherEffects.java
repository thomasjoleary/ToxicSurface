// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.thomasjoleary.toxicsurface.registry.ModParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Overworld weather effects that recolour rain green while the viewer is below the toxic ceiling
 * (DESIGN.md §3). Registered in place of the vanilla overworld effects via
 * {@code RegisterDimensionSpecialEffectsEvent}. Both hooks bow out (return {@code false} → vanilla
 * renders normal blue rain) unless the camera is below the synced ceiling Y, it is raining, and no
 * shader pack is active; otherwise they reproduce vanilla's rain rendering with a toxic-green tint.
 *
 * <p>The streak/particle code is intentionally a faithful copy of {@code LevelRenderer}'s rain logic
 * (only the colour and the splash particle differ), so the blue↔green transition is seamless as the
 * player moves across the gas line.
 */
public class ToxicWeatherEffects extends DimensionSpecialEffects.OverworldEffects {
    private static final ResourceLocation RAIN_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/environment/rain.png");
    private static final ResourceLocation SNOW_LOCATION =
            ResourceLocation.withDefaultNamespace("textures/environment/snow.png");

    // Toxic-green tint multiplied over the (desaturated) rain streak texture.
    private static final float GREEN_R = 0.42f;
    private static final float GREEN_G = 0.95f;
    private static final float GREEN_B = 0.30f;

    // Vanilla's precomputed 32×32 streak-skew spiral (LevelRenderer constructor).
    private static final float[] RAIN_SIZE_X = new float[1024];
    private static final float[] RAIN_SIZE_Z = new float[1024];

    static {
        for (int i = 0; i < 32; i++) {
            for (int j = 0; j < 32; j++) {
                float f = (float) (j - 16);
                float f1 = (float) (i - 16);
                float f2 = Mth.sqrt(f * f + f1 * f1);
                RAIN_SIZE_X[i << 5 | j] = -f1 / f2;
                RAIN_SIZE_Z[i << 5 | j] = f / f2;
            }
        }
    }

    private int rainSoundTime;

    /** Whether the toxic-rain tint applies for a camera at {@code camY}: below the gas line, no shaders. */
    private static boolean toxicRain(double camY) {
        int ceiling = ClientGasState.toxicCeilingY();
        return ceiling != Integer.MIN_VALUE && camY < ceiling && !ShaderState.shadersActive();
    }

    @Override
    public boolean renderSnowAndRain(
            ClientLevel level,
            int ticks,
            float partialTick,
            LightTexture lightTexture,
            double camX,
            double camY,
            double camZ) {
        if (!toxicRain(camY)) {
            return false; // above the gas line / not toxic / shaders on → vanilla draws normal rain
        }
        float f = level.getRainLevel(partialTick);
        if (f <= 0.0F) {
            return false;
        }
        lightTexture.turnOnLightLayer();
        int i = Mth.floor(camX);
        int j = Mth.floor(camY);
        int k = Mth.floor(camZ);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = null;
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        int l = Minecraft.useFancyGraphics() ? 10 : 5;
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        int i1 = -1;
        float f1 = (float) ticks + partialTick;
        RenderSystem.setShader(GameRenderer::getParticleShader);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int j1 = k - l; j1 <= k + l; j1++) {
            for (int k1 = i - l; k1 <= i + l; k1++) {
                int l1 = (j1 - k + 16) * 32 + k1 - i + 16;
                double d0 = (double) RAIN_SIZE_X[l1] * 0.5;
                double d1 = (double) RAIN_SIZE_Z[l1] * 0.5;
                blockpos$mutableblockpos.set((double) k1, camY, (double) j1);
                Biome biome = level.getBiome(blockpos$mutableblockpos).value();
                if (biome.hasPrecipitation()) {
                    int i2 = level.getHeight(Heightmap.Types.MOTION_BLOCKING, k1, j1);
                    int j2 = j - l;
                    int k2 = j + l;
                    if (j2 < i2) {
                        j2 = i2;
                    }
                    if (k2 < i2) {
                        k2 = i2;
                    }
                    int l2 = Math.max(i2, j);
                    if (j2 != k2) {
                        RandomSource randomsource = RandomSource.create(
                                (long) (k1 * k1 * 3121 + k1 * 45238971 ^ j1 * j1 * 418711 + j1 * 13761));
                        blockpos$mutableblockpos.set(k1, j2, j1);
                        Biome.Precipitation biome$precipitation = biome.getPrecipitationAt(blockpos$mutableblockpos);
                        if (biome$precipitation == Biome.Precipitation.RAIN) {
                            if (i1 != 0) {
                                if (i1 >= 0) {
                                    BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
                                }
                                i1 = 0;
                                RenderSystem.setShaderTexture(0, RAIN_LOCATION);
                                bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                            }
                            int i3 = ticks & 131071;
                            int j3 = k1 * k1 * 3121 + k1 * 45238971 + j1 * j1 * 418711 + j1 * 13761 & 0xFF;
                            float f2 = 3.0F + randomsource.nextFloat();
                            float f3 = -((float) (i3 + j3) + partialTick) / 32.0F * f2;
                            float f4 = f3 % 32.0F;
                            double d2 = (double) k1 + 0.5 - camX;
                            double d3 = (double) j1 + 0.5 - camZ;
                            float f6 = (float) Math.sqrt(d2 * d2 + d3 * d3) / (float) l;
                            float f7 = ((1.0F - f6 * f6) * 0.5F + 0.5F) * f;
                            blockpos$mutableblockpos.set(k1, l2, j1);
                            int k3 = LevelRenderer.getLightColor(level, blockpos$mutableblockpos);
                            bufferbuilder
                                    .addVertex(
                                            (float) ((double) k1 - camX - d0 + 0.5),
                                            (float) ((double) k2 - camY),
                                            (float) ((double) j1 - camZ - d1 + 0.5))
                                    .setUv(0.0F, (float) j2 * 0.25F + f4)
                                    .setColor(GREEN_R, GREEN_G, GREEN_B, f7)
                                    .setLight(k3);
                            bufferbuilder
                                    .addVertex(
                                            (float) ((double) k1 - camX + d0 + 0.5),
                                            (float) ((double) k2 - camY),
                                            (float) ((double) j1 - camZ + d1 + 0.5))
                                    .setUv(1.0F, (float) j2 * 0.25F + f4)
                                    .setColor(GREEN_R, GREEN_G, GREEN_B, f7)
                                    .setLight(k3);
                            bufferbuilder
                                    .addVertex(
                                            (float) ((double) k1 - camX + d0 + 0.5),
                                            (float) ((double) j2 - camY),
                                            (float) ((double) j1 - camZ + d1 + 0.5))
                                    .setUv(1.0F, (float) k2 * 0.25F + f4)
                                    .setColor(GREEN_R, GREEN_G, GREEN_B, f7)
                                    .setLight(k3);
                            bufferbuilder
                                    .addVertex(
                                            (float) ((double) k1 - camX - d0 + 0.5),
                                            (float) ((double) j2 - camY),
                                            (float) ((double) j1 - camZ - d1 + 0.5))
                                    .setUv(0.0F, (float) k2 * 0.25F + f4)
                                    .setColor(GREEN_R, GREEN_G, GREEN_B, f7)
                                    .setLight(k3);
                        } else if (biome$precipitation == Biome.Precipitation.SNOW) {
                            if (i1 != 1) {
                                if (i1 >= 0) {
                                    BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
                                }
                                i1 = 1;
                                RenderSystem.setShaderTexture(0, SNOW_LOCATION);
                                bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
                            }
                            float f8 = -((float) (ticks & 511) + partialTick) / 512.0F;
                            float f9 = (float) (randomsource.nextDouble()
                                    + (double) f1 * 0.01 * (double) ((float) randomsource.nextGaussian()));
                            float f10 = (float) (randomsource.nextDouble()
                                    + (double) (f1 * (float) randomsource.nextGaussian()) * 0.001);
                            double d4 = (double) k1 + 0.5 - camX;
                            double d5 = (double) j1 + 0.5 - camZ;
                            float f11 = (float) Math.sqrt(d4 * d4 + d5 * d5) / (float) l;
                            float f5 = ((1.0F - f11 * f11) * 0.3F + 0.5F) * f;
                            blockpos$mutableblockpos.set(k1, l2, j1);
                            int j4 = LevelRenderer.getLightColor(level, blockpos$mutableblockpos);
                            int k4 = j4 >> 16 & 65535;
                            int l4 = j4 & 65535;
                            int l3 = (k4 * 3 + 240) / 4;
                            int i4 = (l4 * 3 + 240) / 4;
                            bufferbuilder
                                    .addVertex(
                                            (float) ((double) k1 - camX - d0 + 0.5),
                                            (float) ((double) k2 - camY),
                                            (float) ((double) j1 - camZ - d1 + 0.5))
                                    .setUv(0.0F + f9, (float) j2 * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                            bufferbuilder
                                    .addVertex(
                                            (float) ((double) k1 - camX + d0 + 0.5),
                                            (float) ((double) k2 - camY),
                                            (float) ((double) j1 - camZ + d1 + 0.5))
                                    .setUv(1.0F + f9, (float) j2 * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                            bufferbuilder
                                    .addVertex(
                                            (float) ((double) k1 - camX + d0 + 0.5),
                                            (float) ((double) j2 - camY),
                                            (float) ((double) j1 - camZ + d1 + 0.5))
                                    .setUv(1.0F + f9, (float) k2 * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                            bufferbuilder
                                    .addVertex(
                                            (float) ((double) k1 - camX - d0 + 0.5),
                                            (float) ((double) j2 - camY),
                                            (float) ((double) j1 - camZ - d1 + 0.5))
                                    .setUv(0.0F + f9, (float) k2 * 0.25F + f8 + f10)
                                    .setColor(1.0F, 1.0F, 1.0F, f5)
                                    .setUv2(i4, l3);
                        }
                    }
                }
            }
        }

        if (i1 >= 0) {
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        lightTexture.turnOffLightLayer();
        return true;
    }

    @Override
    public boolean tickRain(ClientLevel level, int ticks, Camera camera) {
        if (!toxicRain(camera.getPosition().y)) {
            return false; // vanilla spawns its normal blue splashes
        }
        Minecraft minecraft = Minecraft.getInstance();
        float f = level.getRainLevel(1.0F) / (Minecraft.useFancyGraphics() ? 1.0F : 2.0F);
        if (f <= 0.0F) {
            return false;
        }
        RandomSource randomsource = RandomSource.create((long) ticks * 312987231L);
        LevelReader levelreader = level;
        BlockPos blockpos = BlockPos.containing(camera.getPosition());
        BlockPos blockpos1 = null;
        int i = (int) (100.0F * f * f) / (minecraft.options.particles().get() == ParticleStatus.DECREASED ? 2 : 1);

        for (int j = 0; j < i; j++) {
            int k = randomsource.nextInt(21) - 10;
            int l = randomsource.nextInt(21) - 10;
            BlockPos blockpos2 = levelreader.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockpos.offset(k, 0, l));
            if (blockpos2.getY() > levelreader.getMinBuildHeight()
                    && blockpos2.getY() <= blockpos.getY() + 10
                    && blockpos2.getY() >= blockpos.getY() - 10) {
                Biome biome = levelreader.getBiome(blockpos2).value();
                if (biome.getPrecipitationAt(blockpos2) == Biome.Precipitation.RAIN) {
                    blockpos1 = blockpos2.below();
                    if (minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
                        break;
                    }
                    double d0 = randomsource.nextDouble();
                    double d1 = randomsource.nextDouble();
                    BlockState blockstate = levelreader.getBlockState(blockpos1);
                    FluidState fluidstate = levelreader.getFluidState(blockpos1);
                    VoxelShape voxelshape = blockstate.getCollisionShape(levelreader, blockpos1);
                    double d2 = voxelshape.max(Direction.Axis.Y, d0, d1);
                    double d3 = (double) fluidstate.getHeight(levelreader, blockpos1);
                    double d4 = Math.max(d2, d3);
                    ParticleOptions particleoptions = !fluidstate.is(FluidTags.LAVA)
                                    && !blockstate.is(Blocks.MAGMA_BLOCK)
                                    && !CampfireBlock.isLitCampfire(blockstate)
                            ? ModParticles.TOXIC_RAIN_SPLASH.get() // green splash below the gas line
                            : ParticleTypes.SMOKE;
                    level.addParticle(
                            particleoptions,
                            (double) blockpos1.getX() + d0,
                            (double) blockpos1.getY() + d4,
                            (double) blockpos1.getZ() + d1,
                            0.0,
                            0.0,
                            0.0);
                }
            }
        }

        if (blockpos1 != null && randomsource.nextInt(3) < this.rainSoundTime++) {
            this.rainSoundTime = 0;
            if (blockpos1.getY() > blockpos.getY() + 1
                    && levelreader
                                    .getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, blockpos)
                                    .getY()
                            > Mth.floor((float) blockpos.getY())) {
                level.playLocalSound(blockpos1, SoundEvents.WEATHER_RAIN_ABOVE, SoundSource.WEATHER, 0.1F, 0.5F, false);
            } else {
                level.playLocalSound(blockpos1, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.2F, 1.0F, false);
            }
        }
        return true;
    }
}
