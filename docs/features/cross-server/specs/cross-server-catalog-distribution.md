# Catalog aggregation and distribution (step 10)

Step 10 adds the coordinator-wide read-only catalog index and distributes validated publications
to connected backends. These are reusable services: platform startup, permissions, GUI behavior
and transfers remain later steps. [Step 11](cross-server-catalog-queries.md) now adds remote queries/commands.

## Ownership and identity

`CatalogIndex` and `CatalogReceiver` live in `common/.../crossserver/catalog` because both the
coordinator and backend replicas use their validation, admission and expiry logic.
`CatalogDistributor` remains in `proxy-common`, its only consumer. A coordinator index accepts
updates only from the session admitted for the exact source server ID. Immutable view maps and
snapshots never expose mutation of the owner. Replacement sessions invalidate old writers and
cleanup callbacks. The coordinator retains the source's actual `TransportMode`, including
PLAINTEXT; a plaintext source is never described as authenticated.

Each backend owns a separate bounded replica, exposed through `BackendAgent.remoteCatalogs()`.
It receives catalogs only from its coordinator connection and rejects echoes of its own ID.
No remote entry is installed in `WaypointFilesManagerCore`. Equal dimension, list or waypoint names
on different servers remain separate under their exact `RemoteServerId` keys. Step 11 adds
query adapters and commands on top of a read-only RemoteCatalogStore facade for these replicas.

The existing metadata wire schema does not carry the source link's transport mode. Consequently
backend replica views have a null/unknown source mode, even over a KK coordinator connection.
The coordinator index is the source of actual link-mode diagnostics. No message IDs, wire fields,
Minecraft payload version, fallback modes or compatibility syntax changed.

## Distribution and recovery

After registration the coordinator starts one coalescing scheduled distribution task for each
peer. A bounded pool of at most four workers is separate from heartbeat and reader workers.
Changes are observed at the configured heartbeat interval. Initial synchronization and reconnect
send metadata and chunked full snapshots for other backends. A source with no validated catalog
yet is not advertised as an empty publication.

The index can retain the latest accepted source delta as an optional optimization. A recipient
whose last-sent available revision matches its base receives that delta. Otherwise it receives
a full latest snapshot. Each authoritative installation discards optional delta history before
budget admission; there is no change queue. Busy sources therefore coalesce naturally. A delta
that exceeds a recipient's frame limit falls back to bounded full chunks. Full content must fit
the recipient's codec limits; incompatible limits close that recipient connection rather than
truncating data or weakening validation.

Each recipient retains only bounded identity/revision/state/metadata-digest/request stamps, not
old catalog copies. A STALE_CATALOG error correlated to the latest delta clears that source's
stamp, forcing a full snapshot on the next poll even without another source edit. Unknown or
obsolete correlations do not create work. A failed or blocked write closes its channel under
existing transport deadlines; workers share no index lock while doing socket I/O. A blocked peer
can occupy one bounded worker until its deadline, without blocking source admission or heartbeats.

Backend replicas require matching metadata/request/revision for full snapshots and exact available
base revisions for deltas. Gaps request full resynchronization. Old, conflicting, malformed or
source-mismatched updates cannot replace the last validated snapshot.

## Budgets and expiry

`CatalogCacheLimits` defaults to 256 retained identities, 2 MiB per server, 64 MiB globally and
five minutes of stale retention. Configuration can lower these values; hard ceilings are 1,024
identities, 2 MiB per server, 64 MiB total and one day of stale retention. Protocol snapshot,
frame, allocation and object limits still apply independently.

Byte accounting covers canonical full-catalog bytes, optional encoded deltas, and current/pending
UTF-8 display metadata. Admission is serialized and checks the entire candidate before replacing
valid state. Optional deltas are discarded before full-data admission. Rejected candidates preserve
the previous publication. Metadata admission also has per-server/global limits. These are canonical
byte budgets, not an exact JVM heap measurement: object overhead, fixed per-identity state and
32-byte revision fingerprints are additionally bounded by identity and protocol object ceilings.
Transport reassembly has its separate existing byte/request limits. Each active distribution worker
can temporarily hold one older immutable publication and bounded encoding buffers during a send;
these references are released after its transport deadline. There is no per-peer snapshot history.

Disconnect, source unavailability, or a delta gap makes the last valid catalog STALE. Stale data
remains visible, distinguishable from an authoritative successful empty catalog. The coordinator
distributes stale snapshots/invalidation to peers; later expiry sends UNAVAILABLE and discards
replica content. Backend loss of its coordinator connection independently makes all retained
replicas stale. Reconnect requires complete validation before availability returns.

Expiry uses monotonic time from the first stale transition; reconnect or repeated invalidation
does not extend stale retention. Periodic maintenance runs even without catalog edits; reads also
apply expiry. Expiry frees snapshot/delta content while retaining bounded revision high-water marks
and SHA-256 fingerprints until agent stop. This rejects revision rollback and equal-revision content
conflicts after expiry, while allowing an identical full refresh. Expiry during an in-flight refresh
does not discard its pending metadata; transport deadlines still bound completion.

Identity slots are not silently recycled: when all configured slots are retained, an additional
ID is rejected until the owner restarts or its configuration is changed for a new instance.
This preserves revision protection without an unbounded tombstone history. Expired content frees
the byte budget for already admitted identities. Stop clears the transient index. Durable backend
revision sequences from Step 9 remain necessary across backend restarts.

## Verification

On 2026-09-08, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passed 444 common tests and 67 proxy tests, with zero failures/errors/skips. The six new cases cover:

- Real A-to-B synchronization in KK and plaintext, duplicate names, edits, immutable views,
  coordinator source-mode retention, backend reconnect and stale/expiry distribution.
- Per-server/global/identity limits, old revisions and equal-revision conflicts after expiry,
  safe identical refresh and byte-budget reuse.
- Source-ID mismatches and obsolete session writes/cleanup.
- Actual delta frames, correlated full resynchronization and multi-chunk full fallback.
- Expiry during a pending full refresh.

The Velocity artifact includes the shared index/receiver and coordinator distributor as Java 17
classes. These tests do not boot Minecraft or a Velocity proxy. Full backend artifact and native
runtime validation remain platform-integration gates.
