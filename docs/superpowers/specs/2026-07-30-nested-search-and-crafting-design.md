# Nested Retrieval and Search Design

## Goal

Improve Find My Items so nested shulker results are actionable, item searches understand enchantments, common matches rank first, catalog refreshes do not lose the user's scroll position, and the Crafting view excludes creative-only items. Add automated and manual test coverage for these behaviors.

## Behavior

### Nested shulkers

The index continues to expose items inside shulkers as searchable results. Each nested result also records the exact path from the accessed container to the outermost shulker containing it. The path must distinguish slots in double chests and ender-chest inventories and must remain bounded by the existing nesting limit.

When the selected result is inside a shulker, Take moves the entire outermost shulker box to the player, preserving all contents. Each action moves one box; the requested item amount neither partially extracts the nested item nor implies multiple boxes. Direct items retain the existing count-based behavior. Every operation must conserve items when the player inventory is full, including creative mode.

### Search

Catalog search and the injected vanilla-container filter use the same matching rules. Queries are split into independent, case-insensitive tokens, and every token must match at least one searchable field:

- item display/custom name
- item ID/path
- tooltip text, including enchantment names and levels

Thus `diamond sword smite` matches a Smite diamond sword but not a Sharpness sword, while `smite` matches any item with Smite, including books and swords. Roman and Arabic enchantment levels remain interchangeable.

Results are ranked by relevance: exact item-name match, name prefix, name containment, metadata/enchantment match, then ID match. Stable item-ID and component-key tie-breakers prevent jitter between refreshes.

### Catalog refresh

Index-driven refreshes update rows without resetting the current scroll offset when the view, layout, and query remain unchanged. Explicit query, view, and layout changes may reset or reposition the list as needed. Refreshes must not recreate cached search/crafting data unless their source data changed.

### Crafting

The Crafting view includes only survival-available recipe outputs. Creative-only entries such as command blocks, barrier blocks, and structure blocks are excluded using vanilla availability metadata or equivalent authoritative item properties, not a brittle list of item names.

## Architecture

Extend the indexed stack/source model with nested provenance sufficient to identify the outermost shulker slot and any parent path. Keep the searchable snapshot independent from the retrieval command's transport details. The catalog row uses the provenance when choosing its Take target; direct-stack rows continue using the existing item key and amount.

Centralize query tokenization, searchable-document construction, level normalization, and relevance scoring in the index/search layer. The client-side container filter calls the shared matcher rather than duplicating display-name and ID checks.

Keep retrieval server-authoritative. A nested Take request validates reach, source dimension/position, the expected shulker/item identity, and the component key before moving the whole holder stack. Existing component serialization continues to use the player's registry access and never degrades failed encodes to `{}`.

## Testing

Add unit tests for token matching, enchantment names and levels, multi-token queries, and relevance ordering. Add game tests for whole-shulker retrieval from ordinary chests and ender chests, multiple matching shulkers, nested-depth limits, full inventories, creative mode, and component-specific variants.

Add client-game coverage for scroll preservation and shared search behavior where the existing client test harness permits it. Add a reusable source data pack and instructions that create a chest-room fixture containing direct items, enchanted books, differently enchanted swords, multiple nested shulkers, ender-chest contents, and inventory-capacity edge cases.

Verification consists of `./gradlew build`, headless `./gradlew runGameTest`, client game tests after user approval to open a client window, and a manual fixture pass in a playable world.

## Scope and Cleanup

Simplify redundant comments and code only in files touched by this feature. Preserve comments documenting non-obvious conservation guarantees, registry-backed component identity, and the intentional single-player boundary. Do not add multiplayer behavior or unrelated refactors.
