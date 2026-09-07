# Cross-server catalog models (step 4)

The domain types live in `common/.../crossserver` because both backends and the proxy consume
catalogs. They do not register networking, publish local data, mutate waypoint files, or grant
teleport permission. Store replacement, cache budgets/expiry, delta application, and publication
remain in their later plan steps.

## Snapshot structure and ownership

`RemoteCatalogSnapshot(serverId, catalogRevision, dimensions, receivedAt)` is a complete immutable
publication. `dimensions` maps exact dimension names to maps of exact list names. Each
`RemoteListSnapshot(displayName, listRevision, waypoints)` maps exact waypoint names to immutable
`RemoteWaypointSnapshot` values. Empty strings, case, whitespace, Unicode, empty dimensions, and
empty lists are preserved. Map iteration order is not a wire-order contract; step 5 must impose
canonical ordering when encoding.

Waypoint values contain display name, initials, immutable integer `WaypointPos`, 24-bit RGB,
yaw in [-180, 180], global flag, keywords, and description. Values are validated without silently
normalizing them. Coordinates remain exact and advisory. Display names do not participate in
lookup; `find(RemoteWaypointKey)` checks the server and each hierarchy component separately.
The global flag is presentation data, not proof of export or permission eligibility.

Every map layer and keyword list is defensively copied and unmodifiable. Null values, keys, and
collection elements are rejected. No mutable `SimpleWaypoint`, `WaypointList`, local manager,
render state, or mutation authority is retained. Authoritative export adapters are still to be
implemented in step 9; they must capture local state atomically before constructing these values.

## Revisions and receipt time

`RemoteRevision` holds a non-negative signed 64-bit value, shared by catalog and list revision
fields. Its ordering uses `Long.compare`; `next()` fails on exhaustion instead of wrapping.
Catalog and list revisions are independent: unchanged lists can retain their revision while the
catalog advances. Compare list revisions only for the same server/dimension/list identity and
revision lifetime. Cross-server revisions are not globally ordered.

`RemoteCatalogSnapshot.isNewerThan` requires matching server IDs and a strictly larger catalog
revision. Equal and older revisions are not newer. Full snapshots may skip revisions; delta-gap
validation belongs to synchronization. `receivedAt` is local receipt metadata and never overrides
revision ordering. Durable revision allocation and restart/re-registration policy remain part of
publication/lifecycle implementation; no allocator or reset semantics are supplied here.

## Reader status

`RemoteCatalogView(serverId, state, snapshot)` separates source status from published contents:

| State | Snapshot requirement |
| --- | --- |
| `AVAILABLE` | Present, including an explicitly empty publication. |
| `STALE` | Present, retained as advisory data. |
| `UNAVAILABLE` | Absent or retained as stale advisory data; no synthetic empty publication. |
| `UNAUTHORIZED` | Absent; a denied reader cannot receive retained entries through this value. |

A present snapshot must match the view's server. Constructors reject inconsistent combinations.
The owning service must perform authorization before supplying a view; these values do not check
permissions. Retaining private cache data internally while hiding it from denied readers is a later
store concern. Only an explicit newer snapshot, expiry, or administrative removal should remove
previous contents; constructing an unavailable view does not modify its retained snapshot.

## Validation

`RemoteCatalogSnapshotTest` covers all collection layers, exact lookup and server separation,
empty hierarchy preservation, catalog/list revision independence, comparison and exhaustion,
receipt-time independence, null/malformed values, and unavailable/empty/unauthorized distinctions.
The existing `RemoteIdentityTest` continues to cover server-ID syntax and exact tuple identity.
