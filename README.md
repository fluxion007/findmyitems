# findmyitems

A client-side Fabric mod for Minecraft Java Edition that indexes the contents of containers you have opened and lets you search and retrieve items from them.

findmyitems downloads: 135

## Overview

`findmyitems` records what it sees whenever you open a storage container in a single-player world. Those records form an index you can search from one screen, so you do not have to open chests one at a time to find an item.

The index is built only from containers you have opened yourself. The mod does not read containers you have never looked in.

Features:

- A searchable catalog of every container you have opened, opened with a keybind.
- Search across item names, item ids, and tooltip text, including enchantment names and levels.
- Retrieval and deposit of items from containers within normal interaction range.
- A visual highlight that outlines a container in the world, visible through blocks.
- A crafting view that breaks an item into its materials and subtracts what your containers already hold.
- Crafting search with word-aware fuzzy matching, cycle-safe planning, and lazy, virtualized result browsing.
- Gather-only and gather-and-craft actions with cancellation, stale-index recovery, and inventory-safe transfers.
- A filter field added to ordinary container screens, which dims slots that do not match what you type.
- Indexing and retrieval of items stored inside shulker boxes placed inside containers, up to four levels of nesting.

Supported containers:

- Chest and double chest
- Trapped chest
- Barrel
- Ender chest
- Shulker box placed as a block

Containers added by other mods are not supported.

### Single-player only

The mod is active only in single-player worlds. On a multiplayer server it disables itself: no indexing takes place, no filter field is added to container screens, and the catalog keybind displays a message instead of opening.

The reason is that the index records what a container held at the moment you last opened it. In a world where only you can change containers, that record stays accurate. On a server, another player can change a container's contents at any time, and a client has no reliable way to learn that this happened. The mod would then report contents that no longer exist.

## Installation

`findmyitems` is a mod for the Fabric mod loader. Fabric is a modding toolchain for Minecraft Java Edition; it consists of a loader that runs mods and a set of shared APIs that mods depend on.

Requirements:

- Minecraft Java Edition `26.2`
- Java `25` or newer
- Fabric Loader `0.19.3` or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) — the shared API library that most Fabric mods, including this one, require
- [Mod Menu](https://modrinth.com/mod/modmenu) — adds an in-game mod list and gives this mod its settings screen
- [Cloth Config](https://modrinth.com/mod/cloth-config) — the library used to build that settings screen

Steps:

1. Install Fabric Loader for Minecraft `26.2`.
2. Download Fabric API, Mod Menu, and Cloth Config for the same Minecraft version.
3. Download `findmyitems-VERSION.jar` from the [releases page](https://github.com/SonicKarnati/findmyitems/releases).
4. Place all four `.jar` files in your `mods/` folder.
5. Start Minecraft using the Fabric profile.

Optional:

- [Shulker Box Tooltip](https://modrinth.com/mod/shulkerboxtooltip) — displays shulker box contents on hover. It is listed as a recommended dependency and is not required. This mod indexes the contents of shulker boxes; that mod displays them in a tooltip.
- [Sodium](https://modrinth.com/mod/sodium) — a rendering optimization mod, listed as recommended and not required.

## Usage

Open a container to add it to the index. No other setup is required.

Press `B` to open the catalog. The keybind can be changed in the vanilla **Options → Controls** screen, under the Miscellaneous category.

The catalog has three views, selected with the tabs at the top or with keyboard shortcuts:

| View | Shortcut | Contents |
| --- | --- | --- |
| Items | `Ctrl+1` | Every item in the index, with the nearest container holding it |
| Containers | `Ctrl+2` | Every container in the index, nearest first |
| Crafting | `Ctrl+3` | Material breakdown for a chosen item |

On macOS, use `Cmd` in place of `Ctrl`.

The Items and Containers views can be shown as a list or as a grid, using the toggle on the right of the tab row.

### Items view

Each row shows an item, the number held across all indexed containers, and the position of the nearest container holding it. Each row has three buttons:

- **Ender eye** — outlines the container in the world for five seconds. The outline is drawn through blocks, so it is visible from a distance and behind walls.
- **Hopper** — moves the amount in the amount field from the container into your inventory.
- **Chest** — moves matching items from your inventory back into the container.

Retrieval and deposit require the container to be within block interaction range: the range in which you could right-click it, plus one block of allowance. Rows outside that range show both buttons as disabled; the ender eye button still works.

That range can be raised, up to 256 blocks, with the **Retrieval reach** setting. It is off by default: it is the one setting that changes what the mod lets you do rather than what it knows. A raised reach still cannot read a container in an unloaded chunk, and it never shortens the reach you already have.

Deposit is intentionally limited: it is offered only when the target container already holds that exact item, including its components. The mod does not choose a destination for an item it has never seen stored.

When you retrieve an item, the mod sends a normal interaction packet that opens the container on the server, moves the items, and then closes it. The container's lid animation and sound occur as they would if you opened it by hand. The container's own screen is not displayed.

In the grid layout, left-click retrieves and right-click highlights.

A cell has room for a count and nothing else, so the grid reserves a pane down its right side. Hover a cell and the pane breaks that item down: every container holding it, how many are in each, and where it is. Containers you can take from now are listed in green; ones you cannot are red, with the reason underneath — too far away, in another dimension, or an ender chest inventory with no ender chest in reach. The pane is always there and empty until you hover, so hovering never moves the cell you were about to click.

### Containers view

Lists every indexed container with its type and position, nearest first. Your ender chest is placed at the top of the list and remains listed when it is empty, unless a search term is active.

### Crafting view

Type an item name, or leave the field empty to list every item that has a crafting recipe and select one.

The result is a tree of the materials required:

- Indented rows are sub-crafts, for example planks listed beneath a chest.
- A row shown in green is fully covered by items already in your containers.
- A row shown in red is a shortfall you must obtain yourself.

A material is expanded into its own components only when the index cannot already cover it. Only crafting-table recipes and player-inventory recipes are supported. Furnaces, stonecutters, smithing, and other processing stations are not followed.

### Searching

The search field matches item names, item ids, and tooltip text.

- Enchantment levels match in either notation. `smite 4` and `smite iv` return the same item.
- Items that differ only by their components are listed separately. Two swords of the same type with different enchantments are two entries, because they are not interchangeable.

### Container screen filter

A filter field is added above ordinary container screens. Typing in it dims the slots whose items do not match. It changes only what is drawn; no items are moved.

## Configuration

Settings are available in Mod Menu, on the `findmyitems` entry. They are stored in `config/findmyitems.json` inside your Minecraft directory.

| Setting | JSON key | Default | Range | Description |
| --- | --- | --- | --- | --- |
| Rescan interval | `rescanIntervalSeconds` | `5` | `0`–`30` | How often indexed containers near you are re-read, in seconds. `0` disables re-scanning, so entries update only when you open a container. |
| Search distance | `searchDistanceChunks` | `4` | `0`–`32` | How far from you a container may be and still be re-read. `0` means unlimited. Lower this if re-scanning causes a frame time increase in a large base. |
| Index ender chest anywhere | `indexEnderInventory` | `true` | on/off | Keep your ender chest contents indexed with no ender chest placed. |
| Retrieval reach | `retrieveDistanceBlocks` | `0` | `0`–`256` | How far away a container may be and still be taken from or deposited into. `0` keeps your own reach. Raising this never shortens it, and a container in an unloaded chunk still cannot be reached into. |

Re-scanning applies only to containers whose chunks are currently loaded.

The ender inventory is the exception. It is player data rather than world data, so it is read without a block placed or a chunk loaded, and its count stays true wherever you are. Knowing what is in there is not the same as being able to reach it: until you are standing at an ender chest, the catalog lists that stock as out of reach, and the take button says so.

The keybind is not stored in this file. It is a standard Minecraft key mapping and is stored with your other controls.

The index itself is stored separately, at `config/findmyitems/worlds/ID.json`, where `ID` identifies the world save and player. Index files are keyed by save directory and player UUID, so two saves with the same displayed name do not share an index.

## Development

The project builds with Gradle and [Fabric Loom](https://fabricmc.net/wiki/tutorial:loom), the Gradle plugin used to build Fabric mods.

Requirements:

- JDK `25` or newer
- No separate Gradle install; use the included wrapper

Common tasks:

```sh
./gradlew build      # compile, then run the JUnit tests
./gradlew runClient  # launch a development client with the mod loaded
```

Versions are declared in `gradle.properties`:

```properties
minecraft_version=26.2
loader_version=0.19.3
fabric_api_version=API_VERSION
mod_version=VERSION
archives_base_name=findmyitems
```

Dependencies are declared in `build.gradle`. This project uses the `implementation` configuration for its Modrinth dependencies:

```groovy
// Groovy DSL
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
    implementation "maven.modrinth:modmenu:${project.modmenu_version}"
    implementation "maven.modrinth:cloth-config:${project.cloth_config_version}"
}
```

The mod uses an access widener, a Fabric mechanism for making selected vanilla fields and methods accessible without a mixin. It is declared in `build.gradle` and in `fabric.mod.json`, and its entries are in `src/main/resources/findmyitems.accesswidener`. The file uses the `official` namespace, matching the Mojang mappings that Loom uses by default.

The `/fmitest` command builds a set of stocked containers in a development world for manual testing. It is registered only when Fabric reports a development environment, and its package is excluded from the released jar. See `CONTRIBUTING.md` for what it places.

### Testing

Three test layers are available. All three run without loading a world by hand.

```sh
./gradlew test               # JUnit only; no Minecraft classes loaded
./gradlew runGameTest        # headless server: real levels, block entities, inventories
./gradlew runClientGameTest  # launches a real client and drives it through input
```

`./gradlew build` compiles the project, runs the JUnit layer, and invokes this project's configured
headless `runGameTest` task. `./gradlew test` is the JUnit-only check. `runClientGameTest` remains a
separate task because it starts a client, opens a game window, and writes screenshots to
`build/run/clientGameTest/screenshots/`.

### Manual crafting planner fixture

The manual fixture pass is a release blocker for this release candidate and has not been manually verified. Automated tests do not replace this pass.

For a repeatable manual pass, copy the fixture into a test world's datapack directory and stand at the origin
before setup:

```sh
cp -R src/test/resources/findmyitems-test-fixture <test-world>/datapacks/findmyitems-test-fixture
./gradlew runClient
```

In the client, run `/reload`, then run `/function findmyitems:reset` explicitly to clear any previous fixture
markers. This reset is safe from any player position. Stand at the chosen origin and run
`/function findmyitems:setup`; coordinates below are relative to the player's position when `setup` runs
(`~x ~y ~z`). Setup claims only air blocks and marks them, so it can be repeated without overwriting
pre-existing blocks. Reset removes only marked fixture blocks that still have their expected fixture type;
it does not restore or destroy unmarked blocks. Run `/function findmyitems:reset` explicitly after the pass.

Open each unobstructed container once so it is indexed. Do not try to open the obstructed chest at `~4 ~ ~2`
while the stone is present: temporarily remove the stone at `~4 ~ ~1`, open the chest, then put the stone back
before testing its unreachable state. The far chest may likewise require a temporary creative-mode position
change to open and index it.

| Relative location | Fixture and expected use |
| --- | --- |
| `~2 ~ ~2` | Accessible chest: white bed and bedrock; search and component identity cases |
| `~4 ~ ~2` | Obstructed chest: stone at `~4 ~ ~1`; locate must be zero/unreachable |
| `~6 ~ ~2` | Doorway-visible chest: stone at `~5 ~ ~1` and `~7 ~ ~1`; locate must be positive/reachable |
| `~8 ~ ~2` and `~9 ~ ~2` | Adjacent double chest: iron in both halves; both sources must index and conserve stock |
| `~12 ~ ~2` | Hopper-fed chest, with hopper at `~12 ~1 ~2`; verify source/container handling |
| `~14 ~ ~2` | Crafting table: reachable workstation case |
| `~16 ~ ~2` | Partial-material chest: diamond, sticks, and oak logs; gather-only and gather-and-craft |
| `~30 ~ ~2` | Far chest: outside normal interaction range; locate must be zero/unreachable |

Pass/fail checklist:

- [ ] **Setup/reset:** after setup, all eight unoccupied fixture groups above exist; after reset, owned blocks are air and nearby pre-existing blocks are unchanged.
- [ ] **Search:** `bed` ranks the white bed, `white bed` matches it, `bedrock` does not match the bed, `whit bed` exercises fuzzy matching, and repeated whitespace behaves like normalized whitespace.
- [ ] **Reachability:** `~2 ~ ~2` is reachable, `~4 ~ ~2` is obstructed, `~6 ~ ~2` is visible through the doorway, and `~30 ~ ~2` is out of range. Record locate count zero/positive for each.
- [ ] **Containers:** the double chest at `~8 ~ ~2`/`~9 ~ ~2` and hopper-fed chest at `~12 ~ ~2` index correctly; no item count changes merely from indexing.
- [ ] **Crafting browse:** empty browse opens without planning, rows remain usable at the bottom of the list, and changing the filter clears an invalid selection.
- [ ] **Planning:** exercise roots at multiple depths, shared stock, SCC cycles, and batch surplus; the plan terminates and never spends the same stock twice.
- [ ] **Execution:** with the table at `~14 ~ ~2`, gather-only and gather-and-craft succeed using the three diamonds and two sticks at `~16 ~ ~2`; repeat with no reachable table and verify the clear failure state.
- [ ] **Failure handling:** verify cancellation, stale sources, and a near-full player inventory do not create or destroy items.

Only crafting-table recipes and player-inventory recipes are supported; furnaces, stonecutters,
smithing, and other processing stations are intentionally not part of the fixture or planner. Record each manual case's setup,
expected result, actual result, and screenshot/log path. Manual screenshots/logs are evidence distinct from
JUnit, `build`, headless GameTest, and client GameTest results.

## Modules

Source is split into three source sets. `main` contains code that may run on either side, `client` contains client-only code, and `gametest` contains in-game tests. Package root is `dev.smpb.findmyitems`.

`main`:

- `index` — the in-memory index. `ContainerIndex` is the interface, `InMemoryContainerIndex` the implementation. Also holds `SearchQuery` and the result types returned to the UI.
- `model` — value types shared across the mod: `SourceKey` identifies a container, `StackKey` identifies an item including its components, and `ContainerObservation` is one reading of one container.
- `observation` — turns a container's slots into model objects. `SlotReader` reads slots, including nested shulker box contents.
- `retrieval` — `RetrieveHandler` moves items between a container and the player, and enforces interaction range. All item movement is required to conserve item counts.
- `craft` — `CraftingPlanner` builds the material tree and charges it against the index.
- `store` — reading and writing the index as JSON, with atomic writes and a backup file.
- `config` — `ModConfig`, the settings record loaded from and saved to JSON.
- `debug` — the `/fmitest` testbed command. Excluded from the released jar.

`client`:

- `FindMyItemsClient` — the client entry point. Registers the keybind, loads the index for the current world, and holds the single check that disables the mod outside single-player.
- `gui` — `CatalogScreen` is the catalog. `ChestHighlighter` draws the container outline.
- `observation` — `ObservationCollector` records containers as you open them and drives periodic re-scanning. `PositionCache` records the position of your last block interaction.
- `retrieval` — `GhostOpen` opens a container on the server, runs an action, and closes it, without displaying the container screen.
- `search` — `InventorySearchController` adds the filter field to container screens.
- `config` — the Mod Menu settings screen.

## Contributing

Bug reports, feature requests, and pull requests are accepted on GitHub.

- Report a bug or request a feature: [issue tracker](https://github.com/SonicKarnati/findmyitems/issues). Templates are provided and ask for the information needed to reproduce a problem.
- Before changing code, read [`CONTRIBUTING.md`](CONTRIBUTING.md). It covers branch and commit conventions, where tests belong, and the two constraints that changes are expected to respect.

## Support

- **Known issues** are tracked in the [issue tracker](https://github.com/SonicKarnati/findmyitems/issues). The manual crafting-planner fixture pass remains outstanding for the `0.1.4` release candidate.
- **Multiplayer behaviour is intentional.** The mod disabling itself on a server is not a bug. See [Single-player only](#single-player-only).
- **When reporting a problem**, include the mod version, the Minecraft, Fabric Loader, and Fabric API versions, and the other mods you have installed. Relevant lines from `logs/latest.log` help. If a reported count is wrong, state what changed the container between the last time you opened it and the moment the count was shown.
- **Release notes** are in [`CHANGELOG.md`](CHANGELOG.md) and on each [release](https://github.com/SonicKarnati/findmyitems/releases).

## License

All rights reserved. See `fabric.mod.json` for the declared license field.
