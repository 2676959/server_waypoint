# Cross-server waypoints — release notes

Cross-server waypoints first shipped in Server Waypoint 4.0.0. The [changelog](../../../CHANGELOG.md)
covers the rest of that release.

- Optional Velocity integration lets players browse PUBLIC waypoint catalogs and request prepared,
  destination-validated teleports across dedicated backends. The feature is disabled by default.
- Remote commands work without the client mod. `/wp remote` shows the connected servers and their
  state, and `/wp remote page <n>` pages it. `/wp remote list [<server> [<dimension> [<list>]]]`
  browses their waypoints with the `/wp list` options except distance sorting,
  `/wp remote details` shows one waypoint, and `/wp remote tp` teleports to it. `/wp help remote`
  explains each command.
- With the matching client mod, the waypoint manager has a read-only remote view. A local/remote
  toggle appears in its sidebar when there are remote servers to show. In the remote view, a server
  rail picks a server and marks stale and unavailable ones, and the list and details panels show
  that server's waypoints. The teleport button rechecks the selection, sends `/wp remote tp`
  without a confirmation dialog and closes the screen, so progress and failures appear in chat.
  See the [remote waypoint manager](specs/cross-server-gui.md).
- Default Noise KK authenticates paired backend keys and encrypts TCP traffic. Pairing uses public
  keys and requires no manually created certificates. Explicit loopback-only plaintext is available
  for trusted local processes; it provides no cryptographic identity or encryption and is never a fallback.
- Source, proxy and destination checks bind each one-time handoff to the exact target and player.
  The destination resolves current waypoint coordinates again after arrival.
- Remote data does not replace local waypoint files. Existing local commands remain local.

## Administration

- `/serverwaypoint status` on the Velocity console reports the coordinator's state, transport mode
  and listening port, and which enabled backends are online. Players need
  `server_waypoint.command.cross_server.status` to run it.
- `/sw-cross-server-keygen` on the console of a Paper or dedicated mod backend creates that
  backend's `NOISE_KK` key pair once `cross-server.json` is configured, without a start-and-stop
  pass. It requires command level 4 and never replaces an existing key.
- `serverIconItem` in a backend's `cross-server.json` sets the item shown for that server in the
  server rail. The default is `minecraft:beacon`.
- Remote browsing requires `server_waypoint.command.remote.list` (`remoteList`, default level 0).
  Remote teleport requires `server_waypoint.command.remote.tp` (`remoteTp`, default level 2) at
  the source, independently of its local teleport permission. The destination requires both
  `server_waypoint.command.tp` and `server_waypoint.command.remote.tp` (`tp` and `remoteTp`, each
  default level 2) before transfer and on arrival. Local waypoint teleportation requires only `tp`.
- When a destination rejects the server connection, Velocity shows its rejection message to the
  player when available, including whitelist and ban reasons. Standard handoff failure feedback
  remains; the cross-server protocol is unchanged.

## Compatibility

Install the shaded Velocity plugin, `server_waypoint-<version>-velocity.jar` (Java 25), and the
backend artifacts from the same release, and update modded clients with them. Remote client
synchronization uses Minecraft custom-payload protocol 2, independently of cross-server application
protocol 1 between the backends and the coordinator.

See the [administrator guide](cross-server-admin.md) for setup, permissions, rotation and recovery.
The [release verification record](validation/cross-server-release-readiness.md) and the later
[validation records](validation/) show what was tested before release and what those runs don't cover.

Cross-server teleport permission denials now name the server and show independent colored
`tp` and `remote.tp` statuses. Deploy matching backends and coordinator builds; the application protocol version remains 1.
