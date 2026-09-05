# Find My Items

Find My Items (`findmyitems`) is a client-side Fabric mod for Minecraft Java Edition. It indexes containers you open, lets you search them from one screen, and can retrieve or deposit items without opening each container manually.

findmyitems downloads: 161

## Features

- Search item names, item IDs, and tooltip text, including enchantment names and levels.
- Browse indexed items and containers, with list and grid layouts.
- Retrieve items from, or deposit matching items into, nearby containers.
- Highlight a container through blocks for five seconds.
- Plan crafting materials, subtracting stock already in indexed containers.
- Search recipes with word-aware fuzzy matching, cycle-safe planning, and lazy, virtualized results.
- Gather materials only, or gather and craft them, with cancellation and stale-index recovery.
- Filter ordinary container screens; non-matching slots are dimmed but never changed.
- Index shulker boxes inside containers through four levels of nesting.

The index contains only what the player has personally opened. The mod does not inspect containers that have never been opened.

Supported containers:

- Chest and double chest
- Trapped chest
- Barrel
- Ender chest
- Shulker box placed as a block

Containers added by other mods are not supported.

### Single-player only

The mod intentionally disables itself on multiplayer servers. It does not index there, add the container-screen filter, or open the catalog; the catalog keybind displays a message instead.

This prevents stale results. In single-player, the mod can keep its record accurate because only you change the containers. On a server, another player could change a container at any time without the client knowing.

## Installation

Find My Items requires the [Fabric mod loader](https://fabricmc.net/), [Fabric API](https://modrinth.com/mod/fabric-api), [Mod Menu](https://modrinth.com/mod/modmenu), and [Cloth Config](https://modrinth.com/mod/cloth-config).

Requirements:

- Minecraft Java Edition `26.2`
- Java `25` or newer
- Fabric Loader `0.19.3` or newer

Install:

1. Install Fabric Loader for Minecraft `26.2`.
2. Download Fabric API, Mod Menu, and Cloth Config for the same Minecraft version.
3. Download `find-my-items-VERSION.jar` from the [releases page](https://github.com/simply-sunny/find-my-items/releases).
4. Put all four `.jar` files in the Minecraft `mods/` folder.
5. Start Minecraft with the Fabric profile.

Optional recommended mods:

- [Shulker Box Tooltip](https://modrinth.com/mod/shulkerboxtooltip) displays shulker contents on hover. It is not required; Find My Items indexes those contents itself.
- [Sodium](https://modrinth.com/mod/sodium) provides rendering optimizations. It is not required.

## Usage

Open a container once to index it. No other setup is required.

Press `B` to open the catalog. Change this key in **Options → Controls**, under **Miscellaneous**.

| View | Shortcut | Shows |
| --- | --- | --- |
| Items | `Ctrl+1` | Indexed items and the nearest container holding each |
| Containers | `Ctrl+2` | Indexed containers, nearest first |
| Crafting | `Ctrl+3` | Materials required for a selected item |

On macOS, use `Cmd` instead of `Ctrl`. The Items and Containers views can use a list or grid layout.

### Items view

Each item shows its total indexed count and the nearest container holding it:

- **Ender eye:** highlight the container through blocks for five seconds.
- **Hopper:** retrieve the amount in the amount field.
- **Chest:** deposit matching items from your inventory.

Retrieval and deposit require the container to be within normal block-interaction range, plus one block of allowance. The buttons are disabled outside that range, but highlighting still works. **Retrieval reach** can extend this range to 256 blocks; it is off by default. It does not shorten your normal reach and cannot reach an unloaded chunk.

Deposit is offered only when the target already contains that exact item, including its components. The mod never chooses a destination for an item it has not seen stored.

Retrieval uses a normal server interaction: the container opens, items move, and it closes again. The usual lid animation and sound occur, but the container screen is not shown. Transfers conserve items, including when the player inventory is full or the player is in creative mode.

In the grid layout, left-click retrieves and right-click highlights. Hover a cell to show a pane listing every container holding that item, its count, and its position. Reachable containers are green; unreachable ones are red with the reason: too far away, another dimension, or an ender inventory with no ender chest in reach. The pane remains empty until a cell is hovered.

### Containers view

This view lists every indexed container with its type and position, nearest first. Your ender chest remains at the top even when empty, unless a search term is active.

### Crafting view

Enter an item name, or leave the field empty to browse every item with a crafting recipe. The result is a material tree:

- Indented rows are sub-crafts, such as planks beneath a chest.
- Green rows are fully covered by indexed stock; red rows are shortfalls.
- A material expands into components only when the index cannot cover it.

Only crafting-table and player-inventory recipes are supported. Furnaces, stonecutters, smithing, and other processing stations are not followed.

### Searching

Search matches item names, item IDs, and tooltip text. Enchantment levels accept either notation: `smite 4` and `smite iv` match the same item. Items with different components remain separate; differently enchanted swords are not interchangeable.

### Container-screen filter

A filter field appears above ordinary container screens. It dims slots whose items do not match the text. It changes only the display and never moves items.

## Configuration

Open the Find My Items entry in Mod Menu. Settings are stored in `config/findmyitems.json` in the Minecraft directory.

| Setting | JSON key | Default | Range | Description |
| --- | --- | --- | --- | --- |
| Rescan interval | `rescanIntervalSeconds` | `5` seconds | `0`–`30` | Re-read nearby indexed containers this often. `0` disables rescanning; entries update when opened. |
| Search distance | `searchDistanceChunks` | `4` chunks | `0`–`32` | Maximum distance for rescanning. `0` means unlimited. Lower it if rescanning affects frame time. |
| Index ender chest anywhere | `indexEnderInventory` | `true` | on/off | Keep ender-chest contents indexed without a placed ender chest. |
| Retrieval reach | `retrieveDistanceBlocks` | `0` blocks | `0`–`256` | Maximum distance for retrieval and deposit. `0` uses normal reach; unloaded chunks remain unreachable. |

Rescanning applies only to containers in loaded chunks. Ender-chest inventory is player data, so it is read without a placed block or loaded chunk and remains accurately counted anywhere. It is still unreachable until you are at an ender chest; the catalog marks it out of reach and explains why.

The keybind is stored with Minecraft's other controls, not in this file. The index is stored separately at `config/findmyitems/worlds/ID.json`. Files are keyed by save directory and player UUID, so saves with the same displayed name do not share an index.

## Development

The project uses Gradle and [Fabric Loom](https://fabricmc.net/wiki/tutorial:loom). You need JDK `25` or newer; no separate Gradle installation is required.

```sh
./gradlew build      # compile and run JUnit tests
./gradlew test       # JUnit only; no Minecraft classes loaded
./gradlew runGameTest        # headless server tests
./gradlew runClient           # development client
./gradlew runClientGameTest   # real client/input tests; opens a window
```

`runClientGameTest` writes screenshots to `build/run/clientGameTest/screenshots/`. Versions are in `gradle.properties`; dependencies are in `build.gradle`. The project uses an access widener at `src/main/resources/findmyitems.accesswidener` in the `official` namespace. The development-only `/fmitest` command creates stocked containers for manual testing and is excluded from release jars. See [`CONTRIBUTING.md`](CONTRIBUTING.md) for project conventions and testbed details.

### Manual crafting-planner fixture

The fixture pass is a release blocker for the `0.1.4` release candidate and has not been manually verified. Automated tests do not replace it.

Copy the fixture to a test world's datapack directory and stand at the origin before setup:

```sh
cp -R src/test/resources/findmyitems-test-fixture <test-world>/datapacks/findmyitems-test-fixture
./gradlew runClient
```

Run `/reload`, then explicitly run `/function findmyitems:reset`. At the chosen origin, run `/function findmyitems:setup`. Coordinates below are relative to the player's position when setup runs (`~x ~y ~z`). Setup claims only air blocks and marks them, so it can be repeated without overwriting existing blocks. Reset removes only marked blocks that still have their expected fixture type; it does not restore or destroy unmarked blocks. Run reset again after testing.

Open each unobstructed container once to index it. For the obstructed chest at `~4 ~ ~2`, temporarily remove the stone at `~4 ~ ~1`, open the chest, and replace the stone. The far chest may require a temporary creative-mode position change to index.

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

Pass checklist:

- [ ] Setup creates all eight unoccupied fixture groups; reset leaves owned blocks as air and nearby pre-existing blocks unchanged.
- [ ] Search: `bed` ranks the white bed, `white bed` matches it, `bedrock` does not, `whit bed` tests fuzzy matching, and repeated whitespace normalizes.
- [ ] Reachability: test the accessible, obstructed, doorway-visible, and far chests; record zero/positive locate counts.
- [ ] Containers: verify the double and hopper-fed chests index correctly without changing counts.
- [ ] Crafting browse: empty browse opens without planning; bottom rows remain usable; invalid selections clear when the filter changes.
- [ ] Planning: test multiple depths, shared stock, SCC cycles, and batch surplus; plans terminate and never spend stock twice.
- [ ] Execution: with the table at `~14 ~ ~2`, gather-only and gather-and-craft use the three diamonds and two sticks at `~16 ~ ~2`; repeat without a reachable table and verify failure.
- [ ] Failure handling: cancellation, stale sources, and a nearly full inventory neither create nor destroy items.

Only crafting-table and player-inventory recipes are supported. Record each manual case's setup, expected and actual result, and screenshot/log path. Manual evidence is distinct from JUnit, `build`, headless GameTest, and client GameTest results.

## Architecture

Source sets are `main` (code that may run on either side), `client` (client-only code), and `gametest` (in-game tests). Package root: `dev.smpb.findmyitems`.

- `main/index`: in-memory index, search, and UI result types.
- `main/model`: container, item, and observation value types.
- `main/observation`: slot reading, including nested shulker contents.
- `main/retrieval`: range checks and item-conserving transfers.
- `main/craft`: material planning.
- `main/store`: atomic JSON index writes and backup handling.
- `main/config`: JSON-backed settings.
- `main/debug`: development-only `/fmitest`.
- `client`: keybind, world index loading, single-player guard, catalog, highlighting, rescanning, retrieval, and screen filtering.

## Contributing and support

Bug reports, feature requests, and pull requests are welcome on GitHub.

- Use the [issue tracker](https://github.com/simply-sunny/find-my-items/issues); its templates request reproduction details.
- Read [`CONTRIBUTING.md`](CONTRIBUTING.md) before changing code. It covers branches, commits, tests, and the project's item-conservation and single-player constraints.
- Known issues are in the [issue tracker](https://github.com/simply-sunny/find-my-items/issues). The manual crafting-planner fixture remains outstanding for the `0.1.4` release candidate.
- Multiplayer disabling is intentional; see [Single-player only](#single-player-only).
- When reporting a problem, include the mod, Minecraft, Fabric Loader, Fabric API, and other mod versions, plus relevant lines from `logs/latest.log`. For an incorrect count, explain what changed since the container was last opened.
- Release notes are in [`CHANGELOG.md`](CHANGELOG.md) and on each [release](https://github.com/simply-sunny/find-my-items/releases).

## License

All rights reserved. See `fabric.mod.json` for the declared license field.
