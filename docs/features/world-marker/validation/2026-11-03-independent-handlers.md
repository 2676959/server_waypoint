# Independent navigation display handlers — 2026-11-03

`text_display` and `world_marker` now have separate lifecycle and platform implementations. There is no presentation-mode constructor, mode field or `isWorldMarker()` branch.

- Mounted HUD: `AbstractTextDisplayNavigationHandler`, `ModTextDisplayNavigationHandler`, `PaperTextDisplayNavigationHandler`.
- World marker: `AbstractWorldMarkerNavigationHandler`, `ModWorldMarkerNavigationHandler`, `PaperWorldMarkerNavigationHandler`.
- Each feature owns its display state, configuration, enable/update/disable behavior and cleanup. Only the mounted HUD implements live transformation editing and passenger packets. Only the marker implements projection, movement suppression, dimension hiding and diagnostics.
- Platform-local `ModTextDisplayPackets` and `PaperTextDisplayPackets` share entity construction, text conversion, metadata encoding and owner-only packets. They do not select a navigation mode or configure presentation settings.

Both features retain center billboarding after the user's revert of the temporary fixed-orientation experiment. The marker retains fixed scale, close-range visibility and the default background. The HUD retains its mounted placement and configured background. No new compatibility layer, plans or specs were added. Unrelated performance work was left in place. Changes are uncommitted.

## Build and tests

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build :paper:1.21.11-paper:performanceProbeJar :mods:1.20.1-fabric:compileJava --console=plain
```

Passed: common 724 tests, Fabric 26.1.2 1,120 tests, Paper 26.2 58 tests and Paper 1.21.11 53 tests. Total: 1,955 tests, zero failures/errors/skips. Fabric 1.20.1 compilation also passed. The six existing handler checks now live in the separate HUD and world-marker test classes. Active Stonecutter projects remained Fabric 26.1.2 and Paper 26.2; no switch/reset was required. Gradle generation accepted the relocated version guards. `git diff --check` passed.

Build log: `/tmp/world-marker-split-final-build.log`. Forge/NeoForge and the complete version matrix were not exercised.

## Native verification

```sh
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py --root /Volumes/ssd/sw-world-marker-independent-handlers-2026-11-03 --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar --count 10
```

Passed on the pinned local Paper 1.21.11 build 111 / Java 21 runtime with ten vanilla protocol bots. Candidate SHA-256: `f68eae97422d230bb1a71dc83dba96f7e6aaed91196729d5694deded4ad70a0d`.

[Retained native snapshots and normal client packet observations](2026-11-03-independent-handlers-native.json) prove:

- Correct owner-only labels and distinct display IDs; marker entities were absent from the server world.
- Stationary marker packet suppression, real walking updates and owner-only retargeting.
- Both display methods active simultaneously for one owner with different entity IDs.
- Disabling `world_marker` preserved the existing HUD entity and its client display.
- Re-enabling the marker preserved the HUD; subsequently disabling `text_display` preserved the marker entity and its client display.
- Stop cleanup and disconnect with active markers left zero sessions, tasks, entities, map state, target index entries and tick counters.

The disposable server and bots were stopped. Native logs had zero ERROR/SEVERE/watchdog records. Raw process logs and world state remain under the run root; launcher output is `/tmp/world-marker-split-live.log`.

This is a functional check with a protocol client. It does not establish rendered appearance, smoothness, client FPS, server tick percentiles or a capacity limit. The earlier experiment's timing and candidate hash do not describe this refactor.
