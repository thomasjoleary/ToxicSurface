package io.github.thomasjoleary.toxicsurface.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Server-synced configuration. Mirrors the gameplay table in DESIGN.md §3 and the
 * engine budgets in §8. Values are server-authoritative and synced to clients in
 * multiplayer (DESIGN.md §4).
 */
public final class ToxicSurfaceConfig {
    public static final ModConfigSpec SPEC;

    // --- Timing & escalation ---
    public static final ModConfigSpec.IntValue TIME_TO_TOXIC_TICKS;
    public static final ModConfigSpec.IntValue TOXIC_START_Y;
    public static final ModConfigSpec.IntValue ESCALATION_SPEED_PER_DAY;
    public static final ModConfigSpec.IntValue ESCALATION_MAX_Y;

    // --- Toxic air bar ---
    public static final ModConfigSpec.IntValue AIR_BAR_DRAIN_TICKS;
    public static final ModConfigSpec.IntValue AIR_BAR_REFILL_TICKS;
    public static final ModConfigSpec.DoubleValue TOXIC_DAMAGE_PER_SECOND;
    public static final ModConfigSpec.BooleanValue NAUSEA_WHILE_DRAINING;

    // --- Sludge & water conversion ---
    public static final ModConfigSpec.DoubleValue SLUDGE_DAMAGE;
    public static final ModConfigSpec.IntValue SLUDGE_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue SLUDGE_DEPTH_MIN;
    public static final ModConfigSpec.IntValue SLUDGE_DEPTH_MAX;
    public static final ModConfigSpec.EnumValue<WaterConversionMode> WATER_CONVERSION_MODE;

    // --- Masks & hazmat suit ---
    public static final ModConfigSpec.IntValue MASK_DURATION_TICKS;
    public static final ModConfigSpec.EnumValue<MaskTickMode> MASK_TICK_MODE;
    public static final ModConfigSpec.IntValue SUIT_FILTER_CAPACITY;
    public static final ModConfigSpec.DoubleValue SUIT_CONSUME_RATE_FACTOR;

    // --- Cleanser ---
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> CLEANSER_TIERS;
    public static final ModConfigSpec.DoubleValue CLEANSER_FUEL_EXPONENT;

    // --- World ---
    public static final ModConfigSpec.ConfigValue<List<? extends String>> AFFECTED_DIMENSIONS;
    public static final ModConfigSpec.IntValue FOLIAGE_DECAY_BLOCKS_PER_TICK;
    public static final ModConfigSpec.BooleanValue TOXIC_RAIN_ENABLED;
    public static final ModConfigSpec.DoubleValue TOXIC_RAIN_DECAY_MULTIPLIER;

    // --- Engine budgets (DESIGN.md §8) ---
    public static final ModConfigSpec.IntValue ENCLOSURE_FLOOD_FILL_BUDGET;
    public static final ModConfigSpec.IntValue WATER_CONVERSION_BLOCKS_PER_TICK;

    /** How much of a water column is converted to sludge (DESIGN.md §2b). */
    public enum WaterConversionMode { BANDED, FULL }

    /** When a mask's filter ticks down (DESIGN.md §3, Filters & masks). */
    public enum MaskTickMode { IN_GAS_ONLY, ALWAYS }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("timing");
        TIME_TO_TOXIC_TICKS = b
                .comment("In-game ticks before the surface first turns toxic (120000 ~= 5 in-game days).")
                .defineInRange("timeToToxicTicks", 120_000, 0, Integer.MAX_VALUE);
        TOXIC_START_Y = b
                .comment("Initial toxic ceiling Y (sea level by default).")
                .defineInRange("toxicStartY", 63, -64, 320);
        ESCALATION_SPEED_PER_DAY = b
                .comment("Blocks the toxic ceiling rises per in-game day (0 = static line).")
                .defineInRange("escalationSpeedPerDay", 4, 0, 320);
        ESCALATION_MAX_Y = b
                .comment("Maximum Y the toxic ceiling can rise to (may be set to world height).")
                .defineInRange("escalationMaxY", 200, -64, 320);
        b.pop();

        b.push("air_bar");
        AIR_BAR_DRAIN_TICKS = b
                .comment("Ticks for the toxic air bar to drain full->empty when unprotected (300 = 15s).")
                .defineInRange("airBarDrainTicks", 300, 1, 12_000);
        AIR_BAR_REFILL_TICKS = b
                .comment("Ticks for the toxic air bar to refill empty->full in clean/protected air.")
                .defineInRange("airBarRefillTicks", 60, 1, 12_000);
        TOXIC_DAMAGE_PER_SECOND = b
                .comment("Real damage per second once the air bar empties (kills the player).")
                .defineInRange("toxicDamagePerSecond", 2.0, 0.0, 1_000.0);
        NAUSEA_WHILE_DRAINING = b
                .comment("Apply Nausea while the air bar is draining (not after).")
                .define("nauseaWhileDraining", true);
        b.pop();

        b.push("sludge");
        SLUDGE_DAMAGE = b
                .comment("Contact damage dealt by toxic sludge per interval.")
                .defineInRange("sludgeDamage", 2.0, 0.0, 1_000.0);
        SLUDGE_INTERVAL_TICKS = b
                .comment("Ticks between sludge contact-damage applications (10 = 0.5s).")
                .defineInRange("sludgeIntervalTicks", 10, 1, 1_200);
        SLUDGE_DEPTH_MIN = b
                .comment("Starting sludge band depth below the water surface (thin skin).")
                .defineInRange("sludgeDepthMin", 4, 0, 384);
        SLUDGE_DEPTH_MAX = b
                .comment("Sludge band depth when the toxic line reaches escalationMaxY.")
                .defineInRange("sludgeDepthMax", 24, 0, 384);
        WATER_CONVERSION_MODE = b
                .comment("BANDED = surface-anchored depth band (DESIGN.md §2b); FULL = whole column.")
                .defineEnum("waterConversionMode", WaterConversionMode.BANDED);
        b.pop();

        b.push("equipment");
        MASK_DURATION_TICKS = b
                .comment("Active filter time for a face mask (2400 = 2 minutes).")
                .defineInRange("maskDurationTicks", 2_400, 1, 1_728_000);
        MASK_TICK_MODE = b
                .comment("IN_GAS_ONLY = filter only counts down in toxic gas; ALWAYS = always ticks.")
                .defineEnum("maskTickMode", MaskTickMode.IN_GAS_ONLY);
        SUIT_FILTER_CAPACITY = b
                .comment("Filters stored in the hazmat chestpiece.")
                .defineInRange("suitFilterCapacity", 10, 1, 64);
        SUIT_CONSUME_RATE_FACTOR = b
                .comment("Suit burns filters at this fraction of the mask rate (0.5 = half).")
                .defineInRange("suitConsumeRateFactor", 0.5, 0.0, 1.0);
        b.pop();

        b.push("cleanser");
        CLEANSER_TIERS = b
                .comment("Redstone-selected cleanser sphere radii, ascending.")
                .defineList("cleanserTiers", List.of(8, 16, 32, 64, 128),
                        () -> 8, o -> o instanceof Integer i && i > 0);
        CLEANSER_FUEL_EXPONENT = b
                .comment("Fuel cost scales as (range/8)^k; this is k.")
                .defineInRange("cleanserFuelExponent", 2.0, 1.0, 8.0);
        b.pop();

        b.push("world");
        AFFECTED_DIMENSIONS = b
                .comment("Dimensions that turn toxic (opt-in whitelist; Overworld only by default).")
                .defineList("affectedDimensions", List.of("minecraft:overworld"),
                        () -> "minecraft:overworld", o -> o instanceof String);
        FOLIAGE_DECAY_BLOCKS_PER_TICK = b
                .comment("Global throttle for the foliage death pass (blocks/tick).")
                .defineInRange("foliageDecayBlocksPerTick", 64, 1, 65_536);
        TOXIC_RAIN_ENABLED = b
                .comment("Enable the surface toxic-rain overlay.")
                .define("toxicRainEnabled", true);
        TOXIC_RAIN_DECAY_MULTIPLIER = b
                .comment("Foliage decay speed-up while toxic rain is falling.")
                .defineInRange("toxicRainDecayMultiplier", 2.0, 1.0, 100.0);
        b.pop();

        b.push("performance");
        ENCLOSURE_FLOOD_FILL_BUDGET = b
                .comment("Max air blocks an enclosure flood-fill visits before declaring 'open' (DESIGN.md §8).")
                .defineInRange("enclosureFloodFillBudget", 4_096, 64, 1_048_576);
        WATER_CONVERSION_BLOCKS_PER_TICK = b
                .comment("Global throttle for the water->sludge conversion queue (blocks/tick).")
                .defineInRange("waterConversionBlocksPerTick", 512, 1, 65_536);
        b.pop();

        SPEC = b.build();
    }

    private ToxicSurfaceConfig() {}
}
