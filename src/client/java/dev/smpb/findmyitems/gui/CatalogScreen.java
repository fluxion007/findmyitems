package dev.smpb.findmyitems.gui;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.smpb.findmyitems.craft.CraftingPlan;
import dev.smpb.findmyitems.craft.CraftingPlanner;
import dev.smpb.findmyitems.craft.DisplayPlan;
import dev.smpb.findmyitems.craft.PlanningInventory;
import dev.smpb.findmyitems.craft.RecipeCatalog;
import dev.smpb.findmyitems.FindMyItemsClient;
import dev.smpb.findmyitems.config.ModConfig;
import dev.smpb.findmyitems.index.ContainerIndex;
import dev.smpb.findmyitems.index.IndexedContainer;
import dev.smpb.findmyitems.index.ItemResult;
import dev.smpb.findmyitems.index.SourceResult;
import dev.smpb.findmyitems.index.SearchQuery;
import dev.smpb.findmyitems.model.ContainerKind;
import dev.smpb.findmyitems.model.ContainerObservation;
import dev.smpb.findmyitems.model.SourceKey;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.model.StackSnapshot;
import dev.smpb.findmyitems.observation.SlotReader;
import dev.smpb.findmyitems.retrieval.GhostOpen;
import dev.smpb.findmyitems.retrieval.CraftingExecutor;
import dev.smpb.findmyitems.retrieval.ExecutionStatus;
import dev.smpb.findmyitems.retrieval.ReachabilityService;
import dev.smpb.findmyitems.retrieval.RetrieveHandler;
import dev.smpb.findmyitems.retrieval.TargetKind;
import dev.smpb.findmyitems.search.SearchIndex;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;

import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class CatalogScreen extends Screen {
    static final int TITLE_Y = 8;
    static final int TABS_Y = 22;
    static final int SEARCH_Y = 46;
    static final int WIDGET_HEIGHT = 20;
    static final int LIST_Y = 72;
    static final int FOOTER_HEIGHT = 22;
    static final int ROW_HEIGHT = 28;
    static final int SLOT_SIZE = 18;
    static final int CELL_SIZE = 22;
    static final int BUTTON_SIZE = 20;
    static final int ICON_SIZE = 16;
    static final int GAP = 4;
    static final int AMOUNT_WIDTH = 56;
    static final int LAYOUT_BUTTON_WIDTH = 52;
    static final int MAX_LIST_WIDTH = 420;
    static final int INDENT = 10;
    /** Width of the grid's detail pane. Enough for a container name and a coordinate triple. */
    static final int DETAIL_WIDTH = 150;
    static final int DETAIL_PADDING = 6;
    static final int DETAIL_LINE = 10;


    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");

    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFFA0A0A0;
    private static final int TEXT_DISABLED = 0xFF6E6E6E;
    private static final int TEXT_MISSING = 0xFFFF7B6B;
    private static final int TEXT_OK = 0xFF8CE07A;
    private static final int LIST_BACKGROUND = 0xC0101010;
    private static final int LIST_BORDER = 0xFF3A3A3A;
    private static final int CELL_HOVER = 0x60FFFFFF;

    public enum View { ITEMS, CONTAINERS, CRAFTING }

    public enum Layout { LIST, GRID }

    private static RecipeManager cachedRecipeManager;
    private static RecipeCatalog cachedRecipeCatalog;
    private static int cachedRecipeFingerprint;

    private Map<StackKey, Long> cachedStock;
    private long cachedStockRevision = -1;

    private final ContainerIndex index;
    private final ModConfig config;
    private final ReachabilityService reachability;
    private EditBox searchField;
    private EditBox amountField;
    private RowList rowList;
    private final Map<View, Button> tabs = new HashMap<>();
    private Button layoutButton;
    private Button gatherButton;
    private Button craftButton;

    private String currentQuery = "";
    private View view = View.ITEMS;
    private int amount = 1;
    private int resultCount;
    private long lastSeenRevision = -1;
    private OutputIdentity selectedOutput;
    private OutputIdentity hoveredOutput;
    private long searchGeneration;
    private long planGeneration;
    private long appliedPlanGeneration = -1;
    private int planRequestCount;
    private List<DisplayPlan.Row> plannedRows;
    private CraftingPlan plannedPlan;
    private long seenRecipeGeneration = -1;
    private Component status = Component.empty();
    private boolean gatherOnlyStatus;
    private ExecutionStatus lastExecutorStatus = ExecutionStatus.COMPLETE;
    private ItemResult hoveredItem;
    private final List<ActionRegion> actionRegions = new ArrayList<>();

    record OutputIdentity(StackKey key, long recipeGeneration) {}
    record GenerationState(long searchGeneration, long planGeneration, long appliedPlanGeneration) {}
    record BrowseState(int rowCount, int planRequests, boolean rootRows, OutputIdentity selected,
                       OutputIdentity hovered) {}
    record SelectionState(int planRequests, OutputIdentity selected, OutputIdentity hovered,
                          GenerationState generations) {}
    record RowBounds(double left, double top, double width, double height) {}

    public static void invalidateRecipeCache() {
        cachedRecipeManager = null;
        cachedRecipeCatalog = null;
        cachedRecipeFingerprint = 0;
    }

    public CatalogScreen(ContainerIndex index, ModConfig config) {
        super(Component.translatable("screen.findmyitems.catalog"));
        this.index = index;
        this.config = config;
        this.reachability = ReachabilityService.shared();
    }

    /**
     * List or grid, read from the config every time.
     *
     * <p>Not a field: the screen is rebuilt from scratch on every press of the open key, so anything
     * kept here is forgotten when the catalog closes. It is a reading preference, so it belongs in
     * the config and persists across restarts.
     */
    private Layout layout() {
        return config.gridLayout ? Layout.GRID : Layout.LIST;
    }

    // ---------------------------------------------------------------- layout

    private int listWidth() {
        return Math.min(MAX_LIST_WIDTH, width - 24);
    }

    private int listLeft() {
        return (width - listWidth()) / 2;
    }

    @Override
    protected void init() {
        var left = listLeft();
        var total = listWidth();

        var tabWidth = (total - LAYOUT_BUTTON_WIDTH - GAP - 2 * GAP) / 3;
        var x = left;
        for (var candidate : View.values()) {
            var button = Button.builder(tabLabel(candidate), b -> switchTo(candidate))
                    .bounds(x, TABS_Y, tabWidth, WIDGET_HEIGHT)
                    .build();
            tabs.put(candidate, button);
            addRenderableWidget(button);
            x += tabWidth + GAP;
        }

        layoutButton = Button.builder(layoutLabel(), b -> toggleLayout())
                .bounds(left + total - LAYOUT_BUTTON_WIDTH, TABS_Y, LAYOUT_BUTTON_WIDTH, WIDGET_HEIGHT)
                .build();
        addRenderableWidget(layoutButton);

        gatherButton = Button.builder(Component.translatable("screen.findmyitems.craft.gather_materials"),
                        ignored -> startExecution(CraftingExecutor.Mode.GATHER_ONLY))
                .bounds(left, height - FOOTER_HEIGHT, 108, WIDGET_HEIGHT).build();
        craftButton = Button.builder(Component.translatable("screen.findmyitems.craft.gather_and_craft"),
                        ignored -> startExecution(CraftingExecutor.Mode.GATHER_AND_CRAFT))
                .bounds(left + 112, height - FOOTER_HEIGHT, 124, WIDGET_HEIGHT).build();
        addRenderableWidget(gatherButton);
        addRenderableWidget(craftButton);

        var searchWidth = total - AMOUNT_WIDTH - GAP;
        searchField = new EditBox(font, left, SEARCH_Y, searchWidth, WIDGET_HEIGHT,
                Component.translatable("screen.findmyitems.search"));
        searchField.setMaxLength(64);
        searchField.setValue(currentQuery);
        searchField.setResponder(this::onSearchChanged);
        addRenderableWidget(searchField);
        setInitialFocus(searchField);

        amountField = new EditBox(font, left + searchWidth + GAP, SEARCH_Y, AMOUNT_WIDTH, WIDGET_HEIGHT,
                Component.translatable("screen.findmyitems.amount"));
        amountField.setMaxLength(4);
        amountField.setValue(String.valueOf(amount));
        amountField.setResponder(this::onAmountTyped);
        addRenderableWidget(amountField);

        rebuildList();
        refreshChrome();
    }

    /**
     * The grid's detail pane, which the list layout does without.
     *
     * <p>A grid cell is an icon and a number: it has room to say <em>how many</em> and nowhere to
     * say <em>where</em>. The list rows carry that on their subtitle; the grid needs somewhere to
     * put it, so the pane is permanently reserved rather than popped up over the cells — a panel
     * that appears under the cursor moves the thing you were about to click.
     */
    private boolean hasDetailPane() {
        return view == View.ITEMS && layout() == Layout.GRID;
    }

    private int gridWidth() {
        return hasDetailPane() ? listWidth() - DETAIL_WIDTH - GAP : listWidth();
    }

    private void rebuildList() {
        if (rowList != null) removeWidget(rowList);
        rowList = new RowList(minecraft, gridWidth(), height, listLeft(),
                layout() == Layout.GRID ? CELL_SIZE : ROW_HEIGHT);
        addRenderableWidget(rowList);
        updateResults();
    }

    private void refreshChrome() {
        tabs.forEach((candidate, button) -> {
            button.setMessage(tabLabel(candidate));
            button.active = candidate != view;
        });
        layoutButton.setMessage(layoutLabel());
        // A crafting plan is a tree; a grid cannot show the nesting, so the toggle is meaningless there.
        layoutButton.active = view != View.CRAFTING;
        // Amount drives how many to take (items) or how many to craft (crafting), but nothing in containers.
        amountField.visible = view != View.CONTAINERS;
        gatherButton.visible = view == View.CRAFTING && selectedOutput != null && plannedPlan != null;
        craftButton.visible = gatherButton.visible;
        gatherButton.active = !FindMyItemsClient.executor().busy();
        craftButton.active = !FindMyItemsClient.executor().busy();
        searchField.setHint(Component.translatable(switch (view) {
            case ITEMS -> "screen.findmyitems.hint.items";
            case CONTAINERS -> "screen.findmyitems.hint.containers";
            case CRAFTING -> "screen.findmyitems.hint.crafting";
        }));
    }

    private Component tabLabel(View candidate) {
        return Component.translatable(switch (candidate) {
            case ITEMS -> "screen.findmyitems.view.items";
            case CONTAINERS -> "screen.findmyitems.view.containers";
            case CRAFTING -> "screen.findmyitems.view.crafting";
        });
    }

    /** The toggle is labelled with the layout it switches to, not the one you are in. */
    private Component layoutLabel() {
        return Component.translatable(layout() == Layout.LIST
                ? "screen.findmyitems.layout.grid"
                : "screen.findmyitems.layout.list");
    }

    private void switchTo(View next) {
        if (view == next) return;
        view = next;
        invalidateQuery();
        if (view != View.CRAFTING) selectedOutput = null;
        refreshChrome();
        rebuildList();
        // Switching views is always followed by typing, so hand the cursor back to the search box.
        setFocused(searchField);
    }

    private void toggleLayout() {
        config.gridLayout = !config.gridLayout;
        config.save();
        invalidateQuery();
        refreshChrome();
        rebuildList();
        setFocused(searchField);
    }

    // ---------------------------------------------------------------- input

    private void onSearchChanged(String query) {
        currentQuery = query;
        invalidateQuery();
        hoveredOutput = null;
        if (selectedOutput != null && !craftingRoots().stream().anyMatch(key -> key.equals(selectedOutput.key()))) {
            selectedOutput = null;
        }
        updateResults();
        refreshChrome();
        if (selectedOutput != null) requestPlan();
    }

    private void onAmountTyped(String typed) {
        var digits = typed.replaceAll("\\D", "");
        if (!digits.equals(typed)) {
            amountField.setValue(digits);
            return;
        }
        amount = digits.isEmpty() ? 1 : Math.min(9999, Integer.parseInt(digits));
        if (view == View.CRAFTING) {
            invalidateQuery();
            updateResults();
            refreshChrome();
            if (selectedOutput != null) requestPlan();
        }
    }

    private void setAmount(int next) {
        amount = Math.max(1, Math.min(9999, next));
        amountField.setValue(String.valueOf(amount));
    }

    /**
     * Ctrl+1/2/3 (Cmd on macOS) jumps between the views. The plain digits are left alone because
     * the search box owns them — you type "64 arrows" far more often than you switch tabs.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (isCommandOrControl()) {
            var picked = switch (event.key()) {
                case GLFW.GLFW_KEY_1 -> View.ITEMS;
                case GLFW.GLFW_KEY_2 -> View.CONTAINERS;
                case GLFW.GLFW_KEY_3 -> View.CRAFTING;
                default -> null;
            };
            if (picked != null) {
                switchTo(picked);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /**
     * Polls whether Ctrl or Cmd is physically held, rather than reading the event's modifier bits.
     *
     * <p>Both halves matter: {@code hasControlDown()} only reports the Control bit, so the macOS
     * Cmd shortcut would never fire, and modifier bits are absent entirely from synthetic key
     * events — which is how the client game test drives this. Asking the window directly is
     * correct for a real keyboard and drivable from a test.
     */
    private static boolean isCommandOrControl() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, InputConstants.KEY_LCONTROL)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RCONTROL)
                || InputConstants.isKeyDown(window, InputConstants.KEY_LSUPER)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RSUPER);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (rowList != null && rowList.viewport().hitTest(event.x(), event.y()).isPresent()) {
            for (var region : actionRegions) {
                if (!region.contains((int) event.x(), (int) event.y())) continue;
                var action = event.button() == 1 ? region.secondary() : region.primary();
                if (action != null) {
                    action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (rowList != null && rowList.viewport().contains(x, y)) {
            for (var region : actionRegions) {
                if (region.scrollsAmount() && region.contains((int) x, (int) y)) {
                    setAmount(amount + (int) scrollY);
                    if (view == View.CRAFTING) {
                        invalidateQuery();
                        updateResults(true);
                        requestPlan();
                    }
                    return true;
                }
            }
            rowList.scrollBy(-scrollY * rowList.rowHeight());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        actionRegions.clear();
        // Whichever grid cell is under the cursor claims this during the list's own render below.
        hoveredItem = null;
        hoveredOutput = null;
        super.extractRenderState(graphics, mouseX, mouseY, a);

        if (hasDetailPane()) drawDetailPane(graphics);

        graphics.centeredText(font, title, width / 2, TITLE_Y, TEXT);

        if (resultCount == 0 && !status.getString().isEmpty()) {
            graphics.centeredText(font, status, width / 2, height / 2 - 4, TEXT_DIM);
        }

        var footer = switch (view) {
            case ITEMS -> Component.translatable(resultCount == 1
                    ? "screen.findmyitems.footer.items.one"
                    : "screen.findmyitems.footer.items", resultCount, amount);
            case CONTAINERS -> Component.translatable("screen.findmyitems.footer.containers", resultCount);
            case CRAFTING -> selectedOutput == null
                    ? Component.translatable("screen.findmyitems.footer.craft_index", resultCount)
                    : Component.translatable(plannedRows == null
                            ? "screen.findmyitems.craft.gather_materials"
                            : "screen.findmyitems.craft.gather_and_craft");
        };
        graphics.centeredText(font, footer, width / 2, height - FOOTER_HEIGHT + 6, TEXT_DIM);
    }

    /**
     * Draws the grid's detail pane: every container holding the hovered item, and what it holds.
     *
     * <p>The headline count answers "have I got any"; this answers "where, and can I get at it".
     * Sources are grouped by container rather than listed one per access position, so a double
     * chest is one line — two lines of 64 for one chest of 64 would be the same lie the item
     * total used to tell.
     */
    private void drawDetailPane(GuiGraphicsExtractor graphics) {
        var left = listLeft() + gridWidth() + GAP;
        var top = LIST_Y;
        var bottom = height - FOOTER_HEIGHT;
        graphics.fill(left, top, left + DETAIL_WIDTH, bottom, LIST_BACKGROUND);
        graphics.outline(left, top, DETAIL_WIDTH, bottom - top, LIST_BORDER);

        var item = hoveredItem;
        var x = left + DETAIL_PADDING;
        var y = top + DETAIL_PADDING;
        graphics.enableScissor(left + 1, top + 1, left + DETAIL_WIDTH - 1, bottom - 1);

        if (item == null) {
            graphics.text(font, Component.translatable("screen.findmyitems.detail.hint").getString(),
                    x, y, TEXT_DIM);
            graphics.disableScissor();
            return;
        }

        graphics.text(font, item.displayName(), x, y, TEXT);
        y += DETAIL_LINE + 2;
        graphics.text(font, Component.translatable(
                "screen.findmyitems.detail.total", item.totalCount()).getString(), x, y, TEXT_DIM);
        y += DETAIL_LINE + 4;

        for (var container : containerBreakdown(item)) {
            if (y > bottom - DETAIL_LINE) break;
            var reason = unreachableReason(container.where());
            graphics.text(font, container.count() + " × " + kindLabel(container.where().kind()),
                    x, y, reason == null ? TEXT_OK : TEXT_MISSING);
            y += DETAIL_LINE;
            graphics.text(font, positionLabel(container.where()), x + INDENT, y, TEXT_DIM);
            y += DETAIL_LINE;
            if (reason != null) {
                graphics.text(font, reason.getString(), x + INDENT, y, TEXT_DISABLED);
                y += DETAIL_LINE;
            }
            y += 3;
        }
        graphics.disableScissor();
    }

    /** One line's worth of a breakdown: a container, the nearest way in, and what it holds. */
    private record ContainerShare(SourceKey where, int count) {}

    private static List<ContainerShare> containerBreakdown(ItemResult item) {
        var byContainer = new LinkedHashMap<SourceKey, SourceResult>();
        for (var source : item.sources()) {
            byContainer.merge(source.contentsKey(), source,
                    (a, b) -> distanceSqr(a.source()) <= distanceSqr(b.source()) ? a : b);
        }
        return byContainer.values().stream()
                .sorted(Comparator.comparingDouble(source -> distanceSqr(source.source())))
                .map(source -> new ContainerShare(source.source(), source.count()))
                .toList();
    }

    private static String positionLabel(SourceKey key) {
        if (key.positions().isEmpty()) {
            return Component.translatable("screen.findmyitems.anywhere").getString();
        }
        var p = key.positions().getFirst();
        return p.x() + ", " + p.y() + ", " + p.z();
    }

    /** Why this container cannot be taken from right now, or null when it can. */
    private Component unreachableReason(SourceKey key) {
        var player = Minecraft.getInstance().player;
        if (player == null) return Component.translatable("screen.findmyitems.detail.no_world");
        if (key.positions().isEmpty()) {
            return Component.translatable("screen.findmyitems.detail.remembered_ender");
        }
        if (!key.dimension().equals(player.level().dimension().identifier().toString())) {
            return Component.translatable("screen.findmyitems.detail.other_dimension");
        }
        if (!inReach(key)) {
            return Component.translatable("screen.findmyitems.detail.too_far", (int) Math.sqrt(distanceSqr(key)));
        }
        return null;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Re-reads the index when it has actually changed.
     *
     * <p>The background rescan updates the index on schedule, but nothing used to tell an open
     * catalog about it — results only refreshed when you retyped the search or reopened the screen,
     * which made a 1-second rescan interval look like it was doing nothing. Guarded on the revision
     * so a quiet tick costs one long compare rather than a full re-search.
     */
    @Override
    public void tick() {
        super.tick();
        var executorStatus = FindMyItemsClient.executor().status();
        if (executorStatus != lastExecutorStatus) {
            lastExecutorStatus = executorStatus;
            if (executorStatus == ExecutionStatus.COMPLETE
                    || executorStatus == ExecutionStatus.CANCELLED
                    || executorStatus == ExecutionStatus.FAILED
                    || executorStatus == ExecutionStatus.MISSING
                    || executorStatus == ExecutionStatus.FULL
                    || executorStatus == ExecutionStatus.NO_TABLE) {
                if (!gatherOnlyStatus) status = executorStatus.component();
            }
            refreshChrome();
        }
        if (!FindMyItemsClient.executor().busy()) refreshChrome();
        if (view == View.CRAFTING) {
            var catalog = currentCatalog();
            if (catalog != null && seenRecipeGeneration >= 0 && catalog.generation() != seenRecipeGeneration) {
                seenRecipeGeneration = catalog.generation();
                selectedOutput = null;
                invalidateQuery();
                updateResults(true);
            }
        }
        if (index.revision() == lastSeenRevision) return;
        lastSeenRevision = index.revision();
        if (FindMyItemsClient.executor().busy()) {
            updateResults(true, true);
            return;
        }
        invalidateQuery();
        updateResults(true);
        if (selectedOutput != null) requestPlan();
    }

    // ---------------------------------------------------------------- data

    void updateResults() {
        updateResults(false);
    }

    private void updateResults(boolean preserveScroll) {
        updateResults(preserveScroll, false);
    }

    private void updateResults(boolean preserveScroll, boolean preserveStatus) {
        if (!preserveStatus) status = Component.empty();
        var rows = switch (view) {
            case ITEMS -> itemRows();
            case CONTAINERS -> containerRows();
            case CRAFTING -> craftingRows();
        };
        rowList.setRows(rows, preserveScroll);
    }

    boolean craftingActionsVisible() {
        return gatherButton != null && craftButton != null && gatherButton.visible && craftButton.visible;
    }

    boolean craftingActionsActive() {
        return gatherButton != null && craftButton != null && gatherButton.active && craftButton.active;
    }

    private List<Row> itemRows() {
        var results = index.search(currentQuery);
        resultCount = results.size();
        if (results.isEmpty()) {
            status = currentQuery.isEmpty()
                    ? Component.translatable("screen.findmyitems.empty")
                    : Component.translatable("screen.findmyitems.no_results", currentQuery);
            return List.of();
        }
        return layout() == Layout.LIST
                ? results.stream().<Row>map(ItemRow::new).toList()
                : chunk(results, ItemGridRow::new);
    }

    private List<Row> containerRows() {
        var known = index.snapshot().containers();
        var cards = new ArrayList<ContainerCard>();

        var hasEnder = known.stream().anyMatch(c -> c.contentsKey().equals(SourceKey.enderInventory()));
        // The ender inventory remains reachable without a visible chest, so it heads the list.
        if (!hasEnder && currentQuery.isBlank()) {
            cards.add(ContainerCard.emptyEnder());
        }
        for (var container : known) {
            var card = ContainerCard.of(container);
            if (card.matches(currentQuery)) cards.add(card);
        }

        cards.sort(Comparator.comparing((ContainerCard c) -> !c.isEnder())
                .thenComparingDouble(ContainerCard::distanceSqr));

        resultCount = cards.size();
        if (cards.isEmpty()) {
            status = Component.translatable("screen.findmyitems.no_containers");
            return List.of();
        }
        return layout() == Layout.LIST
                ? cards.stream().<Row>map(ContainerRow::new).toList()
                : chunk(cards, ContainerGridRow::new);
    }

    private List<Row> craftingRows() {
        if (selectedOutput == null) return rootRows();
        if (plannedRows == null) {
            status = Component.translatable(planRequestCount > 1
                    ? "screen.findmyitems.craft.busy"
                    : "screen.findmyitems.craft.calculating");
            return List.of();
        }
        resultCount = plannedRows.size();
        return plannedRows.stream().<Row>map(MaterialRow::new).toList();
    }

    private List<Row> rootRows() {
        var roots = craftingRoots();
        resultCount = roots.size();
        return roots.stream().<Row>map(ItemChoiceRow::new).toList();
    }

    private List<StackKey> craftingRoots() {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var level = mc.level;
        if (server == null || level == null) {
            status = Component.translatable("screen.findmyitems.craft.singleplayer_only");
            return List.of();
        }

        var catalog = recipeCatalog(server.getRecipeManager(), level);
        seenRecipeGeneration = catalog.generation();
        var roots = catalog.craftableRoots();
        var snapshots = roots.stream()
                .map(key -> new StackSnapshot(key, 1, buildStack(key).getHoverName().getString(), List.of()))
                .toList();
        return SearchIndex.rootOnly(snapshots, roots).search(SearchQuery.parse(currentQuery), roots.size())
                .stream().map(document -> document.key()).toList();
    }

    void selectOutput(StackKey key) {
        if (FindMyItemsClient.executor().busy()) {
            FindMyItemsClient.executor().cancel(CraftingExecutor.CancelReason.SELECTION_CHANGED);
        }
        plannedPlan = null;
        plannedRows = null;
        appliedPlanGeneration = -1;
        refreshChrome();
        var catalog = currentCatalog();
        if (catalog == null) return;
        selectedOutput = new OutputIdentity(key, catalog.generation());
        hoveredOutput = null;
        plannedRows = null;
        searchGeneration++;
        requestPlan();
        updateResults();
        refreshChrome();
    }

    private RecipeCatalog currentCatalog() {
        var mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        var level = mc.level;
        return server == null || level == null ? null : recipeCatalog(server.getRecipeManager(), level);
    }

    private void requestPlan() {
        if (view != View.CRAFTING || selectedOutput == null) return;
        var catalog = currentCatalog();
        if (catalog == null || catalog.generation() != selectedOutput.recipeGeneration()) return;

        var requestGeneration = ++planGeneration;
        var queryGeneration = searchGeneration;
        var revision = index.revision();
        var requestedAmount = amount;
        var identity = selectedOutput;
        var inventory = PlanningInventory.of(stock());
        planRequestCount++;
        status = Component.translatable("screen.findmyitems.craft.calculating");
        plannedRows = null;
        CompletableFuture.supplyAsync(() -> CraftingPlanner.plan(catalog, identity.key(), requestedAmount,
                        inventory, dev.smpb.findmyitems.craft.PlanningPolicy.DEFAULT))
                .whenComplete((plan, failure) -> Minecraft.getInstance().execute(() -> {
                    if (failure != null) {
                        if (requestGeneration == planGeneration) {
                            status = Component.translatable("screen.findmyitems.craft.failed");
                            plannedRows = List.of();
                            updateResults();
                        }
                        return;
                    }
                    if (requestGeneration != planGeneration || queryGeneration != searchGeneration
                            || revision != index.revision() || requestedAmount != amount
                            || selectedOutput != identity || currentCatalog() != catalog) return;
                    plannedRows = DisplayPlan.flatten(plan);
                    plannedPlan = plan;
                    appliedPlanGeneration = requestGeneration;
                    status = Component.empty();
                    updateResults();
                    refreshChrome();
                }));
    }

    private void invalidateQuery() {
        if (FindMyItemsClient.executor().busy()) {
            FindMyItemsClient.executor().cancel(CraftingExecutor.CancelReason.QUERY_CHANGED);
        }
        searchGeneration++;
        planGeneration++;
        plannedRows = null;
        plannedPlan = null;
        hoveredOutput = null;
        refreshChrome();
    }

    private void startExecution(CraftingExecutor.Mode mode) {
        if (plannedPlan == null || selectedOutput == null) return;
        var sources = new ArrayList<CraftingExecutor.SourceSnapshot>();
        for (var entry : plannedPlan.consumedDelta().entrySet()) {
            var template = buildStack(entry.getKey());
            if (template.isEmpty()) {
                status = Component.translatable("screen.findmyitems.craft.unavailable");
                return;
            }
            var result = index.search(entry.getKey().itemId()).stream()
                    .filter(item -> item.key().equals(entry.getKey())).findFirst().orElse(null);
            if (result == null) continue;
            var remaining = entry.getValue();
            for (var source : result.sources()) {
                if (source.source().positions().isEmpty()) continue;
                var positions = source.source().positions().stream()
                        .map(p -> new BlockPos(p.x(), p.y(), p.z())).toList();
                for (var location : source.locations()) {
                    if (!location.stack().key().equals(entry.getKey())) continue;
                    var count = (int) Math.min(remaining, location.stack().count());
                    if (count <= 0) continue;
                    sources.add(new CraftingExecutor.SourceSnapshot(entry.getKey(), source.source().dimension(),
                            source.source().kind(), positions, location.stack().provenance().slots(), count,
                            source.contentsKey(), result.sources().stream()
                                    .filter(other -> other.contentsKey().equals(source.contentsKey()))
                                    .map(SourceResult::source).distinct().toList(), template));
                    remaining -= count;
                    if (remaining == 0) break;
                }
                if (remaining == 0) break;
            }
        }
        var executor = FindMyItemsClient.executor();
        executor.setTargetGenerationSupplier(() -> {
            var live = currentCatalog();
            return live == null ? -1 : live.generation();
        });
        executor.start(new CraftingExecutor.ExecutionRequest(plannedPlan, sources, selectedOutput.key(),
                selectedOutput.recipeGeneration(), CraftingExecutor.currentPlayerGeneration(),
                CraftingExecutor.currentWorldGeneration(), mode));
        lastExecutorStatus = executor.status();
        gatherOnlyStatus = mode == CraftingExecutor.Mode.GATHER_ONLY;
        status = gatherOnlyStatus
                ? gatherOnlyStatus(plannedPlan.root(), currentCatalog())
                : executor.status().component();
        refreshChrome();
    }

    private Component gatherOnlyStatus(CraftingPlan.Node node, RecipeCatalog catalog) {
        var tableItems = new LinkedHashSet<String>();
        collectTableRequirements(node, catalog, tableItems);
        if (tableItems.isEmpty()) return Component.translatable("screen.findmyitems.craft.gather_materials");
        return Component.translatable("screen.findmyitems.craft.table_required", String.join(", ", tableItems));
    }

    private void collectTableRequirements(CraftingPlan.Node node, RecipeCatalog catalog, Set<String> tableItems) {
        var recipe = node.selectedRecipe();
        if (node.craftCount() > 0 && recipe != null && recipe.station() == RecipeCatalog.Station.CRAFTING_TABLE) {
            tableItems.add(buildStack(node.item()).getHoverName().getString());
        }
        for (var child : node.children()) collectTableRequirements(child, catalog, tableItems);
    }

    @Override
    public void onClose() {
        if (FindMyItemsClient.executor().busy()) {
            FindMyItemsClient.executor().cancel(CraftingExecutor.CancelReason.SCREEN_CLOSED);
        }
        super.onClose();
    }

    private RecipeCatalog recipeCatalog(RecipeManager recipes, Level level) {
        var fingerprint = recipes.getRecipes().hashCode();
        if (recipes != cachedRecipeManager || cachedRecipeCatalog == null || fingerprint != cachedRecipeFingerprint) {
            cachedRecipeManager = recipes;
            cachedRecipeFingerprint = fingerprint;
            cachedRecipeCatalog = RecipeCatalog.from(recipes, level);
        }
        return cachedRecipeCatalog;
    }

    /**
     * How many of each item the index knows about, keyed by item id.
     *
     * <p>Cached against the index revision: the crafting view asks for this on every keystroke, and
     * a full {@code search("")} walks every slot of every container to answer it.
     */
    private Map<StackKey, Long> stock() {
        if (cachedStock != null && cachedStockRevision == index.revision()) {
            return cachedStock;
        }
        var counts = new HashMap<StackKey, Long>();
        for (var result : index.search("")) {
            counts.merge(result.key(), (long) result.totalCount(), Math::addExact);
        }
        cachedStock = counts;
        cachedStockRevision = index.revision();
        return counts;
    }

    private <T> List<Row> chunk(List<T> values, java.util.function.Function<List<T>, Row> factory) {
        var columns = Math.max(1, (rowList.getRowWidth() - GAP) / CELL_SIZE);
        var rows = new ArrayList<Row>();
        for (int i = 0; i < values.size(); i += columns) {
            rows.add(factory.apply(values.subList(i, Math.min(values.size(), i + columns))));
        }
        return rows;
    }

    // ---------------------------------------------------------------- actions

    private void takeItem(ItemResult item) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) return;

        var server = mc.getSingleplayerServer();
        if (server == null) return;

        var source = nearestReachableSource(item);
        if (source == null) return;

        // The grid reaches this without going past the list row's disabled button, so the guard
        // lives here where every caller passes. A retrieval into a full inventory would open the
        // chest, move nothing and say nothing — indistinguishable from success until you go to build.
        if (RetrieveHandler.roomFor(player, buildStack(item.key())) == 0) {
            player.sendOverlayMessage(Component.translatable("message.findmyitems.inventory_full"));
            return;
        }

        var pos = source.source().positions().getFirst();
        var mcPos = new BlockPos(pos.x(), pos.y(), pos.z());
        var dim = source.source().dimension();
        var itemId = item.key().itemId();
        var componentsJson = item.key().componentsJson();
        var requested = amount;
        var reach = config.retrieveDistanceBlocks;

        GhostOpen.openThen(mcPos, () -> server.execute(() -> {
            var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
            if (serverPlayer == null) return;

            var worldKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim));
            var world = server.getLevel(worldKey);
            if (world == null) return;

            var success = RetrieveHandler.retrieve(
                    serverPlayer, mcPos, dim, itemId, componentsJson, requested, reach, source.source().kind());
            if (!success) return;

            var be = world.getBlockEntity(mcPos);
            if (be instanceof Container container && !be.isRemoved()) {
                var slots = SlotReader.readContainerSlots(container, serverPlayer);
                var contentsKey = SourceKey.storage(dim, source.source().kind(), source.source().positions());
                var observation = new ContainerObservation(contentsKey, List.of(source.source()), slots, Instant.now());

                Minecraft.getInstance().execute(() -> {
                    index.observe(observation);
                    updateResults();
                });
            }
        }));
    }

    /** Glows a container in the world. The glow is the whole feedback — no toast on top of it. */
    private static void locate(SourceKey key) {
        if (key.positions().isEmpty()) return;

        var positions = key.positions().stream()
                .map(p -> new BlockPos(p.x(), p.y(), p.z()))
                .toList();
        ChestHighlighter.highlight(positions, key.dimension());
    }

    /**
     * Pushes matching items from the inventory back into the container they came from. Only
     * offered when that container already stocks this exact item — see
     * {@link RetrieveHandler#deposit}.
     */
    private void depositItem(ItemResult item) {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        var server = mc.getSingleplayerServer();
        if (player == null || server == null) return;

        var source = nearestReachableSource(item);
        if (source == null) return;

        var pos = source.source().positions().getFirst();
        var mcPos = new BlockPos(pos.x(), pos.y(), pos.z());
        var dim = source.source().dimension();
        var itemId = item.key().itemId();
        var componentsJson = item.key().componentsJson();
        var requested = amount;
        var reach = config.retrieveDistanceBlocks;

        GhostOpen.openThen(mcPos, () -> server.execute(() -> {
            var serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
            if (serverPlayer == null) return;

            var worldKey = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim));
            var world = server.getLevel(worldKey);
            if (world == null) return;

            var moved = RetrieveHandler.deposit(serverPlayer, mcPos, itemId, componentsJson, requested, reach,
                    source.source().kind());
            if (moved == 0) return;

            var be = world.getBlockEntity(mcPos);
            if (be instanceof Container container && !be.isRemoved()) {
                var slots = SlotReader.readContainerSlots(container, serverPlayer);
                var contentsKey = SourceKey.storage(dim, source.source().kind(), source.source().positions());
                var observation = new ContainerObservation(contentsKey, List.of(source.source()), slots, Instant.now());

                Minecraft.getInstance().execute(() -> {
                    index.observe(observation);
                    updateResults();
                });
            }
        }));
    }

    /**
     * What clicking Take would actually move, and why it differs from what was asked for.
     *
     * <p>Three numbers meet here: the amount in the box, what the nearest reachable container holds,
     * and how much of it the inventory can still accept. The button has to promise the smallest of
     * them — a tooltip reading "Take 55" over a chest with four chickens in it is a lie the click
     * then exposes — and has to go dead entirely when the answer is zero, because a retrieval that
     * moves nothing still swings the chest lid and still leaves the catalog up, which reads as
     * success to everyone who has ever used it.
     */
    private enum Limit { NONE, ROOM, STOCK, UNREACHABLE }

    private record TakePlan(int count, Limit limit) {}

    private TakePlan planTake(ItemResult item, SourceResult nearest, ItemStack stack) {
        var player = Minecraft.getInstance().player;
        var available = nearest == null ? 0 : nearest.count();
        var room = player == null ? 0 : RetrieveHandler.roomFor(player, stack);
        var count = Math.min(amount, Math.min(available, room));
        if (count >= amount) return new TakePlan(count, Limit.NONE);
        if (room <= available) return new TakePlan(count, Limit.ROOM);
        return new TakePlan(count, unreachableCount(item) > 0 ? Limit.UNREACHABLE : Limit.STOCK);
    }

    /** Stock counted by the row but with no reachable position: a remembered ender inventory. */
    private static int unreachableCount(ItemResult item) {
        return item.sources().stream()
                .filter(source -> source.source().positions().isEmpty())
                .mapToInt(source -> source.count())
                .sum();
    }

    /** How many of this exact item the player is carrying — the cap on what deposit can move. */
    private static int carried(ItemResult item) {
        var player = Minecraft.getInstance().player;
        if (player == null) return 0;

        var inventory = player.getInventory();
        var held = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.isEmpty()) continue;
            if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(item.key().itemId())) continue;
            if (!SlotReader.serializeComponents(stack.getComponentsPatch(), SlotReader.registriesOf(player))
                    .equals(item.key().componentsJson())) continue;
            held += stack.getCount();
        }
        return held;
    }

    private void locateItem(ItemResult item) {
        var source = nearestSource(item);
        if (source != null) locate(source.source());
    }

    /** Finds an indexed item by id so a crafting-tree node can point at a real chest. */
    private ItemResult lookup(StackKey key) {
        return index.search(key.itemId()).stream()
                .filter(result -> result.key().equals(key))
                .findFirst()
                .orElse(null);
    }

    // ---------------------------------------------------------------- geometry helpers

    /** Closest container holding the item in the player's current dimension, at any distance. */
    private static SourceResult nearestSource(ItemResult item) {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;

        var dimension = player.level().dimension().identifier().toString();
        return item.sources().stream()
                .filter(s -> s.source().dimension().equals(dimension))
                .filter(s -> !s.source().positions().isEmpty())
                .filter(s -> s.count() > 0)
                .min(Comparator.comparingDouble(s -> distanceSqr(s.source())))
                .orElse(null);
    }

    private SourceResult nearestReachableSource(ItemResult item) {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        var dimension = player.level().dimension().identifier().toString();
        return item.sources().stream()
                .filter(s -> s.count() > 0)
                .filter(s -> s.source().dimension().equals(dimension))
                .filter(s -> !s.source().positions().isEmpty())
                .filter(s -> inReach(s.source()))
                .min(Comparator.comparingDouble(s -> distanceSqr(s.source())))
                .orElse(null);
    }

    /** Defers to the same reach rule the server enforces, rather than re-deriving a radius here. */
    private boolean inReach(SourceKey source) {
        var player = Minecraft.getInstance().player;
        if (player == null || source.positions().isEmpty()) return false;
        var p = source.positions().getFirst();
        return reachability.check(new BlockPos(p.x(), p.y(), p.z()), TargetKind.CONTAINER).actionable();
    }

    private static double distanceSqr(SourceKey source) {
        var player = Minecraft.getInstance().player;
        if (player == null || source.positions().isEmpty()) return Double.MAX_VALUE;
        var p = source.positions().getFirst();
        var playerPos = player.position();
        var dx = (p.x() + 0.5) - playerPos.x();
        var dy = (p.y() + 0.5) - playerPos.y();
        var dz = (p.z() + 0.5) - playerPos.z();
        return dx * dx + dy * dy + dz * dz;
    }

    static Item containerItem(ContainerKind kind) {
        return switch (kind) {
            case CHEST -> Items.CHEST;
            case TRAPPED_CHEST -> Items.TRAPPED_CHEST;
            case BARREL -> Items.BARREL;
            case SHULKER_BOX -> Items.SHULKER_BOX;
            case ENDER_CHEST -> Items.ENDER_CHEST;
        };
    }

    private static String sourceLabel(SourceResult source) {
        return kindLabel(source.source().kind());
    }

    /** Uses the item's own name so a resource pack or language pack renames it too. */
    static String kindLabel(ContainerKind kind) {
        return new ItemStack(containerItem(kind)).getHoverName().getString();
    }

    /** Stack-size label limited to four characters so it fits in the item-slot corner. */
    static String compactCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 100_000) return (count / 1000) + "k";
        return "99k+";
    }

    static ItemStack buildStack(StackKey key) {
        var id = Identifier.parse(key.itemId());
        var itemHolder = BuiltInRegistries.ITEM.get(id);
        if (itemHolder.isEmpty()) return ItemStack.EMPTY;

        var stack = new ItemStack(itemHolder.get());
        if (!key.componentsJson().equals("{}")) {
            if (key.componentsJson().startsWith("!")) return ItemStack.EMPTY;
            try {
                var json = JsonParser.parseString(key.componentsJson());
                var level = Minecraft.getInstance().level;
                if (level == null) return ItemStack.EMPTY;
                var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
                var pair = DataComponentPatch.CODEC.decode(ops, json).getOrThrow();
                stack.applyComponents(pair.getFirst());
            } catch (Exception ignored) {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    static boolean locateVisible(long indexedCount, boolean hasPosition) {
        return indexedCount > 0 && hasPosition;
    }

    static String automaticStatusKey(long missing, long indexed, boolean reachableStorage,
                                     boolean reachableCraftingTable) {
        if (missing > 0 && !reachableCraftingTable) return "screen.findmyitems.craft.no_reachable_table";
        return indexed > 0 && reachableStorage
                ? "screen.findmyitems.craft.reachable_now"
                : "screen.findmyitems.craft.unavailable";
    }

    // ---------------------------------------------------------------- container cards

    /** One row's worth of facts about a remembered container. */
    private record ContainerCard(SourceKey key, ContainerKind kind, int itemCount, String contents, double distanceSqr) {
        static ContainerCard of(IndexedContainer container) {
            var count = container.slots().stream().mapToInt(s -> s.stack().count()).sum();
            var names = container.slots().stream()
                    .limit(8)
                    .map(s -> s.stack().displayName())
                    .distinct()
                    .toList();
            return new ContainerCard(container.contentsKey(), container.contentsKey().kind(), count,
                    String.join(", ", names), CatalogScreen.distanceSqr(container.contentsKey()));
        }

        static ContainerCard emptyEnder() {
            return new ContainerCard(SourceKey.enderInventory(), ContainerKind.ENDER_CHEST, 0, "", -1);
        }

        boolean isEnder() {
            return kind == ContainerKind.ENDER_CHEST;
        }

        boolean matches(String query) {
            var needle = query.strip().toLowerCase(Locale.ROOT);
            if (needle.isEmpty()) return true;
            return (kindLabel(kind) + " " + position() + " " + key.dimension() + " " + contents)
                    .toLowerCase(Locale.ROOT).contains(needle);
        }

        String position() {
            if (key.positions().isEmpty()) return Component.translatable("screen.findmyitems.anywhere").getString();
            var p = key.positions().getFirst();
            return "%d, %d, %d".formatted(p.x(), p.y(), p.z());
        }

        ItemStack icon() {
            return new ItemStack(containerItem(kind));
        }
    }

    // ---------------------------------------------------------------- widgets

    private final class RowList extends AbstractWidget {
        private final int listWidth;
        private final int rowHeight;
        private List<Row> currentRows = List.of();
        private int renderedRowCount;
        private double scroll;

        RowList(Minecraft minecraft, int listWidth, int screenHeight, int listX, int rowHeight) {
            super(listX, LIST_Y, listWidth, screenHeight - LIST_Y - FOOTER_HEIGHT,
                    Component.translatable("screen.findmyitems.view." + view.name().toLowerCase(Locale.ROOT)));
            this.listWidth = listWidth;
            this.rowHeight = rowHeight;
        }

        void setRows(List<Row> rows, boolean preserveScroll) {
            var oldScroll = scroll;
            currentRows = List.copyOf(rows);
            var layout = ViewportLayout.layout(getX(), getRight(), getY(), getBottom(), rowHeight,
                    currentRows.size(), preserveScroll ? oldScroll : 0, 1);
            scroll = layout.scroll();
        }

        ViewportLayout.Layout viewport() {
            return ViewportLayout.layout(getX(), getRight(), getY(), getBottom(), rowHeight,
                    currentRows.size(), scroll, 1);
        }

        int rowHeight() {
            return rowHeight;
        }

        int getRowWidth() {
            return listWidth - 8;
        }

        double scrollAmount() {
            return scroll;
        }

        void scrollBy(double amount) {
            scroll = ViewportLayout.layout(getX(), getRight(), getY(), getBottom(), rowHeight,
                    currentRows.size(), scroll + amount, 1).scroll();
        }

        int renderedRowCount() {
            return renderedRowCount;
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), LIST_BACKGROUND);
            graphics.outline(getX(), getY(), getWidth(), getHeight(), LIST_BORDER);
            graphics.enableScissor(getX(), getY(), getRight(), getBottom());
            var layout = viewport();
            renderedRowCount = layout.rows().size();
            for (var visible : layout.rows()) {
                var row = currentRows.get(visible.index());
                var actionStart = actionRegions.size();
                row.setBounds(visible.renderTop(), rowHeight, getX() + 2, getRight() - 2);
                row.extractContent(graphics, mouseX, mouseY,
                        layout.hitTest(mouseX, mouseY).stream().anyMatch(index -> index == visible.index()), delta);
                if (visible.height() == 0) {
                    actionRegions.subList(actionStart, actionRegions.size()).clear();
                }
            }
            var maximum = layout.scrollMaximum();
            if (maximum > 0) {
                var trackTop = getY() + 2;
                var trackHeight = getHeight() - 4;
                var thumbHeight = Math.max(12, (int) (trackHeight * getHeight() / (double) (getHeight() + maximum)));
                var thumbTop = trackTop + (int) ((trackHeight - thumbHeight) * layout.scroll() / maximum);
                graphics.fill(getRight() - 6, thumbTop, getRight() - 2, thumbTop + thumbHeight, TEXT_DIM);
            }
            graphics.disableScissor();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }

    List<Row> currentRows() {
        return rowList == null ? List.of() : rowList.currentRows;
    }

    double scrollAmount() {
        return rowList == null ? 0 : rowList.scrollAmount();
    }

    OutputIdentity selectedIdentity() {
        return selectedOutput;
    }

    OutputIdentity hoveredIdentity() {
        return hoveredOutput;
    }

    int visibleRowCount() {
        return rowList == null ? 0 : rowList.viewport().rows().size();
    }

    int renderedRowCount() {
        return rowList == null ? 0 : rowList.renderedRowCount();
    }

    RowBounds firstVisibleRowBounds() {
        if (rowList == null || rowList.viewport().rows().isEmpty()) {
            throw new IllegalStateException("no visible catalog rows");
        }
        var row = rowList.viewport().rows().getFirst();
        return new RowBounds(rowList.getX() + 2, row.top(), rowList.getWidth() - 4, row.height());
    }

    RowBounds lastVisibleRowBounds() {
        if (rowList == null || rowList.viewport().rows().isEmpty()) {
            throw new IllegalStateException("no visible catalog rows");
        }
        var layout = rowList.viewport();
        var row = layout.rowRect(layout.lastVisibleRow());
        return new RowBounds(rowList.getX() + 2, row.top(), rowList.getWidth() - 4, row.height());
    }

    java.util.OptionalInt hitTestRow(double x, double y) {
        return rowList == null ? java.util.OptionalInt.empty() : rowList.viewport().hitTest(x, y);
    }

    int planRequestCount() {
        return planRequestCount;
    }

    String statusText() {
        return status.getString();
    }

    GenerationState generationState() {
        return new GenerationState(searchGeneration, planGeneration, appliedPlanGeneration);
    }

    abstract class Row {
        private double top;
        private double height;
        private int left;
        private int right;

        void setBounds(double top, double height, int left, int right) {
            this.top = top;
            this.height = height;
            this.left = left;
            this.right = right;
        }

        int getY() {
            return (int) Math.round(top);
        }

        int getContentX() {
            return left;
        }

        int getContentRight() {
            return right;
        }

        OutputIdentity outputIdentity() {
            return null;
        }

        abstract void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                     boolean hovered, float delta);

        /** Draws button chrome and reports whether the cursor is over it. */
        boolean actionButton(GuiGraphicsExtractor graphics, int x, int y, Item icon,
                             int mouseX, int mouseY, boolean enabled, Component tooltip) {
            var over = mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
            var background = !enabled ? BUTTON_DISABLED : over ? BUTTON_HOVER : BUTTON;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, background, x, y, BUTTON_SIZE, BUTTON_SIZE);
            // Drawn as an item, not a sprite, so resource packs restyle these buttons too.
            graphics.item(new ItemStack(icon),
                    x + (BUTTON_SIZE - ICON_SIZE) / 2, y + (BUTTON_SIZE - ICON_SIZE) / 2);
            if (over) {
                graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
            }
            return over;
        }

        void slot(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, String count) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x, y, SLOT_SIZE, SLOT_SIZE);
            graphics.item(stack, x + 1, y + 1);
            if (count != null) {
                graphics.itemDecorations(font, stack, x + 1, y + 1, count);
            }
        }
    }

    /** Items view, one item per row: icon, name, where it lives, locate and take. */
    private final class ItemRow extends Row {
        private final ItemResult item;
        private final ItemStack stack;

        ItemRow(ItemResult item) {
            this.item = item;
            this.stack = buildStack(item.key());
        }

        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var top = getY();
            var left = getContentX();
            var right = getContentRight();
            var middle = top + ROW_HEIGHT / 2;

            slot(graphics, stack, left, middle - SLOT_SIZE / 2, compactCount(item.totalCount()));

            var nearest = nearestSource(item);
            var reachable = nearest != null && inReach(nearest.source());
            var outOfReach = Component.translatable("screen.findmyitems.tooltip.out_of_reach");
            var held = carried(item);

            var buttonY = middle - BUTTON_SIZE / 2;
            var takeX = right - BUTTON_SIZE;
            var depositX = takeX - GAP - BUTTON_SIZE;
            var locateX = depositX - GAP - BUTTON_SIZE;

            var textLeft = left + SLOT_SIZE + 8;
            graphics.enableScissor(textLeft, top, locateX - GAP, top + ROW_HEIGHT);
            graphics.text(font, item.displayName(), textLeft, top + 5, TEXT);
            graphics.text(font, subtitle(nearest), textLeft, top + 16, TEXT_DIM);
            graphics.disableScissor();

            var locatable = locateVisible(item.totalCount(), nearest != null);
            actionButton(graphics, locateX, buttonY, Items.ENDER_EYE, mouseX, mouseY, locatable, locatable
                    ? Component.translatable("screen.findmyitems.locate", sourceLabel(nearest))
                    : Component.translatable("screen.findmyitems.tooltip.nowhere"));
            actionRegions.add(ActionRegion.click(locateX, buttonY, () -> locateItem(item)));

            // Deposit is offered only where the chest already stocks this exact item, so the
            // button is dead unless you are carrying some of something that lives there.
            var canDeposit = reachable && held > 0;
            actionButton(graphics, depositX, buttonY, Items.CHEST, mouseX, mouseY, canDeposit,
                    !reachable ? outOfReach
                            : held == 0 ? Component.translatable("screen.findmyitems.deposit.none")
                            : Component.translatable("screen.findmyitems.deposit", Math.min(amount, held)));
            if (canDeposit) {
                actionRegions.add(ActionRegion.take(depositX, buttonY, () -> depositItem(item)));
            }

            var plan = planTake(item, nearest, stack);
            var canTake = reachable && plan.count() > 0;
            actionButton(graphics, takeX, buttonY, Items.HOPPER, mouseX, mouseY, canTake,
                    !reachable ? outOfReach : takeTooltip(plan));
            if (canTake) {
                actionRegions.add(ActionRegion.take(takeX, buttonY, () -> takeItem(item)));
            }
        }

        /** Names the reason the button promises less than the amount box asks for. */
        private Component takeTooltip(TakePlan plan) {
            return switch (plan.limit()) {
                case NONE -> Component.translatable("screen.findmyitems.take", plan.count());
                case ROOM -> plan.count() == 0
                        ? Component.translatable("screen.findmyitems.take.full")
                        : Component.translatable("screen.findmyitems.take.max.room", plan.count());
                case STOCK -> Component.translatable("screen.findmyitems.take.max.stock", plan.count());
                case UNREACHABLE -> Component.translatable(
                        "screen.findmyitems.take.max.unreachable", plan.count(), unreachableCount(item));
            };
        }

        private String subtitle(SourceResult nearest) {
            // Distinct containers, not access positions: a double chest is one chest, not two.
            var containers = (int) item.sources().stream().map(SourceResult::contentsKey).distinct().count();
            var where = Component.translatable(containers == 1
                    ? "screen.findmyitems.in_container"
                    : "screen.findmyitems.in_containers", containers).getString();
            if (nearest != null) {
                var pos = nearest.source().positions().getFirst();
                where += " · " + pos.x() + ", " + pos.y() + ", " + pos.z();
            }
            var unreachable = unreachableCount(item);
            if (unreachable > 0) {
                where += " · " + Component.translatable(
                        "screen.findmyitems.unreachable", unreachable).getString();
            }
            return where;
        }

        public Component getNarration() {
            return Component.literal(item.displayName() + ", " + item.totalCount());
        }
    }

    /** Items view, grid layout: a strip of item slots. Left-click takes, right-click locates. */
    private final class ItemGridRow extends Row {
        private final List<ItemResult> items;

        ItemGridRow(List<ItemResult> items) {
            this.items = items;
        }

        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var x = getContentX();
            var y = getY() + (CELL_SIZE - SLOT_SIZE) / 2;

            for (var item : items) {
                var stack = buildStack(item.key());
                slot(graphics, stack, x, y, compactCount(item.totalCount()));

                if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, CELL_HOVER);
                    graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
                    hoveredItem = item;
                }
                actionRegions.add(ActionRegion.grid(x, y, SLOT_SIZE,
                        () -> takeItem(item), () -> locateItem(item)));
                x += CELL_SIZE;
            }
        }

        public Component getNarration() {
            return Component.translatable("screen.findmyitems.view.items");
        }
    }

    /** Containers view, one container per row. */
    private final class ContainerRow extends Row {
        private final ContainerCard card;

        ContainerRow(ContainerCard card) {
            this.card = card;
        }

        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var top = getY();
            var left = getContentX();
            var right = getContentRight();
            var middle = top + ROW_HEIGHT / 2;

            slot(graphics, card.icon(), left, middle - SLOT_SIZE / 2,
                    card.itemCount() > 0 ? compactCount(card.itemCount()) : null);

            var buttonY = middle - BUTTON_SIZE / 2;
            var locateX = right - BUTTON_SIZE;
            var locatable = locateVisible(card.itemCount(), !card.key().positions().isEmpty());

            var textLeft = left + SLOT_SIZE + 8;
            graphics.enableScissor(textLeft, top, locateX - GAP, top + ROW_HEIGHT);
            graphics.text(font, kindLabel(card.kind()) + "  " + card.position(), textLeft, top + 5, TEXT);
            graphics.text(font, summary(), textLeft, top + 16, TEXT_DIM);
            graphics.disableScissor();

            actionButton(graphics, locateX, buttonY, Items.ENDER_EYE, mouseX, mouseY, locatable, locatable
                    ? Component.translatable("screen.findmyitems.locate", kindLabel(card.kind()))
                    : Component.translatable("screen.findmyitems.tooltip.nowhere"));
            actionRegions.add(ActionRegion.click(locateX, buttonY, () -> locate(card.key())));
        }

        private String summary() {
            if (card.itemCount() == 0) {
                return Component.translatable("screen.findmyitems.container.empty").getString();
            }
            return Component.translatable("screen.findmyitems.container.holds", card.itemCount()).getString()
                    + (card.contents().isEmpty() ? "" : " · " + card.contents());
        }

        public Component getNarration() {
            return Component.literal(kindLabel(card.kind()) + " " + card.position());
        }
    }

    /** Containers view, grid layout: container icons, click to locate. */
    private final class ContainerGridRow extends Row {
        private final List<ContainerCard> cards;

        ContainerGridRow(List<ContainerCard> cards) {
            this.cards = cards;
        }

        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var x = getContentX();
            var y = getY() + (CELL_SIZE - SLOT_SIZE) / 2;

            for (var card : cards) {
                slot(graphics, card.icon(), x, y, card.itemCount() > 0 ? compactCount(card.itemCount()) : null);

                if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, CELL_HOVER);
                    graphics.setComponentTooltipForNextFrame(font, List.of(
                            Component.literal(kindLabel(card.kind())),
                            Component.literal(card.position()),
                            Component.translatable("screen.findmyitems.container.holds", card.itemCount())
                    ), mouseX, mouseY);
                }
                actionRegions.add(ActionRegion.grid(x, y, SLOT_SIZE, () -> locate(card.key()), null));
                x += CELL_SIZE;
            }
        }

        public Component getNarration() {
            return Component.translatable("screen.findmyitems.view.containers");
        }
    }

    /** Crafting view with an empty box: pick an item to plan. Clicking one types it into the search. */
    private final class ItemChoiceRow extends Row {
        private final StackKey key;
        private final ItemStack stack;

        ItemChoiceRow(StackKey key) {
            this.key = key;
            this.stack = buildStack(key);
        }

        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var top = getY();
            var left = getContentX();
            var right = getContentRight();
            var middle = top + ROW_HEIGHT / 2;

            if (mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + ROW_HEIGHT) {
                graphics.fill(left, top, right, top + ROW_HEIGHT, CELL_HOVER);
                var catalog = currentCatalog();
                if (catalog != null) hoveredOutput = new OutputIdentity(key, catalog.generation());
            }

            slot(graphics, stack, left, middle - SLOT_SIZE / 2, null);
            graphics.text(font, stack.getHoverName().getString(), left + SLOT_SIZE + 8, middle - 4, TEXT);

            actionRegions.add(ActionRegion.row(left, top, right, top + ROW_HEIGHT, this::plan));
        }

        /** Selects this exact component-aware output as the planning root. */
        private void plan() {
            selectOutput(key);
        }

        @Override
        OutputIdentity outputIdentity() {
            var catalog = currentCatalog();
            return catalog == null ? null : new OutputIdentity(key, catalog.generation());
        }

        public Component getNarration() {
            return stack.getHoverName();
        }
    }

    /** Crafting view: one semantic display row from the authoritative planner. */
    private final class MaterialRow extends Row {
        private final DisplayPlan.Row material;

        MaterialRow(DisplayPlan.Row material) {
            this.material = material;
        }

        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            var top = getY();
            var left = getContentX() + material.depth() * INDENT;
            var right = getContentRight();
            var middle = top + ROW_HEIGHT / 2;
            var stack = buildStack(material.item());

            slot(graphics, stack, left, middle - SLOT_SIZE / 2, compactCount(material.requested()));

            var buttonY = middle - BUTTON_SIZE / 2;
            var locateX = right - BUTTON_SIZE;
            var found = material.indexed() > 0 ? lookup(material.item()) : null;

            var textLeft = left + SLOT_SIZE + 8;
            graphics.enableScissor(textLeft, top, locateX - GAP, top + ROW_HEIGHT);
            graphics.text(font, stack.getHoverName().getString(), textLeft, top + 5, TEXT);
            graphics.text(font, status(), textLeft, top + 16, statusColor());
            graphics.disableScissor();

            if (mouseX >= left && mouseX < left + SLOT_SIZE && mouseY >= middle - SLOT_SIZE / 2 && mouseY < middle + SLOT_SIZE / 2) {
                graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
            }

            actionButton(graphics, locateX, buttonY, Items.ENDER_EYE, mouseX, mouseY, found != null, found != null
                    ? Component.translatable("screen.findmyitems.locate.quantity", material.requested(),
                    stack.getHoverName().getString())
                    : Component.translatable("screen.findmyitems.tooltip.nowhere"));
            if (found != null) {
                actionRegions.add(ActionRegion.click(locateX, buttonY, () -> locateItem(found)));
            }
        }

        private String status() {
            var accessKey = automaticStatusKey(material.missing(), material.indexed(),
                    hasReachableStorage(material), hasReachableCraftingTable());
            var access = Component.translatable(accessKey).getString();
            if (material.missing() == 0) {
                return Component.translatable("screen.findmyitems.craft.known_in_storage", material.indexed()).getString()
                        + " · " + access;
            }
            return Component.translatable("screen.findmyitems.craft.missing_materials", material.missing(),
                    material.indexed()).getString() + " · " + access;
        }

        private boolean hasReachableStorage(DisplayPlan.Row row) {
            var found = row.indexed() > 0 ? lookup(row.item()) : null;
            return found != null && nearestReachableSource(found) != null;
        }

        private boolean hasReachableCraftingTable() {
            var player = Minecraft.getInstance().player;
            if (player == null) return false;
            var radius = Math.max(4, config.retrieveDistanceBlocks);
            var center = player.blockPosition();
            return BlockPos.betweenClosedStream(center.offset(-radius, -radius, -radius),
                            center.offset(radius, radius, radius))
                    .anyMatch(pos -> player.level().getBlockState(pos).is(Blocks.CRAFTING_TABLE)
                            && reachability.check(pos, TargetKind.CRAFTING_TABLE).actionable());
        }
        private int statusColor() {
            if (material.missing() == 0) return TEXT_OK;
            return TEXT_MISSING;
        }

        public Component getNarration() {
            return Component.literal(buildStack(material.item()).getHoverName().getString() + " " + status());
        }
    }

    /**
     * A clickable box collected while rendering. Rows are drawn before clicks are dispatched, so
     * this is how a list entry publishes where its buttons ended up.
     */
    private record ActionRegion(int left, int top, int right, int bottom,
                                Runnable primary, Runnable secondary, boolean scrollsAmount) {
        static ActionRegion click(int x, int y, Runnable action) {
            return new ActionRegion(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, action, null, false);
        }

        static ActionRegion take(int x, int y, Runnable action) {
            return new ActionRegion(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, action, null, true);
        }

        static ActionRegion grid(int x, int y, int size, Runnable primary, Runnable secondary) {
            return new ActionRegion(x, y, x + size, y + size, primary, secondary, false);
        }

        static ActionRegion row(int left, int top, int right, int bottom, Runnable action) {
            return new ActionRegion(left, top, right, bottom, action, null, false);
        }

        boolean contains(int mx, int my) {
            return mx >= left && mx < right && my >= top && my < bottom;
        }
    }
}
