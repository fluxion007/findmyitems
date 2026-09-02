package dev.smpb.findmyitems.observation;

import com.mojang.serialization.JsonOps;
import dev.smpb.findmyitems.model.CanonicalJson;
import dev.smpb.findmyitems.model.SlotSnapshot;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.model.StackSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

public final class SlotReader {
    /** Nested slots get indices from here up, so they never collide with real ones. */
    private static final int NESTED_SLOT_BASE = 10_000;
    /** A shulker nested in another shulker and a bundle is excessive; stop there. */
    private static final int MAX_NESTING = 4;

    private SlotReader() {}

    public static List<SlotSnapshot> readContainerSlots(Container container, Player player) {
        var snapshots = new ArrayList<SlotSnapshot>(container.getContainerSize());
        var ctx = tooltipContext(player);
        var nested = new int[]{NESTED_SLOT_BASE};
        for (int i = 0; i < container.getContainerSize(); i++) {
            var stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            snapshots.add(snapshotStack(stack, i, ctx, player,
                    new StackSnapshot.Provenance(List.of(i), -1)));
            addNestedContents(snapshots, stack, ctx, player, nested, 1,
                    List.of(i), stack.get(DataComponents.CONTAINER) == null ? -1 : i);
        }
        return List.copyOf(snapshots);
    }

    public static List<SlotSnapshot> readMenuSlots(AbstractContainerMenu menu, int containerSlots, Player player) {
        var snapshots = new ArrayList<SlotSnapshot>(containerSlots);
        var ctx = tooltipContext(player);
        var nested = new int[]{NESTED_SLOT_BASE};
        for (int i = 0; i < containerSlots; i++) {
            var slot = menu.getSlot(i);
            var stack = slot.getItem();
            if (stack.isEmpty()) continue;
            snapshots.add(snapshotStack(stack, i, ctx, player,
                    new StackSnapshot.Provenance(List.of(i), -1)));
            addNestedContents(snapshots, stack, ctx, player, nested, 1,
                    List.of(i), stack.get(DataComponents.CONTAINER) == null ? -1 : i);
        }
        return List.copyOf(snapshots);
    }

    /**
     * Indexes what is inside a shulker box (or any item carrying container contents) so that a
     * search finds items stashed in a shulker that itself sits in a chest. The shulker stays in
     * the index as an item in its own right; its contents are added alongside it.
     */
    private static void addNestedContents(List<SlotSnapshot> out, ItemStack stack,
                                          Item.TooltipContext ctx, Player player, int[] nextIndex, int depth,
                                          List<Integer> parentPath, int holderSlot) {
        if (depth > MAX_NESTING) return;
        var contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return;

        var items = contents.allItemsCopyStream().toList();
        for (int physicalSlot = 0; physicalSlot < items.size(); physicalSlot++) {
            var inner = items.get(physicalSlot);
            if (inner.isEmpty()) continue;
            var slot = nextIndex[0]++;
            var path = new ArrayList<>(parentPath);
            path.add(physicalSlot);
            out.add(snapshotStack(inner, slot, ctx, player,
                    new StackSnapshot.Provenance(path, holderSlot)));
            addNestedContents(out, inner, ctx, player, nextIndex, depth + 1, path,
                    holderSlot >= 0 ? holderSlot : physicalSlot);
        }
    }

    private static Item.TooltipContext tooltipContext(Player player) {
        return player != null && player.level() != null
                ? Item.TooltipContext.of(player.level())
                : Item.TooltipContext.EMPTY;
    }

    static SlotSnapshot snapshotStack(ItemStack stack, int slotIndex,
                                      Item.TooltipContext ctx, Player player) {
        return snapshotStack(stack, slotIndex, ctx, player, StackSnapshot.Provenance.empty());
    }

    private static SlotSnapshot snapshotStack(ItemStack stack, int slotIndex,
                                              Item.TooltipContext ctx, Player player,
                                              StackSnapshot.Provenance provenance) {
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var componentsJson = serializeComponents(stack.getComponentsPatch(), registriesOf(player));
        var key = new StackKey(itemId, componentsJson);
        var count = stack.getCount();
        var displayName = stack.getHoverName().getString();
        var tooltip = getTooltipLines(stack, ctx, player);
        return new SlotSnapshot(slotIndex, new StackSnapshot(key, count, displayName, tooltip, provenance));
    }

    /** Returns the registries required to encode a stack's components, or null without a world. */
    public static HolderLookup.Provider registriesOf(Player player) {
        return player == null || player.level() == null ? null : player.registryAccess();
    }

    /**
     * The identity half of a {@link StackKey}: two stacks are the same item to this mod exactly when
     * their item id and this string both match.
     *
     * <p>Must be encoded against the world's registries. Enchantments, potions, trim patterns and
     * anything else data-driven hold registry entries, and their codecs cannot encode without a
     * {@link HolderLookup.Provider} — with bare {@code JsonOps} they throw, every time.
     *
     * <p>Therefore, the failure path must not return {@code "{}"}: that is the key for a plain,
     * component-less stack. Using it would silently identify a Sharpness V sword as an unenchanted
     * one, causing server-side retrieval to remove every variant from a chest.
     * A key that cannot be built is degraded to something unique-per-patch instead. It will not decode
     * back into an icon, and that is a far smaller lie than merging two different items.
     */
    public static String serializeComponents(DataComponentPatch patch, HolderLookup.Provider registries) {
        if (patch.isEmpty()) return "{}";
        try {
            var ops = registries == null
                    ? JsonOps.INSTANCE
                    : registries.createSerializationContext(JsonOps.INSTANCE);
            var json = DataComponentPatch.CODEC.encodeStart(ops, patch).getOrThrow();
            return CanonicalJson.stringify(json);
        } catch (Exception e) {
            return UNENCODABLE_PREFIX + patch;
        }
    }

    /** Prefix for component keys that failed to encode; deliberately invalid JSON. */
    private static final String UNENCODABLE_PREFIX = "!";

    static List<String> getTooltipLines(ItemStack stack,
                                        Item.TooltipContext ctx, Player player) {
        try {
            return stack.getTooltipLines(ctx, player, TooltipFlag.NORMAL)
                    .stream()
                    .map(Component::getString)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
