# Step 17: remote catalogs on modded clients

Step 17 adds authorized remote cache synchronization. [Step 18](cross-server-gui.md) adds GUI selection and actions.
Remote catalogs never enter local waypoint codecs, managers, files, renderers or map-mod exports.

## Protocol and lifecycle

The planned 4.0.0 public release uses Minecraft custom-payload protocol **1**; released 3.0.4 uses **0**. Development snapshots previously used **11**. The backend TCP application protocol remains **1**.
The shared chunked registry adds clientbound `REMOTE_CATALOG` (6) and serverbound
`REMOTE_CATALOG_REQUEST` (7). Existing Fabric, Forge, NeoForge and Paper chunk payloads carry them;
no extra platform payload registration is needed. Forge/NeoForge registration versions already
read `ProtocolVersion`, and the common handshake gates all chunked traffic on exact equality.
Mismatched clients receive the existing incompatibility feedback and can use ordinary commands.

A compatible dedicated-server handshake enables client polling on the next transport tick, then
at most once every five seconds. Requests contain a fresh UUID. Only one request is outstanding;
after fifteen seconds without a matching response the remote cache is cleared and polling retries.
Join, leave and every server handshake reset the remote cache and correlation token, including
proxy transfers which do not deliver a normal disconnect. Replies for an old request or session
cannot install data. Integrated-server local sharing does not start remote polling.

Each response atomically replaces the full remote cache. It contains the request UUID, overall
catalog state and a map keyed by exact stable server ID. Each view has display name, availability,
transport mode and optional canonical `ApplicationCodec` catalog bytes. Catalog/list revisions and
exact dimension/list/waypoint identifiers are retained. Receipt timestamps are assigned locally.
An explicitly empty AVAILABLE map is distinct from UNAVAILABLE and UNAUTHORIZED.

## Authorization and limits

The shared backend handler checks the actual player's `remoteList` permission on the existing
platform-owned receive path for every admitted request. Global denial sends an empty UNAUTHORIZED
replacement. Individually UNAUTHORIZED server entries are omitted entirely, including their names
and retained data. Current v1 exports are PUBLIC; no new per-player export policy is introduced.
Revocation and catalog/status changes are reflected on the next successful poll. No background
worker looks up players or evaluates permissions.

Requests are exactly 16 bytes and use a dedicated serverbound receive limit. Request admission
permits one per player per four seconds, retains at most 4096 players, and clears on disconnect,
handshake and server reset. Responses allow at most 256 servers and 8 MiB of encoded data. Each
nested catalog retains its v1 1-MiB wire and 8-MiB allocation/65,536-object limits; all nested
catalogs also share the outer transport's 64-MiB allocation and 1,000,000-object budget. Existing
chunk pacing, retained-byte limits, checksums, timeouts and capability checks remain in force.

The backend preflights serialization and decoding against these bounds. An over-budget snapshot
sends an empty UNAVAILABLE replacement, never a partial apparently complete catalog. Stale views
can retain their read-only snapshots; unavailable views cannot contain data. Remote data is only
available through `WaypointClientMod.remoteCatalogs()`, a session-only store with immutable views.
`RemoteClientCatalogs.session()` exposes a monotonically changing session generation, incremented
by `clear()`. GUI confirmations bind to it so reconnecting with identical catalog contents cannot
reuse a confirmation from the previous server/world session.

## Verification

Automated coverage exercises exact identities, stale/unavailable/empty states, denied identity
filtering, global permission denial, request throttling, malformed/truncated input, allocation
limits, protocol mismatch, request timeout and reconnect/late-response rejection. A Fabric test
constructs the actual `WaypointClientMod`, sends a multi-frame catalog through `onMessageChunk`,
and checks atomic installation, unchanged local manager identity/revision and identical saved
local files. The incompatible-state dispatcher rejects remote frames.

Validated targets: common/proxy/Velocity tests and build; Fabric 26.1.2 tests; Paper 1.21 tests/build
and Paper 26.2 compile; Fabric 1.20.1, NeoForge 1.21.2 and Forge 26.1.2 compilation. No active
Stonecutter version changed. These are automated JVM/dispatcher checks, not a newly booted game
session. Step 16's native logs predate protocol 11 and do not validate these client changes.
Native protocol-11 proxy reconnect/permission-revocation sessions and the complete release matrix
remain explicit Step 19 release gates.
