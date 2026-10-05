# Remote permissions and authorization (step 12)

Step 12 adds backend permission gates and reusable authorization callbacks. Steps 13–15 now add
coordinator/destination handoff services and `/wp remote tp` initiation through adapters. Live
platform startup and transfer integration remain Step 16. See [source initiation](cross-server-source-teleport.md).

## Permission contract

| Operation | Required node | Configured vanilla fallback |
| --- | --- | --- |
| Remote servers/list and their catalog identity suggestions | `server_waypoint.command.remote.list` | `CommandPermission.remoteList`: 0 |
| Initiate a remote teleport | Source `server_waypoint.command.remote.tp` | Source `CommandPermission.remoteTp`: 2 |
| Prepare a destination teleport | Destination `server_waypoint.command.tp` **and** `server_waypoint.command.remote.tp` | Destination offline permission lookup described below |
| Final destination teleport | Destination `server_waypoint.command.tp` **and** `server_waypoint.command.remote.tp` | Destination `CommandPermission.tp`: 2 and `CommandPermission.remoteTp`: 2 |

`PermissionKeys` and `PermissionStringKeys` expose the two new nodes to every existing backend
permission manager. `RemotePermissions` reads the supplied current configuration and permission
provider on every invocation; no permission result is cached. Browsing checks the command source.
Teleport initiation resolves the actual source player and checks only `remote.tp` on that player, so a
console or command-source permission cannot stand in for the player's permissions. A missing
player denies initiation/arrival. Console browsing follows the platform's normal permission rules.
Source local `tp` permission is independent: a player denied local waypoint teleportation can still
initiate a remote teleport. The destination must authorize both its own `tp` and `remote.tp`
permissions before transfer and again on arrival; a source grant cannot authorize arrival at a
destination denying either node. Ordinary local waypoint teleportation still requires only `tp`.

`CoreWaypointCommand` gates browsing and teleport separately. The remote branch/help topic is
available with either permission; help shows only allowed operations. Teleport suggestions require
`remote.tp`, independently of local teleport and browsing permissions. Execution and cache-backed
suggestions check again, including when a Brigadier parse was created before revocation. Denied
readers do not touch the catalog store.
Brigadier may still suggest static grammar words from an already parsed command; no catalog
identities are returned. Existing local list commands retain their behavior.

## Platform semantics and deployment guidance

- Fabric uses explicit assignments through Fabric Permissions API when available, falling back
  to its configured vanilla level. Without that API, only the vanilla level is checked.
- Forge and NeoForge currently use vanilla permission levels. They do not implement arbitrary
  permission-node assignments; installing a permission provider does not change these adapters.
- Paper checks `isPermissionSet(node)` first and honors `hasPermission(node)`, including explicit
  denials for operators. If unset, it checks the actual vanilla command-source level against the
  configured fallback. Grant `server_waypoint.command.remote.list` for intended browsers and
  `server_waypoint.command.remote.tp` at the source for intended remote teleport users. Grant
  both `server_waypoint.command.tp` and `server_waypoint.command.remote.tp` at the destination,
  and use explicit denials where required.
  Destination preparation uses the separate offline lookup described below.

## Permission check before transfer

`DestinationHandoffService.prepare` returns a completion stage. It reserves a bounded record, then
calls `DestinationPlatform.canPrepare` using the coordinator-bound player UUID. Only a successful
destination permission lookup and authoritative waypoint resolution produce `HandoffPrepared`.
Denied, failed, timed-out or disconnected lookups cannot authorize a proxy transfer. A late permission
response cannot revive an expired or cancelled record. The final live-player check still runs on arrival.

- Fabric queries the Fabric Permissions API offline UUID lookup for both nodes, with the destination
  operator level and each node's configured `tp`/`remoteTp` level as its separate fallback.
  Forge/NeoForge use those operator-level checks directly. Both checks must allow preparation.
- Paper optionally loads LuckPerms user data asynchronously and checks `server_waypoint.command.tp`
  and `server_waypoint.command.remote.tp` in the destination's static server context. Each undefined
  node falls back to operator status; explicit denial of either node overrides operator status. Without
  LuckPerms, only the native operator fallback is available. Other plugins' player attachments and
  dynamic player contexts cannot be evaluated while the player is absent; their live result is
  checked on arrival. Deployments needing offline node grants should use LuckPerms.
- All permission-provider exceptions fail closed. No source-side grant is forwarded as a destination grant.

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

On 2026-10-05, source initiation was changed to require only `remote.tp`, while destination
preparation and arrival require both `tp` and `remote.tp`. Arrival, Paper offline lookup and mod
offline adapter regressions failed against the previous destination rule, then passed after the
change. The Survival/Creative case verifies allowed initiation with source `tp` denied and denied
arrival back on Survival. The mod contract also verifies separate configured fallback levels,
explicit denials, delayed provider responses and provider failure. No native server transfer or
live permission-provider validation was run.

`./gradlew :common:test :proxy-common:test :paper:26.2-paper:test
:mods:26.1.2-fabric:compileJava --max-workers=2 --console=plain` passed 625 common, 161 proxy and
30 Paper tests with no failures, errors or skips, and compiled Fabric 26.1.2. The active Stonecutter
projects were not switched.

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

## Step-16 integration

The services now have live coordinator/backend lifecycle and Velocity transfer wiring. See
[the runtime contract and current validation](cross-server-velocity-runtime.md). Earlier step-specific
verification above describes its historical boundary; client/GUI and full release hardening remain.
