# Initials, distance and item marker — 2026-11-04

The initial row order described below was subsequently changed to match the client mod: item icon, initials badge, distance. See the final section; the retained native snapshot predates this ordering change.

The in-world marker now has two text displays and an optional item display, arranged as three camera-aligned rows:

1. Saved waypoint initials, using the client world renderer's default `0x80` alpha waypoint-coloured background and the same `ColorUtils.getSafeTextColor` calculation, without text shadow.
2. White straight-line distance, retaining the text display's default background and shadow.
3. The waypoint's registered item icon, displayed in GUI context. VoxelMap, missing and unregistered/non-item icons leave this row absent. Existing item entities are removed when switching to these icon kinds.

The old full-name/diamond label was removed. `NavigationTarget` now snapshots saved initials and icon alongside the other waypoint properties and preserves them across list identity changes. Constructor call sites were migrated; no compatibility constructor was added. Stored navigation still resolves presentation from current waypoint data. The standalone mounted HUD was not changed.

All parts use CENTER billboarding, full brightness, one-tick teleport interpolation and the existing bounded motion forecast. They receive identical predicted absolute positions. Static local transformations arrange the badge at +0.3 blocks/scale 1, distance at 0/scale 0.8, and item at -0.3 blocks/scale 0.4. These style scales do not vary with distance. Layout does not require per-tick transformation metadata. Normal item depth rendering applies; text remains see-through.

The badge's custom background is necessary for the requested client-style colour; only this row disables the default-background flag. Distance keeps that flag. Vanilla text-display font/padding and client item models still determine final rendering; pixel-identical rendering against every client resource pack is not established.

The common lifecycle owns the two text entities and optional item entity, caches text/locale/colour/icon changes, and cleans them up together. Position counters aggregate actual per-part submissions. At 20 TPS, the group sends 40 position packets/second without an item or 60 with one. It does not insert entities into the server world or load waypoint chunks.

## Automated verification

Updated lifecycle tests failed against the old single-label implementation and then passed. Coverage includes separate badge/distance components, shared positions, contrast/background values, one-tick scheduling, locale changes, existing motion prediction, lifecycle cleanup, creating/restoring an item, removing it on VoxelMap/no-icon changes and suppressing unresolved items without repeated registry lookups. Existing model tests check saved initials survive snapshotting and list identity changes.

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build :paper:1.21.11-paper:performanceProbeJar --console=plain
rtk proxy ./gradlew :mods:26.1.2-fabric:compileJava :paper:26.2-paper:compileJava :paper:1.21.11-paper:compileJava :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava :mods:1.20.6-fabric:compileJava --console=plain
```

Passed: common 730 tests, Fabric 26.1.2 1,120, Paper 26.2 58 and Paper 1.21.11 53 — **1,961 Java tests**, with zero failures/errors/skips. Older Fabric compile checks passed. Active Stonecutter versions were unchanged. An intermediate Paper source-edit splice error was corrected before the passing builds. Conditional blocks and `git diff --check` were checked. Gradle emitted deprecation/Unsafe warnings. Logs: `/tmp/world-marker-parts-red.log`, `/tmp/world-marker-parts-common.log`, `/tmp/world-marker-parts-compile.log`, `/tmp/world-marker-parts-build.log`.

The diagnostic bot now independently observes item-display spawn/removal packets, separate from text displays. A new observer test first failed and then passed. The full Rust suite passed 32 tests and its release binary was rebuilt offline:

```sh
rtk proxy cargo test --release --offline
rtk proxy cargo build --release --offline
```

Run from `tools/server-performance/bots/versions/mc1.21.11`. Logs: `/tmp/world-marker-parts-bot-red.log`, `/tmp/world-marker-parts-bot-tests.log`, `/tmp/world-marker-parts-bot-build.log`.

## Native runtime verification

```sh
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py --root /Volumes/ssd/sw-world-marker-three-parts-2026-11-04 --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar --count 2
```

Passed on pinned Paper 1.21.11 build 111 / Java 21 with two vanilla protocol bots. [Retained native/client evidence](2026-11-04-three-part-marker-native.json) records candidate SHA-256 `186d1a78ea8fe44547d78fb7c97edb23cef7328d90ad6e2508e653ac212bc4ef` and runtime/probe/bot hashes.

- Each owner received two text rows and a `minecraft:diamond` item display. Actual native metadata confirmed saved initials, custom badge ARGB/foreground contrast, badge flags 2, distance flags 7, GUI item context, all row transforms/scales, duration 1, CENTER billboarding and no world insertion. Logical entity positions matched across rows.
- Normal client packets delivered the two correct text rows and private item entity IDs. Enabling the first owner's marker did not expose it to the other owner.
- Stationary phase: 163 ticks, 326 group updates and 978 position submissions across six parts, with unchanged metadata. Motion-following, head-turn and walking checks passed.
- Retargeting one owner to a white waypoint with a VoxelMap icon produced the contrasting black initials badge and no item row. The bot observed the old item entity disappear. Retargeting to a waypoint without an icon also left no item entity. Returning to the item waypoint restored one item entity.
- The independent HUD coexisted with all marker rows. Disabling either feature retained the other; stop removed both text and item entities. Disconnect left every tracked resource, including motion history, at zero. All owned processes stopped. Native logs contained no ERROR/SEVERE/watchdog records.

This verifies native configuration, packet visibility, identity and lifecycle. The bot observes item entity spawn/removal; item identity/context are read from native server state. No rendered-client appearance, occlusion quality, FPS or capacity test was performed.

## Client row order

The user replaced the initial ordering requirement with the client mod's order. `WorldWaypointMarkerLayout` places the item above the initials badge, and `OptimizedWaypointRenderer` places distance below the label/badge. Both server backends now use that order: item, initials, distance. The item's static local Y offset changed from -0.3 to +0.8 blocks, above the badge at +0.3; distance remains at 0. The native runner's expected offset and README were updated.

`rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build --console=plain` passed, as did `git diff --check`. Log: `/tmp/world-marker-client-row-order.log`. Native/rendered checks were not repeated for this static layout adjustment.

## Waypoint block anchor — 2026-11-05

At the user's request, the marker anchor was moved from `(x + 0.5, y + 2, z + 0.5)` to the waypoint block centre `(x + 0.5, y + 0.5, z + 0.5)`, matching `OptimizedWaypointRenderer.getWaypointX/Y/Z`. The shared projection computes these coordinates in double precision. Row order/style, 12-block distant projection, close-range visibility and motion prediction remain. The native runner's reference projection was updated.

The existing nearby-anchor test first failed with the old elevation, then passed with the block-centre anchor. `rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:build :paper:26.2-paper:build :paper:1.21.11-paper:build --console=plain` and `git diff --check` passed. Logs: `/tmp/world-marker-block-anchor-red.log`, `/tmp/world-marker-block-anchor-build.log`. Native/rendered checks were not repeated for this anchor adjustment.
