# Changelog

Notable changes to Find My Items (`findmyitems`). Versions follow the `MAJOR.MINOR.PATCH` form, and each released version has a matching `vVERSION` Git tag.

## Unreleased

## 0.1.4 — 2026-08-04

### Added

- The Crafting view searches for craftable roots correctly, including word-aware fuzzy matching, and browses large result sets with lazy loading and virtualization.
- Crafting plans choose recipe alternatives, reuse shared stock, account for batch surplus, and terminate safely when recipes contain cycles.
- Gathering supports both gather-only and gather-and-craft actions, with cancellation and recovery from stale indexed sources.

### Fixed

- Reachability now follows vanilla-valid interaction range, loaded-chunk checks, and line-of-sight obstruction rules.
- Inventory capacity and item movement remain safe when gathering or crafting cannot complete; no items are created or destroyed.
- Crafting titles, action labels, status messages, selection changes, and scrolling behavior are consistent in the catalog UI.

### Known limitations

- Only crafting-table recipes and player-inventory recipes are supported. Furnaces, stonecutters, smithing, and other processing stations are not supported.
- The documented manual crafting-planner fixture pass remains a release blocker and has not been manually verified for this candidate.
- Multiplayer is intentionally unsupported; the mod remains single-player only.

## 0.1.3 — 2026-08-02

### Changed

- The catalog's default item and crafting amount is now 1 instead of 64. You can still enter a different amount.

### Fixed

- An ender chest stays indexed as reachable while its block is standing. A re-scan read the block entity, which holds only the lid — an ender chest's items live on the player — and took that for a missing chest, so simply standing nearby long enough left the contents counted in the item total while the container count, the coordinates and the take amount all described a different chest.
- An item's total equals what its row can account for. Stock in a container with no way in is listed as its own quantity and labelled out of reach, rather than being folded into a total nothing else on the row explains.
- The take button's tooltip names what limited it: the container's stock, the room left in your inventory, or stock that is out of reach.

- An item held in a double chest is described as one container rather than two. Both halves are still offered as ways in, so the nearer one is still the one a retrieval uses.

### Added

- The ender inventory is indexed with no ender chest placed, on the new `indexEnderInventory` setting. It is player data, so reading it needs no block and no loaded chunk.
- Retrieval reach is configurable, up to 256 blocks, on the new `retrieveDistanceBlocks` setting. It defaults to your own reach, never shortens it, and still refuses a container whose chunk is not loaded.
- The grid layout has a detail pane. Hovering a cell lists every container holding that item and how many are in each, marking the ones that cannot be taken from and why: too far away, in another dimension, or an ender inventory with no ender chest in reach.

## 0.1.2 — 2026-07-31

Release packaging now includes local Modrinth publishing with changelog validation.

## 0.1.1 — 2026-07-25

Bug fixes.

### Fixed

- Items that differ only by their components are keyed against the world's registries, so enchanted variants are listed and retrieved separately. Previously a component patch that could not be encoded fell back to the key of a stack with no components, which listed a plain, a Smite IV, and a Sharpness V diamond sword as one entry and retrieved all three at once.
- Retrieval is refused when the inventory has no room for the item, rather than opening the container and moving nothing. Room is counted per item, so a partly filled stack of the same item still accepts a retrieval.
- The take button states the amount the click will move, and marks it `(max)` when that is fewer than requested.
- An open catalog refreshes when the index changes, instead of only when the search text changes.
- Settings take effect immediately. The settings screen previously edited a separate copy of the configuration, so changes applied only after a restart.
- The list and grid choice is retained across view switches, reopening the catalog, and restarts.

### Changed

- Search distance is configured in chunks rather than blocks. The default of 4 chunks matches the previous default of 64 blocks.

## 0.1.0 — 2026-07-24

First release. Single-player worlds only; the mod disables itself on multiplayer servers.

### Catalog

- Added a catalog screen, opened with `B`, containing three views: Items, Containers, and Crafting. `Ctrl+1`, `Ctrl+2`, and `Ctrl+3` select a view; `Cmd` replaces `Ctrl` on macOS.
- Items and Containers can be displayed as a list or as a grid.
- Search matches item names, item ids, and tooltip text. Enchantment levels match in both Arabic and Roman notation, for example `smite 4` and `smite iv`.
- Items that differ only by their components are listed as separate entries.

### Retrieval

- Added retrieval and deposit of items from containers within normal block interaction range.
- Retrieval opens the container on the server with a normal interaction packet, so the lid animation and sound occur, and closes it afterwards. The container's own screen is not displayed.
- Deposit is offered only for items the target container already holds.
- Items inside shulker boxes placed in containers are indexed and retrievable, up to four levels of nesting.

### Highlighting

- Added a container highlight, drawn as an outline around the container's own shape for five seconds. The outline is visible through blocks.

### Crafting

- Added a crafting view that produces a material tree for an item and subtracts what indexed containers already hold. Covered and missing materials are distinguished.
- The item list offered when the search field is empty contains only items that have a crafting recipe.

### Container screens

- Added a filter field to ordinary container screens. Typing dims slots that do not match. No items are moved.

### Storage

- The index is stored at `config/findmyitems/worlds/ID.json`, keyed by save directory and player UUID, so two saves with the same displayed name do not share an index.
- Settings are stored at `config/findmyitems.json`. The rescan interval is configured in seconds and the search distance in blocks.

### Known issues

Tracked in the [issue tracker](https://github.com/simply-sunny/find-my-items/issues):

- The grid layout is not retained when switching views or reopening the catalog.
- The retrieval tooltip shows the requested amount rather than the amount that will be moved when fewer are available.
