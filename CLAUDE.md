# Find My Items (`findmyitems`) — instructions for Claude

## Commits

Do not add `Co-Authored-By: Claude` or any AI co-author trailer to git commit messages. Do not add "Generated with Claude Code" or any similar attribution line to pull request descriptions.

## Build and test

```sh
./gradlew build          # compile + JUnit
./gradlew runGameTest    # headless server tests — run these before saying a change works
```

`runClientGameTest` boots a real client and opens a window, which takes over the screen. Do not just stop the turn to ask about it — use the AskUserQuestion tool ("Open a client window and run the client game tests?", yes/no) and carry on with the answer, so the rest of the work in flight is not left hanging on a reply.

Full conventions, house style and the testbed live in [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Two standing constraints

- **Nothing is destroyed.** Any path that moves items must conserve them — including a full inventory, and including creative mode, where vanilla's `Inventory.add` voids the leftover.
- **Single-player only, on purpose.** The mod stands down on multiplayer servers. Do not add a partial multiplayer mode; see the README for the reasoning.

## Item identity is a components key, and it needs the registries

Two stacks are "the same item" here exactly when their item id and their `StackKey.componentsJson` match. That string comes from `SlotReader.serializeComponents`, and it **must** be encoded against a `HolderLookup.Provider` (`player.registryAccess()`). Enchantments, potions, trims and anything else data-driven hold registry entries whose codecs simply throw under bare `JsonOps` — every time, not intermittently.

Two rules follow, and both were learned the expensive way:

- **Never fall back to `"{}"`.** That is a plain stack's key. A swallowed encode failure that returns it declares a Sharpness V sword to be the same object as an unenchanted one, and retrieval — which re-derives this key server-side — then pulls every variant out of the chest at once. Degrade to something unique-per-patch instead.
- **Both ends must agree.** The index writes the key on the client, `RetrieveHandler.matches` re-derives it on the server. Change one encoder and you have silently changed what "the same item" means; `ComponentKeyTest` and the variant game tests exist to catch that.

## One config instance

`FindMyItemsClient.config()` is the only `ModConfig` anyone may hold. The settings screen used to `ModConfig.load` its own copy, so every change wrote a file the running `ObservationCollector` never re-read — the setting looked broken until a restart. Anything that reads settings at runtime reads that instance.
