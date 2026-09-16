# Server Waypoint 3.1.0

Release candidate for the chunked upload and download transport.

## Breaking change: network protocol 9

3.1.0 moves the client and server to **network protocol 9**. A 3.1.x client and a
3.1.x server are required on both ends; mixing 3.0.x and 3.1.x will not sync.

- `ProtocolVersion.PROTOCOL_VERSION = 9`, `COMPATIBLE_VERSION = "3.1.x"`.
- Application-level ACK and retry frames are removed. Minecraft's connection
  already provides reliable ordered delivery, so the chunk layer now owns only
  fragmentation, checksums, bounded reassembly, timeout cleanup, backpressure
  and pacing.
- Logical messages use stable type IDs over a shared ordered chunk channel
  instead of one payload type per message.

## Upload transport safety

Waypoint uploads on Paper/Folia now use an exclusive, server-issued upload
pairing rather than a general transaction system:

- Only one client can hold the upload lease (30 seconds), and only that client
  and request ID may send upload chunks. Concurrent uploads return `BUSY`.
- A timeout or disconnect cannot interrupt an upload that has reached
  `APPLYING`.
- New upload requests and client waypoint edit requests are rejected while the
  lease is active; edits arriving during an upload return `UPLOAD_BUSY`.
- Uploads capture a per-dimension revision before receiving and skip any
  dimension that changed before commit, so server-side or third-party mutations
  are never destructively overwritten.
- A five-second reacquisition cooldown applies per player after an admitted
  lease terminates. Other players remain immediately eligible.

Each dimension commits atomically. A multi-dimension upload is **not**
crash-atomic as a whole; see the progress document for the rationale.

## Transport robustness

- **Bounds and limits** — channel-specific logical-byte and decoded-object
  budgets; update requests reject excessive or duplicate dimensions and
  oversized or control-character identifiers.
- **Passive timeout cleanup** — incomplete inbound transfers expire on progress
  inactivity or an absolute lifetime, clearing only that peer's receive lane.
- **Client divergence detection** — state-affecting transport failures mark the
  session uncertain; revision gaps mark the affected list out of sync until an
  authoritative replacement arrives. Healthy lists continue normally.
- **Saturation containment** — exhausted transfer slots or retained bytes return
  `PEER_BUSY` instead of throwing through a broadcast.
- **Global pacing** — at most 8 frames / 192 KiB per peer per tick and 32 frames
  / 768 KiB manager-wide, over a fair round-robin rotation.
- **Memory accounting** — broadcast bodies, inbound reassembly and deferred
  Paper dispatch are admitted to a 256 MiB manager budget; inbound reservations
  stay charged through decoding and the synchronous application callback.

## Platform behaviour

- **Paper / Folia** — player work uses the entity scheduler and maintenance uses
  the async scheduler. No legacy `Bukkit.getScheduler()` or `BukkitScheduler`
  usage remains, and `folia-supported: true` is declared.
- **Capability handshake** — chunked traffic is only admitted after a matching
  protocol-9 handshake. Capability is reset on join and cleared on disconnect.
- **Proxy (Velocity) transfers** — pending waypoint edits are flushed to the
  previous server's file before rebinding, and stale cache bindings are cleared
  until the new server's handshake completes.

## Compatibility notes

- **Protocol compatibility is 3.1.x only.** Do not mix 3.1.x clients with 3.0.x
  servers or vice versa; the client reports an incompatible protocol and does
  not sync.
- **Minecraft** — supported across 1.20.1 through 26.2, split per loader as
  listed below.
- **Java** — Paper 26.2, Fabric 26.1.2/26.2, Forge 26.2 and NeoForge 26.2 build
  against Java 25; every other target builds against Java 17 or 21. Servers must
  run a matching runtime.
- **Paper / Folia** — 1.21-paper covers 1.21–1.21.10; 1.21.11-paper covers
  1.21.11–26.1.2; 26.2-paper covers 26.2.
- **Optional dependencies** — Xaero's Minimap remains the primary integration;
  Xaero's World Map and VoxelMap are now declared build inputs.
- **Development-only targets** — the 1.21.3 Fabric and NeoForge targets exist
  only as runtime test environments (Xaero's Minimap has no 1.21.2 build) and
  are excluded from Modrinth, CurseForge and the release artifact collection.

## Validated targets

See "Validation status" in `docs/upload-transport-progress.md` for the recorded
build, test and live Folia results backing this release candidate.
