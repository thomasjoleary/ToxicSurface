// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.core.generator;

/**
 * Power tiers for the toxic generators (DESIGN.md §7). Pure and unit-testable — the
 * Create-side block entities map an inserted fuel item (or a tank of sludge) to one of
 * these {@link Fuel} tiers and feed the {@code rpm}/{@code capacity} into Create's kinetic
 * source API. Compacted waste blocks are the premium fuel: they burn far longer and spin
 * the shaft at a higher RPM (and add more stress capacity) than loose residue — i.e.
 * "waste blocks produce more power."
 */
public final class GeneratorFuel {
    private GeneratorFuel() {}

    /**
     * A power tier.
     *
     * @param burnTicks how long one unit of solid fuel keeps the generator running (ignored
     *     for the continuously-fed sludge generator, which gates on tank contents instead)
     * @param rpm rotation the generator produces while this fuel burns
     * @param capacity stress capacity (su) the generator adds to its network while running
     */
    public record Fuel(int burnTicks, int rpm, float capacity) {
        /** True if this tier actually drives a shaft (a non-zero generated speed). */
        public boolean generates() {
            return rpm != 0;
        }
    }

    /** No fuel / idle: the generator is a dead weight on the network (no speed, no capacity). */
    public static final Fuel NONE = new Fuel(0, 0, 0f);

    /** Loose toxic residue — the basic solid fuel: short burn, modest power. */
    public static final Fuel RESIDUE = new Fuel(400, 32, 256f);

    /** A compacted toxic waste block — premium solid fuel: long burn, double RPM, double capacity. */
    public static final Fuel WASTE_BLOCK = new Fuel(2_000, 64, 512f);

    /** Toxic sludge — the fluid generator's steady output while its tank holds sludge. */
    public static final Fuel SLUDGE = new Fuel(0, 48, 384f);

    /** Millibuckets of sludge the fluid generator consumes per tick while running. */
    public static final int SLUDGE_MB_PER_TICK = 2;
}
