# Remote permissions and authorization (step 12)

Step 12 adds backend permission gates and reusable authorization callbacks. Steps 13–15 now add
coordinator/destination handoff services and `/wp remote tp` initiation through adapters. Live
platform startup and transfer integration remain Step 16. See [source initiation](cross-server-source-teleport.md).

## Permission contract

| Operation | Required node | Configured vanilla fallback |
| --- | --- | --- |
| Remote servers/list and their catalog identity suggestions | `server_waypoint.command.remote.list` | `CommandPermission.remoteList`: 0 |
| Initiate a remote teleport | `server_waypoint.command.tp` **and** `server_waypoint.command.remote.tp` | `CommandPermission.tp`: 2 and `CommandPermission.remoteTp`: 2 |
| Final destination teleport | Destination `server_waypoint.command.tp` | Destination `CommandPermission.tp`: 2 |

`PermissionKeys` and `PermissionStringKeys` expose the two new nodes to every existing backend
permission manager. `RemotePermissions` reads the supplied current configuration and permission
provider on every invocation; no permission result is cached. Browsing checks the command source.
Teleport initiation resolves the actual source player and checks both nodes on that player, so a
console or command-source permission cannot stand in for the player's permissions. A missing
player denies initiation/arrival. Console browsing follows the platform's normal permission rules.

`CoreWaypointCommand` gates browsing and teleport separately. The remote branch/help topic is
available with either permission; help shows only allowed operations. Teleport suggestions require
both teleport nodes, independently of browsing permission. Execution and cache-backed suggestions check again, including when a
Brigadier parse was created before revocation. Denied readers do not touch the catalog store.
Brigadier may still suggest static grammar words from an already parsed command; no catalog
identities are returned. Existing local list commands retain their behavior.

## Platform semantics and deployment guidance

- Fabric uses explicit assignments through Fabric Permissions API when available, falling back
  to its configured vanilla level. Without that API, only the vanilla level is checked.
- Forge and NeoForge currently use vanilla permission levels. They do not implement arbitrary
  permission-node assignments; installing a permission provider does not change these adapters.
- Paper checks `isPermissionSet(node)` first and honors `hasPermission(node)`, including explicit
  denials for operators. If unset, it uses `isOp()` and ignores the configured fallback level.
  This existing behavior is preserved. For example, an ordinary Paper player with an unset remote
  list node is denied even though `remoteList` is 0. Grant
  `server_waypoint.command.remote.list` explicitly for intended browsers. Grant both teleport nodes
  at the source and the local teleport node at the destination for intended teleport users; use
  explicit denials where required. Do not rely on vanilla levels to express Paper policy.

## Destination callback ownership

`DestinationAuthorization.ExportPolicy` receives only the exact `RemoteWaypointKey`. Its platform
implementation must resolve current authoritative destination state and current PUBLIC export
selection; return false if the waypoint is missing or not exported, and throw if state cannot be
read. Do not implement it using the source cache. It intentionally accepts no player UUID and
cannot represent player-private catalogs or offline player permission checks.

`prepare(key)` first enforces the local server ID and then evaluates this callback. It does not
check a player who has not arrived. `arrive(key, player)` repeats that export check and invokes the
final live-player permission callback (normally `RemotePermissions::canTeleportOnArrival`). It
returns stable denials for wrong destination, missing export, missing player, denied permission or
unavailable callbacks. Callback exceptions fail closed without copying their messages into results.
Permission/export changes after preparation therefore affect arrival. An allowed result is only
an authorization decision, not a reservation or proof of a valid claim.

The later handoff caller must bind the arrived player's authenticated UUID to a valid single-use
claim, run these callbacks on the appropriate owning thread, then resolve current coordinates and
teleport in that same owning-thread operation. This API supplies neither coordinates nor an offline
UUID-to-permission path. Coordinator admission, source routing, replay/expiry and rate checks remain
separate handoff responsibilities. Neither callback changes the KK versus trusted-loopback identity
boundary; plaintext is not cryptographically authenticated.

## Verification

On 2026-09-08, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passes 460 common tests and 67 proxy tests with no failures/errors/skips. The eight new cases cover:

- Exact destination/waypoint identity, export revocation, live permission revocation and missing players.
- Unavailable export/permission callbacks and absence of offline player checks during preparation.
- Both source nodes, console restrictions, destination local-only permission and live config levels.
- Revocation after Brigadier parsing, denied cache access and cache-backed suggestions.
- The checked-in Paper, Fabric, Forge and NeoForge permission managers compiled with Java 17 and
  executed against minimal platform API doubles: explicit allow/deny where supported, operator and
  vanilla fallback, console browsing, source/arrival checks and permission revocation.

The platform contract harness covers the currently active permission sources (the newer Minecraft
permission API branch). Its inputs are registered with Gradle so adapter edits invalidate the tests.
It does not boot Minecraft, Paper/Folia or LuckPerms, validate legacy generated branches, or prove
platform scheduling/claim binding. Existing shared-root tests also verify denied remote commands/help
and unchanged local browsing. Full backend builds and native runtime validation were not repeated.
