# findmyitems Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a client-side Fabric mod for Minecraft Java 26.2 that remembers searched containers, safely retrieves reachable items, and recursively plans and fetches materials for manual crafting requests.

**Architecture:** Pure Java modules own item identity, indexing, persistence, retrieval state, recipe planning, and batch coordination. Minecraft-specific classes are adapters at narrow seams; Fabric screens only consume immutable view models and submit commands. Every transfer is revalidated through the live vanilla screen handler.

**Tech Stack:** Java 25, Gradle 9.5.1, Fabric Loom 1.17-SNAPSHOT, Fabric Loader 0.19.3, Fabric API 0.154.2+26.2, JUnit 6.1.0, Gson supplied by Minecraft.

## Global Constraints

- Public name and mod ID are exactly `findmyitems`; Java package is `dev.smpb.findmyitems`.
- Support Minecraft Java 26.2 single-player; multiplayer remains undocumented and best effort.
- The mod is client-only and sends no custom networking packets.
- Supported storage is chest, trapped chest, barrel, placed shulker box, and ender chest; utility screens and generic modded containers are excluded.
- No background scanning, automatic crafting, remote transfer, raw OpenGL/Vulkan calls, or mixins into performance-mod classes.
- All mutable Minecraft state is read or changed on the client thread; pure modules receive immutable snapshots.
- Implement each behavior test-first and commit only after focused tests and `./gradlew check` pass.

---

## File Map

Build and metadata:

- `settings.gradle`, `build.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`: Fabric/Gradle build.
- `src/main/resources/fabric.mod.json`: client-only mod metadata.
- `src/main/resources/assets/findmyitems/lang/en_us.json`: visible copy.
- `README.md`: installation, supported scope, controls, and limitations.
- `src/main/java/dev/smpb/findmyitems/FindMyItemsMod.java`: environment-neutral constants only.

Pure modules:

- `src/main/java/dev/smpb/findmyitems/model/*`: immutable item, slot, source, observation, and world records.
- `src/main/java/dev/smpb/findmyitems/index/*`: `ContainerIndex` interface and in-memory implementation.
- `src/main/java/dev/smpb/findmyitems/store/*`: schema DTOs, codec, and atomic world store.
- `src/main/java/dev/smpb/findmyitems/retrieval/*`: one-source transaction state machine.
- `src/main/java/dev/smpb/findmyitems/recipe/*`: recipe graph and recursive planner.
- `src/main/java/dev/smpb/findmyitems/batch/*`: multi-source material-retrieval coordinator.

Minecraft adapters and UI:

- `src/client/java/dev/smpb/findmyitems/FindMyItemsClient.java`: composition root and lifecycle.
- `src/client/java/dev/smpb/findmyitems/minecraft/*`: world, container, stack, recipe, reachability, and transfer adapters.
- `src/client/java/dev/smpb/findmyitems/ui/InventorySearchOverlay.java`: search field and slot dimming.
- `src/client/java/dev/smpb/findmyitems/ui/CatalogScreen.java`: global split catalog and craft-request UI.
- `src/client/java/dev/smpb/findmyitems/ui/CatalogController.java`: immutable UI state and commands.

Tests mirror pure-module packages under `src/test/java`; deterministic client test scenarios live in `docs/testing/manual-client-scenarios.md`.

### Task 1: Scaffold the Fabric client project

**Files:**
- Create all build/metadata files listed above.
- Create: `src/main/java/dev/smpb/findmyitems/FindMyItemsMod.java`
- Create: `src/client/java/dev/smpb/findmyitems/FindMyItemsClient.java`
- Test: `src/test/java/dev/smpb/findmyitems/ScaffoldTest.java`

**Interfaces:**
- Produces `FindMyItemsClient implements ClientModInitializer` as the only Fabric entrypoint.

- [ ] **Step 1: Install JDK 25 and verify `java --version` reports 25.**

Run: `brew install --cask temurin@25` and `/usr/libexec/java_home -v 25 --exec java --version`.
Expected: exit 0 and `openjdk 25`.

- [ ] **Step 2: Generate the wrapper and build files from Fabric's official example layout.**

Use these exact dependency properties:

```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
minecraft_version=26.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT
fabric_api_version=0.154.2+26.2
mod_version=0.1.0
maven_group=dev.smpb
archives_base_name=findmyitems
junit_version=6.1.0
```

Configure `build.gradle` with split environment source sets, Java release 25, `modImplementation` for Loader/Fabric API, `testImplementation platform("org.junit:junit-bom:${junit_version}")`, `testImplementation "org.junit.jupiter:junit-jupiter"`, and `test { useJUnitPlatform() }`.

- [ ] **Step 3: Write the scaffold test.**

```java
package dev.smpb.findmyitems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class ScaffoldTest {
    @Test void modIdentityIsStable() {
        assertEquals("findmyitems", FindMyItemsMod.MOD_ID);
    }
}
```

- [ ] **Step 4: Add the minimal entrypoint and metadata.**

```java
package dev.smpb.findmyitems;

public final class FindMyItemsMod {
    public static final String MOD_ID = "findmyitems";
    private FindMyItemsMod() { }
}
```

```java
package dev.smpb.findmyitems;

import net.fabricmc.api.ClientModInitializer;

public final class FindMyItemsClient implements ClientModInitializer {
    @Override public void onInitializeClient() { }
}
```

`fabric.mod.json` must set `environment` to `client`, register only the client entrypoint, and depend on `minecraft: "26.2"`, `fabricloader: ">=0.19.3"`, `fabric-api: "*"`, and `java: ">=25"`.

- [ ] **Step 5: Verify and commit.**

Run: `./gradlew test build`
Expected: `BUILD SUCCESSFUL` and a remapped JAR under `build/libs/`.

Commit: `chore: scaffold findmyitems fabric mod`

### Task 2: Define item, source, and observation values

**Files:**
- Create: `src/main/java/dev/smpb/findmyitems/model/{BlockPosition,ContainerKind,StackKey,StackSnapshot,SlotSnapshot,SourceKey,ContainerObservation}.java`
- Test: `src/test/java/dev/smpb/findmyitems/model/ModelTest.java`

**Interfaces:**
- Produces immutable records used by every later task.

- [ ] **Step 1: Write failing invariant/canonicalization tests.**

```java
@Test void doubleChestPositionsAreCanonical() {
    var a = new BlockPosition(10, 64, 3);
    var b = new BlockPosition(9, 64, 3);
    assertEquals(List.of(b, a), SourceKey.storage("minecraft:overworld", ContainerKind.CHEST, List.of(a, b)).positions());
}

@Test void stackCountMustBePositive() {
    assertThrows(IllegalArgumentException.class,
        () -> new StackSnapshot(new StackKey("minecraft:stone", "{}"), 0, "Stone", List.of()));
}
```

- [ ] **Step 2: Run the tests and confirm missing types fail compilation.**

Run: `./gradlew test --tests '*ModelTest'`
Expected: compilation failure for missing model classes.

- [ ] **Step 3: Implement the records.**

```java
public record BlockPosition(int x, int y, int z) implements Comparable<BlockPosition> {
    @Override public int compareTo(BlockPosition o) {
        int byX = Integer.compare(x, o.x); if (byX != 0) return byX;
        int byY = Integer.compare(y, o.y); return byY != 0 ? byY : Integer.compare(z, o.z);
    }
}

public enum ContainerKind { CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX, ENDER_CHEST }

public record StackKey(String itemId, String componentsJson) {
    public StackKey { Objects.requireNonNull(itemId); Objects.requireNonNull(componentsJson); }
}

public record StackSnapshot(StackKey key, int count, String displayName, List<String> tooltip) {
    public StackSnapshot {
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
        tooltip = List.copyOf(tooltip);
    }
}

public record SlotSnapshot(int slotIndex, StackSnapshot stack) { }

public record SourceKey(String dimension, ContainerKind kind, List<BlockPosition> positions) {
    public SourceKey { positions = positions.stream().sorted().distinct().toList(); }
    public static SourceKey storage(String d, ContainerKind k, List<BlockPosition> p) { return new SourceKey(d, k, p); }
    public static SourceKey enderInventory() { return new SourceKey("findmyitems:all_dimensions", ContainerKind.ENDER_CHEST, List.of()); }
}

public record ContainerObservation(
        SourceKey contentsKey,
        List<SourceKey> accessSources,
        List<SlotSnapshot> slots,
        Instant observedAt) {
    public ContainerObservation {
        accessSources = List.copyOf(accessSources);
        slots = List.copyOf(slots);
    }
}
```

For ordinary storage, `contentsKey` is also the only `accessSources` entry. For an ender chest, `contentsKey` is one world/player logical key with no block position and the observed block is added to `accessSources`; the index replaces the shared contents while retaining distinct access locations.

- [ ] **Step 4: Run focused and full tests, then commit.**

Run: `./gradlew test --tests '*ModelTest' && ./gradlew check`
Expected: all tests pass.

Commit: `feat: define immutable container observations`

### Task 3: Build the searchable container index

**Files:**
- Create: `src/main/java/dev/smpb/findmyitems/index/{ContainerIndex,InMemoryContainerIndex,IndexSnapshot,ItemResult,SourceResult,SearchQuery}.java`
- Test: `src/test/java/dev/smpb/findmyitems/index/InMemoryContainerIndexTest.java`

**Interfaces:**
- Produces:
```java
public interface ContainerIndex {
    long revision();
    void observe(ContainerObservation observation);
    void markMissing(SourceKey source);
    List<ItemResult> search(String query);
    IndexSnapshot snapshot();
    void replace(IndexSnapshot snapshot);
}
```

- [ ] **Step 1: Write failing tests for replacement, component-sensitive aggregation, AND search, tooltip search, sorting, double-source counts, and missing removal.**

```java
@Test void observationReplacesRatherThanMerges() {
    index.observe(observation(source, slot(0, "minecraft:stone", 64)));
    index.observe(observation(source, slot(0, "minecraft:dirt", 3)));
    assertTrue(index.search("stone").isEmpty());
    assertEquals(3, index.search("dirt").getFirst().totalCount());
}

@Test void allTermsMustMatchSearchDocument() {
    index.observe(observation(source, enchantedPickaxe("Efficiency V")));
    assertEquals(1, index.search("diamond efficiency").size());
    assertTrue(index.search("diamond silk").isEmpty());
}
```

- [ ] **Step 2: Verify failure.**

Run: `./gradlew test --tests '*InMemoryContainerIndexTest'`
Expected: missing index classes.

- [ ] **Step 3: Implement normalized query matching and immutable results.**

`SearchQuery.parse` lowercases with `Locale.ROOT`, trims, splits on `\\s+`, removes duplicates, and requires every token to occur in the joined display name, item ID, and tooltip. `InMemoryContainerIndex` replaces observations by `SourceKey`, increments its revision only on mutation, groups by `StackKey`, and returns sources sorted by last-observed descending until reachability is supplied by Task 6.

- [ ] **Step 4: Verify and commit.**

Run: `./gradlew test --tests '*InMemoryContainerIndexTest' && ./gradlew check`
Expected: all tests pass.

Commit: `feat: index and search observed containers`

### Task 4: Persist world indexes atomically

**Files:**
- Create: `src/main/java/dev/smpb/findmyitems/store/{WorldKey,WorldStore,JsonWorldStore,StoreDocument,StoreResult,AtomicFileWriter,SavedCraftRequest}.java`
- Test: `src/test/java/dev/smpb/findmyitems/store/JsonWorldStoreTest.java`

**Interfaces:**
```java
public interface WorldStore {
    StoreResult load(WorldKey key);
    void save(WorldKey key, IndexSnapshot snapshot, List<SavedCraftRequest> requests) throws IOException;
}
public record SavedCraftRequest(StackKey output, int count) {
    public SavedCraftRequest { if (count <= 0) throw new IllegalArgumentException("count must be positive"); }
}
```

- [ ] **Step 1: Write failing temporary-directory tests for round trip, deterministic world digest, corrupt-primary backup recovery, newer-schema refusal, and failed-replace preservation.**

```java
@TempDir Path temp;

@Test void corruptPrimaryRecoversBackup() throws Exception {
    store.save(key, snapshot, List.of());
    store.save(key, newerSnapshot, List.of());
    Files.writeString(store.pathFor(key), "not json");
    assertEquals(snapshot, store.load(key).snapshot());
}
```

- [ ] **Step 2: Verify the failure, then implement schema `1`, Gson DTO conversion, `.tmp` writes, `.bak` rotation, and atomic-move fallback.**

Do not serialize domain records directly. `StoreDocument` owns JSON names; `JsonWorldStore` converts explicitly so schema changes remain local.

- [ ] **Step 3: Verify and commit.**

Run: `./gradlew test --tests '*JsonWorldStoreTest' && ./gradlew check`
Expected: all tests pass and failure tests leave the prior good file readable.

Commit: `feat: persist indexes by world`

### Task 5: Observe supported Minecraft containers

**Files:**
- Create: `src/client/java/dev/smpb/findmyitems/minecraft/{MinecraftWorldAdapter,MinecraftContainerAdapter,MinecraftStackAdapter,PendingInteraction}.java`
- Modify: `src/client/java/dev/smpb/findmyitems/FindMyItemsClient.java`
- Test: `src/test/java/dev/smpb/findmyitems/minecraft/ContainerShapeTest.java`

**Interfaces:**
```java
public interface ContainerObservationSink { void accept(ContainerObservation observation); }
public record ContainerShape(ContainerKind kind, int storageSlots, List<BlockPosition> positions) { }
```

- [ ] **Step 1: Test pure shape rules for 27/54-slot chest, 27-slot barrel/shulker/ender, player-slot exclusion, and utility-screen rejection.**
- [ ] **Step 2: Implement source capture from the player's last normal block interaction and match the next opened supported `AbstractContainerScreen`/menu.**

Resolve a double chest from block state and neighboring half before creating `SourceKey`. Encode each non-empty `ItemStack` with registry ID, data components via Minecraft's codec/registry ops, hover name, tooltip, and live count. For ender chests, persist the shared inventory under a logical ender source and record access positions separately.

- [ ] **Step 3: Register screen-initialization, client-tick, join, disconnect, and world-change callbacks in the composition root. Debounce persistence outside rendering.**
- [ ] **Step 4: Run unit tests and `./gradlew runClient`; use a development world to open every supported shape and confirm snapshots in the JSON file.**

Expected: utility screens create no observations; reopening replaces counts.

Commit: `feat: observe vanilla storage containers`

### Task 6: Add in-container search and catalog reachability

**Files:**
- Create: `src/client/java/dev/smpb/findmyitems/ui/{InventorySearchOverlay,CatalogScreen,CatalogController,CatalogViewModel,ReachabilityView}.java`
- Create: `src/client/java/dev/smpb/findmyitems/minecraft/MinecraftReachabilityAdapter.java`
- Modify: `FindMyItemsClient.java`, `en_us.json`
- Test: `src/test/java/dev/smpb/findmyitems/ui/CatalogControllerTest.java`

**Interfaces:**
```java
public enum Reachability { REACHABLE, DIFFERENT_DIMENSION, CHUNK_UNLOADED, OUT_OF_RANGE, MISSING, OBSTRUCTED, UNVERIFIED }
public interface ReachabilityResolver { Reachability resolve(SourceKey source); }
```

- [ ] **Step 1: Test search focus/clear state, reachable-first sorting, stable selection, and reason labels in `CatalogController`.**
- [ ] **Step 2: Implement `InventorySearchOverlay` with Fabric screen events: an `EditBox`, `Ctrl+F`, first-Escape clear, and draw-time dimming of only nonmatching storage slots. Do not alter slot coordinates or clicks.**
- [ ] **Step 3: Register configurable `K` catalog binding and implement the split screen with item list left and source list right.**
- [ ] **Step 4: Implement reachability checks in the exact order from the spec and show coordinates for unreachable navigation.**
- [ ] **Step 5: Verify with unit tests and manual screen scenarios at GUI scales 1, 2, 3, and Auto.**

Run: `./gradlew test && ./gradlew runClient`
Expected: no draw errors; player inventory remains undimmed.

Commit: `feat: search containers and browse catalog`

### Task 7: Implement the retrieval transaction state machine

**Files:**
- Create: `src/main/java/dev/smpb/findmyitems/retrieval/{RetrievalRequest,QuantityRequest,RetrievalState,RetrievalResult,RetrievalPort,RetrievalTransaction,Validation,LiveContainer,TransferProgress,CancelReason}.java`
- Test: `src/test/java/dev/smpb/findmyitems/retrieval/RetrievalTransactionTest.java`

**Interfaces:**
```java
public sealed interface QuantityRequest permits OneStack, AllMatching, ExactCount { }
public interface RetrievalPort {
    Validation validate(RetrievalRequest request);
    void interact(SourceKey source);
    Optional<LiveContainer> openedContainer();
    TransferProgress transfer(LiveContainer live, StackKey key, QuantityRequest quantity);
    void closeContainer();
}
public final class RetrievalTransaction {
    public RetrievalState state();
    public Optional<RetrievalResult> tick(Instant now);
    public void cancel(CancelReason reason);
}
```

- [ ] **Step 1: Write deterministic fake-clock/fake-port tests for every transition and terminal result, including timeout, changed source, partial, full inventory, cancellation, and second-request rejection.**
- [ ] **Step 2: Implement one-way transitions `REQUESTED → INTERACTING → WAITING_FOR_MENU → REVALIDATING → TRANSFERRING → FINISHED`; make terminal states idempotent.**
- [ ] **Step 3: Verify no test leaves the fake cursor non-empty and commit.**

Run: `./gradlew test --tests '*RetrievalTransactionTest' && ./gradlew check`
Expected: all state paths pass.

Commit: `feat: model safe retrieval transactions`

### Task 8: Adapt retrieval to vanilla container clicks

**Files:**
- Create: `src/client/java/dev/smpb/findmyitems/minecraft/MinecraftRetrievalPort.java`
- Modify: `CatalogController.java`, `CatalogScreen.java`, `FindMyItemsClient.java`
- Test/Doc: `docs/testing/manual-client-scenarios.md`

**Interfaces:**
- Consumes `RetrievalPort`; produces confirmed vanilla interactions and menu clicks.

- [ ] **Step 1: Wire right-click to `OneStack`, Shift-right-click to `AllMatching`, and Shift-scroll to a positive `ExactCount` shown in the tooltip.**
- [ ] **Step 2: Implement interaction, menu matching, live snapshot refresh, and stack re-find by complete `StackKey`.**
- [ ] **Step 3: Implement acknowledged QUICK_MOVE for whole stacks and serialized PICKUP/place-one actions for exact counts, advancing only after menu-revision or slot-state changes. Bound the action count to the request and to one action per client tick.**
- [ ] **Step 4: Restore catalog query/selection and show the exact terminal result.**
- [ ] **Step 5: Execute every retrieval scenario in the manual test document and inspect `run/logs/latest.log`.**

Run: `./gradlew test build` and `rg -n 'ERROR|Exception|findmyitems' run/logs/latest.log`
Expected: build passes; no findmyitems error/exception.

Commit: `feat: retrieve reachable container items`

### Task 9: Build the recursive recipe planner

**Files:**
- Create: `src/main/java/dev/smpb/findmyitems/recipe/{IngredientChoice,RecipeNode,RecipeGraph,Stock,StockLocation,CraftRequest,CraftStage,RecipePlan,RecipePlanner}.java`
- Test: `src/test/java/dev/smpb/findmyitems/recipe/RecipePlannerTest.java`

**Interfaces:**
```java
public interface RecipePlanner {
    RecipePlan plan(RecipeGraph graph, CraftRequest request, Stock player, Stock reachable, Stock unreachable);
}
public record CraftRequest(StackKey output, int count) { }
```

- [ ] **Step 1: Write failing tests for 17 observers (102 cobblestone, 34 redstone, 17 quartz), output rounding, inventory subtraction, existing intermediate use, alternative selection, deterministic tie-break, shared-stock reservation, two-level recursion, compacting-cycle rejection, and unsupported leaves.**
- [ ] **Step 2: Implement recursive planning with a path set, immutable remaining-stock ledger, memoization key, and ranking tuple `(missingLeaves, sourceCount, craftOperations, stableId)`.**
- [ ] **Step 3: Add a 10,000-source performance fixture and assert a representative warmed query/plan stays below the spec's 50 ms target on the development machine; tag it `performance` so CI can report it separately if noisy.**
- [ ] **Step 4: Verify and commit.**

Run: `./gradlew test --tests '*RecipePlannerTest' && ./gradlew check`
Expected: all tests pass with no cyclic recursion.

Commit: `feat: plan recursive crafting materials`

### Task 10: Adapt Minecraft recipes and add craft-request UI

**Files:**
- Create: `src/client/java/dev/smpb/findmyitems/minecraft/MinecraftRecipeAdapter.java`
- Modify: `CatalogController.java`, `CatalogScreen.java`, `FindMyItemsClient.java`, `en_us.json`
- Test: `src/test/java/dev/smpb/findmyitems/recipe/RecipeAdapterContractTest.java`

- [ ] **Step 1: Define adapter contract fixtures proving shaped and shapeless recipes preserve output counts and all ingredient alternatives while unsupported recipe kinds are omitted.**
- [ ] **Step 2: Convert the live recipe manager/display collection into the pure `RecipeGraph` on recipe sync/reload, using registry/component identities from `MinecraftStackAdapter`.**
- [ ] **Step 3: Add `Craft request` mode with output search, numeric quantity, staged plan, surplus, and four stock buckets: inventory, reachable, unreachable, missing.**
- [ ] **Step 4: Persist only request output/count intent and recompute on resume.**
- [ ] **Step 5: Verify 17 observers and a two-stage recipe in the development world.**

Run: `./gradlew test build`
Expected: all tests pass; unsupported recipes appear as leaf requirements.

Commit: `feat: add recursive craft requests`

### Task 11: Coordinate multi-container material retrieval

**Files:**
- Create: `src/main/java/dev/smpb/findmyitems/batch/{MaterialAllocation,BatchState,BatchResult,RetrievalBatch,InventoryCapacity,ChildRetrievalFactory}.java`
- Modify: `CatalogController.java`, `CatalogScreen.java`
- Test: `src/test/java/dev/smpb/findmyitems/batch/RetrievalBatchTest.java`

**Interfaces:**
```java
public interface ChildRetrievalFactory { RetrievalTransaction start(MaterialAllocation allocation); }
public final class RetrievalBatch {
    public BatchState state();
    public Optional<BatchResult> tick(InventoryCapacity capacity, Stock currentStock);
    public void resume(RecipePlan replanned);
}
```

- [ ] **Step 1: Test sequential child execution, changed-source replanning, source failure, capacity pause, manual-crafting resume, world change, recipe reload, and exact fetched/held/unreachable/missing summaries.**
- [ ] **Step 2: Implement one-child-at-a-time coordination; never send slot actions directly from the batch.**
- [ ] **Step 3: Add `Fetch available materials`, progress, pause reason, and `Resume request` UI.**
- [ ] **Step 4: Manually retrieve the 17-observer materials from at least three reachable containers, then test an oversized paused request.**
- [ ] **Step 5: Verify and commit.**

Run: `./gradlew test build`
Expected: all tests pass and material summaries reconcile exactly.

Commit: `feat: fetch crafting materials across containers`

### Task 12: Compatibility, documentation, and release gate

**Files:**
- Modify: `README.md`, `docs/testing/manual-client-scenarios.md`
- Create: `docs/testing/compatibility-matrix.md`
- Modify build files only if verified dependency/profile configuration requires it.

- [ ] **Step 1: Pin current Minecraft 26.2-compatible Sodium, Lithium, FerriteCore, and ScalableLux artifacts in a local test profile without making them runtime dependencies. Record exact versions and download sources.**
- [ ] **Step 2: Run the complete manual matrix in clean Fabric and performance profiles: supported shapes, utility exclusions, search, restart persistence, changed, destroyed, and obstructed sources, full inventory, death/respawn, dimension changes, one/all/exact retrieval, recursive planning, multi-source fetching, capacity pause/resume, and crash-safe store recovery.**
- [ ] **Step 3: Test GUI scales and both OpenGL and experimental Vulkan when the machine supports Vulkan; record `not available on test hardware` rather than claiming a pass if it does not.**
- [ ] **Step 4: Run final automated verification.**

Run: `./gradlew clean check build`
Expected: `BUILD SUCCESSFUL`, all tests green, and distributable `findmyitems-0.1.0.jar`.

Run: `rg -n 'ERROR|Exception' run/logs/latest.log`
Expected: no findmyitems-caused error or exception.

- [ ] **Step 5: Confirm metadata and README advertise only client-side Minecraft 26.2 single-player support and list multiplayer/modded containers as unsupported.**
- [ ] **Step 6: Commit release readiness.**

Commit: `docs: complete findmyitems release verification`
