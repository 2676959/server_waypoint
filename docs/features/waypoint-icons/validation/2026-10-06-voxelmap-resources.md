# VoxelMap icon resource loading — 2026-10-06

The shared icon resolver requested the newer VoxelMap resource layout on every
Minecraft version. The configured 1.21 dependency (`QZuVdvFR`, VoxelMap
`1.21-1.13.1`) packages `assets/voxelmap/images/waypoints/waypointstar.png`,
not `assets/voxelmap/images/waypoints/selectable/star.png`. Resource lookup
therefore returned empty and displayed initials for every VoxelMap icon.

The cached configured Fabric dependencies from 1.20.1 through 1.21.9 use
`images/waypoints/waypoint<suffix>.png`. The configured 1.21.11 dependency
uses `images/waypoints/selectable/<suffix>.png`. The shared resolver now
selects these paths with a Stonecutter `>=1.21.11` predicate. Both default
IDs (`voxelmap:waypoint` and `voxelmap:point`) resolve to `waypoint.png` in
the earlier layout and `selectable/point.png` in the newer layout. All
existing consumers use this resolver, including the picker, forms, local
and remote rows, details, and world markers.

`VoxelMapIconResourceTest` invokes the production resolver with resource
lookup backed by the actual dependency images on the test classpath. It
checks all 33 selectable IDs and opens each resolved image to verify its
PNG signature. Two additional tests cover missing resources and unsupported
IDs. Before the fix, all 33 selectable-icon cases failed on 1.21 with
`expected: VOXELMAP but was: INITIALS`; the fallback cases passed.

## Verification

| Fabric target | Icon resource regression | Other verification |
| --- | --- | --- |
| 1.20.1 | 35 passed | Full suite: 664 tests, 5 existing Xaero fixture failures |
| 1.20.2 | 35 passed | Full suite: 664 tests, 5 existing Xaero fixture failures |
| 1.20.4 | 35 passed | Full suite: 664 tests, 5 existing Xaero fixture failures |
| 1.20.6 | 35 passed | Full suite: 666 tests, 5 existing Xaero fixture failures |
| 1.21 | 35 passed | Full suite: 666 tests, 5 existing Xaero fixture failures; `remapJar` passed |
| 1.21.2 | 35 passed | Full suite: 663 tests, 5 existing Xaero fixture failures |
| 1.21.3 | 35 passed | Full suite: 663 tests, 5 existing Xaero fixture failures |
| 1.21.5 | 35 passed | Full suite: 663 tests passed |
| 1.21.6 | 35 passed | Full suite: 663 tests passed |
| 1.21.9 | 35 passed | Full suite: 665 tests passed |
| 1.21.11 | 35 passed | Full suite: 665 tests passed |
| 26.1.2 | 35 passed | Full suite: 670 tests passed |

The expanded check covers every configured Fabric 1.20.x and 1.21.x target:
385 icon-resource test cases passed, with no errors or skips. Production and
test sources compiled on all targets. No further implementation changes were
needed. VoxelMap integration is registered only on Fabric in this project;
these results do not claim Forge or NeoForge integration testing.

Commands run:

```sh
rtk ./gradlew :mods:1.21-fabric:test --tests '*VoxelMapIconResourceTest'
rtk ./gradlew :mods:1.21-fabric:test --tests '*VoxelMapIconResourceTest' --tests '*WaypointIconRendererTest' --tests '*VoxelMapIconIdsTest'
rtk ./gradlew :mods:1.21-fabric:test :mods:1.21-fabric:remapJar --continue
rtk ./gradlew :mods:1.21-fabric:remapJar :mods:1.21.11-fabric:test :mods:26.1.2-fabric:test --continue
rtk ./gradlew :mods:1.20.1-fabric:test :mods:1.21.9-fabric:test --tests '*VoxelMapIconResourceTest' --tests '*WaypointIconRendererTest' --tests '*VoxelMapIconIdsTest'
rtk ./gradlew :mods:1.20.1-fabric:test :mods:1.20.2-fabric:test :mods:1.20.4-fabric:test :mods:1.20.6-fabric:test :mods:1.21-fabric:test :mods:1.21.2-fabric:test :mods:1.21.3-fabric:test :mods:1.21.5-fabric:test :mods:1.21.6-fabric:test :mods:1.21.9-fabric:test :mods:1.21.11-fabric:test --continue --max-workers=3
```

The earlier command with two test tasks applies its test filters only to the
last task, so 1.20.1 ran its full suite. The expanded command deliberately runs
all full suites and returns failure for seven targets because of the five
existing `XaeroMinimapWorldTest` fixture failures on each target through 1.21.3,
documented in the
[sync icon validation](2026-10-06-voxelmap-sync-icon.md):

- `missingAutomaticWorldResolvesRequestedDimension`
- `enteringNetherDoesNotResolveToStaleOverworld`
- `returningToOverworldDoesNotResolveToStaleNether`
- `matchingAutomaticWorldPreservesXaerosConnectedSubworld`
- `nonCurrentDimensionResolvesItsOwnWorld`

Each fails with `InstantiationException` while allocating the old dependency's
abstract `MinimapWorld`, before invoking production code. Those fixtures were
left unchanged.

The distributable is
`mods/versions/1.21-fabric/build/libs/server_waypoint-4.0.0-fabric-mc1.21-1.21.1.jar`.
The active Stonecutter project remains `26.1.2-fabric`; no source switch was used.

These checks prove resource resolution against packaged images and versioned
source compilation. They do not start a game, upload textures to the GPU, or
verify appearance. In-game verification remains: on 1.21 with VoxelMap installed,
select a named icon and the default point icon, then confirm they appear in the
picker, saved waypoint rows, preview, and world markers.
