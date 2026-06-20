// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.core.machine.CleanserRange;
import io.github.thomasjoleary.toxicsurface.menu.CleanserMenu;
import io.github.thomasjoleary.toxicsurface.registry.ModBlockEntities;
import io.github.thomasjoleary.toxicsurface.registry.ModBlocks;
import io.github.thomasjoleary.toxicsurface.world.CleanserBubbles;
import io.github.thomasjoleary.toxicsurface.world.ToxicityTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The Cleanser (DESIGN.md §3) — a furnace-fuelled reclamation machine that reverts toxic
 * sludge back to water within a sphere. Range is set in the menu; a redstone signal is an
 * optional on-the-fly tier override (see {@link CleanserRange}). Fuel burns faster at
 * larger ranges. The gas-purge bubble (clean breathable air in range) lands in a
 * follow-up increment; this core handles fuel, range control, and sludge reversion.
 */
public class CleanserBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_COUNT = 1;

    public static final int DATA_LIT_TIME = 0;
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_MENU_RANGE = 2;
    public static final int DATA_EFFECTIVE_RANGE = 3;

    /** Cells scanned per active tick for the reversion sweep (bounds the work). */
    private static final int SCAN_BUDGET = 4096;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getBurnTime(null) > 0;
        }
    };

    private int litTime;
    private int litDuration;
    private int menuRange = CleanserRange.BASE_RANGE;
    private int effectiveRange = CleanserRange.BASE_RANGE;
    private int scanCursor;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_LIT_TIME -> litTime;
                case DATA_LIT_DURATION -> litDuration;
                case DATA_MENU_RANGE -> menuRange;
                case DATA_EFFECTIVE_RANGE -> effectiveRange;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_LIT_TIME -> litTime = value;
                case DATA_LIT_DURATION -> litDuration = value;
                case DATA_MENU_RANGE -> menuRange = value;
                case DATA_EFFECTIVE_RANGE -> effectiveRange = value;
                default -> {}
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public CleanserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CLEANSER.get(), pos, state);
    }

    public IItemHandler getItemHandler() {
        return items;
    }

    public ItemStackHandler getItems() {
        return items;
    }

    public ContainerData getDataAccess() {
        return data;
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
        boolean canRun = level instanceof ServerLevel sl
                && ToxicityTicker.isAffected(sl)
                && ToxicityTicker.currentToxicY(sl) != ToxicityTicker.NOT_TOXIC;
        if (!canRun) {
            if (level instanceof ServerLevel sl) {
                CleanserBubbles.remove(sl, pos);
            }
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;

        int cost = Math.max(1, (int) Math.round(
                CleanserRange.fuelCostMultiplier(be.effectiveRange, ToxicSurfaceConfig.CLEANSER_FUEL_EXPONENT.get())));

        if (be.litTime <= 0 && be.consumeFuel()) {
            changed = true;
        }

        if (be.litTime > 0) {
            // Larger ranges burn through the current charge faster.
            be.litTime = Math.max(0, be.litTime - (cost - 1));
            be.revertSludge(level, pos, be.effectiveRange);
            CleanserBubbles.update(serverLevel, pos, be.effectiveRange); // keep breathable air in range
            changed = true;
        } else {
            CleanserBubbles.remove(serverLevel, pos); // out of fuel: the bubble collapses
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    private boolean consumeFuel() {
        ItemStack fuel = items.getStackInSlot(SLOT_FUEL);
        int burn = fuel.getBurnTime(null);
        if (burn <= 0) {
            return false;
        }
        litTime = burn;
        litDuration = burn;
        ItemStack remainder = fuel.getCraftingRemainingItem();
        fuel.shrink(1);
        if (fuel.isEmpty() && !remainder.isEmpty()) {
            items.setStackInSlot(SLOT_FUEL, remainder);
        }
        return true;
    }

    /** Sweeps a budgeted window of the sphere, turning sludge back into water. */
    private void revertSludge(Level level, BlockPos pos, int range) {
        int side = 2 * range + 1;
        long total = (long) side * side * side;
        if (scanCursor >= total) {
            scanCursor = 0;
        }
        int rangeSq = range * range;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int n = 0; n < SCAN_BUDGET; n++) {
            if (scanCursor >= total) {
                scanCursor = 0;
            }
            int idx = scanCursor++;
            int dx = idx % side - range;
            int dy = (idx / side) % side - range;
            int dz = idx / (side * side) - range;
            if (dx * dx + dy * dy + dz * dz > rangeSq) {
                continue;
            }
            cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
            if (level.getBlockState(cursor).is(ModBlocks.SLUDGE_BLOCK.get())) {
                level.setBlock(cursor, Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
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
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("LitTime", litTime);
        tag.putInt("LitDuration", litDuration);
        tag.putInt("MenuRange", menuRange);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        litTime = tag.getInt("LitTime");
        litDuration = tag.getInt("LitDuration");
        menuRange = tag.contains("MenuRange") ? tag.getInt("MenuRange") : CleanserRange.BASE_RANGE;
    }
}
