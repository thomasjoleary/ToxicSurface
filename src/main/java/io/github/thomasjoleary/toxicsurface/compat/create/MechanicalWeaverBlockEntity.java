// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import io.github.thomasjoleary.toxicsurface.block.WeaverLogic;
import io.github.thomasjoleary.toxicsurface.compat.jade.JadeReadout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The Mechanical Weaver (DESIGN.md §3) — the Create rotation-powered sibling of the fuel
 * {@link io.github.thomasjoleary.toxicsurface.block.WeaverBlockEntity}. It shares the exact
 * {@link WeaverLogic} recipe table (kelp + wool → Hazmat Material, filters, …) but instead of
 * burning furnace fuel it weaves while driven by rotation: progress per tick scales with the
 * supplied RPM, so a faster shaft fabricates faster. A redstone signal or an over-stressed
 * network halts it; the inventory is hopper/pipe-automatable. Extends Create's kinetic API, so
 * it is only ever loaded when Create is present (registered via {@link CreateContent}).
 */
public class MechanicalWeaverBlockEntity extends KineticBlockEntity implements JadeReadout {
    public static final int SLOT_INPUT_A = 0;
    public static final int SLOT_INPUT_B = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int SLOT_COUNT = 3;

    /** Stress units consumed per RPM (Create's stress model); makes the machine a real load. */
    private static final float STRESS_IMPACT = 8f;

    /** Rotation below this leaves the machine idle; at this speed it weaves at the fuel rate (1/tick). */
    private static final float MIN_RPM = 16f;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // Push the new contents to clients so the depot-style renderer shows them in-world.
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot != SLOT_OUTPUT;
        }
    };

    private int progress;
    private int maxProgress;
    /** Synced to the client purely to drive the weaving-stick animation (no GUI to read from). */
    private boolean weaving;

    // Client-only: the last synced progress and the client tick it arrived, so the renderer can
    // extrapolate a smooth 0..1 weave fraction between syncs (one animation cycle per crafted item).
    private int clientSyncedProgress;
    private long clientSyncTick;

    public MechanicalWeaverBlockEntity(BlockPos pos, BlockState state) {
        super(CreateContent.MECHANICAL_WEAVER_BE.get(), pos, state);
    }

    public IItemHandler getItemHandler() {
        return items;
    }

    /** The stack in a slot, for the in-world renderer (client reads the synced inventory). */
    public ItemStack getRenderStack(int slot) {
        return items.getStackInSlot(slot);
    }

    /** Whether the machine is actively weaving — drives the animated weaving sticks on the client. */
    public boolean isWeaving() {
        return weaving;
    }

    /**
     * Client-side weave progress as a looping 0..1 fraction for the renderer — one full cycle per
     * crafted item. The server only syncs {@code progress} at craft/insert moments, so between them we
     * extrapolate at the same rate the server advances (rounded RPM ÷ {@link #MIN_RPM}); the per-craft
     * resync to 0 keeps it honest, so the animation naturally speeds up with the shaft and resets each
     * item regardless of the recipe's length.
     */
    public float progressFraction(float partialTick) {
        if (maxProgress <= 0 || level == null) {
            return 0f;
        }
        int rate = Math.max(1, (int) (Math.abs(getSpeed()) / MIN_RPM)); // matches the server's per-tick step
        float elapsed = (level.getGameTime() - clientSyncTick) + partialTick;
        float fraction = (clientSyncedProgress + rate * elapsed) / maxProgress;
        return fraction - (float) Math.floor(fraction); // loop within the item
    }

    @Override
    public float calculateStressApplied() {
        this.lastStressApplied = STRESS_IMPACT;
        return STRESS_IMPACT;
    }

    @Override
    public void tick() {
        super.tick(); // Create rotation/stress network bookkeeping (both sides)
        if (level == null || level.isClientSide) {
            return;
        }

        float rpm = Math.abs(getSpeed());
        boolean halted = level.hasNeighborSignal(getBlockPos()) || isOverStressed() || rpm < MIN_RPM;
        WeaverLogic.WeaveRecipe recipe = halted
                ? null
                : WeaverLogic.find(items.getStackInSlot(SLOT_INPUT_A), items.getStackInSlot(SLOT_INPUT_B));

        boolean nowWeaving = recipe != null && canOutput(recipe.result());
        if (nowWeaving) {
            maxProgress = recipe.time();
            // Faster rotation weaves faster: one fuel-tick of progress per MIN_RPM of speed.
            progress += Math.max(1, (int) (rpm / MIN_RPM));
            if (progress >= maxProgress) {
                craft(recipe);
                progress = 0;
            }
            setChanged();
        } else if (progress != 0) {
            progress = 0;
            setChanged();
        }

        // Tell clients only when the weaving state flips, so the stick animation starts/stops in sync
        // without a packet every tick (the client runs its own animation clock while weaving is true).
        if (nowWeaving != weaving) {
            weaving = nowWeaving;
            sendData();
        }
    }

    @Override
    public void appendJadeData(CompoundTag tag) {
        int rpm = (int) Math.abs(getSpeed());
        tag.putBoolean("tsActive", rpm > 0 && progress > 0);
        if (maxProgress > 0 && progress > 0) {
            tag.putInt("tsWeave", Math.min(100, progress * 100 / maxProgress));
        }
        if (rpm > 0) {
            tag.putInt("tsRpm", rpm);
        }
    }

    private boolean canOutput(ItemStack result) {
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    private void craft(WeaverLogic.WeaveRecipe recipe) {
        recipe.consume(items.getStackInSlot(SLOT_INPUT_A), items.getStackInSlot(SLOT_INPUT_B));
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            items.setStackInSlot(SLOT_OUTPUT, recipe.result().copy());
        } else {
            out.grow(recipe.result().getCount());
        }
        // A small white puff to punctuate the transformation, offset toward the work face.
        if (level instanceof ServerLevel server) {
            BlockPos pos = getBlockPos();
            Direction workFace = getBlockState().getValue(MechanicalWeaverBlock.WORK_FACE);
            double ox = workFace.getStepX() * 0.6;
            double oy = workFace.getStepY() * 0.6;
            double oz = workFace.getStepZ() * 0.6;
            server.sendParticles(
                    ParticleTypes.POOF,
                    pos.getX() + 0.5 + ox,
                    pos.getY() + 0.5 + oy,
                    pos.getZ() + 0.5 + oz,
                    6,
                    0.15,
                    0.08,
                    0.15,
                    0.01);
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putInt("MaxProgress", maxProgress);
        tag.putBoolean("Weaving", weaving);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        progress = tag.getInt("Progress");
        maxProgress = tag.getInt("MaxProgress");
        weaving = tag.getBoolean("Weaving");
        if (clientPacket) {
            // Anchor the client extrapolation to this fresh progress value.
            clientSyncedProgress = progress;
            clientSyncTick = level != null ? level.getGameTime() : 0L;
        }
    }
}
