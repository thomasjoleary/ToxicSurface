// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import io.github.thomasjoleary.toxicsurface.core.generator.GeneratorFuel;
import io.github.thomasjoleary.toxicsurface.registry.ModBlocks;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import io.github.thomasjoleary.toxicsurface.world.GeneratorEmissions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The Toxic Waste Generator (DESIGN.md §7) — a rotation <em>source</em> fuelled by burning
 * solid toxic waste. It maps the inserted item to a {@link GeneratorFuel} tier: loose
 * {@link ModItems#TOXIC_RESIDUE residue} is the basic fuel, and a compacted
 * {@link ModBlocks#TOXIC_WASTE_BLOCK waste block} is the premium fuel — longer burn, higher RPM
 * and more stress capacity, i.e. "waste blocks produce more power." A redstone signal halts it;
 * the single fuel slot is hopper/Create-funnel automatable.
 *
 * <p><b>The catch (DESIGN.md §7):</b> while it runs it vents a toxic smog cloud and pollutes the
 * dimension via {@link GeneratorEmissions} — free power from burning waste poisons the air and
 * hastens the apocalypse. Extends Create's kinetic API, so it is only ever loaded with Create.
 */
public class WasteGeneratorBlockEntity extends GeneratingKineticBlockEntity {
    public static final int SLOT_FUEL = 0;

    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return fuelFor(stack).generates();
        }
    };

    /** Ticks of burn remaining on the unit currently combusting. */
    private int burnTicks;
    /** RPM produced by the unit currently combusting (set from its {@link GeneratorFuel} tier). */
    private int litRpm;
    /** Stress capacity added by the unit currently combusting. */
    private float litCapacity;

    public WasteGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(CreateContent.WASTE_GENERATOR_BE.get(), pos, state);
    }

    public IItemHandler getItemHandler() {
        return items;
    }

    private boolean halted() {
        return level != null && level.hasNeighborSignal(getBlockPos());
    }

    private boolean running() {
        return burnTicks > 0 && !halted();
    }

    @Override
    public float getGeneratedSpeed() {
        if (!running()) {
            return 0;
        }
        return convertToDirection(litRpm, getBlockState().getValue(DirectionalKineticBlock.FACING));
    }

    @Override
    public float calculateAddedStressCapacity() {
        float capacity = running() ? litCapacity : 0f;
        this.lastCapacityProvided = capacity;
        return capacity;
    }

    @Override
    public void tick() {
        super.tick(); // Create rotation/stress source bookkeeping (both sides)
        if (level == null || level.isClientSide) {
            return;
        }
        ServerLevel server = (ServerLevel) level;
        BlockPos pos = getBlockPos();

        float beforeSpeed = getGeneratedSpeed();
        float beforeCapacity = running() ? litCapacity : 0f;

        if (burnTicks > 0) {
            burnTicks--;
        }
        if (!halted() && burnTicks <= 0) {
            consumeFuel(); // light the next unit, or fall idle if none/invalid
        }

        if (running()) {
            GeneratorEmissions.emit(server, pos); // smog + pollution: the drawback of burning waste
        } else {
            GeneratorEmissions.stop(server, pos);
        }

        // Tell Create only when our generated rotation or capacity actually changed.
        if (getGeneratedSpeed() != beforeSpeed || (running() ? litCapacity : 0f) != beforeCapacity) {
            updateGeneratedRotation();
            setChanged();
        }
    }

    private void consumeFuel() {
        ItemStack fuel = items.getStackInSlot(SLOT_FUEL);
        GeneratorFuel.Fuel tier = fuelFor(fuel);
        if (!tier.generates()) {
            burnTicks = 0;
            litRpm = 0;
            litCapacity = 0f;
            return;
        }
        burnTicks = tier.burnTicks();
        litRpm = tier.rpm();
        litCapacity = tier.capacity();
        fuel.shrink(1);
        setChanged();
    }

    private static GeneratorFuel.Fuel fuelFor(ItemStack stack) {
        if (stack.is(ModItems.TOXIC_RESIDUE.get())) {
            return GeneratorFuel.RESIDUE;
        }
        if (stack.is(ModBlocks.TOXIC_WASTE_BLOCK.get().asItem())) {
            return GeneratorFuel.WASTE_BLOCK;
        }
        return GeneratorFuel.NONE;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level instanceof ServerLevel server) {
            GeneratorEmissions.stop(server, getBlockPos()); // collapse the smog when broken/unloaded
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("BurnTicks", burnTicks);
        tag.putInt("LitRpm", litRpm);
        tag.putFloat("LitCapacity", litCapacity);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        burnTicks = tag.getInt("BurnTicks");
        litRpm = tag.getInt("LitRpm");
        litCapacity = tag.getFloat("LitCapacity");
    }
}
