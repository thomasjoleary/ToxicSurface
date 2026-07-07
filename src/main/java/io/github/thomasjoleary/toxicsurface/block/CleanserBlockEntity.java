// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.core.machine.CleanserRange;
import io.github.thomasjoleary.toxicsurface.menu.CleanserMenu;
import io.github.thomasjoleary.toxicsurface.registry.ModBlockEntities;
import io.github.thomasjoleary.toxicsurface.world.CleanserBubbles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Cleanser (DESIGN.md §3) — a furnace-fuelled reclamation machine that reverts toxic
 * sludge back to water within a sphere. Range is set in the menu; a redstone signal is an
 * optional on-the-fly tier override (see {@link CleanserRange}). Fuel burns faster at
 * larger ranges. The gas-purge bubble (clean breathable air in range) lands in a
 * follow-up increment; this core handles fuel, range control, and sludge reversion.
 */
public class CleanserBlockEntity extends AbstractFueledMachineBlockEntity {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_COUNT = 1;

    public static final int DATA_MENU_RANGE = 2;
    public static final int DATA_EFFECTIVE_RANGE = 3;

    private int menuRange = CleanserRange.BASE_RANGE;
    private int effectiveRange = CleanserRange.BASE_RANGE;
    private int scanCursor;

    public CleanserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLEANSER.get(), pos, state, SLOT_COUNT);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return stack.getBurnTime(null) > 0;
    }

    @Override
    protected int getMachineData(int index) {
        return switch (index) {
            case DATA_MENU_RANGE -> menuRange;
            case DATA_EFFECTIVE_RANGE -> effectiveRange;
            default -> 0;
        };
    }

    @Override
    protected void setMachineData(int index, int value) {
        switch (index) {
            case DATA_MENU_RANGE -> menuRange = value;
            case DATA_EFFECTIVE_RANGE -> effectiveRange = value;
            default -> {}
        }
    }

    /** Sets the menu range, clamped to the configured maximum. */
    public void setMenuRange(int range) {
        menuRange = CleanserRange.clamp(range, ToxicSurfaceConfig.CLEANSER_MAX_RANGE.get());
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CleanserBlockEntity be) {
        boolean changed = false;
        if (be.litTime > 0) {
            be.litTime--;
        }

        int signal = level.getBestNeighborSignal(pos);
        be.effectiveRange = CleanserRange.effectiveRange(
                be.menuRange,
                signal,
                ToxicSurfaceConfig.CLEANSER_TIERS.get(),
                ToxicSurfaceConfig.CLEANSER_MAX_RANGE.get());

        // Only run where there is something to clean: an affected, already-toxic dimension.
        boolean canRun = SludgeReclaimer.canReclaim(level);
        if (!canRun) {
            if (level instanceof ServerLevel sl) {
                CleanserBubbles.remove(sl, pos);
            }
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;

        int cost = Math.max(1, (int) Math.round(
                CleanserRange.fuelCostMultiplier(be.effectiveRange, ToxicSurfaceConfig.CLEANSER_FUEL_EXPONENT.get())));

        if (be.litTime <= 0 && be.consumeFuel(SLOT_FUEL)) {
            changed = true;
        }

        if (be.litTime > 0) {
            // Larger ranges burn through the current charge faster.
            be.litTime = Math.max(0, be.litTime - (cost - 1));
            be.scanCursor = SludgeReclaimer.tickActive(serverLevel, pos, be.effectiveRange, be.scanCursor);
            changed = true;
        } else {
            CleanserBubbles.remove(serverLevel, pos); // out of fuel: the bubble collapses
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    @Override
    public void appendJadeData(CompoundTag tag) {
        tag.putInt("tsRange", effectiveRange);
        tag.putBoolean("tsActive", litTime > 0);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.toxicsurface.cleanser");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CleanserMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("MenuRange", menuRange);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        menuRange = tag.contains("MenuRange") ? tag.getInt("MenuRange") : CleanserRange.BASE_RANGE;
    }
}
