// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.jade;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The single Jade provider for all ToxicSurface machines (DESIGN.md §5 Phase 8). On the server it
 * harvests live state from any {@link JadeReadout} block entity into Jade's per-block data tag; on
 * the client it renders those primitives as tooltip lines. Because it reads the synced NBT rather
 * than casting to a block-entity type, it shows the Create-gated generators without ever referencing
 * {@code compat.create}.
 */
public class MachineReadoutProvider implements IComponentProvider<BlockAccessor>, IServerDataProvider<BlockAccessor> {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ToxicSurface.MODID, "machine");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    /** Only round-trip server data for our own machines, not every block the player looks at. */
    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return accessor.getBlockEntity() instanceof JadeReadout;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof JadeReadout readout) {
            readout.appendJadeData(data);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag d = accessor.getServerData();
        if (d.contains("tsRange")) {
            tooltip.add(line("range", d.getInt("tsRange")));
        }
        if (d.contains("tsActive")) {
            tooltip.add(plain(d.getBoolean("tsActive") ? "active" : "idle"));
        }
        if (d.contains("tsWeave")) {
            tooltip.add(line("weaving", d.getInt("tsWeave")));
        }
        if (d.contains("tsRunning")) {
            tooltip.add(plain(d.getBoolean("tsRunning") ? "generating" : "stopped"));
        }
        if (d.contains("tsRpm")) {
            tooltip.add(line("rpm", d.getInt("tsRpm")));
        }
        if (d.contains("tsScrub")) {
            tooltip.add(plain(d.getInt("tsScrub") == 1 ? "scrub_clean" : "scrub_venting"));
        }
        if (d.contains("tsSludge")) {
            tooltip.add(line("sludge", d.getInt("tsSludge")));
        }
        if (d.contains("tsFuel")) {
            tooltip.add(line("fuel", d.getInt("tsFuel")));
        }
    }

    private static Component line(String key, int value) {
        return Component.translatable("jade." + ToxicSurface.MODID + "." + key, value);
    }

    private static Component plain(String key) {
        return Component.translatable("jade." + ToxicSurface.MODID + "." + key);
    }
}
