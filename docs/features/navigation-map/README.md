# Navigation map terrain

Navigation maps keep one persistent map ID per player. Targets within the active map region retain its live terrain and update the waypoint marker. Changing regions saves the outgoing terrain and restores the destination's previously explored pixels; a region without history starts blank.

Both Paper and modded backends use `NavigationMapTerrainStore` from `common`. History is separated by map ID, dimension, map center and scale. JSON snapshots contain Base64-encoded pixels and are saved under `<save root>/server_waypoint/navigation-map-terrain/<map-id>/`. The active region remains in Minecraft's normal saved map data. History survives logout, plugin reload and server restart without allocating extra vanilla map IDs.

Only pixels explored with the navigation map are retained. Terrain erased before this change cannot be recovered from these snapshots.

- [Validation](validation/2026-10-14-terrain-history.md)
- [Plans](plans/)
- [Design specifications](specs/)
