// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.world;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pre-toxicity telegraph (DESIGN.md §3 onboarding telegraph). Broadcasts an escalating title +
 * subtitle + chat warning to every player in an affected dimension as the clock crosses each
 * configured threshold, so players (especially on multiplayer) get a heads-up before the surface
 * turns toxic rather than only the activation advancement.
 */
public final class ToxicityTelegraph {
    private static final int TICKS_PER_MC_DAY = 24_000;
    private static final int TICKS_PER_MC_HOUR = 1_000;
    private static final double TICKS_PER_MC_MINUTE = TICKS_PER_MC_HOUR / 60.0;

    private ToxicityTelegraph() {}

    /** Warns all players in {@code level} that the surface turns toxic in {@code remainingTicks}. */
    public static void warn(ServerLevel level, int remainingTicks) {
        String time = formatDuration(remainingTicks);
        Component title = Component.translatable("telegraph.toxicsurface.title").withStyle(ChatFormatting.DARK_GREEN);
        Component subtitle =
                Component.translatable("telegraph.toxicsurface.subtitle", time).withStyle(ChatFormatting.GREEN);
        Component chat =
                Component.translatable("telegraph.toxicsurface.chat", time).withStyle(ChatFormatting.GREEN);
        for (ServerPlayer player : level.players()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.sendSystemMessage(chat);
        }
    }

    /** Largest-unit, in-game-time label for a tick count (e.g. "3 days", "1 hour", "10 minutes"). */
    static String formatDuration(int ticks) {
        if (ticks >= TICKS_PER_MC_DAY) {
            long days = Math.round(ticks / (double) TICKS_PER_MC_DAY);
            return days + (days == 1 ? " day" : " days");
        }
        if (ticks >= TICKS_PER_MC_HOUR) {
            long hours = Math.round(ticks / (double) TICKS_PER_MC_HOUR);
            return hours + (hours == 1 ? " hour" : " hours");
        }
        long minutes = Math.max(1, Math.round(ticks / TICKS_PER_MC_MINUTE));
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
