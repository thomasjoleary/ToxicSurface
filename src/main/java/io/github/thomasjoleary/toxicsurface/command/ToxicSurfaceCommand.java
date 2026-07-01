// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.config.ToxicSurfaceConfig;
import io.github.thomasjoleary.toxicsurface.core.toxicity.ToxicityModel;
import io.github.thomasjoleary.toxicsurface.world.ToxicityState;
import io.github.thomasjoleary.toxicsurface.world.ToxicityTicker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /toxicsurface} testing command (DESIGN.md §12): lets a live-testing session tune the
 * escalation clock, force a specific toxic ceiling, and read/write every scalar server config
 * value without editing the config file and restarting. Op-only ({@code permission 2}), since it
 * mutates world-affecting state live.
 *
 * <p>{@link #ENTRIES} is the single source of truth for {@code get}/{@code set}/{@code status}: add
 * a scalar {@link ModConfigSpec.IntValue}/{@code DoubleValue}/{@code BooleanValue}/{@code EnumValue}
 * here and all three subcommands pick it up. List-typed config values (affected dimensions,
 * telegraph thresholds, cleanser tiers) are intentionally left out — they need file edits anyway.
 */
@EventBusSubscriber(modid = ToxicSurface.MODID)
public final class ToxicSurfaceCommand {
    private ToxicSurfaceCommand() {}

    private static final List<ConfigEntry> ENTRIES = List.of(
            intEntry("timeToToxicTicks", "timing", ToxicSurfaceConfig.TIME_TO_TOXIC_TICKS),
            intEntry("toxicStartY", "timing", ToxicSurfaceConfig.TOXIC_START_Y),
            intEntry("escalationSpeedPerDay", "timing", ToxicSurfaceConfig.ESCALATION_SPEED_PER_DAY),
            intEntry("escalationMaxY", "timing", ToxicSurfaceConfig.ESCALATION_MAX_Y),
            boolEntry("telegraphEnabled", "timing", ToxicSurfaceConfig.TELEGRAPH_ENABLED),
            intEntry("airBarDrainTicks", "air_bar", ToxicSurfaceConfig.AIR_BAR_DRAIN_TICKS),
            intEntry("airBarRefillTicks", "air_bar", ToxicSurfaceConfig.AIR_BAR_REFILL_TICKS),
            doubleEntry("toxicDamagePerSecond", "air_bar", ToxicSurfaceConfig.TOXIC_DAMAGE_PER_SECOND),
            boolEntry("nauseaWhileDraining", "air_bar", ToxicSurfaceConfig.NAUSEA_WHILE_DRAINING),
            doubleEntry("sludgeDamage", "sludge", ToxicSurfaceConfig.SLUDGE_DAMAGE),
            intEntry("sludgeIntervalTicks", "sludge", ToxicSurfaceConfig.SLUDGE_INTERVAL_TICKS),
            intEntry("sludgeDepthMin", "sludge", ToxicSurfaceConfig.SLUDGE_DEPTH_MIN),
            intEntry("sludgeDepthMax", "sludge", ToxicSurfaceConfig.SLUDGE_DEPTH_MAX),
            enumEntry(
                    "waterConversionMode",
                    "sludge",
                    ToxicSurfaceConfig.WATER_CONVERSION_MODE,
                    ToxicSurfaceConfig.WaterConversionMode.class),
            intEntry("maskDurationTicks", "equipment", ToxicSurfaceConfig.MASK_DURATION_TICKS),
            enumEntry(
                    "maskTickMode",
                    "equipment",
                    ToxicSurfaceConfig.MASK_TICK_MODE,
                    ToxicSurfaceConfig.MaskTickMode.class),
            intEntry("suitFilterCapacity", "equipment", ToxicSurfaceConfig.SUIT_FILTER_CAPACITY),
            doubleEntry("suitConsumeRateFactor", "equipment", ToxicSurfaceConfig.SUIT_CONSUME_RATE_FACTOR),
            doubleEntry(
                    "carbonFilterDurationMultiplier",
                    "equipment",
                    ToxicSurfaceConfig.CARBON_FILTER_DURATION_MULTIPLIER),
            intEntry("cleanserMaxRange", "cleanser", ToxicSurfaceConfig.CLEANSER_MAX_RANGE),
            doubleEntry("cleanserFuelExponent", "cleanser", ToxicSurfaceConfig.CLEANSER_FUEL_EXPONENT),
            intEntry("generatorSmogRadius", "generators", ToxicSurfaceConfig.GENERATOR_SMOG_RADIUS),
            intEntry("generatorPollutionPerTick", "generators", ToxicSurfaceConfig.GENERATOR_POLLUTION_PER_TICK),
            intEntry("industrialFilterLifeTicks", "generators", ToxicSurfaceConfig.INDUSTRIAL_FILTER_LIFE_TICKS),
            intEntry("foliageDecayBlocksPerTick", "world", ToxicSurfaceConfig.FOLIAGE_DECAY_BLOCKS_PER_TICK),
            boolEntry("toxicRainEnabled", "world", ToxicSurfaceConfig.TOXIC_RAIN_ENABLED),
            doubleEntry("toxicRainDecayMultiplier", "world", ToxicSurfaceConfig.TOXIC_RAIN_DECAY_MULTIPLIER),
            intEntry("enclosureFloodFillBudget", "performance", ToxicSurfaceConfig.ENCLOSURE_FLOOD_FILL_BUDGET),
            intEntry(
                    "waterConversionBlocksPerTick",
                    "performance",
                    ToxicSurfaceConfig.WATER_CONVERSION_BLOCKS_PER_TICK));

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher()
                .register(Commands.literal("toxicsurface")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(ToxicSurfaceCommand::status))
                        .then(Commands.literal("get")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(ToxicSurfaceCommand::suggestKeys)
                                        .executes(ToxicSurfaceCommand::getConfig)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .suggests(ToxicSurfaceCommand::suggestKeys)
                                        .then(Commands.argument("value", StringArgumentType.string())
                                                .executes(ToxicSurfaceCommand::setConfig))))
                        .then(Commands.literal("ceiling")
                                .then(Commands.literal("get").executes(ToxicSurfaceCommand::ceilingGet))
                                .then(Commands.literal("clear").executes(ToxicSurfaceCommand::ceilingClear))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("y", IntegerArgumentType.integer(-64, 320))
                                                .executes(ToxicSurfaceCommand::ceilingSet))))
                        .then(Commands.literal("toxicity")
                                .then(Commands.literal("on").executes(ToxicSurfaceCommand::toxicityOn))
                                .then(Commands.literal("off").executes(ToxicSurfaceCommand::toxicityOff))
                                .then(Commands.literal("in")
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                                .executes(ToxicSurfaceCommand::toxicityIn)))));
    }

    // ------------------------------------------------------------------ status

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        boolean affected = ToxicityTicker.isAffected(level);
        ToxicityState state = ToxicityState.get(level);
        int ceiling = ToxicityTicker.currentToxicY(level);
        boolean toxic = ceiling != ToxicityTicker.NOT_TOXIC;

        send(source, "=== ToxicSurface — " + level.dimension().location() + " ===");
        send(source, "affected dimension: " + affected);
        send(source, "toxic now: " + toxic + (toxic ? " (ceiling Y=" + ceiling + ")" : ""));
        if (state.ceilingOverride() != ToxicityState.NO_OVERRIDE) {
            send(source, "  ceiling is OVERRIDDEN (use '/toxicsurface ceiling clear' to resume the model)");
        }
        if (state.hasStarted()) {
            send(source, "escalation started at tick " + state.startTick());
        } else if (state.isSuppressed()) {
            send(source, "escalation not started — SUPPRESSED (forced off; '/toxicsurface toxicity on|in' re-arms it)");
        } else {
            long remaining =
                    ToxicSurfaceConfig.TIME_TO_TOXIC_TICKS.get() - (level.getGameTime() + state.pollutionTicks());
            send(source, "escalation not started — " + Math.max(0, remaining) + " ticks remaining");
        }
        send(source, "pollution ticks accumulated: " + state.pollutionTicks());
        send(source, "telegraph stage: " + state.telegraphStage());
        if (toxic) {
            int depth = ToxicityModel.sludgeDepth(
                    ceiling,
                    ToxicSurfaceConfig.TOXIC_START_Y.get(),
                    ToxicSurfaceConfig.ESCALATION_MAX_Y.get(),
                    ToxicSurfaceConfig.SLUDGE_DEPTH_MIN.get(),
                    ToxicSurfaceConfig.SLUDGE_DEPTH_MAX.get());
            send(source, "sludge band depth at this ceiling: " + depth);
        }

        send(source, "--- config ---");
        String category = null;
        for (ConfigEntry entry : ENTRIES) {
            if (!entry.category.equals(category)) {
                category = entry.category;
                send(source, "[" + category + "]");
            }
            send(source, "  " + entry.key + " = " + entry.currentValue());
        }
        return 1;
    }

    // ------------------------------------------------------------------ get/set

    private static int getConfig(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String key = StringArgumentType.getString(ctx, "key");
        ConfigEntry entry = find(key);
        if (entry == null) {
            source.sendFailure(Component.literal("Unknown key '" + key + "'. Run /toxicsurface status to list keys."));
            return 0;
        }
        send(source, entry.key + " = " + entry.currentValue());
        return 1;
    }

    private static int setConfig(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String key = StringArgumentType.getString(ctx, "key");
        String value = StringArgumentType.getString(ctx, "value");
        ConfigEntry entry = find(key);
        if (entry == null) {
            source.sendFailure(Component.literal("Unknown key '" + key + "'. Run /toxicsurface status to list keys."));
            return 0;
        }
        try {
            entry.apply(value);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(entry.key + ": " + e.getMessage()));
            return 0;
        }
        send(source, entry.key + " set to " + entry.currentValue());
        return 1;
    }

    private static ConfigEntry find(String key) {
        for (ConfigEntry entry : ENTRIES) {
            if (entry.key.equalsIgnoreCase(key)) {
                return entry;
            }
        }
        return null;
    }

    private static CompletableFuture<Suggestions> suggestKeys(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        List<String> keys = new ArrayList<>(ENTRIES.size());
        for (ConfigEntry entry : ENTRIES) {
            keys.add(entry.key);
        }
        return SharedSuggestionProvider.suggest(keys, builder);
    }

    // ------------------------------------------------------------------ ceiling override

    private static int ceilingGet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        ToxicityState state = ToxicityState.get(level);
        int ceiling = ToxicityTicker.currentToxicY(level);
        if (ceiling == ToxicityTicker.NOT_TOXIC) {
            send(source, "Not toxic in " + level.dimension().location());
        } else {
            boolean overridden = state.ceilingOverride() != ToxicityState.NO_OVERRIDE;
            send(source, "Toxic ceiling Y=" + ceiling + (overridden ? " (overridden)" : " (computed)"));
        }
        return 1;
    }

    private static int ceilingSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int y = IntegerArgumentType.getInteger(ctx, "y");
        ToxicityState.get(source.getLevel()).setCeilingOverride(y);
        send(
                source,
                "Toxic ceiling forced to Y=" + y + " in "
                        + source.getLevel().dimension().location());
        return 1;
    }

    private static int ceilingClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ToxicityState.get(source.getLevel()).clearCeilingOverride();
        send(source, "Ceiling override cleared — resuming the escalation model.");
        return 1;
    }

    // ------------------------------------------------------------------ toxicity on/off/in

    private static int toxicityOn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        ToxicityState state = ToxicityState.get(level);
        if (state.hasStarted()) {
            source.sendFailure(Component.literal("Already toxic in "
                    + level.dimension().location() + " (started at tick " + state.startTick() + ")."));
            return 0;
        }
        state.setSuppressed(false); // an explicit "on" always re-arms the natural clock too
        ToxicityTicker.forceStart(level);
        send(source, "Toxicity forced ON in " + level.dimension().location());
        return 1;
    }

    private static int toxicityOff(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        ToxicityState state = ToxicityState.get(level);
        state.reset();
        // Without this, the natural clock re-checks every tick against the absolute
        // timeToToxicTicks threshold, which real game time has already passed — so it would
        // re-trigger on the very next tick. "on"/"in" clear it again to re-arm the clock.
        state.setSuppressed(true);
        send(source, "Toxicity forced OFF in " + level.dimension().location() + " (clock and ceiling override reset).");
        return 1;
    }

    private static int toxicityIn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        ToxicityState state = ToxicityState.get(level);
        state.reset(); // pollutionTicks -> 0 so the threshold below lines up exactly
        state.setSuppressed(false); // re-arm the natural clock so it actually counts down again
        int threshold = (int) Math.min(Integer.MAX_VALUE, level.getGameTime() + ticks);
        ToxicSurfaceConfig.TIME_TO_TOXIC_TICKS.set(threshold);
        send(
                source,
                "Toxicity will start naturally in " + ticks + " ticks in "
                        + level.dimension().location());
        return 1;
    }

    private static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    // ------------------------------------------------------------------ config entry registry

    /** One scalar server config value exposed to {@code get}/{@code set}/{@code status}. */
    private static final class ConfigEntry {
        final String key;
        final String category;
        private final ModConfigSpec.ConfigValue<?> value;
        private final Function<String, ?> parser;

        <T> ConfigEntry(String key, String category, ModConfigSpec.ConfigValue<T> value, Function<String, T> parser) {
            this.key = key;
            this.category = category;
            this.value = value;
            this.parser = parser;
        }

        String currentValue() {
            return String.valueOf(value.get());
        }

        /** Parses {@code raw}, validates it against the config's own range/enum, then applies it. */
        @SuppressWarnings("unchecked")
        void apply(String raw) {
            Object parsed = parser.apply(raw);
            ModConfigSpec.ValueSpec spec = value.getSpec();
            if (!spec.test(parsed)) {
                ModConfigSpec.Range<?> range = spec.getRange();
                String bounds = range != null ? " (must be " + range.getMin() + ".." + range.getMax() + ")" : "";
                throw new IllegalArgumentException("'" + raw + "' is invalid" + bounds);
            }
            ((ModConfigSpec.ConfigValue<Object>) value).set(parsed);
        }
    }

    private static ConfigEntry intEntry(String key, String category, ModConfigSpec.IntValue value) {
        return new ConfigEntry(key, category, value, ToxicSurfaceCommand::parseInt);
    }

    private static ConfigEntry doubleEntry(String key, String category, ModConfigSpec.DoubleValue value) {
        return new ConfigEntry(key, category, value, ToxicSurfaceCommand::parseDouble);
    }

    private static ConfigEntry boolEntry(String key, String category, ModConfigSpec.BooleanValue value) {
        return new ConfigEntry(key, category, value, ToxicSurfaceCommand::parseBool);
    }

    // The enum Class is passed explicitly (not derived from value.get()) because ENTRIES is built at
    // class-init time, before the config is loaded — reading a ConfigValue that early throws
    // "Cannot get config value before config is loaded" and takes the whole mod down with it.
    private static <E extends Enum<E>> ConfigEntry enumEntry(
            String key, String category, ModConfigSpec.EnumValue<E> value, Class<E> type) {
        return new ConfigEntry(key, category, value, s -> parseEnum(type, s));
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + s + "' is not a whole number");
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + s + "' is not a number");
        }
    }

    private static boolean parseBool(String s) {
        if (s.equalsIgnoreCase("true")) {
            return true;
        }
        if (s.equalsIgnoreCase("false")) {
            return false;
        }
        throw new IllegalArgumentException("'" + s + "' must be true or false");
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String s) {
        for (E e : type.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(s)) {
                return e;
            }
        }
        throw new IllegalArgumentException("'" + s + "' is not one of " + Arrays.toString(type.getEnumConstants()));
    }
}
