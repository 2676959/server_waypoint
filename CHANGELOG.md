# Changelog

## Server Waypoint 4.0.0

A major release: navigation that works without the client mod, waypoint icons, uploads from map mods, VoxelMap support, cross-server waypoints through Velocity, and redesigned commands and screens.

### Before you update

- **Update servers and clients together.** 4.0.0 changes the network protocol and doesn't sync with 3.x. Use matching builds on Velocity and every participating backend too.
- **`/wp edit` has a new syntax.** It changes one property at a time: `/wp edit waypoint <dimension> <list> <waypoint> set <property> [<value>]`, or `clear <property>` for an optional one. The 3.x form that took every property at once is gone, so update command blocks and scripts that use it. `/wp add` still accepts its 3.x syntax.
- **Paper now applies the `CommandPermission` levels.** In 3.x, players without an explicit permission node needed operator status. They now get the level set in `config.json`, so with the defaults every player can use `/wp add`, `/wp edit`, `/wp remove` and `/wp navigate`. To limit these to operators again, set their levels to 2 or deny the nodes in your permission plugin.
- **Xaero's Minimap sets synced by 3.x stay behind.** Server Waypoint now marks the sets it creates and changes only those. Sets synced by 3.x have plain list names, so they remain next to the new ones as your own sets. Delete them in Xaero's Minimap if you don't need them.
- **Custom translation files need updating.** Most server message keys were renamed, so language files made for 3.x no longer override them. Start a new file from the 4.0.0 `en_us.json`.
- **Choose the Paper JAR for your Minecraft version.** The ranges are now 1.21–1.21.8, 1.21.9–1.21.10, 1.21.11 plus 26.1–26.1.2, and 26.2–26.3. The new 1.21.9 build provides native chat icons.
- Waypoint files, `config.json` and client settings from 3.x load as they are.

### Navigation

- `/wp navigate <dimension> <list> <waypoint>` guides you to a waypoint with a compass, a map, the boss bar, the action bar or a floating label. It runs on the server, so it works without the client mod.
- `/wp navigate` shows your target and switches methods on and off. `/wp navigate config text_display` moves, turns and resizes the floating label.
- `defaultNavigationMethods` in `config.json` chooses the methods a new session starts with (default: `actionbar`). `CommandPermission.navigate` sets who can navigate (default: level 0).
- Navigation resumes when you rejoin, and the compass, map and label follow edits to the waypoint.
- Compass and map tooltips show the waypoint and dimension with clearer formatting.

### Waypoints

- Choose an item or a VoxelMap image as a waypoint's icon. It shows on the in-world marker, in the waypoint manager and in VoxelMap.
- Waypoints have an optional display name, keywords and description. Lists can have a display name too.
- `/wp remove` answers with a Restore link that works once within 10 minutes.

### Commands

- `/wp` opens a menu: most features are a click away, and each reply links to the next steps. `/wp help [<topic>]` explains every command with usages you can click and examples.
- The console, RCON and command blocks get the same information as plain text.
- On Minecraft 1.21.9 and later, chat feedback shows supported vanilla item sprites and player heads without the client mod. Older versions and unsupported icons retain text or initials.
- Commands run through `/execute as` send feedback to the executing player and a copy labelled **Viewed as** to the original sender. Teleport confirmations name the player who teleported.
- `/wp list` can search, sort by name, distance or colour, change the page size, and show lists, a tree or flat rows. `/wp list dimensions` lists every dimension with its waypoint count. `defaultPageLimit` in `config.json` sets the default page size.
- `/wp details` shows every property of a waypoint or list, with buttons to edit each one.
- `/wp add` on its own picks a list for a waypoint where you stand. `/wp add <x y z> <list> <name>` fills in the initials, a random colour and your facing; put a dimension first to add it elsewhere. The full form also takes keywords, a description and an icon.
- `/wp edit waypoint ... set color` and `set yaw` without a value open a colour picker and a facing picker. `set color random` picks a colour.
- Errors explain the problem and link to a way to fix it.
- `/wp reload` now applies `defaultPageLimit`, `defaultNavigationMethods`, `CommandPermission`, `addWaypointFromChatSharing` and `compressChunkedMessages` immediately.

### Map mods

- **VoxelMap** (Fabric; Forge 1.21.11, 26.1-26.1.2 and 26.2; NeoForge 1.21.2-1.21.4, 1.21.11, 26.1-26.1.2, 26.2 and 26.3): server waypoints sync to VoxelMap. Its waypoint list marks them with a sync icon, and editing one opens Server Waypoint's manager.
- **Upload:** `/wp upload <xaero|voxelmap>` imports waypoints from the map mod on your client. Add `force local` to overwrite conflicts with your copy, or `force local delete` to also remove what your map doesn't have. It also works in singleplayer and for the host of a LAN world. Uploading needs level 2 (`upload`) and deleting needs level 4 (`uploadDelete`).
- **Xaero's World Map:** right-click the map to add a waypoint to the server at that spot, or right-click a waypoint to add it to the server or edit its server copy.
- Xaero's Minimap and VoxelMap show synced sets and waypoints under their server names.
- The client settings list each map mod with its auto sync setting and a **Sync now** button.
- VoxelMap reports synchronized waypoint updates in chat.

### Cross-server waypoints (Velocity)

- A new Velocity plugin, `server_waypoint-4.0.0-velocity.jar`, links the servers behind one proxy. It's disabled by default and requires Java 25. Get it from [Modrinth](https://modrinth.com/plugin/server_waypoint/versions?l=velocity) or the [GitHub releases](https://github.com/2676959/server_waypoint/releases) and follow the [setup guide](https://github.com/2676959/server_waypoint#cross-server-teleport-setup).
- `/wp remote` shows the connected servers and their state. `/wp remote list` and `/wp remote details` browse the waypoints other servers share, and `/wp remote tp` teleports you to one. The destination checks the target and your permission before you switch and confirms your arrival. These commands work without the client mod.
- With the client mod, the waypoint manager has a remote view with a server selector.
- Servers connect over an encrypted channel authenticated with pinned public keys (`NOISE_KK`), or unencrypted over loopback on a single machine (`PLAINTEXT`).
- `/serverwaypoint status` on Velocity reports the coordinator's state. `/sw-cross-server-keygen` on a backend console creates the backend's keys without a start-and-stop pass. `serverIconItem` sets the item a backend shows in the server selector.
- New permissions: `remoteList` (default level 0) and `remoteTp` (default level 2).
- Remote teleport checks `remote.tp` on the source player, independently of local `tp` and remote browsing permissions. The destination checks both its own `tp` and `remote.tp` before transfer and again on arrival.
- Permission failures during destination preparation name the destination and show the separate `tp` and `remote.tp` results. Paper's offline checks honor LuckPerms decisions and each node's configured vanilla fallback level.

### Waypoint manager

- New layout: a dimension rail, the waypoint list and a details panel.
- Search by name; sort by name, distance or colour in either direction; group by list; show every dimension at once. Distances convert between Nether and Overworld coordinates.
- The manager remembers its sorting, grouping and dimension view.
- Escape closes an open popup before the screen. Enter and Space press the focused button.
- The current/remote scope control identifies which waypoints it shows and includes the current server name in its tooltip. Remote waypoints are read-only and show their server's availability.
- In singleplayer, the manager keeps the world running; add, edit, client settings and theme screens pause it.

### Add and edit screens

- A redesigned form with editable dimension and list comboboxes and suggestions for names, coordinates and facing. Initials follow the name until you type your own.
- New fields for keywords, a description and an icon. Problems are marked on the field they belong to.

### Client settings and themes

- A rebuilt settings screen: hover a row to see what it does and its default, ↺ resets one setting and **Reset to defaults…** resets them all. Mod Menu (Fabric) and the Mods screen (Forge and NeoForge) open it.
- New setting: **Render waypoints under F1** (off by default). Waypoints are hidden on loading screens.
- Colour themes: pick Translucent Dark, Modern Dark, High Contrast or the new Classic preset, or edit the colours in game. The default theme is now translucent dark.

### Action logging

- Server consoles receive action logs with the player's name and UUID, the original command sender and UUID, and the outcome. Commands run through `/execute as` retain both identities through asynchronous completion.
- Waypoint and list changes, restore, teleports, navigation, uploads, downloads, chat sharing and reload are logged. Save failures and partial uploads have distinct outcomes; browsing and routine synchronization do not produce action entries.

### Platforms

- Minecraft 26.3 on Fabric, NeoForge and Paper. Forge has no 26.3 release yet.
- Folia support.
- Waypoint sync, downloads and uploads use a new chunked network transport. `compressChunkedMessages` (default `true`) compresses its messages.
- The Velocity plugin reports anonymous usage statistics to bStats, as the Paper plugin already does.
- Release JARs include only the classes and resources their platform uses. Velocity packages the shared cross-server code and proxy services, reducing its size and removing backend code and translations.

### Translations

- English and Simplified Chinese include all current server and client translation keys. Traditional Chinese, Hong Kong Chinese, Spanish and Hebrew fall back to English in the theme editor, the waypoint details panel, the manager's search, sort and grouping controls, and some navigation and networking messages. Help translate on [Crowdin](https://crowdin.com/project/server-waypoint).
- Many 4.0.0 translations were generated by AI and may contain mistakes. Earlier community credits recognize past contributions; they do not imply review of these translations. Human review status is tracked in [the verification checklist](TRANSLATOR_CREDITS.md#human-verification-checklist-for-400).
- Custom server translations use Java `MessageFormat` placeholders (`{0}`, `{1}`); literal apostrophes must be doubled (`''`).

### Fixes

- In singleplayer, adding or editing waypoints could fail with "RenderSystem called from wrong thread", and some changes didn't refresh the markers, the waypoint manager or the map mods.
- Players on Minecraft 1.21.2 and later receive interactive chat feedback instead of console-style output.
- Movement keys work in waypoint screens on Forge and NeoForge while text entry is inactive.
- Open colour pickers and combobox popups no longer leak hover to controls beneath them.
- The waypoint details panel correctly handles its controls and lays out descriptions, including literal `\n` line breaks and formatted text.
- Remote server status indicators keep their intended colours. Chat colour swatches have consistent sizes, and GUI icons use smoother filtering.
- Paper destination preparation uses the configured `tp` and `remoteTp` levels for unset permissions, so allowing non-operators with level `0` works before transfer as well as on arrival.

### Tools

- `locations2waypoints.py` converts the waypoints of the MCDR plugins Locations and LocationMarker into Server Waypoint files.
