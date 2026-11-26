# World marker experiment — 2026-11-02

Implemented on `codex/experimental-world-marker`. No commits or releases were made. No design spec or implementation plan was created.

## Build and unit checks

This exact command passed against the final production source:

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build :paper:1.21.11-paper:performanceProbeJar :mods:1.20.1-fabric:compileJava --console=plain
```

- Common: 723 tests, zero failures/errors/skips.
- Fabric 26.1.2: 1,120 tests, zero failures/errors/skips; full build passed.
- Paper 26.2: 58 tests, zero failures/errors/skips; full build passed.
- Paper 1.21.11: 53 tests, zero failures/errors/skips; full build and separate probe build passed.
- Fabric 1.20.1: compilation passed, including its older teleport packet branch. It was not booted.
- Active Stonecutter projects stayed at Fabric 26.1.2 and Paper 26.2. No source switch/reset was needed.
- `git diff --check` passed.

The new focused checks exercise the 12-block projection, stationary packet suppression, movement, dimension hiding/restoration, host/world replacement, close-range hiding, locale refresh, disable and UUID cleanup. Existing mounted text-display tests still pass. Neither Forge nor NeoForge was built during this experiment; the full version matrix was not exercised.

## Native smoke

```sh
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py --root /Volumes/ssd/sw-world-marker-experiment-2026-11-02-final --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar --count 10
```

Passed on the existing pinned Paper 1.21.11 build 111 and Java 21 runtime, with ten distinct vanilla protocol bots and the freshly built candidate. The disposable server bound to localhost. The runner stopped all bots and the server.

Candidate JAR SHA-256: `bbd35a5aa4d28a71b093fa155016da2485515418ebeda4730fe129bb27df5a98`.

[Retained native/client observations](2026-11-02-native-smoke.json) include the candidate/server/probe/bot hashes, owner isolation, phase snapshots, retarget isolation and cleanup. Complete process logs and the disposable world are under `/Volumes/ssd/sw-world-marker-experiment-2026-11-02-final`; the build log is `/tmp/world-marker-final-build.log`.

Observed behavior:

- Enabling only the first owner sent one text display to that bot and zero to all other bots. Labels contained the correct target name, straight-line distance and diamond.
- All ten owners then had distinct entity IDs. No display was inserted into the server world.
- Eight-second stationary phase: 321 updates, zero movement/metadata/spawn/removal packets after the initial setup.
- Three-second real walking action plus a 0.5-second drain: 145 updates, 140 movement packets, 70 metadata packets; all ten actors changed native position.
- Retargeting the first owner to Target B preserved Target A for the other nine owners.
- The diagnostic command executed successfully.
- Stopping removed all client displays and native display state. Disconnecting with active markers left zero sessions, tasks, entities, map targets/cache entries, target index entries and tick counters.
- Native logs contained no ERROR records or watchdog reports. The six warnings were the expected offline-mode notice and pinned Paper version age notice.

| Phase | Updates | Mean handler time per update | Movement packets | Metadata packets |
| --- | ---: | ---: | ---: | ---: |
| Stationary | 321 | 83.8 µs | 0 | 0 |
| Walking | 145 | 185.2 µs | 140 | 70 |

These are aggregate counter deltas from one short run after spawning, not server tick latency, percentiles, a controlled benchmark, throughput limits or capacity acceptance. Handler timing includes packet submission and excludes snapshot computation, socket delivery and client rendering. Startup/JIT, bot and probe activity can affect the observations.

## Remaining feasibility questions

No rendered client was exercised. The run proves normal client packets and native lifecycle behavior, not the marker's visual appearance, occlusion behavior or frame rate. A rendered in-game comparison should examine perspective changes, latency, camera movement, flight, vehicles, third person and long labels. Five-tick server updates plus client interpolation may make a nearby projected marker visibly lag. There is no edge-of-screen arrow or cross-dimension direction indicator. Live dimension changes and live respawn were not exercised in this smoke; their state transitions have focused unit coverage only.

## Follow-up: fixed scale near the target

Removed distance-based shrinking inside the 12-block projection radius. The marker now keeps its world scale at 1.0, so approaching it naturally increases its apparent size. Its transformation is set only when spawning; distance changes can still update the label. The native observations and candidate hash above describe the earlier revision; the native smoke was not rerun for this follow-up.

All six focused display-handler tests passed, including a new close-range approach check. Fabric 26.1.2, Paper 26.2 and Paper 1.21.11 JARs were rebuilt successfully with the active Stonecutter projects unchanged:

```sh
rtk proxy ./gradlew :common:test --tests '*AbstractTextDisplayNavigationHandlerTest' :mods:26.1.2-fabric:shadowJar :paper:26.2-paper:shadowJar :paper:1.21.11-paper:shadowJar --console=plain
```

## Follow-up — 2026-11-03: close-range visibility and default background

Removed close-range hiding, including when enabling the marker exactly at its destination anchor. World markers now use the vanilla `default_background` flag on both server adapters, with no explicit background-colour override. The client determines background opacity. The existing mounted HUD retains its configured background.

All six focused display-handler tests passed, including approach to the anchor and enabling at zero eye-to-anchor distance. Fabric 26.1.2, Paper 26.2 and Paper 1.21.11 JARs rebuilt successfully. `git diff --check` passed. The native smoke and rendered-client checks were not rerun.

```sh
rtk proxy ./gradlew :common:test --tests '*AbstractTextDisplayNavigationHandlerTest' :mods:26.1.2-fabric:shadowJar :paper:26.2-paper:shadowJar :paper:1.21.11-paper:shadowJar --console=plain
```

## Follow-up — 2026-11-03: mounted HUD without billboarding

Temporarily stopped setting `billboard=center` for the mounted `text_display` HUD in both platform adapters. Fresh displays keep vanilla's default `fixed` orientation. Riding and the configured transformation are unchanged. `world_marker` still uses center billboarding. Restart with the rebuilt server JAR to compare the behavior in game.

Fabric 26.1.2, Paper 26.2 and Paper 1.21.11 assemblies passed; `git diff --check` passed. No tests or rendered-client checks were run for this temporary setting change.

```sh
rtk proxy ./gradlew :mods:26.1.2-fabric:assemble :paper:26.2-paper:assemble :paper:1.21.11-paper:assemble --console=plain
```
