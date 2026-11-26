# Task 5: independent Local profile persistence validation

Date: 2026-10-28. Base: `dc6e94d8`, branch `feature/private-waypoint`.
Task 5 is implemented and verified with automated tests. Local commit authorized on 2026-10-28.

## Delivered behavior

`LocalWaypointProfiles` owns independent account/world data, UUID profile directories, profile
metadata, teleport-command settings, fallback selection, and backend bindings. Multiplayer roots
use a SHA-256 digest of the normalized full host and effective port (default 25565). DNS case,
IDN names, trailing DNS root dots, and IPv6 spelling are normalized without resolving DNS hosts.
Singleplayer roots live inside the world save. Display names never enter paths.

The first join creates Default. A valid backend identity uses its saved binding; a new identity
binds an unbound fallback or receives an empty profile when fallback already belongs to a backend.
Manual selection persists as fallback and, when known, the backend binding; it wins over a late
handshake for that session. Late discovery switches references only and never moves/merges data.
Create, rename, select, delete, and per-profile command settings are available through the profile
owner API. Deleting the final profile creates Default and removes the deleted profile's bindings.
Deleting an inactive profile does not turn off automatic selection.

Profile/disk work is synchronous and serialized by the owner. Selection is published after a
strict outgoing save, target load, and atomic GSON index replacement. Failed saves or index writes
keep the outgoing store and metadata. Malformed indexes, missing referenced directories, unknown
bindings, and invalid waypoint files fail closed without creating writable empty replacements.
The mutation entry point validates a captured Local selection and saves before returning success.
A save failure retains live changes for retry and throws instead of claiming durable success.

Join (including proxy rejoin without disconnect), dimension, leave, shutdown, and compatible
identity discovery are connected through `WaypointClientMod`. An existing client-only Minecraft
mixin flushes on `Minecraft.close`. Incompatible protocol handling disables server collections
while retaining Local. Actual disconnect unbinds Local and invalidates pending selections. Failed
outgoing stores can be retried but cannot be reactivated by a later handshake on another connection.
Standalone clients enable the existing client-field serializer so Local list visibility/expansion
survive reconnect without integrated-server initialization.

The profile owner has no network or map-mod calls; all stores remain separate. A localized storage
error is present in all six client locales. Human language review has not been performed.

## Executed checks

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:test --console=plain --max-workers=2

rtk proxy ./gradlew :mods:1.20.1-fabric:test \
    --tests '*LocalWaypointProfilesTest' --tests '*ClientWaypointCollectionsTest' \
    --tests '*ClientHandshakeMapSyncTest' :mods:1.20.1-forge:compileJava \
    :mods:26.3-neoforge:compileJava --console=plain --max-workers=2

rtk proxy ./gradlew :mods:26.1.2-fabric:test \
    --tests '*LocalWaypointProfilesTest' --tests '*ClientWaypointCollectionsTest' \
    --console=plain --max-workers=2
```

The full suites, focused active Fabric command, older Fabric checks, Forge 1.20.1 compilation,
and NeoForge 26.3 compilation pass. See the [machine-readable counts](execution/task-5/test-counts.json)
for suite totals: common 718, active Fabric 1,000, older Fabric focused 41; all have zero
failures/errors/skips. The final exact Task 5 focused run passes 24 tests. Active projects stay `26.1.2-fabric` and `26.2-paper`; no switch was used.
The older Fabric compile verifies Java 17 language compatibility for the new profile manager.

Coverage includes account/host/port/save isolation, GSON metadata and waypoint reconnects, manual
and automatic backend choice, UUID paths with path-like labels, profile deletion, save/index failure,
corruption, transfer flushes without disconnect, blocked mutations during leave, late-selection
rejection, incompatible handshakes, shutdown, visibility persistence, and unchanged server caches
and Shared map-integration callbacks. Existing Gradle, loader, map integration, and Unsafe fixture
warnings remain visible in the retained logs.

The contract tests first failed because the profile owner/lifecycle methods were absent.
Behavior regressions were observed failing before fixes for detached-store reactivation,
visibility/expansion persistence, and inactive deletion suppressing automatic backend selection.

## Review and scope decisions

Review was performed inline because the plan forbids delegation unless the user chooses it.
The review covered the complete Task 5 diff, the profile selection algorithm, paths, failure order,
connection generations, Minecraft join/dimension/leave/close hooks, and all new locales.
No unresolved review findings remain.

- Synchronous owner serialization implements the planned synchronous selection and ordered disk
  lifecycle. No asynchronous binding publication is introduced.
- The existing feature checkout was clean and already held Tasks 1–4; work stayed there.
- The current renderer and manager consume Shared. Source-aware operation routing, profile dialogs,
  query refresh and composed rendering remain the explicit Tasks 6–7; Task 5 exposes active profile
  identity/name/store and invalidates captured Local references on switches.
- A missing index beside existing profile data fails closed. No recovery reader, migration,
  automatic copy, or compatibility layer was added.
- The user subsequently authorized a local Task 5 commit. No push or publication is authorized.

## Evidence and limits

- [Pre-commit suites](execution/task-5/precommit-suites.log)
- [Full suites](execution/task-5/full-suites.log)
- [Active focused command](execution/task-5/task-tests.log)
- [Older-target checks](execution/task-5/version-checks.log)
- [Initial contract failure](execution/task-5/red.log)
- [Lifecycle contract failure](execution/task-5/lifecycle-red.log)
- [Detached-store failure](execution/task-5/detached-red.log)
- [Visibility failure](execution/task-5/visibility-red.log)
- [Inactive-deletion failure](execution/task-5/review-red.log)
- [Execution progress](execution/progress.md)

No live Minecraft session, actual proxy transfer, graphical profile control, marker rendering,
physical input, command-template execution, or teleport arrival was tested here. Tasks 6–8 and
live acceptance in the feature checklist remain pending.
