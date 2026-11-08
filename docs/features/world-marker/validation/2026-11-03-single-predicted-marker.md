# Single marker with motion prediction — 2026-11-03

The user requested removal of the other comparison displays. `world_marker` now creates exactly one private text display per player, configured with `teleport_duration=1` and position updates every world tick. Comparison lists, interval selection and per-variant counters were removed from the handler. Normal waypoint text, default background, center billboarding, scale 1.0 and close-range visibility remain.

Bounded horizontal motion prediction remains. With the retained one-tick interpolation duration and at most 200 ms of RTT compensation, its forecast is at most five ticks and two blocks. This is the same prediction behavior as the former comparison's 1/1 variant. Stops, position discontinuities, sampling gaps and entity/world changes still reset motion history.

Updated tests failed against the five-display handler and passed after the removal. The eight handler tests now exercise a single marker, its exact per-tick schedule, same-tick suppression, normal text, locale/lifecycle handling, sprint-delay prediction, stop/teleport/world resets, prediction bounds, current eye height and nearby destinations.

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build :paper:1.21.11-paper:performanceProbeJar --console=plain
```

Passed: common 728 tests, Fabric 26.1.2 1,120, Paper 26.2 58 and Paper 1.21.11 53 — 1,959 with zero failures/errors/skips. Active Stonecutter versions were unchanged. Gradle emitted deprecation/Unsafe warnings. Runner syntax and `git diff --check` were checked. Logs: `/tmp/world-marker-single-predicted-red.log`, `/tmp/world-marker-single-predicted-build.log`.

Native smoke passed on pinned Paper 1.21.11 build 111 / Java 21 with two vanilla protocol bots. Each owner received exactly one private marker with duration 1 and one-tick updates. Across 161 stationary ticks, the two markers submitted 322 position packets. The bounded movement lead, owner isolation, normal text/default background, no world insertion, retargeting, simultaneous HUD use, independent disable and cleanup checks passed. Disconnect left all tracked resources zero, including motion history. All owned processes stopped. Native ERROR/SEVERE/watchdog records: 0.

```sh
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py --root /Volumes/ssd/sw-world-marker-single-predicted-2026-11-03 --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar --count 2
```

[Retained native evidence](2026-11-03-single-predicted-marker-native.json) records candidate SHA-256 `2d593cc82acbadd9f7c6ae2784f5ecc08fd4377b8d125a7ededf1d44ac3ec435` and runtime/probe/bot hashes. Rendered visual quality and FPS were not tested.
