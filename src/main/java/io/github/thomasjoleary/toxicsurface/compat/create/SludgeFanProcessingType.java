// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.effect.GasProtection;
import io.github.thomasjoleary.toxicsurface.registry.ModFluids;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A custom Create fan-processing type (DESIGN.md §3/§7): when an encased fan blows air through
 * toxic sludge, the contaminated airflow re-soils clean air filters passing through it, turning
 * them into used filters. It is the fan-driven inverse of Create's water "splashing" wash —
 * blow through water to clean a filter, blow through sludge to dirty one. Registered to Create's
 * {@code FAN_PROCESSING_TYPE} registry only when Create is present (see {@link CreateContent}),
 * so this class is never loaded in the standalone jar.
 */
public class SludgeFanProcessingType implements FanProcessingType {
    @Override
    public boolean isValidAt(Level level, BlockPos pos) {
        // Matches both the source and flowing sludge fluids via their shared fluid type.
        return level.getFluidState(pos).getFluidType() == ModFluids.SLUDGE_TYPE.get();
    }

    @Override
    public int getPriority() {
        // Same band as splashing; sludge and water never occupy the same block, so order is moot.
        return 400;
    }

    @Override
    public boolean canProcess(ItemStack stack, Level level) {
        return stack.is(ModItems.CLEAN_AIR_FILTER.get());
    }

    @Override
    public List<ItemStack> process(ItemStack stack, Level level) {
        return List.of(new ItemStack(ModItems.USED_AIR_FILTER.get(), stack.getCount()));
    }

    @Override
    public void spawnProcessingParticles(Level level, Vec3 pos) {
        // Visual polish deferred, matching the rest of the mod's machines (DESIGN deferred TODO).
    }

    @Override
    public void morphAirFlow(AirFlowParticleAccess particleAccess, RandomSource random) {
        particleAccess.setColor(0xFF4A5D23); // murky toxic-green tint on the fan's airflow
    }

    @Override
    public void affectEntity(Entity entity, Level level) {
        // Create calls this server-side for entities caught in the airflow (the same hook lava
        // uses to set them on fire). Sludge airflow hits them like standing in toxic gas.
        if (level.isClientSide || !(entity instanceof LivingEntity living)) {
            return;
        }
        // Protected / creative / spectator players breathe easy — same rules as the gas (§3).
        if (entity instanceof Player player
                && (player.isCreative() || player.isSpectator() || GasProtection.isProtected(player))) {
            return;
        }
        living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false));
        // Throttle the toxic damage to match the gas's lethal rate (toxicDamagePerSecond).
        if (living.tickCount % 10 == 0) {
            float perHalfSecond = (float) (ToxicSurfaceConfig.TOXIC_DAMAGE_PER_SECOND.get() * 0.5);
            living.hurt(living.damageSources().magic(), perHalfSecond);
        }
    }
}
