# Coordinator handoff state machine (step 13)

Step 13 implements `HandoffRegistry` and `HandoffRequestHandler` in `proxy-common`. They are
platform-neutral coordinator policy APIs for backend requests and destination replies. They do not
start TCP dispatch, resolve destination waypoints, register remote teleport commands, or switch
players. Destination execution is step 14; command and Velocity integration are steps 15–16.

## Ownership and admission

Only the transport/lifecycle owner may construct `HandoffPeer`: an admitted server ID, unique
session UUID and actual transport mode. A reconnect must receive a fresh session UUID. Do not derive
this context from request fields or a presence snapshot. KK registration authenticates the backend;
plaintext registration trusts local processes and is not cryptographically authenticated. All
handoff checks below apply to both modes.

`HandoffRequestHandler` requires three nonblocking local callbacks:

- Current registered session for an exact server ID.
- Proxy-authenticated player snapshot, such as `ProxyPlayerRouter::findPlayer`. The returned UUID
  must match the queried UUID and its current server must match the operation's expected route.
- Admission decision for a prepare request: configured source handoff permission, source/destination
  route mappings, coordinator catalog policy, and any deployment-specific request rate policy.

There are no permissive default callbacks. Initial preparation checks the current source session,
source/player identity, source route, admission and destination availability. Destination confirmation
rechecks source session, proxy source route and admission before notifying the source. Claim checks
current destination session, retained source session and proxy arrival at the destination. These
checks do not replace source player permissions, destination PUBLIC export validation or the final
arrived-player permission check from step 12.

The owner must serialize dispatch with disconnect/revocation, call `disconnect(oldPeer)`, and deliver
returned messages only to the exact still-live peer. A stale callback/delivery must never be rebound
to a replacement connection with the same server ID. `Delivery` carries the session and original
request UUID explicitly. No callback or delivery performs a player switch.

## State and message flow

| Current state | Accepted operation | New state / output |
| --- | --- | --- |
| Absent | Source `PrepareHandoff` after admission | PREPARING; forward exact request to destination |
| PREPARING | Destination `HandoffPrepared` with matching binding | PREPARED; forward accepted binding to source |
| PREPARING | Destination `HandoffRejected` | REJECTED; forward rejection to source |
| PREPARED | Destination `ClaimHandoff`, after proxy arrival | CLAIMED; return binding once |
| CLAIMED | Destination `CompleteHandoff` with success or failure | COMPLETED; notify source once |
| PREPARED | Source or destination `CancelHandoff` | CANCELLED; notify counterpart once |
| CLAIMED | Destination `CancelHandoff` | CANCELLED; notify source once |
| Any active state | Deadline reached | EXPIRED |
| Any active state | Either bound session disconnects/revokes | CANCELLED / UNAVAILABLE |

PREPARING already occupies the player's single active slot. The coordinator retains the prepare
UUID, player UUID, both exact sessions, source/destination IDs, exact waypoint tuple, action,
observed catalog/list revisions and deadline. There are no coordinates in the record.

With the existing v1 messages, the destination allocates the reservation/handoff UUID in its
`HandoffPrepared` response. The coordinator rejects collisions with retained handoff IDs and any
changed player, source, target or action. It caps the proposed expiration at the coordinator's
original deadline. This permits a destination to reply after network delay without extending the
handoff. A destination may shorten expiration. Source `HandoffPrepared` and destination
`HandoffClaimed` contain the accepted, capped binding. Step 14 must treat that expiry as an upper
bound on its local reservation, never extend a reservation, and resolve current local coordinates.
No wire fields, message IDs or protocol versions change.

Denied prepares use `HandoffRejected`; other denied phases use `Error`, which also closes a
pending claim in the existing TCP accounting.

Every phase preserves the original prepare request UUID. Claim and completion must also match the
handoff UUID, player UUID and destination ID. Claims are atomic and never idempotently grant a second
claim. Completion requires CLAIMED. Repeated completion with identical result succeeds without
sending another notification; conflicting results fail. Repeated cancellation with identical reason
also succeeds without another notification; it cannot turn completed work into cancellation. After
claim, source cancellation is denied because destination work now owns completion. A destination
may report a failed completion without converting it to a successful teleport.

## Time, retention and resource limits

Defaults in `HandoffLimits`:

| Budget | Default |
| --- | --- |
| Active handoffs, globally | 256 |
| Retained active plus terminal records | 4096 |
| Retained records per source server ID, across sessions | 64 |
| Estimated retained record bytes | 16 MiB |
| Handoff expiry from initial prepare | 15 seconds maximum |
| Terminal-record retention | 60 seconds |
| Audit entries | 256 |

The retained-byte charge includes a fixed 2048-byte metadata allowance plus two bytes per UTF-16
unit of the exact dimension/list/waypoint identities. Immutable request values are retained without
copies of catalog contents. Admission checks budgets before storing records. Terminal records count
against both global and per-source budgets. Capacity failure returns BUSY; it never evicts an active
record or a retained replay record to admit more work.

Expiry uses a monotonic clock, including preparation and claimed states. The wire deadline is an
epoch timestamp but a wall-clock rollback cannot extend the original monotonic deadline. Every
registry access enforces expiry; a periodic `maintain()` is not needed for claim safety. Maintenance
can emit best-effort expiry notices to both peers. An earlier operation may already have observed
expiry, so backends must enforce their own deadlines rather than rely on receiving a notice.

Terminal records retain identity and results for bounded replay protection and completion/cancel
idempotence. Once that retention expires, old claims/completions fail NOT_FOUND. Request UUIDs are
not a durable forever-replay ledger: callers must generate fresh IDs, and the TCP session layer
independently rejects duplicate request/type frames. Exact wire duplicates may therefore close a
session before reaching the idempotent semantic API. A duplicate phase under another request UUID
cannot consume an existing handoff. Restart creates an empty registry; old claims never recover
reservations. `close()` disables admission and clears all record/player/handoff indexes.

The bounded audit ring stores only a sequence, event enum and result enum. It omits player, request,
handoff and session UUIDs; waypoint text; key material; and exception messages. Callback failures
produce only UNAVAILABLE. Audit snapshots and operation snapshots are immutable.

## Integration boundary

The request handler is a reusable dispatcher for messages received from backends, not a backend
game service. Existing catalog-only agents remain unchanged. The future owner must route handoff
messages separately from catalog traffic, run maintenance and disconnect callbacks, and deliver
returned messages with fresh transport sequences. Step 14 supplies destination reservation/arrival
handling; step 16 supplies the real proxy callbacks and transport/platform lifecycle wiring.

An accepted claim is necessary but insufficient to teleport. The destination must validate its
local reservation and actual arrived player, recheck permissions/export, and resolve/teleport on
the owning thread. The proxy must recheck source route and deadline immediately before transferring.
This step neither enables optimistic transfers nor claims live end-to-end teleport validation.

## Verification

On 2026-09-08, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passes 460 common tests and 88 proxy tests with no failures/errors/skips. The 21 new in-memory cases
exercise canonical message round trips in both modes, destination confirmation, proxy identity and
route checks, admission revocation, wrong request/player/destination/session/target, replay,
idempotent terminal operations, failure completion, cancellation ownership, concurrent preparation,
32 concurrent claims, a claim/cancel race, monotonic expiry in all active states, delayed/capped
confirmation, unavailable callbacks, resource/audit limits, disconnect/reconnect and coordinator
restart. Exactly one concurrent claim is accepted.

The Velocity artifact builds with the new Java 17 proxy classes. No native Minecraft/Velocity
instances or full backend artifact matrix were run. The game-owning thread, destination-local
reservation/teleport and live TCP handoff dispatcher remain later integration gates.
