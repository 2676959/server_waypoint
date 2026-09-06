# Noise dependency spike

Historical NKpsk0 investigation only. The revised plan selects KK; this spike has not been
converted or rerun for KK and its passing tests do not validate the new design. **No production dependency is selected; the adoption gate is blocked.**
See the [selection decision](../../docs/cross-server-noise-dependency-decision.md).
This standalone Gradle build is not included in the repository's root settings or release tasks.

From the repository root:

```sh
./gradlew -p tools/noise-spike check --console=plain
./gradlew -p tools/noise-spike dependencies --configuration runtimeClasspath --console=plain
```

The repository wrapper runs Gradle; Java compilation, tests, and both child JVMs use a Java 17
toolchain. A locally discoverable Java 17 JDK is required. The process tests use POSIX temporary
file permissions (macOS/Linux). Downloads require Maven Central, the Gradle plugin portal, and
GitHub. The candidate source archive is pinned to a commit and SHA-256 in `build.gradle.kts`.
Archive verification occurs before extraction/compilation, including for cached downloads.
No upstream source is patched or committed here.

`candidate` compiles the pinned upstream implementation separately. `shadowJar` relocates it and
its annotation dependency into private packages. Tests execute two child JVMs for each scenario,
first on the normal runtime classpath, then using only the relocated executable JAR:

- Successful handshake, authenticated traffic in both directions, three connections using the
  same test credentials and fresh ephemeral state, and clean EOF/process shutdown.
- Wrong PSK, wrong pinned coordinator key, mismatched prologue, tampered initial handshake, and
  tampered response. The authenticating side must reject the tag, and its peer must see closure;
  a timeout, unrelated exception, or admitted application traffic fails the test.
- Empty, small, and maximum-size Noise plaintexts in both directions.

Other tests check the exact upstream Cacophony vector (both handshake records, four transport
records, and handshake hash), tamper/replay rejection, record limits, Java 17 class versions,
relocation, and absence of test dependencies from the executable JAR. A Signal-release test proves
that its pattern parser rejects the required suite.

`recordsKnownNonceExhaustionGapInCandidate` is deliberately a **characterization of a defect**:
it succeeds when the pinned candidate exhibits the documented missing guard. Green tests therefore
mean the investigation is reproducible, not that the candidate is approved for deployment.

Reports: `build/reports/tests/test/index.html` and `build/test-results/test/`.
Executable probe: `build/libs/server-waypoint-noise-spike-relocated.jar`.
Candidate JAR: `candidate/build/libs/candidate.jar`.

The probe binds only to loopback, uses bounded records and socket/process timeouts, and creates
fresh disposable credentials in JUnit temporary files with mode `0600`. The client fixture contains
only the public pin and PSK. Normal output contains scenario results, never credentials. Tests
delete their temporary files and forcibly reap children on failure. The four-byte length prefix,
fixture encoding, fixed prologue, and session orchestration are test infrastructure, not the v1
wire protocol, pairing system, or step-6 production framing.

The downloaded upstream sources, vectors, and license remain in ignored `build/upstream/`.
The candidate JAR and relocated JAR retain the upstream MIT notice as `META-INF/LICENSE-java-noise`.
The relocated JAR includes `META-INF/LICENSE-jsr305` (Apache 2.0, as declared by the JSR305 POM).
The published vector's original attribution is documented upstream; the full pinned corpus is
read directly without copying it into this repository.
