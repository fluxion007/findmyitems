# Nested Retrieval and Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make nested shulker results retrieve the whole box, improve enchantment-aware search and ranking, stabilize catalog refreshes, exclude creative-only crafting targets, and provide automated and manual test fixtures.

**Architecture:** Preserve nested item search results but attach exact container-slot provenance for the outermost shulker. Share searchable-document construction and matching between the catalog index and vanilla container filter, while keeping server-side retrieval authoritative. Keep ranking and scroll restoration separate from item identity and retrieval validation.

**Tech Stack:** Java, Fabric/Minecraft game tests, JUnit, client game tests, Gradle, Minecraft data pack JSON/functions.

## Global Constraints

- Every item-moving path must conserve items, including full inventories and creative mode.
- The mod remains single-player only; do not add multiplayer behavior.
- Item identity remains item ID plus registry-backed `StackKey.componentsJson`.
- Never replace failed component encoding with `{}`; use a unique degraded key if degradation is required.
- Use `player.registryAccess()` for component/enchantment serialization at both indexing and retrieval ends.
- Run `./gradlew build` and `./gradlew runGameTest` before claiming the change works.
- Client game tests require explicit user approval before opening a client window.

---

## File Map

- Modify `src/main/java/dev/smpb/findmyitems/model/SlotSnapshot.java` and `StackSnapshot.java`: carry nested provenance without changing item identity.
- Modify `src/main/java/dev/smpb/findmyitems/observation/SlotReader.java`: emit provenance while recursively reading container contents.
- Modify `src/main/java/dev/smpb/findmyitems/index/ItemResult.java`, `InMemoryContainerIndex.java`, and `SearchQuery.java`: preserve provenance, centralize matching, and rank results.
- Modify `src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java`: send nested Take requests correctly and preserve list scroll during index refreshes.
- Modify `src/client/java/dev/smpb/findmyitems/search/InventorySearchController.java`: call the shared matcher.
- Modify `src/main/java/dev/smpb/findmyitems/retrieval/RetrieveHandler.java`: validate and move an identified outer shulker as one stack.
- Modify `src/main/java/dev/smpb/findmyitems/craft/CraftingPlanner.java`: filter recipe outputs by survival availability.
- Modify `src/test/java/dev/smpb/findmyitems/index/InMemoryContainerIndexTest.java` and add focused search tests: verify matching and ranking.
- Modify `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsGameTest.java` and `RetrieveEdgeCaseGameTest.java`: verify nested whole-box retrieval, ender chests, conservation, variants, and depth limits.
- Modify `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsClientGameTest.java`: verify client search and refresh behavior where the harness supports it.
- Create `src/test/resources/findmyitems-test-fixture/`: reusable data pack with chest-room setup and instructions.
- Modify `README.md`: document installing and using the fixture data pack.

---

### Task 1: Add Provenance to Nested Index Entries

**Files:**
- Modify: `src/main/java/dev/smpb/findmyitems/model/SlotSnapshot.java`
- Modify: `src/main/java/dev/smpb/findmyitems/model/StackSnapshot.java`
- Modify: `src/main/java/dev/smpb/findmyitems/observation/SlotReader.java`
- Test: `src/test/java/dev/smpb/findmyitems/observation/ObservationBuilderTest.java`

**Interfaces:**
- Produce an immutable path type representing the direct source slot and nested shulker slots, with the first shulker identified as the retrieval holder.
- Keep `StackKey` unchanged; provenance is location metadata, not item identity.
- Preserve existing direct-item constructors through explicit empty provenance so current tests remain readable.

- [ ] **Step 1: Write failing tests** for a chest slot containing a shulker with a gold stack: the gold snapshot must expose the outer shulker slot, and a second nested shulker must retain the outermost holder rather than replacing it.
- [ ] **Step 2: Run the focused tests** with `./gradlew test --tests '*ObservationBuilderTest*'`; expect failure because snapshots do not expose provenance.
- [ ] **Step 3: Add the immutable provenance record** and thread it through recursive `SlotReader` traversal. The direct-slot path must be stable and bounded by the existing nesting limit.
- [ ] **Step 4: Run the focused tests** again; expect PASS and verify existing component-key tests still pass.
- [ ] **Step 5: Commit** with `git add src/main/java src/test/java && git commit -m "feat: retain nested item provenance"`.

### Task 2: Centralize Enchantment Search and Relevance Ranking

**Files:**
- Modify: `src/main/java/dev/smpb/findmyitems/index/SearchQuery.java`
- Modify: `src/main/java/dev/smpb/findmyitems/index/InMemoryContainerIndex.java`
- Modify: `src/client/java/dev/smpb/findmyitems/search/InventorySearchController.java`
- Test: `src/test/java/dev/smpb/findmyitems/index/InMemoryContainerIndexTest.java`
- Test: `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsGameTest.java`

**Interfaces:**
- `SearchQuery.parse(String)` remains the query parser.
- Add a shared `SearchQuery.matches(ItemStack or searchable document, String)`-equivalent API that the client filter can call without server-only registry state.
- Add a relevance score method used by `InMemoryContainerIndex.search`, with stable key tie-breakers.

- [ ] **Step 1: Add failing tests** for enchanted books and swords: `smite` matches both; `diamond sword smite` excludes Sharpness; `smite 4` matches Smite IV; exact `stone` sorts before names containing or extending it.
- [ ] **Step 2: Run `./gradlew test --tests '*InMemoryContainerIndexTest*'`** and the relevant game test; expect failures for ranking and any missing shared API.
- [ ] **Step 3: Refactor `SearchQuery`** to build one normalized document from display name, item ID, and tooltip, preserving Roman-to-Arabic level aliases. Keep token AND semantics.
- [ ] **Step 4: Implement relevance scoring** in the index and call it from `InventorySearchController` using the stack’s local display name, ID, and tooltip. Do not make the client filter depend on `StackKey` serialization.
- [ ] **Step 5: Run focused unit/game tests** and expect PASS for all query cases.
- [ ] **Step 6: Commit** with `git add src/main src/client src/test src/gametest && git commit -m "feat: rank enchantment-aware search results"`.

### Task 3: Retrieve the Identified Outer Shulker

**Files:**
- Modify: `src/main/java/dev/smpb/findmyitems/index/ItemResult.java`
- Modify: `src/main/java/dev/smpb/findmyitems/index/InMemoryContainerIndex.java`
- Modify: `src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java`
- Modify: `src/main/java/dev/smpb/findmyitems/retrieval/RetrieveHandler.java`
- Test: `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsGameTest.java`
- Test: `src/gametest/java/dev/smpb/findmyitems/test/RetrieveEdgeCaseGameTest.java`

**Interfaces:**
- `ItemResult` exposes optional nested provenance and continues to expose the inner item’s search/display data.
- Add a server retrieval entry point accepting the source position plus holder slot/path and expected holder component key; return false without mutating anything if the path is stale or invalid.
- Direct retrieval continues through the existing `(itemId, componentsJson, amount)` path.

- [ ] **Step 1: Write failing game tests** for a shulker in a normal chest and a player ender chest. Search metadata must identify the nested result, while Take must move one complete shulker and leave every contained item intact. Add a two-matching-shulker test proving the selected holder is deterministic.
- [ ] **Step 2: Run `./gradlew runGameTest`** with the new tests; expect failure because nested retrieval currently extracts inner items.
- [ ] **Step 3: Add holder provenance to aggregated results** without merging away distinct holder paths. Preserve totals for direct items and make nested rows actionable against their specific holder.
- [ ] **Step 4: Implement server-side holder-path resolution and validation**. Check reach, container identity, slot contents, holder item identity/components, and player inventory capacity before removing the holder. Use the existing conservation-safe `give` logic and never shrink the source before knowing how much moved.
- [ ] **Step 5: Update catalog action selection** so nested rows request whole-box retrieval and direct rows retain amount retrieval. Display status when a stale path or full inventory prevents the move.
- [ ] **Step 6: Run all retrieval game tests**, including full inventory, creative mode, double chest, ender chest, component variants, and nesting limit; expect PASS.
- [ ] **Step 7: Commit** with `git add src/main src/client src/gametest && git commit -m "feat: retrieve nested shulkers intact"`.

### Task 4: Preserve Catalog Refresh Position and Filter Crafting Targets

**Files:**
- Modify: `src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java`
- Modify: `src/main/java/dev/smpb/findmyitems/craft/CraftingPlanner.java`
- Modify: `src/client/java/dev/smpb/findmyitems/observation/ObservationCollector.java` only if refresh notifications need narrowing
- Test: `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsClientGameTest.java`

**Interfaces:**
- Capture and restore the `RowList` scroll amount around index-only `updateResults()` calls.
- Keep explicit query/view/layout rebuilds free to reset or reposition the list.
- `CraftingPlanner.craftable` returns only recipe outputs passing the survival-availability predicate.

- [ ] **Step 1: Write failing client/unit tests** that set a nonzero catalog scroll, increment the index revision, tick the screen, and assert the scroll remains; add a crafting assertion that command blocks and barriers are absent while ordinary recipe outputs remain.
- [ ] **Step 2: Run the focused client tests** using the repository’s existing client-test task; expect failure on scroll reset and creative outputs.
- [ ] **Step 3: Separate explicit rebuilds from revision refreshes** and restore the old scroll amount after replacing rows when the query/view/layout identity is unchanged.
- [ ] **Step 4: Apply the authoritative vanilla survival-availability check** when building the craftable-output index, avoiding a manually maintained item-name list.
- [ ] **Step 5: Run client and crafting tests** and expect PASS.
- [ ] **Step 6: Commit** with `git add src/client src/main src/gametest && git commit -m "fix: stabilize catalog refreshes and crafting targets"`.

### Task 5: Add Fixture Data Pack, Cleanup, and Full Verification

**Files:**
- Create: `src/test/resources/findmyitems-test-fixture/pack.mcmeta`
- Create: `src/test/resources/findmyitems-test-fixture/data/findmyitems/function/setup.mcfunction`
- Create: `src/test/resources/findmyitems-test-fixture/data/findmyitems/function/reset.mcfunction`
- Create: `src/test/resources/findmyitems-test-fixture/data/minecraft/tags/function/load.json`
- Modify: `README.md`
- Modify: touched Java files from Tasks 1-4 for concise comments and dead-code removal

**Interfaces:**
- `/function findmyitems:setup` creates a labeled chest room with direct stone, enchanted books, Smite/Sharpness swords, multiple shulkers, and ender-chest instructions.
- `/function findmyitems:reset` removes and recreates the fixture without affecting unrelated player/world content.

- [ ] **Step 1: Create the data pack** with version metadata, deterministic room construction, item NBT/components, signs, and reset behavior. Keep commands compatible with the repository’s Minecraft version.
- [ ] **Step 2: Add README instructions** for copying the pack into a test world, running setup/reset, opening each container once so the index can observe it, and manually checking search, whole-box Take, scroll preservation, and crafting exclusions.
- [ ] **Step 3: Remove only redundant comments and dead code** in modified files. Retain conservation, registry, and single-player rationale comments.
- [ ] **Step 4: Run `git diff --check`** and inspect the complete diff for accidental scope expansion.
- [ ] **Step 5: Run `./gradlew build`**; expected result is BUILD SUCCESSFUL.
- [ ] **Step 6: Run `./gradlew runGameTest`**; expected result is all server/game tests passing.
- [ ] **Step 7: Ask the user whether to open a client window**, then run the client game tests if approved. Do not run `runClientGameTest` without that approval.
- [ ] **Step 8: Launch the playable client/world**, install the reusable fixture, run setup, open every fixture container, and manually verify the requested edge cases.
- [ ] **Step 9: Commit** with `git add README.md src/test/resources src/main src/client src/test src/gametest && git commit -m "test: add nested search fixture"`.

## Plan Self-Review

- Nested whole-box retrieval: Task 1 records provenance and Task 3 consumes it.
- Enchanted-book and sword searches: Task 2 covers shared fields, token AND matching, levels, and ranking.
- Stone result ordering and refresh scroll jumps: Tasks 2 and 4 cover ranking and position preservation.
- Creative crafting exclusions: Task 4 covers authoritative filtering.
- Comment/code cleanup: Task 5 scopes cleanup to touched files.
- Automated, client, manual, and data-pack testing: Tasks 1-5 cover each requested verification path.
- No unresolved placeholders or undefined cross-task method names remain; implementations must preserve the stated interfaces.
