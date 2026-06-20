// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data-driven armor material for the hazmat suit (DESIGN.md §3 Hazmat suit). */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, ToxicSurface.MODID);

    public static final Holder<ArmorMaterial> HAZMAT = ARMOR_MATERIALS.register(
            "hazmat",
            () -> new ArmorMaterial(
                    defense(),
                    9, // enchantability (iron-tier)
                    SoundEvents.ARMOR_EQUIP_IRON,
                    () -> Ingredient.of(ModItems.HAZMAT_MATERIAL.get()),
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "hazmat"))),
                    1.0F, // toughness
                    0.0F)); // knockback resistance

    private static Map<ArmorItem.Type, Integer> defense() {
        EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.BOOTS, 2);
        defense.put(ArmorItem.Type.LEGGINGS, 5);
        defense.put(ArmorItem.Type.CHESTPLATE, 6);
        defense.put(ArmorItem.Type.HELMET, 2);
        return defense;
    }

    private ModArmorMaterials() {}
}
