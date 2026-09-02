# Contributing to Find My Items

How to report problems, request changes, and submit code to Find My Items (`findmyitems`).

## Project principles

Find My Items (`findmyitems`) is a client-side Fabric mod with a deliberately small feature surface. Evaluate changes by whether they make the container index more accurate or usable, not simply by whether they add functionality.

Two constraints apply to every change:

- **Conserve item counts.** Any code path that moves items must neither create nor destroy them. This includes a full player inventory and creative mode. Minecraft's `Inventory.add` reports success when it places only part of a stack and discards the remainder in creative mode. `RetrieveHandler` therefore counts what actually arrived in the inventory instead of relying on that return value.
- **Remain client-side and single-player.** The mod sends no packets that a vanilla client would not send and requires no server-side component. When it needs authoritative world state, it uses the integrated server of a single-player world. Do not add a partial multiplayer mode; see the README for why multiplayer is excluded.

## Reporting a bug

Open an issue with the **Bug report** template. Include:

- What happened and what you expected instead
- Steps to reproduce
- The mod version
- The Minecraft, Fabric Loader, and Fabric API versions
- Other installed mods
- Relevant lines from `logs/latest.log`

Before filing, note:

- Disabling itself on a multiplayer server is intended behaviour, not a bug.
- An incorrect item count is the most useful category of report. The index records what a container held when it was last read. If a count is wrong, describe what changed in the container between that reading and when the count was displayed.

If you are running from source, the index file at `config/findmyitems/worlds/ID.json` is a useful attachment. It contains item names and block coordinates from your world; review it before posting.

## Requesting a feature

Open an issue with the **Feature request** template. Describe the situation in your world that prompted the request, not only the feature you have in mind. Describing the problem allows for a wider range of solutions.

## Development

### Requirements

- JDK `25` or newer
- The included Gradle wrapper; no separate Gradle installation

### Build and run

```sh
./gradlew build      # compile, then run the JUnit tests
./gradlew runClient  # launch a development client with the mod loaded
```

### Testing

Three test layers are available, in increasing cost:

| Command | Scope | Approximate time |
| --- | --- | --- |
| `./gradlew test` | JUnit over the index, store, and model types. No Minecraft classes are loaded. | 1 second |
| `./gradlew runGameTest` | Headless server with real levels, block entities, and player inventories. Covers retrieval and indexing. | 10 seconds |
| `./gradlew runClientGameTest` | Real client that creates a world and drives the mod through input. | 30 seconds |

`./gradlew build` runs only the first layer. Continuous integration runs the first two layers on every pull request.

`runClientGameTest` opens a game window and writes screenshots to `build/run/clientGameTest/screenshots/`. Its assertions check facts such as index contents and which screen is open, not pixels, so an intended visual change does not fail the build.

### Where tests belong

| Change type | Location |
| --- | --- |
| No Minecraft required: index, store, model | `src/test/`, plain JUnit |
| World, block entities, inventories | A `@GameTest` method in `src/gametest/` |
| A container that is not a single block entity, an inventory that cannot accept what is offered, or any case whose invariant is that no items were created or destroyed | `RetrieveEdgeCaseGameTest` |
| Input, screens, rendering | A step in `FindMyItemsClientGameTest` |

List every new `@GameTest` class in the `fabric-gametest` entrypoint of `src/gametest/resources/fabric.mod.json`. An unlisted class never runs, and no error is reported.

### Testbed command

In a development client (`./gradlew runClient`), `/fmitest build` places a row of stocked containers in front of the player, and `/fmitest clear` restores blocks that `build` overwrote.

The command is registered only when Fabric reports a development environment. The `debug` package is excluded from the released jar by the `jar` task in `build.gradle`.

`/fmitest strand` removes the testbed's ender chest, leaving its remembered contents with no way to reach them—the one state in which a container is counted but cannot be opened. `clear` puts the block back.

`clear` restores only blocks recorded by `build` during the current session. That record is held in memory and does not survive a restart.

The testbed places thirteen containers, each covering a specific case:

| # | Container | Case covered |
| --- | --- | --- |
| 1 | Chest | Baseline indexing, search, and retrieval |
| 2 | Double chest | One container occupying two block positions |
| 3 | Trapped chest | Container type handling |
| 4 | Barrel | Container type handling |
| 5 | Shulker box block | A distinct menu type with 27 slots |
| 6 | Ender chest | Listed first in the Containers view. Stocked with 10 emeralds on the player, against 5 in #1, so one item is split between a block container and the ender inventory |
| 7 | Chest containing shulker boxes | Nested indexing and retrieval, including one item two levels deep |
| 8 | Chest | Items differing only by components: two identically named swords with different enchantments, two enchanted books, a plain sword, a damaged pickaxe |
| 9 | Chest | Partial crafting materials, so the Crafting view shows both covered and missing rows |
| 10 | Chest | 512 iron ingots, for amounts above one stack |
| 11 | Chest | Stacking edge cases: beds and dragon eggs, which do not stack; buckets, filled and empty; elytra; cake |
| 12 | Empty barrel | Display of an empty container |
| 13 | Chest, 25 blocks away | Out of interaction range: highlighting works, retrieval is disabled |

## Submitting a change

1. **Branch from `main`.** Use `fix/short-description` or `feat/short-description`.
2. **Keep the change focused.** Give each pull request one reason. Combining a rename with a behaviour change makes the behaviour change harder to review.
3. **Add a test.** Any change involving a branch, a loop, or items moving between two places needs one. See [Where tests belong](#where-tests-belong).
4. **Run the checks.** Run `./gradlew build` and `./gradlew runGameTest` at minimum. Add `./gradlew runClientGameTest` if the change affects input, screens, or rendering.
5. **Write a specific commit message.** For example, `fix: reach the far half of a double chest` rather than `fix chest bug`. Prefixes in use: `feat:`, `fix:`, `perf:`, `docs:`, `test:`, `chore:`.
6. **Open a pull request.** The template asks what changed, why, which checks were run, and which test covers it.

Do not add AI co-author trailers to commit messages or attribution lines to pull request descriptions. See `CLAUDE.md`.

## Code style

Comments should explain why code is written a certain way, not what it does. Code that needs no explanation needs no comment. Names should describe behaviour.

Mark deliberate simplifications with a known limit using a `ponytail:` comment that names the limit and the upgrade path. For example, a single-entry memo would need proper invalidation if datapack reloads became relevant.

## Releasing

For maintainers:

1. Update `mod_version` in `gradle.properties`.
2. Add a dated section to `CHANGELOG.md`.
3. Commit and push to `main`.
4. Tag the commit `vVERSION`, matching `mod_version` exactly, and push the tag.

The `release` workflow compiles the mod, runs the JUnit and headless server tests, verifies that the tag matches `mod_version`, and creates a draft GitHub release with the jar attached. Review the draft before publishing it.

### Modrinth

Publishing to Modrinth is a separate, manual step once the release looks right:

```sh
./gradlew publishMods -PdryRun   # prints what would be uploaded, uploads nothing
MODRINTH_TOKEN=... ./gradlew publishMods
```

The version number, jar, and supported Minecraft version all come from `gradle.properties`. Release notes come from the `CHANGELOG.md` section for that version; a version without a section fails the task instead of uploading blank notes. `MODRINTH_TOKEN` must be a Modrinth personal access token with the `Create versions` scope. It is read from the environment and never stored in the repository.

The dependencies declared to Modrinth mirror `depends` and `recommends` in `fabric.mod.json`. Changing one without the other makes Modrinth offer a download that the loader then refuses to run.
