# Server Waypoint

[![discord-singular](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-singular_vector.svg)](https://discord.com/invite/tKtSSYDkHx)

## Make your world easier to explore.

Share useful places with your community and help players find their way around your world.

[Get started](https://github.com/2676959/server_waypoint/blob/4.0.0/README.md#your-first-waypoint) | [Source code](https://github.com/2676959/server_waypoint)

Server Waypoint provides shared destinations, cross-server teleportation, navigation and clickable chat commands. Players can use the server features with a vanilla client. The optional client mod adds in-world markers, a waypoint manager and map integrations.

## Across servers, closer together

The optional **Velocity** integration lets players browse shared waypoints on connected servers and teleport between them when permitted. Cross-server browsing and teleportation are available through clickable chat without the client mod.

![Clickable chat browser showing four connected remote servers](https://raw.githubusercontent.com/2676959/server_waypoint/4.0.0/docs/gallery/4.0.0/cross-server-chat-browser.png)

**Use chat across the network.** Run `/wp remote` to browse without the client mod. The chat view lists connected servers with availability indicators and waypoint counts, with an **All servers** option for browsing them together.

![Cross-server waypoint manager showing the building server catalog and teleport action](https://raw.githubusercontent.com/2676959/server_waypoint/4.0.0/docs/gallery/4.0.0/cross-server-waypoint-manager.png)

**Browse another server's places.** In the client manager, choose a remote server and explore its lists. Here, the building server's Collaborative Build Sites list contains Public Bridge Works. The details panel shows the server's availability, the destination's properties and a **Teleport** action.

Cross-server teleportation follows the server's permissions. To edit a remote waypoint, join the server that owns it.

[Set up cross-server waypoints](https://github.com/2676959/server_waypoint/blob/4.0.0/docs/tips/server-guide.md#cross-server-teleport-setup)

## Follow the route your way

Choose the guidance that feels comfortable as you travel. The labeled example shows all five server navigation methods pointing to **Festival Green** at the same time.

![All five server navigation methods with labels for the compass, map, boss bar, action bar and floating label](https://raw.githubusercontent.com/2676959/server_waypoint/4.0.0/docs/gallery/4.0.0/server-navigation-labeled.png)

- **Compass and map:** follow the compass toward the destination or find its marker on the navigation map.
- **Boss bar and action bar:** keep the destination's name, direction, distance and height difference on screen.
- **Floating label:** see the destination's guidance within the world.

Use `/wp navigate` to change your navigation methods or stop navigation. All five methods are provided by the server and work without the client mod.

## Browse and manage with clickable commands

Browse and manage waypoints from Minecraft's chat. The server provides clickable lists, hover details and waypoint actions, so players can get started with a vanilla client.

![Interactive chat waypoint browser with a destination tooltip](https://raw.githubusercontent.com/2676959/server_waypoint/4.0.0/docs/gallery/4.0.0/interactive-chat-waypoint-browser.png)

![Waypoint details with clickable edit, navigation and teleport controls](https://raw.githubusercontent.com/2676959/server_waypoint/4.0.0/docs/gallery/4.0.0/interactive-chat-waypoint-details.png)

**Browse before you travel.** Open `/wp` to view waypoints grouped by dimension and list, with icons and distances beside their names. Search or change the sort order, and hover over a destination to read its description and coordinates. The example shows Slime Foundry in the Industrial Quarter list.

**Details and actions in one place.** Select a waypoint to see its properties and the available edit controls. The detail view includes actions to navigate, teleport, download or remove the waypoint. Hover hints explain controls such as **Here**, which moves the saved position to where you are standing.

The server's permissions determine which actions each player can use. Messages follow each player's Minecraft language setting.

[Explore server commands](https://github.com/2676959/server_waypoint/blob/4.0.0/docs/tips/server-guide.md#commands)

## Give every destination its own style

Shared waypoint display names and descriptions support **JSON text components**, so you can combine colors and text styles for each destination. The screenshots show how the client mod displays that shared formatting in the manager and in-world markers.

![Waypoint manager with a JSON-styled display name and multicolored description](https://raw.githubusercontent.com/2676959/server_waypoint/4.0.0/docs/gallery/4.0.0/json-styled-waypoint-details.png)

![Colorful bold italic waypoint display name rendered in the world](https://raw.githubusercontent.com/2676959/server_waypoint/4.0.0/docs/gallery/4.0.0/json-styled-waypoint-marker.png)

**Styled names in the manager and the world.** The example uses **Fancy Waypoint**, with a blue-to-purple display name in bold italics. Its formatting appears in the waypoint list, the details panel and the in-world marker.

**Descriptions with their own formatting.** The details panel shows a multicolored description with several text styles. You can use the same format to highlight directions or add context about a place.

**A separate identifier for commands.** The styled display name belongs to the waypoint identified as `fancy_wp`. You can keep a simple identifier for commands while editing the display name and description through the waypoint's edit controls or `/wp edit`.

[Learn about identifiers and display names](https://github.com/2676959/server_waypoint/blob/4.0.0/docs/tips/server-guide.md#identifiers-and-display-names)

## A whole world, neatly organized

The client manager brings waypoint lists and destination details into one view. Choose a dimension, search by name or expand a list to browse its places.

![Waypoint manager with Harbour Town expanded and Lantern Market selected](https://raw.githubusercontent.com/2676959/server_waypoint/4.0.0/docs/gallery/4.0.0/waypoint-manager.png)

**Lists for the places you share.** Harbour Town is expanded here, with Lantern Market selected alongside Harbour Square, Mapmakers Library and other destinations. Item icons and distances help distinguish entries. The details panel shows the selected waypoint's identifier, display name, initials, coordinates, visibility, color and keywords.

Open the manager with **Right Shift** or `/wp_gui`. Hover over a local waypoint and press **T** to teleport to it. Available actions, including editing, navigation and teleportation, follow the server's permissions.

## Your waypoints. Your favorite map.

Use shared destinations with **Xaero's Minimap** or supported **VoxelMap-Updated** builds. The example shows **Shared Waypoint** in both minimaps alongside its in-world markers.

![Shared waypoint displayed in the world and in Xaero and VoxelMap minimaps](https://raw.githubusercontent.com/2676959/server_waypoint/4.0.0/docs/gallery/4.0.0/shared-waypoint-map-integrations.png)

![Client settings with Xaero and VoxelMap automatic and manual synchronization controls](https://raw.githubusercontent.com/2676959/server_waypoint/4.0.0/docs/gallery/4.0.0/map-integration-sync-settings.png)

**Choose when to sync.** Press **C** in the waypoint manager to open the client configuration screen. Client settings provide separate automatic synchronization toggles and manual **Sync** buttons for Xaero and VoxelMap. The screenshot has automatic sync enabled for both, while syncing into Xaero's default set is disabled.

You can also upload map waypoints when you have permission. **Xaero's World Map** offers actions for adding and editing server waypoints when installed alongside Xaero's Minimap.

[Check map and version compatibility](https://github.com/2676959/server_waypoint/blob/4.0.0/README.md#feature-availability-by-minecraft-version)

## Shared places, visible as you explore

![Server Waypoint in-world markers with item icons over a village](https://raw.githubusercontent.com/2676959/server_waypoint/4.0.0/docs/gallery/4.0.0/in-world-waypoint-markers.png)

The client mod shows waypoints in the world with item icons, colors and initials. Look toward a marker to see its display name and distance.

---

## Install it your way

**For a server**

Install the matching **Fabric, Forge or NeoForge** mod, or the **Paper, Folia or Purpur** plugin. Once you join, run `/wp` to browse waypoints and get started.

**For a connected network**

Install the optional **Velocity** plugin and configure the backend servers to share waypoints. Cross-server support is off by default and requires matching versions. The proxy plugin requires Java 25.

**For client markers and the manager**

Install the client mod with the dependencies listed for your download. Fabric requires Fabric API. You can also use the mod in singleplayer.

[Server setup and permissions](https://github.com/2676959/server_waypoint/blob/4.0.0/docs/tips/server-guide.md)

## Build somewhere worth finding

[Community Discord](https://discord.com/invite/tKtSSYDkHx) | [Report a bug](https://github.com/2676959/server_waypoint/issues)

You're welcome to join the community, report an issue or help translate. Messages follow each player's Minecraft language setting; see the [translation credits and review status](https://github.com/2676959/server_waypoint/blob/4.0.0/TRANSLATOR_CREDITS.md) for language coverage.
