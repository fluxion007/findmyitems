package dev.smpb.findmyitems.retrieval;

import dev.smpb.findmyitems.observation.SlotReader;
import dev.smpb.findmyitems.model.ContainerKind;
import dev.smpb.findmyitems.model.ContainerObservation;
import dev.smpb.findmyitems.model.SourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;

import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

public final class RetrieveHandler {
    /** Matches {@link dev.smpb.findmyitems.observation.SlotReader}'s indexing depth. */
    private static final int MAX_NESTING = 4;
    private RetrieveHandler() {}

    public static boolean retrieve(
            ServerPlayer player,
            BlockPos pos,
            String dimensionId,
            String itemId,
            String componentsJson,
            int amount
    ) {
        return retrieve(player, pos, dimensionId, itemId, componentsJson, amount, 0, ContainerKind.CHEST);
    }

    public static boolean retrieve(
            ServerPlayer player,
            BlockPos pos,
            String dimensionId,
            String itemId,
            String componentsJson,
            int amount,
            int maxReachBlocks
    ) {
        return retrieve(player, pos, dimensionId, itemId, componentsJson, amount, maxReachBlocks,
                ContainerKind.CHEST);
    }

    public static boolean retrieve(
            ServerPlayer player,
            BlockPos pos,
            String dimensionId,
            String itemId,
            String componentsJson,
            int amount,
            int maxReachBlocks,
            ContainerKind expectedContainer
    ) {
        if (!inReach(player, pos, maxReachBlocks)) return false;
        var facts = Reachability.check(player.level(), player, pos, dimensionId,
                TargetKind.CONTAINER, expectedContainer, maxReachBlocks);
        if (!facts.actionable()) return false;

        var container = containerAt(player, pos, expectedContainer);
        if (container == null) return false;

        var remaining = amount;
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            var stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            if (!matches(player, stack, itemId, componentsJson)) continue;

            // Offer a copy and shrink the real stack only by what fit. Splitting first and
            // growing the remainder back would briefly leave a zero-count stack in the container.
            var toTake = Math.min(remaining, stack.getCount());
            var moved = toTake - give(player, stack.copyWithCount(toTake));
            if (moved > 0) {
                stack.shrink(moved);
                container.setChanged();
                remaining -= moved;
            }
            if (moved < toTake) return remaining < amount;
        }

        // Anything still owed may be sitting inside a shulker box in this container: the index
        // reports those items, so retrieval has to be able to reach them too.
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            remaining -= takeFromNested(player, container.getItem(i), itemId, componentsJson, remaining, 1);
        }
        if (remaining < amount) container.setChanged();

        return remaining < amount;
    }

    /**
     * Retrieves only from the slot captured by a plan. The slot is re-read immediately before the
     * action, so an indexed source that was replaced in the meantime cannot satisfy the request.
     */
    public static int retrieveSlot(ServerPlayer player, BlockPos pos, String dimensionId,
                                   ContainerKind expectedContainer, int slot, String itemId,
                                   String componentsJson, int amount, int maxReachBlocks) {
        return retrievePath(player, pos, dimensionId, expectedContainer, List.of(slot), itemId,
                componentsJson, amount, maxReachBlocks);
    }

    /** Retrieves from one exact physical path, including nested container slots. */
    public static int retrievePath(ServerPlayer player, BlockPos pos, String dimensionId,
                                   ContainerKind expectedContainer, List<Integer> path, String itemId,
                                   String componentsJson, int amount, int maxReachBlocks) {
        if (amount <= 0 || !inReach(player, pos, maxReachBlocks)) return 0;
        if (path == null || path.isEmpty() || path.size() > MAX_NESTING + 1
                || path.stream().anyMatch(slot -> slot < 0)) return 0;
        var facts = Reachability.check(player.level(), player, pos, dimensionId,
                TargetKind.CONTAINER, expectedContainer, maxReachBlocks);
        if (!facts.actionable()) return 0;
        var container = containerAt(player, pos, expectedContainer);
        if (container == null || path.getFirst() >= container.getContainerSize()) return 0;
        var root = container.getItem(path.getFirst());
        var moved = path.size() == 1
                ? takeExact(player, root, itemId, componentsJson, amount)
                : takeNested(player, root, path, 1, itemId, componentsJson, amount);
        if (moved > 0) container.setChanged();
        return moved;
    }

    /** Reads the authoritative post-transfer contents for client-index reconciliation. */
    public static ContainerObservation observe(ServerPlayer player, BlockPos pos, ContainerKind kind,
                                               SourceKey contentsKey, List<SourceKey> accessSources) {
        var container = containerAt(player, pos, kind);
        if (container == null) return null;
        return new ContainerObservation(contentsKey, accessSources,
                SlotReader.readContainerSlots(container, player), Instant.now());
    }

    private static int takeNested(ServerPlayer player, ItemStack holder, List<Integer> path, int depth,
                                   String itemId, String componentsJson, int amount) {
        if (depth > MAX_NESTING) return 0;
        var contents = holder.get(DataComponents.CONTAINER);
        if (contents == null || path.get(depth) >= contents.allItemsCopyStream().count()) return 0;
        var items = new ArrayList<>(contents.allItemsCopyStream().toList());
        var childIndex = path.get(depth);
        if (childIndex >= items.size()) return 0;
        var child = items.get(childIndex);
        var moved = depth == path.size() - 1
                ? takeExact(player, child, itemId, componentsJson, amount)
                : takeNested(player, child, path, depth + 1, itemId, componentsJson, amount);
        if (moved > 0) {
            items.set(childIndex, child);
            holder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
        return moved;
    }

    private static int takeExact(ServerPlayer player, ItemStack stack, String itemId,
                                 String componentsJson, int amount) {
        if (stack.isEmpty() || !matches(player, stack, itemId, componentsJson)) return 0;
        var toTake = Math.min(amount, stack.getCount());
        var moved = toTake - give(player, stack.copyWithCount(toTake));
        if (moved > 0) stack.shrink(moved);
        return moved;
    }

    /**
     * Moves up to {@code amount} of an item from the player's inventory into the container.
     *
     * <p>Deliberately narrow: the container must already hold that exact item, including all
     * components. This restores an item to its known source rather than acting as a general storage
     * button; guessing where a never-stored item belongs could scatter it across nearby containers.
     *
     * @return how many items moved; 0 if the container does not stock this item or is full
     */
    public static int deposit(
            ServerPlayer player,
            BlockPos pos,
            String itemId,
            String componentsJson,
            int amount
    ) {
        return deposit(player, pos, itemId, componentsJson, amount, 0, ContainerKind.CHEST);
    }

    public static int deposit(
            ServerPlayer player,
            BlockPos pos,
            String itemId,
            String componentsJson,
            int amount,
            int maxReachBlocks
    ) {
        return deposit(player, pos, itemId, componentsJson, amount, maxReachBlocks, ContainerKind.CHEST);
    }

    public static int deposit(
            ServerPlayer player,
            BlockPos pos,
            String itemId,
            String componentsJson,
            int amount,
            int maxReachBlocks,
            ContainerKind expectedContainer
    ) {
        if (!inReach(player, pos, maxReachBlocks)) return 0;
        var facts = Reachability.check(player.level(), player, pos,
                player.level().dimension().identifier().toString(), TargetKind.CONTAINER,
                expectedContainer, maxReachBlocks);
        if (!facts.actionable()) return 0;

        var container = containerAt(player, pos, expectedContainer);
        if (container == null) return 0;
        if (!alreadyStocks(player, container, itemId, componentsJson)) return 0;

        var inventory = player.getInventory();
        var moved = 0;

        for (int slot = 0; slot < inventory.getContainerSize() && moved < amount; slot++) {
            var held = inventory.getItem(slot);
            if (held.isEmpty() || !matches(player, held, itemId, componentsJson)) continue;

            var offered = Math.min(amount - moved, held.getCount());
            var accepted = insert(container, held, offered);
            if (accepted == 0) continue;

            held.shrink(accepted);
            moved += accepted;
        }

        if (moved > 0) {
            container.setChanged();
            inventory.setChanged();
        }
        return moved;
    }

    /**
     * Hands a stack to the player and reports how many did not fit, so the caller can leave those
     * where they were.
     *
     * <p>Counted rather than taken from {@code add}'s own bookkeeping, which cannot be trusted for
     * this. It reports success when it placed <em>any</em> of the stack, and in creative mode it
     * zeroes the leftover outright — items are free there, so vanilla discards them. Trusting either
     * result would delete overflow from the chest whenever a nearly full
     * inventory asks for a big stack.
     */
    private static int give(ServerPlayer player, ItemStack stack) {
        var offered = stack.getCount();
        var probe = stack.copy();

        var before = countMatching(player, probe);
        player.getInventory().add(stack);
        var placed = countMatching(player, probe) - before;

        return offered - placed;
    }

    /**
     * How many of {@code stack} the player's inventory could accept right now.
     *
     * <p>Deliberately per-item rather than a free-slot count: an inventory packed with dragon eggs
     * still has room for more dragon eggs if a stack is part-full, and a caller that only asked
     * "is any slot empty?" would refuse a take that would have worked perfectly.
     *
     * <p>Only the 36 storage slots count. Armour and the offhand are in {@code getContainerSize()}
     * but {@link net.minecraft.world.entity.player.Inventory#add} will never place into them, so
     * counting them promises room that no retrieval can use.
     */
    public static int roomFor(Player player, ItemStack stack) {
        if (stack.isEmpty()) return 0;

        var room = 0;
        for (var slot : player.getInventory().getNonEquipmentItems()) {
            if (slot.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
                room += Math.max(0, slot.getMaxStackSize() - slot.getCount());
            }
        }
        return room;
    }

    private static int countMatching(ServerPlayer player, ItemStack probe) {
        var inventory = player.getInventory();
        var total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var held = inventory.getItem(slot);
            if (ItemStack.isSameItemSameComponents(held, probe)) total += held.getCount();
        }
        return total;
    }

    /**
     * Returns the container the player would actually open.
     *
     * <p>Not simply the block entity: a double chest's entity holds only half of its 54 slots, so using it
     * directly would leave half the indexed chest unreachable. An ender chest's block entity is only
     * the lid animation; its items live on the player.
     *
     * @return null if there is nothing here to take from
     */
    private static Container containerAt(ServerPlayer player, BlockPos pos, ContainerKind expectedContainer) {
        var world = player.level();
        // Asked first, and only because retrieval reach is configurable: reading a block state is
        // what forces a chunk to load, so a raised reach would otherwise generate terrain on click.
        if (!world.isLoaded(pos)) return null;
        var state = world.getBlockState(pos);
        var block = state.getBlock();

        if (expectedContainer != null && !Reachability.expectedBlock(world, pos,
                TargetKind.CONTAINER, expectedContainer)) return null;

        if (block instanceof EnderChestBlock) return player.getEnderChestInventory();
        if (block instanceof ChestBlock chest) return ChestBlock.getContainer(chest, state, world, pos, true);

        var blockEntity = world.getBlockEntity(pos);
        return blockEntity instanceof Container container && !blockEntity.isRemoved() ? container : null;
    }

    /** Fills existing partial stacks first, then empty slots. Returns how many were taken. */
    private static int insert(Container container, ItemStack source, int offered) {
        var placed = 0;

        for (int i = 0; i < container.getContainerSize() && placed < offered; i++) {
            var existing = container.getItem(i);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, source)) continue;
            var room = Math.min(container.getMaxStackSize(existing), existing.getMaxStackSize()) - existing.getCount();
            if (room <= 0) continue;
            var take = Math.min(room, offered - placed);
            existing.grow(take);
            placed += take;
        }

        for (int i = 0; i < container.getContainerSize() && placed < offered; i++) {
            if (!container.getItem(i).isEmpty()) continue;
            var copy = source.copy();
            var take = Math.min(offered - placed, Math.min(container.getMaxStackSize(copy), copy.getMaxStackSize()));
            copy.setCount(take);
            container.setItem(i, copy);
            placed += take;
        }
        return placed;
    }

    private static boolean alreadyStocks(Player player, Container container, String itemId, String componentsJson) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            var stack = container.getItem(i);
            if (!stack.isEmpty() && matches(player, stack, itemId, componentsJson)) return true;
        }
        return false;
    }

    /**
     * Same item ID and components.
     *
     * <p>Registries must come from the player; without them, enchantment codecs fail and distinct variants
     * collapse to the plain stack's key, so one Take empties every variant.
     */
    private static boolean matches(Player player, ItemStack stack, String itemId, String componentsJson) {
        if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemId)) return false;
        return SlotReader.serializeComponents(stack.getComponentsPatch(), SlotReader.registriesOf(player))
                .equals(componentsJson);
    }

    /** Pulls up to {@code wanted} of {@code itemId} out of a stack's container contents. Returns how many moved. */
    private static int takeFromNested(ServerPlayer player, ItemStack holder, String itemId,
                                      String componentsJson, int wanted, int depth) {
        if (depth > MAX_NESTING || wanted <= 0) return 0;
        var contents = holder.get(DataComponents.CONTAINER);
        if (contents == null) return 0;

        // Keep empty slots: provenance paths are physical indices, not a compacted inventory view.
        var items = new ArrayList<>(contents.allItemsCopyStream().toList());
        var moved = 0;

        for (var inner : items) {
            if (moved >= wanted) break;
            if (!matches(player, inner, itemId, componentsJson)) continue;

            var toTake = Math.min(wanted - moved, inner.getCount());
            var took = toTake - give(player, inner.copyWithCount(toTake));
            inner.shrink(took);
            moved += took;
            if (took < toTake) break;
        }

        for (var inner : items) {
            if (moved >= wanted) break;
            moved += takeFromNested(player, inner, itemId, componentsJson, wanted - moved, depth + 1);
        }

        if (moved > 0) {
            holder.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
        return moved;
    }

    /**
     * Vanilla reach plus one block of slack.
     *
     * <p>The previous check measured feet-to-block-centre, which reads as roughly a block shorter
     * than the reach a player actually has: vanilla measures eye position to the nearest point of
     * the block's box. Deferring to {@link Player#isWithinBlockInteractionRange} fixes that at the
     * source, and the shared reach helper then buys back a little more so a chest you can plainly
     * click is never refused by the catalog.
     */
    public static boolean inReach(Player player, BlockPos pos) {
        return inReach(player, pos, 0);
    }

    /**
     * Vanilla reach, or a configured radius when larger.
     *
     * <p>Never narrower than the arm you already have: the setting raises the ceiling, so a small
     * value cannot take away a chest you could plainly click. Measured eye to block centre, which
     * is a blunter rule than vanilla's box test — at these distances a half-block either way is
     * not relevant at these distances.
     */
    public static boolean inReach(Player player, BlockPos pos, int maxReachBlocks) {
        return Reachability.inRange(player, pos, maxReachBlocks);
    }

    public static int defaultAmount(String itemId) {
        var id = net.minecraft.resources.Identifier.parse(itemId);
        var itemHolder = BuiltInRegistries.ITEM.get(id);
        if (itemHolder.isEmpty()) return 1;
        var stack = new net.minecraft.world.item.ItemStack(itemHolder.get());
        return stack.getMaxStackSize();
    }
}
