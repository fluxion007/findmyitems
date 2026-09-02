# Crafting Planner and Automation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a verified release candidate that makes crafting search, recipe planning, catalog rendering, vanilla reachability, and gather/craft automation correct and safe.

**Architecture:** Preserve the existing index, persistence, single-player boundary, and conservation-safe server retrieval. Extract pure search, recipe-graph/planning, inventory simulation, display-layout, and execution-state services, then connect them to `CatalogScreen` through generation-tagged immutable snapshots.

**Tech Stack:** Java 25, Minecraft 26.2, Fabric Loader 0.19.3, Fabric API 0.154.2+26.2, Fabric Loom 1.17.17, JUnit 6.1.0, Fabric headless GameTests, Fabric client GameTests, Gradle Mod Publish Plugin.

## Global Constraints

- The implementation is one release candidate with phased commits and verification gates.
- Automation supports player-inventory crafting and vanilla crafting tables only.
- The next patch version is `0.1.4`, derived from the current `0.1.3` version during release preparation.
- The mod remains client-side and single-player only.
- Item identity remains item identifier plus registry-backed `StackKey.componentsJson`.
- Failed component encoding must never degrade to `{}`; any degraded representation must remain unique enough to prevent variant conflation.
- Every movement path must conserve items, including full inventories and creative mode.
- Runtime settings use `FindMyItemsClient.config()` as the sole `ModConfig` instance.
- Run `./gradlew build` and `./gradlew runGameTest` before claiming a phase works.
- Run client GameTests only after user approval to open a client window.
- Do not push, tag, publish, or create a remote release without explicit user approval after release preflight.

## File Map

### Existing files to modify

- `src/main/java/dev/smpb/findmyitems/index/SearchQuery.java`: shared normalization, token matching, searchable documents, and match categories.
- `src/main/java/dev/smpb/findmyitems/index/InMemoryContainerIndex.java`: indexed search snapshot, ranking, and stable result ordering.
- `src/main/java/dev/smpb/findmyitems/index/ItemResult.java`: stable result identity and source quantity metadata.
- `src/main/java/dev/smpb/findmyitems/craft/CraftingPlanner.java`: recipe catalog integration and immutable memoized planner.
- `src/main/java/dev/smpb/findmyitems/retrieval/RetrieveHandler.java`: shared reachability, stale-source validation, and safe transfer entry points.
- `src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java`: lazy crafting rows, stable selection, virtualization, clipping, status/actions, and executor integration.
- `src/client/java/dev/smpb/findmyitems/retrieval/GhostOpen.java`: replace implicit one-shot behavior with validated normal interaction support where needed.
- `src/client/java/dev/smpb/findmyitems/FindMyItemsClient.java`: initialize shared services and cancel operations on world lifecycle changes.
- `src/main/resources/assets/findmyitems/lang/en_us.json`: title and user-facing action/status terminology.
- `src/main/resources/fabric.mod.json`: visible mod name/description if metadata changes are needed.
- `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsGameTest.java`: server-side reachability, stale-source, planning integration, and conservation cases.
- `src/gametest/java/dev/smpb/findmyitems/test/RetrieveEdgeCaseGameTest.java`: full inventory, creative, component, and partial-transfer invariants.
- `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsClientGameTest.java`: client UI and automation acceptance cases.
- `README.md`: supported crafting stations, reachability, action states, and fixture instructions.
- `CHANGELOG.md`: verified release notes.
- `gradle.properties`: next patch version after implementation approval.

### New files

- `src/main/java/dev/smpb/findmyitems/search/SearchDocument.java`: immutable searchable fields and normalized token data.
- `src/main/java/dev/smpb/findmyitems/search/SearchIndex.java`: static indexes and deterministic candidate retrieval.
- `src/main/java/dev/smpb/findmyitems/craft/RecipeCatalog.java`: recipe outputs, alternatives, batches, graph, and SCC metadata.
- `src/main/java/dev/smpb/findmyitems/craft/PlanningInventory.java`: immutable/copy-on-write component-aware inventory state.
- `src/main/java/dev/smpb/findmyitems/craft/PlanningPolicy.java`: supported station flags and candidate cap.
- `src/main/java/dev/smpb/findmyitems/craft/PlanScore.java`: lexicographic candidate score.
- `src/main/java/dev/smpb/findmyitems/craft/CraftingPlan.java`: plan nodes, deltas, status, and stable identities.
- `src/main/java/dev/smpb/findmyitems/craft/InventorySimulation.java`: capacity and surplus preflight.
- `src/main/java/dev/smpb/findmyitems/craft/PlayerInventorySnapshot.java`: immutable storage-slot snapshot for simulation.
- `src/main/java/dev/smpb/findmyitems/craft/CapacityResult.java`: simulation result and failure details.
- `src/main/java/dev/smpb/findmyitems/craft/DisplayPlan.java`: root-preserving flattening model.
- `src/main/java/dev/smpb/findmyitems/gui/ViewportLayout.java`: pure visible-row and scroll-range calculations.
- `src/main/java/dev/smpb/findmyitems/retrieval/Reachability.java`: environment-independent reachability facts and reason types.
- `src/main/java/dev/smpb/findmyitems/retrieval/TargetKind.java`: container and crafting-table target categories.
- `src/client/java/dev/smpb/findmyitems/retrieval/ReachabilityService.java`: client/world raycast and handler validation.
- `src/client/java/dev/smpb/findmyitems/retrieval/CraftingExecutor.java`: tick-driven state machine and transfer journal.
- `src/client/java/dev/smpb/findmyitems/retrieval/ExecutionStatus.java`: user-facing executor states and failure reasons.
- `src/client/java/dev/smpb/findmyitems/retrieval/ExecutionRequest.java`: immutable executor input snapshot.
- `src/client/java/dev/smpb/findmyitems/retrieval/CancelReason.java`: executor cancellation causes.
- `src/client/java/dev/smpb/findmyitems/gui/OutputIdentity.java`: stable selected-output identity.
- `src/test/java/dev/smpb/findmyitems/search/SearchQueryTest.java`: pure search behavior tests.
- `src/test/java/dev/smpb/findmyitems/craft/CraftingPlannerTest.java`: pure planning, cycles, alternatives, and allocation tests.
- `src/test/java/dev/smpb/findmyitems/craft/InventorySimulationTest.java`: capacity and surplus tests.
- `src/test/java/dev/smpb/findmyitems/gui/ViewportLayoutTest.java`: pure layout and clipping tests.
- `src/gametest/java/dev/smpb/findmyitems/test/CraftingPlannerGameTest.java`: real recipe and inventory integration cases.
- `src/test/resources/findmyitems-test-fixture/`: documented manual fixture data pack if commands are compatible with Minecraft 26.2.

---

### Task 1: Create the Feature Branch and Capture the Baseline

**Files:**
- Modify: none
- Test: none

**Interfaces:**
- Consumes: current clean `main` worktree and repository instructions.
- Produces: branch `feature/crafting-planner-rework`, recorded baseline command results, and no source changes.

- [ ] **Step 1: Verify the current worktree and branch.**

Run:

```bash
git status --short --branch
```

Expected: no uncommitted source changes, current branch is `main`, and commit `b0dd40b` or its descendant is present.

- [ ] **Step 2: Create the feature branch.**

Run:

```bash
git switch -c feature/crafting-planner-rework
```

- [ ] **Step 3: Run the baseline commands serially.**

Run each command only after the prior command exits, so `runGameTest` does not share its world lock with another Gradle process:

```bash
./gradlew clean test
./gradlew check
./gradlew build
./gradlew runGameTest
```

Expected: JUnit, check, build, and all existing headless GameTests pass. Record existing warnings separately from failures.

- [ ] **Step 4: Commit no code.**

Do not create a commit for this task. Keep the baseline output in the implementation log or final report.

### Task 2: Add Shared Search Documents and Root-Only Ranking

**Files:**
- Create: `src/main/java/dev/smpb/findmyitems/search/SearchDocument.java`
- Create: `src/main/java/dev/smpb/findmyitems/search/SearchIndex.java`
- Modify: `src/main/java/dev/smpb/findmyitems/index/SearchQuery.java`
- Modify: `src/main/java/dev/smpb/findmyitems/index/InMemoryContainerIndex.java`
- Modify: `src/main/java/dev/smpb/findmyitems/index/ItemResult.java`
- Modify: `src/client/java/dev/smpb/findmyitems/search/InventorySearchController.java`
- Test: `src/test/java/dev/smpb/findmyitems/search/SearchQueryTest.java`
- Test: `src/test/java/dev/smpb/findmyitems/index/InMemoryContainerIndexTest.java`

**Interfaces:**
- `SearchDocument.from(StackSnapshot)` returns an immutable document containing normalized display name, item identifier/path, tooltip, component fingerprint, and token fields.
- `SearchQuery.parse(String)` retains the existing parser entry point and returns normalized unique terms.
- `SearchQuery.match(SearchDocument)` returns a match category and numeric score, or no match.
- `SearchIndex.search(SearchQuery, int)` returns all matching root candidate identities ordered deterministically before limiting.
- `ItemResult` exposes a stable identity method based on `StackKey`, and source counts remain component-exact.

- [ ] **Step 1: Write failing unit tests for normalization and matching.**

Add tests covering:

```java
assertThat(SearchQuery.parse("  WHITE   BED  ").terms()).containsExactly("white", "bed");
assertThat(match("White Bed", "minecraft:white_bed", "bed").category()).isEqualTo(EXACT_FULL_NAME);
assertThat(match("White Bed", "minecraft:white_bed", "white bed").category()).isEqualTo(EXACT_FULL_NAME);
assertThat(match("White Bed", "minecraft:white_bed", "whit bed").category()).isEqualTo(FUZZY);
assertThat(match("Bedrock", "minecraft:bedrock", "bed")).isPresent();
```

Add an index test with White Bed, Orange Bed, and Bedrock roots and assert `bed` orders all bed outputs before Bedrock. Add a test proving a root is not included merely because its recipe contains a matching ingredient.

- [ ] **Step 2: Run focused tests and verify the intended failures.**

Run:

```bash
./gradlew test --tests '*SearchQueryTest' --tests '*InMemoryContainerIndexTest'
```

Expected: failures for missing match categories, fuzzy ranking, or root filtering.

- [ ] **Step 3: Implement immutable documents and matching.**

Keep the existing Roman-to-Arabic enchantment alias behavior, but tokenize on normalized word boundaries. Implement the six ordered match categories from the design. Build exact-name, token, prefix, and optional trigram candidate maps in `SearchIndex`; run edit distance only for candidates that survive those maps.

- [ ] **Step 4: Route the index and container filter through the shared matcher.**

Make `InMemoryContainerIndex.search` build documents from indexed snapshots, aggregate only matching root stack keys, and sort by category, score, display name, item ID, and component JSON. Change `InventorySearchController.matches` to use the same document builder for local stacks without using server-only registry state.

- [ ] **Step 5: Run focused and existing tests.**

Run:

```bash
./gradlew test --tests '*SearchQueryTest' --tests '*InMemoryContainerIndexTest'
./gradlew test
```

Expected: all focused and existing JUnit tests pass.

- [ ] **Step 6: Commit the search phase.**

```bash
git add src/main/java/dev/smpb/findmyitems/search src/main/java/dev/smpb/findmyitems/index src/client/java/dev/smpb/findmyitems/search src/test/java/dev/smpb/findmyitems/search src/test/java/dev/smpb/findmyitems/index
git commit -m "feat: add ranked root-only search"
```

### Task 3: Build the Recipe Catalog and Cycle-Safe Planner

**Files:**
- Create: `src/main/java/dev/smpb/findmyitems/craft/RecipeCatalog.java`
- Create: `src/main/java/dev/smpb/findmyitems/craft/PlanningInventory.java`
- Create: `src/main/java/dev/smpb/findmyitems/craft/PlanScore.java`
- Create: `src/main/java/dev/smpb/findmyitems/craft/CraftingPlan.java`
- Create: `src/main/java/dev/smpb/findmyitems/craft/DisplayPlan.java`
- Modify: `src/main/java/dev/smpb/findmyitems/craft/CraftingPlanner.java`
- Test: `src/test/java/dev/smpb/findmyitems/craft/CraftingPlannerTest.java`
- Test: `src/test/java/dev/smpb/findmyitems/craft/DisplayPlanTest.java`

**Interfaces:**
- `RecipeCatalog.from(RecipeManager, Level)` returns a reload-scoped catalog of supported crafting outputs and recipe alternatives.
- `RecipeCatalog.craftableRoots()` returns output identities only.
- `PlanningInventory` stores `Map<StackKey, Long>` available counts and returns copy-on-write consumption/surplus states.
- `PlanningPolicy` is a record containing `boolean allowCraftingTable`, `boolean allowInventoryCrafting`, and `int candidateCap`.
- `CraftingPlanner.plan(RecipeCatalog, StackKey, long, PlanningInventory, PlanningPolicy)` returns `CraftingPlan`.
- `CraftingPlan` contains root node, remaining inventory, consumed/surplus deltas, missing quantities, and `PlanScore`.
- `DisplayPlan.flatten(CraftingPlan)` returns rows with explicit `rootId`, `nodeId`, `parentId`, and `depth`.

- [ ] **Step 1: Write failing pure-planner tests.**

Cover these exact assertions:

```java
// Two sibling branches each need four planks; four total stock covers only one branch.
assertThat(plan.missing("minecraft:oak_planks")).isEqualTo(4);

// Hopper with two ingots and a chest recipe needs three more ingots and never invents a block.
assertThat(plan.missing("minecraft:iron_ingot")).isEqualTo(3);
assertThat(plan.flattenedItemIds()).doesNotContain("minecraft:iron_block");

// An owned iron block may be used as a reversible conversion source, but missing blocks cannot bootstrap ingots.
assertThat(planWithOwnedBlock.hasConversion("minecraft:iron_block", "minecraft:iron_ingot")).isTrue();
assertThat(planWithNoOwnedConversionSource.conversionCount()).isZero();

// Independent roots always start at depth zero.
assertThat(DisplayPlan.flatten(twoRoots)).extracting(DisplayPlan.Row::depth).contains(0, 0);
```

Also cover A -> B -> C -> A termination, output batch rounding, generated surplus, direct-stock preference, and component-bearing stack identity.

- [ ] **Step 2: Run the focused tests and verify failures.**

```bash
./gradlew test --tests '*CraftingPlannerTest' --tests '*DisplayPlanTest'
```

Expected: failures against the current single-recipe, depth-limited planner.

- [ ] **Step 3: Implement `RecipeCatalog`.**

Index only non-special `RecipeType.CRAFTING` recipes that are survival-available. Preserve all alternatives per output, resolve output batch sizes with the level registry context, group ingredient alternatives, and build directed dependency edges. Run Tarjan or Kosaraju once to assign SCC IDs. Use a catalog generation identity for memoization invalidation.

- [ ] **Step 4: Implement copy-on-write planning inventory and score.**

Use `StackKey` rather than item ID for stock. Each candidate receives a child state; consuming a stack decrements only that child. Return generated surplus and remainders explicitly. Use `long` arithmetic with `Math.addExact`/`Math.multiplyExact` and convert overflow into a failed candidate rather than wrapping.

- [ ] **Step 5: Replace recursive expansion with candidate evaluation and memoization.**

For every request, consume stock, detect path re-entry/SCC conversion policy, calculate craft count, aggregate immediate ingredients, solve each child against the candidate state, add surplus, score, and select the lexicographically best candidate. Memoize by the full state tuple defined in the spec, never by item ID alone. Accept a cancellation token at every candidate loop.

- [ ] **Step 6: Implement explicit display flattening.**

Flatten with `flatten(node, depth, rootId, parentId, out)`, passing depth as a value. Do not retain mutable depth in the planner or screen. Preserve one root row per independent result.

- [ ] **Step 7: Run focused, JUnit, and serial build checks.**

```bash
./gradlew test --tests '*CraftingPlannerTest' --tests '*DisplayPlanTest'
./gradlew test
./gradlew build
```

Expected: all pass.

- [ ] **Step 8: Commit the planner phase.**

```bash
git add src/main/java/dev/smpb/findmyitems/craft src/test/java/dev/smpb/findmyitems/craft
git commit -m "feat: add cycle-safe memoized crafting planner"
```

### Task 4: Add Inventory Simulation and Pure Viewport Layout

**Files:**
- Create: `src/main/java/dev/smpb/findmyitems/craft/InventorySimulation.java`
- Create: `src/main/java/dev/smpb/findmyitems/gui/ViewportLayout.java`
- Test: `src/test/java/dev/smpb/findmyitems/craft/InventorySimulationTest.java`
- Test: `src/test/java/dev/smpb/findmyitems/gui/ViewportLayoutTest.java`

**Interfaces:**
- `InventorySimulation.simulate(PlayerInventorySnapshot, CraftingPlan)` returns `CapacityResult` with required free capacity, final stacks, surplus, and a safe/unsafe status.
- `PlayerInventorySnapshot` is an immutable record of the 36 storage-slot `List<ItemStack>` copied before execution.
- `CapacityResult` is an immutable record containing `boolean safe`, `int requiredFreeSlots`, `List<ItemStack> finalStacks`, `Map<StackKey, Long> surplus`, and `String failureReason`.
- `ViewportLayout.layout(int top, int bottom, int rowHeight, int rowCount, double scroll, int overscan)` returns first visible row, last visible row, maximum scroll, and row rectangles.

- [ ] **Step 1: Write failing tests.**

Test partial compatible stacks, full inventories, component-incompatible stacks, bucket/bottle remainders, batch surplus, and a final crafted output that needs an otherwise empty slot. Test viewport rows at top, middle, and bottom, including a final row that must not overlap the footer and hit-testing outside the viewport.

- [ ] **Step 2: Run focused tests.**

```bash
./gradlew test --tests '*InventorySimulationTest' --tests '*ViewportLayoutTest'
```

Expected: failures because these services do not exist.

- [ ] **Step 3: Implement capacity simulation.**

Model only player storage slots accepted by vanilla inventory insertion. Apply planned source transfers and intermediate outputs in order. Treat stacks as compatible only when `ItemStack.isSameItemSameComponents` would be true. Return the exact first unsafe stage and required free slots.

- [ ] **Step 4: Implement viewport math.**

Derive `viewportHeight`, `scrollMaximum`, `firstVisibleRow`, and `lastVisibleRow` from one rectangle. Clamp scroll to `[0, scrollMaximum]`. Include only overscan rows in rendering and reject hit tests outside the same rectangle.

- [ ] **Step 5: Run tests and commit.**

```bash
./gradlew test --tests '*InventorySimulationTest' --tests '*ViewportLayoutTest'
git add src/main/java/dev/smpb/findmyitems/craft/InventorySimulation.java src/main/java/dev/smpb/findmyitems/gui/ViewportLayout.java src/test/java/dev/smpb/findmyitems/craft/InventorySimulationTest.java src/test/java/dev/smpb/findmyitems/gui/ViewportLayoutTest.java
git commit -m "feat: simulate crafting capacity and viewport layout"
```

### Task 5: Integrate Lazy Crafting UI, Stable Selection, and Virtualization

**Files:**
- Modify: `src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java`
- Modify: `src/client/java/dev/smpb/findmyitems/FindMyItemsClient.java`
- Modify: `src/main/resources/assets/findmyitems/lang/en_us.json`
- Test: `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsClientGameTest.java`

**Interfaces:**
- `CatalogScreen` consumes `RecipeCatalog.craftableRoots()` for browse rows and requests a plan only for a selected root.
- `OutputIdentity` is a stable record of output `StackKey` plus recipe-catalog generation.
- `CatalogScreen` stores `OutputIdentity selectedOutput`, `OutputIdentity hoveredOutput`, `long searchGeneration`, and `long planGeneration`.
- A completed plan is applied only when its generation matches the current screen query, root, amount, index revision, and recipe generation.
- `CatalogScreen` exposes package-private test accessors for current rows, scroll amount, selected identity, and visible row count.

- [ ] **Step 1: Add failing client assertions without running the client yet.**

Extend the client GameTest source with assertions for empty Crafting tab opening not invoking `CraftingPlanner.plan`, browse rows being root outputs only, selecting one output invoking exactly one plan request, stale selection clearing after filter, stable scroll after index-only refresh, and bottom-row clipping/hit-testing.

- [ ] **Step 2: Run the focused client task if approved by the user.**

```bash
./gradlew runClientGameTest
```

Expected: the new assertions fail against eager planning, index-based selection, and the current list reset behavior. If the task cannot run because the display is unavailable, record that as an environment blocker and continue with pure tests.

- [ ] **Step 3: Separate browse rows from selected-plan rows.**

Replace `allItemRows()` with a lightweight root-entry list from `RecipeCatalog`. Keep `craftingRows()` for one selected plan only. Do not call `CraftingPlanner.plan` from empty-query browsing or row rendering.

- [ ] **Step 4: Apply stable identity and generation checks.**

Preserve selection only if the same output identity remains. Clear hover on query changes. Increment generation on query, amount, view, layout, index revision, recipe reload, and selection changes. Apply asynchronous plan results only when all captured values still match.

- [ ] **Step 5: Replace list calculations with `ViewportLayout`.**

Use one list viewport for rendering, scissor clipping, scroll maximum, and mouse hit testing. Render visible rows plus overscan. Preserve scroll on index-only refreshes by capturing the old scroll and restoring the clamped value after rows are replaced.

- [ ] **Step 6: Update title and terminology.**

Change the visible title translation to `Find My Items`. Add translations for `Gather materials`, `Gather and craft`, `Known in storage`, `Reachable now`, `Unavailable`, `Missing materials`, `No reachable crafting table`, `Calculating`, `Busy`, and `Failed`.

- [ ] **Step 7: Run client tests and commit.**

```bash
./gradlew runClientGameTest
git add src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java src/client/java/dev/smpb/findmyitems/FindMyItemsClient.java src/main/resources/assets/findmyitems/lang/en_us.json src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsClientGameTest.java
git commit -m "fix: lazy-load and virtualize crafting catalog"
```

### Task 6: Implement Shared Vanilla Reachability

**Files:**
- Create: `src/main/java/dev/smpb/findmyitems/retrieval/Reachability.java`
- Create: `src/client/java/dev/smpb/findmyitems/retrieval/ReachabilityService.java`
- Modify: `src/main/java/dev/smpb/findmyitems/retrieval/RetrieveHandler.java`
- Modify: `src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java`
- Modify: `src/client/java/dev/smpb/findmyitems/retrieval/GhostOpen.java`
- Test: `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsGameTest.java`

**Interfaces:**
- `Reachability.Result` contains `boolean actionable`, `Reason`, target position, dimension, and handler expectation.
- `TargetKind` is an enum containing `CONTAINER` and `CRAFTING_TABLE`.
- `ReachabilityService.check(BlockPos, TargetKind)` validates client world state and returns `Reachability.Result`.
- `RetrieveHandler.inReach` becomes a conservative early rejection; server retrieval additionally validates loaded block identity and interaction context.
- `CatalogScreen` uses the service for locate/action state and source choice.

- [ ] **Step 1: Write failing headless tests.**

Add chest and crafting-table fixtures for nearby unobstructed, nearby behind stone, visible through a doorway, unloaded chunk, wrong block, and outside vanilla range. Assert radius alone cannot make an obstructed target actionable.

- [ ] **Step 2: Run the focused GameTests.**

```bash
./gradlew runGameTest
```

Expected: new obstruction and wrong-block tests fail against the current radius/center-point logic.

- [ ] **Step 3: Implement target facts and server-side validation.**

Check same dimension, `world.isLoaded`, expected block state, interaction range, and the block entity/handler that vanilla would open. Keep configured extended reach as an upper bound but require a valid target and loaded chunk.

- [ ] **Step 4: Implement client raycast sampling.**

Sample the block interaction shape's visible points and reject the target when every candidate is blocked by another block. Use the same target classification for containers and crafting tables.

- [ ] **Step 5: Update UI and GhostOpen guards.**

Hide locate when indexed count is zero. Keep locate available for positive unavailable stock, but label it unavailable for automatic retrieval. Refuse `GhostOpen` when the shared service says no valid interaction point.

- [ ] **Step 6: Run all server tests and commit.**

```bash
./gradlew runGameTest
git add src/main/java/dev/smpb/findmyitems/retrieval src/client/java/dev/smpb/findmyitems/retrieval src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsGameTest.java
git commit -m "fix: require vanilla-valid container reachability"
```

### Task 7: Add Tick-Driven Gather and Craft Execution

**Files:**
- Create: `src/client/java/dev/smpb/findmyitems/retrieval/ExecutionStatus.java`
- Create: `src/client/java/dev/smpb/findmyitems/retrieval/CraftingExecutor.java`
- Modify: `src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java`
- Modify: `src/client/java/dev/smpb/findmyitems/FindMyItemsClient.java`
- Modify: `src/main/java/dev/smpb/findmyitems/retrieval/RetrieveHandler.java`
- Test: `src/gametest/java/dev/smpb/findmyitems/test/CraftingPlannerGameTest.java`
- Test: `src/gametest/java/dev/smpb/findmyitems/test/RetrieveEdgeCaseGameTest.java`
- Test: `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsGameTest.java`

**Interfaces:**
- `CraftingExecutor.start(ExecutionRequest)` accepts a plan, immutable source snapshot, player/world generation, and mode `GATHER_ONLY` or `GATHER_AND_CRAFT`.
- `CraftingExecutor.tick()` advances at most one bounded action per tick.
- `CancelReason` is an enum containing `SCREEN_CLOSED`, `SELECTION_CHANGED`, `QUERY_CHANGED`, `OUT_OF_REACH`, `DIMENSION_CHANGED`, `PLAYER_DIED`, `SOURCE_CHANGED`, `TARGET_CHANGED`, `INVENTORY_FULL`, and `SUPERSEDED`.
- `CraftingExecutor.cancel(CancelReason)` records a transfer journal and returns `ExecutionStatus.CANCELLED`.
- `CraftingExecutor.status()` returns calculating, gather, craft, missing, no-table, full, busy, failed, or complete state.

- [ ] **Step 1: Write failing GameTests and client assertions.**

Cover diamond pickaxe materials with a reachable table, the same materials without a reachable table, missing materials, full inventory, stale source slot changes, source deletion, player movement away, screen closure, selection change, and a second automation request while busy. Assert no item is lost and no pickaxe is fabricated without a reachable table.

- [ ] **Step 2: Run focused tests and verify failures.**

```bash
./gradlew runGameTest
```

Expected: new tests fail because no executor or state machine exists.

- [ ] **Step 3: Implement execution states and cancellation.**

Implement `Preflight`, `OpenSource`, `WaitForSource`, `ValidateSource`, `Transfer`, `CloseSource`, `CraftInventory`, `LocateTable`, `OpenTable`, `WaitForTable`, `ValidateTable`, `PlaceRecipe`, `TakeOutput`, `Complete`, `Cancelled`, and `Failed`. Each transition checks generation, timeout, reachability, screen identity, and inventory state.

- [ ] **Step 4: Implement exact stale-source transfers.**

Before each slot action, compare the actual stack's item ID, component JSON, and required count to the plan. If changed, stop and replan. Use `RetrieveHandler` conservation-safe insertion logic and reconcile the index after movement.

- [ ] **Step 5: Implement gather-only and crafting-table flows.**

Gather-only stops after legal player-inventory subrecipes and reports the materials still requiring a table. Gather-and-craft continues only after a reachability recheck and capacity simulation. Use vanilla inventory/menu slot actions; never mutate a remote container directly.

- [ ] **Step 6: Run all relevant tests.**

```bash
./gradlew runGameTest
./gradlew test
```

Expected: all server and JUnit tests pass, with conservation assertions covering normal and creative inventories.

- [ ] **Step 7: Commit the executor phase.**

```bash
git add src/client/java/dev/smpb/findmyitems/retrieval src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java src/client/java/dev/smpb/findmyitems/FindMyItemsClient.java src/main/java/dev/smpb/findmyitems/retrieval src/gametest/java/dev/smpb/findmyitems/test
git commit -m "feat: add cancellable gather and craft execution"
```

### Task 8: Expand Integration Tests and Manual Fixture

**Files:**
- Modify: `src/gametest/resources/fabric.mod.json`
- Modify: `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsGameTest.java`
- Modify: `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsClientGameTest.java`
- Create: `src/test/resources/findmyitems-test-fixture/pack.mcmeta`
- Create: `src/test/resources/findmyitems-test-fixture/data/findmyitems/function/setup.mcfunction`
- Create: `src/test/resources/findmyitems-test-fixture/data/findmyitems/function/reset.mcfunction`
- Create: `src/test/resources/findmyitems-test-fixture/data/minecraft/tags/function/load.json`
- Modify: `README.md`

**Interfaces:**
- The new `CraftingPlannerGameTest` is listed in the `fabric-gametest` entrypoint and runs headlessly.
- `/function findmyitems:setup` creates deterministic accessible, obstructed, doorway-visible, far, double-chest, hopper-fed, crafting-table, and partial-material fixtures.
- `/function findmyitems:reset` removes only fixture blocks and restores the documented area.

- [ ] **Step 1: Add all acceptance cases to test sources.**

Include search queries `bed`, `white bed`, `bedrock`, `whit bed`, and repeated whitespace; multiple root depths; SCC cycles; shared stock; batch surplus; empty browse laziness; bottom clipping; selection invalidation; obstruction; locate count zero/positive; reachable table; no table; cancellation; stale source; and inventory-full behavior.

- [ ] **Step 2: Run headless acceptance tests.**

```bash
./gradlew runGameTest
```

Expected: all required tests pass and the log reports zero failed required tests.

- [ ] **Step 3: Add the manual fixture and README instructions.**

Document copying or loading the fixture, running setup/reset, opening each container once to index it, and checking each acceptance case. State that only crafting-table and player-inventory crafting are supported.

- [ ] **Step 4: Obtain client-test approval and run the client suite.**

Ask the user before opening the client window. After approval, run:

```bash
./gradlew runClientGameTest
```

Expected: client assertions pass and screenshots are written under `build/run/clientGameTest/screenshots/`.

- [ ] **Step 5: Launch the development client for manual verification.**

Run:

```bash
./gradlew runClient
```

In the fixture world, verify startup, title, search ranking, lazy browse responsiveness, bottom scrolling, selection, line-of-sight reachability, locate counts, gather-only, gather-and-craft, cancellation, stale sources, and near-full inventory. Record each case, setup, expected result, actual result, and screenshot/log location.

- [ ] **Step 6: Commit the integration and fixture phase.**

```bash
git add src/gametest src/test/resources/findmyitems-test-fixture README.md
git commit -m "test: cover crafting automation acceptance cases"
```

### Task 9: Final Documentation, Release Candidate, and Preflight

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `gradle.properties`
- Modify: `README.md`
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `src/main/resources/assets/findmyitems/lang/en_us.json`

**Interfaces:**
- `gradle.properties` contains `mod_version=0.1.4`.
- `CHANGELOG.md` has a matching dated version section used by `publishMods` changelog extraction.
- The built jar contains the matching metadata and no development-only debug classes.

- [ ] **Step 1: Update release documentation.**

Add changelog entries for correct crafting search, word-aware fuzzy matching, cycle-safe planning, lazy/virtualized UI, vanilla-valid line-of-sight reachability, gather-only, gather-and-craft, cancellation, inventory safety, stale-index recovery, and title/UI fixes. Do not claim support for furnaces, stonecutters, smithing, or multiplayer.

- [ ] **Step 2: Set the next patch version and inspect metadata.**

Set `mod_version=0.1.4` and ensure the version appears consistently in `gradle.properties`, processed `fabric.mod.json`, artifact name, and changelog heading. Keep the internal mod ID `findmyitems` unchanged.

- [ ] **Step 3: Run the final serial verification gates.**

```bash
./gradlew clean
./gradlew test
./gradlew check
./gradlew build
./gradlew runGameTest
./gradlew publishMods -PdryRun
git diff --check
```

Expected: every command succeeds; dry-run reports the proposed version, matching changelog section, supported Minecraft version, dependencies, and artifact without uploading.

- [ ] **Step 4: Inspect the release artifact.**

```bash
find build/libs -maxdepth 1 -type f -print
VERSION=$(awk -F= '/^mod_version=/{print $2}' gradle.properties)
jar tf "build/libs/findmyitems-${VERSION}.jar"
```

Confirm the release jar has the expected version metadata, no unwanted classifier, and no `dev/smpb/findmyitems/debug/` classes.

- [ ] **Step 5: Review the complete local release preflight.**

Inspect:

```bash
git status --short --branch
git log --oneline -10
```

Report changed files, commits, automated results, manual test matrix, performance observations, known limitations, artifact path, proposed version, changelog, and release title. Stop and wait for explicit user approval.

- [ ] **Step 6: Publish only after explicit approval.**

After approval, verify the token without printing it:

```bash
test -n "$MODRINTH_TOKEN" && echo "MODRINTH_TOKEN is set" || echo "MODRINTH_TOKEN is missing"
```

Run one final build and the configured publish task only after the token check succeeds:

```bash
./gradlew clean check build
./gradlew publishMods
```

Do not push, tag, create a GitHub release, or retry a failed publication blindly. Diagnose any failure first and report the exact publication result.

## Plan Self-Review

- Root-only search and word-aware fuzzy matching are covered by Task 2 and the acceptance tests in Task 8.
- Recipe alternatives, SCC cycle prevention, shared stock, batch surplus, memoization, and long quantities are covered by Task 3.
- Lazy planning, stable selection, virtualization, clipping, scroll preservation, and title changes are covered by Tasks 4 and 5.
- Vanilla reachability, obstruction, line-of-sight, and loaded-chunk validation are covered by Task 6.
- Gather-only, gather-and-craft, normal handlers, cancellation, stale indexes, and conservation are covered by Task 7.
- Headless, client, and manual verification are covered by Task 8 and final serial gates in Task 9.
- Release metadata, dry-run publishing, artifact inspection, and explicit publication approval are covered by Task 9.
- No task claims support for unsupported recipe stations or multiplayer.

## Final Review Closure

- [x] Colored `ShulkerBoxBlock` reachability, locating, retrieval, and gathering paths accept every
  shulker color; server GameTest coverage includes a purple shulker target.
- [x] Crafting nodes retain the selected recipe, ingredient alternatives, batch count, remainders,
  and surplus; the executor consumes that immutable selection and never reselects the first recipe.
- [x] Crafting root browse/filter uses `SearchQuery` and `SearchIndex` ranking. Unit coverage includes
  `whit bed`, repeated whitespace, exact/root ordering, and ingredient-only exclusion.
- [x] Nested retrieval preserves physical empty slots and exact provenance paths through transfer and
  reconciliation; repeated nested retrieval coverage verifies stable paths.
- [x] Verification: `./gradlew test`, `./gradlew build`, `./gradlew runGameTest` (55 required tests),
  `./gradlew runClientGameTest`, `./gradlew publishMods -PdryRun`, and `git diff --check` passed.
