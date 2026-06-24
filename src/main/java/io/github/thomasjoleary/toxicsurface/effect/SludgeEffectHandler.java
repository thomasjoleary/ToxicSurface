// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.effect;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.item.HazmatSuit;
import io.github.thomasjoleary.toxicsurface.registry.ModDamageTypes;
import io.github.thomasjoleary.toxicsurface.registry.ModFluids;
import io.github.thomasjoleary.toxicsurface.registry.ModTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Contact hazards for entities inside toxic sludge (DESIGN.md §3 Toxic sludge).
 * Living entities take periodic damage plus Poison; organic item entities are
 * consumed. Drowning is handled by the fluid itself ({@code canDrown}). Runs
 * server-side only.
 *
 * <p>A full hazmat suit negates the contact damage (you still drown). A custom toxic
 * damage type (Phase 2 polish) is still stubbed.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID)
public final class SludgeEffectHandler {
    private SludgeEffectHandler() {}

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || !entity.isInFluidType(ModFluids.SLUDGE_TYPE.get())) {
            return;
        }

        if (entity instanceof ItemEntity item) {
            if (isOrganic(item.getItem())) {
                item.discard(); // dissolved by the sludge
            }
            return;
        }

        if (entity instanceof LivingEntity living) {
            // A full hazmat suit negates sludge contact damage — but you still drown (§3).
            if (living instanceof Player player && HazmatSuit.isFullSuit(player)) {
                return;
            }
            int interval = ToxicSurfaceConfig.SLUDGE_INTERVAL_TICKS.get();
            if (living.tickCount % interval == 0) {
                float damage = (float) (double) ToxicSurfaceConfig.SLUDGE_DAMAGE.get();
                living.hurt(ModDamageTypes.toxic(living.level()), damage);
                living.addEffect(new MobEffectInstance(MobEffects.POISON, interval + 20, 0));
            }
        }
    }

    private static boolean isOrganic(ItemStack stack) {
        return stack.has(DataComponents.FOOD)
                || stack.is(ItemTags.LOGS)
                || stack.is(ItemTags.LEAVES)
                || stack.is(ModTags.Items.ORGANIC);
    }
}
