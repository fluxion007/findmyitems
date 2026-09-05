package dev.smpb.findmyitems.test;

import dev.smpb.findmyitems.FindMyItemsClient;
import dev.smpb.findmyitems.config.ConfigScreen;
import dev.smpb.findmyitems.config.ConfigScreenTestAccess;
import dev.smpb.findmyitems.gui.CatalogScreen;
import dev.smpb.findmyitems.gui.CatalogScreenTestAccess;
import dev.smpb.findmyitems.gui.ChestHighlighter;
import dev.smpb.findmyitems.craft.CraftingPlan;
import dev.smpb.findmyitems.craft.RecipeCatalog;
import dev.smpb.findmyitems.craft.PlanScore;
import dev.smpb.findmyitems.craft.PlanningInventory;
import dev.smpb.findmyitems.index.ItemResult;
import dev.smpb.findmyitems.model.ContainerKind;
import dev.smpb.findmyitems.model.BlockPosition;
import dev.smpb.findmyitems.model.SourceKey;
import dev.smpb.findmyitems.model.StackKey;
import dev.smpb.findmyitems.retrieval.CraftingExecutor;
import dev.smpb.findmyitems.retrieval.ExecutionStatus;
import dev.smpb.findmyitems.retrieval.GhostOpen;
import dev.smpb.findmyitems.search.InventorySearchController;
import net.minecraft.client.gui.components.EditBox;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.FurnaceScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;

/**
 * End-to-end test that boots the real Minecraft client, creates a world, and drives the mod
 * through actual input: right-click a chest, open the catalog with the keybind, type a query.
 *
 * <p>Run with {@code ./gradlew runClientGameTest}. Screenshots land in
 * {@code run/clientGameTest/screenshots/} — they are the "what does it look like" artifact,
 * so the assertions below stay on facts (index contents, which screen is open) rather than pixels.
 */
public final class FindMyItemsClientGameTest implements FabricClientGameTest {
    /** Platform + chest are built in the air so terrain generation cannot get in the way. */
    private static final BlockPos STAND = new BlockPos(0, 100, 0);
    private static final BlockPos CHEST = new BlockPos(0, 100, 2);
    private static final BlockPos ENDER = new BlockPos(2, 100, 2);
    private static final BlockPos FURNACE = new BlockPos(-2, 100, 2);
    private static final BlockPos CRAFTING_TABLE = new BlockPos(0, 100, 3);

    private static final int DIAMONDS = 32;
    /** Sits inside a shulker box that sits inside the chest. */
    private static final int BURIED_GOLD = 5;
    /** Issue #14: emeralds split between a block chest and the ender inventory. */
    private static final int CHEST_EMERALDS = 5;
    private static final int ENDER_EMERALDS = 10;

    @Override
    public void runTest(ClientGameTestContext context) {
        var suiteStarted = System.nanoTime();
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();

            var server = singleplayer.getServer();
            server.runCommand("gamemode creative");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("time set noon");
            buildScene(server);

            context.waitTicks(20);
            singleplayer.getClientLevel().waitForChunksRender();

            openChest(context);
            assertFilterBarVisible(context, true);
            assertIndexed(context);
            assertContainerFilterSearchesTooltips(context);

            context.setScreen(() -> null);
            openFurnace(context);
            assertFilterBarVisible(context, false);

            context.setScreen(() -> null);
            waitForCondition(context, "furnace screen closed", 20, mc -> mc.gui.screen() == null);

            enderChestTotalsStayHonest(context, server);
            logPhase(suiteStarted, "ender conservation");

            openCatalog(context);
            assertTickDrivenDiamondPickaxe(context, server);
            assertLocateAndAutomaticRetrievalLabels(context);
            assertDefaultCatalogAmount(context);
            search(context, "diamond");
            context.takeScreenshot("items-list-search");

            assertNestedShulkerIsSearchable(context);

            clearSearch(context, "diamond".length());

            click(context, "screen.findmyitems.layout.grid");
            context.takeScreenshot("items-grid");
            hoverFirstGridCell(context);
            context.takeScreenshot("items-grid-detail-reachable");

            // The emerald is the interesting cell: its stock is remembered with no chest to open.
            context.getInput().typeChars("emer");
            waitForCondition(context, "typed search \"emer\"", 20,
                    mc -> mc.gui.screen() instanceof CatalogScreen && catalogSearchText(mc).equals("emer"));
            hoverFirstGridCell(context);
            context.takeScreenshot("items-grid-detail");
            clearSearch(context, "emer".length());

            click(context, "screen.findmyitems.layout.list");

            click(context, "screen.findmyitems.view.containers");
            context.takeScreenshot("containers-list");
            click(context, "screen.findmyitems.layout.grid");
            context.takeScreenshot("containers-grid");
            click(context, "screen.findmyitems.layout.list");

            // Ctrl+3 is the shortcut for the third view; the tab buttons are already covered above.
            switchViewByShortcut(context, GLFW.GLFW_KEY_3, "CRAFTING");
            context.takeScreenshot("crafting-index");
            assertCraftingIndexIsPopulated(context);
            assertCraftingBrowseIsLazyAndRootBased(context);
            assertCraftingViewportAndScroll(context);
            context.runOnClient(mc -> requireCatalog(mc).mouseScrolled(200, 100, 0, 20));
            context.waitTicks(2);

            clickFirstCraftingRow(context);
            assertSingleSelectedPlan(context);
            assertCraftingActionsVisible(context);
            assertGenerationInvalidation(context);

            context.getInput().typeChars("not-a-real-item");
            waitForCondition(context, "typed search \"not-a-real-item\"", 20,
                    mc -> mc.gui.screen() instanceof CatalogScreen
                            && catalogSearchText(mc).equals("not-a-real-item"));
            assertSelectionClearsAfterFilter(context);
            context.takeScreenshot("crafting-tree");

            switchViewByShortcut(context, GLFW.GLFW_KEY_1, "ITEMS");
            assertShowingItems(context);
            logPhase(suiteStarted, "catalog browse");

            context.setScreen(() -> null);
            assertGhostOpenRefusesBlockedChest(context, server);
            highlightTheChest(context);
            context.takeScreenshot("chest-highlighted");
            assertExecutorBusyGuard(context);
            assertExecutorRefusesFullInventory(context, server);
            assertExecutorCancelsDeletedSource(context, server);
            assertExecutorCancelsMovement(context, server);
            assertExecutorCancelsClosedScreen(context);
            assertExecutorCancelsQueryAndSelection(context);
            assertExecutorCancelsChangedTarget(context);
            assertCancellationConservesSourceAndPlayerTotals(context, server);
            assertGatherOnlyShowsTableRequirementAfterInventorySubrecipe(context, server);
            assertExecutorReportsMenuActionFailure(context, server);
            assertExecutorTimesOutWhenMenuCallbackIsDelayed(context, server);
            assertSourceSnapshotTemplateIsDefensive(context);
            assertCreativeCraftedOutputOverflowIsRetained(context, server);
            assertSettingsScreenPreservesAndPersists(context);
            assertLayoutTogglePersistsAndResizeKeepsQuery(context, server);
            assertExecutorFailsWithoutTable(context, server);
            logPhase(suiteStarted, "suite complete");
        }
    }

    private static void logPhase(long suiteStarted, String phase) {
        System.out.println("[FindMyItemsClientGameTest] " + phase + " +"
                + (System.nanoTime() - suiteStarted) / 1_000_000 + "ms");
    }

    /**
     * A bounded condition check: returns as soon as the state holds, fails loudly on timeout.
     * Replaces blind tick counts everywhere the wait serves a real postcondition; the explicit
     * re-check after the wait covers either timeout semantic of the underlying call.
     */
    private static void waitForCondition(ClientGameTestContext context, String what, int timeoutTicks,
            java.util.function.Predicate<net.minecraft.client.Minecraft> condition) {
        try {
            context.waitFor(condition, timeoutTicks);
        } catch (RuntimeException timeout) {
            throw new AssertionError("timed out after " + timeoutTicks + " ticks waiting for " + what,
                    timeout);
        }
        var settled = context.computeOnClient(condition::test);
        if (!settled) {
            throw new AssertionError("wait finished without reaching: " + what);
        }
    }

    private static void switchViewByShortcut(ClientGameTestContext context, int digit, String expectedView) {
        context.getInput().holdControl();
        context.getInput().pressKey(digit);
        context.getInput().releaseControl();
        // The key event lands on a later tick; poll for the view instead of sleeping blindly.
        waitForCondition(context, "view " + expectedView, 20,
                mc -> mc.gui.screen() instanceof CatalogScreen screen
                        && CatalogScreenTestAccess.viewName(screen).equals(expectedView));
    }

    private static void assertExecutorBusyGuard(ClientGameTestContext context) {
        var result = context.computeOnClient(mc -> {
            var key = new StackKey("minecraft:diamond_pickaxe", "{}");
            var plan = CraftingPlan.root(key, 1, PlanningInventory.empty(), new PlanScore(0, 0, 0, 0, 0));
            var request = new CraftingExecutor.ExecutionRequest(plan, List.of(), 0, 0,
                    CraftingExecutor.Mode.GATHER_ONLY);
            var executor = FindMyItemsClient.executor();
            var first = executor.start(request);
            var second = executor.start(request);
            var cancelled = executor.cancel(CraftingExecutor.CancelReason.SELECTION_CHANGED);
            executor.start(request);
            executor.tick();
            var actions = executor.actionsLastTick();
            var replaced = executor.replace(request);
            var mismatch = new CraftingExecutor.ExecutionRequest(plan, List.of(),
                    new StackKey("minecraft:stick", "{}"), 1, 0, 0, CraftingExecutor.Mode.GATHER_ONLY);
            executor.start(mismatch);
            executor.tick();
            var targetChanged = executor.status();
            return new ExecutionStatus[] {first, second, cancelled, replaced,
                    actions <= 1 ? ExecutionStatus.COMPLETE : ExecutionStatus.FAILED, targetChanged};
        });
        if (result[0] != ExecutionStatus.CALCULATING || result[1] != ExecutionStatus.BUSY
                || result[2] != ExecutionStatus.CANCELLED || result[3] != ExecutionStatus.CALCULATING
                 || result[4] != ExecutionStatus.COMPLETE || result[5] != ExecutionStatus.CANCELLED) {
            throw new AssertionError("executor must reject overlapping requests and record cancellation: "
                    + java.util.Arrays.toString(result));
        }
    }

    private static void assertExecutorRefusesFullInventory(
            ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        resetExecutorFixture(context, server, 1, 0);
        var result = context.computeOnClient(mc -> {
            fillClientInventory(mc);
            var diamond = new StackKey("minecraft:diamond", "{}");
            var plan = executorPlan(new StackKey("minecraft:diamond_pickaxe", "{}"),
                    Map.of(diamond, 1L), 1);
            var source = sourceSnapshot(diamond, CHEST, 1, 0);
            var executor = FindMyItemsClient.executor();
            executor.start(new CraftingExecutor.ExecutionRequest(plan, List.of(source),
                    CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(),
                    CraftingExecutor.Mode.GATHER_AND_CRAFT));
            executor.tick();
            return new Object[] {executor.status(), executor.state()};
        });
        var sourceStillHasItem = server.computeOnServer(s -> s.overworld().getBlockEntity(CHEST) instanceof ChestBlockEntity chest
                && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 1);
        if (result[0] != ExecutionStatus.FULL || result[1] != CraftingExecutor.State.FAILED || !sourceStillHasItem) {
            throw new AssertionError("full inventory must refuse before opening the source: "
                    + java.util.Arrays.toString(result));
        }
    }

    private static void assertExecutorCancelsDeletedSource(
            ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        resetExecutorFixture(context, server, 1, 0);
        var started = context.computeOnClient(mc -> {
            var diamond = new StackKey("minecraft:diamond", "{}");
            var plan = executorPlan(new StackKey("minecraft:diamond_pickaxe", "{}"), Map.of(diamond, 1L), 0);
            var executor = FindMyItemsClient.executor();
            executor.start(new CraftingExecutor.ExecutionRequest(plan,
                    List.of(sourceSnapshot(diamond, CHEST, 1, 0)),
                    CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(),
                    CraftingExecutor.Mode.GATHER_ONLY));
            executor.tick();
            return executor.state();
        });
        if (started != CraftingExecutor.State.GATHER) throw new AssertionError("preflight did not enter gather");
        server.runOnServer(s -> ((ChestBlockEntity) s.overworld().getBlockEntity(CHEST)).setItem(0, ItemStack.EMPTY));
        runExecutorTicks(context, 80);
        assertCancelled(context, "SOURCE_CHANGED");
    }

    private static void assertExecutorCancelsMovement(
            ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        resetExecutorFixture(context, server, 1, 0);
        context.computeOnClient(mc -> {
            var diamond = new StackKey("minecraft:diamond", "{}");
            var plan = executorPlan(new StackKey("minecraft:diamond_pickaxe", "{}"), Map.of(diamond, 1L), 0);
            FindMyItemsClient.executor().start(new CraftingExecutor.ExecutionRequest(plan,
                    List.of(sourceSnapshot(diamond, CHEST, 1, 0)),
                    CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(),
                    CraftingExecutor.Mode.GATHER_ONLY));
            return null;
        });
        context.runOnClient(mc -> {
            mc.player.setNoGravity(true);
            mc.player.setPos(CHEST.getX() + 40.5, CHEST.getY() + 1, CHEST.getZ() + 0.5);
        });
        waitForCondition(context, "cancellation after moving away", 20,
                mc -> FindMyItemsClient.executor().status() == ExecutionStatus.CANCELLED);
        assertCancelled(context, "OUT_OF_REACH");
        context.runOnClient(mc -> {
            mc.player.setNoGravity(false);
            mc.player.setPos(STAND.getX() + 0.5, STAND.getY() + 1, STAND.getZ() + 0.5);
        });
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(player ->
                player.setPos(STAND.getX() + 0.5, STAND.getY() + 1, STAND.getZ() + 0.5)));
        context.waitTicks(2);
    }

    private static void assertExecutorCancelsClosedScreen(ClientGameTestContext context) {
        openCatalog(context);
        var result = context.computeOnClient(mc -> {
            var executor = startIdleExecutor(CraftingExecutor.Mode.GATHER_ONLY);
            return executor.state();
        });
        context.runOnClient(mc -> ((CatalogScreen) mc.gui.screen()).onClose());
        context.setScreen(() -> null);
        if (result == CraftingExecutor.State.IDLE
                || context.computeOnClient(mc -> FindMyItemsClient.executor().status()) != ExecutionStatus.CANCELLED) {
            throw new AssertionError("closing the catalog must cancel an active executor");
        }
    }

    private static void assertExecutorCancelsQueryAndSelection(ClientGameTestContext context) {
        openCatalog(context);
        context.computeOnClient(mc -> startIdleExecutor(CraftingExecutor.Mode.GATHER_ONLY).state());
        context.getInput().typeChars("diamond");
        waitForCondition(context, "cancellation after typing", 20,
                mc -> FindMyItemsClient.executor().status() == ExecutionStatus.CANCELLED);
        assertCancelled(context, "QUERY_CHANGED");

        clearSearch(context, "diamond".length());
        switchViewByShortcut(context, GLFW.GLFW_KEY_3, "CRAFTING");
        var pending = context.computeOnClient(mc -> startPendingExecutor(CraftingExecutor.Mode.GATHER_ONLY).status());
        if (pending != ExecutionStatus.CALCULATING) {
            throw new AssertionError("selection cancellation fixture did not become active: " + pending);
        }
        clickFirstCraftingRow(context);
        var cancelled = context.computeOnClient(mc -> FindMyItemsClient.executor().status());
        if (cancelled != ExecutionStatus.CANCELLED) {
            throw new AssertionError("selecting a different catalog output must cancel the active executor, status="
                    + cancelled);
        }
    }

    private static void assertExecutorCancelsChangedTarget(ClientGameTestContext context) {
        var result = context.computeOnClient(mc -> {
            var executor = startIdleExecutor(CraftingExecutor.Mode.GATHER_ONLY);
            executor.setTargetGenerationSupplier(() -> 99);
            var plan = executorPlan(new StackKey("minecraft:diamond_pickaxe", "{}"), Map.of(), 0);
            executor.replace(new CraftingExecutor.ExecutionRequest(plan, List.of(),
                    new StackKey("minecraft:diamond_pickaxe", "{}"), 1,
                    CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(),
                    CraftingExecutor.Mode.GATHER_ONLY));
            executor.tick();
            return executor.transferJournal().getLast().note();
        });
        if (!result.equals("cancelled:target_changed")) {
            throw new AssertionError("recipe generation changes must cancel execution: " + result);
        }
    }

    private static void assertCancellationConservesSourceAndPlayerTotals(
            ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        resetExecutorFixture(context, server, 3, 0);
        server.runOnServer(s -> {
            var nested = new ItemStack(Items.SHULKER_BOX);
            nested.set(DataComponents.CONTAINER,
                    ItemContainerContents.fromItems(List.of(new ItemStack(Items.GOLD_INGOT, 20))));
            ((ChestBlockEntity) s.overworld().getBlockEntity(CHEST)).setItem(2, nested);
        });
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(player ->
                player.containerMenu.setCarried(new ItemStack(Items.GOLD_INGOT, 2))));
        var before = executorAccounting(context, server);
        if (before.getOrDefault("minecraft:gold_ingot|{}", 0L) != 22L) {
            throw new AssertionError("conservation accounting must include nested contents and menu cursor: " + before);
        }
        context.computeOnClient(mc -> {
            var diamond = new StackKey("minecraft:diamond", "{}");
            var plan = executorPlan(new StackKey("minecraft:diamond_pickaxe", "{}"), Map.of(diamond, 3L), 0);
            FindMyItemsClient.executor().start(new CraftingExecutor.ExecutionRequest(plan,
                    List.of(sourceSnapshot(diamond, CHEST, 3, 0)),
                    CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(),
                    CraftingExecutor.Mode.GATHER_ONLY));
            return null;
        });
        runExecutorTicks(context, 40);
        context.runOnClient(mc -> FindMyItemsClient.executor().cancel(CraftingExecutor.CancelReason.SELECTION_CHANGED));
        var after = executorAccounting(context, server);
        if (!before.equals(after)) throw new AssertionError("cancellation lost source/player/cursor items: before="
                + before + " after=" + after);
    }

    private static void assertGatherOnlyShowsTableRequirementAfterInventorySubrecipe(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        resetExecutorFixture(context, server, 3, 0);
        server.runOnServer(s -> {
            var chest = (ChestBlockEntity) s.overworld().getBlockEntity(CHEST);
            chest.setItem(1, new ItemStack(Items.OAK_LOG, 12));
        });
        // The 1-second rescan picks the restock up; poll for it instead of sleeping through four.
        waitForCondition(context, "rescan noticing the oak logs", 120,
                mc -> FindMyItemsClient.index().search("oak log").stream()
                        .mapToInt(r -> r.totalCount()).sum() >= 12);
        openCatalog(context);
        switchViewByShortcut(context, GLFW.GLFW_KEY_3, "CRAFTING");
        context.getInput().typeChars("diamond_pickaxe");
        waitForCondition(context, "typed search \"diamond_pickaxe\"", 20,
                mc -> mc.gui.screen() instanceof CatalogScreen
                        && catalogSearchText(mc).equals("diamond_pickaxe"));
        clickFirstCraftingRow(context);
        waitForCondition(context, "applied pickaxe plan", 80,
                mc -> mc.gui.screen() instanceof CatalogScreen screen
                        && CatalogScreenTestAccess.selectionState(screen).selected()
                        && CatalogScreenTestAccess.selectionState(screen).generations()
                                .appliedPlanGeneration() == CatalogScreenTestAccess.selectionState(screen)
                                        .generations().planGeneration());
        var before = executorAccounting(context, server);
        context.clickScreenButton("screen.findmyitems.craft.gather_materials");
        var actionStatus = context.computeOnClient(mc -> CatalogScreenTestAccess.statusText(requireCatalog(mc)));
        context.runOnClient(mc -> FindMyItemsClient.index().replace(FindMyItemsClient.index().snapshot()));
        context.waitTicks(1);
        var refreshedStatus = context.computeOnClient(mc -> CatalogScreenTestAccess.statusText(requireCatalog(mc)));
        if (!refreshedStatus.equals(actionStatus)) {
            throw new AssertionError("index refresh must preserve an active execution status: before="
                    + actionStatus + " after=" + refreshedStatus);
        }
        runExecutorTicks(context, 100);
        context.waitTicks(1);
        var result = context.computeOnClient(mc -> new Object[] {
                FindMyItemsClient.executor().tableRequiredMaterials(),
                CatalogScreenTestAccess.rowCount(requireCatalog(mc)),
                FindMyItemsClient.executor().status(),
                CatalogScreenTestAccess.craftingActionsActive(requireCatalog(mc))
        });
        var after = executorAccounting(context, server);
        var expectedDelta = Map.of(
                "minecraft:oak_log|{}", -1L,
                "minecraft:oak_planks|{}", 2L,
                "minecraft:oak_pressure_plate|{}", 1L);
        if (!actionStatus.contains("crafting table required for")
                || !actionStatus.contains("Diamond Pickaxe")
                || !((List<?>) result[0]).contains(new StackKey("minecraft:diamond_pickaxe", "{}"))
                || Integer.parseInt(result[1].toString()) == 0
                || result[2] != ExecutionStatus.COMPLETE
                || !Boolean.TRUE.equals(result[3])
                || !accountingDelta(before, after).equals(expectedDelta)) {
            throw new AssertionError("gather-only UI/accounting mismatch: status=" + actionStatus
                    + " required=" + result[0] + " rows=" + result[1] + " executor=" + result[2]
                    + " active=" + result[3]
                    + " before=" + before + " after=" + after + " delta=" + accountingDelta(before, after));
        }
    }

    private static void assertExecutorReportsMenuActionFailure(
            ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        resetExecutorFixture(context, server, 0, 0);
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(player -> {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
            player.getInventory().setItem(1, new ItemStack(Items.STICK, 2));
        }));
        context.waitTicks(2);
        var executor = context.computeOnClient(mc -> {
            var playerGeneration = CraftingExecutor.currentPlayerGeneration();
            var worldGeneration = CraftingExecutor.currentWorldGeneration();
            return new CraftingExecutor(FindMyItemsClient.index(), FindMyItemsClient.config(),
                    () -> playerGeneration, () -> worldGeneration);
        });
        var result = context.computeOnClient(mc -> {
            mc.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
            mc.player.getInventory().setItem(1, new ItemStack(Items.STICK, 2));
            var diamond = new StackKey("minecraft:diamond", "{}");
            var sticks = new StackKey("minecraft:stick", "{}");
            var plan = executorPlan(new StackKey("minecraft:diamond_pickaxe", "{}"),
                    Map.of(diamond, 1L, sticks, 2L), 1);
            executor.start(new CraftingExecutor.ExecutionRequest(plan, List.of(),
                    CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(),
                    CraftingExecutor.Mode.GATHER_AND_CRAFT));
            return executor.state();
        });
        if (result == CraftingExecutor.State.IDLE) throw new AssertionError("menu failure fixture did not start");
        var reachedAction = false;
        for (int tick = 0; tick < 80; tick++) {
            context.waitTicks(1);
            context.runOnClient(mc -> executor.tick());
            if (context.computeOnClient(mc -> executor.state())
                    == CraftingExecutor.State.PLACE_RECIPE) {
                reachedAction = true;
                break;
            }
        }
        if (!reachedAction) {
            throw new AssertionError("menu failure fixture did not reach a real menu action: "
                    + context.computeOnClient(mc -> executor.state() + " " + executor.failureDiagnostics()
                    + " journal=" + executor.transferJournal()));
        }
        context.runOnClient(mc -> executor.tick());
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(net.minecraft.server.level.ServerPlayer::closeContainer));
        for (int tick = 0; tick < 80; tick++) {
            context.waitTicks(1);
            context.runOnClient(mc -> executor.tick());
            if (context.computeOnClient(mc -> executor.status() == ExecutionStatus.FAILED
                    || executor.status() == ExecutionStatus.CANCELLED
                    || executor.status() == ExecutionStatus.COMPLETE)) break;
        }
        var status = context.computeOnClient(mc -> executor.status());
        var reason = context.computeOnClient(mc -> executor.transferJournal().stream()
                .map(CraftingExecutor.TransferJournalEntry::note)
                .filter(note -> note.contains("menu action rejected"))
                .findFirst().orElse("") );
        if ((status != ExecutionStatus.FAILED && status != ExecutionStatus.CANCELLED) || reason.isEmpty()) {
            throw new AssertionError("a rejected menu action must fail the executor and record its reason, status=" + status
                    + " reason=" + reason
                    + " state=" + context.computeOnClient(mc -> executor.state())
                    + " diagnostics=" + context.computeOnClient(mc -> executor.failureDiagnostics()));
        }
    }

    private static void assertExecutorTimesOutWhenMenuCallbackIsDelayed(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        resetExecutorFixture(context, server, 0, 0);
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(player -> {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
            player.getInventory().setItem(1, new ItemStack(Items.STICK, 2));
        }));
        context.waitTicks(2);
        var executor = context.computeOnClient(mc -> {
            mc.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
            mc.player.getInventory().setItem(1, new ItemStack(Items.STICK, 2));
            var newExecutor = new CraftingExecutor(FindMyItemsClient.index(), FindMyItemsClient.config(),
                    CraftingExecutor::currentPlayerGeneration, CraftingExecutor::currentWorldGeneration);
            var diamond = new StackKey("minecraft:diamond", "{}");
            var sticks = new StackKey("minecraft:stick", "{}");
            var plan = executorPlan(new StackKey("minecraft:diamond_pickaxe", "{}"),
                    Map.of(diamond, 1L, sticks, 2L), 1);
            newExecutor.start(new CraftingExecutor.ExecutionRequest(plan, List.of(),
                    CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(),
                    CraftingExecutor.Mode.GATHER_AND_CRAFT));
            return newExecutor;
        });
        for (int tick = 0; tick < 80; tick++) {
            context.waitTicks(1);
            context.runOnClient(mc -> executor.tick());
            if (context.computeOnClient(mc -> executor.state()) == CraftingExecutor.State.PLACE_RECIPE) break;
        }
        if (context.computeOnClient(mc -> executor.state()) != CraftingExecutor.State.PLACE_RECIPE) {
            throw new AssertionError("delayed-callback fixture did not reach a menu action");
        }
        context.runOnClient(mc -> executor.tick());
        context.runOnClient(mc -> {
            try {
                var token = CraftingExecutor.class.getDeclaredField("runToken");
                token.setAccessible(true);
                token.setLong(executor, token.getLong(executor) + 1);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("could not suppress the menu callback", exception);
            }
        });

        for (int tick = 0; tick < 80; tick++) {
            context.waitTicks(1);
            context.runOnClient(mc -> executor.tick());
            var status = context.computeOnClient(mc -> executor.status());
            if (status == ExecutionStatus.FAILED || status == ExecutionStatus.CANCELLED) break;
        }
        var status = context.computeOnClient(mc -> executor.status());
        var timedOut = context.computeOnClient(mc -> executor.transferJournal().stream()
                .map(CraftingExecutor.TransferJournalEntry::note)
                .anyMatch(note -> note.contains("timed out")));
        if (status != ExecutionStatus.FAILED || !timedOut) {
            throw new AssertionError("a lost menu callback must time out: status=" + status
                    + " journal=" + context.computeOnClient(mc -> executor.transferJournal()));
        }
    }

    private static void assertSourceSnapshotTemplateIsDefensive(ClientGameTestContext context) {
        var unchanged = context.computeOnClient(mc -> {
            var snapshot = sourceSnapshot(new StackKey("minecraft:diamond", "{}"), CHEST, 1, 0);
            snapshot.template().setCount(64);
            return snapshot.template().getCount();
        });
        if (unchanged != 1) {
            throw new AssertionError("SourceSnapshot.template must not expose mutable internal state");
        }
    }

    private static void assertCreativeCraftedOutputOverflowIsRetained(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(player -> {
            player.getAbilities().instabuild = true;
            for (int slot = 0; slot < 36; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            var remainder = dev.smpb.findmyitems.retrieval.CraftingExecutorTestAccess.insertCraftedOutput(
                    player, new ItemStack(Items.DIAMOND));
            player.containerMenu.setCarried(remainder);
            if (remainder.getCount() != 1 || player.getInventory().countItem(Items.DIAMOND) != 0
                    || player.containerMenu.getCarried().getCount() != 1) {
                throw new AssertionError("creative crafted output overflow must remain on the cursor: remainder="
                        + remainder + " inventory=" + player.getInventory().countItem(Items.DIAMOND)
                        + " cursor=" + player.containerMenu.getCarried());
            }
        }));
    }

    private static CraftingPlan executorPlan(StackKey output, Map<StackKey, Long> consumed, long craftCount) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var catalog = RecipeCatalog.from(mc.getSingleplayerServer().getRecipeManager(), mc.level);
        var recipe = catalog.recipesFor(output).stream().findFirst().orElse(null);
        var selected = recipe == null ? List.<StackKey>of()
                : recipe.ingredientOptions().stream().map(options -> options.getFirst()).toList();
        var node = CraftingPlan.node(output, 1, 0, craftCount, List.of(), consumed, Map.of(), Map.of(),
                null, recipe, selected, null);
        return CraftingPlan.of(node, PlanningInventory.empty(), consumed, Map.of(), Map.of(),
                new PlanScore(0, 0, 0, 0, 0));
    }

    private static CraftingExecutor.SourceSnapshot sourceSnapshot(StackKey key, BlockPos position,
                                                                   int count, int slot) {
        var contents = SourceKey.storage("minecraft:overworld", ContainerKind.CHEST,
                List.of(new BlockPosition(position.getX(), position.getY(), position.getZ())));
        return new CraftingExecutor.SourceSnapshot(key, "minecraft:overworld", ContainerKind.CHEST,
                List.of(position), List.of(slot), count, contents, List.of(contents), new ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                                net.minecraft.resources.Identifier.parse(key.itemId())).orElseThrow()));
    }

    private static CraftingExecutor startIdleExecutor(CraftingExecutor.Mode mode) {
        var plan = executorPlan(new StackKey("minecraft:diamond_pickaxe", "{}"), Map.of(), 0);
        var executor = FindMyItemsClient.executor();
            executor.start(new CraftingExecutor.ExecutionRequest(plan, List.of(),
                    CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(), mode));
        executor.tick();
        return executor;
    }

    private static CraftingExecutor startPendingExecutor(CraftingExecutor.Mode mode) {
        var plan = executorPlan(new StackKey("minecraft:diamond_pickaxe", "{}"), Map.of(), 0);
        var executor = FindMyItemsClient.executor();
        executor.start(new CraftingExecutor.ExecutionRequest(plan, List.of(),
                CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(), mode));
        return executor;
    }

    private static void runExecutorTicks(ClientGameTestContext context, int maximum) {
        for (int tick = 0; tick < maximum; tick++) {
            context.waitTicks(1);
            var terminal = context.computeOnClient(mc -> {
                var status = FindMyItemsClient.executor().status();
                return status == ExecutionStatus.CANCELLED || status == ExecutionStatus.FAILED
                        || status == ExecutionStatus.COMPLETE;
            });
            if (terminal) return;
        }
    }

    private static void assertCancelled(ClientGameTestContext context, String reason) {
        var result = context.computeOnClient(mc -> new Object[] {
                FindMyItemsClient.executor().status(),
                FindMyItemsClient.executor().transferJournal().stream()
                        .map(CraftingExecutor.TransferJournalEntry::note).anyMatch(note -> note.endsWith(reason.toLowerCase()))
        });
        if (result[0] != ExecutionStatus.CANCELLED || !Boolean.TRUE.equals(result[1])) {
            throw new AssertionError("executor did not cancel for " + reason + ": " + java.util.Arrays.toString(result));
        }
    }

    private static void resetExecutorFixture(ClientGameTestContext context,
                                              net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
                                              int diamonds, int sticks) {
        context.setScreen(() -> null);
        server.runOnServer(s -> {
            for (int y = STAND.getY(); y <= STAND.getY() + 2; y++) {
                s.overworld().setBlockAndUpdate(
                        new BlockPos(STAND.getX(), y, STAND.getZ() + 1), Blocks.AIR.defaultBlockState());
            }
            var chest = (ChestBlockEntity) s.overworld().getBlockEntity(CHEST);
            if (chest == null) {
                s.overworld().setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
                chest = (ChestBlockEntity) s.overworld().getBlockEntity(CHEST);
            }
            chest.clearContent();
            if (diamonds > 0) chest.setItem(0, new ItemStack(Items.DIAMOND, diamonds));
            if (sticks > 0) chest.setItem(4, new ItemStack(Items.STICK, sticks));
            for (var player : s.getPlayerList().getPlayers()) {
                player.getInventory().clearContent();
                player.closeContainer();
            }
        });
        context.runOnClient(mc -> {
            mc.player.getInventory().clearContent();
            mc.player.containerMenu = mc.player.inventoryMenu;
        });
        context.waitTicks(2);
    }

    private static void fillClientInventory(net.minecraft.client.Minecraft minecraft) {
        for (int slot = 0; slot < 36; slot++) minecraft.player.getInventory().setItem(slot,
                new ItemStack(Items.COBBLESTONE, 64));
    }

    private static Map<String, Long> executorAccounting(ClientGameTestContext context,
                                                        net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        return server.computeOnServer(s -> {
            var totals = new java.util.LinkedHashMap<String, Long>();
            var chest = (ChestBlockEntity) s.overworld().getBlockEntity(CHEST);
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                addAccountingStack(totals, chest.getItem(slot), s.registryAccess());
            }
            for (var player : s.getPlayerList().getPlayers()) {
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    addAccountingStack(totals, player.getInventory().getItem(slot), player.registryAccess());
                }
                addAccountingStack(totals, player.containerMenu.getCarried(), player.registryAccess());
            }
            return Map.copyOf(totals);
        });
    }

    private static void addAccountingStack(Map<String, Long> totals, ItemStack stack,
                                           net.minecraft.core.HolderLookup.Provider registries) {
        if (stack.isEmpty()) return;
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|"
                + dev.smpb.findmyitems.observation.SlotReader.serializeComponents(
                stack.getComponentsPatch(), registries);
        totals.merge(key, (long) stack.getCount(), Long::sum);
        var contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            contents.allItemsCopyStream().forEach(inner -> addAccountingStack(totals, inner, registries));
        }
    }

    private static Map<String, Long> accountingDelta(Map<String, Long> before, Map<String, Long> after) {
        var delta = new java.util.LinkedHashMap<String, Long>();
        var keys = new java.util.HashSet<String>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        for (var key : keys) {
            var change = after.getOrDefault(key, 0L) - before.getOrDefault(key, 0L);
            if (change != 0) delta.put(key, change);
        }
        return Map.copyOf(delta);
    }

    private static void assertTickDrivenDiamondPickaxe(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        var before = executorAccounting(context, server);
        var result = context.computeOnClient(mc -> {
            var output = new StackKey("minecraft:diamond_pickaxe", "{}");
            var diamonds = new StackKey("minecraft:diamond", "{}");
            var sticks = new StackKey("minecraft:stick", "{}");
            var catalog = RecipeCatalog.from(mc.getSingleplayerServer().getRecipeManager(), mc.level);
            var recipe = catalog.recipesFor(output).getFirst();
            var selected = recipe.ingredientOptions().stream().map(options -> options.getFirst()).toList();
            var node = CraftingPlan.node(output, 1, 1, 1, List.of(), Map.of(), Map.of(), Map.of(),
                    null, recipe, selected, null);
            var plan = CraftingPlan.of(node, PlanningInventory.empty(),
                    Map.of(diamonds, 3L, sticks, 2L), Map.of(), Map.of(),
                    new PlanScore(0, 0, 1, 0, 1));
            var positions = List.of(new BlockPosition(CHEST.getX(), CHEST.getY(), CHEST.getZ()));
            var contents = SourceKey.storage(mc.level.dimension().identifier().toString(), ContainerKind.CHEST, positions);
            var diamondSource = new CraftingExecutor.SourceSnapshot(diamonds,
                    mc.level.dimension().identifier().toString(), ContainerKind.CHEST,
                    List.of(CHEST), List.of(0), 3, contents, List.of(contents), new ItemStack(Items.DIAMOND));
            var stickSource = new CraftingExecutor.SourceSnapshot(sticks,
                    mc.level.dimension().identifier().toString(), ContainerKind.CHEST,
                    List.of(CHEST), List.of(4), 2, contents, List.of(contents), new ItemStack(Items.STICK));
            var executor = FindMyItemsClient.executor();
            executor.start(new CraftingExecutor.ExecutionRequest(plan, List.of(diamondSource, stickSource),
                    CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(),
                    CraftingExecutor.Mode.GATHER_AND_CRAFT));
            return output;
        });
        var maxActions = 0;
        for (int tick = 0; tick < 140; tick++) {
            context.waitTicks(1);
            var actions = context.computeOnClient(mc -> FindMyItemsClient.executor().actionsLastTick());
            maxActions = Math.max(maxActions, actions);
            var complete = context.computeOnClient(mc -> FindMyItemsClient.executor().status()
                    == ExecutionStatus.COMPLETE);
            if (complete) break;
        }
        var complete = context.computeOnClient(mc -> FindMyItemsClient.executor().status());
        if (complete != ExecutionStatus.COMPLETE) {
            var diagnostics = context.computeOnClient(mc -> FindMyItemsClient.executor().state() + " "
                    + FindMyItemsClient.executor().transferJournal() + " table="
                    + FindMyItemsClient.executor().tableRequiredMaterials() + " "
                    + FindMyItemsClient.executor().failureDiagnostics());
            throw new AssertionError("tick-driven pickaxe plan did not complete: " + complete + " " + diagnostics);
        }
        if (maxActions > 1) throw new AssertionError("executor performed multiple actions in one tick: " + maxActions);
        var after = executorAccounting(context, server);
        var expected = Map.of(
                "minecraft:diamond|{}", -3L,
                "minecraft:stick|{}", -2L,
                "minecraft:diamond_pickaxe|{}", 1L);
        var delta = accountingDelta(before, after);
        if (!delta.equals(expected)) {
            throw new AssertionError("source, inventory, and cursor accounting changed unexpectedly: before="
                    + before + " after=" + after + " delta=" + delta);
        }
    }

    /** With an empty box the crafting view lists recipe roots, not expanded plans. */
    private static void assertCraftingIndexIsPopulated(ClientGameTestContext context) {
        var rows = context.computeOnClient(mc -> CatalogScreenTestAccess.rowCount(requireCatalog(mc)));
        if (rows < 500) {
            throw new AssertionError("crafting view should list recipe roots, listed " + rows + " rows");
        }
    }

    private static void assertLocateAndAutomaticRetrievalLabels(ClientGameTestContext context) {
        var semantics = context.computeOnClient(mc -> new String[] {
                String.valueOf(CatalogScreenTestAccess.locateVisible(0, true)),
                String.valueOf(CatalogScreenTestAccess.locateVisible(5, true)),
                CatalogScreenTestAccess.automaticStatusKey(0, 5, false, true),
                CatalogScreenTestAccess.automaticStatusKey(0, 5, true, true),
        });
        if (!semantics[0].equals("false") || !semantics[1].equals("true")) {
            throw new AssertionError("locate must hide zero stock and retain positive stock: "
                    + java.util.Arrays.toString(semantics));
        }
        if (!semantics[2].equals("screen.findmyitems.craft.unavailable")
                || !semantics[3].equals("screen.findmyitems.craft.reachable_now")) {
            throw new AssertionError("positive unavailable stock must be labeled unavailable: "
                    + java.util.Arrays.toString(semantics));
        }
    }

    private static void assertCraftingBrowseIsLazyAndRootBased(ClientGameTestContext context) {
        var state = context.computeOnClient(mc -> {
            var screen = requireCatalog(mc);
            return CatalogScreenTestAccess.browseState(screen);
        });
        if (state.planRequests() != 0) {
            throw new AssertionError("empty crafting browse must not invoke the planner, requests="
                    + state.planRequests());
        }
        if (state.selected()) {
            throw new AssertionError("empty crafting browse must not select an output");
        }
        if (!state.rootRows()) {
            throw new AssertionError("crafting browse rows must contain root outputs only");
        }
    }

    private static void assertCraftingViewportAndScroll(ClientGameTestContext context) {
        var visible = context.computeOnClient(mc -> CatalogScreenTestAccess.visibleRowCount(requireCatalog(mc)));
        var rendered = context.computeOnClient(mc -> CatalogScreenTestAccess.renderedRowCount(requireCatalog(mc)));
        var total = context.computeOnClient(mc -> CatalogScreenTestAccess.rowCount(requireCatalog(mc)));
        if (visible <= 0 || visible >= total) {
            throw new AssertionError("crafting viewport should render a clipped subset of rows, visible="
                    + visible + ", total=" + total);
        }
        if (rendered <= 0 || rendered > visible + 2) {
            throw new AssertionError("crafting renderer should use visible rows plus overscan, rendered=" + rendered
                    + ", visible=" + visible);
        }

        context.runOnClient(mc -> requireCatalog(mc).mouseScrolled(200, 100, 0, -20));
        context.waitTicks(2);
        var before = context.computeOnClient(mc -> CatalogScreenTestAccess.scrollAmount(requireCatalog(mc)));
        if (before <= 0) {
            throw new AssertionError("crafting list should scroll before an index-only refresh");
        }
        context.runOnClient(mc -> FindMyItemsClient.index().replace(FindMyItemsClient.index().snapshot()));
        context.waitTicks(3);
        var after = context.computeOnClient(mc -> CatalogScreenTestAccess.scrollAmount(requireCatalog(mc)));
        if (Math.abs(before - after) > 0.01) {
            throw new AssertionError("index-only refresh must preserve scroll, before=" + before + ", after=" + after);
        }

        var hit = context.computeOnClient(mc -> CatalogScreenTestAccess.hitTestRow(requireCatalog(mc), 200.0, 100.0));
        var miss = context.computeOnClient(mc -> CatalogScreenTestAccess.hitTestRow(requireCatalog(mc), 200.0, 10000.0));
        if (hit.isEmpty() || miss.isPresent()) {
            throw new AssertionError("viewport hit testing must accept an in-viewport row and reject clipped space");
        }

        var bottom = context.computeOnClient(mc -> CatalogScreenTestAccess.lastVisibleRowBottomCenter(requireCatalog(mc)));
        var bottomHit = context.computeOnClient(mc -> CatalogScreenTestAccess.hitTestRow(requireCatalog(mc),
                bottom[0], bottom[1]));
        if (bottomHit.isEmpty()) {
            throw new AssertionError("bottom clipped row must remain hit-testable inside the viewport");
        }
    }

    private static void clickFirstCraftingRow(ClientGameTestContext context) {
        var cursor = context.computeOnClient(mc -> {
            var row = CatalogScreenTestAccess.firstVisibleRowCenter(requireCatalog(mc));
            var window = mc.getWindow();
            return new double[] {
                    row[0] * window.getScreenWidth() / window.getGuiScaledWidth(),
                    row[1] * window.getScreenHeight() / window.getGuiScaledHeight()
            };
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(1);
        var hovered = context.computeOnClient(mc -> CatalogScreenTestAccess.hasHoveredIdentity(requireCatalog(mc)));
        if (!hovered) {
            throw new AssertionError("hovering a browse row must expose its stable output identity");
        }
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(1);
    }

    private static void assertSingleSelectedPlan(ClientGameTestContext context) {
        var state = context.computeOnClient(mc -> {
            var screen = requireCatalog(mc);
            return CatalogScreenTestAccess.selectionState(screen);
        });
        if (state.planRequests() < 1) {
            throw new AssertionError("selecting one crafting output must invoke a plan request, requests="
                    + state.planRequests());
        }
        if (!state.selected()) {
            throw new AssertionError("selecting a crafting output must retain its stable identity");
        }

        // Planning runs off-thread; poll for the applied plan instead of sleeping through it.
        waitForCondition(context, "applied crafting plan", 80,
                mc -> mc.gui.screen() instanceof CatalogScreen screen
                        && CatalogScreenTestAccess.selectionState(screen).generations()
                                .appliedPlanGeneration() == CatalogScreenTestAccess.selectionState(screen)
                                        .generations().planGeneration()
                        && CatalogScreenTestAccess.selectionState(screen).selected());
        state = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc)));
        if (state.generations().appliedPlanGeneration() != state.generations().planGeneration()) {
            throw new AssertionError("selected output should apply its current plan before invalidation");
        }
    }

    private static void assertCraftingActionsVisible(ClientGameTestContext context) {
        var visible = context.computeOnClient(mc -> CatalogScreenTestAccess.craftingActionsVisible(requireCatalog(mc)));
        if (!visible) {
            throw new AssertionError("planned crafting output must make gather and craft actions visible");
        }
    }

    private static void assertSelectionClearsAfterFilter(ClientGameTestContext context) {
        var state = context.computeOnClient(mc -> {
            var screen = requireCatalog(mc);
            return CatalogScreenTestAccess.selectionState(screen);
        });
        if (state.selected() || state.hovered()) {
            throw new AssertionError("filter changes must clear stale crafting selection");
        }
        if (state.generations().appliedPlanGeneration() == state.generations().planGeneration()) {
            throw new AssertionError("stale plan result must not be applied after a query generation change");
        }
    }

    private static void assertGenerationInvalidation(ClientGameTestContext context) {
        var beforeAmount = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        setCatalogAmount(context, "2");
        var afterAmount = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        if (afterAmount.searchGeneration() <= beforeAmount.searchGeneration()
                || afterAmount.planGeneration() <= beforeAmount.planGeneration()) {
            throw new AssertionError("amount changes must advance query and plan generations");
        }
        context.runOnClient(mc -> CatalogScreenTestAccess.selectOutput(requireCatalog(mc),
                new StackKey("minecraft:iron_pickaxe", "{}")));
        if (context.computeOnClient(mc -> CatalogScreenTestAccess.craftingActionsVisible(requireCatalog(mc)))) {
            throw new AssertionError("selecting a new output must hide actions until its plan arrives");
        }

        switchViewByShortcut(context, GLFW.GLFW_KEY_1, "ITEMS");
        var beforeView = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        switchViewByShortcut(context, GLFW.GLFW_KEY_3, "CRAFTING");
        var afterView = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        if (afterView.searchGeneration() <= beforeView.searchGeneration()) {
            throw new AssertionError("view changes must advance the query generation");
        }

        switchViewByShortcut(context, GLFW.GLFW_KEY_1, "ITEMS");
        var beforeLayout = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        click(context, "screen.findmyitems.layout.grid");
        var afterLayout = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        click(context, "screen.findmyitems.layout.list");
        if (afterLayout.searchGeneration() <= beforeLayout.searchGeneration()) {
            throw new AssertionError("layout changes must advance the query generation");
        }

        switchViewByShortcut(context, GLFW.GLFW_KEY_3, "CRAFTING");
        var beforeRecipe = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        context.runOnClient(mc -> CatalogScreen.invalidateRecipeCache());
        // The catalog notices the new recipe generation on its next tick; poll for it.
        waitForCondition(context, "recipe reload advancing the query generation", 40,
                mc -> mc.gui.screen() instanceof CatalogScreen screen
                        && CatalogScreenTestAccess.selectionState(screen).generations()
                                .searchGeneration() > beforeRecipe.searchGeneration());
        var afterRecipe = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        if (afterRecipe.searchGeneration() <= beforeRecipe.searchGeneration()) {
            throw new AssertionError("recipe reloads must advance the query generation");
        }

        var beforeIndex = afterRecipe;
        context.runOnClient(mc -> FindMyItemsClient.index().replace(FindMyItemsClient.index().snapshot()));
        waitForCondition(context, "index revision advancing the query generation", 40,
                mc -> mc.gui.screen() instanceof CatalogScreen screen
                        && CatalogScreenTestAccess.selectionState(screen).generations()
                                .searchGeneration() > beforeIndex.searchGeneration());
        var afterIndex = context.computeOnClient(mc -> CatalogScreenTestAccess.selectionState(requireCatalog(mc))
                .generations());
        if (afterIndex.searchGeneration() <= beforeIndex.searchGeneration()) {
            throw new AssertionError("index revisions must advance the query generation");
        }
    }

    /**
     * Real geometry: the screen must fill exactly the window's scaled dims. A logical-only
     * resize with an unchanged framebuffer fails this outright, which is the point.
     */
    private static void assertScreenMatchesWindow(ClientGameTestContext context) {
        var actual = context.computeOnClient(mc -> {
            var screen = requireCatalog(mc);
            return screen.width + "x" + screen.height + " vs window "
                    + mc.getWindow().getGuiScaledWidth() + "x" + mc.getWindow().getGuiScaledHeight();
        });
        var dims = context.computeOnClient(mc -> mc.getWindow().getGuiScaledWidth() + "x"
                + mc.getWindow().getGuiScaledHeight());
        var screenDims = actual.substring(0, actual.indexOf(" vs window"));
        if (!screenDims.equals(dims)) {
            throw new AssertionError("catalog must fill the rescaled window, screen " + actual);
        }
    }

    /** Every interactive widget must be non-empty and fully inside the screen. */
    private static void assertCatalogWidgetsInBounds(ClientGameTestContext context) {
        var bad = context.computeOnClient(mc -> {
            var screen = requireCatalog(mc);
            var complaints = new java.util.ArrayList<String>();
            for (var rect : CatalogScreenTestAccess.widgetBounds(screen)) {
                if (rect[2] <= 0 || rect[3] <= 0) {
                    complaints.add("empty " + java.util.Arrays.toString(rect));
                } else if (rect[0] < 0 || rect[1] < 0 || rect[0] + rect[2] > screen.width
                        || rect[1] + rect[3] > screen.height) {
                    complaints.add("out of bounds " + java.util.Arrays.toString(rect)
                            + " on " + screen.width + "x" + screen.height);
                }
            }
            return complaints;
        });
        if (!bad.isEmpty()) {
            throw new AssertionError("catalog widgets must sit inside the screen: " + bad);
        }
    }

    /**
     * Settings geometry at the rescaled size: all six rows plus Done fit on-screen with no
     * overlap. Done returns to the same catalog, which keeps its query.
     */
    private static void assertSettingsGeometryAtScale(ClientGameTestContext context) {
        var catalog = context.computeOnClient(FindMyItemsClientGameTest::requireCatalog);
        context.runOnClient(mc -> mc.gui.setScreen(ConfigScreen.create(
                catalog, FindMyItemsClient.config(), FindMyItemsClient.configPath())));
        context.waitForScreen(ConfigScreen.class);
        var bad = context.computeOnClient(mc -> {
            var screen = requireSettings(mc);
            var complaints = new java.util.ArrayList<String>();
            var seen = new java.util.ArrayList<int[]>();
            for (var rect : ConfigScreenTestAccess.settingsBounds(screen)) {
                if (rect[2] <= 0 || rect[3] <= 0) {
                    complaints.add("empty " + java.util.Arrays.toString(rect));
                } else if (rect[0] < 0 || rect[1] < 0 || rect[0] + rect[2] > screen.width
                        || rect[1] + rect[3] > screen.height) {
                    complaints.add("out of bounds " + java.util.Arrays.toString(rect)
                            + " on " + screen.width + "x" + screen.height);
                }
                for (var other : seen) {
                    if (rect[0] < other[0] + other[2] && other[0] < rect[0] + rect[2]
                            && rect[1] < other[1] + other[3] && other[1] < rect[1] + rect[3]) {
                        complaints.add("overlap " + java.util.Arrays.toString(rect));
                        break;
                    }
                }
                seen.add(rect);
            }
            if (seen.size() != 7) {
                complaints.add("expected 6 rows plus Done, found " + seen.size());
            }
            return complaints;
        });
        if (!bad.isEmpty()) {
            throw new AssertionError("settings must fit its scale without overlap: " + bad);
        }
        context.takeScreenshot("settings-rescaled");
        context.clickScreenButton("gui.done");
        context.waitForScreen(CatalogScreen.class);
        waitForCondition(context, "catalog query surviving settings", 20,
                mc -> catalogSearchText(mc).equals("diamond"));
    }

    private static CatalogScreen requireCatalog(net.minecraft.client.Minecraft minecraft) {
        if (!(minecraft.gui.screen() instanceof CatalogScreen screen)) {
            throw new AssertionError("catalog screen is not open");
        }
        return screen;
    }

    private static ConfigScreen requireSettings(net.minecraft.client.Minecraft minecraft) {
        if (!(minecraft.gui.screen() instanceof ConfigScreen screen)) {
            throw new AssertionError("settings screen is not open");
        }
        return screen;
    }

    /** Screen-space center of a settings row, converted to window pixels for real clicks. */
    private static void clickSettingsRow(ClientGameTestContext context, int row) {
        var cursor = context.computeOnClient(mc -> {
            var center = ConfigScreenTestAccess.rowCenter(requireSettings(mc), row);
            var window = mc.getWindow();
            return new double[] {
                    center[0] * window.getScreenWidth() / window.getGuiScaledWidth(),
                    center[1] * window.getScreenHeight() / window.getGuiScaledHeight()
            };
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(1);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /**
     * The vanilla settings screen preserves all six settings, edits the shared instance through
     * real clicks, and persists through both Done and Esc — Esc reaches {@code onClose} in
     * vanilla, so both paths save and both return to the parent.
     */
    private static void assertSettingsScreenPreservesAndPersists(ClientGameTestContext context) {
        context.setScreen(() -> null);
        waitForCondition(context, "no screen", 20, mc -> mc.gui.screen() == null);
        var original = context.computeOnClient(mc -> new int[] {
                FindMyItemsClient.config().rescanIntervalSeconds,
                FindMyItemsClient.config().searchDistanceChunks,
                FindMyItemsClient.config().retrieveDistanceBlocks,
                FindMyItemsClient.config().indexEnderInventory ? 1 : 0,
                FindMyItemsClient.config().filterInventory ? 1 : 0,
                FindMyItemsClient.config().filterContainers ? 1 : 0});
        try {
            context.runOnClient(mc -> mc.gui.setScreen(ConfigScreen.create(
                    null, FindMyItemsClient.config(), FindMyItemsClient.configPath())));
            context.waitForScreen(ConfigScreen.class);
            context.takeScreenshot("settings");
            var rows = context.computeOnClient(mc -> ConfigScreenTestAccess.rows(requireSettings(mc)).size());
            if (rows != 6) {
                throw new AssertionError("settings must preserve all six settings, found " + rows + " rows");
            }

            // Row 3 is the ender toggle: flip it with a real click into the shared instance.
            var enderBefore = original[3] == 1;
            clickSettingsRow(context, 3);
            waitForCondition(context, "ender toggle flipping the shared config", 20,
                    mc -> FindMyItemsClient.config().indexEnderInventory != enderBefore);
            var toggleMessage = context.computeOnClient(mc ->
                    ConfigScreenTestAccess.rowMessage(requireSettings(mc), 3));
            if (!toggleMessage.endsWith(enderBefore ? "OFF" : "ON")) {
                throw new AssertionError("toggled row must name its new state, shows: " + toggleMessage);
            }

            // Row 0 is the rescan slider: move it to Disabled and watch the label follow.
            context.runOnClient(mc -> ConfigScreenTestAccess.setSliderValue(requireSettings(mc), 0, 0));
            var slider = context.computeOnClient(mc -> new Object[] {
                    FindMyItemsClient.config().rescanIntervalSeconds,
                    ConfigScreenTestAccess.rowMessage(requireSettings(mc), 0)});
            if (!slider[0].equals(0) || !((String) slider[1]).contains("Disabled")) {
                throw new AssertionError("rescan slider must write 0 and read Disabled: "
                        + java.util.Arrays.toString(slider));
            }

            // A real Esc press: the edits must reach the file, not just the live instance.
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            waitForCondition(context, "settings closed by Esc", 20, mc -> mc.gui.screen() == null);
            var saved = context.computeOnClient(mc -> {
                try {
                    return java.nio.file.Files.readString(FindMyItemsClient.configPath());
                } catch (java.io.IOException e) {
                    throw new AssertionError("could not read saved config", e);
                }
            });
            if (!saved.contains("\"rescanIntervalSeconds\": 0")
                    || saved.contains("\"indexEnderInventory\": " + enderBefore)) {
                throw new AssertionError("Esc must persist settings edits, file holds: " + saved);
            }

            // Reopening shows the persisted values, not the defaults.
            context.runOnClient(mc -> mc.gui.setScreen(ConfigScreen.create(
                    null, FindMyItemsClient.config(), FindMyItemsClient.configPath())));
            context.waitForScreen(ConfigScreen.class);
            var reopened = context.computeOnClient(mc -> new String[] {
                    ConfigScreenTestAccess.rowMessage(requireSettings(mc), 0),
                    ConfigScreenTestAccess.rowMessage(requireSettings(mc), 3)});
            if (!reopened[0].contains("Disabled") || !reopened[1].endsWith(enderBefore ? "OFF" : "ON")) {
                throw new AssertionError("reopened settings must show persisted values: "
                        + java.util.Arrays.toString(reopened));
            }

            // Done returns to the parent screen, which here is no screen.
            context.clickScreenButton("gui.done");
            waitForCondition(context, "settings closed by Done", 20, mc -> mc.gui.screen() == null);
        } finally {
            context.runOnClient(mc -> {
                var config = FindMyItemsClient.config();
                config.rescanIntervalSeconds = original[0];
                config.searchDistanceChunks = original[1];
                config.retrieveDistanceBlocks = original[2];
                config.indexEnderInventory = original[3] == 1;
                config.filterInventory = original[4] == 1;
                config.filterContainers = original[5] == 1;
                config.save(FindMyItemsClient.configPath());
                if (mc.gui.screen() instanceof ConfigScreen) mc.gui.setScreen(null);
            });
            context.waitTicks(2);
        }
    }

    /**
     * The layout toggle is a persisted preference rather than per-screen state, and a window
     * resize rebuilds the catalog without losing the query, the results, or the view.
     */
    private static void assertLayoutTogglePersistsAndResizeKeepsQuery(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        // Earlier gather plans legitimately emptied the chest, so restock it and let the real
        // rescan path re-index it: this test must not depend on leftover index state.
        server.runOnServer(s -> {
            var chest = (ChestBlockEntity) s.overworld().getBlockEntity(CHEST);
            chest.clearContent();
            chest.setItem(0, new ItemStack(Items.DIAMOND, DIAMONDS));
            chest.setChanged();
        });
        waitForCondition(context, DIAMONDS + " restocked diamonds re-indexed", 150,
                mc -> FindMyItemsClient.index().search("diamond").stream()
                        .mapToInt(r -> r.totalCount()).sum() == DIAMONDS);
        openCatalog(context);
        search(context, "diamond");
        // A real resize: change the vanilla GUI-scale option and run the same resizeGui the
        // options screen runs, so the window mapping and the screen rebuild together. Restored
        // in a finally so later tests keep their coordinates; the option file is never written.
        var original = context.computeOnClient(mc -> new int[] {
                mc.options.guiScale().get(), mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight()});
        var targetScale = context.computeOnClient(mc -> mc.getWindow().getGuiScale() == 2 ? 1 : 2);
        try {
            context.runOnClient(mc -> {
                mc.options.guiScale().set(targetScale);
                mc.resizeGui();
            });
            waitForCondition(context, "window actually rescaled", 40,
                    mc -> mc.gui.screen() instanceof CatalogScreen screen
                            && screen.width == mc.getWindow().getGuiScaledWidth()
                            && screen.height == mc.getWindow().getGuiScaledHeight()
                            && (screen.width != original[1] || screen.height != original[2]));
            assertScreenMatchesWindow(context);
            waitForCondition(context, "query surviving a resize", 20,
                    mc -> mc.gui.screen() instanceof CatalogScreen screen
                            && catalogSearchText(mc).equals("diamond")
                            && CatalogScreenTestAccess.rowCount(screen) == 1
                            && CatalogScreenTestAccess.viewName(screen).equals("ITEMS"));
            assertCatalogWidgetsInBounds(context);
            context.takeScreenshot("catalog-resized");
            assertSettingsGeometryAtScale(context);
        } finally {
            context.runOnClient(mc -> {
                mc.options.guiScale().set(original[0]);
                mc.resizeGui();
            });
            waitForCondition(context, "window scale restored", 40,
                    mc -> mc.getWindow().getGuiScaledWidth() == original[1]
                            && mc.getWindow().getGuiScaledHeight() == original[2]);
        }
        waitForCondition(context, "query surviving resize back", 20,
                mc -> mc.gui.screen() instanceof CatalogScreen && catalogSearchText(mc).equals("diamond"));

        click(context, "screen.findmyitems.layout.grid");
        waitForCondition(context, "grid layout persisted", 20, mc -> FindMyItemsClient.config().gridLayout);
        var savedGrid = context.computeOnClient(mc -> {
            try {
                return java.nio.file.Files.readString(FindMyItemsClient.configPath());
            } catch (java.io.IOException e) {
                throw new AssertionError("could not read saved config", e);
            }
        });
        if (!savedGrid.contains("\"gridLayout\": true")) {
            throw new AssertionError("layout toggle must persist to the config file: " + savedGrid);
        }
        context.setScreen(() -> null);
        openCatalog(context);
        var kind = context.computeOnClient(mc -> CatalogScreenTestAccess.rowKind(requireCatalog(mc)));
        if (!kind.equals("ItemGridRow")) {
            throw new AssertionError("reopened catalog must keep the grid layout, rows are " + kind);
        }
        context.takeScreenshot("items-grid-persisted");
        click(context, "screen.findmyitems.layout.list");
        waitForCondition(context, "list layout restored", 20, mc -> !FindMyItemsClient.config().gridLayout);
        context.setScreen(() -> null);
    }

    /**
     * Gathering into a plan that needs a crafting table with no table in reach fails as NO_TABLE
     * before moving anything: the diamond and sticks stay exactly where they were.
     */
    private static void assertExecutorFailsWithoutTable(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        context.setScreen(() -> null);
        waitForCondition(context, "no screen", 20, mc -> mc.gui.screen() == null);
        server.runOnServer(s -> s.overworld().setBlockAndUpdate(CRAFTING_TABLE,
                Blocks.AIR.defaultBlockState()));
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(player -> {
            player.getInventory().clearContent();
            player.closeContainer();
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
            player.getInventory().setItem(1, new ItemStack(Items.STICK, 2));
        }));
        context.waitTicks(2);
        var executor = context.computeOnClient(mc -> {
            mc.player.getInventory().clearContent();
            mc.player.getInventory().setItem(0, new ItemStack(Items.DIAMOND));
            mc.player.getInventory().setItem(1, new ItemStack(Items.STICK, 2));
            var fresh = new CraftingExecutor(FindMyItemsClient.index(), FindMyItemsClient.config(),
                    CraftingExecutor::currentPlayerGeneration, CraftingExecutor::currentWorldGeneration);
            var diamond = new StackKey("minecraft:diamond", "{}");
            var sticks = new StackKey("minecraft:stick", "{}");
            var plan = executorPlan(new StackKey("minecraft:diamond_pickaxe", "{}"),
                    Map.of(diamond, 1L, sticks, 2L), 1);
            fresh.start(new CraftingExecutor.ExecutionRequest(plan, List.of(),
                    CraftingExecutor.currentPlayerGeneration(), CraftingExecutor.currentWorldGeneration(),
                    CraftingExecutor.Mode.GATHER_AND_CRAFT));
            return fresh;
        });
        try {
            for (int tick = 0; tick < 40; tick++) {
                context.waitTicks(1);
                context.runOnClient(mc -> executor.tick());
                var terminal = context.computeOnClient(mc -> executor.status() == ExecutionStatus.NO_TABLE
                        || executor.status() == ExecutionStatus.FAILED
                        || executor.status() == ExecutionStatus.CANCELLED);
                if (terminal) break;
            }
            var result = context.computeOnClient(mc -> new Object[] {
                    executor.status(),
                    executor.transferJournal().stream()
                            .map(CraftingExecutor.TransferJournalEntry::note).toList()});
            var held = server.computeOnServer(s -> {
                var diamonds = 0;
                var sticks = 0;
                for (var player : s.getPlayerList().getPlayers()) {
                    diamonds += player.getInventory().countItem(Items.DIAMOND);
                    sticks += player.getInventory().countItem(Items.STICK);
                }
                return diamonds + "/" + sticks;
            });
            if (result[0] != ExecutionStatus.NO_TABLE
                    || !((List<?>) result[1]).contains("no reachable crafting table")
                    || !held.equals("1/2")) {
                throw new AssertionError("missing table must fail as NO_TABLE with stock untouched: status="
                        + result[0] + " journal=" + result[1] + " held=" + held);
            }
        } finally {
            server.runOnServer(s -> {
                s.overworld().setBlockAndUpdate(CRAFTING_TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
                s.getPlayerList().getPlayers().forEach(player -> {
                    player.getInventory().clearContent();
                    player.closeContainer();
                });
            });
            context.runOnClient(mc -> {
                mc.player.getInventory().clearContent();
                mc.player.containerMenu = mc.player.inventoryMenu;
            });
            context.waitTicks(2);
        }
    }

    private static void assertShowingItems(ClientGameTestContext context) {
        var open = context.computeOnClient(mc -> mc.gui.screen() instanceof CatalogScreen);
        if (!open) {
            throw new AssertionError("ctrl+1 should have stayed on the catalog screen");
        }
    }

    /** Drives the highlight the locate button uses, so the glow render path is exercised for real. */
    private static void highlightTheChest(ClientGameTestContext context) {
        context.waitTicks(5);
        context.runOnClient(mc -> ChestHighlighter.highlight(
                List.of(CHEST), mc.level.dimension().identifier().toString()));
        context.waitTicks(10);
    }

    private static void assertGhostOpenRefusesBlockedChest(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runOnServer(s -> {
            for (int y = STAND.getY(); y <= STAND.getY() + 2; y++) {
                s.overworld().setBlockAndUpdate(
                        new BlockPos(STAND.getX(), y, STAND.getZ() + 1), Blocks.STONE.defaultBlockState());
            }
        });
        // The block update reaches the client on a later tick; poll for the refusal.
        waitForCondition(context, "blocked chest refusing GhostOpen", 40,
                mc -> !GhostOpen.canOpen(CHEST));
        var canOpen = context.computeOnClient(mc -> GhostOpen.canOpen(CHEST));
        if (canOpen) {
            throw new AssertionError("GhostOpen must refuse a chest with no visible interaction point");
        }
    }

    /** Stone platform at y=100 with a stocked chest two blocks in front of the player. */
    private static void buildScene(net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runOnServer(s -> {
            var level = s.overworld();
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 3; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, STAND.getY() - 1, z), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(x, STAND.getY(), z), Blocks.AIR.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(x, STAND.getY() + 1, z), Blocks.AIR.defaultBlockState());
                }
            }

            level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(FURNACE, Blocks.FURNACE.defaultBlockState());
            if (level.getBlockEntity(CHEST) instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.DIAMOND, DIAMONDS));
                chest.setItem(1, new ItemStack(Items.OAK_LOG, 12));
                chest.setItem(2, shulkerHolding(new ItemStack(Items.GOLD_INGOT, BURIED_GOLD)));
                chest.setItem(3, new ItemStack(Items.EMERALD, CHEST_EMERALDS));
                chest.setItem(4, new ItemStack(Items.STICK, 12));
                chest.setChanged();
            }

            level.setBlockAndUpdate(new BlockPos(0, STAND.getY(), 3), Blocks.CRAFTING_TABLE.defaultBlockState());

            level.setBlockAndUpdate(ENDER, Blocks.ENDER_CHEST.defaultBlockState());

            for (var player : s.getPlayerList().getPlayers()) {
                player.teleportTo(STAND.getX() + 0.5, STAND.getY(), STAND.getZ() + 0.5);
                player.getInventory().clearContent();
                player.getEnderChestInventory().clearContent();
                player.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, ENDER_EMERALDS));
            }
        });
    }

    /** Right-clicks the chest for real, so PositionCache and ObservationCollector both run. */
    private static void openChest(ClientGameTestContext context) {
        context.getInput().lookAt(CHEST);
        context.waitTicks(2);
        context.getInput().holdKeyFor(options -> options.keyUse, 2);
        context.waitForScreen(ContainerScreen.class);
        // ObservationCollector indexes on the client tick after the screen is initialised.
        waitForCondition(context, DIAMONDS + " indexed diamonds", 60,
                mc -> FindMyItemsClient.index() != null && FindMyItemsClient.index().search("diamond")
                        .stream().mapToInt(r -> r.totalCount()).sum() == DIAMONDS);
        context.takeScreenshot("chest-opened");
    }

    private static void openFurnace(ClientGameTestContext context) {
        context.getInput().lookAt(FURNACE);
        context.waitTicks(2);
        context.getInput().holdKeyFor(options -> options.keyUse, 2);
        context.waitForScreen(FurnaceScreen.class);
        context.waitTicks(2);
    }

    private static void assertFilterBarVisible(ClientGameTestContext context, boolean expected) {
        var visible = context.computeOnClient(mc -> mc.gui.screen() != null
                && mc.gui.screen().children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.EditBox));
        if (visible != expected) {
            throw new AssertionError("expected filter bar visible=" + expected + ", but was " + visible);
        }
    }

    /**
     * Issue #14, end to end: an item split between a block chest and the ender inventory.
     *
     * <p>The ender chest is the one container whose contents outlive every block that opens it, so
     * it is the one that can end up counted in a total and reachable from nothing. Nothing here is
     * broken or dug up to get there — the way it happens in a played world is simply standing next
     * to the chest long enough for a rescan. After every step the same question is asked: does the
     * row's total equal what the row can name, and does Take move what the button promised?
     */
    private static void enderChestTotalsStayHonest(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        openEnderChest(context);
        context.setScreen(() -> null);
        assertEmeraldRowAddsUp(context, "after opening both containers", CHEST_EMERALDS + ENDER_EMERALDS);

        // Nothing is touched here but the clock. A rescan reads the ender chest's block entity,
        // which is only the lid — the items live on the player — and used to take that for "the
        // chest is gone", stranding the contents while the block stood there in plain sight.
        context.runOnClient(mc -> FindMyItemsClient.config().rescanIntervalSeconds = 1);
        // One rescan at a 1-second interval rewrites the ender entry; poll for its result.
        waitForCondition(context, "single indexed ender container after a rescan", 120,
                mc -> FindMyItemsClient.index().snapshot().containers().stream()
                        .filter(c -> c.contentsKey().kind() == ContainerKind.ENDER_CHEST)
                        .count() == 1);

        var enderContainers = context.computeOnClient(mc -> (int) FindMyItemsClient.index().snapshot().containers()
                .stream()
                .filter(c -> c.contentsKey().kind() == ContainerKind.ENDER_CHEST)
                .count());
        if (enderContainers != 1) {
            throw new AssertionError("rescanning the ender chest should leave one indexed container, found "
                    + enderContainers);
        }
        assertEmeraldRowAddsUp(context, "after a rescan", CHEST_EMERALDS + ENDER_EMERALDS);

        var reachable = context.computeOnClient(mc -> emeraldRow().sources().stream()
                .allMatch(source -> !source.source().positions().isEmpty()));
        if (!reachable) {
            throw new AssertionError("waiting out one rescan next to a standing ender chest left its "
                    + "contents unreachable — nothing was broken, so nothing should have been lost");
        }

        // Now empty both, nearest first. The ender chest is the second take, and it only works
        // because the rescan above left its access source alone.
        takeTheNearestEmeralds(context, server, "the block chest",
                CHEST_EMERALDS, ENDER_EMERALDS, CHEST_EMERALDS, "items-emerald-take-chest");
        takeTheNearestEmeralds(context, server, "the ender chest",
                CHEST_EMERALDS + ENDER_EMERALDS, 0, ENDER_EMERALDS, "items-emerald-take-ender");

        strandedEnderStockIsStillCounted(context, server);
    }

    /**
     * Restocks the ender inventory, then takes its block away.
     *
     * <p>This is the one honest route to a container with no way in: the remembered contents are
     * still true — they are on the player — but no block can open them. They must stay counted and
     * stay labelled, never silently folded into a total the rest of the row cannot account for.
     */
    private static void strandedEnderStockIsStillCounted(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p ->
                p.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, ENDER_EMERALDS))));
        // No reopening: the rescan is expected to pick the restock up through the standing block.
        assertEmeraldRowAddsUp(context, "after a rescan found the ender chest restocked", ENDER_EMERALDS);

        server.runOnServer(s -> s.overworld().setBlockAndUpdate(ENDER, Blocks.AIR.defaultBlockState()));
        // Timeout-specific wait, kept: several rescans must pass without dropping the remembered
        // stock before the invariant below means anything.
        context.waitTicks(80);
        assertEmeraldRowAddsUp(context, "after the ender chest block was removed", ENDER_EMERALDS);

        var stranded = context.computeOnClient(mc -> emeraldRow().sources().stream()
                .filter(source -> source.source().positions().isEmpty())
                .mapToInt(source -> source.count())
                .sum());
        if (stranded != ENDER_EMERALDS) {
            throw new AssertionError("the remembered ender stock should still be listed, as "
                    + ENDER_EMERALDS + " with no position; row lists " + stranded);
        }

        openCatalog(context);
        context.getInput().typeChars("emer");
        waitForCondition(context, "typed search \"emer\"", 20,
                mc -> mc.gui.screen() instanceof CatalogScreen && catalogSearchText(mc).equals("emer"));
        context.takeScreenshot("items-emerald-unreachable");
        context.setScreen(() -> null);
        waitForCondition(context, "catalog closed", 20, mc -> mc.gui.screen() == null);

        // With no ender chest anywhere in the world, the count still follows the player's own
        // data: nothing is opened, nothing is placed, no chunk is read.
        server.runOnServer(s -> s.getPlayerList().getPlayers().forEach(p ->
                p.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, ENDER_EMERALDS * 2))));
        assertEmeraldRowAddsUp(context, "after the ender inventory changed with no chest placed",
                ENDER_EMERALDS * 2);
    }

    /**
     * Clicks the row's real Take button and checks what moved against what it offered.
     *
     * <p>This is the half of issue #14 no assertion on the index can reach. The clamp the player
     * acts on comes from the nearest source's count, so a total padded with stock that has no
     * source shows up here and nowhere else: the button says one number, the chest gives another.
     */
    private static void takeTheNearestEmeralds(
            ClientGameTestContext context,
            net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server,
            String from,
            int expectedCarried,
            int expectedRemaining,
            int requested,
            String screenshot) {
        openCatalog(context);
        context.getInput().typeChars("emer");
        context.waitTicks(3);
        setCatalogAmount(context, String.valueOf(requested));

        var cursor = context.computeOnClient(mc -> {
            var row = CatalogScreenTestAccess.firstVisibleRowTakeCenter(requireCatalog(mc));
            var window = mc.getWindow();
            return new double[] {
                    row[0] * window.getScreenWidth() / window.getGuiScaledWidth(),
                    row[1] * window.getScreenHeight() / window.getGuiScaledHeight(),
            };
        });

        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(3);
        context.takeScreenshot(screenshot);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        // Retrieval goes out through a ghost container open and back from the server.
        var expectedLeft = expectedRemaining;
        waitForCondition(context, "emerald row counting " + expectedLeft + " after taking", 100,
                mc -> emeraldTotal(mc) == expectedLeft);
        context.setScreen(() -> null);

        // The server's copy is where the items actually are; the client mirror follows it.
        var carried = server.computeOnServer(s -> {
            var total = 0;
            for (var p : s.getPlayerList().getPlayers()) {
                var inventory = p.getInventory();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    if (inventory.getItem(i).is(Items.EMERALD)) total += inventory.getItem(i).getCount();
                }
            }
            return total;
        });
        var remaining = context.computeOnClient(mc -> emeraldRow() == null ? 0 : emeraldRow().totalCount());
        var report = " (carried " + carried + ", row total " + remaining + ")";

        if (carried != expectedCarried) {
            throw new AssertionError("taking from " + from + " should have brought the inventory to "
                    + expectedCarried + " emeralds" + report);
        }
        if (remaining != expectedRemaining) {
            throw new AssertionError("after taking from " + from + " the row should count "
                    + expectedRemaining + report);
        }
    }

    private static ItemResult emeraldRow() {
        return FindMyItemsClient.index().search("emerald").stream()
                .filter(r -> r.key().itemId().equals("minecraft:emerald"))
                .findFirst()
                .orElse(null);
    }

    private static String catalogSearchText(net.minecraft.client.Minecraft mc) {
        return mc.gui.screen().children().stream()
                .filter(child -> child instanceof EditBox)
                .map(child -> (EditBox) child)
                .findFirst()
                .map(EditBox::getValue)
                .orElse("<no search field>");
    }

    private static void setCatalogAmount(ClientGameTestContext context, String amount) {
        context.runOnClient(mc -> mc.gui.screen().children().stream()
                .filter(child -> child instanceof EditBox)
                .map(child -> (EditBox) child)
                .skip(1)
                .findFirst()
                .ifPresent(field -> field.setValue(amount)));
        waitForCondition(context, "amount " + amount, 20,
                mc -> mc.gui.screen() != null && mc.gui.screen().children().stream()
                        .filter(child -> child instanceof EditBox)
                        .map(child -> (EditBox) child)
                        .skip(1)
                        .findFirst()
                        .map(field -> field.getValue().equals(amount))
                        .orElse(false));
    }

    /** The one invariant issue #14 is about: the headline total is the sum of what the row lists. */
    private static void assertEmeraldRowAddsUp(ClientGameTestContext context, String stage, int expectedTotal) {
        // Indexing and rescans land on later ticks; poll for the invariant instead of sleeping.
        waitForCondition(context, "emerald row adding up " + stage, 120,
                mc -> emeraldComplaint(expectedTotal) == null);
        var complaint = context.computeOnClient(mc -> emeraldComplaint(expectedTotal));
        if (complaint != null) {
            throw new AssertionError("emerald row " + stage + ": " + complaint);
        }
    }

    private static String emeraldComplaint(int expectedTotal) {
        var row = emeraldRow();
        if (row == null) return "no emerald row at all";
        var listed = row.sources().stream().mapToInt(source -> source.count()).sum();
        if (row.totalCount() != expectedTotal) {
            return "total is " + row.totalCount() + ", expected " + expectedTotal;
        }
        if (listed != row.totalCount()) {
            return "total is " + row.totalCount() + " but its sources account for only " + listed;
        }
        return null;
    }

    private static void openEnderChest(ClientGameTestContext context) {
        context.getInput().lookAt(ENDER);
        context.waitTicks(2);
        context.getInput().holdKeyFor(options -> options.keyUse, 2);
        context.waitForScreen(ContainerScreen.class);
        waitForCondition(context, "indexed ender emeralds", 60,
                mc -> emeraldTotal(mc) == CHEST_EMERALDS + ENDER_EMERALDS);
        context.takeScreenshot("ender-chest-opened");
    }

    private static int emeraldTotal(net.minecraft.client.Minecraft mc) {
        var row = FindMyItemsClient.index().search("emerald").stream()
                .filter(r -> r.key().itemId().equals("minecraft:emerald"))
                .findFirst()
                .orElse(null);
        return row == null ? 0 : row.totalCount();
    }

    private static void assertIndexed(ClientGameTestContext context) {
        var count = context.computeOnClient(mc -> {
            var index = FindMyItemsClient.index();
            if (index == null) return -1;
            return index.search("diamond").stream().mapToInt(r -> r.totalCount()).sum();
        });
        if (count != DIAMONDS) {
            throw new AssertionError("opening the chest should have indexed " + DIAMONDS
                    + " diamonds, index reports " + count);
        }
    }

    /** A shulker box item whose container component holds the given stack. */
    private static ItemStack shulkerHolding(ItemStack inner) {
        var shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(inner)));
        return shulker;
    }

    /** The gold is inside a shulker inside the chest — indexing has to walk into it. */
    private static void assertNestedShulkerIsSearchable(ClientGameTestContext context) {
        var gold = context.computeOnClient(mc -> FindMyItemsClient.index().search("gold ingot").stream()
                .filter(r -> r.key().itemId().equals("minecraft:gold_ingot"))
                .mapToInt(r -> r.totalCount())
                .sum());
        if (gold != BURIED_GOLD) {
            throw new AssertionError("gold inside the nested shulker should be indexed as "
                    + BURIED_GOLD + ", index reports " + gold);
        }
    }

    private static void assertContainerFilterSearchesTooltips(ClientGameTestContext context) {
        var results = context.computeOnClient(mc -> {
            var sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.set(DataComponents.CUSTOM_NAME, Component.literal("Stormblade"));
            var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            enchantments.set(mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.SMITE), 4);
            sword.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
            try {
                var matcher = InventorySearchController.class.getDeclaredMethod(
                        "matches", ItemStack.class, String.class);
                matcher.setAccessible(true);
                return new boolean[] {
                        (boolean) matcher.invoke(null, sword, "smite"),
                        (boolean) matcher.invoke(null, sword, "iv"),
                        (boolean) matcher.invoke(null, sword, "sharpness v"),
                        (boolean) matcher.invoke(null, sword, "sharpness"),
                        (boolean) matcher.invoke(null, sword, "stormblade"),
                        (boolean) matcher.invoke(null, sword, "diamond_sword")
                };
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("could not invoke container filter matcher", e);
            }
        });
        if (!results[0] || !results[1] || results[2] || results[3]
                || !results[4] || !results[5]) {
            throw new AssertionError("container filter should match display name, tooltip name and level, "
                    + "item name and path, but not unrelated enchantments or levels");
        }
    }

    /** Puts the cursor on the first grid cell, so the detail pane has something to describe. */
    private static void hoverFirstGridCell(ClientGameTestContext context) {
        var cursor = context.computeOnClient(mc -> {
            var row = CatalogScreenTestAccess.firstVisibleCellCenter(requireCatalog(mc));
            var window = mc.getWindow();
            return new double[] {
                    row[0] * window.getScreenWidth() / window.getGuiScaledWidth(),
                    row[1] * window.getScreenHeight() / window.getGuiScaledHeight(),
            };
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(3);
    }

    private static void click(ClientGameTestContext context, String translationKey) {
        context.clickScreenButton(translationKey);
        context.waitTicks(3);
    }

    private static void clearSearch(ClientGameTestContext context, int characters) {
        for (int i = 0; i < characters; i++) {
            context.getInput().pressKey(GLFW.GLFW_KEY_BACKSPACE);
        }
        waitForCondition(context, "empty search", 20,
                mc -> mc.gui.screen() instanceof CatalogScreen && catalogSearchText(mc).isEmpty());
    }

    private static void openCatalog(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_B);
        context.waitForScreen(CatalogScreen.class);
        context.waitTicks(2);
        context.takeScreenshot("catalog-open");
    }

    private static void assertDefaultCatalogAmount(ClientGameTestContext context) {
        var amount = context.computeOnClient(mc -> mc.gui.screen().children().stream()
                .filter(child -> child instanceof EditBox)
                .map(child -> (EditBox) child)
                .skip(1)
                .findFirst()
                .map(EditBox::getValue)
                .orElse(""));
        if (!amount.equals("1")) {
            throw new AssertionError("a new catalog should default to amount 1, but was " + amount);
        }
    }

    private static void search(ClientGameTestContext context, String query) {
        context.getInput().typeChars(query);
        waitForCondition(context, "typed search \"" + query + "\"", 20,
                mc -> mc.gui.screen() instanceof CatalogScreen && catalogSearchText(mc).equals(query));

        var typed = context.computeOnClient(mc -> mc.gui.screen() instanceof CatalogScreen);
        if (!typed) {
            throw new AssertionError("catalog screen closed while typing");
        }

        var matches = context.computeOnClient(mc -> FindMyItemsClient.index().search(query).size());
        if (matches != 1) {
            throw new AssertionError("expected exactly one match for '" + query + "', got " + matches);
        }
    }
}
