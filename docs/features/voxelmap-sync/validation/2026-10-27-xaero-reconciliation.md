# Xaero handshake reconciliation validation

Date: 2026-10-27. Implementation base: `c9aae611`.

## Implemented behavior

An unchanged remote handshake now reconciles Xaero's managed markers as well as
VoxelMap's when that map's auto-sync preference is on. The coordinator keeps
pending work per upload target: a map that is not ready queues one full
reconciliation from the latest model, each map drains on the client tick once it
is ready, and a disabled or uninstalled map drops only its own pending work.
Xaero's `isEnabled` now reports the preference alone; the coordinator checks
readiness separately.

Xaero's world-ready callback only marks readiness. Before, every level-id packet
(sent on join, respawn and each dimension change) rewrote all of Xaero's managed
markers once the handshake had finished, on top of the handshake's own write.
Readiness resets on join and leave, so a previous connection cannot admit writes.

Preserved: the integrated-world entry guard, manual download and Sync Now
preference independence, and idle ticks that do not probe readiness. No protocol,
localization, reload, icon or compatibility changes.

## Regression evidence

HEAD's `WaypointClientMod`, `MapModSyncCoordinator`, `XaerosMinimapIntegration`
and `MinimapWorldStateUpdaterMixin` were compiled into an overlay placed ahead of
the current Fabric 26.1.2 classes. The six focused classes (52 tests) then ran
against it through the JUnit launcher: 8 failed at their expected assertions and
44 passed. With the current classes all 52 pass.

| Test | Result on the old implementation |
| --- | --- |
| `MapModSyncCoordinatorTest.unchangedHandshakeRestoresManagedMarkersInBothMaps` | Xaero untouched |
| `MapModSyncCoordinatorTest.xaeroWaitsForReadinessAndReconcilesOnlyOnce` | Deferred Xaero work dropped |
| `MapModSyncCoordinatorTest.pendingMapsBecomeReadyIndependently` | Xaero never reconciled |
| `ClientHandshakeMapSyncTest.xaeroReadyBeforeUnchangedHandshakeRestoresMarkers` | Stale marker kept |
| `ClientHandshakeMapSyncTest.xaeroReadyAfterUnchangedHandshakeRestoresMarkersOnlyOnce` | Two rewrites: the repeated readiness callback wrote again |
| `ClientHandshakeMapSyncTest.leavingServerClearsXaeroReadinessAndItsPendingWork` | Readiness still true after leaving |
| `ClientHandshakeMapSyncTest.changedHandshakeAlsoWaitsForXaeroReadiness` | Wrote before readiness (see below) |
| `MapModIntegrationsTest.xaeroAutoSyncPreferenceIsIndependentOfReadiness` | Preference reported off while not ready |

The `changedHandshakeAlsoWaitsForXaeroReadiness` fixture follows the new
`isEnabled` contract. With the old real integration the changed handshake would
have been dropped and the readiness callback would have written instead.

## Readiness ordering review

The join reset is only safe if Xaero's level id cannot reach the client before
`onJoinServer` for the same connection. A static review of the pinned jars found
it cannot:

- Resets run at `ClientPacketListener.handleLogin` TAIL (`onJoinServer`) and in
  `onLeaveServer`, which `Minecraft.updateLevelInEngines(null)` and the telemetry
  `onDisconnect` hook call.
- Servers send the level id after the login packet: Server Waypoint's
  `PlayerList.sendLevelInfo` HEAD mixin (when `sendXaerosWorldId` is on), Paper's
  channel-registration listener, or Xaero's own server component through its
  `PlayerList` mixin (`CommonEvents.onPlayerWorldJoin`), which also runs in
  singleplayer.
- Clients run the callback on the client thread, after packets queued before it:
  Xaero 25.2.10 on Fabric 1.20 submits it to the client executor; XaeroLib 1.1.15
  on Fabric 26.1.2 runs it on the client thread or submits it there; XaeroLib
  1.1.15 on Forge 1.20.1 uses `enqueueWork`. `onServerLevelId` also reads the
  current client level, so it cannot run before one exists.
- `MinimapWorldManager.getWorld` creates missing worlds, so one full sync covers
  every dimension. Dropping the rewrite on each dimension change loses no markers.

Boundary: a connection that never receives a level id (Server Waypoint with
`sendXaerosWorldId` off and no Xaero server component) keeps Xaero automatic sync
deferred for that connection. Before, readiness left over from an earlier
connection in the same game session could let writes through; after a fresh
launch it was never ready either.

## Suite results

Command (run through a wrapper that sets the SSD Gradle home and the JDK 25
toolchain path):

```sh
./gradlew :mods:26.1.2-fabric:cleanTest :mods:26.1.2-fabric:test :mods:26.1.2-neoforge:cleanTest :mods:26.1.2-neoforge:test :mods:26.1.2-forge:cleanTest :mods:26.1.2-forge:test :mods:1.20.1-forge:cleanTest :mods:1.20.1-forge:test :mods:1.20.1-fabric:cleanTest :mods:1.20.1-fabric:test --console=plain --max-workers=2 --continue
```

| Target | Tests | Failures/errors | Skipped |
| --- | ---: | ---: | ---: |
| Fabric 26.1.2 | 972 | 0 | 0 |
| NeoForge 26.1.2 | 977 | 0 | 27 |
| Forge 26.1.2 | 976 | 0 | 1 |
| Forge 1.20.1 (no VoxelMap dependency) | 881 | 0 | 3 |
| Fabric 1.20.1 | 960 | 0 | 0 |
| Total | 4,766 | 0 | 31 |

4,735 tests passed. Each target gained the 9 new tests; the skips are the same
existing ones as in the [VoxelMap reconciliation record](2026-10-27-voxelmap-reconciliation.md).

The first run failed four new `ClientHandshakeMapSyncTest` Xaero tests on Forge
1.20.1. Ticking the whole client sent a payload, and `ServerWaypointForge`'s static
initializer needs FML there. The VoxelMap tests that tick the client compile only
where VoxelMap exists, so this class had never ticked on Forge 1.20.1. The Xaero
tests now call `MapModIntegrations.tickPendingSync`, the tick's map-sync step, as
two existing VoxelMap tests do. `git diff --check` passes, and the Stonecutter
markers in the touched files are balanced.

## Proof boundaries

Unit tests, the overlay run and the static jar review do not prove mixin
application, real Xaero storage writes, live packet timing, proxy transfers,
dimension changes or rendering in a running client. Live connect and reconnect
with Xaero ready before and after the handshake, a proxy transfer, a dimension
change and disabled auto-sync remain separate validation scenarios.
