// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.item;

import io.github.thomasjoleary.toxicsurface.menu.HazmatChestMenu;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Hazmat chestpiece (DESIGN.md §3). Sneak + right-click opens its filter inventory so
 * filters can be swapped in and out directly; a normal right-click equips it like any
 * armour.
 */
public class HazmatChestItem extends ArmorItem {
    public HazmatChestItem(Holder<ArmorMaterial> material, Properties properties) {
        super(material, ArmorItem.Type.CHESTPLATE, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inventory, p) -> new HazmatChestMenu(id, inventory, stack),
                        Component.translatable("container.toxicsurface.hazmat_chest")));
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }
}
