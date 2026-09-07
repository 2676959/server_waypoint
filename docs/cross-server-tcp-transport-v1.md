# Cross-server TCP transport v1 (step 6)

`common/.../crossserver/transport` now provides reusable backend/coordinator TCP channels. Both
backends and the coordinator use this code, so it belongs in `common`. No Minecraft, Paper or
Velocity lifecycle starts it yet. Pairing/key persistence is step 7; bounded worker ownership,
reconnect, registration policy and heartbeat generation are step 8. Feature enablement remains off.

## Ownership and admission

- `TcpCoordinator` binds one explicitly selected `TransportMode`, with an immutable snapshot of
  admitted IDs/public pins. Its blocking `accept()` performs one admission attempt. It counts
  handshaking sockets against its connection ceiling, reserves each claimed ID, and rejects
  unknown/duplicate IDs without trying other keys. A reservation is not authenticated presence.
- `TcpBackend.connect()` performs one blocking connection attempt in the supplied mode. There is
  no negotiation/retry to plaintext. Callers must explicitly supply a mode; configuration will
  default to `NOISE_KK` in the later integration.
- `TcpChannel` is returned only after KK confirmation/acknowledgement, or explicit plaintext
  admission. `authenticated()` is false for plaintext. The ID and advertised capabilities are
  immutable; capabilities do not themselves grant authorization.
- Use bounded transport workers, never game threads. The implementation creates no accept loop,
  unbounded task queue or thread per socket. One shared daemon scheduler closes expired sockets.
  A channel supports one concurrent reader and writer; additional callers serialize per direction.
  State updates and all cipher access/destruction are separately serialized. Socket I/O never holds
  the state/cipher monitor. `close()` interrupts blocked I/O, destroys ciphers, releases reservations,
  clears retained state and cancels scheduled expiry. Closing the listener closes pending and live
  sockets. Credential objects remain owned by the caller and may be destroyed after listener stop.
- `disconnect(id)` closes current sockets only; it does not revoke admission. Step 7/8 must replace
  admission policy before permitting subsequent connections. This is deliberately not an
  implementation of the nonblocking `TransportLifecycle` interfaces; those wrappers belong to the
  lifecycle step and must complete shutdown only after their own workers have exited.

`NoiseKeys` defensively owns a raw 32-byte X25519 private key, wipes it on close and redacts
`toString()`. Raw public pins are copied at configuration/connection boundaries. These are internal
transport inputs, not the final credential-file/import formats. Plaintext rejects supplied keys;
its admitted registry values are empty arrays. Credentials are neither logged nor persisted here.

Plaintext endpoint validation accepts dotted-quad 127/8 or literal IPv6 loopback (`::1`, optionally
bracketed, or its full expansion). It rejects DNS names, wildcard/remote addresses, abbreviated IPv4,
IPv4-mapped IPv6 and scoped addresses. Both local and peer socket addresses must also be loopback.
This mode trusts local processes; sequencing and configured IDs do not authenticate them.

## Preface and handshake framing

Every outer frame is a big-endian signed 32-bit positive length followed by exactly that many bytes.
Validate the ceiling before allocation: prefaces 65,536 bytes, Noise handshake messages 65,535 bytes.
Each endpoint sends this preface payload, in order:

| Field | Encoding |
| --- | --- |
| Magic | int `0x53575054` (`SWPT`) |
| Transport version | int 1 |
| Mode | int 1 = KK, 2 = plaintext |
| Application version | int 1, independent of Minecraft payload protocol |
| Backend server ID | int length (1–64), exact ASCII bytes |
| Backend capabilities | int count (0–64), strictly ascending positive ints |
| Fresh nonce | 32 random bytes, independently generated at each endpoint |
| Offered suites | int count (0–16), strictly ascending positive ints |
| Selected suite | int; request 0, KK response 1, plaintext response 0 |

The coordinator echoes ID/capabilities. KK advertises only suite 1,
`Noise_KK_25519_AESGCM_SHA256`. Unknown future IDs can be parsed in an offer but are never selected;
a KK offer must include suite 1. Plaintext has no suites. Unsupported versions, mismatched modes,
noncanonical fields, trailing bytes and unsupported selections reject.

The Noise prologue is Java `DataOutputStream.writeUTF("ServerWaypoint TCP v1")`, followed by
int request-preface length, exact request-preface bytes, int response-preface length, exact
response-preface bytes. This binds versions, mode, ID, capabilities, fresh nonces, all offered suites
and the selected suite. The backend is the KK initiator, with a pinned coordinator key. The
coordinator selects the one registered backend pin using the initially untrusted ID hint.

Both KK handshake payloads must be empty. After split, the backend sends its first encrypted
transport record containing byte 1 followed by the 32-byte handshake hash. The coordinator
validates this before sending byte 2 plus the same hash in its first encrypted record. The backend
validates this acknowledgement before returning a channel. Operational bytes are never accepted
as confirmation. A failed handshake or confirmation closes the socket with no downgrade.

## Records and application reassembly

Encrypted records have a maximum of 65,535 bytes including the 16-byte AEAD tag. Plaintext records
have a maximum of 65,519 bytes. The encrypted plaintext/cleartext fragment payload is:

| Field | Encoding |
| --- | --- |
| Complete application-frame length | int, 36 through local `ProtocolLimits.frameBytes()` |
| Offset | int, exactly the next contiguous offset starting at 0 |
| Fragment data | nonempty remaining bytes, at most 65,511 |

Only one application frame may be assembled in each direction at once. Totals cannot change;
fragments cannot overlap, skip, exceed the declared total, or interleave frames. Decode only the
complete frame with `ApplicationCodec`. KK's implicit per-direction record nonces authenticate
fragment contents/order; plaintext does not. Cipher counters fail before signed-long exhaustion,
well below the dependency's reserved nonce boundary.

`send(uuid, message)` assigns independent monotonically increasing outbound envelope sequences
starting at 0; `receive()` requires exact contiguous inbound sequences starting at 0. Neither
counter wraps. A repeated `(request UUID, message type)` is terminal, with one exception for
contiguous chunks of an active catalog publication. Different correlated response/handoff phases
can share a UUID. Completed-operation history is never evicted; exhaustion closes the session.
These checks prevent transport replay, not semantic handoff reuse under a different request ID;
the later handlers still own authorization and one-time handoff consumption.

Catalog publications retain a bounded complete encoded catalog per active request UUID. The first
chunk must start at zero; subsequent chunks must keep server ID, revision, snapshot UUID and total,
with exact contiguous offsets. Decode only at completion and verify the decoded server ID/revision
against metadata. `receive()` returns the envelope and an optional `completedCatalog`; consumers
must publish only that completed catalog, never partially apply raw chunks. Completed models are
owned by the caller; catalog-store/cache bounds belong to later services.

## Limits and terminal failures

`ProtocolLimits` retains all step-5 byte/object/decoding ceilings. `TcpLimits.DEFAULT` adds:

| Resource | Default | Configurable hard maximum |
| --- | --- | --- |
| Accepted sockets including pending handshakes | 32 | 256 |
| Whole connection/handshake/confirmation attempt | 10 seconds | 10 seconds |
| Whole read/write, idle session, pending request/publication lifetime | 30 seconds | 300 seconds |
| Pending request UUIDs, separately per direction | 64 | 64 |
| Pending catalog publications, separately per direction | 64 | 64 |
| Retained accounting budget, separately per direction | 4 MiB | 16 MiB |
| Application envelopes, separately per direction | 65,536 | 1,048,576 |
| Configured admitted IDs | 4,096 | 4,096 |

The absolute read deadline covers all fragments of one frame, so dribbling bytes cannot extend it.
Write deadlines close blocked sockets too. Pending requests/publications have fixed start times;
other traffic does not renew them. Idle expiry needs no caller polling. The expiry sweep runs at
most one second after a pending/idle deadline (or once per timeout for shorter configured limits);
read/write/handshake deadlines are scheduled directly. Normal scheduler delays remain possible.

Each new operation reserves 192 accounting bytes for retained UUID/history entries, each pending
request 128, and each publication its declared total plus 256. Request/publication completion
releases its reservation; history remains until close. Accounting is conservative bookkeeping,
not exact JVM heap measurement. In addition to these retained budgets, each active direction uses
at most one 1-MiB frame, bounded 64-KiB record buffers, and the codec's per-operation allocation/object
budgets. Connection and worker ceilings bound simultaneous transient allocations. No application
outbound queue is maintained here. Reaching any ceiling fails closed; later reconnect policy must
handle normal session-history exhaustion.

REGISTER_SERVER, PREPARE_HANDOFF and CLAIM_HANDOFF open pending request UUIDs. Matching result
families, ERROR or CANCEL_HANDOFF close them in the opposite direction. Multi-phase handoff
semantics and unsolicited-response policy remain handler responsibilities.

Invalid framing, AEAD failure, replay, missing/changed fragments, malformed models, exhausted
budgets, deadlines and socket errors are terminal. Public I/O failures expose fixed diagnostic
strings without underlying exception messages, payloads or keys. Nothing retries another mode.

## Verification

On 2026-09-07, `./gradlew :common:test :proxy-common:test --max-workers=2 --console=plain`
passed 433 common tests and 11 proxy tests, with zero failures/errors/skips. Compilation uses the
Java 17 common toolchain. The 48 new cases include real localhost sockets in both modes,
fragmented catalog round trips, bidirectional correlation, concurrent writers/full duplex,
duplicate IDs and repeated disconnects, endpoint/mode rejection, wrong pins and unknown IDs,
absolute handshake timeout and connection saturation, malformed/oversized frames, invalid tags,
sequence/operation/ciphertext replay, changed transcripts, forbidden handshake payloads,
missing/invalid confirmation, early operations, idle/incomplete-frame expiry, stalled writers,
shutdown interrupting reads, catalog metadata/offset/replay validation and retained/request limits.

`./gradlew :velocity:build --max-workers=2 --console=plain` also passed. Inspection of the final
Velocity JAR confirmed that `NoiseRecordCipher` uses the private relocated handshake classes,
contains no original Noise package reference, and has Java 17 bytecode; the relocated dependency
is present and the original dependency namespace is absent. Whitespace checks passed for tracked
changes and all new files. The full backend artifact matrix was not rerun.

These are reusable-channel tests, not live Minecraft/Velocity feature validation. Step-3 native
classloader evidence remains historical. No pairing, reconnect service, command, remote catalog
store, player transfer or platform lifecycle has been enabled by this step.
