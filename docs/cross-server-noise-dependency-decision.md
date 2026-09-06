# Noise dependency selection: step 2

## Historical scope after the KK decision

The plan now selects `NOISE_KK` by default and explicit loopback-only `PLAINTEXT` as an option.
The investigation below records the earlier NKpsk0 requirement and has not been rerun for KK.
Noise-Java/Signal pattern incompatibility with NKpsk0 no longer disqualifies them for the revised
plan. KK source review, vectors, rejection cases, and dependency approval are pending; the known
jchambers nonce result remains specific evidence about that candidate. Do not interpret the old
no-go as a completed KK evaluation. Final platform artifact/classloader checks now belong to
step 3 after its modules exist; step 2 retains selection and standalone relocation checks.

Decision date: 2026-09-06. **No-go for production adoption.** The isolated spike works, but no
evaluated implementation satisfies the maintained, reviewed, exact-suite dependency gate as-is.
Step 2 remains blocked at selection and platform integration approval. Do not advance the secure
transport implementation using this candidate. This records the plan's explicit stop condition;
it does not change the v1 suite or substitute a different cryptographic protocol.

## Candidates and source review

| Candidate | Java / license / dependencies | Required suite and decision |
| --- | --- | --- |
| [rweather/noise-java](https://github.com/rweather/noise-java/tree/49377b6dfc6a1e75740bce2318118291a57c0d6e) | Java 8 source target; MIT; no runtime dependencies in its POM. Latest inspected commit: 2022-08-03. No release binary selected or size measured. | Provides AES-GCM but its pattern table lacks `NKpsk0`; PSKs use the older `NoisePSK` prefix. Rejected without modifying its cryptography. |
| [Signal fork](https://github.com/signalapp/noise-java/tree/49af72520c711a173e77cfaeba85d68b0d644d02) / `org.signal.forks:noise-java:0.1.1` | Java 8 source target; MIT; no runtime dependencies. Published JAR: 127,167 bytes. Latest inspected source commit: 2024-02-29. | Retains the older pattern handling. The spike tests the published artifact and proves construction of `Noise_NKpsk0_25519_AESGCM_SHA256` throws. Rejected. |
| [jchambers/java-noise](https://github.com/jchambers/java-noise/tree/4de5aefbf2bb19bf2efcfbe97d3d13231e693488) | Java 17; MIT; `jsr305:3.0.2` is the only runtime dependency (19,936 bytes). Locally compiled candidate: 78,169 bytes. Latest inspected commit: 2024-09-07. | Exact suite and Cacophony vector pass. Selected only for the isolated experiment; rejected for production adoption as-is for the reasons below. |

The upstream heads were checked live for this investigation; the dates are evidence of the
observed activity, not a claim that the authors have permanently abandoned their projects.
No currently maintained exact-suite release was established by this search. The Signal/original
AES-GCM implementation includes its own CTR/GHASH/fallback code. Their stateful handshake and cipher
objects have mutable unsynchronized state and should not be shared concurrently; exact-suite
incompatibility already disqualifies them, so no further security audit is claimed.

The jchambers review covered its builder/pattern parsing, PSK handling, `NoiseHandshake` split,
`CipherState`, `NoiseTransportImpl`, JCE AES-GCM and X25519 components, POM, license, and vector
tests. This was a scoped source review and executable experiment, not an independent security
audit. Its [upstream status](https://github.com/jchambers/java-noise/tree/4de5aefbf2bb19bf2efcfbe97d3d13231e693488#license-and-status)
explicitly describes it as unpublished and not independently audited.

## Findings that prevent adoption as-is

1. **Nonce exhaustion is not fail-closed.** `CipherState` increments a Java `long` without checking
   the reserved all-ones value. The isolated characterization test sets that boundary and observes
   encryption followed by wrap to zero. This is documented upstream, not a newly inferred exploit.
   It conflicts with the plan's exhausted-sequence rejection requirement. A reviewed correction
   or an explicitly reviewed outer lifetime/message budget is required before adoption; the spike
   does not patch the dependency or silently treat the gap as safe because it is unlikely.
2. **No maintained release/security-review basis was established for the exact-suite candidate.**
   Pinning source and passing vectors gives reproducibility and interoperability evidence, not
   maintenance ownership or a comprehensive security assessment. There is no selected released
   Maven coordinate for this candidate. Shipping a source-built fork would require an explicit
   dependency ownership and review decision beyond this spike.

Additional integration constraints from source inspection:

- Handshake and transport instances are explicitly not thread-safe. Split reader/writer states
  share a mutable cipher component; separate direction counters do not permit simultaneous calls.
  Future integration must serialize all operations on one session or use a reviewed alternative.
  The experiment keeps each peer session on one thread and each connection gets fresh state.
- The implementation retains PSK arrays and exposes no explicit transport destruction API.
  Clean socket/process exit is verified; guaranteed erasure of all JVM/JCE key copies is not.
- The [Noise specification](https://noiseprotocol.org/noise.html#message-format) and candidate cap
  each Noise record at 65,535 bytes, leaving 65,519 bytes of transport plaintext with a 16-byte tag.
  The plan's 1 MiB application budget and 256 KiB catalog chunks cannot be single Noise records.
  Step 6 must specify bounded fragmentation/reassembly above Noise or revise those budgets;
  increasing the library's record limit is not part of this experiment.
- The feature's symbolic suite ID `NOISE_NKPSK0_25519_AESGCM_SHA256` maps to the case-sensitive
  Noise protocol name `Noise_NKpsk0_25519_AESGCM_SHA256`. This is a naming distinction, not a fallback.

## Executable evidence

Run the [standalone spike](../tools/noise-spike/README.md):

```sh
./gradlew -p tools/noise-spike check --console=plain
```

Verified on macOS arm64 with OpenJDK 17.0.19 for compilation/test/child JVMs and the repository's
Gradle 9.5.1 wrapper. All 18 tests pass: five candidate characterizations and thirteen packaging/
process tests. Twelve process scenarios launch two distinct Java processes each. Both unshaded
and relocated success scenarios exchange encrypted data in both directions over three connections;
all ten negative process scenarios fail closed with the expected rejection/EOF behavior. Both
peers exit normally and the listener is closed. The matching external vector proves exact bytes
and handshake hash, rather than relying only on two copies of one implementation agreeing.

The source archive is pinned to commit `4de5aefbf2bb19bf2efcfbe97d3d13231e693488` and SHA-256
`e31e460651048e2f53a28d88da9ba417dbb9ba3da45bebd3335e5357eaaa4205`.
`dependencies --configuration runtimeClasspath` reports only `candidate -> jsr305:3.0.2`.
GSON, JUnit, and the Signal comparison artifact are test-only. Reports are regenerated under
`tools/noise-spike/build/`; no credentials or generated binaries are committed.

## Relocation and platform packaging boundary

The standalone spike uses the repository's Shadow 9.4.1 version. It relocates `com.eatthepath.noise`
and `javax.annotation` to private `_959.server_waypoint.internal` packages, retains the upstream
MIT notice and the JSR305 Apache 2.0 license, checks all class files are Java 17 compatible, and reruns the full two-process matrix
using only the shaded executable JAR. No native code, extra JCE provider registration, or platform
API is needed by this tested suite.

This proves standalone relocation, **not final platform artifacts or live platform loading**.
The following repository packaging routes were inspected; none was changed to ship a rejected
dependency:

| Platform | Inspected integration requirement | Remaining verification after a dependency passes selection |
| --- | --- | --- |
| Paper | `paper/build.gradle.kts` currently excludes dependencies other than `common` and bStats. Noise must be explicitly retained and relocated. | Build/audit each supported final plugin JAR and exercise its classloader. |
| Fabric | Both Fabric scripts include only `common` and Adventure in Shadow. Regular Fabric feeds the shadow JAR through `remapJar`; unobfuscated Fabric publishes the shadow output. | Add the selected dependency to the filter, inspect final remapped/unobfuscated JARs and run the probe. |
| Forge | `shadedDependencies` and Shadow filters must retain Noise; the shadow archive is merged into `shadowJarJar` and older targets use reobfuscation. | Verify the final jarjar/reobfuscated output retains relocated classes and notices. |
| NeoForge | Both ModDev and legacy NeoGradle scripts filter shaded dependencies. | Verify both build routes and their final artifacts. |
| Velocity | No Velocity subproject exists yet (step 3 owns its creation). | Add private relocation when that adapter exists and verify its final plugin/classloader. |

Platform builds, actual server/proxy startup, cross-loader interoperability, and a production
connection lifecycle are **not performed or signed off** by step 2's no-go experiment. Changing
the root builds or creating Velocity prematurely would bypass the selection stop condition.

## Reopening the gate

Identify a maintained exact-suite release or explicitly take ownership of a pinned fork; obtain
review of its handshake/key lifecycle and nonce handling; replace the defect characterization with
a fail-closed assertion for the corrected candidate. Then rerun the vectors and process matrix and
complete the platform artifact/classloader checks above. Only then mark step 2 complete and adopt
the dependency. No alternative suite, hand-written cryptography, or backward-compatibility format
was introduced by this investigation.
