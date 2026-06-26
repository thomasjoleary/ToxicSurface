// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.compat.create;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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

    // Weaving sticks: two stand-in sticks crossing at the centre, bobbing out of phase (over-under).
    private static final float STICK_BASE_Y = 1.28f;
    private static final float STICK_AMPLITUDE = 0.16f;
    private static final float STICK_CROSS_DEGREES = 38f;
    private static final float STICK_SCALE = 0.75f;
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

        ItemStack output = be.getRenderStack(MechanicalWeaverBlockEntity.SLOT_OUTPUT);
        if (!output.isEmpty()) {
            // Finished: show the result centred on the depot.
            renderFlat(be, output, 0.5f, ITEM_Y, 0.5f, ITEM_SCALE, poseStack, buffer, packedLight, packedOverlay);
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
                    packedLight,
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
                    packedLight,
                    packedOverlay);
        }

        if (be.isWeaving()) {
            float phase = (level.getGameTime() + partialTick) * BOB_SPEED;
            renderStick(
                    be, +STICK_CROSS_DEGREES, (float) Math.sin(phase), poseStack, buffer, packedLight, packedOverlay);
            renderStick(
                    be,
                    -STICK_CROSS_DEGREES,
                    (float) Math.sin(phase + Math.PI), // opposite phase: one dips as the other lifts
                    poseStack,
                    buffer,
                    packedLight,
                    packedOverlay);
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

    /** One crossing "weaving stick", angled by {@code crossDegrees} and bobbed by {@code bob} in [-1,1]. */
    private void renderStick(
            MechanicalWeaverBlockEntity be,
            float crossDegrees,
            float bob,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5f, STICK_BASE_Y + STICK_AMPLITUDE * bob, 0.5f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(crossDegrees)); // tilt to make the two cross
        poseStack.scale(STICK_SCALE, STICK_SCALE, STICK_SCALE);
        itemRenderer.renderStatic(
                new ItemStack(Items.STICK),
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                buffer,
                be.getLevel(),
                0);
        poseStack.popPose();
    }
}
