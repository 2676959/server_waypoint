# Step 3 platform validation

Validation date: 2026-09-06, macOS arm64, Gradle 9.5.1.

**Step 3 is complete: the full build, 41 artifact checks, and all 10 native runtime cases pass.**
Source baseline after rebase: `fd3ba53`, including upload fix `f0d8281`.

## Scope

The module contracts and inert Velocity plugin are documented in
[proxy module contracts](cross-server-proxy-module-contracts.md). There is no production
cross-server listener, connection, catalog, pairing flow, or player transfer.

Every final target archive is checked in a fresh staging directory. The audit verifies unique
ZIP entries, retained shared classes, private Noise relocation, its exact license, no original
Noise/test/probe classes, the selected Java 17 Noise bytecode, and platform-specific plugin
metadata/dependencies. The shared module JARs are checked for Java 17 bytecode and absence of
Minecraft, Bukkit, Velocity, or Bungee API references.

Each final archive also runs an isolated JDK-parent classloader probe: exact KK handshake,
matching transcript hashes, bidirectional empty/31-byte/maximum-size records, replay rejection,
and repeated fail-closed reserved-nonce encryption checks. Step 2 retains the broader vectors,
decryption-boundary, malformed, identity, confirmation, and reconnect evidence.

The runtime probe observes the real plugin/mod entrypoint through a Java agent, then loads the
relocated Noise classes through that entrypoint's native platform classloader. It does not
transform game classes or invoke plugin services. Success requires both cryptographic proof
and server readiness followed by normal shutdown with exit code 0.

## Artifact matrix

41 final artifacts: 13 Fabric, 12 Forge, 12 NeoForge, 3 Paper, 1 Velocity. This includes the two
26.2 Fabric/NeoForge development targets plus Velocity; the existing backend release collector
still selects 38 artifacts. See [artifact hashes](validation/cross-server-step3/artifacts.json).

```sh
./gradlew :common:test :proxy-common:build :velocity:build --max-workers=2 --console=plain
./gradlew build --max-workers=2 --console=plain
python3 tools/noise-platform-test/audit.py --java17-home /opt/homebrew/opt/openjdk@17 --output /private/tmp/server-waypoint-step3-artifact-verified
python3 tools/noise-platform-test/run_live.py /private/tmp/server-waypoint-step3-runtime-oxmlwttn/rebased-manifest.json
```

The [tool README](../../tools/noise-platform-test/README.md) describes the explicit disposable-runtime
manifest. Every server uses a fresh world/configuration on loopback; user map data is never copied.
Runtime logs, exact command lines, artifact hashes, and native classloader/code-source evidence
are retained in [runtime results](validation/cross-server-step3/runtime-results.json).

## Native runtime matrix

| Platform | Runtime | Java |
| --- | --- | --- |
| Paper | 1.21 build 130 | 21 |
| Paper | 1.21.11 build 111 | 25 |
| Paper | 26.2 build 24 | 25 |
| Velocity | 4.1.1 build 24 | 25 |
| Fabric | 1.20.1, loader 0.17.3, API 0.92.6+1.20.1 | 17 |
| Fabric | 26.2, loader 0.19.3, API 0.152.1+26.2 | 25 |
| Forge | 1.20.1, 47.4.20 | 17 |
| Forge | 26.2, 65.0.0 | 25 |
| NeoForge | 1.20.2, 20.2.93 | 17 |
| NeoForge | 26.2, 26.2.0.3-beta | 25 |

Java patch versions: 17.0.19, 21.0.11, 25.0.3. Runtime distribution SHA-256 values are recorded
alongside the final artifact hashes. Velocity's API dependency resolves to
`4.1.1-SNAPSHOT:20260826.101250-1`; its runtime JAR SHA-256 is
`846411d2d0560fed0f23496ffb89681be528d2c0650ecdcf21724d2d7bd9c1ee`.

## Initial blockers and upstream repair

The first run passed 8/10 complete startup/probe/shutdown cases. NeoForge 26.2 passed the native
Noise probe but failed duplicate payload registration; Forge 1.20.1 failed a cartography Mixin
before the entrypoint loaded. Both failures reproduced without the observer on the committed
step-2 baseline `ca167a1`, and on `feature/upload-3.1.0` at `32d4498`.

At the user's direction, the defects were repaired on the upload branch before rebasing the
cross-server branch. See [upstream repair evidence](../upload/upload-startup-fixes.md). These runtime
fixes are separate from step 3's module and packaging implementation.

The post-rebase full build passed (563 tasks, 2m 38s), with 316 common tests and 11 proxy contract
tests passing without failures or skips. The final contract tests were rerun after strengthening
the duplicate-callback cancellation fixture; all 11 still pass. No production code changed in
that final test-only refinement. See [build result](validation/cross-server-step3/build-result.json),
[test counts](validation/cross-server-step3/test-summary.json), and
[final contract run](validation/cross-server-step3/contract-tests-final.txt).

All 41 freshly built artifacts passed the audit. All 10 listed native runtime cases passed their
classloader crypto probe, server readiness, and clean shutdown. The exact live mod/plugin SHA-256
values match the final audited artifacts. The Fabric setups initially omitted Fabric API and the
Forge 26.2 setup omitted its shim launcher; after restoring those runtime dependencies, the three
cases passed with unchanged mod artifacts. [Setup retry logs](validation/cross-server-step3/setup-retries.json)
are retained separately from source failures.

Retained console text normalizes trailing whitespace and tabs; raw and retained SHA-256 values
are recorded separately. [Initial runtime results](validation/cross-server-step3/initial-runtime-results.json)
and [step-2 baseline comparisons](validation/cross-server-step3/baseline-results.json) preserve the
original blockers without treating them as the final outcome.

This closes the step-3 packaging/classloader gate. Production transport and all steps 4–19 remain
unimplemented; these results are not end-to-end cross-server or release approval.
