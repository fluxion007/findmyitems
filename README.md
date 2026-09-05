<p align="center">
  <img src="src/client/resources/assets/findmyitems/icon.png" width="128" height="128" alt="Find My Items logo" />
</p>

# Find My Items

Index opened containers, search items and crafting materials, and retrieve or deposit items from one native client-side screen.

## Features

- **Catalog Screen**: Native client-side search UI with Mod Menu integration and keybind support (`B` by default).
- **Container Indexing**: Automatically indexes chests, trapped chests, barrels, ender chests, and placed shulker boxes when opened.
- **Search & Filter**: Word-aware fuzzy search matching item names, item IDs, and tooltip text (including enchantment names and Roman/Arabic numerals).
- **Retrieve & Deposit**: Quick retrieval and item-conserving deposit to nearby containers in interaction range.
- **Crafting Planner**: Multi-depth material planning with stock deduction from indexed containers.
- **Container Screen Filter**: In-place filtering overlay on standard container screens.
- **Nested Shulker Indexing**: Recursively indexes shulker boxes inside containers up to four levels deep.
- **Single-Player Guard**: Automatically disables on multiplayer servers to prevent stale index state.

## Requirements

- Minecraft 26.2
- Fabric Loader (>=0.19.3)
- Fabric API
- Mod Menu (optional, >=20.0.0)

## Install

1. Install Fabric Loader for Minecraft 26.2.
2. Add Fabric API and Mod Menu to `mods/`.
3. Add `find-my-items-0.1.5.jar` (from [Releases](https://github.com/simply-sunny/find-my-items/releases)) to `mods/`.
4. Launch the client.

## Controls

- Open Catalog: press `B` (configurable in Options → Controls → Miscellaneous).
- Switch Views: `Ctrl+1` (Items), `Ctrl+2` (Containers), `Ctrl+3` (Crafting) — use `Cmd` on macOS.
- Items View:
  - Eye button: highlight container through blocks for 5 seconds.
  - Hopper button: retrieve specified item quantity.
  - Chest button: deposit matching inventory items.
- Grid Layout: left-click to retrieve, right-click to highlight, hover for detailed container breakdown.
- Container Filter: type in the search bar above open container screens to highlight matching slots.

## Build

```bash
./gradlew clean test build
```

Output jar: `build/libs/find-my-items-0.1.5.jar` (requires Java 25).

## Limitations

- Client-side only; indexes only containers personally opened by the player.
- Single-player worlds only (disables on multiplayer to avoid stale container records).
- Supports vanilla containers and crafting recipes (crafting table and inventory); custom modded containers and furnaces/workstations are not indexed.
- Container retrieval and deposit respect normal player reach distances and unloaded chunk boundaries.

## License

All rights reserved — see `fabric.mod.json` for license declarations.
