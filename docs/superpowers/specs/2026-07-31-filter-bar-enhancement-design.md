# Filter Bar Enhancement Design

## Goal

Give players independent control over the injected filter bar in their survival inventory and other container screens. Make the filter search the complete item tooltip so enchantments and their levels remain discoverable when items share the same display name.

## Behavior

### Settings

Add two persisted boolean settings to `ModConfig`, both defaulting to `true`:

- `filterInventory`: show the filter bar in the player's survival inventory/crafting screen.
- `filterContainers`: show the filter bar in non-creative container screens such as chests, barrels, and shulker boxes.

The Cloth Config settings screen exposes both booleans in the existing general category. Runtime reads use the shared config instance returned by `FindMyItemsClient.config()`.

Creative inventory remains excluded regardless of either setting, including when the player is in a survival game mode.

### Tooltip-aware matching

The filter remains a visual dimming filter and does not move or modify items. A non-empty query matches when its case-insensitive text occurs in any of these searchable fields:

- item display/custom name;
- item registry ID/path;
- every line of the rendered item tooltip.

Tooltip matching includes enchantment names and levels, including the user-visible Roman numeral and Arabic-digit forms where the tooltip provides them. It also covers other tooltip attributes without requiring separate component-specific matching rules.

The tooltip is generated with the client player's current context and registry access. Empty stacks are ignored as before. Existing single-player-only behavior remains unchanged.

## Architecture

`InventorySearchController` classifies the screen before injecting the widget: the survival player inventory uses `filterInventory`, while all other eligible non-creative `AbstractContainerScreen` instances use `filterContainers`. The controller continues to skip creative inventory screens and inactive slots.

Matching is kept in a small helper within the search controller unless the implementation's tests demonstrate a clear need for a separate utility. It builds normalized searchable text from the display name, registry ID, and tooltip lines, then checks the query against that text. No serialized component data is used for display search.

## Testing

Add configuration tests verifying both defaults and persistence. Add client-side search tests for:

- each toggle enabling and suppressing only its intended screen category;
- creative inventory remaining excluded;
- matching an enchanted book by enchantment name;
- matching enchantment levels as shown in the tooltip;
- matching an item through another tooltip attribute;
- preserving existing display-name and registry-ID matching.

Run `./gradlew build` and headless `./gradlew runGameTest`. Do not run client game tests without first asking the user because they open a real client window.

## Scope and constraints

Keep the existing filter layout, dimming behavior, single-player boundary, and shared-config invariant. Do not change catalog search, item identity, retrieval, or multiplayer behavior. Preserve all item-conservation guarantees.
