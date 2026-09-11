# Destination preparation and arrival (step 14)

Step 14 adds `DestinationHandoffService` and its authoritative resolver in `common`, plus
`ModDestinationPlatform` in `mods` and `PaperDestinationPlatform` in `paper`. These implement
reservation, claim, final validation and teleport initiation. No lifecycle creates a service yet;
remote teleport command registration and the live proxy/TCP dispatcher remain steps 15–16.

## Session and reservation ownership

Create exactly one destination service per admitted coordinator connection and close it before
replacing that connection. Its `CoordinatorLink` must correlate claim responses with the original
request UUID on that exact connection. It must not move pending futures or queued terminal messages
to a new connection. Only coordinator-forwarded messages may reach `prepare`/`receive`; client
payloads and arbitrary backend assertions are not authenticated coordinator messages. This same
application contract applies to KK and explicitly trusted-loopback plaintext.

`prepare(requestId, request)` checks the local destination ID, reserves the player/request slot,
and resolves the exact current waypoint and PUBLIC selection. It returns `HandoffPrepared` only
after successful resolution. Missing identity returns NOT_FOUND, a non-exported list UNAUTHORIZED,
and unavailable data UNAVAILABLE. Failed attempts retain their request IDs for replay rejection.
The reservation includes the request/player/source/destination/waypoint/action/expiry binding but
stores no coordinates. Observed source revisions do not provide coordinate authority.

Defaults bound active plus terminal records to 1024 and estimated metadata/identity storage to
8 MiB. The accounting charges 2048 bytes per record plus two bytes per dimension/list/waypoint
UTF-16 unit. Only one active reservation is allowed per player. Reservations expire after at most
15 seconds; terminal records retain replay protection for 60 seconds and are removed by maintenance.
Capacity failure returns BUSY without evicting replay protection. Terminal records release their
player references. `close()` completes pending arrivals as UNAVAILABLE and clears retained state.

## Authoritative lookup

`DestinationResolver.fromManager` uses `WaypointFilesManagerCore.snapshotWaypointData` with an
explicit object budget. This is an atomic detached capture of local data, not the backend's remote
catalog cache. It reads the current `CatalogSelection` supplier on every lookup and preserves exact
server, dimension, list and waypoint names. It returns only the resolved key, position and yaw.
Unavailable manager state or budget failure is not converted to an empty catalog. Preparation can
run as model work without a player; no offline permission lookup is attempted.

Arrival performs a new lookup on the player's owner after the coordinator grants the claim.
Deletion or renaming denies; movement under the same identity uses the newly resolved position/yaw.
No preparation snapshot is retained for teleport, and a resolver returning another identity is
rejected. Model locks are released before calling platform code.

## Arrival and final validation

`arrive(authenticatedPlayerId, player)` takes the UUID from the local authenticated join event.
It selects the reservation once, then schedules the first owner task. The adapter must verify both
UUID and current player-instance identity; a replacement/disconnected player cannot reuse it.
The service calls no player API until `ownsThread` confirms ownership.

The first owner task checks the player and sends `ClaimHandoff`. The response must be
`HandoffClaimed` with the same handoff/player/source/target/action and an expiry no later than the
local reservation. The coordinator may shorten expiry, as specified in step 13. A fresh owner task
then rechecks the current player, destination local teleport permission, PUBLIC selection and exact
waypoint. It checks the monotonic deadline immediately before initiating teleport. Claimed expiry
can only shorten the local monotonic deadline; wall-clock rollback cannot extend it.

Pass the step-12 `RemotePermissions::canTeleportOnArrival` callback to either platform adapter.
This uses the destination's current `server_waypoint.command.tp` permission and configuration.
Do not pass a cached permission result or the source's remote permission assertion.

The service moves through CHECKING, PREPARED, SCHEDULED, CLAIMING, READY, VERIFYING, TELEPORTING and
TERMINAL. Duplicate/concurrent arrivals cannot issue another claim or teleport. Duplicate scheduler
callbacks and late claim replies cannot revive terminal state. Scheduling rejection, entity
retirement, wrong ownership, cancellation and disconnect all deny work that has not started.
The returned completion stage cannot be used by a caller to complete/cancel the internal operation.

## Concrete platform adapters

- `ModDestinationPlatform` is bound to a specific `MinecraftServer`. It queues through
  `server.execute`, verifies `server.isSameThread`, checks player-list object identity/disconnection,
  resolves the exact loaded dimension, and returns the actual boolean result from `teleportTo`.
  Small Stonecutter branches cover dimension-key naming and the teleport camera argument.
- `PaperDestinationPlatform` uses the player's entity scheduler with its retirement callback and
  verifies `Bukkit.isOwnedByCurrentRegion`. It checks the live player instance, resolves the exact
  namespaced world and initiates `teleportAsync`. Its future determines success; no thread waits
  for asynchronous chunk loading. This respects Folia player ownership.

Both adapters place the player at block-center X/Z, integer Y and current waypoint yaw, with zero
pitch. Missing/unloaded worlds or failed/cancelled platform teleport return failure.

## Completion, cancellation and asynchronous limits

A valid claim followed by a denial reports `CompleteHandoff` with a stable failure result. A
pre-claim local failure queues cancellation; coordinator claim denials are consumed without falsely
reporting successful claim/completion. No exception text is sent. The `ArrivalResult` distinguishes
the local outcome from whether a terminal notification was queued. A failed notification never
retries player work. Queued delivery is not proof of coordinator receipt.

Teleport initiation is serialized with local cancellation/close. Once initiated, an asynchronous
platform teleport cannot be undone by this service. Its player slot stays occupied until the
platform future completes or the connection service closes, even if the initial deadline passes.
This prevents maintenance from admitting overlapping work for that player while a teleport is in
flight. A stalled future can occupy one bounded slot; the lifecycle owner must close on shutdown.
Close suppresses late reports/callback work, but cannot reverse an already submitted platform
teleport. A coordinator may expire before asynchronous completion arrives; neither side retries
the teleport automatically. These are platform-operation limits, not a promise of distributed
transaction rollback.

The lifecycle owner must run `maintain`, forward cancellations, close on connection loss, and avoid
replaying arrived-player events onto replacement services. Live transport correlation and join
routing are still step-16 integration work. No new wire message, protocol version, command or
local navigation target was introduced.

## Verification

On 2026-09-08, 484 common tests and 90 proxy tests pass with no failures/errors/skips. The 26 new
cases cover current manager data, removal, renaming, movement/yaw changes, export/permission
revocation, player replacement, owner scheduling/retirement/rejection, duplicate/concurrent arrival,
late/invalid/denied/capped claims, monotonic expiry, cancellation/disconnect, async failure and
completion-report failure, metadata limits, and full in-memory coordinator/destination exchanges
in both transport modes. Sixteen concurrent arrival events result in one claim and one teleport.

This exact command also passed representative production adapter compilation and the Velocity build:

```sh
./gradlew :common:test :proxy-common:test :velocity:build :mods:26.1.2-fabric:compileJava :mods:1.20.1-fabric:compileJava :mods:1.21.2-neoforge:compileJava :mods:26.1.2-forge:compileJava :paper:1.21-paper:compileJava :paper:26.2-paper:compileJava --max-workers=2 --console=plain
```

The active projects remain `26.1.2-fabric` and `26.2-paper`; no switch/reset was needed. The mod
adapter was checked against local Minecraft 1.20.1 and 26.1.2 sources for scheduler/player/teleport
contracts. The common service uses Java 17; platform projects keep their existing required toolchains.
Stonecutter markers and new-file whitespace checks pass. No native Minecraft/Paper/Folia/Velocity
session, live permission-provider integration, or full release artifact matrix was run. The tests
exercise controlled owning-thread adapters, while the concrete platform adapters are compile-verified.

## Step-16 integration

The services now have live coordinator/backend lifecycle and Velocity transfer wiring. See
[the runtime contract and current validation](cross-server-velocity-runtime.md). Earlier step-specific
verification above describes its historical boundary; client/GUI and full release hardening remain.

## Step-19 native arrival scheduling correction

Mod owner dispatch now enqueues a `TickTask` even when called from the server thread; Fabric's
JOIN hook can precede UUID lookup installation. Paper/Folia dispatch always uses the next player
scheduler tick. Both adapters recheck the actual player object and lifecycle at execution time.
The [release record](../validation/cross-server-release-readiness.md) contains the native before/after evidence
and delayed-registration/replacement/shutdown regression coverage.
