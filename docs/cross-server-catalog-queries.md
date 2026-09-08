# Remote catalog queries and suggestions (step 11)

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

`RemoteCatalogQuery` consumes one immutable capture per query and produces immutable presentation
rows. Each row preserves exact server/dimension/list/waypoint identity separately from display
labels. Name/keyword filtering calls the existing `WaypointQueryEngine` matching logic, including
fuzzy matching. Sorting reuses `WaypointSorting` name comparison and `ColorUtils` color ordering.
There is no conversion to mutable local waypoints and no destination-world lookup.

## Command grammar

```text
/wp remote
/wp help remote
/wp remote servers [page <number> [limit <1-100>]]
/wp remote list [<server> [<dimension> [<list>]]] [list options]
```

Omitted scopes select all cached entries within the supplied hierarchy. A dimension requires a
server and a list requires both. All three selectors use exact quoted/escaped string arguments;
remote dimensions need not exist on the executing backend. Names equal to option words, such as
`search`, must be quoted. Empty dimension/list identities are expressed as `""`.

`ListCommandOptions` is the shared local/remote Brigadier option builder. It preserves the existing
local grammar, including its reserved-list handling. Remote options are:

```text
search <query>
sort <default|name|distance|color> [order <ascending|descending>]
page <number>
limit <1-100>
view <tree|flat>
```

The canonical order is search, sort/order, page, limit, view; the existing trailing-search forms
are also shared. Default sorting has no order modifier. `sort distance` is parsed but returns a
localized error: coordinates from another server are never compared with the executor's position,
even if dimension names match. Name/color sorting works for grouped trees and flat rows; flat
default sorting uses name order. Default grouped order is deterministic by exact identities because
wire catalogs do not preserve a local file's insertion order.

Pagination defaults to the configured local page limit. Every waypoint or empty/unavailable scope
row occupies one result slot. A page contains only its own hierarchy headings; headings do not
consume additional slots. Out-of-range pages return the existing localized page error. Generated
page links retain the exact scope, filter, sort/order, page size and view. They share the existing
list-option formatter and quote option-like identities at every remote scope level.

For example:

```text
/wp remote list "survival" "minecraft:overworld" "search" search village sort name page 1 limit 10 view flat
```

## Availability, suggestions and feedback

Every displayed server is labeled AVAILABLE, STALE, UNAVAILABLE or UNAUTHORIZED. Stale snapshots
remain advisory and visibly marked. Unavailable/unauthorized scopes do not render retained
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
or teleport controls.

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
