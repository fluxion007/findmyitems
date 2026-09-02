# Filter Bar Enhancement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add independent survival-inventory and container filter-bar settings, and make live filtering search complete item tooltip text, including enchantments.

**Architecture:** Extend the shared `ModConfig` and existing Cloth Config screen. Classify eligible screens in `InventorySearchController`, then build one normalized search document from display name, item ID, and rendered tooltip lines before dimming non-matches. Keep creative inventory excluded and preserve the single-player boundary.

**Tech Stack:** Java, Fabric API, Minecraft client GUI APIs, Cloth Config, JUnit/Gradle.

## Global Constraints

- Both new settings default to `true`.
- The inventory setting applies only to the survival player inventory/crafting screen.
- The container setting applies to other eligible non-creative container screens.
- Creative inventory is always excluded, even in survival game mode.
- Matching uses display/custom name, registry ID/path, and every rendered tooltip line.
- Runtime settings use the shared `FindMyItemsClient.config()` instance.
- Preserve single-player-only behavior and item-conservation guarantees.

---

### Task 1: Add persisted filter settings

**Files:**
- Modify: `src/main/java/dev/smpb/findmyitems/config/ModConfig.java`
- Modify: `src/client/java/dev/smpb/findmyitems/config/ConfigScreen.java`
- Modify: `src/main/resources/assets/findmyitems/lang/en_us.json`
- Test: `src/test/java/dev/smpb/findmyitems/config/ModConfigTest.java`

**Interfaces:**
- Produces `ModConfig.filterInventory` and `ModConfig.filterContainers`, both public persisted booleans defaulting to `true`.
- The settings screen edits the shared `ModConfig` passed to `ConfigScreen.create` and saves through its existing saving runnable.

- [ ] **Step 1: Add failing config coverage**

Add assertions that a fresh `ModConfig` has both booleans enabled and that JSON round-tripping preserves false values alongside existing settings.

- [ ] **Step 2: Run the focused test**

Run: `./gradlew test --tests '*ModConfigTest'`

Expected: FAIL because the fields do not yet exist.

- [ ] **Step 3: Add fields, UI entries, and translations**

Declare:

```java
public boolean filterInventory = true;
public boolean filterContainers = true;
```

Add two `startBooleanToggle` entries in the general Cloth Config category, with save consumers assigning the corresponding fields. Add concise English labels and tooltips under `screen.findmyitems.config.filter_inventory` and `screen.findmyitems.config.filter_containers`.

- [ ] **Step 4: Run the focused test**

Run: `./gradlew test --tests '*ModConfigTest'`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/smpb/findmyitems/config/ModConfig.java src/client/java/dev/smpb/findmyitems/config/ConfigScreen.java src/main/resources/assets/findmyitems/lang/en_us.json src/test/java/dev/smpb/findmyitems/config/ModConfigTest.java
git commit -m "feat: add filter bar visibility settings"
```

### Task 2: Make the filter controller setting-aware

**Files:**
- Modify: `src/client/java/dev/smpb/findmyitems/search/InventorySearchController.java`

**Interfaces:**
- Consumes `FindMyItemsClient.config().filterInventory` and `.filterContainers`.
- Produces no widget and no overlay when the relevant setting is disabled.

- [ ] **Step 1: Add screen classification tests or test seams**

Add a package-private screen-classification method if the existing private `onAfterInit` method cannot be exercised directly. Cover these cases: survival player inventory maps to the inventory setting; chest/container screens map to the container setting; creative inventory is rejected before either setting is read.

- [ ] **Step 2: Run the focused test**

Run: `./gradlew test --tests '*InventorySearchController*'`

Expected: FAIL until classification consults configuration.

- [ ] **Step 3: Implement the minimal classification guard**

Keep the existing single-player and `CreativeModeInventoryScreen` guards. Identify the player inventory screen using the vanilla inventory menu/screen type used by this Minecraft version. Return early when `filterInventory` is false for that screen or `filterContainers` is false for every other eligible container screen.

- [ ] **Step 4: Run verification**

Run: `./gradlew test --tests '*InventorySearchController*'`

Expected: PASS, or if no isolated client unit harness exists, verify compilation with `./gradlew compileClientJava` and cover behavior in the client game test task.

- [ ] **Step 5: Commit**

```bash
git add src/client/java/dev/smpb/findmyitems/search/InventorySearchController.java
git commit -m "feat: gate filter bar by screen settings"
```

### Task 3: Search complete tooltip text

**Files:**
- Modify: `src/client/java/dev/smpb/findmyitems/search/InventorySearchController.java`
- Test: Add or extend the nearest existing client search test fixture under `src/client` or `src/gametest` according to the repository’s available harness.

**Interfaces:**
- `matches(ItemStack stack, String needle)` continues returning a boolean but searches normalized display name, registry ID/path, and all tooltip component lines.

- [ ] **Step 1: Add failing matching coverage**

Create an enchanted book or enchanted item and assert that matching succeeds for the enchantment name and the level text shown in its tooltip, while an unrelated enchantment or level does not match. Retain assertions for display name and item ID/path matching.

- [ ] **Step 2: Run the focused test**

Run `./gradlew runGameTest` after adding the server-side search fixture, or run the focused JUnit test if the test remains a pure utility test. Expected: FAIL because `matches` currently reads only hover name and item ID.

- [ ] **Step 3: Build the searchable tooltip document**

Use the client tooltip API for the current screen context and collect each tooltip line’s plain string. Lowercase with `Locale.ROOT`, append the display name and registry path, and check the query against the combined text. Keep query trimming and empty-query behavior unchanged. Do not serialize components or introduce a `{}` fallback.

- [ ] **Step 4: Run verification**

Run: `./gradlew build` and `./gradlew runGameTest`.

Expected: both complete successfully; the enchantment test demonstrates that books with identical display names can be distinguished by tooltip attributes.

- [ ] **Step 5: Commit**

```bash
git add src/client/java/dev/smpb/findmyitems/search/InventorySearchController.java src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsGameTest.java
git commit -m "feat: search item tooltips in container filter"
```

### Task 4: Final verification and issue closure

**Files:**
- No additional source files unless verification exposes a defect.

- [ ] **Step 1: Run the required checks**

Run: `./gradlew build` and `./gradlew runGameTest`.

- [ ] **Step 2: Inspect the final diff**

Run: `git status --short`, `git diff main...HEAD --check`, and `git log --oneline main..HEAD`. Confirm only the spec, plan, config, UI, translations, tests, and filter implementation changed.

- [ ] **Step 3: Close resolved issues**

Use the repository’s GitHub issue tracker to close issues 2 and 3 and the resolved take-tooltip issue, each with a concise comment identifying the shipped fix. Do not close unrelated issues.

- [ ] **Step 4: Commit any verification-only fixes**

```bash
git add src/main/java src/client/java src/main/resources src/test/java src/gametest/java
git commit -m "test: verify filter bar enhancement"
```
