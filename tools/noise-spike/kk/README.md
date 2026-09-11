# KK dependency spike

Isolated evaluation of `org.signal.forks:noise-java:0.1.1` for
`Noise_KK_25519_AESGCM_SHA256`. See the [selection decision](../../../docs/features/cross-server/specs/cross-server-noise-dependency-decision.md).
This is a subproject of the standalone spike, not a root runtime/release dependency.
The parent NKpsk0 probe is preserved as historical evidence and is not a compatibility mode.

From the repository root:

```sh
./gradlew -p tools/noise-spike :kk:check --console=plain
./gradlew -p tools/noise-spike :kk:dependencies --configuration runtimeClasspath --console=plain
```

`check` without a project prefix runs both investigations. Java compilation, tests, and child JVMs
use Java 17; the process fixtures require POSIX temporary permissions (macOS/Linux).

## Reproducibility

- Published candidate: `org.signal.forks:noise-java:0.1.1`, 127,167 bytes, no runtime transitives.
- JAR SHA-256: `2bbc531e5e31b3151269dbb7596548e3c884ded217ab6312c2d87591bfea543b`.
  `verifyCandidate` verifies the coordinate, complete runtime dependency set, and cached JAR bytes
  before compilation. No upstream sources or cryptographic algorithms are modified.
- Published sources SHA-256: `f8ddea9f91659f8796e860ddeca51e90239b8cdb485b0a2ad69b3ffd20c86cba`.
  The source JAR was reviewed separately; every Java source matches Signal commit
  `49af72520c711a173e77cfaeba85d68b0d644d02`.
- Vectors: read the exact KK entry from the parent's SHA-256-pinned jchambers source archive,
  which carries the external Cacophony corpus. No candidate implementation is used to generate
  expected ciphertext. The historical candidate is not on the KK runtime classpath.
- Shadow 9.4.1 relocates the entire `com.southernstorm.noise` namespace to
  `_959.server_waypoint.internal.noisekk`; the MIT notice is retained as `META-INF/LICENSE-noise-java`.

## Checks

35 checks: eight candidate/harness cases and 27 packaging/process cases.

- External KK vector: exact bytes of two handshake and four transport messages and the final
  handshake hash, using both default AES/CTR-plus-GHASH and forced pure-Java fallback paths.
- Nonce boundary: last legal unsigned nonce succeeds in both directions; the reserved all-ones
  nonce rejects repeatedly without writing output. Both AES-GCM paths are tested.
- Invalid tags do not release plaintext; the harness closes failed sessions and disallows reuse.
- Harness enforces the 65,535-byte ciphertext / 65,519-byte plaintext budget. The raw cipher API
  does not enforce Noise record sizes for callers.
- Nonempty handshake payloads and transport before split are rejected.
- Replaying KK message one against a fresh responder cannot authenticate an old transport confirmation.
- 13 two-process scenarios, repeated unshaded and using only the relocated executable JAR:
  success; wrong coordinator pin; wrong backend key; unknown/revoked ID; transcript mismatch;
  tampered handshake request/response; tampered, operational, or missing initial confirmation;
  tampered transport; replayed transport. Success reconnects three times, exchanges empty, small,
  and maximum records in both directions, checks distinct handshake hashes, and closes cleanly.
- Packaging checks require relocated classes and the license, reject original/test dependency
  namespaces, and check every class file is Java 17 compatible.

Negative cases must fail at the expected side and phase. Timeouts, unexpected exceptions, or
admitted operations fail the run. In the missing-confirmation case, any coordinator bytes before
backend confirmation fail the run. Tests reap both child processes on failure; temporary credentials
are mode `0600` and contain only each endpoint's own private scalar plus its peer's public pin.
Private byte arrays and per-session Noise state are cleared on exit where the API allows it.
This does not guarantee erasure of all JVM/JCE copies.

## Boundary

`KkSession` and `KkPeer` are test harnesses, not production transport or public APIs. Their one-entry
registry, fresh nonce, preface encoding, confirmation strings, raw key fixture format, and four-byte
record length are disposable test infrastructure. Unknown/revoked ID tests exercise fixture policy,
not an implemented coordinator registry or live key-revocation service. KK handshake payloads are
empty in the process probe; the external vector test deliberately uses the vector's payloads.

The complete application preface, all negotiation fields, live revocation, canonical SPKI/PKCS#8
key import, pairing, concurrency, timeouts under load, and bounded application fragmentation remain
later plan work. No plaintext transport is implemented here. Final Paper/Fabric/Forge/NeoForge/
Velocity packaging and classloader tests belong to step 3.

Reports: `build/reports/tests/test/index.html`, `build/test-results/test/`.
Executable: `build/libs/server-waypoint-noise-kk-spike-relocated.jar`.
