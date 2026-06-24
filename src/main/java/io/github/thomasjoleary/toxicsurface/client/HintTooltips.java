// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.client;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Client-side hint tooltips (towards DESIGN.md §5 Phase 8 "JEI/EMI recipe support"). The toxic
 * generators and the industrial-filter cleaning cycle involve mechanics that have <b>no recipe
 * view</b> — what fuels a generator, that a filter clogs from <em>use</em>, that scrubbing avoids
 * the smog — so JEI/EMI can't surface them automatically. JEI and EMI both render the standard item
 * tooltip in their ingredient panel, so attaching the hints here makes them show up as the in-JEI
 * "what is this for" note (and in the normal inventory tooltip too).
 *
 * <p>Keyed by registry path rather than item references so this base/client class never classloads
 * the Create-gated generator items; in the standalone jar the generator cases simply never match.
 * A proper {@code @JeiPlugin} with {@code addIngredientInfo} info-pages is the Phase 8 follow-up
 * (it needs the JEI API on the classpath).
 */
@EventBusSubscriber(modid = ToxicSurface.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class HintTooltips {
    private HintTooltips() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!ToxicSurface.MODID.equals(id.getNamespace())) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        switch (id.getPath()) {
            case "industrial_filter" -> {
                line(tooltip, "industrial_filter.use");
                line(tooltip, "industrial_filter.clog");
            }
            case "dirty_industrial_filter" -> line(tooltip, "dirty_industrial_filter");
            case "wet_industrial_filter" -> line(tooltip, "wet_industrial_filter");
            case "toxic_residue" -> line(tooltip, "toxic_residue.fuel");
            case "toxic_waste_block" -> line(tooltip, "toxic_waste_block.fuel");
            case "waste_generator" -> {
                line(tooltip, "waste_generator.fuel");
                line(tooltip, "generator.scrub");
            }
            case "sludge_generator" -> {
                line(tooltip, "sludge_generator.fuel");
                line(tooltip, "generator.scrub");
            }
            default -> {
                /* no hint for this item */
            }
        }
    }

    private static void line(List<Component> tooltip, String suffix) {
        tooltip.add(Component.translatable("tooltip." + ToxicSurface.MODID + "." + suffix)
                .withStyle(ChatFormatting.GRAY));
    }
}
