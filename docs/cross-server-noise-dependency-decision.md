# Noise dependency selection: step 2

Decision date: 2026-09-06. **Select `org.signal.forks:noise-java:0.1.1` for
`Noise_KK_25519_AESGCM_SHA256`. Step 2 is complete; step 3's module and final
platform packaging/classloader checks also pass (see its validation record).** This is dependency selection after a scoped source review
and executable verification, not an independent cryptographic audit or production release approval.
No production dependency or transport has been added by this step.

The [historical NKpsk0 investigation](cross-server-noise-nkpsk0-investigation.md) and its unchanged
18-test probe remain available. Its no-go still applies to those candidates under that old suite.
NKpsk0 is not an advertised suite, fallback, or compatibility mode. Explicit loopback plaintext
remains later work and did not bypass this KK evaluation.

## Candidate decision and maintenance

| Candidate | Current KK assessment | Decision |
| --- | --- | --- |
| [Signal Noise-Java](https://github.com/signalapp/noise-java/tree/49af72520c711a173e77cfaeba85d68b0d644d02), `org.signal.forks:noise-java:0.1.1` | Released Maven artifact, MIT, Java 8 bytecode usable on Java 17, no runtime transitives. Exact KK vectors, both AES-GCM nonce guards, session failures, and standalone relocation pass. | Selected, subject to the integration requirements below. No local crypto fork or patch. |
| [rweather/noise-java](https://github.com/rweather/noise-java/tree/49377b6dfc6a1e75740bce2318118291a57c0d6e) | KK is present; the old lack of NKpsk0 does not disqualify it. Original implementation from which Signal's artifact is derived. | Prefer the published Signal coordinate over introducing a source-built copy of the original. No separate original binary was tested. |
| [jchambers/java-noise](https://github.com/jchambers/java-noise/tree/4de5aefbf2bb19bf2efcfbe97d3d13231e693488) | KK is supported, but the previously reproduced unguarded cipher nonce increment is shared across suites. Source remains unpublished and explicitly unaudited. | Not selected; the historical nonce characterization remains a rejection reason. No new KK process claim is made for this candidate. |

Upstream HEADs were rechecked live and still match the commits linked above. Signal's repository
is not archived or disabled; its latest inspected commit is 2024-02-29. The original's is 2022-08-03
and jchambers' is 2024-09-07. Signal describes its fork as providing Maven releases and offering
substantive changes upstream. These are sparse maintenance histories, not evidence of a recent
release cadence, promised security response, or an independent audit.

The selection accepts sparse upstream activity as a dependency risk: use the existing published
release with a pinned hash and this reproducible review, rather than taking ownership of an
unpublished crypto fork. Recheck upstream changes/advisories before release and review any upgrade
with this same suite. No ongoing-maintenance guarantee is inferred from the Signal name.

## Artifact and review provenance

- Runtime coordinate: `org.signal.forks:noise-java:0.1.1`, from Maven Central, 127,167 bytes.
- Runtime SHA-256: `2bbc531e5e31b3151269dbb7596548e3c884ded217ab6312c2d87591bfea543b`.
- [Published sources](https://repo.maven.apache.org/maven2/org/signal/forks/noise-java/0.1.1/noise-java-0.1.1-sources.jar)
  SHA-256: `f8ddea9f91659f8796e860ddeca51e90239b8cdb485b0a2ad69b3ffd20c86cba`.
  All published Java source files match inspected Signal HEAD byte-for-byte.
- The KK Gradle build verifies the artifact coordinate, sole runtime dependency, and SHA-256 before
  compilation. GSON/JUnit are test-only; the historical jchambers candidate is absent from KK runtime.
- The external Cacophony KK vector is read from the parent's pinned source archive (commit
  `4de5aefbf2bb19bf2efcfbe97d3d13231e693488`, archive SHA-256
  `e31e460651048e2f53a28d88da9ba417dbb9ba3da45bebd3335e5357eaaa4205`). It supplies expected ciphertext
  and handshake hash independently of Signal's implementation.

Review covered `Pattern`, `HandshakeState`, `SymmetricState`, `CipherStatePair`, `Noise`,
`Curve25519DHState`, both AES-GCM cipher states, dependency metadata, and lifecycle/error call paths.
It did not constitute a formal proof, side-channel audit, or audit of unused suites/primitives.

## Findings and mandatory integration requirements

1. **Exact KK behavior.** Both static public keys are mixed as pre-messages; the pattern processes
   `e, es, ss` then `e, ee, se`. Both handshake payloads must remain empty for this feature. Wrong
   backend key or coordinator pin rejects at the responder's first read in the probe. A previously
   valid first message can be replayed, so successful handshake processing alone must not admit
   operations or release catalogs. Require the backend's encrypted transport confirmation using the
   fresh session keys. The old-confirmation replay test verifies this distinction.
2. **Nonce exhaustion fails closed.** Both `AESGCMOnCtrCipherState` and `AESGCMFallbackCipherState`
   reject unsigned `2^64 - 1` before writing output. Tests use the last permitted nonce, then verify
   repeated encryption/decryption rejection. Production must never expose `setNonce`, reset a live
   cipher, or reuse old cipher state; close and reconnect before configured session/message budgets.
3. **Caller owns terminal failures and limits.** A raw cipher is not a session state machine and
   does not enforce the Noise message-size cap. Validate lengths before allocation, cap encrypted
   records at 65,535 bytes / plaintext at 65,519, and discard the whole session on tag, framing,
   timeout, sequence, or confirmation failure. Never retry a failed record on the same state.
   Larger application frames require bounded fragmentation in step 6. The harness enforces these
   rules; it is not the production codec or transport.
4. **Single session owner.** Handshake/cipher instances are mutable and unsynchronized. Split makes
   separate send/receive ciphers, but this review does not authorize concurrent use of a session.
   Serialize all crypto and lifecycle actions per session; allocate fresh state per connection.
   Do not expose mutable DH/cipher objects, `fallback()`, or the global `setForceFallbacks` test knob.
5. **Key lifecycle.** Static private bytes are copied into per-handshake DH objects. DH generation
   uses `SecureRandom`; ephemeral keys are newly generated for each handshake. Copy the handshake
   hash before destroying handshake state immediately after split. Destroy both transport ciphers
   on every exit. The harness guards calls after close because raw `destroy()` is not itself an
   application terminal-state guard. Array clearing and JCE reinitialization are best effort, not
   guaranteed erasure of all JVM/provider copies. Step 6/7 must implement the contract's canonical
   SPKI public/PKCS#8 private import and validate pins; raw scalar fixtures are only test inputs.
6. **Cryptographic implementation boundary.** This release uses its own Curve25519 and GHASH code;
   AES-GCM normally combines JCE AES/CTR with GHASH, with a pure-Java AES fallback. It does not use
   JDK X25519 or JCE AES/GCM directly. Both AES-GCM paths match the KK vector and nonce assertions;
   this is not a constant-time certification. No native library or provider registration is needed.
   Invalid X25519 DH inputs may yield zeros; this is permitted by the
   [Noise 25519 definition](https://noiseprotocol.org/noise.html#the-25519-dh-functions), and must not
   be confused with accepting arbitrary configured static pins or authenticating early messages.
7. **Identity and transcript policy remain application responsibilities.** The fixture rejects
   unknown/disabled IDs before creating a handshake and binds its ID, fixed mode/version/suite,
   and fresh coordinator nonce into the prologue. Full negotiation binding, duplicate IDs, live
   revocation, and configuration policy remain steps 6–8; no deployed registry or pairing system
   is claimed by the spike.

## Executable evidence

```sh
./gradlew -p tools/noise-spike :kk:check --console=plain
./gradlew -p tools/noise-spike check :kk:dependencies --configuration runtimeClasspath --console=plain
```

Freshly verified on macOS arm64, OpenJDK 17.0.19 for compile/test/child JVMs, Gradle 9.5.1.
All **35 KK checks** pass: eight candidate/harness cases plus 27 packaging/process cases.
The combined command also reran all **18 historical NKpsk0 checks**, including its explicit
known-defect characterization; these are separate results, not 53 KK acceptance checks.

The 26 KK process cases each launch two child JVMs. Success in both packaging forms uses three
successive connections, fresh handshake hashes, bidirectional empty/small/maximum records, and
clean shutdown. Negative scenarios cover wrong keys, unknown/revoked fixture IDs, transcript
mismatch, tampered handshake/confirmation/transport, missing or operational initial confirmation,
and replayed transport. A wrong-side or wrong-phase exception, timeout, or admitted application
traffic fails the test. The unit replay test checks a repeated first handshake against a fresh
responder and rejects the previous session's confirmation.

See [KK spike README](../tools/noise-spike/kk/README.md) for report paths, test boundaries, fixture
permissions, child cleanup, and commands. Whitespace checks pass. No root platform build was
necessary or run for this isolated dependency change.

## Standalone relocation and step-3 handoff

Shadow 9.4.1 relocates `com.southernstorm.noise` to `_959.server_waypoint.internal.noisekk` and retains
`META-INF/LICENSE-noise-java`. The executable JAR runs without original Noise classes and excludes
historical/test dependencies. All class files are Java 17 compatible.

The platform routes below were rechecked in this worktree. The implementation and results now live in
[step-3 platform validation](cross-server-step3-validation.md), before production transport work.

| Platform | Required packaging integration and check |
| --- | --- |
| Paper | Retains common/bStats and the selected Noise coordinate, with private relocation and license; checks all three final plugin artifacts/classloaders. |
| Fabric | Both regular and unobfuscated scripts retain Noise alongside common/Adventure; checks cover regular `remapJar` and unobfuscated shadow outputs. |
| Forge | Retains Noise through Shadow, then `shadedJar`/`shadedJarJar` consume the completed relocated archive. Older targets reobfuscate the result with vanilla and generated Mixin member mappings. |
| NeoForge | Both ModDev and legacy NeoGradle retain/relocate Noise; checks include both final packaging routes. |
| Velocity | The new inert plugin shades common, proxy-common, and relocated Noise, excluding proxy API/runtime libraries. |

Use one private relocation namespace consistently across adapters. Do not put the rejected
NKpsk0 candidate or test harness classes into production artifacts. Step 3 creates only the planned
modules/interfaces and packaging probes; production transport remains step 6.
