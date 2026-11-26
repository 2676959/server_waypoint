# Matched-duration comparison column — 2026-11-03

The column layout below is historical. A later adjustment places all five markers at the same projected waypoint position and removes their comparison headers; see the final section. Duration/interval pairs remain unchanged.

At the user's request, `world_marker` again shows a comparison, with only the matched-duration column. Five displays appear together, labeled with duration and interval: **1/1, 2/2, 3/3, 4/4, 5/5 ticks**, from top to bottom. No zero-duration variant is created. Rows are centered horizontally on the original waypoint projection and spaced 1.4 blocks vertically.

The existing command and independent HUD remain available. Center billboarding, scale 1.0, full brightness, see-through text, default background, owner-only packets and close-range visibility are preserved. No entities are added to the server world. Each marker uses world game time for its own position schedule, so repeated same-tick callbacks do not advance it. Metadata still updates only when labels/locales change. Duration comparison requires Minecraft 1.20.2 or later.

The four updated handler tests failed against the single-marker implementation, then passed after restoring the column. They cover exactly five durations/positions, all five update schedules over 30 ticks, repeated same-tick callbacks, sub-centimetre movement, locale updates, dimension/player-entity changes, close-range visibility and cleanup.

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build :paper:1.21.11-paper:performanceProbeJar :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava --console=plain
```

Passed: common 724 tests, Fabric 26.1.2 1,120 tests, Paper 26.2 58 tests and Paper 1.21.11 53 tests — 1,955 with no failures/errors/skips. Both older Fabric compile checks passed. Active Stonecutter versions were unchanged. Translation JSON, runner syntax and `git diff --check` were checked. Gradle emitted deprecation/Unsafe warnings. Logs: `/tmp/world-marker-matched-red.log`, `/tmp/world-marker-matched-build.log`.

Native smoke passed on pinned Paper 1.21.11 build 111 / Java 21 with two vanilla protocol bots (ten marker entities total). Actual display metadata confirmed precisely the five matching duration/interval pairs, CENTER billboarding and default-background flags, with no server-world insertion. Over 162 stationary ticks, the first owner's five displays submitted 162, 81, 54, 40, 32 position packets respectively. Both owners' rates matched their intervals. Client labels, owner isolation, walking, retargeting, simultaneous HUD use, independent disable and cleanup passed. Disconnect left all tracked resources zero. All owned processes stopped; native ERROR/SEVERE/watchdog records: 0.

```sh
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py --root /Volumes/ssd/sw-world-marker-matched-column-2026-11-03 --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar --count 2
```

[Retained native evidence](2026-11-03-matched-column-native.json) records candidate SHA-256 `5f96a354d6b7342887b5fe656044bdeb3024938d605453763ce6c190b8c3b00e` and runtime/probe/bot hashes. This validates configuration and server packet-submission cadence; rendered smoothness and FPS remain for in-game inspection.

## Shared position and normal waypoint text

At the user's request, all layout offsets and comparison-header code/translations were removed. Each marker now samples the same projected waypoint position and displays only the normal waypoint name, distance and diamond. All five overlap while stationary; differences during movement come from the existing matched 1–5-tick settings. Default background, center billboarding and scale remain unchanged.

The updated handler test first failed against the column layout, then passed after the adjustment. It asserts that all five initial positions exactly equal the shared projection and all five text components equal the normal waypoint text. Existing cadence/lifecycle tests remain passing. The smoke runner's expected client labels were updated; the retained native evidence above predates this adjustment.

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build --console=plain
```

Passed: 1,955 tests across common, Fabric 26.1.2 and Paper 26.2/1.21.11, with no failures/errors/skips. JSON/runner syntax and `git diff --check` passed. Logs: `/tmp/world-marker-overlap-red.log`, `/tmp/world-marker-overlap-build.log`. Native/rendered-client testing was not repeated for this appearance-only adjustment.
