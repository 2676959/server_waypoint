# Selected world-marker setting — 2026-11-03

Historical selection: the user later reopened the comparison with only the five matched-duration variants. See [matched-column verification](2026-11-03-matched-column.md) for the current setup.

Following the ten-display comparison, the user selected **`teleport_duration=1` with entity-position updates every server tick**. The implementation now uses one marker at the original projected waypoint position. Grid offsets, comparison labels/translations, selectable intervals and per-variant counters were removed. The direct riding and carrier attempts remain removed.

The marker retains center billboarding, scale 1.0, default background, full brightness, see-through text and close-range visibility. It sends one position packet per world tick after spawning, including while stationary. Repeated callbacks within a tick do not send additional position packets. Label/locale updates remain independent of movement. Minecraft 1.20.1 uses vanilla interpolation because it does not expose `teleport_duration`.

The four revised tests first failed against the ten-display implementation and then passed with the selected single-marker implementation. Checks cover duration selection, original projected placement, exact per-tick cadence, same-tick suppression, small movement, label/locale updates, dimension/player-entity changes and cleanup.

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build :paper:1.21.11-paper:performanceProbeJar :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava --console=plain
```

Passed: common 724, Fabric 26.1.2 1,120, Paper 26.2 58 and Paper 1.21.11 53 tests — 1,955 total with no failures/errors/skips. Both older Fabric compile checks passed. Active Stonecutter versions were unchanged. Gradle emitted deprecation/Unsafe warnings. Logs: `/tmp/world-marker-selected-red.log`, `/tmp/world-marker-selected-build.log`. Translation JSON, runner syntax and `git diff --check` were checked.

The native smoke passed with two vanilla protocol bots on pinned Paper 1.21.11 build 111 / Java 21. It read `teleport_duration=1` from both actual displays, confirmed one private marker per owner, CENTER billboarding, default background, no world insertion, owner isolation, simultaneous HUD use, independent disable, retargeting and cleanup. Across both owners, 161 stationary ticks submitted 322 position packets and no metadata packets; 17 turn-in-place ticks submitted 34 position packets and no metadata; 71 walking ticks submitted 142 position packets and 26 metadata packets. Disconnect left all tracked resources zero. All owned processes stopped; native logs had no ERROR/SEVERE/watchdog records.

```sh
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py --root /Volumes/ssd/sw-world-marker-selected-1-tick-2026-11-03 --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar --count 2
```

[Retained native evidence](2026-11-03-selected-setting-native.json) includes candidate SHA-256 `30cad0f407f9a5896a96e28136845320c23dac1ccfccb9ee56d6e1098c3a3376`. These checks verify configuration, server packet submission and lifecycle. They do not measure rendered smoothness, FPS or a capacity limit.
