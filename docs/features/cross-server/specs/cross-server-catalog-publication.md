# Backend catalog publication (step 9)

Step 9 connects authoritative backend snapshots to one validated coordinator publication per
backend. It adds shared capture/selection/revision/delta/publication code under
`common/.../crossserver/catalog` and initially a coordinator-only `CatalogReceiver` under `proxy-common`.
[Step 10](cross-server-catalog-distribution.md) moves the receiver to shared `common` scope and
adds coordinator-wide indexing, fan-out, expiry and global/per-server cache policy.
No platform lifecycle, game command or client GUI has been enabled.

## Atomic source capture and export

`WaypointFilesManagerCore.snapshotWaypointData(maximumObjects)` captures detached data across all
dimensions under the existing mutation-admission and lifecycle write locks. It checks a configured
ceiling of at most 65,536 dimension/list/waypoint objects before deep copying. File-manager state
locks protect the copies. No JSON encoding, persistence, socket I/O, selection predicate or
external callback runs under these model locks. Calls from a mutation/read scope or mutation
callback reject instead of attempting a deadlocking lock upgrade.

A missing authoritative directory is unavailable, not an empty catalog. An initialized manager
whose authoritative map is empty is a valid explicit empty source. The complete capture is detached
from later changes; conversion and canonical encoding occur after the model locks are released.
Large local models that exceed the capture count limit fail publication safely; the capture budget
counts the complete source, including data that selection later excludes. Strings remain immutable
values and keyword lists are detached; detailed byte/object budgets apply during canonical encoding.

`CatalogSource.fromManager` converts that detached capture into immutable remote-model values.
`CatalogSelection` is an immutable, server-configured, player-independent PUBLIC selection: either
all lists or exact dimension/list names. An empty selection explicitly exports an empty catalog.
Selection happens before any wire encoding. List/waypoint identities, display labels, coordinates,
colors, yaw, global flag, keywords and description retain their distinct fields. Nothing writes a
remote catalog into a local waypoint manager, normalizes names or authorizes teleportation.

A custom `CatalogSource` must return complete immutable export data or throw on failure. Returning
an empty map means authoritative deletion, so it must never be used as an error fallback.

## Durable catalog and list revisions

`CatalogRevisionSequence` stores a nonnegative high-water mark in a separate catalog-state directory.
It reuses the bounded, exclusive-owner, atomic-file/fsync mechanics of `CredentialFiles`; that
helper does not generate keys. Catalog state is required in either transport mode and is separate
from optional pairing credentials. The owner keeps the sequence open until its backend agent stops.
The production publisher receives `sequence::next`; test-only in-memory suppliers implement the
same strictly increasing contract.

A publisher reserves a positive durable revision whenever exported content changes. The first
capture after a publisher/process restart reserves a new revision, so old coordinator snapshots
cannot make the new session restart at zero. The high-water mark must be retained across restarts;
restoring/deleting it is an administrative recovery decision, not an automatic reset path. Failed
encoding can leave unused revision numbers. Exhaustion, malformed state and uncertain writes fail
closed; values never wrap or move backwards within a publisher.

Changed/new lists receive the new catalog revision; unchanged list content retains its previous
list revision. Catalog/list revisions are independent of local `int` synchronization counters,
which can change across reload or reset. Exact list/waypoint renames and removals count as content
changes. A fresh publisher assigns fresh list revisions in its initial complete snapshot.

## Full publication, deltas and resynchronization

A `BackendAgent` can be configured with a `CatalogPublisher`. It creates one separate bounded
publication worker after start. Following successful registration, and on every reconnect, that
worker captures and publishes a full catalog. Heartbeat work remains on its own worker, so waiting
for a model snapshot or revision-file write does not occupy the heartbeat executor. Channel writes
still serialize and retain their existing deadlines and record/sequence limits.

The first publication sends CATALOG_METADATA followed by CATALOG_SNAPSHOT chunks, with one request
UUID shared across metadata and all chunks, and a fresh snapshot UUID. Each chunk fits both the
configured chunk budget and application-frame budget; transport fragmentation still respects the
Noise record maximum. The publisher retains one latest validated snapshot and one sent baseline,
not an unbounded update queue. Polls coalesce edits into the latest complete state.

Later changes use CATALOG_DELTA with the exact last-sent base revision, whole changed-list
replacements, removed lists and removed dimensions. Empty dimensions remain distinct from removed
dimensions. The complete resulting catalog is encoded/validated before any delta is sent, so many
small deltas cannot grow a catalog beyond its full-snapshot limit. If the delta itself exceeds a
frame/object budget while the complete catalog remains valid, send a bounded full snapshot instead.
Unchanged content sends nothing.

A receiver without a matching available baseline marks its previous snapshot stale and replies
with ERROR(STALE_CATALOG), correlated to the delta request UUID. The backend reader requests a full
snapshot from the publication worker without blocking on capture or encoding. The next publication
sends metadata and a complete latest snapshot, even if no further edit occurred. Reconnect and
recovery from a source failure also force full snapshots. No new message IDs, compatibility syntax,
transport fallback or Minecraft custom-payload protocol changes were added.

## Coordinator validation and stale state

`CatalogReceiver` is owned by one admitted session generation. It validates the source ID on every
message, requires matching metadata/request/revision for full chunks, and publishes only the
transport's completed catalog. Metadata or partial chunks never replace the previous snapshot.
A full snapshot may advance over revisions, or refresh an equal revision only when the catalog
content is identical. Conflicting/regressing list revisions reject. List revisions cannot exceed
the catalog revision.

Deltas require the exact available base revision. Removal targets must exist, replacement list
revisions must advance, and the complete candidate must satisfy the receiver's catalog/object
budgets before atomic replacement. Malformed, old, oversized or conflicting data closes the
session and preserves its last valid snapshot as stale. A base gap requests resynchronization
instead. Generation checks prevent old-session completion/cleanup from changing a replacement
session's publication.

Source capture/revision/encoding failure sends CATALOG_INVALIDATE(UNAVAILABLE) once per failure
transition/session. It does not send an empty snapshot. The coordinator keeps its last validated
snapshot STALE, or reports UNAVAILABLE if none exists. Closing a backend socket immediately makes
that retained snapshot visibly stale, even before asynchronous worker cleanup finishes. An explicit
successful empty snapshot/delta is the only way to delete all entries. Reconnection requires a
complete snapshot before stale data becomes available again.

`CoordinatorAgent.catalogs()` exposes detached immutable views with snapshot, state, display name
and actual transport mode. The original Step 9 retained at most `TcpLimits.connections()` backend catalog slots,
each limited by its channel's full catalog and codec budgets. Disconnected slots remain retained;
if the slot ceiling is reached, new IDs are rejected before a successful registration reply.
Stopping the coordinator clears these transient views and backends republish after restart.
Step 10 supersedes this slot policy with explicit bounded index limits and expiry, and distributes
other backends' catalogs. See the distribution contract for current behavior.

## Verification

On 2026-09-08, common/proxy tests pass: 444 common and 61 proxy tests, including 13 new publication
cases. Coverage includes real KK and plaintext publication, authoritative waypoint edits,
unchanged list revisions, exact export filtering, detached captures, callback/budget rejection,
persistent revision restart/exhaustion, whole-list delta semantics, explicit empty catalogs,
unavailable source preservation, reconnect, a deliberately missed delta followed by correlated
full resynchronization, multi-chunk fallback for oversized deltas, encoding/cumulative-size failure,
conflicting full/list revisions and stale-generation cleanup.

A blocked revision-persistence fixture allows a real model mutation to finish while publication is
paused, proving the post-capture persistence/encoding path holds no mutation lock. Existing model
concurrency tests also pass. Two existing asynchronous-cleanup assertions were corrected to wait
for both socket/presence closure and subsequent cleanup/accounting; they no longer assume those
observations become visible at the same instant.

The Velocity artifact builds with the new publication classes. Full backend artifact/native game
validation was not repeated. These are reusable service tests; platform lifecycle wiring,
bootstrap dispatch, command/GUI behavior and transfer remain later work. Catalog fan-out is now Step 10.
