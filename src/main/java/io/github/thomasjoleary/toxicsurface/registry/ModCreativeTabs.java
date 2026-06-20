// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Creative-mode tab grouping all ToxicSurface content. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ToxicSurface.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.toxicsurface"))
                    .icon(() -> new ItemStack(ModItems.CLEAN_AIR_FILTER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CLEAN_AIR_FILTER.get());
                        output.accept(ModItems.USED_AIR_FILTER.get());
                        output.accept(ModItems.FACE_MASK.get());
                        output.accept(ModItems.HAZMAT_MATERIAL.get());
                        output.accept(ModItems.HAZMAT_HELMET.get());
                        output.accept(ModItems.HAZMAT_CHESTPLATE.get());
                        output.accept(ModItems.HAZMAT_LEGGINGS.get());
                        output.accept(ModItems.HAZMAT_BOOTS.get());
                        output.accept(ModItems.SLUDGE_BUCKET.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
