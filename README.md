# ServerWaypoint

Host waypoints on Minecraft server and allow players to download waypoints to their client and added into Xaero's Minimap automatically.

## Features
- Syncing waypoints from server automatically.
- Support server proxies like Velocity.
- Tree style display of waypoints.
- Easy to use waypoint list allowing player click to teleport, or remove waypoint.
- Commands auto-completion.
- Custom permission for `/wp <options>` commands.

## Dependencies

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)

## Setup
Install Fabric Loader and all dependencies with this mod on both server and client.

## Server Command
Command `/wp` is **only** registered on **dedicated server**.
- `/wp add` add a new waypoint. Cannot add waypoint with duplicate name. Prompts user to use `/wp edit` to replace the existing one.
- `/wp list` list all waypoints. Shows all waypoints in a tree hierarchy. Allowing user to click to teleport, edit and remove the waypoint.
- `/wp edit` edit a waypoint.
- `/wp remove` remove a waypoint by name. Shows the removed waypoint and click it to restore that waypoint.
- `/wp download` download waypoints and add to Xaero's Minimap (will not work without client installation).

## Configuration
The configuration file is stored at `<minecraft_root>\config\server_waypoint\config.json`.

Changes made in `config.json` will take effects after server restarts.

- ### Command Permission

    Changes the vanilla [permission level](https://minecraft.wiki/w/Permission_level) required to execute the command.
    
    Example:
    ```json5
    {
      "CommandPermission": {
        // /wp add
        "add": 0,
        // /wp edit
        "edit": 0,
        // /wp remove
        "remove": 0
      }
    }
    ```


## Server & Client Compatibility

| Server | Client | Description                                                                                   |
| :---: | :---: |-----------------------------------------------------------------------------------------------|
|   ✅    |   ✅    | All functionalities supported.                                                                |
|   ✅    |   ❌    | Downloading and syncing waypoints are unsupported.<br/>Client can still connect to the server | 
|   ❌    |   ✅    | No functionalities supported.<br/>Client can still connect to any server.                     |
|   ❌    |   ❌    | No functionalities supported.                                                                 |

✅ : Installed with this mod. ❌ : Not installed with this mod.\
Generally, the server with this mod installed will not prevent clients without this mod installed from connecting.


## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
