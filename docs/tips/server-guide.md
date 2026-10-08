# Server setup and command reference

Use the [quick-start guide](../../README.md) for installation and your first waypoint. This reference covers full command options, map uploads, server settings and Velocity setup for 4.0.0.

## Commands

Run `/wp` for a menu: most features are a click away, and every screen offers its next steps as links. The console, RCON and command blocks get the same information as plain text, with identifiers and coordinates written out.

On Minecraft 1.21.9 and later, player chat can show supported vanilla item icons and player heads without the client mod. Older versions and unsupported icons use text or initials. When a command runs through `/execute as <player> run wp ...`, that player receives the feedback and the original sender receives a copy labelled **Viewed as**.

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
    - Method IDs are `compass`, `map`, `bossbar`, `actionbar` and `text_display`. Use `default` to select the server's `defaultNavigationMethods`, or `all` for every supported method.
- `/wp remote` shows the remote servers and their state; `/wp remote page <number>` pages it.
- `/wp remote list [<server> [<dimension> [<list>]]]` browses cached remote waypoints with the same options as `/wp list`. Quote exact identities, including names matching option words. Distance sorting reports that cross-server distances are unavailable.
    - Results are read-only and work through ordinary server chat. A coloured dot shows each server's state: available, stale, unreachable or no access. `/wp remote details <server> <dimension> <list> <waypoint>` shows one waypoint. Run `/wp help remote` for help.
    - Catalog synchronization starts when cross-server configuration is enabled. See [Velocity runtime setup](../features/cross-server/specs/cross-server-velocity-runtime.md) and [remote catalog queries](../features/cross-server/specs/cross-server-catalog-queries.md).
- `/wp remote tp <server> <dimension> <list> <waypoint>` requests a teleport using exact cached identities (quote names with spaces). Stale or missing targets fail before preparation; the player stays on the source until destination preparation and fresh permission checks succeed, and the destination confirms the arrival. See [remote teleport initiation](../features/cross-server/specs/cross-server-source-teleport.md).
    - Velocity and dedicated backend runtime integration is implemented and disabled by default. Suggestions use only the local cache. Use unquoted dimension identifiers such as `minecraft:overworld` or `overworld` (default namespace: `minecraft`). See [configuration and validation](../features/cross-server/specs/cross-server-velocity-runtime.md).
- `/wp reload` reloads `config.json` and the translation files in the `lang/` directory beside it (see [configuration paths](#server-configurations)). `defaultPageLimit`, `defaultNavigationMethods`, `CommandPermission`, `addWaypointFromChatSharing` and `compressChunkedMessages` apply at once; `serverId`, `cross-server.json` and turning on `sendXaerosWorldId` need a restart. Waypoint files are not reloaded, so edit them while the server is stopped.
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
5. Join through Velocity and run `/wp remote`, then `/wp remote list survival`. Teleport to an existing exported waypoint, for example `/wp remote tp creative minecraft:overworld "Public list" "Home"`. Check the destination coordinates and feedback, then test the other direction. A server switch alone does not confirm waypoint arrival.

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
6. Restart Velocity, then both backends. Configure authenticated Velocity player forwarding so player UUIDs agree across servers. Run `/wp remote` and `/wp remote list survival`, then test `/wp remote tp creative minecraft:overworld "Public list" "Home"` with an existing exported waypoint. Verify destination coordinates and feedback in both directions.

Remote browsing uses `server_waypoint.command.remote.list` (default level 0). Remote teleport requires `server_waypoint.command.remote.tp` at the source, plus both `server_waypoint.command.tp` and `server_waypoint.command.remote.tp` at the destination (each defaults to level 2). The source's local teleport permission is independent. See the [administrator guide](../features/cross-server/cross-server-admin.md) for permissions, key rotation, and troubleshooting.

If destination preparation is denied by permissions, the failure message names the destination and shows the separate `tp` and `remote.tp` results. Both permissions are checked again when the player arrives. See [permission feedback](../features/cross-server/specs/2026-10-05-permission-check-feedback.md).

### Cross-server administration

- On the Velocity console, `/serverwaypoint status` shows whether the coordinator is running, its transport mode and which backends are online. Players need `server_waypoint.command.cross_server.status` to use it. Velocity can't grant permissions by itself, so give it to them with a permissions plugin such as LuckPerms.
- On a backend console, `/sw-cross-server-keygen` creates the backend's `NOISE_KK` key pair after you configure `cross-server.json`, without a first start and stop. It requires level 4 and never replaces an existing key.
- Set `"serverIconItem": "minecraft:diamond"` in a backend's `cross-server.json` and restart that backend to choose the item players see for it in the server selector. The default is `minecraft:beacon`.

The cross-server [release notes](../features/cross-server/cross-server-release-notes.md) and [release verification](../features/cross-server/validation/cross-server-release-readiness.md) provide more detail.

## Uploading from client map mods

Map sync replaces the waypoints Server Waypoint previously added, including local edits to those synced copies. Personal waypoints outside its managed sets remain separate. Upload any edits you want to keep before using **Sync now**.

Upload is initiated by the server but reads map data from the executing player’s client. The required `<source>` is `xaero` or `voxelmap`; the client must have both Server Waypoint and the selected map mod installed and ready. The server accepts only the dimensions and optional list/waypoint selected by the command.

In singleplayer (integrated-server mode), the host uses the same `/wp upload xaero` and `/wp upload voxelmap` commands. Waypoints are collected on the Minecraft client thread and passed directly to the server thread for validation and application, without upload request packets or chunked upload transport. This also applies to the host of a world opened to LAN; joining players still upload from their own clients over the network. Permissions, scope, conflict policies, and deletion rules remain the same.

For Xaero, only normal, enabled, non-temporary waypoints are imported. Upload synchronizes the waypoint name, initials, coordinates, Xaero color, yaw, and local/global visibility. For VoxelMap, disabled waypoints, waypoints outside the active subworld and coordinate-highlight waypoints are skipped. Server-synced VoxelMap names are restored to their original list and waypoint identifiers; other VoxelMap waypoints are imported into a fixed `VoxelMap` list. VoxelMap coordinates are converted back from its dimension scale, initials are generated from the waypoint name, yaw is zero and visibility is local. Server-only display names, keywords, and descriptions are preserved when an existing waypoint is updated.

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

Messages follow each player's Minecraft language setting without requiring the client mod. Bundled languages include English, Simplified Chinese, Traditional Chinese, Hong Kong Chinese, Spanish and Hebrew. Help translate on [Crowdin](https://crowdin.com/project/server-waypoint).

English and Simplified Chinese include every current server and client translation key. The other bundled languages fall back to English for some client controls and server messages. Key coverage does not establish translation quality; see the [translation credits and human review status](../../TRANSLATOR_CREDITS.md).

### Add translations

Place the lang files in the `lang/` directory beside the server's [`config.json`](#server-configurations), for example `config/server_waypoint/lang/` on Fabric or `plugins/ServerWaypoint/lang/` on Paper. The server loads them at startup; use `/wp reload` if it is already running.

### Create a lang file

Follow the format used in [`en_us.json`](../../common/src/main/resources/lang/en_us.json), [`zh_cn.json`](../../common/src/main/resources/lang/zh_cn.json).

Name the lang file with a [valid language code](https://minecraft.wiki/w/Language#Languages).

Version 4.0.0 renamed most translation keys, so lang files made for 3.x need to be rebuilt from the current `en_us.json`.

Server translations use Java `MessageFormat`: keep zero-based placeholders such as `{0}` and `{1}`, preserve their indices, and write a literal apostrophe as `''`. Do not use printf placeholders such as `%s`.

### Translation order

If the translation you’ve added uses the same language code as the built-in language, this mod will try to find the translation key in the file you added first. If that key isn’t there, it’ll fall back to the built-in translation. So, if you’d like to use your own translation version, you can easily do that by adding your own file and overriding the built-in translation.

Community translator credits and credit pull request guidelines are documented in [`TRANSLATOR_CREDITS.md`](../../TRANSLATOR_CREDITS.md).

## Waypoint files and backups

### Location

For a dedicated server:

The `waypoints/` directory beside [`config.json`](#server-configurations), for example `config/server_waypoint/waypoints/` on Fabric or `plugins/ServerWaypoint/waypoints/` on Paper.

For a single player world:

`<minecraft-root>/saves/<world-name>/server_waypoint/waypoints/`

### File format

Back up the `waypoints/` directory before updating or editing files. All waypoints are saved in JSON files. Each JSON file contains one dimension's waypoints; its filename comes from the dimension identifier.
For example, Overworld waypoints are stored in `minecraft$overworld.json`.

Stop the server before editing waypoint files. `/wp reload` does not reload them.

## Server Configurations

Fabric, Quilt:

`<minecraft-root>/config/server_waypoint/config.json`

NeoForge, Forge:

`<minecraft-root>/defaultconfigs/server_waypoint/config.json`

Paper, Folia, Purpur:

`<server-root>/plugins/ServerWaypoint/config.json`

`/wp reload` applies changes to `defaultPageLimit`, `defaultNavigationMethods`, `CommandPermission`, `addWaypointFromChatSharing` and `compressChunkedMessages`. Changes to `serverId`, turning on `sendXaerosWorldId` and changes to `cross-server.json` take effect after a restart.

### Default Page Limit
Sets the page limit `L` that `/wp list` and `/wp remote list` use when the command does not include `limit`: flat views and single lists show `L` rows, while the tree view, the one-line-per-list view and the dimension and server lists hold `L + 5` lines. Values are constrained to `1-100`, and the default is `10`. This setting takes effect after `/wp reload`.

```json5
{
    "defaultPageLimit": 10
}
```
### Default Navigation Methods
Sets one or more methods enabled when `/wp navigate <dimension> <list> <waypoint>` starts a new session without a method argument, or when you explicitly select `default`. The value must be a non-empty array containing `compass`, `map`, `bossbar`, `actionbar`, or `text_display`. The default is `actionbar`.

```json5
{
    "defaultNavigationMethods": [
      "actionbar"
    ]
}
```
### Command Permission
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
See [remote authorization](../features/cross-server/specs/cross-server-authorization.md) for the integration boundary.

Before a remote transfer, the destination checks the absent player's permissions. Paper uses LuckPerms when installed; unset nodes fall back to the destination's configured level for each node. Without LuckPerms, those level checks apply directly. Setting both destination levels to `0` allows non-operators unless a node is explicitly denied. Other permission plugins' live player attachments are checked after arrival.

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
### Features
#### addWaypointFromChatSharing
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
#### sendXaerosWorldId
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
#### compressChunkedMessages
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

## Action logging

The server logs player actions at INFO level under `server_waypoint.actions`, including waypoint and list changes, restore, teleport, navigation, uploads, downloads, chat sharing and reload. Entries record the acting player and UUID, the original command sender and UUID, and the outcome. Under `/execute as`, the player and original sender remain distinct; GUI actions use the player for both.

Save completion, teleport results and upload summaries report success, failure or partial completion. Uploads summarize affected counts rather than logging each waypoint. Read-only browsing and routine synchronization do not produce action entries. See [player action logging](../features/action-logging/README.md) for fields and persistence outcomes.
