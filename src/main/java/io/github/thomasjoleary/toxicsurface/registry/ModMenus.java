// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.menu.HazmatChestMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Container menu types (DESIGN.md §3 Hazmat suit — filter inventory). */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ToxicSurface.MODID);

    public static final Supplier<MenuType<HazmatChestMenu>> HAZMAT_CHEST = MENUS.register(
            "hazmat_chest",
            () -> IMenuTypeExtension.create((id, inventory, data) -> new HazmatChestMenu(id, inventory)));

    private ModMenus() {}
}
