// SPDX-License-Identifier: LGPL-3.0-or-later

package io.github.thomasjoleary.toxicsurface.gametest;

import io.github.thomasjoleary.toxicsurface.ToxicSurface;
import io.github.thomasjoleary.toxicsurface.block.WeaverBlockEntity;
import io.github.thomasjoleary.toxicsurface.core.enclosure.BlockMapPassabilityProbe;
import io.github.thomasjoleary.toxicsurface.core.enclosure.EnclosureScanner;
import io.github.thomasjoleary.toxicsurface.core.enclosure.LevelPassabilityProbe;
import io.github.thomasjoleary.toxicsurface.core.enclosure.ScanResult;
import io.github.thomasjoleary.toxicsurface.registry.ModBlocks;
import io.github.thomasjoleary.toxicsurface.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless GameTests (DESIGN.md §10) that exercise the Minecraft-integrated behaviour the standalone
 * unit tests can't reach: the enclosure flood-fill against a <em>real</em> level (not the mock probe),
 * the contraption seal's rules against a captured-block map (DESIGN.md §9), and the toxic-sludge
 * fluid's float-on-water rules. All tests load the shared {@code empty} arena template and build their
 * own scenario with the helper. Standalone (no Create); run by CI's {@code gameTestServer} job and
 * {@code ./gradlew runGameTestServer}.
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

    // ------------------------------------------------------------------ doors & pistons seal rules
    /** A closed door in a wall keeps the pocket sealed; an open one breaches it. */
    @GameTest(template = "empty")
    public static void closedDoorSeals(GameTestHelper helper) {
        buildHollowBox(helper, 1, 1, 1, 5, 5, 5);
        helper.setBlock(3, 3, 1, Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, false));
        if (scanSealed(helper, 3, 3, 3)) {
            helper.succeed();
        } else {
            helper.fail("a closed door should seal");
        }
    }

    @GameTest(template = "empty")
    public static void openDoorDoesNotSeal(GameTestHelper helper) {
        buildHollowBox(helper, 1, 1, 1, 5, 5, 5);
        helper.setBlock(3, 3, 1, Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true));
        if (scanSealed(helper, 3, 3, 3)) {
            helper.fail("an open door should not seal");
        } else {
            helper.succeed();
        }
    }

    /** A retracted piston is a full block and seals; an extended one leaves a gap and doesn't. */
    @GameTest(template = "empty")
    public static void retractedPistonSeals(GameTestHelper helper) {
        buildHollowBox(helper, 1, 1, 1, 5, 5, 5);
        helper.setBlock(3, 3, 1, Blocks.PISTON);
        if (scanSealed(helper, 3, 3, 3)) {
            helper.succeed();
        } else {
            helper.fail("a retracted piston should seal");
        }
    }

    @GameTest(template = "empty")
    public static void extendedPistonDoesNotSeal(GameTestHelper helper) {
        buildHollowBox(helper, 1, 1, 1, 5, 5, 5);
        helper.setBlock(3, 3, 1, Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.EXTENDED, true));
        if (scanSealed(helper, 3, 3, 3)) {
            helper.fail("an extended piston should not seal");
        } else {
            helper.succeed();
        }
    }

    // ------------------------------------------------------------------ contraption (captured-block) sealing
    // These exercise the contraption seal's sealing rules via BlockMapPassabilityProbe against a
    // hand-built captured-block map — no Create needed — so the §9 contraption logic can't silently
    // regress. (The world→local coordinate mapping and detection are Create-only and verified in-game.)
    @GameTest(template = "empty")
    public static void contraptionSealedBox(GameTestHelper helper) {
        if (scanBoxMap(hollowStoneBoxMap()).isSealed()) {
            helper.succeed();
        } else {
            helper.fail("a fully walled contraption box should read as a sealed pocket");
        }
    }

    @GameTest(template = "empty")
    public static void contraptionBreachedBox(GameTestHelper helper) {
        Map<BlockPos, StructureBlockInfo> map = hollowStoneBoxMap();
        map.remove(new BlockPos(3, 5, 3)); // punch a hole in the ceiling
        if (scanBoxMap(map).isSealed()) {
            helper.fail("a contraption box with a hole should read as exposed");
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = "empty")
    public static void contraptionClosedDoorSeals(GameTestHelper helper) {
        Map<BlockPos, StructureBlockInfo> map = hollowStoneBoxMap();
        putBlock(map, 3, 3, 1, Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, false));
        if (scanBoxMap(map).isSealed()) {
            helper.succeed();
        } else {
            helper.fail("a closed door in a contraption wall should seal");
        }
    }

    @GameTest(template = "empty")
    public static void contraptionOpenDoorExposes(GameTestHelper helper) {
        Map<BlockPos, StructureBlockInfo> map = hollowStoneBoxMap();
        putBlock(map, 3, 3, 1, Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true));
        if (scanBoxMap(map).isSealed()) {
            helper.fail("an open door in a contraption wall should not seal");
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

    // ------------------------------------------------------------------ weaver (datapack recipes)
    /**
     * The Weaver must craft from the datapack-loaded {@code toxicsurface:weaving} recipes: 2 string
     * weave into a clean air filter (100 ticks at the fuel rate). Proves the JSON recipes parse,
     * register, and drive the machine end-to-end.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void weaverCraftsDatapackRecipe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.WEAVER.get());
        if (!(helper.getBlockEntity(pos) instanceof WeaverBlockEntity weaver)) {
            helper.fail("placing the weaver block should give a WeaverBlockEntity");
            return;
        }
        weaver.getItems().setStackInSlot(WeaverBlockEntity.SLOT_INPUT_A, new ItemStack(Items.STRING, 2));
        weaver.getItems().setStackInSlot(WeaverBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL));
        helper.succeedWhen(() -> {
            ItemStack out = weaver.getItems().getStackInSlot(WeaverBlockEntity.SLOT_OUTPUT);
            if (!out.is(ModItems.CLEAN_AIR_FILTER.get())) {
                helper.fail("the weaver should weave 2 string into a clean air filter");
            }
        });
    }

    // ------------------------------------------------------------------ helpers
    /** Flood-fill the captured-block map from its interior centre (3,3,3) with the contraption probe. */
    private static ScanResult scanBoxMap(Map<BlockPos, StructureBlockInfo> blocks) {
        return EnclosureScanner.scan(3, 3, 3, new BlockMapPassabilityProbe(blocks), BUDGET);
    }

    /** A hollow stone shell (1,1,1)-(5,5,5); interior cells are simply absent (as in a real capture). */
    private static Map<BlockPos, StructureBlockInfo> hollowStoneBoxMap() {
        Map<BlockPos, StructureBlockInfo> map = new HashMap<>();
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    if (x == 1 || x == 5 || y == 1 || y == 5 || z == 1 || z == 5) {
                        putBlock(map, x, y, z, Blocks.STONE.defaultBlockState());
                    }
                }
            }
        }
        return map;
    }

    private static void putBlock(Map<BlockPos, StructureBlockInfo> map, int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        map.put(pos, new StructureBlockInfo(pos, state, null));
    }

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
