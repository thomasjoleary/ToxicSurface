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
    private static final int TICKS_PER_REAL_MINUTE = 1_200; // 20 ticks/s * 60
    private static final int TICKS_PER_REAL_SECOND = 20;

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

    /**
     * Friendly remaining-time label: in-game <b>days</b> at the day scale (matching the world's
     * day-based framing), then real-time <b>minutes</b>/<b>seconds</b> for the final countdown
     * (e.g. "3 days", "1 day", "5 minutes", "30 seconds").
     */
    static String formatDuration(int ticks) {
        if (ticks >= TICKS_PER_MC_DAY) {
            long days = Math.round(ticks / (double) TICKS_PER_MC_DAY);
            return days + (days == 1 ? " day" : " days");
        }
        if (ticks >= TICKS_PER_REAL_MINUTE) {
            long minutes = Math.round(ticks / (double) TICKS_PER_REAL_MINUTE);
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        long seconds = Math.max(1, Math.round(ticks / (double) TICKS_PER_REAL_SECOND));
        return seconds + (seconds == 1 ? " second" : " seconds");
    }
}
