# Server Waypoint

[English](README.md) · [中文](README_zh.md)

[![License: MIT](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](https://opensource.org/licenses/MIT)
![Modrinth Version](https://img.shields.io/modrinth/v/server_waypoint?style=flat-square&label=Version)
![both](https://img.shields.io/badge/Environment-Server%26Client-4caf50?style=flat-square)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/server_waypoint?style=flat-square&logo=modrinth&logoColor=%2300AF5C&label=Modrinth%20Downloads&color=%2300AF5C)](https://modrinth.com/plugin/server_waypoint)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1416929?style=flat-square&logo=curseforge&logoColor=%23F16436&label=CurseForge%20Downloads&color=%23F16436)](https://www.curseforge.com/minecraft/mc-mods/server-waypoint)

Save useful places, share them with other players and find your way back. Server Waypoint works on Fabric, Forge, NeoForge, Paper, Folia and Purpur. An optional Velocity plugin connects waypoints across servers.

**Players can use commands, clickable chat and navigation without installing a client mod.** Install the client mod for a waypoint manager, in-world markers and map mod integration. You can also use it in singleplayer.

This guide covers **4.0.0**. Read the [changelog](CHANGELOG.md) for new features and upgrade notes.

## What you can do

- Keep shared waypoints in named lists, with colours, icons and descriptions.
- Browse, add and edit waypoints through clickable chat or the client waypoint manager.
- Navigate with a compass, map, boss bar, action bar or floating label.
- Sync waypoints to Xaero's Minimap or supported VoxelMap builds, and upload your own map waypoints to the server.
- Use Xaero's World Map menus to add or edit server waypoints.
- Browse and teleport to waypoints on other servers when the administrator enables Velocity support.

## Feature availability by Minecraft version

These ranges apply to the supported **4.0.0** builds. Always use the JAR for your Minecraft version and platform; a feature's range does not add support for versions without a matching build.

| Feature | Platform | Available Minecraft versions | Unavailable versions or limitations |
| --- | --- | --- | --- |
| Waypoint item icons in chat | Fabric, Forge, NeoForge, Paper/Folia/Purpur | 1.21.9 and later, within each platform's supported versions | Before 1.21.9, waypoints use text or initials. Only mapped vanilla item/block textures appear as chat icons; custom and VoxelMap icons use text or initials. |
| Player heads in chat feedback | Fabric, Forge, NeoForge, Paper/Folia/Purpur | 1.21.9 and later, within each platform's supported versions | Before 1.21.9, feedback uses text without player heads. |
| Text-display entity navigation (`text_display`, the floating label) | Fabric, Forge, NeoForge, Paper/Folia/Purpur | Every supported target: Fabric 1.20–26.3, Forge 1.20–26.2, NeoForge 1.20.2–26.3, Paper/Folia/Purpur 1.21–26.3 | No version exclusions within the supported targets. |
| VoxelMap sync, upload and editing | Fabric client | Every supported Fabric version, 1.20–26.3 | Requires a matching VoxelMap-Updated build and the Server Waypoint client mod. |
| VoxelMap sync, upload and editing | Forge client | 1.21.11, 26.1–26.1.2, 26.2 | Unavailable on supported Forge versions before 1.21.11. Requires VoxelMap-Updated and the Server Waypoint client mod. |
| VoxelMap sync, upload and editing | NeoForge client | 1.21.2–1.21.4, 1.21.11, 26.1–26.1.2, 26.2, 26.3 | Unavailable on 1.20.2–1.21.1 and 1.21.5–1.21.10. Requires VoxelMap-Updated and the Server Waypoint client mod. |

Chat icons, player heads and text-display navigation do not require the client mod. The waypoint manager, in-world client markers and Xaero integrations are available on every supported client-mod target; Xaero's World Map actions also require Xaero's Minimap. These client features can connect to a server running any supported backend, including Paper, Folia or Purpur.

## Your first waypoint

1. Join your server or open a singleplayer world, then run `/wp`.
2. Use the chat buttons to browse waypoints or add one where you stand. If you have no waypoint list yet, create a list first. For example, in the Overworld:

    ```text
    /wp add minecraft:overworld Public
    /wp add 100 64 200 Public Home
    ```

3. Run `/wp list`, choose **Home** and use its navigation button to find it. Teleport is available when you have permission.
4. Use the waypoint's edit buttons to change its name, colour, description or icon. Removing a waypoint gives you a **Restore** link that works once within 10 minutes.

Use quotes for names with spaces, such as `"Village square"`. Run `/wp help` whenever you need examples.

## Use the client waypoint manager

With the client mod installed, press **Right Shift** or run `/wp_gui` to open the manager. You can change the keybind in Minecraft's Controls settings.

Choose a dimension and select a waypoint to see its details. Search, sort or group the list, then add, edit, navigate or teleport using the buttons. Teleporting requires the server's permission.

Press **C** in the manager to open client settings. Adjust marker size, range and opacity, choose a colour theme, or change map sync settings. Hover over a setting for help; use its reset button to restore the default. Fabric users can also open settings through [Mod Menu](https://modrinth.com/mod/modmenu), and Forge and NeoForge users through the Mods screen.

When cross-server support is enabled, switch to **Remote server waypoints** and choose a server. You can browse and teleport to its shared waypoints; editing stays on the server that owns them.

## Use a map mod

Map mods are optional and go on your client alongside Server Waypoint:

- [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap): shows shared waypoints and lets you upload your map waypoints.
- [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map): adds Server Waypoint actions to its right-click menus. Install Xaero's Minimap as well for this integration.
- [VoxelMap-Updated](https://modrinth.com/mod/voxelmap-updated): syncs shared waypoints and supports uploads on the versions below.

| VoxelMap integration | Supported Minecraft versions in 4.0.0 |
| --- | --- |
| Fabric | Every supported Fabric target |
| Forge | 1.21.11, 26.1–26.1.2, 26.2 |
| NeoForge | 1.21.2–1.21.4, 1.21.11, 26.1–26.1.2, 26.2, 26.3 |

On multiplayer servers, automatic sync keeps shared waypoints up to date. Your personal map waypoints are kept separately. In singleplayer, opening a world does not automatically replace the map's saved waypoints; use **Sync now** in client settings when you want a full refresh. Synced waypoints can be edited through Server Waypoint's screens.

**Xaero's default waypoint set:** `gui.xaero_default` is the translation key and internal name of Xaero's Minimap's **Default** set. Uploading that set creates a Server Waypoint list named `gui.xaero_default`. In 4.0.0, syncing this list back creates a separate server-owned set; it does not merge into your personal Default set.

To copy your map waypoints to the server, run `/wp upload xaero` or `/wp upload voxelmap`. Upload needs permission from the administrator. A normal upload adds missing waypoints and keeps the server's version when there is a conflict. See the [upload guide](docs/tips/server-guide.md#uploading-from-client-map-mods) before using overwrite or delete options.

## Useful commands

`/waypoint` and `/wp` are interchangeable. Most actions are available from the `/wp` menu.

| Command | What it does |
| --- | --- |
| `/wp list` | Browse waypoints |
| `/wp add` | Add a waypoint where you stand |
| `/wp navigate` | View your navigation target and change guidance methods |
| `/wp upload` | Open the map upload menu; requires the client mod |
| `/wp remote` | Browse connected servers when cross-server support is enabled |
| `/wp help` | Get command help and examples |

For full command syntax, read the [command reference](docs/tips/server-guide.md#commands).

## For server owners

The default settings let players add, edit, remove and navigate to waypoints. Teleporting and uploading require operator-level permission by default; deleting server waypoints through an upload needs a separate, higher permission. Change these in `config.json` or with a supported permission provider. [LuckPerms](https://modrinth.com/plugin/luckperms) is optional on Fabric and Paper; Forge and NeoForge use the configured permission levels.

| Server platform | Settings file |
| --- | --- |
| Fabric | `config/server_waypoint/config.json` |
| Forge or NeoForge | `defaultconfigs/server_waypoint/config.json` |
| Paper, Folia or Purpur | `plugins/ServerWaypoint/config.json` |

The files are created on first startup. Run `/wp reload` after changing ordinary settings or custom translations. Cross-server settings need a restart. Read the [server reference](docs/tips/server-guide.md#server-configurations) for permissions, backups and settings that require a restart.

### Cross-server teleport setup

Cross-server waypoints are optional and **disabled by default**. They require Velocity, at least two dedicated servers with Server Waypoint, and matching versions throughout. The Velocity plugin requires **Java 25**. Players can use remote chat commands without the client mod.

Follow the [step-by-step setup guide](docs/tips/server-guide.md#cross-server-teleport-setup) to connect your servers. It covers a same-machine setup and encrypted connections between machines. The [administrator guide](docs/features/cross-server/cross-server-admin.md) covers permissions and troubleshooting.

## Languages and help

Messages follow each player's Minecraft language setting, even without the client mod. Bundled languages include English, Simplified Chinese, Traditional Chinese, Hong Kong Chinese, Spanish and Hebrew. Some messages fall back to English.

Many 4.0.0 translations were generated by AI and may contain mistakes. Earlier community translators are credited for their past contributions and are not responsible for those errors. See [translation credits and review status](TRANSLATOR_CREDITS.md), or help translate on [Crowdin](https://crowdin.com/project/server-waypoint).

- [Discord](https://discord.com/invite/tKtSSYDkHx) — questions and community help.
- [GitHub issues](https://github.com/2676959/server_waypoint/issues) — report a bug with your Minecraft version, platform and Server Waypoint version.
- [Server setup and command reference](docs/tips/server-guide.md) — detailed instructions for administrators.
- [Developer documentation](docs/README.md) — feature specifications and validation records.
