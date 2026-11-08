# Riding world-marker comparison — 2026-11-03

Historical attempt 2, following the original entity-position-packet method. This implementation and its supporting passenger merger, diagnostics and tests were removed on 2026-11-03. The figures below describe the comparison revision only. The retained implementation is the original free marker, now sending position packets every tick. The subsequent [carrier investigation](2026-11-03-carrier-investigation.md) was also abandoned.

The existing `world_marker` command now enables two independent placement handlers together. The original lower marker uses absolute position packets and center billboarding. The new upper marker is labelled “Riding”, rides the player, uses fixed orientation with no center-billboard override, and places its plane using transformation metadata. It retains scale 1.0, close-range visibility, full brightness, see-through text and the default background. No command argument or navigation enum entry was added.

## Placement and lifecycle

`MountedWorldMarkerPlacement` computes the clamped waypoint bearing in double precision, adds a 1.25-block comparison height, and subtracts the vanilla passenger anchor before converting the small offset to floats. Its quaternion faces the player's physical eye position. Head yaw/pitch is not an input. Small translation/rotation changes are suppressed, and unchanged eye/anchor/target positions skip projection and quaternion calculations. Text is cached independently by rounded distance, target and locale. This does not introduce scale changes at close range.

The riding variant sends no entity-position packets. Client passenger movement handles translation with the player; only changes to the relative offset/orientation need metadata. The plane remains fixed when a stationary player turns their head. During walking it can still show the limits of server-tick updates, especially near a target or with latency; this experiment does not implement a custom client renderer.

The in-game comparison motivating the next investigation was better player following for distant targets, but poorer view-angle tracking and less smooth movement than the free entity's position-packet path. This revision used zero transformation interpolation. Vanilla transformation interpolation was identified as a possible improvement, but was not implemented or validated here. Center billboarding could not simply be enabled on the rider: it also rotates the display's transformation translation around the player. A display riding another display would not inherit the parent's rendered transformation as a logical attachment offset. Those constraints led to the subsequent native-carrier investigation.

The HUD and riding marker use a merged passenger list that includes real passengers. It is sent only when the list, player entity or world changes. Both platforms invalidate this cache on respawn. This avoids losing a mount when the client replaces its player entity while retaining the same entity ID. UUID cleanup drops cached passenger state. `WorldMarkerNavigationHandler` enables, updates and cleans up both placement handlers under the existing method; their implementations remain separate.

## Build and automated checks

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build :paper:1.21.11-paper:performanceProbeJar :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava :mods:1.20.6-fabric:compileJava --console=plain
```

Passed: common 729 tests, Fabric 26.1.2 1,120 tests, Paper 26.2 58 tests, Paper 1.21.11 53 tests — 1,960 Java tests, zero failures/errors/skips. The five new checks cover world-position reconstruction near the world border, facing direction, passenger-pose offsets, finite rotation at a coincident anchor, merged passenger ownership/cache invalidation, and the riding lifecycle/head-turn cache. Fabric 1.20.1, 1.20.2 and 1.20.6 compile checks accepted their respective vanilla passenger-anchor APIs. Active Stonecutter projects remain unchanged. Forge/NeoForge and a complete version matrix were not exercised.

The vanilla protocol bot now observes normal passenger packets. Its full Rust suite passed 31 tests and its release binary rebuilt offline:

```sh
rtk proxy cargo test --release --offline
rtk proxy cargo build --release --offline
```

Run these from `tools/server-performance/bots/versions/mc1.21.11`. Logs: `/tmp/world-marker-riding-final-build.log`, `/tmp/world-marker-riding-bot-tests.log`, `/tmp/world-marker-riding-bot-binary.log`. `git diff --check` passed.

## Native comparison

```sh
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py --root /Volumes/ssd/sw-world-marker-riding-comparison-2026-11-03-final-2 --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar --count 10
```

Passed on the pinned local Paper 1.21.11 build 111 / Java 21 runtime with ten vanilla protocol bots. Candidate JAR SHA-256: `001bcd95561bacb93859fec278e8db48515126fb9fcba35610908454b5c0502a`. [Retained native snapshots and client observations](2026-11-03-riding-comparison-native.json) include hashes, both entity IDs per owner, transformation offsets, fixed billboard/flags, normal client passenger lists and all cleanup stages.

- Each owner received both correct labels, with distinct IDs and no world entity insertion. Other owners received no private display from the first owner's enable.
- All ten new displays reported `FIXED` billboarding and flags 7 (shadow, see-through and default background). Client passenger packets mounted the riding entity to its owning player, excluding the free entity.
- Eight-second stationary phase: 324 updates per variant in aggregate; the rider skipped all 324 placement calculations and sent zero transformation/metadata/mount updates.
- Head-turn phase: 66 updates per variant, unchanged player positions, zero rider transformation/metadata/mount updates and all 66 placement calculations skipped.
- Walking phase: 144 updates per variant. The free variant submitted 140 position packets and 123 metadata packets. The rider changed its relative transformation 40 times, submitted 126 metadata packets and no new mount packets. Labels also contribute to metadata packets, so transformation changes are not a separate packet count. Riding uses no position-packet path.
- Retargeting affected only the intended owner's two markers.
- The HUD ran alongside both markers with its ID and the rider ID together in the same client passenger list. Disabling `world_marker` removed both markers while retaining the HUD; disabling the HUD retained both markers and the correct remaining passenger.
- A real kill/automatic respawn retained active navigation and produced a fresh passenger packet remounting both HUD and rider. The observer required its passenger-packet count to increase, so an unchanged cached list could not pass this check.
- Stop and disconnect checks left zero sessions, tasks, entities, maps, target-index entries and tick counters. All owned processes stopped. Native logs had zero ERROR/SEVERE/watchdog records.

An earlier respawn attempt failed because the test runner used the old `doImmediateRespawn` gamerule name; the corrected runner uses the owning 1.21.11 source's `immediate_respawn`. That failed attempt remains at `/Volumes/ssd/sw-world-marker-riding-comparison-2026-11-03-final`; it is not reported as passing. The earlier functional run without the respawn extension is retained separately at `/Volumes/ssd/sw-world-marker-riding-comparison-2026-11-03-a`.

This is one short functional/counter comparison. It does not establish a capacity limit, server tick percentiles, client FPS, appearance, occlusion quality or physical-input behavior. A rendered client was not exercised. Pose-height and close-range math have unit coverage; live crouching, vehicle travel, cross-dimension travel and every loader remain separate validation. The retained `updateNanos` values measure handler wall time, including submission, not total tick cost or network delivery.
