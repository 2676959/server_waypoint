# Cross-server administrator guide

Cross-server waypoints are opt-in and **disabled by default**. Install the matching Server Waypoint
backend artifact on each dedicated backend and the shaded `server_waypoint-<version>-velocity.jar`
on Velocity. The proxy plugin requires Java 25. Use the Java version required by each backend.
Integrated servers do not participate. Remote commands work with vanilla clients; the remote GUI
requires a matching protocol-1 client mod. Update the proxy and all participating backends together.
See the [release verification record](validation/cross-server-release-readiness.md) before deploying.

## Pair two backends with KK

1. Assign stable IDs, for example `survival` and `creative`. IDs contain 1–64 lowercase ASCII letters,
   digits, `_` or `-`, starting with a letter or digit. Keep the IDs stable when changing display names.
   Configure the two corresponding registered servers in Velocity. A stable ID maps to exactly one
   Velocity server name, and mappings cannot share a destination name.
2. Start each component once to create its disabled `cross-server.json`, then stop it. On Paper the
   directory is `plugins/ServerWaypoint`; on Velocity it is `plugins/server_waypoint`. On mod loaders
   use the Server Waypoint configuration directory created by that installation. Backend and
   coordinator files have different editable templates with every supported top-level field.
   Replace `replace-with-...` IDs and `PASTE_...` public keys with real values before enabling
   their corresponding entries. The sample Velocity backend entry starts disabled.
3. Use the [KK configuration examples](specs/cross-server-velocity-runtime.md#configuration). For each
   backend set `enabled`, `serverId`, `coordinator`, `transportMode: "NOISE_KK"` and
   `catalogExport: "PUBLIC"`. On Velocity set `enabled`, `listen`, `transportMode: "NOISE_KK"`
   and two `backends` entries with their `velocityServer` mappings. Leave the public-key placeholders
   in place for this generation pass. Start and stop the components: invalid pins deliberately
   prevent operation, but KK startup writes each component's `cross-server-public-key.txt`.
   On a Paper or dedicated mod backend, the server console can run
   `/wp cross-server generate-key` after configuring `cross-server.json` to generate the backend's
   `credentials/static.key` and `cross-server-public-key.txt` without a startup pass. The command
   requires command level 4 and refuses to replace an existing key. With a custom
   `credentialsDirectory`, it writes `static.key` there instead. Restart after pin exchange.
4. Exchange **only those public-key files** through a trusted administrative channel. Compare the
   complete contents with the administrator of the other machine. Copy the coordinator's key into
   each backend's `coordinatorPublicKey`; copy each backend's key into that stable ID's `publicKey`
   on Velocity. These are canonical Base64 X25519 SPKI keys, not certificates. Never copy a backend's
   `credentials/static.key` to another component. Each component must have its own private key.
5. Restart Velocity, then both backends. Join through Velocity with a test player and run
   `/wp remote servers`. Each healthy exported backend should become available. Run
   `/wp remote list survival`, then teleport to a real exported target, for example
   `/wp remote tp creative "minecraft:overworld" "Public list" "Home"`.
6. Verify arrival coordinates and success feedback on the destination. A server switch alone does
   not prove waypoint arrival. Test both directions and a denied player before admitting users.

The shipped runtime uses this public-pin exchange. Pairing-code APIs exist internally, but there is
no shipped pairing-code command or network bootstrap carrier. Do not invent a `/wp pair` command.
Only PUBLIC export is supported: enabling export shares local waypoint lists with the coordinator
and authorized readers on participating servers. Confirm that these lists are appropriate to share.

## Binding and firewall rules

For one host, keep the coordinator on `127.0.0.1:25580` or `[::1]:25580`. For multiple hosts use KK,
bind to a private reachable interface and permit the coordinator TCP port only from backend hosts.
The waypoint TCP port is separate from Velocity's player port and each backend's Minecraft port.
Restrict direct player access to backends and configure authenticated player forwarding appropriate
to the backend platform. Proxy and backend player UUIDs must agree. KK authenticates backend
transport identities; it does not configure or replace Minecraft player forwarding.

## Explicit local plaintext

Set `transportMode: "PLAINTEXT"` at **both** ends. Use literal loopback IPs; hostnames (including
`localhost`), wildcard and non-loopback endpoints are rejected. Remove `credentialsDirectory`,
`coordinatorPublicKey`, `requiredSuite` and backend registry `publicKey` fields. Existing credential
files remain untouched and unused. Restart all changed components.

Plaintext has no confidentiality or cryptographic backend authentication. Other local processes can
impersonate an enabled backend ID. Use it only when every local process is inside the intended trust
boundary. Session, player, destination and permission checks still apply. A KK error never falls back
to plaintext; investigate the error instead of switching modes as a recovery shortcut.

## Permissions and read-only behavior

Remote browsing requires `server_waypoint.command.remote.list` (`remoteList`, default level 0).
Remote teleport requires both `server_waypoint.command.tp` and
`server_waypoint.command.remote.tp` (`tp` and `remoteTp`, default level 2) at the source. The
actual destination player must pass local teleport permission and current PUBLIC export checks.
An optional Velocity `proxyPermission` adds another check; omitting it does not remove backend checks.
Remote teleport does not require browse permission. See [authorization](specs/cross-server-authorization.md).

Remote catalogs are read-only caches, separate from local waypoint storage. Exact server, dimension,
list and waypoint identities are retained; quote spaces, empty names and dimension IDs. `STALE`,
`UNAVAILABLE` and an available empty catalog mean different things. Suggestions and GUI filtering
read the cache and never wait on network I/O. A selection can become invalid before confirmation.

## Rotation and revocation

Configuration changes require restart; there is no live administrative reload/revoke command.
To revoke a backend, disable/remove its Velocity registration and restart Velocity. This closes old
sessions and prevents new admission. Disable the backend configuration as well. For an incident,
block its host at the firewall immediately while arranging the restart. A stolen valid private key
continues to represent that identity until revocation; encryption cannot distinguish its owner.

For planned backend rotation, stop the backend, disable its registration and restart the coordinator.
Securely archive its credential directory outside the active location, choose a new empty
`credentialsDirectory`, and run the key-generation pass. Exchange the new public key, replace that
backend's coordinator registration pin, then restart the coordinator and backend. Confirm availability
and transfer again. Do not restore an old compromised key to recover connectivity.

Coordinator rotation affects every backend: stop participation, generate a fresh coordinator key
in a new credential directory, verify and update its public pin on every backend, then restart.
If a backend has an installed `coordinator.pin`, an explicit conflicting pin is rejected. Use a new
credential directory and re-pair that backend with a fresh key too, updating its coordinator registry
entry. Preserve waypoint files and `cross-server-catalog-state`; neither contains the private key.
Do not paste private keys, pairing codes or complete credential directories into support reports.

## Recovery and troubleshooting

On the Velocity console, run `/serverwaypoint status` to see whether the coordinator is starting,
disabled, running, or unavailable. It reports the configured transport mode (`NOISE_KK` encrypted or
`PLAINTEXT` unencrypted), the listening port when running, and online and offline enabled backend
server IDs. The mode shows as inactive when the feature is disabled. Disabled backend entries are
not included. Players need
`server_waypoint.command.cross_server.status` to use this command;
it shows the same administrative information. The command reads current in-memory state and does
not reload configuration. Use the startup log and the reported error to diagnose a failed start.

| Symptom | Check and recovery |
| --- | --- |
| Disabled/unavailable after install | Configuration is disabled by default; verify `enabled` on both ends and restart. |
| Startup fails during first KK start | Inspect the generated public-key file and complete trusted pin exchange. Missing/malformed/conflicting pins fail closed. |
| No remote server appears | Verify TCP reachability, mode, exact stable ID, enabled registry entry and unique Velocity mapping. Review both sides' lifecycle status. |
| Repeated reconnects | Check key mismatch/revocation, duplicate stable IDs, stalled peers, resource limits and destination availability. Do not relax authentication. |
| Plaintext endpoint rejected | Use a literal loopback address at both ends and remove all crypto fields. |
| Stale/changed selection | Wait for republish, refresh and select the target again. Never copy cached coordinates into a teleport workaround. |
| Permission denied | Check both source nodes, optional proxy node, actual destination player permission and PUBLIC export. |
| Switched but arrival failed | Check destination world/waypoint removal, permissions, deadline and player identity. Re-select after recovery; do not replay the old handoff. |
| Coordinator restarted | Old sessions/handoffs are invalid. Backends reconnect and republish; wait for fresh available catalogs before retrying. |

Transport defaults cap connections at 32, handshake time at 10 seconds, operation time at 30 seconds,
pending requests at 64, retained state at 4 MiB per direction and messages at 65,536 per session.
Retained replay history can force reconnect earlier than the message ceiling. Runtime handoff requests
also have shorter deadlines and bounded queues. These are resource ceilings, not a promise of unlimited
throughput or a per-second rate allowance. Errors close the affected session; reconnect repopulates
catalogs. Report version, transport mode, non-secret IDs, timestamp and failure phase with sanitized
logs. See [transport bounds](specs/cross-server-tcp-transport-v1.md) and
[runtime ownership](specs/cross-server-velocity-runtime.md).

## Teleport coordinator logs

Both sides write activity and connection history through dedicated SLF4J categories in the normal
server logs: `server_waypoint.teleport_coordinator.backend` on each backend and
`server_waypoint.teleport_coordinator.proxy` on Velocity. Filter or route these categories in the
server logging configuration when a separate file is desired.

Handoff entries include request UUID, player UUID when carried by that phase, source/destination,
phase and result. Use the request UUID to correlate rejections and cancellations with the initial
player entry. Proxy transfer start/result and backend final arrival/source results are logged
separately. Connection entries cover registration, failures, disconnects, backend retry delays and
coordinator start/stop. These logs exclude credentials, raw packets and waypoint coordinates;
control characters in labels are replaced and logged labels are bounded.

Destination teleport permission is checked before the proxy switches servers, then checked again
on arrival. See [offline provider behavior](specs/cross-server-authorization.md#permission-check-before-transfer).

## Remote server selector icon

Set `"serverIconItem": "minecraft:diamond"` in each backend's `cross-server.json` and restart
that backend to advertise its selector icon. The default is `minecraft:beacon`. Use an exact
namespaced item identifier, at most 256 ASCII characters; malformed identifiers reject backend
startup. A valid item unavailable on a client's registry (or air) renders as a compass.
This setting applies to both modded and Paper backends and is not a coordinator setting.

Server icon metadata keeps application protocol **1** and Minecraft custom-payload protocol **1**.
Update the coordinator, all backends, and modded clients together; if `protocolVersion` is explicitly
set in `cross-server.json`, keep it at `1`. Builds from before the selector change advertise the
same protocol number but do not understand the new icon field.
