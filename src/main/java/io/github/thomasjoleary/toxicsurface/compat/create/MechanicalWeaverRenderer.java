// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * In-world renderer for the {@link MechanicalWeaverBlockEntity} (DESIGN.md §3 "deployer-style"
 * weaver, pass 1). Draws the two inputs (or the finished output) resting on the block's depot-style
 * top face, and — while weaving — a pair of crossing "weaving sticks" that bob over-under to sell the
 * stitching motion.
 *
 * <p>Pass 1 uses vanilla sticks as stand-in geometry so the motion can be evaluated; pass 2 swaps in
 * a proper weaving-head model and texture. Client-only and Create-gated, registered by
 * {@link CreateClientContent}, so it never classloads without Create.
 */
public class MechanicalWeaverRenderer implements BlockEntityRenderer<MechanicalWeaverBlockEntity> {
    private static final float ITEM_Y = 1.02f; // just above the block's top face
    private static final float ITEM_SCALE = 0.5f;

    // Weaving sticks (stand-in: end rods). Two rods stand on the two inputs and lean together at the
    // top into a tent — they "come out of the machine" and reach down into each item — dipping their
    // tips in and out of phase (over-under) to sell the stitching.
    private static final float TIP_SPREAD = 0.16f; // tips land over the two inputs (0.5 ± spread)
    private static final float TIP_BASE_Y = 0.90f; // resting tip height, just above the depot
    private static final float ROD_LENGTH = 0.55f; // scaled length of each rod (lower tent apex)
    private static final float TIP_DIP = 0.05f; // dip depth; capped so the rod's bottom never sinks below 1.0
    private static final float BOB_SPEED = 0.35f; // radians per tick

    private final ItemRenderer itemRenderer;

    public MechanicalWeaverRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            MechanicalWeaverBlockEntity be,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        // The BER's packedLight is sampled at the block's own (opaque, dark) position; sample the open
        // air one block up where the depot items and sticks actually sit, or everything renders black.
        int light = LevelRenderer.getLightColor(level, be.getBlockPos().above());

        ItemStack output = be.getRenderStack(MechanicalWeaverBlockEntity.SLOT_OUTPUT);
        if (!output.isEmpty()) {
            // Finished: show the result centred on the depot.
            renderFlat(be, output, 0.5f, ITEM_Y, 0.5f, ITEM_SCALE, poseStack, buffer, light, packedOverlay);
        } else {
            // Working/idle: show the two inputs side by side.
            renderFlat(
                    be,
                    be.getRenderStack(MechanicalWeaverBlockEntity.SLOT_INPUT_A),
                    0.34f,
                    ITEM_Y,
                    0.5f,
                    ITEM_SCALE,
                    poseStack,
                    buffer,
                    light,
                    packedOverlay);
            renderFlat(
                    be,
                    be.getRenderStack(MechanicalWeaverBlockEntity.SLOT_INPUT_B),
                    0.66f,
                    ITEM_Y,
                    0.5f,
                    ITEM_SCALE,
                    poseStack,
                    buffer,
                    light,
                    packedOverlay);
        }

        if (be.isWeaving()) {
            float phase = (level.getGameTime() + partialTick) * BOB_SPEED;
            renderRod(be, -1f, phase, poseStack, buffer, light, packedOverlay);
            renderRod(be, +1f, phase + (float) Math.PI, poseStack, buffer, light, packedOverlay); // over-under
        }
    }

    /** Renders a single item lying flat (face up) on the depot surface at the given block-local spot. */
    private void renderFlat(
            MechanicalWeaverBlockEntity be,
            ItemStack stack,
            float x,
            float y,
            float z,
            float scale,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        if (stack.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(90)); // lay it flat, facing up
        poseStack.scale(scale, scale, scale);
        itemRenderer.renderStatic(
                stack, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, buffer, be.getLevel(), 0);
        poseStack.popPose();
    }

    /**
     * One weaving rod standing on an input ({@code side} = -1 left / +1 right), leaning toward the
     * centre so the pair forms a tent, with its tip dipping into the item driven by {@code phase}.
     */
    private void renderRod(
            MechanicalWeaverBlockEntity be,
            float side,
            float phase,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        // Lean so the rod's top reaches the centre (tip is TIP_SPREAD out, rod is ROD_LENGTH long).
        float lean = (float) Math.toDegrees(Math.asin(Math.min(1f, TIP_SPREAD / ROD_LENGTH)));
        float tipX = 0.5f + side * TIP_SPREAD;
        float tipY = TIP_BASE_Y - TIP_DIP * Math.max(0f, (float) Math.sin(phase)); // dip into the item

        poseStack.pushPose();
        poseStack.translate(tipX, tipY, 0.5f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * lean)); // tilt the top toward the centre
        poseStack.scale(ROD_LENGTH, ROD_LENGTH, ROD_LENGTH);
        // renderStatic centres the model on the origin (it translates by -0.5 internally), so lift by
        // half a block to put the rod's *bottom* on the tip rather than its centre.
        poseStack.translate(0f, 0.5f, 0f);
        itemRenderer.renderStatic(
                new ItemStack(Items.END_ROD),
                ItemDisplayContext.NONE,
                packedLight,
                packedOverlay,
                poseStack,
                buffer,
                be.getLevel(),
                0);
        poseStack.popPose();
    }
}
