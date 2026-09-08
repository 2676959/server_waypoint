# Pairing and credentials v1 (step 7)

Step 7 provides coordinator administrative pair/revoke/rotation operations, backend credential
storage and an authenticated bootstrap state machine. The shared credential/bootstrap helpers
live in `common/.../crossserver/pairing`; the coordinator admission registry belongs exclusively in
`proxy-common/.../proxy/pairing`. No platform command, bootstrap socket dispatcher or game lifecycle
starts these services yet. Step 8 supplies operational connection workers, registration and reconnect. The bootstrap network
dispatcher remains platform integration work.

## Bootstrap decision and review

The bootstrap authenticates public data using a randomly generated **256-bit one-time secret**
and JCE `HmacSHA256`. This is an application-specific challenge/confirmation protocol, not a new
cryptographic primitive or a claimed standard pairing protocol. It uses HMAC as defined by
[RFC 2104](https://www.rfc-editor.org/rfc/rfc2104), with separate phase/domain inputs and untruncated
32-byte tags. It does not reuse the historical NKpsk0 experiment or add a Noise suite/fallback.

The coordinator generates each code using `SecureRandom`; its canonical external form is 43
unpadded Base64url characters. This is not a short numeric code or human password protocol. Only
`exportCode()` deliberately reveals it. The administrator must deliver the ticket, code and
intended backend ID through an authenticated confidential administrative path. Neither the code nor
any private key is sent through the bootstrap carrier, written into ordinary config, or retained
in final transport configuration. A code holder has authority to select keys for that invitation's
ID; protecting that administrative delivery is part of the threat model.

The carrier can be untrusted because it carries only authenticated public keys, nonces and proofs.
It does not provide confidentiality for those public fields. Do not confuse this dedicated
bootstrap with operational `PLAINTEXT`: it cannot admit operational requests or bypass KK. Once
paired, only normal KK proves possession of the installed private keys and opens an authenticated
operational channel.

Source review traced every pin write back to verification and checked the following attack paths:

| Attempt | Enforcement |
| --- | --- |
| Substitute ID, suite/version, ticket or backend key | Backend request HMAC covers the complete request body; ticket lookup also binds the exact configured ID. |
| Substitute coordinator key or either challenge | Response proof covers the complete request and response body, including both public keys and nonces. |
| Reflect a proof into another protocol phase | HMAC domain and phase bytes differ for all four proofs. |
| Replay an old request/confirmation | Ticket is random, expires, is single-use and has at most one active transcript; new invitations replace old ones for that ID. |
| Concurrent completion | Coordinator monitor serializes verification, ticket consumption and durable pin mutation. Exactly one completion wins. |
| Revoke/rotate during bootstrap | Revocation cancels matching invitations; coordinator rotation cancels all. Backend completion checks that its own key and expected old pin remain unchanged. |
| Guess a short password offline | No password-based mode is offered; coordinator codes contain 32 random bytes. |
| Install early or through a forged acknowledgement | Coordinator installs only after phase 3; backend installs only after phase 4. |
| Reuse a static key for another backend | Registry rejects public-key duplication across IDs and reuse of the coordinator key. |

The reviewed construction is supported by adversarial executable tests and standard JCE primitives;
this is not an independent cryptographic audit or formal security proof. An active attacker can
still disrupt delivery or invalidate a pending attempt. A compromised code or administrative host
can authorize a malicious pin. Application permissions and player identity checks remain later work.

## Exact bootstrap encoding

Integers are fixed-width big-endian. No trailing bytes, alternate encodings or variable extensions
are accepted. Carrier framing is external to these bounded message APIs; reject oversized declared
lengths before allocating them. The maximum request is 204 bytes, response 108 bytes, and each final
proof is exactly 32 bytes. The APIs accept no operational envelopes.

`RequestBody` consists of magic int `0x53575050` (`SWPP`), bootstrap-version int 1, intended KK-suite
int 1, ticket UUID (two longs), server-ID int byte length and exact ASCII bytes (1–64), 44-byte
canonical backend SPKI, and 32 random backend nonce bytes. `Request` is the body followed by
`MAC(1, RequestBody)` and is exactly `140 + serverIdLength` bytes.

`ResponseBody` consists of 44-byte canonical coordinator SPKI and 32 independently random
coordinator nonce bytes. `Transcript = Request || ResponseBody`. `Response` is the response body
followed by `MAC(2, Transcript)`. Backend confirmation is `MAC(3, Transcript)`; the coordinator's
post-installation acknowledgement is `MAC(4, Transcript)`.

`MAC(phase, bytes)` is HMAC-SHA-256 keyed by the decoded 32-byte code, over exact ASCII
`ServerWaypoint pairing v1`, the single phase byte, then `bytes`. The request contains a length for
its sole variable field and the response body has a fixed length, so concatenation is unambiguous.
Proof comparisons use `MessageDigest.isEqual` after exact-length validation. Cryptographic byte
arrays and messages are not exposed through object `toString()` methods.

## Administrative API and failure ordering

- `PairingCoordinator.pair(id, expectedPin, lifetimeMillis)` requires the exact existing backend
  pin, or null for a new backend. It returns an explicitly closable invitation containing a public
  ticket and redacted code object. Issuing another invitation for an ID invalidates its old one.
- `BackendPairing` receives the invitation, local credential owner and expected old coordinator
  pin (null for first pairing). `request()`, `confirm(response)`, and `finish(ack)` advance one
  attempt. The caller closes attempts/invitations on completion, timeout or carrier failure.
- `begin(request)` verifies phase 1 and reserves one transcript; it installs nothing.
  `complete(ticket, confirmation)` verifies phase 3, consumes the ticket, atomically persists the
  backend public pin, updates live admission, then returns phase 4. `finish()` verifies that
  acknowledgement and only then replaces the backend coordinator pin using an expected-pin check.
- Coordinator expiry uses monotonic time and a one-second daemon cleanup sweep. At most 64
  invitations/transcripts are retained, each for at most five minutes; eight failed begin attempts
  retire a ticket. A failed completion also retires it. The backend has its own maximum five-minute
  deadline, checked at every phase, and must be closed by its carrier owner if abandoned.
- Persistence failure consumes the invitation and stops the coordinator/listener because the
  durable result can be uncertain. No acknowledgement is sent. A fresh administrative pairing
  operation is required. A lost acknowledgement can leave only the coordinator updated; inspect
  its current pin and issue a new invitation with that expected pin. Neither side rolls back to an
  unauthenticated or previous transport mode. Cross-host atomic commit is not claimed.

`revoke(id)` first persists removal of the ID, then cancels its invitations and updates the live
listener. `TcpCoordinator.replacePin` invalidates established and pending sessions and checks pin
generation again before exposing a newly authenticated channel. Reconnecting with the removed pin
fails. Other backend IDs and live sessions remain active. No handoff store exists yet; the future
handoff owner must add reservation cancellation to its revocation integration.

For backend rotation, administratively revoke its old registration, call `LocalCredentials.rotate()`
on the backend, then issue a fresh invitation and pair. Explicit expected-pin replacement also
supports a controlled transition: the old pin remains admitted until authenticated completion
replaces it. `rotateCoordinator()` persists an empty registry, cancels every invitation, closes the
listener and replaces its private key. All backends must explicitly re-pair with the new code;
their old coordinator pins are not silently overwritten. Pairing/rotation APIs are administrative
operations; command authorization and operator UI are not registered in this step.

## Credential storage and modes

Public keys are canonical padded Base64 of RFC 8410 X25519 SubjectPublicKeyInfo: the exact 12-byte
DER prefix `302a300506032b656e032100` followed by 32 public-coordinate bytes. Import rejects alternate
DER, wrong algorithms/parameters, whitespace, malformed Base64, noncanonical coordinates and
small-order public inputs. Private keys are binary minimal PKCS#8: the exact 16-byte DER prefix
`302e020100300506032b656e04220420` plus the 32-byte scalar. See
[RFC 8410](https://www.rfc-editor.org/rfc/rfc8410). JDK X25519 generates and derives keys; the existing
Noise transport receives defensive raw-key copies. No third-party dependency was added.

`CredentialFiles` requires a real directory path with an existing parent and rejects symlink
ancestors/entries. It creates or restricts POSIX directories to `0700` and files to `0600`, takes an
exclusive file lock for one credential owner, bounds reads, and writes through an owner-only
same-directory temporary file, file fsync, atomic rename and POSIX directory fsync. There is no
non-atomic write fallback. Close the store after all its services stop. On non-POSIX filesystems,
permission enforcement is limited to the host's filesystem policy; atomic move and locking still
must succeed. JVM/provider copies cannot be guaranteed erased; owned secret arrays are wiped on
close and temporary private arrays after use.

Files are `static.key` (private PKCS#8), `coordinator.pin` (backend public pin),
`backend-pins.json` (coordinator-only ID/public-pin registry, GSON), and `.owner.lock` (no secret
content). Malformed credentials fail closed. A missing private key with existing paired pins is
never silently regenerated. Initial startup without pins generates one unique key and later
loads it unchanged. Registry reads are bounded to 1 MiB and 4,096 entries and reject duplicates,
invalid keys and malformed JSON. Pairing codes and tickets are never persisted; restart invalidates
all pending invitations.

`CredentialFiles.forTransport` validates the selected mode's endpoint first. Explicit plaintext
returns no store and does not touch the credential directory; loopback restrictions still apply.
KK opens the credential owner. Operational defaults remain KK, with the feature disabled until
later lifecycle configuration. `PairingCoordinator.listen()` is an explicit factory for its owned
KK listener; constructing a coordinator never opens a socket or registers commands.

## Verification

Java 17 common/proxy compilation and tests pass. Evidence includes the
[RFC 7748 X25519 public derivation vector](https://www.rfc-editor.org/rfc/rfc7748#section-6.1),
an independently generated Python HMAC fixture, canonical-import negatives, persistent and unique
keys, POSIX permissions, exclusive ownership, symlink/corrupt/missing-key rejection, plaintext
without credential side effects, tampered transcript fields/proofs, wrong/expired/reused codes,
concurrent completion, changed local keys, explicit rotation, failed persistence and captured
console/exception/toString secret scans. Real KK sockets use newly paired keys, exercise pending
handshake revocation, reject revoked reconnects and preserve another backend's live session.

See the [progress record](cross-server-waypoint-teleportation-progress.md) for exact test totals
and build evidence. These tests do not validate platform commands, a network bootstrap dispatcher,
reconnect scheduling or live game/proxy integration. Those are later plan steps.
