# Features

- [Client config](client-config/) — client settings screen, settings list widget and mod-list entry points.
- [Client loading](client-loading/) — production startup failures, loader API boundaries and validation evidence.
- [Command feedback](command-feedback/) — `/wp` chat feedback: menu, lists, details, pickers, remote browsing and console output.
- [Cross-server waypoints](cross-server/) — discovery and teleportation over Velocity.
- [GUI tooltips](gui-tooltips/) — themed tooltips in the mod's screens: surface, scheduling, placement and layering.
- [Jar packaging](jar-packaging/) — what each release jar ships, the `cross-server` module boundary and the release gate's content checks.
- [Upload transport](upload/) — chunked upload/download transport and startup fixes.
- [VoxelMap sync](voxelmap-sync/) — VoxelMap waypoint sync on Fabric, Forge and NeoForge: target matrix, gating, mixin registration and checks.
- [Waypoint form](waypoint-form/) — add and edit screens: layout, checks, feedback and keyboard use.
- [Waypoint icons](waypoint-icons/) — item and VoxelMap icon selection and display plan.
- [Waypoint manager](waypoint-manager/) — manager screen states, layout and empty states.

Each feature owns `plans/`, `specs/` and `validation/`, indexed by its README.
