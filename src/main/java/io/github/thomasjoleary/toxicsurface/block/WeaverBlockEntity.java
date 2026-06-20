// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.block;

import io.github.thomasjoleary.toxicsurface.menu.WeaverMenu;
import io.github.thomasjoleary.toxicsurface.registry.ModBlockEntities;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The Weaver (DESIGN.md §3) — a furnace-fuelled textile/filtration fabricator. Two
 * input slots + fuel + output; converts fibre into Hazmat Material and air filters
 * via a small hard-coded recipe table (datapack-driven recipes are a future
 * enhancement). A redstone signal halts it; the item handler is hopper-automatable.
 */
public class WeaverBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_INPUT_A = 0;
    public static final int SLOT_INPUT_B = 1;
    public static final int SLOT_FUEL = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final int SLOT_COUNT = 4;

    public static final int DATA_LIT_TIME = 0;
    public static final int DATA_LIT_DURATION = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_MAX_PROGRESS = 3;

    private static final List<WeaveRecipe> RECIPES = buildRecipes();

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case SLOT_FUEL -> stack.getBurnTime(null) > 0;
                case SLOT_OUTPUT -> false;
                default -> true;
            };
        }
    };

    private int litTime;
    private int litDuration;
    private int progress;
    private int maxProgress;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_LIT_TIME -> litTime;
                case DATA_LIT_DURATION -> litDuration;
                case DATA_PROGRESS -> progress;
                case DATA_MAX_PROGRESS -> maxProgress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_LIT_TIME -> litTime = value;
                case DATA_LIT_DURATION -> litDuration = value;
                case DATA_PROGRESS -> progress = value;
                case DATA_MAX_PROGRESS -> maxProgress = value;
                default -> {}
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public WeaverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WEAVER.get(), pos, state);
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.toxicsurface.weaver");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new WeaverMenu(containerId, playerInventory, this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WeaverBlockEntity be) {
        boolean changed = false;
        if (be.litTime > 0) {
            be.litTime--;
        }

        WeaveRecipe recipe = level.hasNeighborSignal(pos) ? null : be.findRecipe();
        if (recipe != null && be.canOutput(recipe.result())) {
            if (be.litTime <= 0) {
                changed |= be.consumeFuel();
            }
            if (be.litTime > 0) {
                be.maxProgress = recipe.time();
                be.progress++;
                if (be.progress >= be.maxProgress) {
                    be.craft(recipe);
                    be.progress = 0;
                }
                changed = true;
            } else if (be.progress != 0) {
                be.progress = 0;
                changed = true;
            }
        } else if (be.progress != 0) {
            be.progress = 0;
            changed = true;
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

    private WeaveRecipe findRecipe() {
        ItemStack a = items.getStackInSlot(SLOT_INPUT_A);
        ItemStack b = items.getStackInSlot(SLOT_INPUT_B);
        for (WeaveRecipe recipe : RECIPES) {
            if (recipe.matches(a, b)) {
                return recipe;
            }
        }
        return null;
    }

    private boolean canOutput(ItemStack result) {
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    private void craft(WeaveRecipe recipe) {
        recipe.consume(items.getStackInSlot(SLOT_INPUT_A), items.getStackInSlot(SLOT_INPUT_B));
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            items.setStackInSlot(SLOT_OUTPUT, recipe.result().copy());
        } else {
            out.grow(recipe.result().getCount());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("LitTime", litTime);
        tag.putInt("LitDuration", litDuration);
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        litTime = tag.getInt("LitTime");
        litDuration = tag.getInt("LitDuration");
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
    }

    private static List<WeaveRecipe> buildRecipes() {
        return List.of(
                new WeaveRecipe(
                        Ingredient.of(Items.KELP),
                        1,
                        Ingredient.of(ItemTags.WOOL),
                        1,
                        new ItemStack(ModItems.HAZMAT_MATERIAL.get()),
                        200),
                new WeaveRecipe(
                        Ingredient.of(ItemTags.WOOL),
                        1,
                        Ingredient.EMPTY,
                        0,
                        new ItemStack(ModItems.CLEAN_AIR_FILTER.get()),
                        100),
                new WeaveRecipe(
                        Ingredient.of(Items.STRING),
                        2,
                        Ingredient.EMPTY,
                        0,
                        new ItemStack(ModItems.CLEAN_AIR_FILTER.get()),
                        100),
                new WeaveRecipe(
                        Ingredient.of(ModItems.CLEAN_AIR_FILTER.get()),
                        1,
                        Ingredient.of(Items.CHARCOAL, Items.COAL),
                        1,
                        new ItemStack(ModItems.CARBON_AIR_FILTER.get()),
                        150));
    }

    /** A two-input weave recipe (the second ingredient may be {@link Ingredient#EMPTY} for single-input recipes). */
    private record WeaveRecipe(Ingredient a, int aCount, Ingredient b, int bCount, ItemStack result, int time) {
        boolean singleInput() {
            return bCount == 0;
        }

        boolean matches(ItemStack slotA, ItemStack slotB) {
            if (singleInput()) {
                // exactly one slot filled, matching ingredient a
                if (!slotB.isEmpty() && !slotA.isEmpty()) {
                    return false;
                }
                ItemStack only = slotA.isEmpty() ? slotB : slotA;
                return !only.isEmpty() && a.test(only) && only.getCount() >= aCount;
            }
            return (matchPart(a, aCount, slotA) && matchPart(b, bCount, slotB))
                    || (matchPart(a, aCount, slotB) && matchPart(b, bCount, slotA));
        }

        void consume(ItemStack slotA, ItemStack slotB) {
            if (singleInput()) {
                (slotA.isEmpty() ? slotB : slotA).shrink(aCount);
                return;
            }
            if (matchPart(a, aCount, slotA) && matchPart(b, bCount, slotB)) {
                slotA.shrink(aCount);
                slotB.shrink(bCount);
            } else {
                slotA.shrink(bCount);
                slotB.shrink(aCount);
            }
        }

        private static boolean matchPart(Ingredient ingredient, int count, ItemStack stack) {
            return !stack.isEmpty() && ingredient.test(stack) && stack.getCount() >= count;
        }
    }
}
