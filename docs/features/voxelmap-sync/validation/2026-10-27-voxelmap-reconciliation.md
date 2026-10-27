# VoxelMap handshake reconciliation validation

Date: 2026-10-27. Implementation base: `487d8695`.

## Implemented behavior

An unchanged remote handshake now reconciles VoxelMap when auto-sync is enabled.
Automatic writes wait until VoxelMap's loaded world equals Minecraft's current
non-null client level and both map managers exist. Delayed events coalesce into
one full reconciliation from the current model, and idle ticks do not probe map
readiness. Joining/leaving clears pending work; disabled/uninstalled integrations
cancel it on retry. Integrated entry, unchanged Xaero behavior, manual download,
and Sync Now preference independence are preserved.

No reload, command alias, icon, protocol, or localization changes were made.

## Regression evidence

- Before the repair, `MapModIntegrationsTest.voxelMapIsNotReadyBeforeAWorldIsLoaded`
  failed: the old implementation returned true without a world.
- Initial coordinator tests failed compilation because the coordinator did not
  exist. The implemented coordinator then passed the behavioral tests.
- Removing the new empty-update hook made
  `ClientHandshakeMapSyncTest.unchangedHandshakeRestoresVoxelMapAfterItsWorldIsReady`
  fail at the expected marker-restoration assertion. Restoring the hook passed.
- End-to-end tests exercise real chunked update reception and the client tick;
  external map storage is replaced with isolated fixtures. They cover delayed
  readiness, unchanged Xaero data, integrated entry, cancellation on disconnect,
  and auto-sync preference cancellation.
- Coordinator tests cover latest-model coalescing, pending work consumed by a ready
  event, absent/disabled integrations, session reset, and idle ticks.
- Readiness tests cover no current world, a previous world, the matching world,
  and missing managers. The accessor test checks its annotation and field
  descriptor against the VoxelMap dependency actually on each test classpath.

## Suite results

Command (prefixed with `rtk proxy` in this environment):

```sh
./gradlew :mods:26.1.2-fabric:test :mods:26.1.2-neoforge:test :mods:26.1.2-forge:test :mods:1.20.1-forge:test :mods:1.20.1-fabric:test --console=plain --max-workers=2
```

| Target | Tests | Failures/errors | Skipped |
| --- | ---: | ---: | ---: |
| Fabric 26.1.2 | 963 | 0 | 0 |
| NeoForge 26.1.2 | 968 | 0 | 27 |
| Forge 26.1.2 | 967 | 0 | 1 |
| Fabric 1.20.1 | 951 | 0 | 0 |
| Forge 1.20.1 (no VoxelMap dependency) | 872 | 0 | 3 |
| Total | 4,721 | 0 | 31 |

4,690 tests passed. The first expanded run failed the new NeoForge readiness
fixture because `ClientLevel` initialization requires a running FML loader.
The test now explicitly aborts only for that exact missing-loader error; this
accounts for one NeoForge skip. The remaining 30 skips are in existing tests.
The readiness fixture passes on Fabric and Forge where it can run. Existing
deprecation/Unsafe warnings remain.

## Packaging

The following command passed:

```sh
./gradlew :mods:1.20.1-fabric:remapJar :mods:26.1.2-fabric:jar :mods:1.20.1-forge:jar --console=plain --max-workers=2
```

The new accessor is present and registered in the Fabric 1.20.1 remapped JAR and
Fabric 26.1.2 thin JAR. `javap -s` on the newly remapped 1.20.1 artifact reports
`sw$getWorld()` with descriptor `()Lnet/minecraft/class_638;`, matching the pinned
VoxelMap artifact. The Forge 1.20.1 thin JAR contains neither the accessor class
nor its VoxelMap mixin registration in loader metadata.

`git diff --check` passed. This verifies packaging and descriptor remapping, not
runtime mixin application.

## Review and proof boundaries

A separate read-only review found no actionable code issues. The reviewer checked
all 22 cached pinned VoxelMap JARs: their private `world` field has the expected
client-level type, and their tick binds it before invoking `WaypointManager.newWorld`.
The active Stonecutter projects remain Fabric 26.1.2 and Paper 26.2.

Unit and bytecode checks do not prove actual mixin application, game rendering,
multiplayer/proxy timing, or VoxelMap file persistence in a running Minecraft
client. Live connect/reconnect, proxy transfer, dimension change, disabled-auto-sync,
and stale/missing `.points` recovery remain separate validation scenarios.
