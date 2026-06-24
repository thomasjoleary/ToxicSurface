// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.registry;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom sound events (DESIGN.md §3). */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, ToxicSurface.MODID);

    /**
     * Cough played when a mask/suit filter expires mid-exposure (DESIGN.md §3 filter-expire warning).
     * Mapped in {@code sounds.json} to a vanilla choke sound as a placeholder until a dedicated
     * {@code cough.ogg} is added (swap the entry to a {@code toxicsurface:cough} file).
     */
    public static final Supplier<SoundEvent> COUGH = SOUND_EVENTS.register(
            "cough",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "cough")));

    private ModSounds() {}
}
