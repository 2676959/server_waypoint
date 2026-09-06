# Cross-server protocol v1 contract

This is the normative feature contract for implementation-plan step 1. MUST, MUST NOT,
and SHOULD express requirements for subsequent implementation steps. The constants and
identity types in `common/.../crossserver` implement only this contract; they do not enable
networking, register commands or permissions, or transfer players.

## Version and identity

- `CrossServerProtocol.PROTOCOL_VERSION` is **1**. It versions the backend/coordinator
  application protocol independently of the Minecraft custom-payload `ProtocolVersion`.
  An unsupported application version MUST fail closed; v1 defines no compatibility fallback.
  Transport framing and numeric message IDs will be specified in their implementation steps.
- `RemoteServerId` MUST be an explicitly configured, stable, 1–64 character ASCII string
  matching `[a-z0-9][a-z0-9_-]{0,63}`. No trimming, case conversion, or Unicode normalization
  is allowed. Null and invalid values MUST be rejected. It MUST NOT derive from the random
  integer `Config.serverId`, a display name, or a connection address.
- In `NOISE_KK`, the coordinator MUST bind each ID to its registered backend public key.
  In explicit `PLAINTEXT`, the ID is only a configured claim under the trusted-host model below.
  Both modes MUST reject duplicate active registrations; syntax alone does not prove identity.
- `RemoteWaypointKey(serverId, dimensionName, listName, waypointName)` is an immutable tuple.
  Every component MUST be non-null. Dimension, list, and waypoint strings MUST be preserved
  exactly, including case, whitespace, Unicode, and empty strings; existence and export
  validation belong to the authoritative destination. Equality MUST compare all four fields,
  never a delimiter-joined string. Coordinates and display labels are not identity.
- Identical local identities on different servers MUST remain distinct. Remote entries MUST
  remain separate from local waypoint files, client caches, rendering, and navigation sessions.

## Command and permission contract

The v1 command branch is:

```text
/wp remote servers
/wp remote list [server] [dimension] [list] [existing list options]
/wp remote tp <server> <dimension> <list> <waypoint>
```

Optional identity arguments are hierarchical: a dimension requires a server, and a list
requires both. Omitted scopes mean all authorized remote entries within the supplied scope.
The list options reuse local list search, sort/order, page, limit, and view syntax; they MUST
NOT reinterpret remote coordinates as distances from a player on another server. Exact
identity arguments MUST support quoting/escaping. Suggestions MUST use the local remote
catalog cache without coordinator I/O. Local command behavior MUST remain unchanged.

| Operation | Permission node | Default level |
| --- | --- | --- |
| Remote server/list browsing | `server_waypoint.command.remote.list` | 0 |
| Remote teleport | `server_waypoint.command.remote.tp` | 2 |

Teleport also MUST require the existing local teleport permission at the source and a final
local teleport permission check at the destination. Catalog visibility does not grant teleport
permission. Deployments SHOULD assign explicit Paper nodes because its existing unspecified-node
fallback uses `isOp()`. Registering these nodes and enforcing them is a later step.

## Catalog states and export

`RemoteCatalogState` has these semantic values; enum ordinals MUST NOT be used as wire IDs:

| State | Meaning |
| --- | --- |
| `AVAILABLE` | A current authorized snapshot, including an explicitly empty snapshot. |
| `STALE` | A retained snapshot whose freshness is no longer assured. |
| `UNAVAILABLE` | No current source is available; any retained snapshot remains stale. |
| `UNAUTHORIZED` | Access is denied; retained entries MUST NOT be exposed to that reader. |

An unavailable source MUST NOT clear entries as though it published an empty catalog.
Removing all entries requires an explicit revisioned empty snapshot, configured cache expiry,
or administrative removal. Stale data is advisory; it never authorizes a teleport.

`CatalogExportPolicy.PUBLIC` is the only v1 export policy. It means a server-configured,
player-independent export set shared with authorized network peers, not anonymous Internet
access. Backends MUST apply that policy before publication and recheck it at preparation and
arrival. Exporting a waypoint MUST NOT permit remote editing or bypass player permissions.
Player-private catalogs and offline per-player permission lookup are excluded from v1.

## Threat model and trust boundaries

The coordinator/proxy and destination backend are trusted authorities. Clients, network traffic,
cached catalogs, and source-supplied player claims are untrusted. A backend credential grants
only its configured server identity; it MUST NOT authorize impersonating another backend or
choosing an arbitrary player. A compromised coordinator or authoritative destination is outside
the protection provided by this protocol.

The default `transportMode` is `NOISE_KK`; the feature remains disabled by default. The initial
symbolic suite is `NOISE_KK_25519_AESGCM_SHA256`, using the Noise protocol/configuration value
`Noise_KK_25519_AESGCM_SHA256`. Each side MUST own its private key and securely pin the other's
public key during pairing. No per-backend PSK is required. Backend public keys MUST bind to exact
server IDs and be independently revocable. Pairing MUST authenticate the bootstrap exchange;
KK alone cannot establish trust in previously unknown keys.

KK MUST bind mode, versions, suite negotiation, ID, nonces, and capabilities into its transcript.
The initial ID is only a key lookup hint. Keep handshake payloads empty and require the backend's
first encrypted transport confirmation before operational requests or coordinator catalogs.
Reject replay, invalid tags, exhausted nonces, and downgrade attempts. Serialize session crypto
operations and enforce byte/object/request/time budgets. Noise records MUST NOT exceed 65,535
bytes; larger application frames require bounded reassembly. Hybrid suite design is reserved
for later work and MUST NOT be advertised in v1. KK dependency selection remains unverified;
the historical NKpsk0 experiment is not evidence that KK passes.

`PLAINTEXT` is an explicit exception to transport encryption/authentication, not a negotiated
suite. Both endpoints MUST select it explicitly, use literal loopback IP addresses, and validate
actual socket endpoints as loopback. Reject hostnames, wildcard/non-loopback endpoints, and mode
mismatches. No key files, pins, or pairing are required; reject crypto configuration fields in
this mode. Never retry an encrypted connection as plaintext. Any local process with listener
access can impersonate an enabled backend; this mode trusts the host's processes and MUST NOT
be described as cryptographically authenticated. The credential isolation guarantee above
applies to KK only. The same application validation, budgets, proxy checks, player permissions,
and handoff lifecycle MUST apply in both modes; those checks cannot restore missing transport
identity or confidentiality in plaintext mode.

The proxy MUST obtain the authenticated player's UUID and verify the current source backend.
Any feature-owned plugin-message channel MUST be consumed and its source checked. Destination
preparation MUST succeed before a Velocity switch. Handoffs MUST be expiring, single-use, and
bound to the player, source, destination, request, and exact waypoint key. At arrival the
destination MUST recheck permissions, export policy, and existence, then resolve its current
coordinates on the owning server/player thread. Cached or source-supplied coordinates MUST NOT
be authoritative. Missing or rejected handoffs MUST NOT cause a teleport.

V1 explicitly excludes arbitrary unmodified servers, RCON, remote mutation/editing, player-private
catalogs, and direct client connections to the coordinator. It adds no runtime networking in
step 1. The remaining implementation and validation gates are in the
[implementation plan](cross-server-waypoint-teleportation-plan.md).
