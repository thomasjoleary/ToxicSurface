// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat;

import net.neoforged.fml.ModList;

/**
 * Soft-dependency gate for Create integration (DESIGN.md §9). The mod must load and play
 * standalone; every Create-touching path is guarded by {@link #isLoaded()} and there are
 * no hard references to Create classes at load time. Datapack integration (fan washing,
 * pipe/tank transport of the sludge fluid) is condition-gated in JSON and needs no code
 * here; this gate is for the runtime-Java paths (e.g. the future Mechanical machines).
 */
public final class CreateCompat {
    private CreateCompat() {}

    /** True when Create is present, so Create-coupled features may activate. */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(Mods.CREATE);
    }
}
