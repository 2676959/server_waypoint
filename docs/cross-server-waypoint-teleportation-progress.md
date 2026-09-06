# Cross-server waypoint teleportation progress

Last updated: 2026-09-06.
Implementation baseline: `e33cc62` on `feature/cross-server-tp`; original progress record: `b2f0c1c`.
The KK/plaintext design revision below changes documentation only.

## Current status

**Step 1 identity work is complete, with its transport contract revised to KK/plaintext.
Step 2 has historical NKpsk0 evidence; KK dependency selection and verification are pending.**
Steps 3–19 have not started. No production cross-server networking, remote commands, catalog
synchronization, player transfers, or remote GUI behavior has been enabled.

The [implementation plan](cross-server-waypoint-teleportation-plan.md) defines scope and order.
The [protocol v1 contract](cross-server-protocol-v1.md) defines the frozen feature semantics.
The [Noise selection decision](cross-server-noise-dependency-decision.md) records the dependency
investigation, rejection reasons, and outstanding platform checks.

## Accepted design revision

- Default `NOISE_KK`: unique static key pairs, paired public pins, no backend PSKs/certificates.
- Explicit `PLAINTEXT`: no encryption/authentication, no keys/pairing, literal loopback endpoints
  only, no automatic fallback. Local processes can impersonate enabled backend IDs.
- KK requires transcript-bound key selection and backend transport confirmation before operations.
- Step 2 selects/reviews a KK dependency and proves standalone relocation. Step 3 creates the
  modules and completes the final platform artifact/classloader matrix before production transport.
- These are planned changes; neither mode nor the revised pairing workflow is implemented.

## Completed and partial work

| Step | Status | Implementation and evidence |
| --- | --- | --- |
| 1 — Freeze the feature contract | Complete | Commit `a0af646`: separate protocol version 1, stable server-ID validation, exact `RemoteWaypointKey`, catalog states, public export policy, permission constants, normative command/security contract, and identity tests. |
| 2 — Select and prove the Noise dependency | Partial; KK evaluation pending | Commit `e33cc62`: isolated Java 17 spike, candidate evaluation, published-vector check, two-process loopback tests, standalone relocation checks, and a written no-go decision. No production dependency was selected; these tests target NKpsk0, not KK. |
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

## Outstanding step-2 work after the design revision

The original rejection was against NKpsk0. Noise-Java and Signal's fork support KK at the pattern
level, so they need a fresh suitability evaluation rather than rejection for lacking NKpsk0.
The existing 18-test result is historical and includes a known-defect characterization; it is not
KK approval. No new runtime tests were run for this documentation revision.

## Next work, in order

1. Reevaluate Java 17 KK candidates for exact-suite correctness, maintenance, licensing, nonce
   exhaustion, thread-safety, and key/session lifecycle. Record the selected version and review.
2. Extend the isolated spike with KK vectors, two-process exchanges, wrong coordinator/backend
   keys, unknown/revoked ID, tampering, confirmation gating, reconnect/shutdown, and relocation.
3. If the dependency passes step 2, create step-3 modules and complete all final platform
   artifact/classloader checks, including Velocity. The plaintext option does not bypass KK review.
4. In step 6 implement both modes, endpoint/mode rejection, no downgrade, and bounded Noise record
   fragmentation/reassembly. In step 7 prove the authenticated pairing bootstrap and key rotation.
5. Continue the remaining catalog and handoff steps without claiming plaintext backend identity is
   cryptographically authenticated.

Update this file when a step's status changes, a blocker is resolved, or new validation is run.
Keep completed implementation, experimental evidence, and unperformed validation separate.
