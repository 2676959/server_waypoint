# ServerWaypoint

Host waypoints on Minecraft server and allow players to download waypoints to their client and added into Xaero's Minimap automatically.

## Features
- Syncing waypoints from server automatically.
- Support server proxies like Velocity.
- Tree style display of waypoints.

## Dependencies

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)

## Setup
Install Fabric Loader and all dependencies with this mod on both server and client.

## Server Command
Command `/wp` is **only** registered on **dedicated server**.
- `/wp add` To add a new waypoint.
- `/wp list` To list all waypoints. Click the waypoint displayed can teleport the player to waypoint directly if the player has the permission.
- `/wp edit` To edit an existing waypoint.
- `/wp download` To download waypoints and add to Xaero's Minimap.

## Server & Client Compatibility

| Server | Client | Description |
| :---: | :---: | --- |
|   ✅    |   ✅    | All functionalities supported.|
|   ✅    |   ❌    | Downloading waypoints are unsupported.<br/>Client can still connect to the server | 
|   ❌    |   ✅    | No functionalities supported.<br/>Client can still connect to any server. |
|   ❌    |   ❌    | No functionalities supported. |

✅ : Installed with this mod. ❌ : Not installed with this mod.\
Generally, the server with this mod installed will not prevent clients without this mod installed from connecting.


## License

This template is available under the CC0 license. Feel free to learn from it and incorporate it in your own projects.
