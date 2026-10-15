# Remote catalog queries and suggestions (step 11)

> Updated on 2026-10-01 for the [command feedback redesign](../../command-feedback/specs/2026-10-01-command-feedback-design.md):
> `/wp remote` is the server picker, `/wp remote servers` and list details are removed, and remote
> lists use the local views and line budgets. The verification record below describes Step 11 as
> it shipped.

Step 11 registers `/wp remote servers`, `/wp remote list` and `/wp help remote` through
`CoreWaypointCommand`, shared by the mod and Paper command adapters. Feedback uses ordinary
Adventure server chat; it requires neither client installation nor a custom network payload.
The commands operate on an attached local cache. Starting transport from platform lifecycle hooks
remains a later integration step. Until then, an unattached server reports no cached remote servers.

## Store and query ownership

`RemoteCatalogStore` is a read-only facade over the backend's bounded `CatalogIndex`. It exposes
only an immutable snapshot map, with no ingestion, file-manager mutation or network-operation API.
All Step-10 per-server/global byte budgets, identity ceilings, revision checks and expiry rules
remain in the transport-owned index. The facade does not duplicate its catalogs.

`BackendAgent.remoteCatalogStore()` exposes that facade. A platform owner can attach it with
`WaypointServerCore.setRemoteCatalogStore(agent.remoteCatalogStore())`; commands resolve the current
attachment on each invocation. The initial attachment is an empty bounded store. Backend stop
clears the underlying index. A replacement backend agent must have its new store attached by the
platform owner. Nothing inserts remote data into `WaypointFilesManagerCore` or local waypoint files.

`RemoteCatalogQuery` captures one immutable cache snapshot per command as servers holding lists in
the shape the local screens use; unreachable and no-access servers keep no lists. The capture is a
fresh copy: nothing adds it to `WaypointFilesManagerCore` or writes it to local waypoint files.
Name/keyword filtering and sorting run through the local `WaypointQueryEngine.queryLists`, including
fuzzy matching. There is no destination-world lookup.

## Command grammar

```text
/wp remote [page <number>]
/wp help remote
/wp remote list [<server> [<dimension> [<list>]]] [list options]
/wp remote details <server> <dimension> <list> <waypoint>
```

Omitted scopes select all cached entries within the supplied hierarchy. A dimension requires a
server and a list requires both. Server and list selectors use exact quoted/escaped string arguments.
Dimension selectors use the same native namespaced identifier parser as waypoint item icons: `namespace:path` without quotes,
with `minecraft` as the default namespace. Remote dimensions need not exist on the executing backend.
Dimension suggestions match namespaces and paths from the selected server's bounded local catalog.
String names equal to option words, such as `search`, must be quoted; an empty list identity is `""`.

`ListCommandOptions` is the shared local/remote Brigadier option builder. It preserves the existing
local grammar, including its reserved-list handling. Remote options are:

```text
search <query>
sort <default|name|distance|color> [order <ascending|descending>]
limit <1-100>
view <lists|tree|flat>
page <number>
```

The canonical order is search, sort/order, limit, view, page; the earlier order (page before limit
and view) and a trailing search are still accepted. Default sorting has no order modifier.
`sort distance` is parsed but returns a localized error: coordinates from another server are never
compared with the executor's position, even if dimension names match. Name and color sorting work
in every view. Default sorting follows exact identities, because wire catalogs do not preserve a
local file's insertion order.

Pagination uses the local line budgets with the configured page limit `L`: the server picker, all
servers, a server's dimensions and lists, the tree view and the one-line-per-list view hold `L + 5`
lines; flat views, lists and search results hold `L` rows. A heading continued from the previous
page repeats with "(continued)". Out-of-range pages return the local page error. Generated links
keep the exact scope, filter, sort/order, page size and view in the canonical order, and quote
option-like identities at every scope level.

For example:

```text
/wp remote list survival minecraft:overworld "search" search village sort name limit 10 view flat page 2
```

## Availability, suggestions and feedback

A coloured dot shows every server's state: green available, yellow stale, red unreachable, dark gray
no access. Its tooltip names the state, and plain-text viewers read the state as a word. Stale
snapshots remain advisory, and their teleport links stay off until they refresh.
Unavailable/unauthorized scopes do not render retained
coordinates. Successfully published empty scopes have their own message. Missing server,
dimension and list identities produce separate errors; a filter with no matches is distinct from
an empty publication or absent cache. Browsing does not authorize teleportation.

Server/dimension/list suggestions read only `RemoteCatalogStore.snapshot()`. They neither request
a coordinator refresh nor access local game worlds. Stale identities can be suggested while their
snapshot is retained. Expired/unavailable scopes have no dimension/list suggestions; unauthorized
servers are excluded from suggestions. Each result set is sorted and limited to 100 suggestions,
with escaped values at most 256 characters long. Display labels never substitute for identities.

Presentation treats remote labels as literal text, not formatting markup that could inject events.
Each label is bounded to 256 UTF-16 code units in chat; exact keys remain intact in the query model.
Generated read-only actions are attached only when their complete command fits the conservative
256-character UI budget. No truncated command is sent. Server, scope and waypoint text does not
inherit a neighboring button's click action. Commands expose no edit, delete, download, navigation
or teleport controls at Step 11; the list presentation extension below adds remote teleport controls.

Help and feedback keys are included in all six bundled locales: English, Spanish, Hebrew,
Simplified Chinese, Traditional Chinese and Hong Kong Chinese. [Step 12](cross-server-authorization.md)
now gates browsing, help and catalog identity suggestions with the dedicated remote list permission.

## Verification

On 2026-09-08, common/proxy tests and the Velocity build pass: 452 common tests and 67 proxy tests,
with zero failures/errors/skips. Eight new cases cover:

- Shared command-root registration, live store attachment and unchanged local lists.
- Exact reserved, quoted, escaped and empty identities, with suggestions generated offline.
- Combined list options and executable pagination links preserving source identity and options.
- Shared fuzzy/name/color behavior and duplicate local names on different remote servers.
- Stale, unavailable and explicit-empty feedback, expiry and scoped missing-identity errors.
- Distance rejection, bounded pagination, immutable results and unauthorized data redaction.
- Server paging, help and read-only Adventure actions without event inheritance.

Existing local list, search, sorting, page-link and help tests pass. Main-help assertions now include
the remote topic. A pre-existing reconnect test was corrected to use one immutable presence
snapshot instead of reading a changing status twice. No native Minecraft or Velocity instance was
booted, and the full backend artifact matrix was not run. Platform transport startup and live
vanilla-client validation remain later integration gates.

## Step-15 command extension

`/wp remote tp <server> <dimension> <list> <waypoint>` now shares the exact cache-only identity
suggestions, with waypoint completion and separate teleport permission checks. List results and
pagination remain read-only. See [source teleport initiation](cross-server-source-teleport.md);
this does not enable live platform startup.

## Step-16 integration

The services now have live coordinator/backend lifecycle and Velocity transfer wiring. See
[the runtime contract and current validation](cross-server-velocity-runtime.md). Earlier step-specific
verification above describes its historical boundary; client/GUI and full release hardening remain.

## Remote list presentation parity

`/wp remote list` now uses the local list button builders for search, tree/flat view, name/color/default
sorting, ascending/descending order, and pagination. Selected/disabled controls have the same
colors and decorations as `/wp list`. Distance sorting remains unsupported. Flat output places
server/dimension/list context and the waypoint on one line. Tree output has colored dimension
headings, bold list headings, and clickable server/dimension/list scopes. Scope links reset to page 1
and preserve the search, sorting, page size, and view; view changes preserve the current page.
Search suggestions reset the page and retain the page size, sorting, and view. Sort/order changes
reset to page 1. Pagination continues to count remote result rows, including empty/unavailable scopes.

Waypoint rows show colored, bold initials and a white display label. Hovering the label shows the
cached description and coordinates, including the local list's Overworld/Nether coordinate conversion.
Exact identities remain separate from display labels. Remote presentation strings remain literal text.
Initials execute `/wp remote tp` only for an AVAILABLE entry and a source with teleport permission.
The teleport command rechecks permissions and current cache state when clicked.

The `⋯` buttons open read-only cached details:

```text
/wp remote details <server> <dimension> <list> [<waypoint>]
```

These selectors share the list command's cache-only suggestions, native dimension identifiers,
quoted string identities and remote list permission. List details include the identifier, display name,
dimension, and waypoint count.
Waypoint details additionally show the source list, initials, coordinates, color, yaw, visibility,
keywords, and description. Both provide an Open List action. AVAILABLE waypoint details offer remote
teleport when permitted. Stale details remain visibly advisory and cannot teleport; unavailable or
unauthorized details never expose retained data. Editing, removal, and local navigation are not
remote catalog operations.

All generated actions retain the 256-character command limit and use neutral parent components so
clicks and bold styling do not spill onto neighboring text. `RemoteWaypointCommandTest` covers
control command round trips, option retention, exact detail/teleport identity, permission revocation,
stale/expired data, oversized commands, and effective click-event inheritance.
