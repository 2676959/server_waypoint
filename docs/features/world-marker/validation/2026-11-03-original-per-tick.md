# Original marker retained, per-tick packets — 2026-11-03

After the verification below, the explicit `teleport_duration=1` configuration was removed from both backends at the user's request. Position packets still send every tick; display-specific interpolation now uses vanilla defaults. The native evidence below describes the preceding revision with the explicit one-tick setting, not a rendered or runtime validation of its removal.

The later [ten-display comparison](2026-11-03-interpolation-comparison.md) supersedes this single-marker setup and explicitly compares zero duration against durations matching 1–5-tick position updates.

The two approaches following the original position-packet marker have been documented and removed from source: [direct riding with transformation metadata](2026-11-03-riding-comparison.md), then [native carrier attachments](2026-11-03-carrier-investigation.md). Historical reports and evidence remain available.

Removed code includes mounted-marker lifecycle/placement/statistics classes, platform adapters, the two-handler composite, passenger-merging infrastructure, riding labels/diagnostics/tests, passenger-only bot telemetry, the carrier geometry probe and its runner. Both platforms now register their original free marker directly. The separate `text_display` HUD retains its original independent riding behavior and passenger packets; it is not one of the removed world-marker variants.

The free marker now calculates and submits an absolute position packet every server tick after spawning. There is no stationary or one-centimetre suppression. Position interpolation is one tick where vanilla exposes `teleport_duration` (1.20.2+); 1.20.1 retains vanilla behavior. Center billboarding, default background, scale 1.0, close-range visibility, owner-only delivery and the 12-block projection remain. Label/locale metadata is still suppressed when unchanged. Other navigation displays keep their five-tick schedule and share the snapshot on those ticks.

## Automated verification

The updated lifecycle and scheduling tests first failed against the five-tick/position-suppression implementation (three expected assertion failures). After the change, the full common suite passed. Coverage includes stationary position submission, sub-centimetre motion, close-range visibility, dimension/entity lifecycle, locale changes, per-tick scheduling and sharing the fifth snapshot with the other displays.

```sh
rtk proxy ./gradlew :common:test --console=plain
rtk proxy ./gradlew :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build :paper:1.21.11-paper:performanceProbeJar :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava :mods:1.20.6-fabric:compileJava --console=plain
```

Passed: common 724 tests, Fabric 26.1.2 1,120, Paper 26.2 58 and Paper 1.21.11 53 — 1,955 Java tests with zero failures/errors/skips. The older Fabric compile checks passed. Active Stonecutter versions remain 26.1.2-fabric and 26.2-paper. Logs: `/tmp/world-marker-tick-red.log`, `/tmp/world-marker-tick-common.log`, `/tmp/world-marker-tick-build.log`. Gradle reported deprecation/Unsafe warnings; these were not test failures. A complete version/loader matrix was not exercised.

## Native smoke

```sh
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py --root /Volumes/ssd/sw-world-marker-original-per-tick-2026-11-03 --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar --count 10
```

Passed on the pinned Paper 1.21.11 build 111 / Java 21 runtime with ten vanilla protocol bots. Candidate SHA-256: `5a569c7833265b2482e5e107bc8ee4fb7c2a7fbe454bd0b8516e78232c758b08`. [Retained snapshots and client observations](2026-11-03-original-per-tick-native.json) include artifact/runtime/probe/bot hashes.

| Phase | Server ticks | Aggregate marker updates | Position packets submitted | Metadata packets |
| --- | ---: | ---: | ---: | ---: |
| Stationary | 161 | 1,610 | 1,610 | 0 |
| Turn in place | 32 | 320 | 320 | 0 |
| Walking | 71 | 710 | 710 | 130 |

Each owner received one distinct private marker with CENTER billboarding and flags 7 (shadow, see-through, default background), absent from the server world. Enabling the first owner's marker did not expose it to others. Retargeting updated only the intended owner's label. The independent HUD and marker coexisted; disabling either left the other entity intact.

Stopping navigation left zero sessions, marker entities, target-index entries and tick counters. Ten existing online-player scheduler tasks remained until disconnect, when all resource counts were zero. All owned test processes stopped. Native logs contained no ERROR/SEVERE/watchdog records.

Position counts come from the real handler's packet-submission counters, not client render samples or socket-delivery acknowledgements. This run verifies cadence and lifecycle, not frame smoothness, FPS, latency quality, server tick percentiles or capacity. No rendered client or physical-input test was performed.
