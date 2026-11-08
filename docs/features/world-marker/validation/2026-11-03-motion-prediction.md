# Sprint-following motion prediction — 2026-11-03

The user reported the marker trailing the player while sprinting despite one-tick updates. Source tracing showed that the handler projected from the last server-observed player position, and the client then interpolated the display toward that already old target. Sending more copies of that target does not compensate for either transport delay or display interpolation.

The local 26.1.2 vanilla `ServerGamePacketListenerImpl.handleMovePlayer` applies client-reported positions; `ServerCommonPacketListenerImpl` measures round-trip keepalive latency; `ClientPacketListener.handleTeleportEntity` passes movement to entity interpolation; `Display.tick` runs its interpolation handler. `InterpolationHandler` moves toward a received target over its configured steps. Display entities do not independently integrate movement velocity in their tick, so merely putting nonzero velocity in their teleport packet would not supply player following.

The fix estimates horizontal velocity from consecutive server-tick eye samples, forecasts the observer ahead by the marker's interpolation duration plus bounded measured RTT, then runs the existing 12-block waypoint projection from the forecast eye. This accounts for the inbound age of the client-reported position as well as the outbound trip. Forecast time is at most six ticks, measured RTT contributes at most 200 ms at 20 TPS, and horizontal lead is at most two blocks. Vertical eye position is not extrapolated. Stationary samples clear velocity, sample gaps over three ticks and position discontinuities over two blocks per elapsed tick discard motion, and entity/world replacement or cleanup clears history.

The five overlapping comparison variants and their 1–5-tick packet schedules remain. Their targets can differ while moving because each compensates for its own interpolation duration. All retain normal waypoint text, center billboarding, default background, scale 1.0 and close-range visibility. No riding or transformation-translation method was restored. Packet rates are unchanged.

Four new handler tests cover constant-speed sprinting with simulated 100 ms RTT, stopping/teleport/world resets, high-latency/displacement bounds and sample gaps, plus current crouch height and nearby destination anchors. Three of these tests failed against the old target calculation; after the fix, the full common suite passed. The sprint test compares the received target with a known constant-speed client's expected position after transport and interpolation; it does not render a client or simulate jitter.

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build :paper:1.21.11-paper:performanceProbeJar :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava :mods:1.20.6-fabric:compileJava --console=plain
```

Passed: common 728 tests, Fabric 26.1.2 1,120, Paper 26.2 58 and Paper 1.21.11 53 — 1,959 total with zero failures/errors/skips. Older Fabric compile checks passed, including both latency API paths. Active Stonecutter versions were unchanged. Touched conditional blocks, runner syntax and `git diff --check` were checked. Gradle emitted deprecation/Unsafe warnings. Logs: `/tmp/world-marker-prediction-red.log`, `/tmp/world-marker-prediction-common.log`, `/tmp/world-marker-prediction-build.log`.

This is bounded prediction rather than local client attachment. Abrupt changes of direction, network jitter, large RTT and low TPS can leave residual lag or correction overshoot. Vertical following remains based on the server-observed height. Visual sprinting smoothness and FPS need in-game inspection.

## Native runtime check

```sh
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py --root /Volumes/ssd/sw-world-marker-motion-prediction-2026-11-03 --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar --count 2
```

Passed on pinned Paper 1.21.11 build 111 / Java 21 with two vanilla protocol bots. [Retained evidence](2026-11-03-motion-prediction-native.json) records candidate SHA-256 `bbb3c9a2f465f1240d7f7150b09dd490332e333f67e4c4cf1a4b59f13db02a66`. Both native players were walking with observed horizontal velocity approximately `(-0.15263, -0.15263)` blocks/tick. The actual every-tick text-display positions led the unpredicted projected positions in the movement direction by approximately 0.250 and 0.259 blocks, within the displacement cap. This checks the native output path, not rendered perceived lag.

The run also passed the unchanged five duration/interval pairs, owner-only normal labels, default background/center billboarding, absence from the server world, stationary cadence over 161 ticks, head turns, walking, retargeting, simultaneous HUD use and independent disable. Stop/disconnect cleared motion-history state along with the other navigation resources. All tracked counts were zero after disconnect, all owned processes stopped, and native logs contained no ERROR/SEVERE/watchdog records. Native movement was walking; sprint-speed/100 ms RTT coverage is deterministic unit simulation rather than an actual rendered sprint session.
