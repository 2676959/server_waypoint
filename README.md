# Server Waypoint

[English](README.md) [中文](README_zh.md)

[![License: MIT](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](https://opensource.org/licenses/MIT)
![Modrinth Version](https://img.shields.io/modrinth/v/server_waypoint?style=flat-square&label=Version)
![both](https://img.shields.io/badge/Environment-Server%26Client-4caf50?style=flat-square)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/server_waypoint?style=flat-square&logo=modrinth&logoColor=%2300AF5C&label=Modrinth%20Downloads&color=%2300AF5C)](https://modrinth.com/plugin/server_waypoint)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1416929?style=flat-square&logo=curseforge&logoColor=%23F16436&label=CurseForge%20Downloads&color=%23F16436)](https://www.curseforge.com/minecraft/mc-mods/server-waypoint)

[![Fabric](https://img.shields.io/badge/1.20.x%20%201.21.x%20%2026.1--26.3-555555?style=flat-square&label=Fabric&labelColor=dbb69b)](https://modrinth.com/plugin/server_waypoint/versions?l=fabric)
[![Forge](https://img.shields.io/badge/1.20.x%20%201.21.x%20%2026.1--26.2-555555?style=flat-square&label=Forge&labelColor=959eef)](https://modrinth.com/plugin/server_waypoint/versions?l=forge)
[![NeoForge](https://img.shields.io/badge/1.20.2--1.20.6%20%201.21.x%20%2026.1--26.3-555555?style=flat-square&label=NeoForge&labelColor=f99e6b)](https://modrinth.com/plugin/server_waypoint/versions?l=neoforge)
[![Paper](https://img.shields.io/badge/1.21.x%20%2026.1--26.3-555555?style=flat-square&label=Paper&labelColor=eeaaaa)](https://modrinth.com/plugin/server_waypoint/versions?l=paper)
[![Velocity](https://img.shields.io/badge/1.20.x%20%201.21.x%20%2026.1--26.3-555555?style=flat-square&label=Velocity&labelColor=8ec9ea)](https://modrinth.com/plugin/server_waypoint/versions?l=velocity)

[![discord-singular](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-singular_vector.svg)](https://discord.com/invite/tKtSSYDkHx)

Manage waypoints on the server and sync them to players' clients automatically. Works with Xaero's Minimap, Xaero's World Map and VoxelMap, and most features work for players without the mod.

See the [changelog](CHANGELOG.md) for what's new in 4.0.0.

## Features
- Syncing waypoints from the server automatically, including to Xaero's Minimap and VoxelMap (Fabric).
- Customizable waypoint rendering, with initials or an item or VoxelMap icon on each marker.
- Allow players to manage waypoints by both GUI (need client installation) and clickable chat commands (only need server installation).
- Server-side navigation with a compass, a map, the boss bar, the action bar or a floating label, without client installation.
- Uploading waypoints from Xaero's Minimap or VoxelMap to the server.
- Browsing and teleporting to waypoints on other servers behind a Velocity proxy (opt-in).
- Commands auto-completion and in-game help.
- Custom permission for `/wp <options>` commands. Compatible with [LuckPerms](https://modrinth.com/plugin/luckperms).
- Support adding waypoint conveniently from Xaero's minimap waypoint chat sharing message without requiring client side installation.
- Server-side translations that follow each player's language.

## Dependencies
Required:
  - [Fabric API](https://modrinth.com/mod/fabric-api) (Fabric)
  
Optional:
  - [LuckPerms](https://modrinth.com/plugin/luckperms)
  - [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)
  - [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map): adds Server Waypoint options to its right-click menus
  - [VoxelMap](https://modrinth.com/mod/voxelmap-updated) (Fabric)
  - [Mod Menu](https://modrinth.com/mod/modmenu) (Fabric): opens the client settings from the mod list
  - [Velocity](https://papermc.io/software/velocity): only for [cross-server teleport](#cross-server-teleport-setup)

## Keybinds
- Press `Right Shift` (default keybind) or use `/wp_gui` to open the waypoint manager screen in game.
- In the waypoint manager screen, hover over a waypoint and press `T` to teleport (requires `/wp tp` command permission). 
- In the waypoint manager screen, press `C` to open the client settings. The config button in Mod Menu (Fabric) or the Mods screen (NeoForge and Forge) opens them too.
- The add and edit waypoint screens also set keywords, a description and an icon.
- In Xaero's World Map, right-click the map to add a waypoint to the server at that spot, or right-click a waypoint to add it to the server or edit its server copy.

## Commands
Run `/wp` for a menu: most features are a click away, and every screen offers its next steps as links. The console, RCON and command blocks get the same information as plain text, with identifiers and coordinates written out.
- `/wp add` opens a picker of the current dimension's lists to add a waypoint where you stand. Identifiers must be unique within their list.
  - `/wp add <dimension> <list-identifier>` adds a waypoint list.
  - `/wp add <x y z> <list-identifier> <name>` adds a waypoint in your current dimension with generated initials, a random colour and your facing. Put a dimension first, `/wp add <dimension> <list-identifier> <x y z> <name>`, to add it elsewhere. The full form continues with initials, colour, yaw, visibility, keywords, description and `icon <namespace:path>`.
- `/wp download [<dimension> [<list-identifier> [<waypoint-identifier>]]]` sends waypoints to your map mod (needs the mod on your client).
- `/wp details list <dimension> <list-identifier>` and `/wp details waypoint <dimension> <list-identifier> <waypoint-identifier>` show every property with buttons to edit it.
- `/wp edit list ...` and `/wp edit waypoint ...` set one property at a time or clear an optional property. Without a value, `set color` opens a colour picker and `set yaw` a facing picker; `set color random` picks a colour. Run `/wp help edit` for the complete grammar.
  - `/wp edit waypoint <dimension> <list> <waypoint> set icon minecraft:diamond` selects an item icon. Use `clear icon` to restore the initials marker. A `voxelmap:` ID such as `voxelmap:star` selects a built-in VoxelMap image. The full add command accepts `icon <namespace:path>` after visibility or its optional keywords and description. Enter icon IDs without quotes; tab completion suggests server item IDs and built-in VoxelMap IDs. `/wp details waypoint` includes an icon row with edit and clear controls.
- `/wp help [<topic>]` explains each command with usages you can click and examples.
- `/wp list` shows the current dimension: its lists with their first waypoints when they fit on one page, otherwise one line per list. Use `all`, a dimension, or a dimension plus list identifier to change the scope.
  - Options follow the order `search <query>`, `sort <default|name|distance|color>` (with `order <ascending|descending>` for the other sorts), `limit <1-100>`, `view <lists|tree|flat>`, `page <number>`. Quote multi-word values and list names that match an option word.
  - Pages hold whole lists: the tree view and the one-line-per-list view hold the page limit plus five lines; flat views and single lists hold the page limit in rows.
  - `/wp list dimensions [page <number>]` lists every dimension with its waypoint count.
- `/wp navigate` shows the navigation panel. `/wp navigate <dimension> <list> <waypoint> [<method>|default|all]` starts navigating, `/wp navigate use|disable <method>` turns one method on or off, `/wp navigate disable` stops, and `/wp navigate config text_display` adjusts the floating text.
- `/wp remote` shows the remote servers and their state; `/wp remote page <number>` pages it.
- `/wp remote list [<server> [<dimension> [<list>]]]` browses cached remote waypoints with the same options as `/wp list`. Quote exact identities, including names matching option words. Distance sorting reports that cross-server distances are unavailable.
  - Results are read-only and work through ordinary server chat. A coloured dot shows each server's state: available, stale, unreachable or no access. `/wp remote details <server> <dimension> <list> <waypoint>` shows one waypoint. Run `/wp help remote` for help.
  - Catalog synchronization starts when cross-server configuration is enabled. See [Velocity runtime setup](docs/features/cross-server/specs/cross-server-velocity-runtime.md) and [remote catalog queries](docs/features/cross-server/specs/cross-server-catalog-queries.md).
- `/wp remote tp <server> <dimension> <list> <waypoint>` requests a teleport using exact cached identities (quote names with spaces). Stale or missing targets fail before preparation; the player stays on the source until destination preparation and fresh permission checks succeed, and the destination confirms the arrival. See [remote teleport initiation](docs/features/cross-server/specs/cross-server-source-teleport.md).
  - Velocity and dedicated backend runtime integration is implemented and disabled by default. Suggestions use only the local cache. Quote dimension identities such as `"minecraft:overworld"`. See [configuration and validation](docs/features/cross-server/specs/cross-server-velocity-runtime.md).
- `/wp reload` reloads `config.json` and the translation files in `/config/server_waypoint/lang`. `defaultPageLimit`, `defaultNavigationMethods`, `CommandPermission`, `addWaypointFromChatSharing` and `compressChunkedMessages` apply at once; `serverId`, `cross-server.json` and turning on `sendXaerosWorldId` need a restart. Waypoint files are not reloaded, so edit them while the server is stopped.
- `/wp remove` removes a waypoint by identifier and answers with a temporary, single-use Restore link.
  - `/wp remove <dimension> <list-identifier>` removes an empty waypoint list.
- `/wp restore <token>` restores a waypoint removed in the last 10 minutes. Each token works once.
- `/wp tp <dimension> <list-identifier> <waypoint-identifier>` teleports you to a waypoint.
- `/wp upload` shows the upload panel when your client has the mod. `/wp upload <xaero|voxelmap>` imports waypoints from the selected map mod on the executing player’s client. See [Uploading from client map mods](#uploading-from-client-map-mods) for conflict, force, and delete behavior.

## Cross-server teleport setup

Cross-server teleport is disabled by default and runs through Velocity. Install matching Server Waypoint versions on Velocity and at least two dedicated Paper, Fabric, Forge, or NeoForge backends. The Velocity plugin is `server_waypoint-<version>-velocity.jar`, available on [Modrinth](https://modrinth.com/plugin/server_waypoint/versions?l=velocity) and in the [GitHub releases](https://github.com/2676959/server_waypoint/releases). Velocity requires Java 25; use the Java version required by each backend. Players can use remote commands without a client mod. The remote GUI requires a matching client mod. Integrated servers do not participate.

### No encryption: PLAINTEXT (same host)

This setup is for servers on the **same host**. It has no encryption or cryptographic backend authentication, so use it only when local processes are trusted.

1. Register both backends in Velocity, for example `survival` and `creative`. Start Velocity and each backend once to create their disabled `cross-server.json` files, then stop them. The files are in `<velocity-root>/plugins/server_waypoint/`, `<paper-root>/plugins/ServerWaypoint/`, `<fabric-root>/config/server_waypoint/`, or `<forge-or-neoforge-root>/defaultconfigs/server_waypoint/`.
2. Set Velocity's `cross-server.json` to:

   ```json
   {
       "enabled": true,
       "transportMode": "PLAINTEXT",
       "listen": "127.0.0.1:25580",
       "backends": {
           "survival": {
               "enabled": true,
               "velocityServer": "survival"
           },
           "creative": {
               "enabled": true,
               "velocityServer": "creative"
           }
       }
   }
   ```

   Each `velocityServer` must match a registered Velocity server name. Each backend ID must be unique and remain stable.
3. Set `survival`'s backend `cross-server.json` to:

   ```json
   {
       "enabled": true,
       "transportMode": "PLAINTEXT",
       "serverId": "survival",
       "coordinator": "127.0.0.1:25580",
       "catalogExport": "PUBLIC"
   }
   ```

   Use the same file on `creative`, changing `serverId` to `creative`. `PUBLIC` shares each backend's waypoint lists with the coordinator and authorized readers on participating servers.
4. Remove `credentialsDirectory`, `coordinatorPublicKey`, and `requiredSuite` from plaintext configurations, and remove `publicKey` from Velocity's backend entries. Use a literal loopback IP at both ends: `localhost`, wildcard addresses, and non-loopback addresses are rejected. Restart Velocity and both backends after editing the files. Configure normal Velocity player forwarding so player UUIDs agree across servers.
5. Join through Velocity and run `/wp remote`, then `/wp remote list survival`. Teleport to an existing exported waypoint, for example `/wp remote tp creative "minecraft:overworld" "Public list" "Home"`. Check the destination coordinates and feedback, then test the other direction. A server switch alone does not confirm waypoint arrival.

### Encryption: NOISE_KK

Use `NOISE_KK` when backends run on different hosts or when you want authenticated, encrypted backend connections on one host. Give the Velocity coordinator a private, reachable TCP address and allow its waypoint port only from backend hosts. This port is separate from Velocity's player port and the backends' Minecraft ports. The examples below use loopback for a same-host installation; replace `127.0.0.1` with the coordinator's private address on multiple hosts.

1. Register `survival` and `creative` in Velocity. Start Velocity and each backend once to create their disabled `cross-server.json` files, then stop them. Use the configuration paths listed in the plaintext setup above.
2. Set Velocity's `cross-server.json` to the following **without** the `publicKey` fields for the first start:

   ```json
   {
       "enabled": true,
       "transportMode": "NOISE_KK",
       "listen": "127.0.0.1:25580",
       "backends": {
           "survival": {
               "enabled": true,
               "velocityServer": "survival"
           },
           "creative": {
               "enabled": true,
               "velocityServer": "creative"
           }
       }
   }
   ```

3. Set `survival`'s backend `cross-server.json` to the following **without** `coordinatorPublicKey` for the first start. Use the same file on `creative`, changing `serverId` to `creative`:

   ```json
   {
       "enabled": true,
       "transportMode": "NOISE_KK",
       "serverId": "survival",
       "coordinator": "127.0.0.1:25580",
       "catalogExport": "PUBLIC"
   }
   ```

4. Start Velocity and both backends, then stop them. Startup reports missing public-key pins at this stage, but each component writes `cross-server-public-key.txt` beside its `cross-server.json`.
5. Exchange and verify the full Base64 value in each public-key file through a trusted administrative channel. Add the Velocity key as `coordinatorPublicKey` to **each** backend's `cross-server.json`. Add the `survival` key as `publicKey` inside Velocity's `backends.survival` entry and the `creative` key inside `backends.creative`. For example, add these fields to the existing objects:

   ```text
   "coordinatorPublicKey": "<Velocity public key>"
   ```

   ```text
   "survival": {
       "enabled": true,
       "velocityServer": "survival",
       "publicKey": "<survival public key>"
   }
   ```

   Replace the placeholders with the full Base64 public keys. Keep each component's `credentials/static.key` private; never copy it to another component. No pairing command is needed.
6. Restart Velocity, then both backends. Configure authenticated Velocity player forwarding so player UUIDs agree across servers. Run `/wp remote` and `/wp remote list survival`, then test `/wp remote tp creative "minecraft:overworld" "Public list" "Home"` with an existing exported waypoint. Verify destination coordinates and feedback in both directions.

Remote browsing uses `server_waypoint.command.remote.list` (default level 0). Remote teleport requires `server_waypoint.command.remote.tp` at the source, plus both `server_waypoint.command.tp` and `server_waypoint.command.remote.tp` at the destination (each defaults to level 2). The source's local teleport permission is independent. See the [administrator guide](docs/features/cross-server/cross-server-admin.md) for permissions, key rotation, and troubleshooting.

### Cross-server administration

- On the Velocity console, `/serverwaypoint status` shows whether the coordinator is running, its transport mode and which backends are online. Players need `server_waypoint.command.cross_server.status` to use it. Velocity can't grant permissions by itself, so give it to them with a permissions plugin such as LuckPerms.
- On a backend console, `/sw-cross-server-keygen` creates the backend's `NOISE_KK` key pair after you configure `cross-server.json`, without a first start and stop. It requires level 4 and never replaces an existing key.
- Set `"serverIconItem": "minecraft:diamond"` in a backend's `cross-server.json` and restart that backend to choose the item players see for it in the server selector. The default is `minecraft:beacon`.

The cross-server [release notes](docs/features/cross-server/cross-server-release-notes.md) and [release verification](docs/features/cross-server/validation/cross-server-release-readiness.md) provide more detail.

## Uploading from client map mods

Upload is initiated by the server but reads map data from the executing player’s client. The required `<source>` is `xaero` or `voxelmap`; the client must have both Server Waypoint and the selected map mod installed and ready. The server accepts only the dimensions and optional list/waypoint selected by the command.

In singleplayer (integrated-server mode), the host uses the same `/wp upload xaero` and `/wp upload voxelmap` commands. Waypoints are collected on the Minecraft client thread and passed directly to the server thread for validation and application, without upload request packets or chunked upload transport. This also applies to the host of a world opened to LAN; joining players still upload from their own clients over the network. Permissions, scope, conflict policies, and deletion rules remain the same.

For Xaero, only normal, enabled, non-temporary waypoints are imported. Upload synchronizes the waypoint name, initials, coordinates, Xaero color, yaw, and local/global visibility. For VoxelMap, disabled and coordinate-highlight waypoints are skipped. Server-synced VoxelMap names are restored to their original list and waypoint identifiers; other VoxelMap waypoints are imported into a fixed `VoxelMap` list. VoxelMap coordinates are converted back from its dimension scale, while initials and yaw use empty/zero values and visibility is local. Server-only display names, keywords, and descriptions are preserved when an existing waypoint is updated.

VoxelMap's built-in waypoint images import as `voxelmap:` icon IDs. Xaero uploads preserve an existing icon, while VoxelMap uploads replace it when they supply a recognized image. Item icons and unavailable VoxelMap images use VoxelMap's default waypoint image during sync; the Server Waypoint icon ID remains stored. Waypoint icons also travel in local and cross-server waypoint data, so clients, backends and the Velocity plugin must run the same version.

VoxelMap uploads use the active subworld. If a requested dimension's coordinate scale is unavailable, the entire export is aborted; visit that dimension before retrying. Uploads commit one dimension at a time. If a later dimension fails, earlier changes remain applied and synchronized, and the command reports a partial result.

Every mode accepts the same optional scope:

- No selector: every server dimension available to the command executor.
- `<dimension>`: every map-mod waypoint in that dimension.
- `<dimension> <list>`: one waypoint list (`VoxelMap` for local VoxelMap waypoints).
- `<dimension> <list> <waypoint>`: one waypoint.

### Normal upload / force server

`/wp upload <source> [<dimension> [<list> [<waypoint>]]]` and `/wp upload <source> force server [<dimension> [<list> [<waypoint>]]]` have identical behavior. Missing server waypoints are added. Matching waypoints are left unchanged. If the same name exists with different map-supported properties, the server version wins and the command reports a conflict. Nothing is deleted.

### Force local

`/wp upload <source> force local [<dimension> [<list> [<waypoint>]]]` adds missing waypoints and replaces conflicting map-supported properties with the client values. Server-only display names, keywords, and descriptions are preserved. Nothing is deleted.

### Force local delete

`/wp upload <source> force local delete [<dimension> [<list> [<waypoint>]]]` first applies `force local`, then mirrors the selected scope by deleting server data that is absent from the selected map mod:

- World scope removes absent waypoint sets and waypoints across all selected dimensions.
- Dimension scope removes absent waypoint sets and waypoints in that dimension.
- List scope removes absent waypoints from that list, or removes the server list if the map-mod list is absent.
- Waypoint scope removes only the selected server waypoint if it is absent locally.

Skipped map-mod waypoints count as absent in `force local delete` and can therefore cause the corresponding server waypoint to be deleted. Use this mode only when the selected source scope is intended to be the authoritative copy.

Upload uses `server_waypoint.command.upload` (vanilla permission level 2 by default). The destructive delete mode additionally requires `server_waypoint.command.upload.delete` (level 4 by default).

### Identifiers and display names

List and waypoint identifiers are exact lookup keys. Commands preserve them verbatim: they may be empty (`""`), contain whitespace when quoted, start with option-like text, or look like Minecraft JSON. Add commands create no display-name override and never parse an identifier as formatted text.

Display names are optional presentation overrides edited separately with `/wp edit ... set display-name`. Clearing a display name restores the identifier fallback; setting it to an empty string creates an intentionally empty override. Command suggestions insert identifiers, while a display name may appear only as tooltip context.

## Server-side Translations
Messages and command feedbacks sent by this mod will be automatically translated based on the language setting on the receiver's client. This works entirely on the server-side; players can see the translated message without client-side installation of this mod. Right now, the mod comes with translations for English, Simplified Chinese, Traditional Chinese, Traditional Chinese (Hong Kong), Spanish and Hebrew. If you’re interested, you can help out by adding translations on [Crowdin](https://crowdin.com/project/server-waypoint)!

- ### Add translations
  Place the lang files under the directory: `<config-path>/server_waypoint/lang/`. This mod will load them on server starting, use `/wp reload` if the server is already running.
  
- ### Create a lang file
  Follow the format used in [`en_us.json`](./common/src/main/resources/lang/en_us.json), [`zh_cn.json`](./common/src/main/resources/lang/zh_cn.json).

  Name the lang file with a [valid language code](https://minecraft.wiki/w/Language#Languages).

  Version 4.0.0 renamed most translation keys, so lang files made for 3.x need to be rebuilt from the current `en_us.json`.

- ### Translation order
  If the translation you’ve added uses the same language code as the built-in language, this mod will try to find the translation key in the file you added first. If that key isn’t there, it’ll fall back to the built-in translation. So, if you’d like to use your own translation version, you can easily do that by adding your own file and overriding the built-in translation.

Community translator credits and credit pull request guidelines are documented in [`TRANSLATOR_CREDITS.md`](./TRANSLATOR_CREDITS.md).

## Translation Credits
Thank you to the community translators who contributed translations to earlier versions of Server Waypoint.

Many translations in version 4.0.0 were generated by AI and may contain mistakes. The credits below recognize earlier community contributions and do not imply that the listed contributors wrote or reviewed the AI-generated translations in 4.0.0.

Previous translation contributors are not responsible for errors in translations generated or rewritten by AI for version 4.0.0.

See the [human verification checklist for 4.0.0](./TRANSLATOR_CREDITS.md#human-verification-checklist-for-400) for the recorded review status of each language.

- #### Hebrew
  [hotspotty](https://discord.com/users/575744894593663006)
- #### Spanish
  shimonsolo
- #### Traditional Chinese
  Ymaomi
- #### Traditional Chinese (Hong Kong)
  Ymaomi

## Waypoints
- #### Location
  For a dedicated server:
  
  `<config-path>/server_waypoint/waypoints/`

  For a single player world:

  `<minecraft-root>/saves/<world-name>/server_waypoint/waypoints/`
- #### File Format
  All waypoints are saved in json files. Each json file contains all waypoints in one dimension and the filename is the converted full registry name of that dimension.
  For example, all waypoints in the overworld is stored in `minecraft$overworld.json`.

## Server Configurations
Fabric, Quilt:

`<minecraft-root>/config/server_waypoint/config.json` 

NeoForge, Forge:

`<minecraft-root>/defaultconfigs/server_waypoint/config.json`

Paper, Folia, Purpur:

`<server-root>/plugins/ServerWaypoint/config.json`

`/wp reload` applies changes to `defaultPageLimit`, `defaultNavigationMethods`, `CommandPermission`, `addWaypointFromChatSharing` and `compressChunkedMessages`. Changes to `serverId`, turning on `sendXaerosWorldId` and changes to `cross-server.json` take effect after a restart.

- ### Default Page Limit
  Sets the page limit `L` that `/wp list` and `/wp remote list` use when the command does not include `limit`: flat views and single lists show `L` rows, while the tree view, the one-line-per-list view and the dimension and server lists hold `L + 5` lines. Values are constrained to `1-100`, and the default is `10`. This setting takes effect after `/wp reload`.

  ```json5
  {
    "defaultPageLimit": 10
  }
  ```
- ### Default Navigation Methods
  Sets one or more methods enabled when `/wp navigate <dimension> <list> <waypoint>` starts a new session without `using`. The value must be a non-empty array containing `compass`, `map`, `bossbar`, `actionbar`, or `text_display`. The default is `actionbar`.

  ```json5
  {
    "defaultNavigationMethods": [
      "actionbar"
    ]
  }
  ```
- ### Command Permission
  Changes the vanilla [permission level](https://minecraft.wiki/w/Permission_level) required to execute the command.
  
  This will be overridden by the permission set by [LuckPerms](https://modrinth.com/plugin/luckperms).

  Upload defaults to level 2. The destructive `force local delete` mode requires level 4 and can be granted separately with `server_waypoint.command.upload.delete`; normal upload uses `server_waypoint.command.upload`.
  
  Remote browsing uses `server_waypoint.command.remote.list` (`remoteList`, level 0).
  Remote teleport source authorization requires `server_waypoint.command.remote.tp` (`remoteTp`,
  level 2), independently of the source's local teleport and browsing permissions. Destination
  preparation checks both `server_waypoint.command.tp` and `server_waypoint.command.remote.tp`,
  and arrival rechecks both destination permissions. Local waypoint teleportation still requires only `tp`.
  Paper uses the vanilla command-source level for unset nodes; explicit permission grants or denials
  take precedence. Fabric's permissions API supports node overrides; the current Forge/NeoForge
  adapters use vanilla levels.
  See [remote authorization](docs/features/cross-server/specs/cross-server-authorization.md) for the integration boundary.

  Default value:
  ```json5
  {
    "CommandPermission": {
      // /wp add
      "add": 0,
      // /wp edit
      "edit": 0,
      // /wp remove
      "remove": 0,
      // /wp navigate
      "navigate": 0,
      // /wp tp
      "tp": 2,
      // /wp reload
      "reload": 2,
      // /wp upload xaero
      "upload": 2,
      // /wp upload xaero force local delete
      "uploadDelete": 4,
      // /wp remote and /wp remote list
      "remoteList": 0,
      // /wp remote tp source authorization
      "remoteTp": 2
    }
  }
  ```
- ### Features
  - #### addWaypointFromChatSharing
    Default value: `true`
    
    Prompts the user to add the waypoint they shared in chat. Requires `/wp add` permission.
    
    Example:
    ```json5
     {
       "Features": {
         "addWaypointFromChatSharing": true
       }
     }
     ```
  - #### sendXaerosWorldId
    Default value: `true`
    
    Send world id to client to help Xaero's map mod recognize the server.

    **This should be set to `false` if `xaero-map-protocol` on the [Leaves](https://leavesmc.org/) server or some similar features provided by other plugin or mod is enabled.**

    Example:
    ```json5
     {
       "Features": {
         "sendXaerosWorldId": true
       }
     }
     ```
  - #### compressChunkedMessages
    Default value: `true`

    Compresses the waypoint data exchanged with clients that have the mod installed. The server applies a change after `/wp reload`; a connected client compresses what it sends according to the setting it received when it joined.

    Example:
    ```json5
     {
       "Features": {
         "compressChunkedMessages": true
       }
     }
     ```

## Client Configurations

Open the client settings with `C` in the waypoint manager, or with the config button in Mod Menu
(Fabric) or the Mods screen (NeoForge and Forge). Changes apply immediately and are saved when the
screen closes. Hover a row to see what it does and its default. The ↺ button next to a setting resets
it, and **Reset to defaults…** resets them all.

- #### Waypoint rendering
  - **Show in-world waypoints**: draws waypoint markers in the world. Default: `On`.
  - **Render waypoints under F1**: keeps in-world waypoints visible while F1 hides the HUD. Loading screens always hide them. Default: `Off`.
  - **Scale**: size of the markers, from `0` to `500` percent. Default: `100%`.
  - **Vertical offset**: moves the markers up or down by up to half a block, from `-100` to `100` percent. Default: `0%`.
  - **Background opacity**: opacity of marker backgrounds and icons, from `0` (clear) to `255` (solid). Default: `128`.
  - **Local waypoint range**: waypoints with local visibility are drawn only within this many chunks, from `0` to `1024`; global waypoints are always drawn. Default: `12` chunks.
- #### Map mods
  Xaero's Minimap is supported on every loader and VoxelMap on Fabric. A supported map mod that isn't installed is listed as not installed.
  - **Auto sync**: keeps the waypoints Server Waypoint adds to the map mod up to date as they change on the server. Default: `On`.
  - **Sync now**: after a confirmation, replaces the waypoints Server Waypoint added with the server's current waypoints. Available once you're in a world whose waypoints have synced.

  Server Waypoint marks what it adds: Xaero's Minimap sets and VoxelMap waypoint names carry an internal `sw␟` prefix. Sync only touches these, so your own waypoints and lists are never changed, even ones named like a server list. Changes you made to synced waypoints, waypoints you put in Server Waypoint's lists, and lists removed on the server are lost. Upload maps the managed names back to their server list and waypoint names.
- #### Appearance
  - **Color theme**: opens the theme editor.

Remote catalog synchronization and the remote GUI require matching client and backend versions.
Remote snapshots are kept separately from local waypoint files. See
[client synchronization](docs/features/cross-server/specs/cross-server-client-sync.md) and the
[cross-server teleport setup](#cross-server-teleport-setup).
