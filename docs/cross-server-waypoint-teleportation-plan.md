# Cross-Server Waypoint Teleportation Implementation Plan

## Status and objective

This document plans cross-server waypoint discovery and teleportation for compatible Server
Waypoint installations connected through Velocity. The proxy hosts the coordinator and performs
every player server switch.

The default transport is certificate-free `NOISE_KK`, using a reviewed Noise implementation
with X25519, AES-256-GCM, and SHA-256. Each backend and the coordinator owns a static key pair;
pairing exchanges public keys, with no per-backend PSKs. An explicitly selected `PLAINTEXT` mode
provides unencrypted, unauthenticated TCP on loopback only for trusted same-host deployments. The wire format reserves cryptographic-suite negotiation and variable-length handshake
messages so a future hybrid X25519 plus ML-KEM suite can be added without changing application
messages.

Implementation status is tracked in [the progress record](cross-server-waypoint-teleportation-progress.md).
Steps 1–13 now include transport, pairing/credentials, lifecycle agents, catalog distribution, permission-gated remote commands, authorization callbacks and coordinator handoff state; platform startup and the remaining
services are not implemented. See the progress record for current evidence.

## Scope

### Included

- Register compatible backends over authenticated encrypted KK connections by default.
- Offer explicit loopback-only plaintext connections under the trusted-host model described below.
- Publish each backend's configured read-only waypoint catalog.
- Cache remote catalogs for commands, command suggestions, and the client waypoint manager.
- Identify every remote waypoint by server, dimension, list, and waypoint identity.
- Authorize and prepare a one-time cross-server handoff.
- Switch players through Velocity.
- Resolve the waypoint from authoritative destination state and teleport after arrival.
- Fail safely when a catalog is stale, a waypoint changed, a permission is denied, or a component
  is unavailable.

### Not included in the first release

- Querying unmodified Minecraft servers.
- Editing, removing, rendering, or synchronizing remote waypoints as if they were local.
- Using RCON.
- Sending network requests for each command-suggestion keystroke.
- Player-private target catalogs that require offline permission lookup. The first release exports
  a server-configured catalog and applies player permissions at the source and again after arrival.
- Post-quantum cryptography in the initial suite. Only protocol compatibility for a future hybrid
  suite is included.
- A BungeeCord plugin in the first release. Proxy integration remains behind a platform-neutral API
  so a later version can add a BungeeCord adapter without changing backend or application protocols.
- Adding historical Paper targets below the repository's existing Paper version matrix.

## Architectural decisions

### Hub-and-spoke coordinator

All backends make outbound connections to one coordinator:

```text
                         Coordinator
                  catalog + handoff registry
                     /         |         \
                    TCP        TCP        TCP
                   /            |            \
              Backend A     Backend B     Backend C
```

- The Velocity plugin hosts the coordinator and its platform transfer adapter.
- The coordinator is control-plane state, not waypoint authority. Each backend remains
  authoritative for its own waypoint files and final teleport.
- Only the coordinator TCP port listens for backend connections. Backend agents connect
  outbound, simplifying firewall configuration.

### Separate local and remote waypoint state

Remote data must not be inserted into `WaypointFilesManagerCore`. Local and remote dimensions and
list names can collide, and inserting remote entries there could make them appear editable or
renderable.

Introduce immutable remote-catalog types:

```text
RemoteServerId
RemoteWaypointKey(serverId, dimensionName, listName, waypointName)
RemoteCatalogSnapshot(serverId, catalogRevision, dimensions, receivedAt)
RemoteCatalogState(AVAILABLE | STALE | UNAVAILABLE | UNAUTHORIZED)
```

`RemoteCatalogStore` owns bounded snapshots keyed by stable string server ID. Snapshot replacement
is atomic. Readers never observe partially applied updates.

Do not reuse the existing random integer `Config.serverId` as the cross-server identity. Add an
explicit stable string ID such as `survival`, validated for uniqueness by the coordinator.

### Separate cross-server navigation from local navigation

`NavigationTarget` and `NavigationService` remain server-local. Do not place a remote target into an
active local navigation session.

Cross-server selection uses `RemoteWaypointKey`. After the destination validates the handoff and
resolves that key against its local data, it creates the normal local `NavigationTarget` only if a
future handoff action requests navigation. The first release implements destination teleport only.

## Project and package ownership

### `common`

Place code here only when it is used by backends and proxy/coordinator implementations:

- Cross-server identities and immutable catalog snapshots.
- Coordinator/backend application messages and canonical codecs.
- Catalog revision and validation rules.
- Handoff records, status values, and expiry rules.
- Permission-independent authorization request/result models.
- Secure-channel interfaces, framing limits, and transport state machines if the same implementation
  is used by both Paper and modded backends.
- Unit-test fixtures and protocol test vectors.

Keep this protocol version separate from `ProtocolVersion.PROTOCOL_VERSION`, which belongs to the
Minecraft client/server custom-payload protocol.

### New `proxy-common` subproject

Code shared by Velocity and potential future proxy adapters, but not by normal backends, belongs
here:

- Coordinator server registry.
- Catalog aggregation and fan-out.
- Pending handoff registry.
- Player-to-backend routing abstraction.
- Proxy permission and transfer abstractions.
- Coordinator lifecycle and metrics.

### New `velocity` subproject

The Velocity plugin is a thin adapter around `proxy-common`:

- Plugin lifecycle and configuration path.
- Player lookup and authenticated UUID extraction.
- Proxy permission checks.
- Destination-server lookup.
- Platform-specific asynchronous server switch.
- Connection and disconnect event integration.

The Velocity adapter must consume its own plugin-message channel instead of forwarding matching
messages. Client-originated messages must never be accepted as proxy-originated requests.

### Future BungeeCord compatibility boundary

Do not create a BungeeCord subproject or add a BungeeCord dependency in the first release. Preserve
future support through proxy-neutral contracts in `proxy-common`:

- `ProxyPlayerRouter` resolves a player by UUID, reports the current backend, and requests an
  asynchronous switch through `CompletionStage<TransferResult>`.
- `ProxyServerDirectory` resolves stable Server Waypoint IDs to opaque platform server handles.
- `ProxyPermissionChecker` checks proxy-level permissions without exposing a platform sender type.
- `ProxyLifecycle` supplies startup, shutdown, scheduling, and event callbacks needed by the
  coordinator.
- `TransferResult` represents success and stable failure categories without Velocity-specific
  exceptions or status classes.

Use adapter-owned opaque handle types or narrow generic parameters; do not put Velocity classes,
event types, scheduler types, or logger types in coordinator policy APIs. Add contract tests against
the fake proxy adapter. A later BungeeCord implementation should need only a new platform subproject
and adapter, not changes to backend messages, catalog storage, handoff state, or permissions.

### `mods`

- Start and stop the backend coordinator client with the dedicated server lifecycle.
- Schedule final destination work on the owning Minecraft server/player thread.
- Extend client custom payload handling for remote catalog snapshots and statuses.

### `paper`

- Start and stop the backend coordinator client with plugin lifecycle.
- Implement the destination arrival and teleport adapter.
- Implement proxy-mode integration without assuming that a player is currently carrying a plugin
  message.
- Use the player/entity scheduler for player-owned operations so Folia remains supported.

## Cross-server protocol

### Transport modes and layering

`transportMode` defaults to `NOISE_KK`. The feature itself remains disabled by default.

```text
TCP -> bounded mode/version preface
    -> NOISE_KK: bounded KK handshake -> encrypted Noise records
    -> PLAINTEXT: explicit loopback-only session -> unencrypted records
    -> bounded application frames -> catalog and handoff services
```

A listener has one configured mode. Both endpoints must explicitly agree; reject mode mismatches.
Plaintext is not a cryptographic suite and must never be offered through suite negotiation or used
as an automatic retry after a failed encrypted connection.

In `PLAINTEXT`, require literal loopback IP addresses for the coordinator bind and backend target
(`127.0.0.1` or `[::1]`, for example), and verify accepted/connected socket addresses are loopback.
Reject wildcard, non-loopback, and hostname endpoints in this mode. Do not provide a non-loopback
bypass. No keys or pairing are required. Configured server IDs and proxy mappings still apply,
but a local process can impersonate an allowed backend. This mode trusts processes on the host;
permissions and proxy UUID/source checks do not repair missing transport authentication.
Network attackers, tampering, and confidentiality are protected against only in `NOISE_KK` mode.

Both modes enforce application validation, request/sequence budgets, timeouts, duplicate-ID checks,
proxy player verification, permissions, and prepare/claim/arrival checks. Track the actual mode
on the session; never label a plaintext backend as cryptographically authenticated.

The Minecraft `ChunkedMessageManager` remains separate. TCP framing uses canonical codecs and
bounded application reassembly, not localized command text or JSON waypoint files.

### KK handshake and key selection

- Preface: magic, transport mode/version, application version, stable server ID, capabilities,
  fresh session nonces, offered suite IDs, and selected suite ID.
- The server ID is initially an untrusted lookup hint into the coordinator's enabled backend
  registry. Reject unknown/disabled IDs; do not try every key or authorize the claimed ID yet.
- Pairing must already have pinned the coordinator public key on the backend and registered the
  backend public key against that exact ID on the coordinator.
- Bind the complete preface and negotiation to the authenticated Noise prologue. Accept identity
  only after successful key authentication and confirmation. Reject unknown/disallowed suites.
- Keep the two KK handshake payloads empty. Require the backend's first encrypted transport
  confirmation before accepting operational requests or sending coordinator catalogs. This avoids
  treating replayable early handshake data as an authenticated application operation.

Initial symbolic suite ID: `NOISE_KK_25519_AESGCM_SHA256`.
Its Noise protocol name and configuration value are `Noise_KK_25519_AESGCM_SHA256`.

Reserve extensible suite IDs and variable-length handshake framing for a future hybrid
X25519/ML-KEM design. Its authentication pattern and exact name remain undecided; do not retain
or advertise the former PSK-based reservation as an approved KK successor. No hybrid suite ships
in v1. The historical `NKpsk0` spike is not a compatibility mode.

### Application envelope

Every application message, encrypted under KK or cleartext under plaintext mode, contains:

- Message type ID.
- Request UUID or event sequence.
- Payload length.
- Payload.

Use stable numeric message IDs and canonical codecs. Enforce independent byte and object budgets
before allocating collections or strings.

Initial message families:

```text
REGISTER_SERVER / REGISTER_RESULT
HEARTBEAT
CATALOG_METADATA
CATALOG_SNAPSHOT
CATALOG_DELTA
CATALOG_INVALIDATE
PREPARE_HANDOFF / HANDOFF_PREPARED / HANDOFF_REJECTED
CLAIM_HANDOFF / HANDOFF_CLAIMED
COMPLETE_HANDOFF / CANCEL_HANDOFF
ERROR
```

### Limits and failure behavior

Start with conservative configurable limits:

- Outer handshake frame: 64 KiB; each initial-suite Noise message is at most 65,535 bytes.
- Application frame: 1 MiB.
- Catalog chunk: 256 KiB.
- KK transport record: at most 65,535 bytes, including the 16-byte tag. Fragment larger application
  frames across records with bounded reassembly; never raise the Noise record limit.
- Plaintext records use bounded framing too; cleartext sequence fields do not prevent forgery.
- Pending requests per peer: 64.
- Pending handoffs per player: one.
- Handshake timeout: 10 seconds.
- Handoff expiry: 15 seconds.
- Idle heartbeat interval and disconnect timeout: configurable.

Reject invalid AEAD tags, unknown message types, invalid lengths, duplicate server IDs, replayed
request IDs, exhausted sequence numbers, and malformed catalog objects by closing the session and
logging a bounded diagnostic without secrets.

## Pairing and configuration

The examples below are the planned `cross-server.json` format, not implemented configuration.
Paths are relative to the component's configuration directory. Public keys use Base64-encoded
X.509 SubjectPublicKeyInfo; private-key files use binary PKCS#8 with owner-only permissions where
supported. Public pins may appear in ordinary configuration; private keys and temporary pairing
secrets must not appear in configuration dumps, logs, or `toString()` output.

### KK coordinator configuration

```json
{
    "enabled": true,
    "transportMode": "NOISE_KK",
    "listen": "127.0.0.1:25580",
    "protocolVersion": 1,
    "requiredSuite": "Noise_KK_25519_AESGCM_SHA256",
    "privateKeyFile": "credentials/coordinator.key",
    "backends": {
        "survival": {
            "enabled": true,
            "velocityServer": "survival",
            "publicKey": "<base64 X25519 public key>"
        }
    },
    "catalogCacheLimitBytes": 67108864
}
```

### KK backend configuration

```json
{
    "enabled": true,
    "transportMode": "NOISE_KK",
    "serverId": "survival",
    "coordinator": "127.0.0.1:25580",
    "protocolVersion": 1,
    "requiredSuite": "Noise_KK_25519_AESGCM_SHA256",
    "privateKeyFile": "credentials/backend.key",
    "coordinatorPublicKey": "<base64 X25519 coordinator public key>",
    "catalogExport": "PUBLIC"
}
```

For separate hosts, KK may use reachable private-network addresses. `velocityServer` maps the
stable waypoint server ID to a configured Velocity server. Each backend owns a unique private
key; the coordinator stores only its public key.

### Plaintext same-host configuration

Coordinator:

```json
{
    "enabled": true,
    "transportMode": "PLAINTEXT",
    "listen": "127.0.0.1:25580",
    "backends": {
        "survival": {
            "enabled": true,
            "velocityServer": "survival"
        }
    }
}
```

Backend:

```json
{
    "enabled": true,
    "transportMode": "PLAINTEXT",
    "serverId": "survival",
    "coordinator": "127.0.0.1:25580",
    "catalogExport": "PUBLIC"
}
```

Omitted `protocolVersion` is 1. Plaintext requires no suite, key files, pins, or pairing; reject
crypto configuration fields in that mode to avoid a false impression of authentication. An
operator chooses this trust model explicitly on both sides. Switching modes requires deliberate
configuration changes and new sessions; there is no downgrade or compatibility retry.

### KK pairing and revocation

1. Coordinator and backend each generate their own static X25519 key pair locally.
2. `/serverwaypoint pair <server-id>` creates a short-lived one-time pairing code bound to the ID.
3. A separately reviewed bootstrap exchange authenticates that code and binds both public keys
   and the server ID before installing either pin. The [step-7 bootstrap](cross-server-pairing-v1.md) uses one-time 256-bit codes and transcript-bound HMAC confirmations;
   KK cannot pair previously unknown keys by itself. Never use unauthenticated key exchange or
   plaintext mode as a remote pairing shortcut.
4. The backend stores the coordinator public pin; the coordinator stores the backend public key
   against its server ID and Velocity mapping. Private keys never leave their owning component.
5. Consume the code atomically. No pairing code or PSK remains in the final runtime configuration.
6. `/serverwaypoint revoke <server-id>` disables that registration, closes its sessions, and clears
   its pending handoffs. Rotate keys only through an authenticated administrative/pairing workflow.

In plaintext mode, disabling a configured backend and closing its sessions provides administrative
removal only; it cannot prevent a local process from claiming another enabled server ID.

## Catalog publication and synchronization

### Backend publication

1. Build an immutable snapshot from the authoritative waypoint managers without holding file locks
   during network encoding.
2. Apply the configured export policy before the snapshot enters the transport.
3. Assign a monotonic server catalog revision. Include list revisions so unchanged lists can be
   omitted from deltas.
4. Encode and send metadata followed by bounded snapshot chunks.
5. Publish later changes as deltas. If the coordinator detects a revision gap, request a complete
   snapshot.

An unavailable source is not an empty catalog. `UNAVAILABLE` preserves the last snapshot as stale;
an explicit, revisioned empty snapshot is required to remove all entries.

### Coordinator behavior

- Validate server identity against the KK-authenticated session, or the explicitly configured
  trusted-loopback registration in plaintext mode; preserve that security distinction.
- Maintain one latest immutable snapshot per server ID.
- Reject revisions older than the current snapshot.
- Mark a catalog stale when its backend disconnects; remove it only after configured expiry or an
  explicit administrative action.
- Fan out changed snapshots/deltas to connected source backends.
- Never rewrite waypoint identities or coordinates.

### Source backend and client cache

- Keep a bounded `RemoteCatalogStore` on each backend for command execution and suggestions.
- Send only authorized remote catalog state to modded clients through new chunked messages.
- Keep the client's remote store separate from `WaypointClientMod`'s active local manager and cache
  namespace.
- Store the server ID with every command suggestion and GUI selection.
- Never block Brigadier suggestions or rendering on coordinator I/O.

## Commands and GUI

Use a distinct command branch so local command identity and behavior remain unchanged:

```text
/wp remote servers
/wp remote list [server] [dimension] [list] [existing list options]
/wp remote tp <server> <dimension> <list> <waypoint>
```

Suggestions come exclusively from the bounded local remote-catalog cache. When a catalog is stale,
suggestions may remain available but execution displays the stale state and performs a fresh target
validation through the handoff flow.

The client waypoint manager adds a server selector above the existing dimension/list hierarchy:

```text
This server
Remote servers
  -> survival
     -> minecraft:overworld
        -> public
           -> spawn
```

Remote views are explicitly read-only:

- Hide or visibly disable add, edit, remove, and local visibility/rendering controls with a reason.
- Show teleport only when the client has received the corresponding authorization capability.
- Treat display names as presentation only; all commands and requests carry exact identity strings.
- Keep server identity visible in details and confirmation feedback.

Before changing GUI APIs or render behavior, follow `docs/gui-tips/README.md` if that document is
present at implementation time and update it for any changed API under the documented GUI package.

## Permission and authorization model

Add explicit permission keys:

```text
server_waypoint.command.remote.list
server_waypoint.command.remote.tp
```

Recommended defaults:

- Remote list: level 0.
- Remote teleport: level 2.

A remote teleport requires all of the following:

1. Source player has the existing teleport permission.
2. Source player has `server_waypoint.command.remote.tp`.
3. Source backend is admitted under the configured transport mode and authorized to request
   handoffs: KK requires cryptographic authentication; plaintext explicitly trusts the local host.
4. Destination exports the selected waypoint.
5. Destination waypoint still exists when the player arrives.
6. Destination player passes its final local teleport permission check.

The source must not assert arbitrary player permissions to the destination. The coordinator obtains
the UUID from the authenticated proxy player and verifies the player's current source backend.

Paper's current permission fallback uses `isOp()` when an explicit Bukkit node is absent. Require
explicit nodes for deployment guidance until that separate behavior is corrected; do not imply that
all configured vanilla levels can be represented by the fallback.

The first release's catalog export is server-policy-based, not player-specific. A later private
catalog mode requires an explicit `PlayerPermissionAuthority` abstraction backed by a trusted
network-wide permission provider. Do not add a UUID argument that bypasses this requirement.

## Handoff and teleport lifecycle

### Common preparation

1. Player executes a remote teleport from a command or GUI action.
2. Source resolves the exact `RemoteWaypointKey` from its cache and checks local permissions.
3. Source sends `PREPARE_HANDOFF` with player UUID, source server ID, destination server ID, waypoint
   identity, requested action, and observed catalog/list revision.
4. Coordinator verifies source and destination sessions, rate limits, server routing, and catalog
   policy.
5. Destination validates that the identity is currently exported and creates a pending reservation.
6. Coordinator creates a single-use pending handoff bound to player UUID, source server,
   destination server, waypoint identity, expiry, and request UUID.
7. Only after preparation succeeds does the selected transfer adapter switch the player.

Coordinates are not authoritative in the handoff record. The destination resolves coordinates from
its current local waypoint data after arrival. A removed or renamed waypoint fails safely.

### Velocity transfer

1. The proxy coordinator locates the authenticated proxy player by UUID.
2. It verifies that the player is still connected to the source backend.
3. The platform adapter requests a connection to the configured destination server.
4. On destination join, the backend claims the pending handoff from the coordinator.
5. The destination performs the final permission and waypoint checks, then teleports the player on
   the correct owning thread.
6. Destination reports completion; coordinator consumes the handoff and clears the reservation.

Proxy plugin messaging is not the coordinator catalog transport. If it is used for a local notification,
handlers must verify the source and consume the channel so a client cannot impersonate the proxy.

### Failure behavior

- Preparation failure: keep the player on the source and report the specific safe error.
- Transfer connection failure: let the handoff expire or cancel it if the proxy reports failure.
- Destination unavailable: reject preparation; do not transfer optimistically.
- Missing, expired, or already claimed handoff: leave the player at the normal destination spawn
  and report denial.
- Permission revoked after preparation: deny at destination.
- Waypoint removed or renamed: deny; never use stale cached coordinates.
- Waypoint moved: resolve and use the new authoritative coordinates if its exact identity and policy
  remain valid.
- Coordinator restart: pending handoffs may be discarded; catalogs republish after reconnect.

## Implementation steps

Complete these steps in order. Each step should be reviewable and testable independently; do not
start a player transfer until the destination has successfully prepared a handoff.

### Step 1: freeze the feature contract

Implemented contract: [Cross-server protocol v1](cross-server-protocol-v1.md), with constants,
validated identities, catalog/export enums, and identity tests in `common`'s `crossserver` package.
This step adds no runtime networking or command registration.

- Record cross-server protocol version 1 separately from the Minecraft custom-payload protocol.
- Freeze the stable server-ID rules, `RemoteWaypointKey`, command grammar, permission nodes, catalog
  status values, and first-release public export policy.
- Record the threat model and explicitly exclude arbitrary unmodified servers, RCON, remote editing,
  and player-private catalogs from version 1.

Deliverable: protocol constants and a short normative protocol document with no runtime networking.

Verification: tests reject invalid server IDs and prove that identical local waypoint identities on
different servers remain distinct.

### Step 2: select and prove the Noise dependency

Completed: the [selection decision](cross-server-noise-dependency-decision.md) selects
`org.signal.forks:noise-java:0.1.1` after a scoped KK source review and 35 passing checks in the
[isolated KK spike](../tools/noise-spike/kk/README.md). Historical NKpsk0 evidence is preserved
separately. Runtime dependency integration and final platform packaging/classloader evidence
belong to [step 3](cross-server-step3-validation.md).

- Evaluate maintained Java 17-compatible implementations for `KK`, AES-GCM, exact-suite vectors,
  licensing, dependency size, thread-safety, key lifecycle, and nonce-exhaustion behavior.
- Prove two-process encrypted exchanges, wrong coordinator pin, wrong backend key, unknown/revoked
  identity, transcript tampering, confirmation gating, reconnect, and clean shutdown.
- Verify standalone relocation and document all platform packaging routes. Carry the final
  artifact/classloader matrix into step 3 once the Velocity module exists.

Deliverable: isolated KK dependency spike and reviewed selection decision. Preserve historical
NKpsk0 results as such; do not relabel those tests as KK evidence.

Verification: exact KK vectors and two-process tests pass; all negative cases fail closed. Do not
advance past dependency selection without a suitable reviewed implementation. The plaintext option
does not waive this gate for the default encrypted transport.

### Step 3: add project modules and future-proof proxy interfaces

Completed: [module contracts](cross-server-proxy-module-contracts.md), 11 passing proxy
contract tests, and [platform validation](cross-server-step3-validation.md): 41 final artifacts
and 10 native runtime cases pass after upload-branch fixes and rebase.

- Add `proxy-common` and `velocity` Gradle subprojects.
- Define `CoordinatorTransport`, `BackendTransport`, `ProxyPlayerRouter`, `TransferAdapter`, and
  lifecycle interfaces without proxy API types in shared code.
- Define stable success/failure results and asynchronous operations without Velocity-specific
  exceptions, scheduler types, or connection objects.
- Add fake in-memory implementations and proxy-adapter contract tests.
- Complete the selected dependency's final relocation/shading and classloader checks for Paper,
  Fabric, Forge, NeoForge, and the new Velocity module before proceeding to production transport.

Deliverable: all new modules build while containing only lifecycle skeletons and interfaces.

Verification: the existing build remains unchanged functionally, and platform APIs do not leak into
`common` or `proxy-common` policy classes. The fake adapter can exercise registration, player lookup,
source-backend verification, permission checks, and transfer without Velocity classes.

### Step 4: implement cross-server identity and catalog models

- Add `RemoteServerId`, `RemoteWaypointKey`, `RemoteCatalogSnapshot`, and `RemoteCatalogState`.
- Preserve exact dimension, list, and waypoint identity strings; keep display names presentation-only.
- Make snapshots immutable and include server and list revisions.
- Define `AVAILABLE`, `STALE`, `UNAVAILABLE`, and `UNAUTHORIZED` without treating unavailable as
  empty.

Deliverable: domain types in `common`.

Verification: unit tests cover exact identity, server separation, defensive copies, revision
ordering, and unavailable-versus-empty behavior.

### Step 5: define canonical application messages and codecs

- Assign stable numeric IDs to registration, heartbeat, catalog, and handoff messages.
- Add request UUIDs or event sequences to every operation that needs correlation or replay defense.
- Reuse existing canonical waypoint codecs where their ownership semantics match.
- Add byte and object budgets before decoding variable-size fields.

Deliverable: round-trippable message types with no socket implementation.

Verification: deterministic codec tests cover every message, unknown type IDs, truncation, trailing
bytes, oversized strings/collections, and malformed waypoint data.

### Step 6: implement bounded TCP framing for both modes

Completed as reusable channels: [transport contract and verification](cross-server-tcp-transport-v1.md).
Platform lifecycle/reconnect remains step 8.

- Implement explicit mode matching, KK handshake/confirmation, encrypted records, bounded
  application reassembly, and separate inbound/outbound sequence state.
- Implement loopback-only plaintext framing with the same application budgets; reject remote or
  wildcard addresses, mode mismatch, and any attempt to fall back from KK to plaintext.
- Authenticate suite selection, protocol versions, server ID, and capabilities in the handshake
  transcript.
- Advertise only `NOISE_KK_25519_AESGCM_SHA256`; parse but do not advertise reserved future
  suites.
- Enforce handshake, frame, request, retained-byte, timeout, and connection limits.

Deliverable: reusable coordinator/backend channels with explicit transport mode, independent of
Minecraft lifecycle.

Verification: malformed frames, invalid tags, replay, duplicate IDs, oversized input, downgrade
attempts, slow handshakes, and disconnect storms remain bounded.

### Step 7: implement pairing and credential management

Completed as reusable administrative/bootstrap APIs: [pairing contract, review and tests](cross-server-pairing-v1.md).
Platform commands and the network bootstrap carrier are not started by this step.

- Generate the coordinator static key on first startup.
- Generate a unique backend static key pair locally and register only its public key.
- Prove the authenticated pairing bootstrap before installing pins; bind the code, ID, and keys.
- Plaintext configuration skips pairing and keys, but enforces the trusted-loopback restrictions.
- Implement one-time expiring pairing codes, pinned coordinator keys, revocation, and rotation.
- Store credentials outside ordinary config output and ensure logs never include secrets.

Deliverable: coordinator pair/revoke operations and backend credential storage.

Verification: pairing codes are single-use, revoking one backend does not affect others, file
permissions are restricted where supported, and secret-scanning tests cover logs and `toString()`.

### Step 8: implement connection lifecycle and server registration

Completed as reusable asynchronous agents: [lifecycle contract and verification](cross-server-connection-lifecycle.md).
Platform startup and catalog publication are not enabled by this step.

- Connect backend agents outbound to the coordinator with bounded exponential backoff.
- Register stable server ID, protocol version, and catalog capabilities after KK confirmation or
  explicit plaintext admission. Record the mode on each session and in administrative status.
- Add heartbeat, duplicate-ID rejection, graceful shutdown, reconnect, and connection metrics.
- Keep socket threads isolated from Minecraft state; dispatch platform operations through adapters.

Deliverable: backend presence with explicit KK-authenticated or trusted-loopback status.

Verification: duplicate server IDs fail, reconnect restores registration, coordinator restart is
recoverable, and no connection operation blocks the server tick thread.

### Step 9: publish authoritative backend catalogs

Completed as reusable publisher/receiver services: [publication contract and verification](cross-server-catalog-publication.md).
Coordinator-wide aggregation/fan-out remains Step 10.

- Snapshot waypoint data atomically from each backend.
- Apply the server-configured export policy before encoding.
- Publish metadata and a complete initial snapshot, followed by revisioned deltas.
- Request or send a full snapshot whenever either side detects a revision gap.
- Represent backend disconnect as stale/unavailable rather than an empty snapshot.

Deliverable: each connected backend publishes one validated catalog to the coordinator.

Verification: edits generate revisions, explicit empty catalogs remove entries, encoding occurs
outside mutation locks, and failed publication preserves the previous coordinator snapshot as stale.

### Step 10: aggregate and distribute catalogs

Completed as reusable index/distribution services: [distribution contract and verification](cross-server-catalog-distribution.md).
Platform startup remains a later integration step.

- Maintain one immutable latest snapshot per admitted server ID in the coordinator, retaining its transport mode.
- Reject old revisions and server-ID mismatches.
- Fan out changed metadata, snapshots, and deltas to connected backends.
- Bound global/per-server cache size and mark disconnected catalogs stale until expiry.

Deliverable: coordinator-wide read-only catalog index.

Verification: a change on backend A appears on backend B, duplicate local names do not collide, old
revisions cannot overwrite new state, and a disconnected backend is visibly stale.

This completes the first independently releasable boundary: secure catalog synchronization without
commands or teleporting.

### Step 11: add backend remote-catalog queries and suggestions

Completed in the shared backend command tree: [query contract and verification](cross-server-catalog-queries.md).
Transport startup remains a later integration step; step 12 adds permission gates.

- Add a bounded `RemoteCatalogStore` to each backend, separate from `WaypointFilesManagerCore`.
- Add a query adapter that reuses filtering/sorting presentation logic without granting mutation
  access.
- Register `/wp remote servers` and `/wp remote list`.
- Generate Brigadier suggestions only from the local remote cache.
- Add stale/unavailable feedback and localized command help.

Deliverable: vanilla clients can browse remote catalogs through commands.

Verification: suggestions cause no coordinator I/O, unavailable catalogs remain distinguishable
from empty catalogs, and local `/wp list` behavior is unchanged.

This completes the second independently releasable boundary: read-only remote catalog commands.

### Step 12: add permissions and authorization callbacks

Completed as backend permission gates and reusable callbacks: [authorization contract and verification](cross-server-authorization.md).

- Add `server_waypoint.command.remote.list` with default level 0.
- Add `server_waypoint.command.remote.tp` with default level 2.
- Require the existing teleport permission in addition to the remote teleport permission.
- Add destination export-policy and final player-permission callbacks.
- Document the current Paper `isOp()` fallback and recommend explicit nodes.

Deliverable: permission-gated remote commands and reusable source/destination authorization APIs.

Verification: explicit allow/deny assignments, fallback behavior, console behavior, and permission
revocation are covered on modded and Paper implementations.

### Step 13: implement the coordinator handoff state machine

Completed as a bounded atomic registry and reusable backend-message dispatcher:
[handoff contract and verification](cross-server-handoffs.md). Destination execution and platform dispatch remain steps 14–16.

- Implement prepare, reserve, claim, complete, cancel, and expiry transitions.
- Create single-use records bound to request UUID, player UUID, source server, destination server,
  exact waypoint identity, and expiration.
- Permit only one active handoff per player.
- Make complete and cancel operations idempotent and claims atomic.
- Add bounded audit logs without secret values.

Deliverable: platform-neutral handoff registry and backend request handlers.

Verification: in-memory source/coordinator/destination tests cover success, expiry, replay,
cross-player claims, cross-destination claims, concurrent claims, and coordinator restart.

### Step 14: implement destination preparation and arrival

- Resolve the requested identity from current authoritative destination data during preparation.
- Reserve the handoff without trusting cached coordinates from the source.
- On arrival, bind the claim to the authenticated player's UUID.
- Recheck permission, export policy, and exact waypoint identity.
- Resolve current coordinates and teleport through the platform's owning-thread scheduler.
- Report completion or a specific safe rejection to the coordinator.

Deliverable: destination-side prepare/claim/teleport service shared through platform adapters.

Verification: removal and renaming deny safely, movement uses current coordinates, revoked
permission denies, and duplicate arrival cannot teleport twice.

### Step 15: add the remote teleport command

- Register `/wp remote tp <server> <dimension> <list> <waypoint>`.
- Resolve exact identities from the cache, check source permissions, and send `PREPARE_HANDOFF`.
- Keep the player on the source until destination preparation succeeds.
- Return precise feedback for unavailable destination, stale catalog, permission denial, missing
  waypoint, busy player, timeout, and unsupported transfer method.

Deliverable: transfer-independent command initiation.

Verification: command execution reaches a fake transfer adapter only after successful preparation;
every rejected preparation leaves the player on the source.

### Step 16: implement the Velocity adapter

- Host the coordinator in a Velocity plugin and map stable IDs to registered proxy servers.
- Obtain the player UUID from Velocity and confirm the player is still connected to the claimed
  source backend.
- Request the asynchronous destination connection only after a prepared handoff.
- Apply proxy permissions if configured.
- Consume and source-check any plugin messages owned by this feature.

Deliverable: end-to-end Velocity transfer between two compatible backends.

Verification: successful transfer teleports exactly once; spoofed client messages, wrong source
backend, failed connection, disconnect, and destination rejection do not teleport.

This completes the first end-to-end teleport release candidate.

### Step 17: synchronize remote catalogs to modded clients

- Add remote catalog/status messages to the existing Minecraft chunked custom-payload layer.
- Increment `ProtocolVersion.PROTOCOL_VERSION` when registering the new messages.
- Filter unauthorized servers before sending data to a client.
- Store remote state separately from `WaypointClientMod`'s active local manager and local cache.
- Preserve server identity in every client selection and action.

Deliverable: compatible clients receive bounded authorized remote catalog state.

Verification: protocol mismatch degrades to commands, reconnect refreshes the cache, and remote data
cannot overwrite or delete local client waypoint state.

### Step 18: integrate the waypoint manager GUI

- Read the GUI documentation required by `AGENTS.md` before changing render or API behavior.
- Add local/remote server selection above the dimension/list hierarchy.
- Add read-only remote details and stale/unavailable indicators.
- Hide or visibly disable edit, remove, add, and local rendering controls with a reason.
- Add remote teleport confirmation and progress/failure feedback.
- Update GUI documentation for every changed API in the documented GUI package.

Deliverable: complete read-only remote browsing and teleport initiation in the client GUI.

Verification: local GUI behavior is unchanged, remote entries cannot mutate/render local state, and
exact server identity survives selection, sorting, filtering, and action execution.

### Step 19: harden, document, and release

- Add protocol known-answer tests, codec tests, framing fuzz/property tests, and handoff concurrency
  tests.
- Add rate-limit, retained-byte, slow-peer, reconnect, and credential-revocation tests.
- Run the complete version and integration matrix below.
- Document both transport modes, plaintext local-process trust, binding/firewall rules, KK pairing,
  rotation, revocation, proxy server mappings,
  permissions, recovery, and troubleshooting.
- Add release notes explaining that the feature is opt-in and disabled by default.

Deliverable: release-ready artifacts and administrator documentation.

Verification: all supported builds and integration scenarios pass, security failures are bounded
and documented, and a new administrator can pair two servers without manually creating certificates.

## Validation matrix

### Unit and component tests

- Identity equality preserves exact arbitrary identifiers and server separation.
- Catalog revisions, deltas, explicit deletion, stale state, and snapshot atomicity.
- Codec round trips and malformed/oversized input rejection.
- Noise known-answer tests supplied by the selected implementation.
- Wrong coordinator/backend key, unknown/revoked ID, replay, nonce exhaustion, transcript tampering,
  and authenticated downgrade rejection for KK.
- No application operations before backend transport confirmation.
- Plaintext requires explicit matching modes, no keys, and loopback socket endpoints; reject
  wildcard/non-loopback addresses and prove KK failure never triggers plaintext fallback.
- Handoff prepare/claim races, timeout, cancellation, disconnect, and idempotency.
- Permission denied at source and destination.
- No secret, credential, or pairing code appears in configuration `toString()`, exceptions, or logs.

### Version-qualified builds

- `common` tests.
- Mods: each loader's oldest supported target plus representative latest targets.
- Paper: every currently supported Paper Stonecutter target.
- Velocity adapter build and tests.
- Proxy-neutral adapter contract tests that a future proxy implementation must pass.
- `git diff --check`.

Run Stonecutter generation/build sessions serially when generated-source races are possible and use
the repository's version-specific Gradle tasks rather than assuming the active project covers the
matrix.

### Integration scenarios

- Velocity plus two heterogeneous backends.
- Catalog update while a player is browsing.
- Destination disconnect before preparation, during transfer, and after arrival.
- Waypoint edit/removal between selection and arrival.
- Permission revocation between preparation and arrival.
- Coordinator restart and backend republish.
- Revoked KK backend key cannot register or claim handoffs; a valid compromised key remains a
  compromised identity until revoked.
- Explicit same-host plaintext catalog/handoff flow, including mode and endpoint rejection; this
  does not prove resistance to impersonation by local processes.

## Release boundaries

- Steps 1-10: internal secure catalog foundation; no player-facing commands.
- Step 11: independently releasable read-only remote catalog commands.
- Steps 12-16: first end-to-end teleport release candidate using Velocity.
- Steps 17-18: compatible-client catalog and GUI support.
- Step 19: release hardening and administrator documentation.

Do not release partial remote teleporting that transfers a player before destination preparation is
confirmed. The read-only catalog can ship independently, but `/wp remote tp` must remain unavailable
until the complete prepare-transfer-claim-validate flow and at least one transfer adapter are ready.

## Decisions to preserve during implementation

- The destination server owns waypoint truth and performs the final teleport.
- Remote catalogs remain separate and read-only.
- Suggestions and GUI rendering never wait for network I/O.
- Server, dimension, list, and waypoint identities remain exact; display names are presentation.
- A missing catalog is not an empty catalog.
- Coordinates are never trusted from a stale source cache or handoff record.
- Player transfer is replaceable; catalog and authorization semantics are shared.
- Cryptographic negotiation is authenticated and cannot silently downgrade.
- KK backend key registrations are independently revocable; private keys stay with their owners.
- Plaintext is explicit and loopback-only, never an automatic fallback or authenticated identity.
- Post-quantum support is an explicit future suite, not unfinished cryptography inside v1.

## References

- [Velocity plugin messaging](https://docs.papermc.io/velocity/dev/plugin-messaging/) documents the
  required source checks and the need to consume messages that clients must not be allowed to spoof.
- [Velocity backend security](https://docs.papermc.io/velocity/security/) documents firewalling and
  authenticated player-forwarding considerations for proxy networks.
