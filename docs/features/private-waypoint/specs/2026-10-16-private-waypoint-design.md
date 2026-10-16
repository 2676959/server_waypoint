# Private and local waypoint design

Status: feature behavior agreed during brainstorming on 2026-10-16. This document records that
design and the implementation defaults below; no implementation or runtime validation is claimed.
Branch: `feature/private-waypoint`.

## 1. Intent and agreed scope

Server Waypoint should serve two audiences: players who can only use server commands, and players
who want a complete client-side waypoint mod on any server.

| Collection | Authority and storage | Access |
| --- | --- | --- |
| Shared | Existing server waypoint store | Existing shared permissions |
| Private | Separate server collection for each player UUID | Owning player |
| Local | Client storage for an account and world profile | Local client |

The three collections remain independent. Private ownership applies to the entire collection,
including its lists, rather than a privacy flag on individual waypoints in shared lists.
Two players may each have `Homes / Base`; the same identifiers may also exist in all three
collections without collisions.

The following decisions came directly from the brainstorming discussion:

- Keep collections separate and support deliberate copying.
- Use `/wp private …` and each player's own private lists.
- Allow ordinary players to create, manage, and navigate to their private waypoints; private
  teleport requires a dedicated permission, defaulting to operators.
- Keep private data on its owning server, outside cross-server catalogs.
- Do not add dedicated commands to administer another player's private collection.
- Default server quotas to 1,000 waypoints and 100 lists per player, configurable across dimensions.
- Provide Local as an independent feature using this mod's own manager and world markers.
  Do not copy Local waypoints into Xaero's Minimap or VoxelMap.
- Support automatic identity selection plus manual local world profiles.
- Use an `IconDropdownMenu` with Shared, Private, Local, and Combined views; label sources in
  Combined.
- Default creation from Combined to Local.
- Support a configurable teleport command for Local; use existing markers without adding a
  separate client navigation HUD.

Implementation defaults established by this design are Combined as the initial manager view,
account-isolated local profiles, per-profile teleport configuration, the quota/restore semantics
below, and the exact identity and routing contracts. They are not claims about existing behavior.

Out of scope: automatic mirroring, cross-server private access, other-player administration
commands, local waypoint export to other map mods, private map-mod upload/download extensions,
a new client navigation HUD, and compatibility shims or migration code.

## 2. Collection identity and ownership

Reuse `SimpleWaypoint`, `WaypointList`, existing property patches, and the file manager's mutation
machinery. Keep ownership outside waypoint properties; the existing `global` property remains a
marker visibility property and does not mean public access.

Introduce a backend-neutral collection identity in `common`:

- `WaypointScope`: `SHARED` or `PRIVATE`, the only collection scopes accepted by server messages.
- `WaypointCollectionKey`: scope plus an owner UUID for Private; Shared has no owner.
- Factory methods `shared()` and `privateFor(UUID)` enforce those combinations.

A server waypoint identity is collection key + dimension + list identifier + waypoint identifier.
Client-local selections additionally carry their world-profile identity. List keys, collapsed
nodes, selected rows, pending edits, revision recovery, restore tokens, navigation targets, and
renderer actions must retain the appropriate collection identity.

Clients send a server scope, never an owner UUID. The server derives Private's owner from the
authenticated executing player/connection. There is no server-side `LOCAL` scope and no
client-supplied target player selector.

Privacy means isolation from other players' ordinary browsing and synchronization. Existing
privileged `/execute as` semantics still select the executing player's identity and preserve the
existing commander feedback behavior. The feature does not promise secrecy from server operators
with filesystem access or permission to impersonate players.

## 3. Server-private storage and operations

### Storage and lifecycle

Add `PrivateWaypointStore` in `common` as an owner of separate per-UUID file managers. Store
dedicated-server files under `<config-directory>/private-waypoints/<player-uuid>/`, using the
existing dimension file format within that directory. For integrated servers use
`<world-save>/server_waypoint/private-waypoints/<player-uuid>/`.

Never insert private managers into the shared server's `fileManagerMap`. Existing world snapshot,
upload, broadcast, and catalog-export code must continue to read only the shared store.

Load owner data lazily, persist mutations through the existing file I/O rules, and release loaded
owners after disconnect when no operation is in flight. Shutdown flushes and closes loaded stores.
Coordinate eviction with owner operations; an operation must not retain a detached manager after
eviction. A read/parse failure does not become an empty collection that can overwrite the file.

Serialize quota-bearing mutations per owner, including across dimensions, then use existing
dimension mutation lanes. Hold the owner gate through validation, mutation, persistence attempt,
and outcome capture. Different owners remain independent. A failed save must report the persistence
failure according to the existing mutation contract; it must not claim that the data is safely
stored.

### Commands and feedback

`/wp` retains its shared meaning. Add `/wp private` as a private menu, with:

| Command | Behavior |
| --- | --- |
| `/wp private add …` | Existing add/list-creation grammar applied to the owner's collection |
| `/wp private list …` | Owner-only lists, dimensions, filters, sorting, and pagination |
| `/wp private details list\|waypoint …` | Private property details and action links |
| `/wp private edit list\|waypoint …` | Existing supported property edits within Private |
| `/wp private remove …` | Remove a private waypoint or an empty private list |
| `/wp private restore <token>` | Restore the owner's removed private waypoint |
| `/wp private navigate …` | Existing server navigation methods targeting Private |
| `/wp private tp …` | Server-authoritative teleport to a private waypoint |
| `/wp private help [<topic>]` | Private command help and examples |

Use the existing argument grammar after the prefix, including quoting, current-dimension defaults,
icon validation, list metadata, and waypoint metadata. The private menu/help/details links must keep
the private prefix; suggestions must never use another owner's collection. Expose a Private entry
from the ordinary menu.

These operations need an executing player. A bare console/RCON/command-block invocation reports
that requirement instead of selecting an owner. Do not introduce target-player administration.
Private changes send normal feedback to their executor and compatible data only to their owner;
they do not generate shared player chat announcements.

Use dedicated nodes `server_waypoint.command.private.<action>`:

| Action | Default permission level | Also governs |
| --- | --- | --- |
| `list` | 0 | Private browsing, details, and client snapshots |
| `add` | 0 | List creation, restore, and copying into Private |
| `edit` | 0 | Private list and waypoint property edits |
| `remove` | 0 | Private waypoint and empty-list removal |
| `navigate` | 0 | Private navigation |
| `tp` | 2 | Private teleport |

Use the existing loader permission adapters and Paper permission descriptor conventions. Shared
permissions do not grant Private operations on someone else's data. Menu/help visibility follows
the executing player's permissions. Recheck permission when an operation executes.

### Quotas

Add `privateWaypoints.maxWaypointsPerPlayer = 1000` and
`privateWaypoints.maxListsPerPlayer = 100` to server configuration. Zero means unlimited;
negative values are invalid configuration. Apply valid limit changes through the existing reload
path.

Counts cover all dimensions for one owner and include empty lists. Automatic list creation and
waypoint creation are one quota-checked operation. Exceeding either quota must leave both counts
unchanged. Restore and copy additions use the same checks. Existing data is retained after limits
are reduced; metadata edits, renames, and deletions remain allowed while over quota.

Restore keeps the existing ten-minute, single-use behavior, with owner and collection included in
the token's identity. Wrong-owner/wrong-collection requests fail without consuming the token.
Quota or name conflicts do not consume a valid token.

### Navigation

Reuse the existing per-player navigation service and handlers; do not create a second simultaneous
server navigation session. A player may target Shared or their own Private collection.
Retargeting changes that one session.

Extend `NavigationTarget`, its refresh/list-rename identities, persisted session data, and
resolution with collection identity. A private target is resolved only from that player's store.
Renaming or editing one private target cannot refresh another player's target or an identically
named shared target. Removal stops the affected navigation instead of resolving another collection.

Navigation panels, Resume/Change target links, and item/display metadata preserve target scope.
Existing per-player display delivery remains in use. Bump the navigation session format when
introducing collection identity; do not add a reader for the former format.

## 4. Server/client synchronization

Extend waypoint snapshot/update, list-update, modification, edit-request, edit-result, and
revision-request messages/codecs with `WaypointScope`. Every relevant constructor and producer
must supply it explicitly. Owner UUIDs stay server-side. Add an owner-directed
`WaypointCollectionStateMessage(WaypointScope scope, boolean readable)` so authorization denial
is distinguishable from an empty successful snapshot. Readable state permits synchronization;
the collection becomes ready only when its current snapshot is applied.

- Shared snapshots and broadcasts use Shared only.
- Private requests are answered from the requesting player's collection, after permission checks.
- Private mutations use owner-directed delivery, never the shared broadcast recipient path.
- Keep revisions, tombstones, pending edits, and recovery state separate by collection.
- An empty Private snapshot clears only the requesting player's Private cache.
- Recheck private read permission before every private snapshot or incremental send. Do not
  interpret authorization failure as a successful empty snapshot. On an observed denial, send
  unreadable collection state, clear/hide private cached presentation, and report it distinctly.
- Reject invalid scopes and Private upload payloads. Existing map-mod upload/download behavior
  continues to operate on Shared only.
- Snapshot delivery and edit requests require a compatible handshake. Private commands themselves
  work for clients without the mod.

Increment the Minecraft client/backend protocol from the inspected value 2 to 3, update all
channel checks and codec tests, and use the eventual feature release version for the compatibility
message when that version is assigned. Do not invent a release version in this documentation.
This does not change the cross-server application protocol, since Private is not exported.

Maintain a distinct Private client cache, isolated by account, server address, and server identity;
never share it with Local or the existing Shared cache. Render Private only after the current
connection authorizes/synchronizes it. Disconnect, backend transfer, or account change discards
current private presentation and pending operations before rebinding. A late result from an old
connection must not update the new collection.

## 5. Independent client-local storage

### Profiles and automatic selection

Implement `LocalWaypointProfiles` in `mods`, independent of handshake readiness and the server
cache. Multiplayer storage lives under
`<game-root>/server_waypoint/local/<account-uuid>/<address-key>/`, with a profile index and
stable UUID directories for each profile's waypoint files. Derive `address-key` from the full
normalized host and effective port with a filesystem-safe digest, not lossy character replacement.

A profile contains a display name, its waypoint store, and its teleport command. Display names do
not become directory names. Singleplayer local data lives under
`<world-save>/server_waypoint/client-local/<account-uuid>/`, so a world's profiles follow that save.

Provide create, rename, select, and delete controls. A new profile starts empty. Deletion needs the
existing confirmation dialog, discards that profile's bindings, and creates/selects Default if no
profiles remain. It must not remove Shared/Private data.

Selection rules:

1. On join, select the address's saved fallback profile, creating Default on first use.
2. When a valid server identity arrives, select its saved profile binding if present.
3. For a new server identity, bind the current fallback profile if it has no backend identity
   binding; otherwise create and bind an empty profile for the new backend.
4. A manual selection wins for the rest of the current session, even if a handshake arrives later.
   Save it as the fallback and, when an identity is known, as that identity's binding.
5. Without a server identity, backend changes behind one address require manual selection.
   Do not infer backend identity from dimension names or coordinates.

Profile switching saves the outgoing store before rebinding, clears its render references, and
refreshes manager queries. If saving fails, keep the outgoing profile active and show the error.
Late identity discovery does not move or merge files; waypoints remain in their originating profile.
Expose the selected profile name so a successful automatic switch is visible.

On proxy transfers, flush Local as well as server caches even if the transport connection does not
disconnect. Keep local data functional on unsupported and protocol-incompatible servers. Save local
mutations promptly and on orderly leave/shutdown; a missing handshake is never a reason to skip saving.

### Local operations and teleport

Reuse waypoint properties, form validation, file mutation, search, sorting, and marker rendering.
Local create/edit/remove writes directly to its profile without sending waypoint commands or custom
payloads to the server. There is no new client command grammar in this version; existing manager
entry points provide access.

Teleport is an explicit action that sends the selected profile's configured ordinary command.
Default:

    /execute in {dimension} run tp @s {x} {y} {z} {yaw} ~

Supported named substitutions are `{x}`, `{y}`, `{z}` (stored integer block coordinates),
`{dimension}` (exact dimension identifier), and `{yaw}` (stored normalized yaw). These named
substitutions are unrelated to Java MessageFormat translation placeholders.

Accept a leading slash in settings and remove it before submission. Reject an empty command,
unknown/unbalanced placeholders, line breaks/control characters, and a command exceeding the
supported client's command-length limit. Expand numbers without locale formatting. Do not add
expression evaluation or multiple-command execution.

For a target outside the current dimension, require the template to contain `{dimension}`;
otherwise disable the action with an explanation. Recheck profile, target, and connection at
submission. A successful send means the command was submitted, not that teleport succeeded.
Server permissions, cheats, and command availability remain authoritative.

## 6. Manager, forms, and rendering

### Collection views

Reuse the manager's existing nested `IconDropdownMenu` and popup/input contracts for a selector
with Shared, Private, Local, and Combined choices. Keep all four identifiable; unavailable
server-only views show their reason. Local and Combined remain usable without server support.

Combined is the default for a new configuration. Remember the chosen collection view in client
settings. Treat the existing current-server/remote browser toggle separately: relabel its
current-server side where necessary to avoid confusing it with the Local storage collection.
Hide the collection selector while browsing Remote. Combined contains current-server Shared,
current-player Private, and active-profile Local only, never remote catalogs.

In Combined, show text source labels on waypoint rows, list headers, and selected details.
Group lists by collection plus list identity; never merge equal names. Flat mode sorts across
collections using existing sort modes, with collection order Shared, Private, Local as the final
source tie-breaker. Preserve current dimension/distance rules. Search suggestions and collapsed
state must retain collection provenance.

Track readiness per collection. A loading Shared/Private source must not replace the whole manager
with a loading/unsupported screen while Local is ready. Combined shows available entries and a
compact status for unavailable sources. Query dimensions are the union of the selected collections'
available dimensions.

### Creation, editing, and copying

Add a destination field to the ordinary add form. In a single-collection view default it to that
collection; in Combined default to Local. Offer only usable destinations, and identify disabled
destinations with their reason. Revalidate destination availability and permissions on Save.

Editing and deletion always use the selected entry's collection, not the current dropdown value.
Capture collection, profile/session generation, identifiers, and expected revision when opening a
form. A profile or connection change invalidates the destination; retain entered text and require
an explicit reopening/retargeting rather than saving into a different collection.

Expose Copy from a selected waypoint. It opens a detached, prefilled add form with an explicit
destination; its initial destination follows the same view defaults. Copy is available among the
three current collections subject to source read and destination add permission. Preserve the
complete supported waypoint metadata, validate it at the destination, and leave the source intact.
Conflicting identifiers require renaming; never overwrite. Cancelling has no effect.

Route operations through explicit client collection access rather than direct singleton lookups.
Shared/Private edits retain server acknowledgement and revision checks. Local edits complete from
the profile's mutation/persistence outcome and never wait for a network reply.

### World markers and UI contracts

Render enabled waypoints from all available current collections, regardless of manager view.
Feed the existing renderer a composed scene of distinct list/waypoint instances, retaining source
identity for actions and visibility state. Equal names or coordinates do not merge entries.
Refresh only the affected collection where possible; a Shared resync cannot clear Local markers.

Reuse existing visibility controls, distance behavior, icon rendering, and theme roles. No
Xaero/VoxelMap dependency is needed for Local. Do not route Local or Private changes into existing
Shared map-sync events. Preserve existing Shared map integration behavior.

Do not repurpose renderer fields named `local` or the waypoint's `global` flag to encode collection
ownership; those fields already describe marker visibility. Collection provenance is separate.

Follow the GUI guide for component reuse, registration, layout, separate popup rendering, hover
suppression, keyboard focus, narration, Escape, and constrained viewports. Source labels must remain
readable without depending only on color. Update the guide alongside any implemented GUI API change.

## 7. Delivery and acceptance

Implement the [plan](../plans/2026-10-16-private-waypoint.md) in dependency order. The
[validation checklist](../validation/2026-10-16-private-waypoint-checklist.md) records the required
proof and remains unchecked until execution produces evidence.

Keep backend-neutral ownership/storage in `common`, platform adapters in `mods`/`paper`, and
client profiles/UI/rendering in `mods`. No new Private functionality belongs in `cross-server`,
`proxy-common`, or `velocity`.

Use Java 17 compatible language features, GSON, four-space indentation, existing Stonecutter
version branches, and Kotlin DSL if build configuration changes. Common Adventure translations use
zero-based MessageFormat placeholders and escaped apostrophes. Update all existing server/client
locale files where new keys apply; translation coverage tests do not prove fluent-language quality.

Acceptance requires vanilla-client private command/navigation/permission checks, two-player privacy
checks, independent Local operation on an unmodified server, all four manager views and copying,
and supported platform builds. Builds and unit tests alone do not prove rendering, physical input,
multiplayer isolation, or successful teleport arrival.

No commit, push, release, or publication is authorized by this documentation task.
