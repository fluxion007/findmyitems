package dev.smpb.findmyitems.debug;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a row of containers for manual testing: {@code /fmitest build} creates it, and
 * {@code /fmitest clear} restores the world. Each container exercises a specific feature — double
 * chests, nested shulkers, component variants, and an out-of-reach chest — so the feature set can
 * be tested by opening the containers in sequence.
 *
 * <p>Registered only in a development run, so a released jar has no debug commands in it.
 * {@code clear} restores exactly the blocks {@code build} overwrote, and that record lives in
 * memory: it does not survive a restart, and it is the only thing the command will ever delete.
 */
public final class TestbedCommand {
    /** How far in front of the player the row starts. */
    private static final int DISTANCE = 3;
    /** Gap between containers, so neighbouring chests never merge into unintended double chests. */
    private static final int SPACING = 2;
    /** Beyond retrieval reach, regardless of vanilla's block reach. */
    private static final int FAR_AWAY = 25;

    private static final Map<BlockPos, BlockState> RESTORE = new LinkedHashMap<>();

    /** The ender chest placed by {@code build}, which {@code strand} removes. */
    private static BlockPos ender;

    private TestbedCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> declare(dispatcher));
    }

    private static void declare(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fmitest")
                .then(Commands.literal("build").executes(ctx -> build(ctx.getSource())))
                .then(Commands.literal("strand").executes(ctx -> strand(ctx.getSource())))
                .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource()))));
    }

    // ---------------------------------------------------------------- build

    private static int build(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var player = source.getPlayerOrException();
        var level = source.getLevel();

        if (!RESTORE.isEmpty()) {
            source.sendFailure(Component.literal("A testbed is already up. Run /fmitest clear first."));
            return 0;
        }

        var forward = player.getDirection();
        var right = forward.getClockWise();
        var facing = forward.getOpposite();
        var origin = player.blockPosition().relative(forward, DISTANCE);

        var placed = new ArrayList<String>();
        var slot = -5;

        placed.add(chest(level, at(origin, right, slot++ * SPACING), facing, "1 Basics", basics()));

        // Two chests side by side merge into one double chest: one contents key, two positions.
        var doubleLeft = at(origin, right, slot++ * SPACING);
        chest(level, doubleLeft, facing, "2 Double chest", doubleChest());
        chest(level, doubleLeft.relative(right), facing, "2 Double chest", List.of());
        placed.add("2 Double chest");

        placed.add(container(level, at(origin, right, slot++ * SPACING), Blocks.TRAPPED_CHEST, facing,
                "3 Trapped chest", trapped()));
        placed.add(container(level, at(origin, right, slot++ * SPACING), Blocks.BARREL, facing,
                "4 Barrel", barrel()));
        placed.add(container(level, at(origin, right, slot++ * SPACING), Blocks.DYED_SHULKER_BOX.purple(), facing,
                "5 Shulker block", dyes()));
        // Ender contents belong to the player and persist without a placed block, so they can be
        // indexed even when no ender chest is available. Together with #1's emeralds, this covers
        // issue #14's split total. /fmitest strand removes the block to leave the indexed half
        // unreachable.
        var enderPos = at(origin, right, slot++ * SPACING);
        placed.add(container(level, enderPos, Blocks.ENDER_CHEST, facing, "6 Ender chest", List.of()));
        ender = enderPos;
        player.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, 10));

        placed.add(chest(level, at(origin, right, slot++ * SPACING), facing, "7 Nested shulkers", nested()));
        placed.add(chest(level, at(origin, right, slot++ * SPACING), facing, "8 Components", components(level)));
        placed.add(chest(level, at(origin, right, slot++ * SPACING), facing, "9 Craft partials", craftPartials()));
        placed.add(chest(level, at(origin, right, slot++ * SPACING), facing, "10 Bulk stacks", bulk()));
        placed.add(chest(level, at(origin, right, slot++ * SPACING), facing, "11 Stacking edge cases", stacking()));
        placed.add(container(level, at(origin, right, slot++ * SPACING), Blocks.BARREL, facing,
                "12 Empty barrel", List.of()));

        var far = origin.relative(forward, FAR_AWAY);
        placed.add(chest(level, far, facing, "13 Out of reach", List.of(new ItemStack(Items.EMERALD, 16))));

        source.sendSuccess(() -> Component.literal(
                "Built %d test containers. #13 is %d blocks out, past retrieval reach. /fmitest clear to undo."
                        .formatted(placed.size(), FAR_AWAY)), false);
        return placed.size();
    }

    /**
     * Removes the testbed's ender chest, leaving its remembered contents unreachable. The block
     * is already in the restore record, so {@code clear} puts it back.
     */
    private static int strand(CommandSourceStack source) {
        if (ender == null) {
            source.sendFailure(Component.literal("No testbed ender chest — run /fmitest build first."));
            return 0;
        }
        source.getLevel().setBlockAndUpdate(ender, Blocks.AIR.defaultBlockState());
        source.sendSuccess(() -> Component.literal(
                "Ender chest removed. Its 10 emeralds stay indexed with no way to reach them; "
                        + "search 'emer' after the next rescan."), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        if (RESTORE.isEmpty()) {
            source.sendFailure(Component.literal("Nothing to clear — no testbed built this session."));
            return 0;
        }
        var level = source.getLevel();
        var count = RESTORE.size();

        // Containers were recorded after their floors, so reverse order removes them first.
        var positions = new ArrayList<>(RESTORE.keySet());
        for (int i = positions.size() - 1; i >= 0; i--) {
            var pos = positions.get(i);
            if (level.getBlockEntity(pos) instanceof Container container) {
                container.clearContent();
            }
            level.setBlockAndUpdate(pos, RESTORE.get(pos));
        }
        RESTORE.clear();
        ender = null;

        source.sendSuccess(() -> Component.literal("Restored " + count + " blocks."), false);
        return count;
    }

    // ---------------------------------------------------------------- placement

    private static BlockPos at(BlockPos origin, Direction right, int offset) {
        return origin.relative(right, offset);
    }

    private static String chest(ServerLevel level, BlockPos pos, Direction facing, String name, List<ItemStack> contents) {
        return container(level, pos, Blocks.CHEST, facing, name, contents);
    }

    private static String container(ServerLevel level, BlockPos pos, Block block, Direction facing,
                                    String name, List<ItemStack> contents) {
        // Record the floor, headroom, and container in placement order; clear restores them in reverse.
        var floor = pos.below();
        if (!level.getBlockState(floor).isSolidRender()) {
            record(level, floor);
            level.setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        record(level, pos.above());
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());

        record(level, pos);
        level.setBlockAndUpdate(pos, oriented(block, facing));

        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            blockEntity.setComponents(DataComponentMap.builder()
                    .set(DataComponents.CUSTOM_NAME, Component.literal(name))
                    .build());
        }
        if (blockEntity instanceof Container container) {
            for (int i = 0; i < contents.size() && i < container.getContainerSize(); i++) {
                container.setItem(i, contents.get(i));
            }
            container.setChanged();
        }
        return name;
    }

    private static BlockState oriented(Block block, Direction facing) {
        var state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        if (block instanceof BarrelBlock) {
            return state.setValue(BlockStateProperties.FACING, facing);
        }
        return state;
    }

    /** Remembers a block once, the first time it is about to be overwritten. */
    private static void record(ServerLevel level, BlockPos pos) {
        RESTORE.putIfAbsent(pos.immutable(), level.getBlockState(pos));
    }

    // ---------------------------------------------------------------- contents

    /** Plain single chest: the baseline for indexing, searching and taking. */
    private static List<ItemStack> basics() {
        return List.of(
                new ItemStack(Items.COBBLESTONE, 64),
                new ItemStack(Items.OAK_LOG, 32),
                new ItemStack(Items.IRON_INGOT, 12),
                new ItemStack(Items.COAL, 40),
                new ItemStack(Items.TORCH, 16),
                new ItemStack(Items.EMERALD, 5));
    }

    /** Fills both halves so the catalog must report one container, not two. */
    private static List<ItemStack> doubleChest() {
        var stacks = new ArrayList<ItemStack>();
        stacks.add(new ItemStack(Items.DIAMOND, 24));
        stacks.add(new ItemStack(Items.GOLD_INGOT, 40));
        stacks.add(new ItemStack(Items.REDSTONE, 64));
        stacks.add(new ItemStack(Items.STICK, 64));
        stacks.add(new ItemStack(Items.STRING, 30));
        stacks.add(new ItemStack(Items.BONE, 20));
        stacks.add(new ItemStack(Items.LEATHER, 18));
        // Reach the far half so the second chest's slots are exercised too.
        while (stacks.size() < 40) stacks.add(ItemStack.EMPTY);
        stacks.add(new ItemStack(Items.WHEAT, 45));
        stacks.add(new ItemStack(Items.PAPER, 33));
        return stacks;
    }

    private static List<ItemStack> trapped() {
        return List.of(
                new ItemStack(Items.ARROW, 64),
                new ItemStack(Items.TNT, 8),
                new ItemStack(Items.STRING, 16));
    }

    private static List<ItemStack> barrel() {
        return List.of(
                new ItemStack(Items.BREAD, 20),
                new ItemStack(Items.CARROT, 30),
                new ItemStack(Items.POTATO, 30),
                new ItemStack(Items.WHEAT, 64));
    }

    private static List<ItemStack> dyes() {
        return List.of(
                new ItemStack(Items.DYE.red(), 16),
                new ItemStack(Items.DYE.blue(), 16),
                new ItemStack(Items.DYE.yellow(), 16),
                new ItemStack(Items.DYE.green(), 16));
    }

    /**
     * Shulkers inside a chest, one nested two levels deep. Searches for gold, netherite, or
     * amethyst should find these items, and retrieval should reach in and remove them.
     */
    private static List<ItemStack> nested() {
        var inner = shulker(Items.DYED_SHULKER_BOX.purple(),
                new ItemStack(Items.NETHERITE_INGOT, 2),
                new ItemStack(Items.AMETHYST_SHARD, 9));

        return List.of(
                shulker(Items.DYED_SHULKER_BOX.lime(),
                        new ItemStack(Items.GOLD_INGOT, 20),
                        new ItemStack(Items.DIAMOND, 5)),
                shulker(Items.DYED_SHULKER_BOX.red(), inner),
                new ItemStack(Items.SAND, 12));
    }

    private static ItemStack shulker(net.minecraft.world.item.Item box, ItemStack... contents) {
        var stack = new ItemStack(box);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(contents)));
        return stack;
    }

    /**
     * Items that share an id but differ by component.
     *
     * <p>The two Bee Stingers are the point: same item, same custom name, different enchantment.
     * They must be two rows, and searching {@code smite 4} or {@code smite iv} must pick out
     * exactly one of them.
     */
    private static List<ItemStack> components(ServerLevel level) {
        var sharpBee = new ItemStack(Items.DIAMOND_SWORD);
        sharpBee.set(DataComponents.CUSTOM_NAME, Component.literal("Bee Stinger"));
        enchant(level, sharpBee, DataComponents.ENCHANTMENTS, Enchantments.SHARPNESS, 5);

        var smiteBee = new ItemStack(Items.DIAMOND_SWORD);
        smiteBee.set(DataComponents.CUSTOM_NAME, Component.literal("Bee Stinger"));
        enchant(level, smiteBee, DataComponents.ENCHANTMENTS, Enchantments.SMITE, 4);

        var sharpnessBook = new ItemStack(Items.ENCHANTED_BOOK);
        enchant(level, sharpnessBook, DataComponents.STORED_ENCHANTMENTS, Enchantments.SHARPNESS, 5);

        var efficiencyBook = new ItemStack(Items.ENCHANTED_BOOK);
        enchant(level, efficiencyBook, DataComponents.STORED_ENCHANTMENTS, Enchantments.EFFICIENCY, 3);

        var worn = new ItemStack(Items.IRON_PICKAXE);
        worn.set(DataComponents.DAMAGE, 180);

        return List.of(sharpBee, smiteBee, new ItemStack(Items.DIAMOND_SWORD),
                sharpnessBook, efficiencyBook, worn, new ItemStack(Items.BOOK, 12));
    }

    private static void enchant(ServerLevel level, ItemStack stack,
                                net.minecraft.core.component.DataComponentType<ItemEnchantments> slot,
                                net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> enchantment,
                                int enchantmentLevel) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(registry.getOrThrow(enchantment), enchantmentLevel);
        stack.set(slot, mutable.toImmutable());
    }

    /**
     * Stacking edge cases. Beds and dragon eggs never stack; empty buckets stack to 16, while
     * filled buckets do not stack; potions and armor fall between these cases. Counts, take
     * amounts, and the "put back" button must handle maximum stack sizes other than 64.
     */
    private static List<ItemStack> stacking() {
        return List.of(
                new ItemStack(Items.BED.red()),
                new ItemStack(Items.BED.white()),
                new ItemStack(Items.DRAGON_EGG),
                new ItemStack(Items.BUCKET, 16),
                new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.LAVA_BUCKET),
                new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.ENDER_PEARL, 16),
                new ItemStack(Items.SNOWBALL, 16),
                new ItemStack(Items.OAK_SIGN, 14),
                new ItemStack(Items.CAKE),
                new ItemStack(Items.ELYTRA),
                new ItemStack(Items.SADDLE),
                new ItemStack(Items.TOTEM_OF_UNDYING, 1));
    }

    /**
     * Partial crafting coverage: enough logs and cobblestone for some stone-pickaxe or chest
     * branches, but not enough for all of them.
     */
    private static List<ItemStack> craftPartials() {
        return List.of(
                new ItemStack(Items.OAK_LOG, 12),
                new ItemStack(Items.STICK, 20),
                new ItemStack(Items.COBBLESTONE, 40),
                new ItemStack(Items.IRON_INGOT, 3),
                new ItemStack(Items.OAK_PLANKS, 7));
    }

    /** More than one stack, so the amount field can be set above 64. */
    private static List<ItemStack> bulk() {
        var stacks = new ArrayList<ItemStack>();
        for (int i = 0; i < 8; i++) {
            stacks.add(new ItemStack(Items.IRON_INGOT, 64));
        }
        return stacks;
    }
}
