# Upload and Chunk Transport Progress

Last updated: 2026-08-29
Branch baseline: `feature/upload-3.1.0` at `17c59d9`, with resolutions 1 through
3 committed and runtime Folia verification remaining

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
| Scheduler submissions | Resolved | Prepared bodies enter manager accounting before Paper schedules owner-thread delivery. One asynchronous batch remains in flight per peer, and disconnect or scheduler failure completes the exact delivery without clearing replacement state. |
| Workflow delivery results | Resolved | Delivery tickets separate immediate admission from final delivery. Downloads report success only after completion, failed upload-request packets cancel the exact lease, edit-result failures are logged, and the edit screen restores its controls after a 30-second response deadline. |
| Manager-wide blocking | Resolved | Encoding, compression, decompression, decoding, and platform callbacks run outside transport-state locks. Unrelated peers have independent state and decode locks; only aggregate byte accounting uses one short lock. |
| Aggregate broadcast resources | Resolved | Broadcast bodies, inbound reassembly, and deferred Paper dispatch are admitted to a 256 MiB manager budget. Inbound reservations stay charged through decoding and the synchronous application callback, so decoded object graphs remain inside the same accounting boundary. |
| Frame pacing | Resolved | Each peer emits at most 8 frames and 192 KiB per tick, and one manager-wide round-robin grant bounds all peers together to 32 frames and 768 KiB per tick. Admission no longer transmits; peers admitted during a tick begin on the next tick, and in-flight batches receive no additional grant. |
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
outside the state lock; completed inbound messages are decoded and handed to a
synchronous application callback under the peer's decode lock while their
reservation stays charged. A blocked or malformed transfer for one peer
therefore cannot hold transport state for unrelated Folia regions, and a slow
application callback holds only its own reservation plus its peer's decode
lock.

Transmission is decoupled from admission. `sendTracked` only queues a transfer
and schedules the peer on a round-robin rotation, so aggregate throughput is
bounded by one global grant per manager tick: at most 32 frames and 768 KiB
across all peers, with each visited peer receiving at most its own 8-frame and
192 KiB budget. Peers left without a grant rotate ahead of granted peers, so
continuously active peers all progress without starvation, and peers admitted
during a tick begin on the next tick. Mod loaders drain on their normal
server/client ticks. Paper runs one manager-wide maintenance tick on its async
scheduler, permits at most one outstanding entity-scheduler handoff per peer,
and emits each batch only from that player's owning region.

## Completed follow-up resolutions

| Resolution | Commit | Result |
| --- | --- | --- |
| Passive timeout and client divergence handling | `e35af70` | Removed automatic recovery, added inactivity and lifetime expiry, propagated message type and transfer identity in failures, and added revision-aware client refusal. |
| Request-scoped upload ownership and lifecycle cleanup | `b553c78` | Bound player, request, and transfer identity; reserved leases before revision capture; added exact cancellation, lifecycle reset, and per-player cooldown. |
| Two-stage outbound delivery | `2acd1b7` | Admitted messages before owner-thread scheduling, added exact asynchronous completion, connected user workflows to final outcomes, and bounded edit-screen waiting. |
| End-to-end global resource grants | `17c59d9` | Added manager-wide frame/byte grants over a fair round-robin rotation, made admission queue-only, replaced the list-returning receive API with synchronous application inside the accounting boundary, and removed every per-peer outbound bypass. |

## Remaining issues, in execution order

| Order | Issue | Priority | Is it necessary? | Recommended trade-off or next step |
| ---: | --- | --- | --- | --- |
| 4 | Complete automated and live Folia coverage | Release blocker | Yes | Run multi-region, disconnect, malformed, saturation, incompatible-client, and low-TPS large-transfer smoke tests on a live Folia server. |

### 3. Global resource grants (resolved)

The general and upload managers each carry their own global tick budget and
round-robin rotation, and the upload manager stays additionally constrained by
its upload lease. Inbound reservations remain charged until decoding and the
synchronous handler finish, so decoded object graphs are inside the same
retained-byte budget; cleanup races cannot underflow or double-release the
accounting.

### 4. Runtime Folia and stress coverage

Request identity, lifecycle shutdown, exact upload transport binding, cooldown,
asynchronous delivery completion, stale-failure races, global pacing, and
application-lifetime accounting now have focused coverage. Remaining work is a
Folia smoke test with compatible and incompatible clients in different regions,
stalled and malformed transfers, disconnects, saturation, and a progressing
large transfer under low TPS.

#### 4A. Prepare the live-test environment

This first part prepares a repeatable, disposable environment for the runtime
matrix. It does not count any scenario as passed; execution and evidence
collection belong to the next part of resolution 4.

Working-tree support for this phase lives under `tools/folia-live-test/`, with
test-only fixture, Fabric probe, and Paper region-load source sets. The launcher
requires an explicit Folia JAR and existing MCC executable, refuses repository
or existing roots, builds the direct 1.21.11 version projects, and records
checksums and roles before launch. These tools do not make any gate below pass
until their generated environment is exercised and its evidence is reviewed.

Use Minecraft 1.21.11 for the primary run because this repository has matching
Paper and Fabric projects and the previous live Folia validation used that
version. Record the exact Folia build and Java runtime in the result. Build the
server plugin and compatible client directly from their version projects without
switching either Stonecutter active project:

```shell
./gradlew --no-daemon --no-parallel --max-workers=2 \
    :paper:1.21.11-paper:shadowJar \
    :mods:1.21.11-fabric:build
```

Prepare the environment in this order:

1. Create a temporary Folia server directory outside every checked-in or
   user-owned run directory. Configure a dedicated port, offline test identities,
   accepted EULA, and a disposable world, then install only the newly built
   ServerWaypoint plugin and required server-side test tooling.
2. Create two isolated compatible Fabric client launch directories with distinct
   offline usernames. Keep their configuration, Xaero data, logs, and game
   directories separate from existing user data. Add the existing MCC executable
   as the incompatible vanilla client.
3. Place the two compatible players far enough apart that Folia assigns them to
   independent ticking regions. Record their dimensions and coordinates so the
   placement can be reproduced before every run.
4. Generate disposable server and Xaero fixtures large enough that one logical
   transfer requires multiple manager ticks. Include a smaller control fixture
   whose expected lists, waypoint counts, revisions, and final contents can be
   compared exactly.
5. Add a development-only protocol probe that negotiates protocol 9 and can send
   a valid transfer, stop after a selected frame, corrupt a checksum or header,
   open transfers until admission returns `PEER_BUSY`, and disconnect at a
   selected frame. Reuse production codecs where possible, but keep the probe and
   its controls out of distributable JARs and do not add a production debug
   command or alternate wire protocol.
6. Add a repeatable low-TPS control scoped to the disposable server. Prefer a
   test-only region load tool whose duration and target region are explicit;
   verify that removing the load returns the server to normal operation.
7. Define one launch script or runbook that records the repository commit, plugin
   checksum, Folia build, Java version, client roles, fixture checksum, commands,
   timestamps, and locations of all server and client logs.

Environment preparation is complete only when all of these gates pass:

- Folia starts with the newly built plugin and reports no unsupported scheduler
  or ownership exception during startup.
- Both compatible clients negotiate protocol 9 from separate Folia regions, and
  MCC remains connected without negotiating the custom transport.
- The control fixture completes in both directions and its final revisions and
  waypoint contents match exactly.
- The development probe can deterministically produce partial, malformed,
  saturated, and mid-transfer disconnect inputs without modifying production
  code or user-owned data.
- The large fixture spans multiple transport ticks, and the low-TPS control is
  repeatable and reversible.
- A clean restart removes temporary leases and transfers while preserving only
  the expected committed waypoint state.

After these gates pass, resolution 4 can proceed to the scenario matrix:
multi-region broadcast, incompatible-client containment, timeout and malformed
cleanup, disconnect races, saturation fairness, and large-transfer progress
under low TPS.

## Validation status

Focused checks for the committed global-resource-grant implementation:

- `:common:test` — 283 tests including the chunked-transport outbound pacing,
  fairness, and inbound application-lifetime suites
- `:mods:26.1.2-fabric:test` — 193 tests covering chunk application and edit
  deadlines
- `:paper:26.2-paper:test` — scheduler dispatch and manager-wide maintenance tests
- representative old and new Fabric (1.20.1, 26.2), Forge (1.20.1, 1.21.11,
  26.2), NeoForge (1.20.2 NeoGradle, 1.21.11, 26.2), and Paper (1.21, 1.21.11,
  26.2) compilation
- `git diff --check`

The representative compilation refresh covers Minecraft 1.20.1 through 26.2.

These checks validate compilation and focused state-machine behavior only. No
live Folia result has been recorded, so resolution 4 remains open regardless of
the automated checks above.
