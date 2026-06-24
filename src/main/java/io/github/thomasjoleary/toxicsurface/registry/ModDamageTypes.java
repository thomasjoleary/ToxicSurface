// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/**
 * The mod's custom {@code toxic} damage type (DESIGN.md §3) — the source for lethal toxic-air,
 * sludge, and toxic-airflow damage, so deaths read "succumbed to the toxic air" instead of a generic
 * magic death. The {@link DamageType} itself is data-driven (see
 * {@code data/toxicsurface/damage_type/toxic.json}); this only holds the key and a helper to resolve
 * the {@link DamageSource} from a level's registry access.
 */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> TOXIC = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "toxic"));

    private ModDamageTypes() {}

    /** A {@code toxic} damage source (no attacker — environmental). */
    public static DamageSource toxic(Level level) {
        return new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TOXIC));
    }
}
