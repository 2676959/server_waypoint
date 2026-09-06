# Cross-server waypoint teleportation progress

Last updated: 2026-09-06.
Implementation baseline: `e33cc62` on `feature/cross-server-tp`.

## Current status

**Step 1 is complete. Step 2 is partially implemented and blocked at dependency selection.**
Steps 3–19 have not started. No production cross-server networking, remote commands, catalog
synchronization, player transfers, or remote GUI behavior has been enabled.

The [implementation plan](cross-server-waypoint-teleportation-plan.md) defines scope and order.
The [protocol v1 contract](cross-server-protocol-v1.md) defines the frozen feature semantics.
The [Noise selection decision](cross-server-noise-dependency-decision.md) records the dependency
investigation, rejection reasons, and outstanding platform checks.

## Completed and partial work

| Step | Status | Implementation and evidence |
| --- | --- | --- |
| 1 — Freeze the feature contract | Complete | Commit `a0af646`: separate protocol version 1, stable server-ID validation, exact `RemoteWaypointKey`, catalog states, public export policy, permission constants, normative command/security contract, and identity tests. |
| 2 — Select and prove the Noise dependency | Partial; selection blocked | Commit `e33cc62`: isolated Java 17 spike, candidate evaluation, published-vector check, two-process loopback tests, standalone relocation checks, and a written no-go decision. No production dependency was selected. |
| 3–19 | Not started | Proxy modules/interfaces, catalog models beyond the step-1 identity/enums, codecs, production transport, pairing, registration, synchronization, commands, permissions, handoffs, Velocity integration, client/GUI integration, and release hardening remain pending. |

The spike lives under [`tools/noise-spike`](../tools/noise-spike/README.md) in a standalone Gradle
build. It is not included in the root project's settings, runtime dependencies, or release tasks.

## Verification recorded so far

These results were obtained during implementation on 2026-09-06; this progress-file update did
not rerun the tests.

| Verification | Recorded result | Scope and limitations |
| --- | --- | --- |
| `./gradlew :common:test --console=plain` | Passed, including all 25 identity cases | Rejects invalid IDs, preserves exact identity strings, and keeps identical local identities on different servers distinct. |
| `./gradlew -p tools/noise-spike check --console=plain` | All 18 tests passed | Java 17 candidate tests and standalone packaging/process tests; not production approval. |
| Exact-suite Cacophony vector | Passed | Checks both handshake messages, four transport messages, and handshake hash against published values. |
| Two-process loopback matrix | Passed unshaded and relocated | Bidirectional encryption, three successive connections with fresh handshake state, clean shutdown, wrong PSK/pin/prologue, and tampered request/response rejection. |
| Standalone shaded JAR | Passed | Runs without original Noise classes on the classpath; verifies relocation, Java 17 class versions, license inclusion, and exclusion of test dependencies. |
| Nonce-exhaustion characterization | Known defect reproduced | This test passes when the candidate encrypts at the reserved nonce and wraps to zero. Its passing result is evidence against adoption as-is. |
| Staged whitespace checks | Passed for both implementation commits | `git diff --cached --check`. |

The spike ran on macOS arm64 with OpenJDK 17.0.19 and Gradle 9.5.1. Test reports can be regenerated
using the commands above. Actual Paper, Fabric, Forge, NeoForge, and Velocity artifact/classloader
verification, server startup, and end-to-end cross-server integration have **not** been performed.

## Why step 2 is blocked

- Original Noise-Java and Signal's fork do not support the required `NKpsk0` pattern; their older
  `NoisePSK` format is not an approved substitute.
- The tested jchambers candidate supports the exact suite, but lacks nonce-exhaustion protection.
  No maintained release and sufficient security-review basis were established for adopting it
  as-is. The experiment is not an independent security audit.
- Standalone relocation does not establish compatibility with each platform's final packaging
  and classloader. Those checks remain pending after dependency selection.

Additional integration constraints are recorded in the selection decision: session operations
must be serialized, key destruction is not explicitly supported by the candidate, and Noise's
65,535-byte record limit requires reconciling the larger planned application/chunk budgets.

## Next work, in order

1. Find a suitable maintained exact-suite dependency, or explicitly decide to maintain and review
   a pinned fork. Do not silently substitute a suite or adopt the rejected candidate unchanged.
2. Resolve and review nonce exhaustion and key/session lifecycle handling. For a corrected
   candidate, replace the known-defect characterization with a fail-closed assertion.
3. Rerun vectors, rejection cases, reconnect/shutdown, and standalone relocation against that
   candidate. Record its exact version/source, dependencies, licensing, and review evidence.
4. Resolve the packaging-check sequencing with step 3: the plan places platform verification in
   step 2, but creation of the Velocity module in step 3. Record how that check will be completed
   without treating a standalone JAR as Velocity verification.
5. Complete the required final platform artifact/classloader checks and record approval of the
   dependency before marking step 2 complete and advancing the implementation plan.

Update this file when a step's status changes, a blocker is resolved, or new validation is run.
Keep completed implementation, experimental evidence, and unperformed validation separate.
