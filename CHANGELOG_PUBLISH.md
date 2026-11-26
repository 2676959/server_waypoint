# Changelog

## Server Waypoint 4.1.0

Private waypoints on the server, local waypoints on your client, an in-world navigation marker that needs no mod, and more ways to teleport from the waypoint manager.

### Before you update

- Update the server and the client mod together: 4.1.0 doesn't sync with 4.0.x. If both run Forge, or both run NeoForge, players with the old client mod can't join until they update it. Velocity proxies on 4.0.x keep working.
- Players who were navigating when the server updates need to start navigation again.
- The waypoint manager now closes after you teleport. Turn off **Close after teleport** in the client settings to keep it open.
- NeoForge 1.21–1.21.1 needs NeoForge 21.0.106-beta or newer.

### Private and local waypoints

- Keep your own waypoints on the server with `/wp private`, visible only to you and usable without the client mod. Server owners can set limits and permissions.
- With the client mod, keep local waypoints on your client for any server or world, even servers without Server Waypoint. Profiles keep separate sets, and your client picks the right one for each server.
- The waypoint manager shows Shared, Private and Local waypoints together or one collection at a time, and can copy a waypoint between them.

### In-world navigation marker

- Find your selected waypoint with a marker visible only to you, showing its initials, distance and optional item icon. No client mod or resource pack is required.
- Start with `/wp navigate <dimension> <list> <waypoint> world_marker`, or add it to your current navigation with `/wp navigate use world_marker`. Private waypoints support it through `/wp private navigate` too.
- Adjust size with `/wp navigate config world_marker size 1.5` and projection distance with `/wp navigate config world_marker distance 8`. Your settings follow the navigation session across target changes and reconnects.

### Improvements

- Teleport from the waypoint manager with a key of your choice, a double-click, a right-click or a middle-click.
- Optional chat or toast notifications when waypoints or lists change on the server.
- Choose whether Xaero's Minimap and VoxelMap sync messages appear in chat, as a toast or not at all. Syncing to both map mods shows one message.
- Server owners can limit waypoint change announcements to operators or turn them off, and choose which map mods players can upload from.
- Typing `-` or `+` in a coordinate field adds or removes the minus sign.

### Bug fixes

- Fixed a NeoForge startup crash when MiniMOTD is installed.
- Fixed a crash on NeoForge 1.21–1.21.5 when a text field appears.
- Long waypoint initials no longer overflow their icons in Xaero's World Map.
- Fixed false coordinate errors when switching to local (`^`) coordinates in the waypoint form.
