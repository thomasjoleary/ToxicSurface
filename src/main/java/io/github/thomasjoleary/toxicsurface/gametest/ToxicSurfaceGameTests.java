// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.gametest;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.core.enclosure.EnclosureScanner;
import io.github.thomasjoleary.toxicsurface.core.enclosure.LevelPassabilityProbe;
import io.github.thomasjoleary.toxicsurface.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless GameTests (DESIGN.md §10) that exercise the Minecraft-integrated behaviour the standalone
 * unit tests can't reach: the enclosure flood-fill against a <em>real</em> level (not the mock probe),
 * and the toxic-sludge fluid's float-on-water rules. All tests load the shared {@code empty} arena
 * template and build their own scenario with the helper. Standalone (no Create); run by CI's
 * {@code gameTestServer} job and {@code ./gradlew runGameTestServer}.
 *
 * <p>These classes live in the main jar but are inert unless the {@code toxicsurface} GameTest
 * namespace is enabled, so they never affect normal play.
 */
@GameTestHolder(ToxicSurface.MODID)
@PrefixGameTestTemplate(false)
public final class ToxicSurfaceGameTests {
    private ToxicSurfaceGameTests() {}

    /** Small budget: an interior of a few cells stays sealed, but escaping it blows past the cap. */
    private static final int BUDGET = 512;

    private static final long SETTLE_TICKS = 30;

    // ------------------------------------------------------------------ enclosure (real level)
    @GameTest(template = "empty")
    public static void enclosureSealedBox(GameTestHelper helper) {
        buildHollowBox(helper, 1, 1, 1, 5, 5, 5);
        if (scanSealed(helper, 3, 3, 3)) {
            helper.succeed();
        } else {
            helper.fail("a fully walled box should read as a sealed pocket");
        }
    }

    @GameTest(template = "empty")
    public static void enclosureBreachedBox(GameTestHelper helper) {
        buildHollowBox(helper, 1, 1, 1, 5, 5, 5);
        helper.setBlock(3, 5, 3, Blocks.AIR); // punch a hole in the ceiling
        if (scanSealed(helper, 3, 3, 3)) {
            helper.fail("a box with a hole in it should read as exposed");
        } else {
            helper.succeed();
        }
    }

    // ------------------------------------------------------------------ sludge floats on water
    /** A sludge source above a water pool must not sink into (or contaminate) the water below it. */
    @GameTest(template = "empty")
    public static void sludgeDoesNotSinkIntoWater(GameTestHelper helper) {
        fillWaterPool(helper, 1);
        helper.setBlock(2, 2, 2, ModBlocks.SLUDGE_BLOCK.get()); // sludge source one block above centre
        helper.runAtTickTime(SETTLE_TICKS, () -> {
            helper.assertBlock(
                    new BlockPos(2, 2, 2),
                    b -> b == ModBlocks.SLUDGE_BLOCK.get(),
                    "sludge should stay on top, not drain into the water");
            helper.assertBlock(new BlockPos(2, 1, 2), b -> b == Blocks.WATER, "water under the sludge must stay water");
            helper.succeed();
        });
    }

    /** A sludge source embedded in a water surface must not be overwritten by the surrounding water. */
    @GameTest(template = "empty")
    public static void sludgeNotReplacedBySurroundingWater(GameTestHelper helper) {
        fillWaterPool(helper, 1);
        helper.setBlock(2, 1, 2, ModBlocks.SLUDGE_BLOCK.get()); // replace the centre water with sludge
        helper.runAtTickTime(SETTLE_TICKS, () -> {
            helper.assertBlock(
                    new BlockPos(2, 1, 2),
                    b -> b == ModBlocks.SLUDGE_BLOCK.get(),
                    "adjacent water must not flow in and replace the sludge");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ helpers
    private static boolean scanSealed(GameTestHelper helper, int rx, int ry, int rz) {
        BlockPos p = helper.absolutePos(new BlockPos(rx, ry, rz));
        return EnclosureScanner.scan(p.getX(), p.getY(), p.getZ(), new LevelPassabilityProbe(helper.getLevel()), BUDGET)
                .isSealed();
    }

    /** A stone shell from (x0,y0,z0) to (x1,y1,z1) inclusive, hollow inside. */
    private static void buildHollowBox(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    boolean wall = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                    helper.setBlock(x, y, z, wall ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
    }

    /** A 3x3 pool of water source blocks at the given height, footprint (1,1)..(3,3). */
    private static void fillWaterPool(GameTestHelper helper, int y) {
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(x, y, z, Blocks.WATER);
            }
        }
    }
}
