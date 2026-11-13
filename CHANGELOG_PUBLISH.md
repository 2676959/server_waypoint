# Changelog

## Server Waypoint 4.0.1

A maintenance update with in-world navigation markers, smoother server performance and more reliable waypoint syncing with Xaero's Minimap and VoxelMap.

### In-world navigation

- Find your selected waypoint with a marker visible only to you, showing its initials, distance and optional item icon. No client mod or resource pack is required.
- Start with `/wp navigate <dimension> <list> <waypoint> world_marker`, or add it to your current navigation with `/wp navigate use world_marker`. Private waypoints support it through `/wp private navigate` too.
- Adjust size with `/wp navigate config world_marker size 1.5` and projection distance with `/wp navigate config world_marker distance 8`. Your settings follow the navigation session across target changes and reconnects.

### Improvements

- Reduced command-related pauses when players join, change dimensions or have their permissions updated. Existing command options and suggestions remain available.
- Reduced the server load from navigation compasses and maps, especially during inventory interactions and on servers with many active containers.
- Reduced memory usage when searching large waypoint collections and showing command suggestions.
- Xaero's Minimap avoids unnecessary waypoint refreshes when you respawn or change dimensions.

### Bug fixes

- Fixed server waypoints sometimes missing from Xaero's Minimap or VoxelMap after reconnecting when no waypoints had changed. You no longer need to use **Sync now** to make them reappear.
- Automatic map syncing now waits until the map mod has loaded the current world.
- Fixed navigation compasses and maps being placed into empty bundles on Paper and Folia.

### Navigation item changes

- Navigation items already stored inside bundles, shulker boxes or other containers are left in place. Putting them into a bundle or an open shulker box is still blocked.
- Paper and Folia no longer check navigation items during hopper or other automatic container transfers.
- Fabric, Forge and NeoForge no longer repeatedly scan allays and dropped items. Giving navigation items to allays is still blocked, and dropped navigation items are still removed.

### Updating

- Compatible with 4.0.0 clients, servers and Velocity proxies. Update the client mod for the map-sync fixes and the server mod or plugin for the server improvements.
