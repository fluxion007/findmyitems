# Crafting Planner and Automation Design

## Goal

Rework the crafting search, planning, display, reachability, and execution paths into a single
verified release candidate. Preserve the existing single-player boundary, registry-backed item
identity, indexed-container model, and item-conservation guarantees.

The release includes correct root-only crafting search, word-aware fuzzy matching, lazy and
virtualized crafting-output browsing, cycle-safe recipe planning, legitimate vanilla reachability,
correct locate quantities, gather-only behavior, gather-and-craft automation, safe cancellation,
inventory simulation, stale-index recovery, automated and client tests, manual verification, and
local release preflight. Remote publication is explicitly out of scope until separately
approved by the user.

## Constraints and Decisions

- The implementation is one release candidate with phased commits and verification gates.
- Automation supports player-inventory crafting and vanilla crafting tables only. Furnaces,
  stonecutters, smithing, and other stations are not claimed as supported.
- The next patch version is inferred from the current `0.1.3` version during release preparation.
- The mod remains client-side and single-player only.
- Item identity remains item identifier plus registry-backed `StackKey.componentsJson`.
- Failed component encoding must never degrade to `{}`; any degraded representation must remain
  unique enough to prevent variant conflation.
- Every movement path must conserve items, including full inventories and creative mode.

## Architecture

Retain the existing container index, observation model, JSON store, and server-authoritative
retrieval boundary. Add focused immutable services rather than putting new behavior into one large
screen class.

### Search snapshot

Build an immutable search snapshot from indexed stack observations. It owns normalized translated
names, item identifiers, tooltip text, component fingerprints, token indexes, and deterministic
root-result ordering. It is rebuilt after recipe or language changes and refreshed when the index
revision changes. The catalog and ordinary container filter use the same document and matching rules.

### Recipe catalog

Build a reload-scoped catalog of survival-available crafting outputs, grouped recipe alternatives,
ingredient alternatives, batch sizes, graph edges, strongly connected components, and lower-bound
planning metadata. The catalog is used for lightweight root browsing and never solves every recipe
when the Crafting tab opens.

### Crafting planner

The planner is query-local and immutable from the caller's perspective. It consumes a selected root,
quantity, indexed inventory snapshot, recipe catalog, station policy, and cancellation token. A
candidate result returns its display tree, remaining stock, consumed stock, generated surplus,
crafting remainders, missing quantities, and lexicographic score.

Planner memoization includes item identity, requested quantity, component-aware inventory
fingerprint, recipe graph generation, station policy, and active cycle restrictions. Quantities use
`long` with overflow checks. A path guard and SCC classification prevent missing items from being
invented through reversible conversions. A conversion may consume a physically present source in
the same SCC, but it may not bootstrap a missing source. Only the selected candidate commits its
inventory state, so sibling branches cannot double-spend stock.

### Plan display

Flatten plans using explicit depth and stable root/node identities. Every independent root starts at
depth zero. Rows carry required, indexed, reachable, missing, status, parent, and action data. The
display model is independent from Minecraft rendering and execution transport.

### Execution

Use a client tick-driven state machine with one active operation. Stages cover preflight, source
opening, screen wait and validation, exact transfer, closing, inventory-grid crafting, workstation
opening, workstation validation, recipe placement, output collection, completion, cancellation,
and failure. Each stage has a timeout, generation ID, preconditions, cancellation handling, and a
failure reason.

The executor uses normal vanilla interaction and screen-handler slot actions. It never fabricates a
remote screen or mutates a remote inventory directly. A transfer journal supports partial-progress
reporting; automatic rollback is not attempted after a successful transfer.

## Search Behavior

Crafting browse results contain only top-level craftable output identities. Ingredient names never
make a parent output a root result.

Normalize case, repeated whitespace, leading and trailing whitespace, Unicode consistently, and
translated display names after language reload. Rank candidates in this order:

1. Exact normalized full-name match
2. Complete-word match
3. Ordered multi-token match
4. Word-prefix match
5. General substring match
6. Bounded typo/fuzzy match

Search all valid roots before applying the display limit. Deterministic ties use match category,
score, translated display name, item identifier, and component fingerprint. Fuzzy edit distance is
used only against a reduced candidate set and not against every item on every frame. Rapid typing
uses generation-based invalidation or equivalent debouncing.

## Crafting Planning Behavior

For each requested item, the planner consumes matching existing stock first. If a shortfall remains,
it detects path re-entry, evaluates supported recipe alternatives, computes craft count from output
batch size, aggregates identical immediate ingredients, solves children against an isolated inventory
snapshot, tracks consumed stock and generated surplus, and scores the candidate.

The score prioritizes unavailable or missing quantity, distinct missing kinds, unreachable indexed
stock, source openings, transfers, craft operations, station changes, reversible-conversion penalty,
and tree depth. Protected or unusual component-bearing items are not automatically consumed as
generic substitutes unless the selected recipe explicitly requires that exact identity.

## Catalog UI

The catalog uses one authoritative viewport rectangle for row layout, clipping, hit-testing, and
scroll limits. Crafting browse rows render only visible entries plus minimal overscan. Names, icons,
scores, and plans are not rebuilt during row rendering; tooltips are calculated only for the hovered
visible row.

Selection and hover are stable output identities, not list indexes. Search changes clear stale hover
and clear a selected plan when its root disappears. Index-only refreshes preserve scroll when query,
view, and layout identity are unchanged. The title is `Find My Items`, and status text consistently
distinguishes in-inventory, known storage, reachable, unavailable, missing, gather-only, and
gather-and-craft states.

A selected top-level crafting result owns the single primary action. Recursive child rows do not each
offer full-plan execution. Locate is displayed only when indexed quantity is positive and may show
total, reachable, and unavailable portions without implying automatic access to unavailable stock.

## Reachability and Safety

The shared reachability service validates dimension, loaded chunk, expected block identity,
interactable state, vanilla interaction range, a visible interaction point on the target shape, and
the resulting expected screen handler. Distance is an early rejection or configured upper bound,
never proof of reachability. Sampled valid points allow visible chests through open doorways while
rejecting containers and crafting tables behind full walls.

Before any movement, simulate current inventory stacks, partial-stack capacity, stack limits,
source transfers, intermediate outputs, batch surplus, remainders, buckets or bottles, catalysts,
final output, and component incompatibility. Refuse unsafe gather-and-craft before movement unless
the user explicitly selected gather-only.

Every source is advisory until opened. Revalidate its block, handler, actual slots, expected item
identity, component key, and remaining plan. Reconcile the index after transfers. If a source is
stale, replan or fail safely without taking a different item merely because it occupies an old slot.
Cancel on screen closure, manual clicks, selection or query changes, movement out of reach,
dimension changes, death, source mutation, target changes, inventory-full conditions, or another
active automation.

## Testing and Verification

Add pure JUnit coverage for normalization, token matching, ranking, root filtering, recipe graph and
SCC construction, memoized planning, shared-stock allocation, batch surplus, inventory simulation,
display flattening, selection identity, and viewport math.

Add headless GameTests for real container reachability, obstruction, stale sources, double chests,
component variants, nested storage, full and creative inventories, conservation, reachable crafting
tables, and missing workstations. Add client GameTests for lazy crafting browse loading, search and
selection behavior, scroll and clipping, locate visibility, gather-only and gather-and-craft status,
and cancellation. Run the real client GameTests and a documented fixture world manually after user
approval to open the client window.

The baseline and final gates are serial `./gradlew clean test`, `./gradlew check`, `./gradlew build`,
`./gradlew runGameTest`, client GameTests, `git diff --check`, artifact inspection, and a Modrinth
dry run. A real Modrinth publish, GitHub push, tag, or release is not performed without explicit
user approval after the verified release preflight.

## Release Documentation

Update the changelog, version metadata, mod title/metadata, README behavior and limitations, and
publishing notes as applicable. Changelog text must accurately state crafting search, word-aware
matching, cycle-safe planning, lazy and virtualized browsing, legitimate line-of-sight reachability,
gather-only behavior, gather-and-craft automation, inventory safety, stale-index handling, and UI
fixes without claiming unsupported recipe stations.
