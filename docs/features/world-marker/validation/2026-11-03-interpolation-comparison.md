# Ten-display interpolation comparison — 2026-11-03

Historical comparison: the user subsequently selected `teleport_duration=1` with position updates every tick. The comparison grid and its labels/scheduling variants have been removed from source. See [selected-setting verification](2026-11-03-selected-setting.md) for the retained single marker.

The existing `world_marker` command now enables ten private text displays together for visual inspection. The left column explicitly sets `teleport_duration=0`; the right column sets duration to the corresponding 1–5-tick position update interval. Rows run from one tick at the top to five ticks at the bottom. Every display labels both settings above the waypoint name/distance/diamond. This extends the original unmounted position-packet method; no riding or carrier code was restored and no command argument was added.

Columns are five blocks apart and rows 1.4 blocks apart, around the original 12-block waypoint projection. Their horizontal direction is perpendicular to the waypoint bearing, independent of head yaw. Center billboarding, scale 1.0, close-range visibility, full brightness, see-through text and the default background remain. The separate transformation-interpolation property is not used for movement. Minecraft 1.20.1 lacks `teleport_duration`; inspect this comparison on 1.20.2 or later.

Each display samples the latest projected position at its own interval. Both groups start in the same phase. World game time supplies the clock, so repeated same-tick callbacks do not produce extra position updates. Label/locale changes can update metadata between position updates. Diagnostics aggregate all ten displays. Approximately 91 position packets per second per player are submitted at 20 TPS while the group is visible.

## Automated checks

The four revised handler tests initially failed against the single-marker implementation, then passed with the comparison implemented. They cover all ten configurations and distinct placements, exact 1–5-tick schedules across 30 ticks, repeated same-tick callbacks, sub-centimetre movement, locale refresh without early position packets, dimension/player-entity changes, close-range visibility and full cleanup.

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build :paper:1.21.11-paper:performanceProbeJar :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava --console=plain
```

Passed: common 724 tests, Fabric 26.1.2 1,120 tests, Paper 26.2 58 tests and Paper 1.21.11 53 tests — 1,955 with zero failures/errors/skips. Both older Fabric compile checks passed. Active projects remain 26.1.2-fabric and 26.2-paper. An initial probe compile missed a `List` import; that was corrected before the passing build and native run. Gradle emitted deprecation/Unsafe warnings. Logs: `/tmp/world-marker-comparison-red.log`, `/tmp/world-marker-comparison-common.log`, `/tmp/world-marker-comparison-build.log`. Translation JSON, runner syntax, touched Stonecutter block scopes and `git diff --check` were checked.

## Native runtime check

```sh
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py --root /Volumes/ssd/sw-world-marker-interpolation-comparison-2026-11-03 --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar --count 2
```

Passed on pinned Paper 1.21.11 build 111 / Java 21 with two vanilla protocol bots and twenty private marker entities total. Candidate SHA-256: `f293bf3ce63418679edd41e8ccf58b4b70966c6c8a1d1e4a99ad8fc14c8f549e`. [Retained native/client observations](2026-11-03-interpolation-comparison-native.json) include runtime, probe, bot and candidate hashes.

Each player had exactly ten distinct owner-only displays. Reading actual native display metadata confirmed every requested duration/interval pair; all used CENTER billboarding, flags 7 and no server-world insertion. Normal client packets delivered all ten corresponding labels without exposing them to the other player.

During 161 stationary server ticks, the first owner's per-display position submission counts were:

| Update interval | Left, duration 0 | Right, matching duration |
| ---: | ---: | ---: |
| 1 tick | 161 | 161 |
| 2 ticks | 81 | 81 |
| 3 ticks | 54 | 54 |
| 4 ticks | 40 | 40 |
| 5 ticks | 32 | 32 |

Both owners' per-display rates matched their schedules. Aggregate stationary totals were 322 group updates, 1,470 position submissions and zero metadata submissions. Turn-in-place phase: 15 ticks, 138 position submissions, zero metadata. Walking phase: 72 ticks, 658 position submissions and 260 metadata submissions, with observed player movement. Counts include both owners.

Retargeting changed only the intended owner's labels. The separate HUD coexisted with all ten markers; disabling either retained the other. Stop removed all marker entities and sessions. Disconnect left every tracked resource count zero. All owned processes stopped, and native logs contained no ERROR/SEVERE/watchdog records.

This proves configuration, server packet-submission cadence, ownership and lifecycle. It does not measure delivered client motion, rendered smoothness, FPS, server tick percentiles or a capacity limit. No rendered client or physical-input check was performed. The user must choose the smoothest setting by inspecting the matrix in game.
