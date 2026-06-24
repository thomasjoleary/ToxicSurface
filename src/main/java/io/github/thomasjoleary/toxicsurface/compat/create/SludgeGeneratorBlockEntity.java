// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import io.github.thomasjoleary.toxicsurface.block.ExhaustScrubber;
import io.github.thomasjoleary.toxicsurface.core.generator.GeneratorFuel;
import io.github.thomasjoleary.toxicsurface.registry.ModFluids;
import io.github.thomasjoleary.toxicsurface.world.GeneratorEmissions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The Toxic Sludge Generator (DESIGN.md §7) — a rotation <em>source</em> fuelled by burning toxic
 * sludge from an internal {@link FluidTank}. While the tank holds sludge (and no redstone halts
 * it) the generator drains a steady {@link GeneratorFuel#SLUDGE_MB_PER_TICK} per tick and spins
 * its shaft at the fixed {@link GeneratorFuel#SLUDGE} tier. Create pumps/pipes fill the tank
 * directly because the sludge is a real NeoForge fluid; the handler is exposed via
 * {@link CreateContent}.
 *
 * <p><b>The catch (DESIGN.md §7):</b> like its solid sibling it vents a toxic smog cloud and
 * pollutes the dimension via {@link GeneratorEmissions} while running. Extends Create's kinetic
 * API, so it is only ever loaded with Create.
 */
public class SludgeGeneratorBlockEntity extends GeneratingKineticBlockEntity {
    public static final int SLOT_FILTER = 0;

    /** Tank size: a few buckets of buffer so a pump can keep it topped up. */
    private static final int TANK_CAPACITY = 8_000;

    private final FluidTank tank = new FluidTank(TANK_CAPACITY, stack -> stack.getFluid() == ModFluids.SLUDGE.get()) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return ExhaustScrubber.isFilter(stack); // industrial scrubber filter slot only
        }
    };

    public SludgeGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(CreateContent.SLUDGE_GENERATOR_BE.get(), pos, state);
    }

    public IFluidHandler getFluidHandler() {
        return tank;
    }

    public IItemHandler getItemHandler() {
        return items;
    }

    private boolean running() {
        return tank.getFluidAmount() >= GeneratorFuel.SLUDGE_MB_PER_TICK
                && !(level != null && level.hasNeighborSignal(getBlockPos()));
    }

    @Override
    public float getGeneratedSpeed() {
        if (!running()) {
            return 0;
        }
        return convertToDirection(GeneratorFuel.SLUDGE.rpm(), getBlockState().getValue(DirectionalKineticBlock.FACING));
    }

    @Override
    public float calculateAddedStressCapacity() {
        float capacity = running() ? GeneratorFuel.SLUDGE.capacity() : 0f;
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
        boolean run = running();
        if (run) {
            tank.drain(GeneratorFuel.SLUDGE_MB_PER_TICK, IFluidHandler.FluidAction.EXECUTE);
            // A clean industrial filter captures the exhaust so it runs clean; otherwise it vents.
            if (ExhaustScrubber.advance(items, SLOT_FILTER)) {
                GeneratorEmissions.stop(server, pos); // scrubbed: no smog, no pollution
            } else {
                GeneratorEmissions.emit(server, pos); // raw exhaust: smog + pollution (DESIGN.md §7)
            }
        } else {
            GeneratorEmissions.stop(server, pos);
        }

        // Tell Create only when our generated rotation actually changed (e.g. tank ran dry).
        if (getGeneratedSpeed() != beforeSpeed) {
            updateGeneratedRotation();
            setChanged();
        }
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
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.put("Items", items.serializeNBT(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Tank")) {
            tank.readFromNBT(registries, tag.getCompound("Tank"));
        }
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
    }
}
