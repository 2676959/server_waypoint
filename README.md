# ServerWaypoint

Manage waypoints and sync them to other player's client automatically. Compatible with Xaero's minimap.

## Features
- Syncing waypoints from server automatically.
- Support server proxies like Velocity.
- Interactive waypoint list TUI allowing player click to teleport, or remove waypoint.
- Commands auto-completion.
- Custom permission for `/wp <options>` commands. Compatible with [LuckPerms](https://modrinth.com/plugin/luckperms).
- Support adding waypoint conveniently from Xaero's minimap waypoint chat sharing message without requiring client side installation.

## Dependencies
Required:
  - [Fabric API](https://modrinth.com/mod/fabric-api)
  
Optional:
  - [LuckPerms](https://modrinth.com/plugin/luckperms)
  - [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap) Not required for serverside installation.

## Commands
- `/wp add` add a new waypoint. Cannot add waypoint with duplicate name. Prompts user to use `/wp edit` to replace the existing one.
  - `/wp add <dimension> <list>` add a waypoint list.
- `/wp download` download waypoints and add to Xaero's Minimap (will not work without client installation).
- `/wp edit` edit a waypoint.
- `/wp list` list all waypoints. Shows all waypoints in a tree hierarchy. Allowing user to click to teleport, edit and remove the waypoint.
- `/wp reload` reload `config.json` and translation files in `\config\server_waypoint\lang`, feature `sendXaerosWorldId` requires restarting to take effect.
- `/wp remove` remove a waypoint by name. Shows the removed waypoint and click it to restore that waypoint.
  - `/wp remove <dimension> <list>` remove an empty waypoint list.
- `/wp tp` teleport the executor player to a waypoint

## Translation
This mod currently has built-in translations for English and Simplified Chinese. Command feedbacks will be automatically translated based on the language setting on the sender's client if the corresponding lang file is loaded.

- ### Add translations
  Place the lang files under the directory: `<minecraft_root>\config\server_waypoint\lang\`. This mod will load them on server starting, use `/wp reload` if the server is already running. 
  
- ### Create a lang file
  Follow the format used in [`en_us.json`](./common/src/main/resources/lang/en_us.json), [`zh_cn.json`](./common/src/main/resources/lang/zh_cn.json).

  Name the lang file with a [valid language code](https://minecraft.wiki/w/Language#Languages).
  
  

## Configurations
The configuration file is stored at `<minecraft_root>\config\server_waypoint\config.json`.

Changes made in `config.json` will take effects after server restarts.

- ### Command Permission
  Changes the vanilla [permission level](https://minecraft.wiki/w/Permission_level) required to execute the command.
  
  This will be overridden by the permission set by [LuckPerms](https://modrinth.com/plugin/luckperms).
  
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
      // /wp tp
      "tp": 2,
      // /wp reload
      "reload": 2
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

## License
This mod is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
