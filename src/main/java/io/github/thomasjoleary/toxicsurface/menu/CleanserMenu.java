// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.menu;

import io.github.thomasjoleary.toxicsurface.block.CleanserBlockEntity;
import io.github.thomasjoleary.toxicsurface.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Container menu for the Cleanser (DESIGN.md §3): a single fuel slot plus the player
 * inventory. Range is the primary control and is adjusted with menu buttons (handled in
 * {@link #clickMenuButton}); the {@link ContainerData} carries burn + range values for
 * the screen.
 */
public class CleanserMenu extends AbstractMachineMenu {
    /** Button ids for the range steppers (see {@link #clickMenuButton}). */
    public static final int BUTTON_RANGE_DOWN_1 = 0;

    public static final int BUTTON_RANGE_UP_1 = 1;
    public static final int BUTTON_RANGE_DOWN_8 = 2;
    public static final int BUTTON_RANGE_UP_8 = 3;

    private final ContainerData data;
    private final CleanserBlockEntity cleanser; // server: the BE; client: null

    /** Client constructor — dummy backing; the server syncs slot and data values. */
    public CleanserMenu(int containerId, Inventory playerInventory) {
        this(
                containerId,
                playerInventory,
                new ItemStackHandler(CleanserBlockEntity.SLOT_COUNT),
                new SimpleContainerData(4),
                null);
    }

    /** Server constructor — bound to the block entity. */
    public CleanserMenu(int containerId, Inventory playerInventory, CleanserBlockEntity cleanser) {
        this(containerId, playerInventory, cleanser.getItems(), cleanser.getDataAccess(), cleanser);
    }

    private CleanserMenu(
            int containerId,
            Inventory playerInventory,
            IItemHandler items,
            ContainerData data,
            CleanserBlockEntity cleanser) {
        super(ModMenus.CLEANSER.get(), containerId, CleanserBlockEntity.SLOT_COUNT);
        this.data = data;
        this.cleanser = cleanser;

        addSlot(new SlotItemHandler(items, CleanserBlockEntity.SLOT_FUEL, 80, 53));
        addPlayerInventory(playerInventory, 84);
        addDataSlots(data);
    }

    public int getMenuRange() {
        return data.get(CleanserBlockEntity.DATA_MENU_RANGE);
    }

    public int getEffectiveRange() {
        return data.get(CleanserBlockEntity.DATA_EFFECTIVE_RANGE);
    }

    public boolean isLit() {
        return data.get(CleanserBlockEntity.DATA_LIT_TIME) > 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (cleanser == null) {
            return false;
        }
        int range = cleanser.getDataAccess().get(CleanserBlockEntity.DATA_MENU_RANGE);
        int next =
                switch (id) {
                    case BUTTON_RANGE_DOWN_1 -> range - 1;
                    case BUTTON_RANGE_UP_1 -> range + 1;
                    case BUTTON_RANGE_DOWN_8 -> range - 8;
                    case BUTTON_RANGE_UP_8 -> range + 8;
                    default -> range;
                };
        if (next == range) {
            return false;
        }
        cleanser.setMenuRange(next);
        return true;
    }
}
