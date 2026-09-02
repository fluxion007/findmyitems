# Default Crafting Amount and 0.1.3 Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a newly opened catalog request default to one item and publish the change as `0.1.3` to GitHub and Modrinth.

**Architecture:** Keep the amount state local to `CatalogScreen`; change only its initial value from `64` to `1`. Extend the existing client game test to inspect the amount edit box after opening the real catalog, then update release metadata and use the repository's existing Gradle publishing workflow.

**Tech Stack:** Java 25, Fabric client game tests, Gradle Loom, GitHub Actions, Modrinth publish plugin.

## Global Constraints

- Nothing is destroyed: item movement must conserve items, including full inventories and creative mode.
- Single-player only: do not add multiplayer behavior.
- Item identity remains the item id plus registry-backed component key.
- `0.1.3` must be the value of `mod_version` and the `v0.1.3` tag.
- `MODRINTH_TOKEN` must remain environment-only and must not be written to files or command output.

---

### Task 1: Change and Test the Catalog Default

**Files:**
- Modify: `src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java:111-115`
- Modify: `src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsClientGameTest.java:27-30, 86-90`

**Interfaces:**
- Consumes: the existing `CatalogScreen` constructor and amount edit box.
- Produces: a newly constructed catalog whose amount field is initialized to `1`, with a real client game-test assertion.

- [ ] **Step 1: Add the failing client game-test assertion**

Import `net.minecraft.client.gui.components.EditBox` in `FindMyItemsClientGameTest.java`. Immediately after `openCatalog(context);` in the first catalog flow, add:

```java
assertDefaultCatalogAmount(context);
```

Add this helper before `openCatalog`:

```java
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
```

The search box is the first `EditBox` and the amount box is the second, matching the initialization order in `CatalogScreen.init()`.

- [ ] **Step 2: Run the focused client game test and verify it fails**

Run: `./gradlew runClientGameTest`

Expected: the client game test reaches the new assertion and fails because the current default is `64`. Do not claim that the change works based on this command; the client test opens a real window and may require the user's display session.

- [ ] **Step 3: Change the minimal implementation**

In `CatalogScreen`, change only the field initializer:

```java
private int amount = 1;
```

Do not change `onAmountTyped`, `setAmount`, the crafting planner, retrieval handlers, or the amount cap.

- [ ] **Step 4: Run the focused client game test again**

Run: `./gradlew runClientGameTest`

Expected: the client game test passes, including `assertDefaultCatalogAmount`, with no regression in the existing catalog, crafting, retrieval, or highlighting flow.

- [ ] **Step 5: Commit the implementation and test**

```bash
git add src/client/java/dev/smpb/findmyitems/gui/CatalogScreen.java src/gametest/java/dev/smpb/findmyitems/test/FindMyItemsClientGameTest.java
git commit -m "fix: default catalog amount to one"
```

### Task 2: Prepare and Verify the 0.1.3 Release

**Files:**
- Modify: `gradle.properties:10`
- Modify: `CHANGELOG.md:5`

**Interfaces:**
- Consumes: the tested default-amount implementation from Task 1.
- Produces: release metadata accepted by Gradle's changelog validation and the tag-matching workflow.

- [ ] **Step 1: Add the release changelog section and bump the patch version**

Change `mod_version=0.1.2` to `mod_version=0.1.3`. Insert this section immediately after `## Unreleased`:

```markdown
## 0.1.3 — 2026-08-02

### Changed

- The catalog's default item and crafting amount is now 1 instead of 64. You can still enter a different amount.
```

- [ ] **Step 2: Run the complete verification suite**

Run: `./gradlew build`

Expected: Java compilation and JUnit tests pass.

Run: `./gradlew runGameTest`

Expected: headless server game tests pass.

Run: `./gradlew publishMods -PdryRun`

Expected: Gradle reports a dry-run Modrinth publication for version `0.1.3`, uses the `0.1.3` changelog section, and uploads nothing.

- [ ] **Step 3: Inspect the release diff and commit it**

Run:

```bash
git diff --check
git status --short
```

Confirm only the intended release metadata changed, then commit:

```bash
git add gradle.properties CHANGELOG.md
git commit -m "chore: prepare 0.1.3 release"
```

### Task 3: Push and Publish the Release

**Files:**
- No source files; publish the commits and tag produced by Tasks 1 and 2.

**Interfaces:**
- Consumes: local `main` commits, verified `0.1.3` jar, and environment variable `MODRINTH_TOKEN`.
- Produces: pushed `main`, pushed `v0.1.3`, a published GitHub release, and a published Modrinth `0.1.3` version.

- [ ] **Step 1: Confirm the worktree and recent commits before pushing**

Run:

```bash
git status --short --branch
```

Expected: clean worktree, intended commits visible, and no unrelated files staged.

- [ ] **Step 2: Push `main` directly**

Run: `git push origin main`

Expected: `origin/main` contains the implementation, test, release metadata, and design/spec commits.

- [ ] **Step 3: Create and push the release tag**

Run: `git tag -a v0.1.3 -m "Release 0.1.3"` followed by `git push origin v0.1.3`.

Expected: the release workflow starts for `v0.1.3`.

- [ ] **Step 4: Wait for the GitHub release workflow and publish the generated release**

Use `gh run list --workflow release.yml --limit 5` and `gh run watch <run-id>` until the build, headless game tests, tag/version check, and draft release creation succeed. Then inspect with `gh release view v0.1.3`; publish the draft with:

```bash
gh release edit v0.1.3 --draft=false
```

Expected: GitHub reports a published `v0.1.3` release with the built jar attached.

- [ ] **Step 5: Publish the verified jar to Modrinth**

Run: `MODRINTH_TOKEN="$MODRINTH_TOKEN" ./gradlew publishMods`

Expected: the authenticated task succeeds and Modrinth project `mPTBl3Fj` reports version `0.1.3`.

- [ ] **Step 6: Verify both remote publications and final repository state**

Run:

```bash
gh release view v0.1.3
git status --short --branch
```

Use the Modrinth project/version page or API result to confirm `0.1.3`. Expected: GitHub and Modrinth both show the release, and the local branch is clean and synchronized with `origin/main`.
