# Private and Local Waypoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this
> plan task-by-task. Steps use checkbox syntax for tracking. Do not dispatch subagents unless the
> user explicitly chooses delegation.

**Goal:** Deliver owner-specific server-private waypoints and independent client-local waypoints
with explicit copying and four source-aware manager views.

**Architecture:** Separate owner/profile stores reuse the current waypoint models and mutation
machinery. Server operations carry an explicit server collection identity; client operations route
through source-specific access. The manager and renderer compose sources without merging their
underlying state.

**Tech Stack:** Java 17 compatible language features, GSON, Adventure/Brigadier, Minecraft GUI,
JUnit 5, Gradle Kotlin DSL, and Stonecutter across Fabric, Forge, NeoForge, and Paper.

**Spec:** [Private and local waypoint design](../specs/2026-10-16-private-waypoint-design.md).

Status: planned, not implemented. Prepared on 2026-10-16 against branch
`feature/private-waypoint`. Exact proposed class names below are implementation contracts,
not claims that those classes already exist.

## Global constraints

- Read the spec before implementing any task. Its behavior applies to every task.
- Java 17 compatible features only; GSON for JSON; four spaces, no tabs.
- Keep private backend logic in `common`, platform adapters in `mods`/`paper`, client-only
  storage and UI in `mods`. Do not extend cross-server catalogs for this feature.
- Preserve existing Shared behavior and all inactive Stonecutter branches. No backward-compatibility
  readers, shims, migrations, or automatic merging between collections.
- Inspect current status and targets before execution. At planning time, mods is active on
  `26.1.2-fabric`, Paper on `26.2-paper`, and the client/backend protocol is 2.
- Read `docs/tips/gui/local-guide.md` before GUI changes and update it with changed GUI APIs.
  Read `docs/tips/adventure-text.md` before implementing interactive feedback.
- Update applicable server and client locale files. AdventureTranslator patterns use `{0}`,
  `{1}`, etc.; literal apostrophes are doubled. Local teleport templates use their own named
  token parser.
- No commit or push unless the user requests it. Each task ends with a diff/review checkpoint,
  rather than a commit. This plan does not authorize product implementation by itself.

## Review focus

1. Equal identifiers across collections and players: assert ownership survives queries,
   navigation refresh, restore, rendering, and every mutation (Tasks 1-4 and 7).
2. Quota races across dimensions: exactly one competing addition wins the final slot; list
   auto-creation cannot leak an empty list after rejection (Task 2).
3. Proxy transfer and late handshake/results: outgoing stores are saved and old responses cannot
   mutate new profile/connection data; manual profile choice wins (Tasks 4-5).
4. Unsupported/incompatible servers: Local and Combined stay usable without a matching backend;
   server collection loading/failure never blocks Local (Tasks 5 and 7).
5. Submission after a form's collection/profile changes: retain draft data but reject an obsolete
   destination; never silently save or copy elsewhere (Tasks 6-7).

## Source map and proposed interfaces

Existing package paths below are relative to their project's `src/main/java/_959/server_waypoint/`.
Tests use the corresponding `src/test/java/_959/server_waypoint/` package.

| Area | Existing anchors | Proposed addition/responsibility |
| --- | --- | --- |
| Server collections | `common: core/WaypointServerCore.java`, `core/WaypointFilesManagerCore.java` | `core/collection/WaypointScope.java`, `WaypointCollectionKey.java`, `PrivateWaypointStore.java`: ownership, isolated stores, serialized owner operations |
| Commands and feedback | `common: command/CoreWaypointCommand.java`, `util/StringCommandBuilder.java`, `text/feedback/` | `command/WaypointCommandScope.java`: resolved collection, command prefix, permission selection, recipient policy; reuse command registration and feedback |
| Permissions/config | `common: command/permission/`, `config/Config.java`, `config/CommandPermission.java` | `config/PrivateWaypointConfig.java`: exact quota defaults and validation; add six private permission keys |
| Platform lifecycle | `mods: common/server/WaypointServerMod.java`; `paper: server/WaypointServerPlugin.java` | Bind owner stores to correct config/world root, shutdown and disconnect hooks; adapt permission providers and Paper descriptor |
| Navigation | `common: navigation/NavigationTarget.java`, `StoredNavigationSession.java`, `NavigationService.java`, `NavigationSessionCodec.java` | Scope/owner-aware targets, refresh keys and persisted resolution |
| Network | `common: core/network/C2SPacketHandler.java`, messages/data/codecs, `ProtocolVersion.java` | Explicit server scope, owner-directed delivery, scope-specific results and recovery |
| Client stores | `mods: common/client/WaypointClientMod.java`, `ClientSynchronizationTracker.java` | `common/client/ClientWaypointCollections.java`, `ClientWaypointSource.java`, `ClientWaypointSelection.java`: separate stores/readiness and source identity |
| Local profiles | `mods: common/client/` | `local/LocalWaypointProfiles.java`, `local/LocalTeleportCommand.java`: profile persistence/binding and template expansion |
| Client access/query | `mods: common/client/` | `ClientWaypointAccess.java`, `ClientWaypointQuery.java`: mutation routing and composition with provenance |
| GUI/render | `mods: common/client/gui/screens/WaypointManagerScreen.java`, add/edit/base forms; `gui/widgets/WaypointListWidget.java`, details; `render/OptimizedWaypointRenderer.java` | Reuse nested `IconDropdownMenu`, source labels, destination/profile controls, explicit actions and composed marker scenes |

Lock these cross-task contracts before their consumers are implemented:

- `WaypointScope { SHARED, PRIVATE }`.
- `WaypointCollectionKey(WaypointScope scope, @Nullable UUID ownerUuid)`, with
  `static WaypointCollectionKey shared()` and `static WaypointCollectionKey privateFor(UUID ownerUuid)`.
  The canonical constructor rejects all other scope/owner combinations.
- `WaypointCommandScope` resolves one invocation to one collection key and its file manager;
  it also selects the prefix, permissions, and delivery policy. No private handler falls back to
  the global shared manager.
- `ClientWaypointSource { SHARED, PRIVATE, LOCAL }`.
- `ClientWaypointSelection(ClientWaypointSource source, long generation, @Nullable UUID profileId,
  String dimensionName, String listIdentifier, @Nullable String waypointIdentifier)`.
  Local requires the active profile UUID; server selections do not contain it.
- `ClientWaypointCollections` owns access/readiness by source and a monotonically increasing
  generation on connection/account/profile rebinding. It exposes the active Local store without
  requiring a server handshake. Mutations validate the captured selection generation.
- `ClientWaypointAccess` owns add, list/property edit, remove, copy-draft, and teleport routing.
  Forms receive this access and a selection/destination instead of looking up singleton Shared data.
- `ClientWaypointQuery` composes existing per-store `WaypointQueryEngine` results into immutable
  rows/list groups retaining `ClientWaypointSelection`; it never builds a merged writable manager.
- `WaypointCollectionStateMessage(WaypointScope scope, boolean readable)` reports server collection
  authorization separately from data. A positive state permits synchronization but does not itself
  mark a client collection ready; a negative state clears that source's cached presentation.
- `LocalTeleportCommand.expand(String template, String dimensionName, SimpleWaypoint waypoint)`
  returns a slash-free command or a validation error; submission additionally checks the live
  selection, connection, dimension rule, and protocol command limit.

Preserve existing typed mutation/result objects where possible. Extend their context at the source
boundary rather than inventing unrelated parallel waypoint models.

## Task 1: Collection identity and scoped navigation

**Dependencies:** none.

**Files:** create `common/core/collection/WaypointScope.java` and
`WaypointCollectionKey.java`; modify navigation target/session/service/codec classes and the
callers constructing their identities. Extend navigation tests and add
`core/collection/WaypointCollectionKeyTest.java`.

**Interfaces:** produces the server identity contracts above. Navigation targets carry
`WaypointCollectionKey`; persisted private targets resolve using the current player's UUID and
an explicit collection-to-manager resolver. Task 1 tests supply in-memory managers; Task 2 wires
the private store, so this task does not depend on a store implementation that comes later.

- [ ] Add tests for valid Shared/Private keys and rejection of missing/mismatched owners.
- [ ] Add navigation tests with Shared, Alice Private, and Bob Private all named
  `minecraft:overworld / Homes / Base`: refreshing or renaming Alice's target changes only Alice's
  private session. Removal cannot resolve the shared or Bob target.
- [ ] Add codec round-trip tests retaining collection identity, reject mismatched-owner restoration,
  and reject the former navigation session format after the version increment.
- [ ] Run the focused tests and confirm the new assertions fail before implementation.
- [ ] Implement identity propagation through target creation, target/list refresh keys, stopped
  target links, persistence, and restore resolution. Keep one active navigation session per player.
- [ ] Run `rtk proxy ./gradlew :common:test --tests '*WaypointCollectionKeyTest' --tests '*Navigation*Test'`.
  Expected: successful build and passing assertions, including existing navigation behavior.
- [ ] Review the diff for unscoped target construction and unrelated changes.

## Task 2: Owner storage, quotas, and platform lifecycle

**Dependencies:** Task 1.

**Files:** create `common/core/collection/PrivateWaypointStore.java` and
`common/config/PrivateWaypointConfig.java`; attach them to `WaypointServerCore` and configuration.
Update mod/Paper server lifecycle roots and add `core/collection/PrivateWaypointStoreTest.java`.

**Interfaces:** produces per-owner file access and owner-serialized mutation entry points.
Callers supply an authenticated UUID; no public operation takes an arbitrary filesystem directory
derived from client data.

- [ ] Test distinct owners and dimensions round-trip into the spec's dedicated/integrated roots.
  Assert shared world snapshots and the shared manager map contain no private lists.
- [ ] Test defaults 1,000/100, zero unlimited, negative configuration rejection, and valid reload.
  With a two-waypoint quota and one existing entry, two concurrent additions in different
  dimensions must leave exactly two entries and one rejection.
- [ ] Test list quotas including empty lists and automatic list creation: a rejected add creates
  neither list nor waypoint. Reduced limits preserve data and permit rename/edit/delete.
- [ ] Test disconnect during a mutation, shutdown flushing, and failed reads/saves. Read failures
  cannot expose an empty writable replacement; save failures cannot be reported as durable success.
- [ ] Run the new tests, observe failures, then implement isolated managers with an owner gate held
  before existing dimension lanes. Keep load/eviction/persistence under the same lifecycle contract.
- [ ] Run `rtk proxy ./gradlew :common:test --tests '*PrivateWaypointStoreTest' --tests '*WaypointFilesManagerConcurrencyTest'`.
  Expected: passing isolation, quota concurrency, and existing mutation tests.
- [ ] Review storage paths and all lifecycle hooks before proceeding.

## Task 3: Private commands, permissions, and feedback

**Dependencies:** Tasks 1-2.

**Files:** add `common/command/WaypointCommandScope.java`; adapt `CoreWaypointCommand`,
`StringCommandBuilder`, feedback builders and restore context. Extend permission keys/config,
Fabric/Forge/NeoForge/Paper providers and `paper/src/main/resources/paper-plugin.yml`.
Add `command/CoreWaypointCommandPrivateTest.java` and extend command-builder/restore tests.

**Interfaces:** produces `/wp private` and its spec-defined command subtree. Shared remains the
ordinary `/wp` scope. Existing command syntax builders take explicit server scope where relevant.

- [ ] Add tests for private menu/help, list creation, full waypoint properties, list/waypoint
  details/edit/remove, restore, navigation, and teleport using the same identifiers in all stores.
  Verify every private action/suggestion link retains its prefix.
- [ ] Assert ordinary players get list/add/edit/remove/navigate; default private teleport is denied
  until granted. Assert existing shared grants do not authorize another owner's private access.
- [ ] Assert no-player sources fail without touching storage, privileged execute-as follows the
  executing player's identity and existing commander feedback, and private changes have no shared
  player broadcast.
- [ ] Test restore's ten-minute expiry, single-use behavior, wrong owner/collection, name conflict,
  and quota rejection. Failed restore attempts must not consume a valid token.
- [ ] Run the focused tests to establish failures. Parameterize the existing command registration,
  query lookup, permission selection, and feedback context rather than duplicating the entire
  `CoreWaypointCommand`. Resolve one scope at invocation and carry it through asynchronous work.
- [ ] Add all applicable common translations, with MessageFormat placeholder consistency.
- [ ] Run `rtk proxy ./gradlew :common:test --tests '*CoreWaypointCommand*Test' --tests '*StringCommandBuilderTest' --tests '*WaypointRestoreRegistryTest' --tests '*Platform*Permission*Test'`.
  Expected: passing scoped command and existing permission contracts.
- [ ] Review generated links, source resolution, private persistence outcomes, and permission
  descriptors; no dedicated other-player administration or private upload/download extension.

## Task 4: Owner-only protocol and separate client server caches

**Dependencies:** Tasks 1-3.

**Files:** update `common/ProtocolVersion.java`, waypoint messages/data/codecs and
`C2SPacketHandler`; add the collection-state message/codec to the existing message registry;
update client message dispatch and synchronization tracking. Add
`ClientWaypointCollections`, `ClientWaypointSource`, and `ClientWaypointSelection` in
`mods/common/client/`. Extend `C2SPacketHandlerTest`, protocol/codec tests, and add
`mods/ClientWaypointCollectionsTest.java`.

**Interfaces:** produces explicit `WaypointScope` on the existing waypoint wire messages and
separate Shared/Private client stores, readiness, revisions, and selection generations.
Protocol version becomes 3; all constructors/producers supply a scope.

- [ ] Test snapshot, update/tombstone, list-rename, edit-request and result round-trips with scope;
  invalid scope and Private upload payloads fail before mutation.
- [ ] Test Alice's snapshot/edit delivery contains only Alice's private data; Bob and Shared
  recipients receive no private message. Reject unauthorized and incompatible requests.
- [ ] Test equal list revisions/names across Shared and Private independently. A recovery snapshot
  or tombstone for one source must not replace another source.
- [ ] Test private read permission is checked before every snapshot/incremental send. Observed
  denial/revocation sends unreadable collection state and clears private presentation without
  changing Local/Shared; it never sends a private delta while access is denied.
- [ ] Test late responses and transfers cannot apply to a different connection/account generation.
  Integrated-server client access must select only the local player's private store.
- [ ] Run the new tests to establish failures; implement scope codecs, connection-derived owner
  lookup, owner-directed sends, account/server-isolated private caches and per-source tracking.
  Preserve bounded chunked transport and existing edit revision checks.
- [ ] Update protocol/channel equality tests. Keep incompatible clients outside private custom
  synchronization while vanilla command access remains functional.
- [ ] Run `rtk proxy ./gradlew :common:test --tests '*ProtocolVersionTest' --tests '*CodecTest' --tests '*C2SPacketHandlerTest'`.
  Expected: new and existing codec/handler tests pass. Run the client tests using the versioned
  command in the verification section, filtering for `*ClientWaypointCollectionsTest` and
  `*ClientSynchronizationTrackerTest`.
- [ ] Review every waypoint producer/consumer and broadcast call for an explicit source and correct
  recipient. Existing cross-server catalog models must remain private-data-free.

## Task 5: Independent local profiles and persistence

**Dependencies:** Task 4's client collection contracts.

**Files:** add `mods/common/client/local/LocalWaypointProfiles.java`; connect it to
`WaypointClientMod` join/dimension/leave/shutdown/transfer lifecycle.
Add `local/LocalWaypointProfilesTest.java` and extend client collection lifecycle tests.

**Interfaces:** produces the active profile store and profile management operations, independent
of network readiness. Profile bindings and command settings persist using GSON.

- [ ] Test first join creates Default, reconnect restores the same files, and host/port/account and
  singleplayer saves are isolated. Assert profile display names cannot alter storage paths.
- [ ] Test known and new backend identities using the spec's selection algorithm; manual selection
  must win over a late handshake and survive reconnect through its saved binding.
- [ ] Test unsupported/protocol-incompatible servers can create and persist Local waypoints.
  A proxy transfer saves before rebinding without requiring a disconnect event.
- [ ] Test create/rename/delete, deletion of the last profile, outgoing-save failure, malformed
  indexes/files, and disconnect during pending work. Local state must never overwrite server caches.
- [ ] Run tests to establish failures; implement UUID-backed profile directories, address digest,
  fallback/backend bindings, synchronous selection state, and serialized disk/lifecycle operations.
  Do not move or merge files on late identity discovery.
- [ ] Run `*LocalWaypointProfilesTest` and `*ClientWaypointCollectionsTest` on the active Fabric
  target using the verification command. Expected: all persistence and transition assertions pass.
- [ ] Review join/leave/dimension paths for the old handshake-only renderer/manager assumptions.

## Task 6: Client operation routing and teleport templates

**Dependencies:** Tasks 3-5.

**Files:** add `mods/common/client/ClientWaypointAccess.java` and
`common/client/local/LocalTeleportCommand.java`; adapt add/edit/base forms and their validation
lookups. Add `ClientWaypointAccessTest.java`, `local/LocalTeleportCommandTest.java`, and extend
form patch/validation tests.

**Interfaces:** produces source-aware operations with typed outcomes for local persistence and
existing server results. The collection and generation are captured, never inferred from the
current view when a delayed action runs.

- [ ] Test add/edit/remove routes to only the selected collection. Local must send neither waypoint
  commands nor custom payloads; server edits must wait for matching authoritative results.
- [ ] Test a draft survives profile/connection invalidation but cannot submit into the replacement
  store. Opening and cancelling a copy draft creates nothing.
- [ ] Test copied name, display name, initials, coordinates, color, yaw, global visibility, keywords,
  description, and icon are detached. Name conflicts, quota denial, invalid destination metadata,
  or permission changes leave source and destination unchanged.
- [ ] Test template expansion with negative coordinates and yaw under a non-English locale.
  For the default template and Nether target `(-12, 64, 30), yaw 90`, assert the exact result:
  `execute in minecraft:the_nether run tp @s -12 64 30 90 ~`.
- [ ] Reject blank, unknown/unbalanced tokens, control characters, excessive commands, obsolete
  selections, and cross-dimension targets without `{dimension}`. A submitted command is not a
  successful-teleport result.
- [ ] Run tests to establish failures; implement routing and the named token parser. Reuse
  detached waypoint copies and existing patch/form validation; validate commands at submission.
- [ ] Run `*ClientWaypointAccessTest`, `*LocalTeleportCommandTest`, `*WaypointForm*Test` on the
  active Fabric target. Expected: routing/validation assertions and existing form tests pass.
- [ ] Document changed form/access APIs in the GUI guide and review the diff.

## Task 7: Four manager views, profiles, copying, and composed rendering

**Dependencies:** Tasks 4-6.

**Files:** add `mods/common/client/ClientWaypointQuery.java`; update manager, add/edit forms,
list/details widgets, client config/profile controls, and renderer scene composition.
Extend manager/render tests; add `ClientWaypointQueryTest.java` and
`gui/screens/WaypointManagerScreenCollectionTest.java`.

**Interfaces:** produces source-tagged queries, four view modes, destination/profile controls, and
a composed render scene. Reuse the screen-owned `IconDropdownMenu` for source switching.

- [ ] Test Combined includes all three current collections, retains duplicate identifiers with
  distinct selection keys, and excludes Remote. Grouped headers and flat rows retain source labels.
  Search/sort/dimension choices use the selected sources and preserve provenance.
- [ ] Test Combined is the initial configuration and creation defaults to Local; single-source
  views default to their source. Unavailable sources cannot become accidental save destinations.
- [ ] Test Local/Combined remain ready when the server is absent, incompatible, loading, or denied.
  Preserve the separate remote-browser toggle and its existing read-only behavior.
- [ ] Test captured selection routing after view changes, copied drafts, per-source visibility,
  profile changes while forms are open, and identical-coordinate markers.
- [ ] Test a Shared resync does not remove Local/Private render entries and a private tombstone
  removes only that private entry. Existing renderer `local`/waypoint `global` visibility flags
  retain their meaning. Renderer capacity limits must not cause unrelated stores to be mutated
  or lose saved data.
- [ ] Run tests to establish failures; compose existing query results and marker instances without
  creating a merged mutable store. Add source labels, destination/profile controls, and Copy.
- [ ] Follow existing dropdown popup layering/hover suppression and semantic theme roles; add
  applicable client locale keys and update the GUI guide in the same task.
- [ ] Run manager, query, form, renderer, and existing map-integration tests on active Fabric.
  Expected: new collection behavior and existing Shared integration tests pass.
- [ ] Review constrained-layout/input changes before live validation.

## Task 8: Platform validation, player documentation, and evidence

**Dependencies:** Tasks 1-7.

**Files:** update `docs/tips/server-guide.md`, GUI guide and relevant feature indexes; fill
the feature validation record only with executed evidence.

- [ ] Run the full common suite and representative versioned loader/Paper checks below.
- [ ] Run the validation checklist with two player identities, one vanilla client, and a modded
  client. Verify actual target coordinates after authorized teleports.
- [ ] Test Local on a server without Server Waypoint, an incompatible backend, a singleplayer save,
  and multiple worlds behind one address. Verify persistence by reconnecting/restarting.
- [ ] Inspect all four views at a constrained viewport and with long translated labels. Exercise
  dropdown overlap, wheel handling, keyboard focus, Escape, copy cancellation, and profile dialogs.
- [ ] Verify existing Shared map synchronization still works and receives no Local/Private events.
  No other map mod is required for the local feature.
- [ ] Add user-facing documentation distinguishing collection storage, permissions, quotas,
  profiles, custom teleport commands, and explicit copy behavior.
- [ ] Run whitespace/link/translation checks. Record exact commands, targets, outcomes, screenshots
  or log locations, and remaining proof gaps. Leave unexecuted items unchecked.
- [ ] Review the whole feature diff and remaining working-tree changes. Do not commit or publish.

## Verification commands and proof boundaries

These are commands to execute during implementation, not results from writing this plan.
Resolve the required installed JDK/toolchains before running versioned tasks. Inspect current
task availability and the Stonecutter skill before execution; direct tasks must not leave tracked
sources switched to a different active target.

Core:

    rtk proxy ./gradlew :common:test

Active Fabric client tests (append focused --tests filters during Tasks 4-7):

    rtk proxy ./gradlew :mods:26.1.2-fabric:test

Representative compilation checks after focused suites pass:

    rtk proxy ./gradlew :mods:1.20.1-fabric:compileJava :mods:1.20.1-forge:compileJava
    rtk proxy ./gradlew :mods:26.1.2-fabric:compileJava :mods:26.3-neoforge:compileJava
    rtk proxy ./gradlew :paper:1.21-paper:compileJava :paper:26.2-paper:compileJava

Run relevant existing/new client tests on an older supported target when its test harness is
available, not just compilation. Broaden the matrix only for affected API/loader branches or
failures. Verify Stonecutter markers and the configured active targets after the checks.

No build is proof of private data isolation on a real server, visible marker rendering, physical
input behavior, successful teleport arrival, or fluent translation quality. Those require the
specific live/manual evidence in the [validation checklist](../validation/2026-10-16-private-waypoint-checklist.md).
