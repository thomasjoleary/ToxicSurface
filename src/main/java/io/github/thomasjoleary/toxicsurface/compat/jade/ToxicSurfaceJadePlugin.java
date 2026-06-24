// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.jade;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade integration entry point (DESIGN.md §5 Phase 8). Registers one {@link MachineReadoutProvider}
 * for both halves of Jade's contract: the server-side data provider (keyed to {@link JadeReadout} so
 * only our machines are queried) and the client-side tooltip component. Loaded by Jade only when
 * Jade is present (it scans for {@link WailaPlugin}), so nothing here is classloaded in the
 * standalone jar.
 */
@WailaPlugin(ToxicSurface.MODID)
public class ToxicSurfaceJadePlugin implements IWailaPlugin {
    private static final MachineReadoutProvider PROVIDER = new MachineReadoutProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        // Server-side: harvest live state from any block entity implementing JadeReadout.
        registration.registerBlockDataProvider(PROVIDER, JadeReadout.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // Client-side: the tooltip lines. Registered broadly (Block), but it only draws when our
        // server data is present, which shouldRequestData limits to JadeReadout machines.
        registration.registerBlockComponent(PROVIDER, Block.class);
    }
}
