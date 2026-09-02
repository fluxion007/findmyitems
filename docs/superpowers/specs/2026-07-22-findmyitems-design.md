# findmyitems Design Specification

**Status:** Approved design, awaiting written-spec review

**Date:** 2026-07-22

**Public name and mod ID:** `findmyitems`

**Initial target:** Minecraft Java Edition 26.2 on Fabric

## 1. Product Summary

`findmyitems` is a client-side Fabric mod that remembers the contents of storage containers the player has legitimately opened. A player can search those memories in a single catalog, see which source containers are currently reachable, and retrieve items from a reachable container through Minecraft's normal container interaction and slot-click behavior.

The supported product is centered on single-player survival worlds. Multiplayer may work on a best-effort basis because the same vanilla interactions are used, but multiplayer is undocumented, untested, and not part of the release guarantee. The mod never scans unopened containers, reads server-only state, creates items, bypasses distance checks, or transfers items across dimensions.

## 2. Goals

The first release must:

1. Remember exact snapshots of supported storage containers when the player opens them.
2. Persist those memories separately for each single-player world.
3. Add a search field to supported open-container screens.
4. Provide a global searchable catalog of remembered items and their source containers.
5. Visually distinguish sources predicted to be reachable from sources that are not.
6. Retrieve an item only after opening and revalidating the real source container through normal Minecraft interaction.
7. Support one-stack, all-matching, and exact-count retrieval controls.
8. Coexist with the agreed performance-mod test profile.
9. Plan a requested quantity of a craftable item recursively and retrieve its available materials from reachable containers without automating crafting.

## 3. Non-Goals

The first release will not provide:

- Advertised or tested multiplayer support.
- A server-side companion mod or custom networking protocol.
- Background chunk, block-entity, save-file, or packet scanning.
- Retrieval from unloaded, obstructed, out-of-range, or wrong-dimension containers.
- Generic modded-container support.
- Furnace, smoker, blast-furnace, brewing-stand, crafting, merchant, or other utility-screen indexing.
- Map integration, waypoints, container naming, collaborative indexes, or cloud sync.
- Automatic crafting.
- Automatic smelting, blasting, smoking, stonecutting, smithing, brewing, or special dynamic-recipe processing.
- Minecraft versions other than 26.2.
- A universal JAR spanning multiple Minecraft versions.

## 4. Supported Environment

The initial development baseline is:

- Minecraft Java Edition 26.2.
- Java 25.
- Gradle 9.5.1.
- Fabric Loom 1.17-SNAPSHOT.
- Fabric Loader 0.19.3.
- Fabric API 0.154.2+26.2.
- Mojang's unobfuscated names, as used by Minecraft 26.2 and current Fabric documentation.

The release compatibility profile contains Fabric API plus current 26.2-compatible releases of Sodium, Lithium, FerriteCore, and ScalableLux. Those mod versions are pinned in the development test profile when implementation begins. Compatibility with every inventory, screen-replacement, rendering, or performance mod is not promised.

## 5. Supported Containers

The MVP explicitly supports placed vanilla:

- Chests.
- Trapped chests.
- Barrels.
- Shulker boxes of every color.
- Ender chests.

Single and double chests are treated correctly. The two halves of a double chest form one logical source with one 54-slot snapshot and a canonical location key. A placed shulker box is indexed only when its screen is opened; shulker-box items inside another inventory are not recursively indexed.

Ender chest contents are player-owned and shared by every ender chest. The index therefore stores one ender inventory snapshot per world and player, plus a set of observed ender-chest block positions that can serve as access points.

The implementation may internally recognize a large-storage shape for future adapters, but generic or modded containers are not exposed as supported until they have explicit adapters and tests.

## 6. Observation Rules

Knowledge enters the index only when a supported container screen has actually opened and supplied its live slots to the client. An observation includes:

- World identity.
- Dimension resource key.
- Canonical block position or double-chest position pair.
- Vanilla container kind.
- Slot order and total storage-slot count.
- Each non-empty stack's item identifier, data components, count, localized display name, and searchable tooltip text.
- Observation time.

Opening a previously remembered container replaces its snapshot; it does not merge old and new counts. If the client observes that a remembered, loaded position is no longer the expected container, the source is marked missing and removed after the observation is confirmed on the client thread. Unloaded positions remain stale memories rather than being treated as missing.

Player-inventory slots displayed below a container are never recorded as part of that container.

## 7. Persistence and World Identity

All data is local to the Minecraft instance. Files live under:

`config/findmyitems/worlds/<world-key>.json`

For supported single-player use, `<world-key>` is a filesystem-safe stable digest derived from the local save identity and player UUID. The stored document includes a human-readable world label for the UI, a schema version, the player UUID, and all container records.

Best-effort multiplayer operation may derive a separate key from the normalized server address and player UUID, but it receives no migration, reset-detection, compatibility, or release guarantee. A client-only mod cannot reliably discover that a remote server silently replaced its world; the catalog therefore includes a destructive action labeled `Clear remembered containers for this world/server`, protected by a confirmation dialog.

Writes are debounced after observations and retrieval updates. A save is encoded completely to a temporary sibling file, flushed, and atomically replaces the prior file when the platform supports atomic replacement. A retained prior-good backup is used if parsing the current file fails. Unknown future schema versions are not overwritten; the mod reports that the index was created by a newer version.

## 8. Item Identity and Search

A stack identity is its Minecraft item identifier plus persistent data components. Count, localized display name, and generated tooltip text are searchable/display fields but are not identity fields. This keeps enchanted, named, damaged, dyed, or otherwise component-distinct stacks separate.

Search is case-insensitive and locale-stable. Whitespace-separated query terms use AND semantics. Each term may match:

- Localized display name.
- Namespaced item identifier.
- Searchable tooltip text, including enchantment names when present.

Search results update while typing. An empty query shows every remembered item. Results aggregate equal stack identities across sources while retaining each individual source and count.

## 9. In-Container Search

Every supported open-container screen receives a search field above the container layout. `Ctrl+F` focuses it. `Escape` clears a non-empty query first; a subsequent `Escape` follows normal screen-closing behavior.

Searching does not move, reorder, hide, or change hitboxes. Nonmatching storage slots and their contents are visually dimmed; matching storage slots remain fully colored. Player-inventory slots are unaffected. Slot clicks remain vanilla clicks, even on dimmed slots.

The overlay uses Minecraft/Fabric GUI drawing paths and screen events. Raw OpenGL or Vulkan calls are forbidden. Mixins are used only if Fabric screen events cannot expose a required lifecycle point, and each mixin targets the narrowest stable method available.

## 10. Global Catalog

The default configurable key `K` opens the catalog. All bindings are placed in a `findmyitems` key-binding category. `Ctrl+F` focuses catalog search.

The screen uses a split layout:

- The left side lists matching aggregate item stacks with icon, name, total remembered count, and exact-count selection.
- The right side lists source containers for the selected stack with dimension, coordinates, remembered count, container type, reachability state, and last-observed time.

Sources sort by predicted reachable state, then distance, then most recently observed. Fully colored rows are predicted reachable. Dimmed rows carry a reason such as `different dimension`, `chunk not loaded`, `out of range`, `container missing`, `obstructed`, or `not currently verified`. A stale source remains searchable but never presents its old snapshot as live truth.

Selecting an unreachable source shows its dimension and exact coordinates for manual navigation. The MVP does not render a waypoint or send interaction or inventory packets for that source.

## 11. Reachability

Reachability shown in the catalog is a client prediction, not an authorization decision. A source is predicted reachable only when:

1. The player is in the same dimension.
2. Its chunk and block state are loaded.
3. The block still has the expected supported container kind.
4. The position is within the client's current block interaction range.
5. A vanilla-style interaction ray/path check does not identify an obvious obstruction.

The integrated server remains authoritative. A fully colored row may still fail if state changes between rendering and interaction. Every such failure becomes a normal retrieval result, never a forced transfer.

## 12. Retrieval Controls

When a reachable source row is selected:

- Right-click requests one whole matching stack.
- Shift-right-click requests all matching stacks from that source until the player inventory is full.
- Shift-scroll adjusts an exact requested count displayed in the selected item's tooltip. The next right-click requests that count.

Exact count is clamped to the currently remembered source count and a positive integer. Live revalidation may reduce the fulfillable count. A partial transfer is reported explicitly.

The mod performs only ordinary container interaction and screen-handler slot actions available to the client. It does not mutate an inventory directly.

## 13. Retrieval Transaction

Only one retrieval transaction may exist at a time. The deep module interface accepts a source, stack identity, and quantity request and emits status events plus one terminal result. Its implementation follows this state machine:

1. Validate the current client context and predicted reachability.
2. Close or suspend the catalog without losing its query and selection.
3. Ask Minecraft to interact with the source block using the normal interaction path.
4. Wait for the real container screen handler with a short bounded timeout.
5. Verify that the handler corresponds to the expected supported container shape.
6. Read the live storage slots and replace the remembered snapshot.
7. Re-find slots using full stack identity rather than remembered slot numbers.
8. Execute acknowledged vanilla slot actions for one-stack, all-matching, or exact-count transfer.
9. Stop when the request is satisfied, the player inventory is full, no matching source remains, or an acknowledgement/state check fails.
10. Refresh the snapshot, restore the catalog state, and report the terminal result.

Exact-count transfers may require a bounded sequence of vanilla pickup/place-one actions because the vanilla click interface does not provide an arbitrary-count transfer primitive. Actions are serialized against screen-handler revisions; the implementation never sends an unbounded burst.

Transactions cancel safely on player movement out of range, death, respawn, dimension change, disconnect, screen-handler replacement, source removal, timeout, rejected interaction, changed item components, full inventory, or a second retrieval request. Cancellation leaves all server-confirmed slot actions intact and does not attempt speculative rollback.

Terminal results are: `completed`, `partially completed`, `source changed`, `source unavailable`, `out of range`, `inventory full`, `interaction rejected`, `timed out`, `cancelled`, and `unsupported screen`.

## 14. Recursive Crafting Requests

The catalog includes a `Craft request` mode. The player selects an output item and requested quantity. The UI reports:

- Exact recipe batches required.
- Materials already present in player inventory.
- Materials projected in reachable remembered storage.
- Materials remembered only in unreachable storage.
- Materials not found.
- Intermediate crafting stages in dependency order.
- Expected surplus caused by recipe output batch sizes.

The MVP recipe graph contains loaded shaped and shapeless crafting recipes. Smelting, blasting, smoking, stonecutting, smithing, brewing, and special dynamic recipes are not expanded. When an unsupported process output is required, that output is a leaf material that must already be in inventory or storage.

Planning is recursive, but crafting is always manual. Existing player inventory is allocated first. Existing intermediate items in reachable projected storage are allocated second. Only the remaining intermediate shortage is expanded through a crafting recipe. Recipe output counts are respected, so a recipe that produces four items is planned as a whole batch even when only three are consumed by the parent stage.

When an ingredient accepts alternatives or an output has multiple supported recipes, selection is automatic. Candidate plans are ranked by:

1. Fewest missing leaf materials.
2. Fewest source containers required for projected retrieval.
3. Fewest manual crafting operations.
4. Stable item-identifier ordering as a deterministic tie-breaker.

The planner tracks its current dependency path and rejects cyclic expansion, including reversible compacting recipes such as ingots, blocks, and nuggets. Stock allocation is global to one plan: the same remembered stack cannot satisfy two branches.

The player's inventory and remembered snapshots are planning inputs rather than guarantees. Before retrieval begins, the UI labels the result `projected`. Stale or unreachable stock may explain where materials were seen, but only reachable allocations are eligible for fetching.

## 15. Multi-Container Material Retrieval

`Fetch available materials` converts a projected recipe plan into allocations of stack identities and counts to reachable sources. A `RetrievalBatch` executes one normal `RetrievalTransaction` at a time. Before each source it revalidates reachability; after each source it incorporates the live observation and recomputes the unfinished request if contents changed.

If every allocated material fits in the player inventory, the batch gathers the complete available request. If it does not fit, the mod calculates a bounded batch that fits without dropping or overwriting items. It then pauses and tells the player which manual crafting stage can consume materials or how much inventory space is still required. `Resume request` replans from the current inventory and current index.

The batch stops safely on the same cancellation conditions as a single retrieval, plus a recipe reload, a changed world key, or an unrecoverable child-transaction result. Completed child transfers remain completed. The summary distinguishes fetched, already held, no longer available, unreachable, and missing counts.

Crafting requests are persisted within the current world document as intent: requested output identity and requested quantity. Selected recipes, derived allocations, and availability are not persisted. On resume, the plan is recomputed from the current recipe graph, player inventory, and container index.

## 16. Module Design

### `ContainerIndex`

This module accepts observations and answers immutable catalog queries. Its implementation owns canonical source keys, double-chest handling, ender-chest sharing, aggregation, staleness, search normalization, and missing-source transitions. Callers do not manipulate its maps or persistence representation.

### `WorldStore`

This module loads and saves a complete index document for a world key. Its implementation owns schema versions, codecs, debouncing, atomic replacement, backup recovery, and error reporting. Callers know only world key, load result, and save snapshot.

### `MinecraftContainerAdapter`

This is the version-sensitive seam. It converts current Minecraft screens and handlers into observations, resolves source locations, predicts reachability, initiates vanilla interaction, and exposes acknowledged slot actions to retrieval. Future Minecraft ports should primarily replace this adapter rather than alter domain modules.

### `RetrievalTransaction`

This module hides the asynchronous state machine behind one request interface and a stream of statuses. It depends on the Minecraft adapter and returns outcomes; UI callers do not know packet, revision, or slot-action details.

### `RecipePlanner`

This pure module accepts an immutable crafting-recipe graph, a requested output and quantity, player stock, and projected container stock. It returns an immutable staged plan with allocations, shortages, surplus, and selected recipes. Its interface contains no Minecraft screen, registry, packet, or mutable inventory types.

### `MinecraftRecipeAdapter`

This version-sensitive adapter converts loaded Minecraft shaped and shapeless recipes, ingredient alternatives, and output counts into the planner's recipe graph. Unsupported recipe kinds are omitted explicitly rather than partially interpreted.

### `RetrievalBatch`

This module coordinates allocations across source containers by invoking one `RetrievalTransaction` at a time. It owns progress, inventory-capacity batching, replan triggers, pause/resume behavior, and the terminal material summary. It does not send slot actions itself.

### `InventorySearchOverlay` and `CatalogScreen`

These UI modules render view models and turn input into queries, crafting requests, or retrieval requests. They do not parse persistence files, infer double chests, expand recipe graphs, send low-level slot actions, or own transaction state.

## 17. Error Handling and User Feedback

Persistence errors do not crash or block Minecraft startup. The mod retains the last good in-memory state, reports a concise toast/chat error once, and logs the actionable exception with the affected path.

Retrieval failures appear in the catalog near the selected source. Expected races such as changed contents are informational, not logged as exceptions. Programming faults and impossible state transitions are logged with source key and transaction state but without dumping unrelated world data.

Crafting plans never represent remembered counts as confirmed stock. A plan that cannot be fully satisfied remains useful: it shows exact missing leaf materials and offers retrieval only for currently eligible allocations. Recipe cycles and unsupported recipe kinds appear as explicit leaf or unsupported states rather than generic failures.

The clear-memory action requires confirmation and affects only the current world/server key. It never deletes the entire `findmyitems` directory.

## 18. Performance and Compatibility Constraints

- Catalog searching and aggregation run against the local index only and never block on disk.
- Disk writes occur off the render path; Minecraft state is captured on the client thread before asynchronous encoding.
- Query results are cached by index revision and normalized query.
- Recipe plans are memoized by recipe-graph revision, inventory revision, index revision, output identity, and quantity; computation runs off the render path against immutable snapshots.
- Recursive planning uses cycle guards and bounded candidate exploration so alternative-rich recipes cannot freeze the client.
- After fixture loading and one warm-up query, a benchmark with 10,000 remembered containers must complete each representative search in under 50 milliseconds on the development machine.
- Rendering uses official GUI abstractions so both OpenGL and the experimental Vulkan backend remain viable.
- The mod does not target Sodium, Lithium, FerriteCore, or ScalableLux classes with mixins.
- No optional performance mod is a runtime dependency.

## 19. Testing Strategy

Pure JUnit tests cover:

- World and source key stability.
- Double-chest canonicalization.
- Ender inventory/location separation.
- Observation replacement and missing-source behavior.
- Component-sensitive stack identity.
- Query normalization, AND matching, aggregation, sorting, and staleness.
- Persistence round trips, schema rejection, corrupt-primary recovery, and atomic-write failure behavior.
- Reachability decision inputs through a fake adapter.
- Every retrieval state transition, cancellation reason, partial result, and timeout through a deterministic fake adapter.
- Recipe batch rounding and expected surplus.
- Existing-inventory and existing-intermediate allocation before recursive expansion.
- Automatic ingredient/recipe selection and deterministic tie-breaking.
- Global stock reservation across recipe branches.
- Cycle rejection for reversible recipes.
- Unsupported-process leaf behavior.
- Complete, partial, inventory-limited, changed-source, paused, persisted-intent, and resumed retrieval batches.

Fabric client integration tests or repeatable development-world scenarios cover:

- Every supported vanilla container shape.
- Correct exclusion of player inventory and utility screens.
- Search focus, clearing, dimming, tooltips, and unchanged slot hitboxes.
- Save, quit, reload, and crash-during-write recovery.
- One-stack, all-matching, and exact-count retrieval.
- A 17-observer crafting request requiring 102 cobblestone, 34 redstone dust, and 17 nether quartz before inventory subtraction, including retrieval from multiple reachable sources.
- A recursive request containing at least two intermediate crafting stages, alternative ingredients, existing intermediates, and recipe-batch surplus.
- An oversized request that pauses safely when inventory capacity is exhausted and resumes after manual crafting frees space.
- Changed contents, moved/destroyed containers, obstruction, full inventory, death, respawn, dimension change, and rapid repeated input.
- A vanilla Fabric profile and the pinned Sodium/Lithium/FerriteCore/ScalableLux profile.
- Clean `latest.log` after the full scenario pass.

## 20. Delivery Milestones

1. **Project and durable index:** Fabric scaffold, domain records, world lifecycle, persistence, and unit-test foundation.
2. **Observation:** supported vanilla adapters, snapshot refresh, double chests, ender chests, and missing-source handling.
3. **In-container search:** overlay, query focus, tooltip matching, and nonmatching-slot dimming.
4. **Read-only catalog:** split view, aggregation, source detail, reachability, staleness, navigation, and clearing.
5. **Retrieval:** one-stack first, all-matching second, exact-count third, with transaction tests at each increment.
6. **Recursive recipe planning:** recipe adapter, graph, stock allocation, automatic alternatives, cycle protection, staged plan UI, and saved request intent.
7. **Multi-container material retrieval:** allocation execution, changed-source replanning, capacity-limited batches, pause/resume, and result summaries.
8. **Compatibility and release hardening:** failure scenarios, performance fixtures, pinned performance-mod profile, documentation, and release checklist.

Each milestone must build, pass its focused tests, and be independently reviewable before the next begins.

## 21. Acceptance Criteria

The MVP is complete when all of the following are true:

1. In a Minecraft 26.2 single-player world, opening every supported vanilla container records the correct storage slots and persists them across restart.
2. Searching inside a supported container dims only nonmatching container slots and never changes slot interaction behavior.
3. The global catalog finds remembered items by name, identifier, and tooltip, and shows accurate source, count, location, and last-seen data.
4. Reachable and unreachable sources are visually distinct with an understandable reason.
5. One-stack, all-matching, and exact-count requests revalidate the live container and transfer no more than the server confirms.
6. Every cancellation path ends without a crash, stuck cursor stack, unbounded packet loop, or corrupted index.
7. The clean Fabric profile and pinned performance-mod profile pass the documented scenario suite with no findmyitems errors in `latest.log`.
8. The distribution metadata describes single-player 26.2 support and does not advertise multiplayer or modded-container support.
9. A request for 17 observers reports 102 cobblestone, 34 redstone dust, and 17 nether quartz before inventory subtraction, subtracts matching inventory stock once, and retrieves the remaining available counts across multiple reachable containers.
10. A recursive request reuses available intermediate items, expands only their shortage, selects ingredient alternatives automatically, prevents recipe cycles, and presents manual crafting stages in dependency order.
11. An oversized or partially unavailable request pauses or completes partially with exact remaining requirements; it never drops items, overwrites inventory, or claims stale memory as confirmed stock.

## 22. Primary References

- [Minecraft Java Edition 26.2 release](https://www.minecraft.net/it-it/article/minecraft-java-edition-26-2)
- [Fabric for Minecraft 26.2](https://www.fabricmc.net/2026/06/15/262.html)
- [Fabric development version listing](https://fabricmc.net/develop/)
- [Fabric project creation documentation](https://docs.fabricmc.net/develop/getting-started/creating-a-project)
