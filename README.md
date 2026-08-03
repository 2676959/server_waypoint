# Server Waypoint

[English](README.md) [中文](README_zh.md)

[![License: MIT](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](https://opensource.org/licenses/MIT)
![Modrinth Version](https://img.shields.io/modrinth/v/server_waypoint?style=flat-square&label=Version)
![both](https://img.shields.io/badge/Environment-Server%26Client-4caf50?style=flat-square)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/server_waypoint?style=flat-square&logo=modrinth&logoColor=%2300AF5C&label=Modrinth%20Downloads&color=%2300AF5C)](https://modrinth.com/plugin/server_waypoint)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1416929?style=flat-square&logo=curseforge&logoColor=%23F16436&label=CurseForge%20Downloads&color=%23F16436)](https://www.curseforge.com/minecraft/mc-mods/server-waypoint)

[![Fabric](https://img.shields.io/badge/1.20.x%20%201.21.x%20%2026.1--26.2-555555?style=flat-square&label=Fabric&labelColor=dbb69b)](https://modrinth.com/plugin/server_waypoint/versions?l=fabric)
[![Forge](https://img.shields.io/badge/1.20.x%20%201.21.x%20%2026.1--26.2-555555?style=flat-square&label=Forge&labelColor=959eef)](https://modrinth.com/plugin/server_waypoint/versions?l=forge)
[![NeoForge](https://img.shields.io/badge/1.20.2--1.20.6%20%201.21.x%20%2026.1--26.2-555555?style=flat-square&label=NeoForge&labelColor=f99e6b)](https://modrinth.com/plugin/server_waypoint/versions?l=neoforge)
[![Paper](https://img.shields.io/badge/1.21.x%20%2026.1--26.2-555555?style=flat-square&label=Paper&labelColor=eeaaaa)](https://modrinth.com/plugin/server_waypoint/versions?l=paper)

[![discord-singular](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-singular_vector.svg)](https://discord.com/invite/tKtSSYDkHx)
[![crowdin](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/translate/crowdin_vector.svg)](https://crowdin.com/project/server-waypoint)

Manage waypoints and sync them to other players' clients automatically. Compatible with Xaero's minimap.

## Features
- Syncing waypoints from the server automatically.
- Customizable waypoints rendering. 
- Allow players to manage waypoints by both GUI (need client installation) and commands (only need server installation).
- Commands auto-completion.
- Custom permission for `/wp <options>` commands. Compatible with [LuckPerms](https://modrinth.com/plugin/luckperms).
- Support adding waypoint conveniently from Xaero's minimap waypoint chat sharing message without requiring client side installation.

## Dependencies
Required:
  - [Fabric API](https://modrinth.com/mod/fabric-api)
  
Optional:
  - [LuckPerms](https://modrinth.com/plugin/luckperms)
  - [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)

## Keybinds
- Press `Right Shift` (default keybind) or use `/wp_gui` to open the waypoint manager screen in game.
- In the waypoint manager screen, hover over a waypoint and press `T` to teleport (requires `/wp tp` command permission). 
- In the waypoint manager screen, press `C` to open client configuration screen.

## Commands
- `/wp add` adds a new waypoint. Identifiers must be unique within their list.
  - `/wp add <dimension> <list-identifier>` adds a waypoint list.
- `/wp download` download waypoints and add to Xaero's Minimap (will not work without client installation).
- `/wp details list <dimension> <list-identifier>` and `/wp details waypoint <dimension> <list-identifier> <waypoint-identifier>` show every property and its available actions.
- `/wp edit list ...` and `/wp edit waypoint ...` set one property at a time or clear an optional property. Run `/wp help edit` for the complete grammar.
- `/wp upload` import normal, enabled, non-temporary waypoints from Xaero's Minimap on the executing player's client. Requires the client mod and Xaero's Minimap.
  - `/wp upload <dimension> [<list> [<waypoint>]]` restricts the import to a dimension, waypoint set, or waypoint.
  - The default (or `/wp upload force server`) only adds missing server waypoints. An existing waypoint with the same name but different properties is reported as a conflict and keeps the server version.
  - `/wp upload force local [<dimension> [<list> [<waypoint>]]]` makes the local Xaero waypoint win conflicts without deleting server waypoints.
  - `/wp upload force local delete [<dimension> [<list> [<waypoint>]]]` mirrors local data into the selected scope, deleting server waypoints or waypoint sets that are absent in Xaero's Minimap.
- `/wp list` lists waypoints in the current dimension. Use `all`, a dimension, or a dimension plus list name to change the scope. Results are split using the server's configured page limit (10 by default), with clickable sorting and page controls.
  - Add `search <query>` to filter by waypoint name.
  - Add `sort <default|name|distance|color>` and, for non-default sorts, optionally `order <ascending|descending>` to sort the result.
  - Add `page <number>` and/or `limit <1-100>` to choose a page or change its size. Options follow the order `search`, `sort`, `order`, `page`, `limit`; quote multi-word values and list names that match an option word.
- `/wp reload` reload `config.json` and translation files in `/config/server_waypoint/lang`, feature `sendXaerosWorldId` requires restarting to take effect.
- `/wp remove` removes a waypoint by identifier and returns a temporary, single-use restore action.
  - `/wp remove <dimension> <list-identifier>` removes an empty waypoint list.
- `/wp restore <token>` restores a recently removed waypoint while its temporary token remains valid.
- `/wp tp` teleport the executor player to a waypoint

### Identifiers and display names

List and waypoint identifiers are exact lookup keys. Commands preserve them verbatim: they may be empty (`""`), contain whitespace when quoted, start with option-like text, or look like Minecraft JSON. Add commands create no display-name override and never parse an identifier as formatted text.

Display names are optional presentation overrides edited separately with `/wp edit ... set display-name`. Clearing a display name restores the identifier fallback; setting it to an empty string creates an intentionally empty override. Command suggestions insert identifiers, while a display name may appear only as tooltip context.

## Server-side Translations
Messages and command feedbacks sent by this mod will be automatically translated based on the language setting on the receiver's client. This works entirely on the server-side; players can see the translated message without client-side installation of this mod. Right now, the mod comes with translations for English and Simplified Chinese. If you’re interested, you can help out by adding translations on [Crowdin](https://crowdin.com/project/server-waypoint)!

- ### Add translations
  Place the lang files under the directory: `<config-path>/server_waypoint/lang/`. This mod will load them on server starting, use `/wp reload` if the server is already running.
  
- ### Create a lang file
  Follow the format used in [`en_us.json`](./common/src/main/resources/lang/en_us.json), [`zh_cn.json`](./common/src/main/resources/lang/zh_cn.json).

  Name the lang file with a [valid language code](https://minecraft.wiki/w/Language#Languages).

- ### Translation order
  If the translation you’ve added uses the same language code as the built-in language, this mod will try to find the translation key in the file you added first. If that key isn’t there, it’ll fall back to the built-in translation. So, if you’d like to use your own translation version, you can easily do that by adding your own file and overriding the built-in translation.

Community translator credits and credit pull request guidelines are documented in [`TRANSLATOR_CREDITS.md`](./TRANSLATOR_CREDITS.md).

## Translation Credits
Thank you to the community translators who help make Server Waypoint available to more players in more languages.

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

Paper, Purpur:

`<server-root>/plugins/ServerWaypoint/config.json`

Some changes made in `config.json` may take effects after server restarts.

- ### Default Page Limit
  Sets the number of waypoints shown on each `/wp list` page when the command does not include `limit`. Values are constrained to `1-100`, and the default is `10`. This setting takes effect after `/wp reload`.

  ```json5
  {
    "defaultPageLimit": 10
  }
  ```
- ### Default Navigation Selection
  Sets the method enabled when `/wp navigate <dimension> <list> <waypoint>` starts a new session without `using`. Supported values are `compass`, `map`, `bossbar`, `actionbar`, and `all`. The default is `actionbar`.

  ```json5
  {
    "defaultNavigationSelection": "actionbar"
  }
  ```
- ### Command Permission
  Changes the vanilla [permission level](https://minecraft.wiki/w/Permission_level) required to execute the command.
  
  This will be overridden by the permission set by [LuckPerms](https://modrinth.com/plugin/luckperms).

  Upload defaults to level 2. The destructive `force local delete` mode requires level 4 and can be granted separately with `server_waypoint.command.upload.delete`; normal upload uses `server_waypoint.command.upload`.
  
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
      // /wp upload
      "upload": 2,
      // /wp upload force local delete
      "uploadDelete": 4
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

## Client Configurations
- #### Enable Waypoint Rendering
  Default value: `true`
- #### Waypoint Rendering Scaling Factor (Percentage)
  Default value: `100`
- #### Waypoint Background Transparence
  Default value: `128`
- #### Waypoint Vertical Offset (Percentage)
  Default value: `0`
- #### Local Waypoint View Distance (Chunks)
  Default value: `12`
- #### Auto Sync to Xaero's Minimap
  Default value: `true`

  Requires Xaero's Minimap mod installed.
  Server-managed Xaero waypoint sets use an internal `sw␟` prefix, so automatic sync updates only those sets and preserves personal Xaero waypoint sets. Upload maps these managed names back to their server list and waypoint names.
- #### Manually Sync to Xaero's Minimap
  Default value: `None`
  
  Triggered manually, requires Xaero's Minimap mod installed.
  
  This will replace any waypoint sets on Xaeros' Minimap that has the same name as a list on the server.
  - What stays:
  Waypoint sets with unique names that do not exist on the server.
  - What is lost:
  Any waypoints you added to these shared lists. Any list you created that happens to share a name with a server list.
