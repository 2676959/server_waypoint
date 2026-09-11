# Remote teleport initiation (step 15)

Step 15 registers `/wp remote tp <server> <dimension> <list> <waypoint>` in the shared command
root and adds `SourceHandoffService` in `common`, shared by mod and Paper backends. It initiates
preparation and delegates the server switch through an adapter. Live Velocity integration,
coordinator TCP dispatch and platform lifecycle startup remain Step 16.

## Command and permission behavior

Each argument is an exact identity, independent of display labels. Brigadier quoted strings retain
spaces, quotes, backslashes and empty names. Suggestions read one bounded `RemoteCatalogStore`
snapshot, including waypoint names; they never query a server, resolve a world or contact the
coordinator. Stale names may remain suggested, but execution rejects stale catalogs and requires
an available cached target. Missing dimensions/lists/waypoints return NOT_FOUND. Missing servers
or unavailable catalogs return UNAVAILABLE; unauthorized views return UNAUTHORIZED.

Source initiation requires the actual player's local `tp` and `remote.tp` permissions. Browsing
permission is independent. The remote root/help topic is visible with either permission and shows
only allowed operations. Execution and dynamic suggestions recheck permission after parsing.
Console sources cannot initiate teleportation. Existing local list behavior, remote browsing and
pagination actions remain unchanged. Help and feedback are translated in all six server locales.

The command sends only the exact target and observed catalog/list revisions to the initiator.
`SourceHandoffService` obtains the player UUID on the source owner, supplies its configured source
server ID, generates a fresh request UUID and enqueues `PrepareHandoff`. Cached coordinates are
never transmitted or used to teleport. Revision checks and authoritative destination resolution
remain coordinator/destination responsibilities.

Feedback distinguishes unavailable, unauthorized, missing target, stale catalog, busy player,
timeout, unsupported transfer, source/destination mismatch, replay, cancellation, malformed reply,
transfer failure and internal error. The initial preparing message is not success. SUCCESS from the
source adapter means server switch completion only; destination arrival validation and teleport
have their own outcome and must not be inferred from it.

## Source service and adapter contract

Create one service per admitted coordinator connection and install it using
`CoreWaypointCommand.setRemoteTeleportInitiator`. The default initiator reports UNSUPPORTED for
an otherwise valid cached selection. The lifecycle owner must close the old service before replacing
it, including reconnects to the same coordinator. There is no migration of pending requests.

`SourceHandoffService.Platform` supplies source-owner scheduling, current player/UUID validation
and fresh local/remote teleport permission checks. Initiation runs on that owner. Network futures
queue work back to the owner before reading any player state or initiating transfer. Delayed owner
work rechecks player identity, presence, permissions and deadline. Rejected/retired scheduling clears
the pending slot; feedback is delivered at most once and only to the same current source player.
The Step-16 implementation must use the entity owner on Folia, not a generic server scheduler.

`SourceHandoffService.Link` must be nonblocking and scoped to one exact admitted connection:

- `prepare` correlates the reply to the supplied fresh request UUID on that connection.
- `transfer` is invoked only after a matching `HandoffPrepared`. Its binding must preserve the
  player, source, exact target and action. The proxy adapter must recheck coordinator reservation,
  proxy-authenticated UUID, current source route and expiry immediately before switching.
- `cancel` enqueues best-effort reservation cancellation on that same connection.

The lifecycle/transport dispatcher must deliver unsolicited cancellation/error messages through
`receive`, call `maintain` periodically, and call `close` on disconnect/revocation/shutdown.
Cancellation before queued transfer initiation prevents the switch. Once the adapter has started
an asynchronous switch, cancellation/timeout cannot undo that external operation; the proxy's own
checks and destination claim/arrival rules still apply. A timed-out or disconnected preparation
with no known handoff ID relies on the coordinator/destination deadlines for cleanup. Late replies
never restart an operation. Failed replies and exceptions never trigger an optimistic switch.

There is at most one pending operation per player, 64 per source service, and 1 MiB of estimated
retained entry bytes (2048 bytes plus twice the UTF-16 identity lengths per entry). Admission does
not evict live entries. Every operation has a 15-second monotonic deadline from preparation;
a shorter destination expiry caps it further, and wall-clock rollback cannot extend it. Maintenance
also bounds an unresolved transfer future. Terminal entries release the busy slot and byte budget;
request replay retention belongs to the coordinator and TCP layers. The link must bound its own
pending futures and release them on connection closure.

No application fields, codecs, numeric message IDs or protocol versions change. KK session
admission authenticates the backend; plaintext remains explicitly trusted loopback and does not
provide cryptographic authentication. Source and destination player checks apply in both modes.

## Verification

On 2026-09-08, this command passed:

```sh
./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain
```

520 common tests and 90 proxy tests passed, with no failures, errors or skips. The 36 new common
cases cover command-to-fake-transfer ordering, exact cached identities/revisions, permission and
cache state changes, every rejected preparation result, current-player replacement, owner queue
delay/retirement, monotonic and destination expiry, malformed/late replies, cancellation,
disconnect, busy slots and asynchronous failures. The two existing coordinator/destination exchange
cases now initiate through the source service and a fake `TransferAdapter`, assert PREPARED before
switching, then claim and teleport against fresh destination coordinates in both transport modes.
These exchanges are in-memory policy tests, not live transport/platform evidence.

All 17 new feedback/help keys and their placeholders were checked across six server locales.
The final Velocity JAR contains the source service with Java 17 bytecode. Tracked and new-file
whitespace checks pass. No Stonecutter source branch or platform adapter was edited, and no active
version was switched. Native Minecraft/Velocity sessions and the full backend release artifact
matrix were not run; Step 16 must supply and validate real lifecycle, transport and transfer wiring.

## Step-16 integration

The services now have live coordinator/backend lifecycle and Velocity transfer wiring. See
[the runtime contract and current validation](cross-server-velocity-runtime.md). Earlier step-specific
verification above describes its historical boundary; client/GUI and full release hardening remain.
