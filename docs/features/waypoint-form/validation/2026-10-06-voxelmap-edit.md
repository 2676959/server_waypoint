# VoxelMap tracked waypoint editing — 2026-10-06

VoxelMap's Edit action for a tracked server waypoint now opens `WaypointEditScreen`
for the selected waypoint, with the VoxelMap list screen as its return screen.
Previously `VoxelMapGuiWaypointsMixin` opened `WaypointManagerScreen`.

`VoxelMapWaypointHelper.resolveSyncedEditTarget` resolves the encoded list and
waypoint names against the server data cached by the client. It also matches
VoxelMap's dimension storage names, preserving custom namespaces, so duplicate
names across dimensions cannot open the wrong waypoint. It supplies a detached
copy of the complete server waypoint, preserving unscaled coordinates, formatted
display name, initials, color, yaw, visibility, keywords, description, and icon.
The list display name is also passed to the form.

The injection now runs at `editWaypoint` HEAD, before VoxelMap marks its native
edit flow active. The `editWaypoint(Waypoint)` target was verified using `javap`
against all 14 configured Fabric VoxelMap dependency versions. Local waypoints
continue through the native VoxelMap editor. Tracked markers with missing or
ambiguous cached server targets stay on the VoxelMap list and cannot open a
local editor for the marker.

## Verification

All 23 VoxelMap tests passed on every supported Fabric target: 1.20.1, 1.20.2,
1.20.4, 1.20.6, 1.21, 1.21.2, 1.21.3, 1.21.5, 1.21.6, 1.21.9, 1.21.11,
26.1.2, 26.2, and 26.3. This includes six new tests for native local editing,
server-only metadata, duplicate names in dimensions/lists, custom dimensions,
stale markers, and ambiguous dimension markers. Production and test sources
compiled for every target.

A temporary Gradle init script applied `includeTestsMatching("*VoxelMap*Test")`
to every versioned Test task for the matrix run. The targeted edit tests can
also be run directly:

```sh
rtk ./gradlew :mods:1.21.11-fabric:test --tests '*VoxelMapWaypointEditTargetTest'
```

Full test suites then passed with:

```sh
rtk ./gradlew :mods:1.21.5-fabric:test :mods:1.21.11-fabric:test :mods:26.1.2-fabric:test :mods:26.3-fabric:test
```

| Minecraft | Full suite tests | Failures |
| --- | --- | --- |
| 1.21.5 | 614 | 0 |
| 1.21.11 | 616 | 0 |
| 26.1.2 | 621 | 0 |
| 26.3 | 621 | 0 |

`git diff --check` passed. The active Stonecutter project remains
`26.1.2-fabric`. The earlier sync-icon fix and unrelated working-tree edits were
preserved. No commit was created.

These checks exercise target resolution and local edit fall-through; they do
not apply Mixins in a running game or exercise screen switching and saving.
For live verification, select a tracked waypoint in VoxelMap and press Edit:
confirm the correct server form and fields, Cancel back to VoxelMap, and save
an edit. Also check a local waypoint still opens VoxelMap's own editor and a
same-named waypoint in another dimension resolves correctly.
