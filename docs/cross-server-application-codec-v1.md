# Cross-server application wire format v1 (step 5)

`common/.../crossserver/protocol` supplies immutable `ApplicationMessage` values,
`ApplicationEnvelope`, `ProtocolLimits`, and `ApplicationCodec`. This is a backend/coordinator
protocol, separate from Minecraft custom payloads. There are no sockets, timers, registration
handlers, authorization, transfers, replay caches, or catalog stores in this step.

## Envelope and canonical primitives

All integers use fixed-width big-endian encoding. Each complete application frame is:

| Field | Bytes | Rule |
| --- | --- | --- |
| Application version | 4 | Exactly 1. |
| Message type | 4 | One of the explicit IDs below. |
| Sender event sequence | 8 | Non-negative signed long; session-local and per direction. |
| Request UUID | 16 | Most-significant long followed by least-significant long; non-nil. |
| Payload length | 4 | Non-negative and exactly the remaining bytes. |
| Payload | Variable | Defined below; no trailing bytes permitted. |

The fixed header is 36 bytes, included in the frame limit. Every operation carries both a sequence
and a UUID. Senders assign new UUIDs to unsolicited events/requests; responses echo their request
UUID. All phases of one handoff preserve the original prepare UUID. Multiple snapshot chunks share
a publication request UUID and snapshot UUID but have distinct sender sequences. Step 6 must enforce
monotonic sequences without wrapping, and later request handlers must reject duplicate operations
without rejecting legitimate correlated responses/phases. UUID syntax and decoding alone provide
no replay protection. Nothing about plaintext sequencing authenticates its sender.

Primitive formats:

- String: signed int UTF-8 byte length followed by those bytes. Strict UTF-8 only: reject malformed,
  overlong, surrogate, out-of-range, and truncated encodings. Encoding rejects unpaired Java
  surrogates. No trimming, case folding, Unicode normalization, or replacement characters.
- UUID: two longs, as above. Player, handoff, snapshot and request UUIDs must be non-nil.
- Revision: non-negative signed long; no wrapping or sentinel deletion values.
- Collection: signed int count followed by entries. Map string keys and string sets are strictly
  increasing by Java `String.compareTo` (UTF-16 code-unit order, not locale or UTF-8 byte order).
  Integer capability sets use strictly increasing positive IDs. Duplicates and noncanonical order
  are errors. Keyword lists retain their original order and duplicates.
- Boolean: exactly one byte, 0 or 1. Coordinates, RGB and yaw are signed ints, with RGB restricted to
  [0, 0xFFFFFF] and yaw to [-180, 180]. Coordinates retain their original signed-int values.
- Server ID: string satisfying `RemoteServerId`. Exact waypoint key: server ID, dimension string,
  list string, waypoint string. Display names never replace these identity fields.

No enum ordinal is used on the wire. Export policy PUBLIC = 1; action TELEPORT = 1.
Catalog states are AVAILABLE = 1, STALE = 2, UNAVAILABLE = 3, UNAUTHORIZED = 4.
All other policy/action/state codes reject.

Result codes are SUCCESS = 0, UNAVAILABLE = 1, UNAUTHORIZED = 2, NOT_FOUND = 3,
STALE_CATALOG = 4, BUSY = 5, EXPIRED = 6, REPLAY = 7, WRONG_SOURCE = 8,
WRONG_DESTINATION = 9, TRANSFER_FAILED = 10, CANCELLED = 11, UNSUPPORTED = 12,
INVALID_REQUEST = 13, INTERNAL_ERROR = 14. Unknown codes reject. Failure-only messages cannot
carry SUCCESS. Diagnostics carry these bounded categories, not arbitrary exception text.

## Stable message IDs and ordered payload fields

Field names below are listed in wire order. `binding` expands to: handoff UUID, player UUID,
source server ID, exact target key, action, positive expiry epoch-milliseconds (long).
The destination is the server ID in the key and must differ from the source. Bindings contain no
coordinates. Expiry validity against the current clock and maximum lifetime is a later service check.

| ID | Message | Payload |
| --- | --- | --- |
| 1 | REGISTER_SERVER | server ID, application version (int, exactly 1), capability ID set |
| 2 | REGISTER_RESULT | server ID, result (int) |
| 3 | HEARTBEAT | Empty; sender sequence provides freshness correlation. |
| 10 | CATALOG_METADATA | server ID, display name, catalog revision, export policy (int) |
| 11 | CATALOG_SNAPSHOT | server ID, catalog revision, snapshot UUID, offset (int), total catalog bytes (int), chunk byte length (int), chunk bytes |
| 12 | CATALOG_DELTA | server ID, base revision, new revision, replacement dimension/list map, removed-list map, removed-dimension string set |
| 13 | CATALOG_INVALIDATE | server ID, catalog revision, state (int, must not be AVAILABLE) |
| 20 | PREPARE_HANDOFF | player UUID, source server ID, exact target key, action (int), observed catalog revision, observed list revision |
| 21 | HANDOFF_PREPARED | binding |
| 22 | HANDOFF_REJECTED | failure result (int) |
| 23 | CLAIM_HANDOFF | handoff UUID, player UUID, destination server ID |
| 24 | HANDOFF_CLAIMED | binding |
| 25 | COMPLETE_HANDOFF | handoff UUID, player UUID, destination server ID, result (int) |
| 26 | CANCEL_HANDOFF | handoff UUID, failure result (int) |
| 30 | ERROR | failure result (int) |

Capability IDs are positive extensible identifiers; unknown IDs are preserved as advertisements,
not accepted feature support. The registration step must assign/interpret supported capabilities.
Unknown message IDs reject instead of being skipped or interpreted as a compatibility mode.

## Catalog encoding and chunk boundary

`encodeCatalog` produces a complete canonical byte sequence: application version, server ID,
catalog revision, then a dimension map. Each dimension value is a list map; each list value is:
display name, list revision, waypoint map. Each waypoint value is: display name, initials,
x, y, z, RGB, yaw, global boolean, keyword string list, description. All maps use the canonical
collection format above. Empty dimensions/lists are preserved.

`receivedAt` is deliberately absent from the wire. `decodeCatalog(bytes, receivedAt)` takes the
receiver's timestamp; remote clocks cannot change receipt metadata or revision ordering.
The codec uses the immutable `WaypointPos` value and existing `DecodingContext` budget tracker.
Local waypoint codecs are not reused: they return mutable objects, normalize yaw and display
fields, and their UTF decoder permits replacement characters, which breaks exact canonical input.

CATALOG_SNAPSHOT carries a nonempty slice of the complete catalog bytes. Its offset must be
non-negative, its total positive, and the slice must fit without integer overflow. A chunk may
split any encoded field; its bytes are opaque until the complete catalog is decoded. Step 6 must
bound retained bytes and pending publications, reject overlaps/missing fragments/inconsistent IDs,
revisions or totals, expire incomplete publications, and call `decodeCatalog` only on a complete
assembly. The decoded server ID and revision must match the chunk metadata before publication.
The Step 5 codec does not claim to implement or validate a reassembly session.

Deltas replace whole lists and remove exact lists/dimensions. Replacement dimension values use
the same list-map format as snapshots. The removed-list map maps dimension strings to list-name
sets. New catalog revision must exceed base revision. Removing a dimension while also replacing
or removing its lists is invalid; replacing and removing the same list is invalid. Empty
replacement maps can represent empty dimensions. Applying a delta requires matching current base
revision and validation of list revisions against authoritative state; that work remains in the
catalog synchronization steps. Deltas too large for a frame require full snapshot publication.
Invalidation is a state signal, never an implicit empty publication or permission grant.

## Independent bounds and failures

`ProtocolLimits.DEFAULT` defines these v1 maxima. A configured codec may lower positive limits;
it cannot raise the hard maxima.

| Resource | Maximum/default |
| --- | --- |
| Entire application frame, including header | 1,048,576 bytes |
| Complete encoded catalog, before chunking | 1,048,576 bytes |
| Chunk data | 262,144 bytes |
| Individual UTF-8 string | 65,536 bytes |
| Individual collection | 16,384 entries |
| Object-budget claims per encode/decode | 65,536 |
| Variable-allocation budget charges per encode/decode | 8,388,608 bytes |

Byte-array input length is checked before parsing; the transport must separately check declared
frame length before allocating that input. Buffer capacity is bounded while encoding. Declared
string/chunk lengths must fit the remaining input. Collection counts must fit their configured
limit and a minimum encoded-byte bound before collection creation. Allocation and object claims
precede variable allocations: strings charge six times UTF-8 length, collection slots 64 bytes,
and binary chunks twice their length for the defensive copy. Fixed model values and collection
entries also consume object claims. These are resource accounting units, not an exact JVM heap
measurement; total per-session retained-memory budgeting remains Step 6.

Malformed or over-budget input raises `IllegalArgumentException`; no partial model is returned.
The session layer must make decoding failure terminal. No decoder falls back to JSON, local
waypoint normalization, an old version, or a different transport mode. Neither successful decoding
nor round-trip tests establish authenticated transport or authorization.

## Verification

Tests cover all 15 message families, frozen heartbeat/empty-catalog bytes and numeric message IDs,
all truncation prefixes, trailing bytes (including declared trailing payload), exact catalog values,
receiver timestamps, canonical order and duplicate rejection, strict UTF-8, numeric and collection
malformations, independent resource limits, binary/delta ownership, invalid handoff/delta values,
and 2,000 deterministic byte mutations. Encoder/decoder object-budget agreement is checked for
all messages across budgets 1–200. Production framing and runtime integration remain unimplemented.
