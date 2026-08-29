# Upload and Chunk Transport Progress

Last updated: 2026-08-29
Branch baseline: `feature/upload-3.1.0` at `b553c78`, including the committed
protocol 9 transport, passive failure handling, and upload lifecycle changes

## Goal

Make Xaero waypoint uploads safe on Paper/Folia without building a general
transaction system for a feature that normally has one uploader and modest
payloads. The selected model is an exclusive server-issued upload pairing:

- only one client can hold the upload lease;
- only that client and request ID may send upload chunks;
- new upload requests and client waypoint edit requests are rejected while the
  lease is active;
- server-side revision checks still protect against mutations that do not enter
  through the client edit protocol.

This document distinguishes implemented safety and scaling work from the smaller
operational issues that remain open.

## Current status

| Area | Status | Current behavior |
| --- | --- | --- |
| Exclusive upload pairing | Resolved | `UploadCoordinator` reserves one global 30-second lease before revision capture and moves it through `RESERVING`, `RECEIVING`, `APPLYING`, and `FINISHED`. Concurrent uploads return `BUSY`, and timeout/disconnect cannot interrupt `APPLYING`. |
| Edit-versus-upload race | Resolved for client edit requests | A small admission monitor prevents a pairing request from racing with admitted C2S waypoint edits. Edits received during an upload return `UPLOAD_BUSY`. |
| Upload sender authentication | Resolved | A dedicated C2S upload-chunk channel carries the server-issued request ID. Chunks from another player, request, or expired lease are rejected before reassembly or waypoint decoding. |
| Upload bounds and stale data | Resolved | Uploads retain permission checks, semantic limits, server-owned policy, per-dimension revision checks, atomic dimension mutation, save-failure reporting, and navigation refresh. |
| Cross-platform client capability | Resolved | Paper and the mod loaders admit chunked traffic only after a matching protocol handshake. Capability is reset on join and cleared on disconnect; Paper also checks the live `getListeningPluginChannels()` set before opening an S2C transfer. |
| Directional channel admission | Resolved | The general C2S channel accepts only update and edit requests from negotiated peers. The upload channel accepts only waypoint data for the active negotiated lease, and the client accepts only the defined S2C message types after a compatible server handshake. |
| Serverbound decode limits | Resolved | Update, edit, and upload messages have channel-specific logical-byte and decoded-object budgets. Update requests additionally reject excessive or duplicate dimensions/lists and oversized or control-character identifiers before querying server state. |
| Passive timeout cleanup | Resolved | Incomplete inbound transfers expire after progress inactivity or an absolute lifetime. Expiry clears the peer's incomplete receive lane, releases accounting, reports typed failures, and never requests an automatic snapshot recovery. |
| Client divergence detection | Resolved | State-affecting transport failures mark the session uncertain. Revision gaps mark the affected list out of sync, and later incrementals for that list are refused until an authoritative replacement arrives; healthy lists continue normally. |
| Saturation containment | Resolved at the networking boundary | Active-transfer and retained-byte exhaustion return `PEER_BUSY`; encoding and delivery failures return typed results. A saturated recipient no longer throws through a broadcast and aborts later recipients after a mutation. |
| Paper/Folia ownership dispatch | Resolved | Player work uses the entity scheduler, maintenance uses the async scheduler, and no legacy `Bukkit.getScheduler()` or `BukkitScheduler` usage is present. |
| Scheduler submissions | Partially resolved | Paper's maintenance pass schedules at most one outstanding entity-owned batch per active peer, but initial non-owner dispatch can still retain a prepared body outside manager admission until its entity task runs. |
| Manager-wide blocking | Resolved | Encoding, compression, decompression, decoding, and platform callbacks run outside transport-state locks. Unrelated peers have independent state and decode locks; only aggregate byte accounting uses one short lock. |
| Aggregate broadcast resources | Partially resolved | Broadcast bodies are shared and admitted to a 256 MiB manager budget, but deferred Paper dispatch, decoded-object application lifetime, and aggregate multi-peer throughput are not yet covered end to end. |
| Frame pacing | Partially resolved | Each peer emits at most 8 frames and 192 KiB per tick, but there is no manager-wide frame/byte grant across all peers. |
| Request-scoped upload cleanup | Resolved | The first accepted upload frame binds player, request ID, and transfer ID. Timeout, malformed, and decode failures cancel only that exact session; disconnect and every loader shutdown clear the dedicated manager and coordinator state. |
| Lease reacquisition fairness | Resolved | A stable player UUID receives a five-second cooldown whenever an admitted lease terminates. Other players remain immediately eligible and no queue is introduced. |
| Live Folia verification | Unresolved | Unit tests and representative loader compiles passed, but the pairing, multi-region broadcast, disconnect, timeout, and saturation paths have not been exercised on a running Folia server. |

## What the pairing model intentionally does not guarantee

The lease is not a database transaction or a universal server write lock.
Commands, console actions, and other server/plugin mutation sources are not
rejected by the C2S edit admission gate. Instead, the upload captures a revision
for every requested dimension and skips a dimension if it changed before commit.
This avoids destructive overwrites without forcing unrelated server work through
the upload coordinator.

Uploads also commit one dimension at a time. Each dimension mutation is atomic,
but a multi-dimension upload is not crash-atomic as a whole. Adding a journal or
multi-file transaction is not justified for normal waypoint uploads unless an
all-or-nothing persistence contract becomes a product requirement.

## Resolved transport design

Protocol version 9 removes application-level ACK and retry frames. Minecraft's
connection already provides reliable ordered delivery, so the chunk layer now
owns only fragmentation, checksums, bounded reassembly, timeout cleanup,
backpressure, and pacing.

Broadcasts call `prepare()` once. The resulting immutable frames are referenced
from bounded per-peer queues instead of being regenerated for every recipient.
Each peer may queue at most eight transfers and 64 MiB, while one short global
accounting operation limits unique outbound bodies plus conservative inbound
reassembly/decoding reservations to 256 MiB.

Every peer has an independent state lock and decode lock. Encoding and
compression happen before admission; completed bodies are detached and decoded
outside the state lock; batch callbacks also run unlocked. A blocked or malformed
transfer for one peer therefore cannot hold transport state for unrelated Folia
regions.

Delivery is limited to eight frames and 192 KiB per peer tick. Mod loaders drain
on their normal server/client ticks. Paper uses async maintenance to find active
peers, permits at most one outstanding entity-scheduler handoff per peer, and
emits the batch only from that player's owning region.

## Completed follow-up resolutions

| Resolution | Commit | Result |
| --- | --- | --- |
| Passive timeout and client divergence handling | `e35af70` | Removed automatic recovery, added inactivity and lifetime expiry, propagated message type and transfer identity in failures, and added revision-aware client refusal. |
| Request-scoped upload ownership and lifecycle cleanup | `b553c78` | Bound player, request, and transfer identity; reserved leases before revision capture; added exact cancellation, lifecycle reset, and per-player cooldown. |

## Remaining issues, in execution order

| Order | Issue | Priority | Is it necessary? | Recommended trade-off or next step |
| ---: | --- | --- | --- | --- |
| 2 | Separate outbound admission from owner-thread delivery | P1/P2 | Yes for user workflows | Admit prepared bodies before scheduling, return an asynchronous final result, cancel failed upload requests exactly, and add an edit-screen response deadline. |
| 3 | Add end-to-end global resource grants | P1 under load | Yes before scale sign-off | Round-robin active peers under one global tick budget and retain inbound accounting through synchronous message application. |
| 4 | Complete automated and live Folia coverage | Release blocker | Yes, after resolutions 2 and 3 | Test each boundary above, then run multi-region, disconnect, malformed, saturation, incompatible-client, and low-TPS large-transfer smoke tests. |

### 2. Two-stage outbound dispatch

Manager admission should happen before a Paper entity-scheduler handoff. Return a
ticket with immediate admission and asynchronous final completion so broadcasts
can remain best effort while upload, download, and edit workflows receive exact
failure handling. The edit screen also needs a response deadline.

### 3. Global resource grants

Keep the existing per-peer limits, but assign frame and byte grants from one
round-robin global budget per tick. Retain inbound reservations until decoding and
synchronous handler application finish so decoded object graphs remain inside the
same accounting boundary.

### 4. Runtime Folia and stress coverage

Request identity, lifecycle shutdown, exact upload transport binding, cooldown,
and stale-failure races now have focused coverage. Add asynchronous delivery
completion and global pacing tests, then run a Folia smoke test
with compatible and incompatible clients in different regions, stalled and
malformed transfers, disconnects, saturation, and a progressing large transfer
under low TPS.

## Validation status

Focused checks refreshed at `b553c78`:

- `:common:test`
- `:mods:26.1.2-fabric:test`
- `:paper:26.2-paper:compileJava`
- `git diff --check`

Broader representative Fabric, Forge, NeoForge, and Paper compilation from
Minecraft 1.20.1 through 26.2, plus JSON parsing for the changed language files,
was completed during the preceding implementation work. It has not been
re-run as part of the latest focused refresh.

These checks validate compilation and focused state-machine behavior only. No
live Folia result has been recorded, so resolution 4 remains open regardless of
the automated checks above.
