# Cross-server waypoint teleportation progress

Last updated: 2026-09-06.
Prior implementation: `e33cc62`; original progress record: `b2f0c1c`;
KK/plaintext design revision: `631405e` on `feature/cross-server-tp`.
Step 2 is now `fd3ba53` after rebasing onto upload-branch fix `f0d8281`.
The current change implements step 3 without adding production transport.

## Current status

**Steps 1–3 are complete. Step 2 selects `org.signal.forks:noise-java:0.1.1`
for `Noise_KK_25519_AESGCM_SHA256`, with 35 passing KK checks and a scoped source review.**
Step 3 adds the proxy modules/contracts and private dependency packaging; all 41 artifact checks
and 10 native runtime checks pass. Steps 4–19 have not started. No production cross-server networking, remote commands, catalog
synchronization, player transfers, or remote GUI behavior has been enabled.

The [implementation plan](cross-server-waypoint-teleportation-plan.md) defines scope and order.
The [protocol v1 contract](cross-server-protocol-v1.md) defines the feature semantics.
The [dependency decision](cross-server-noise-dependency-decision.md) records the selected artifact,
source-review findings, maintenance risk, integration requirements, and outstanding platform checks.

## Accepted design

- Default `NOISE_KK`: unique static key pairs, paired public pins, no backend PSKs/certificates.
- Explicit `PLAINTEXT`: no encryption/authentication, no keys/pairing, literal loopback endpoints
  only, no automatic fallback. Local processes can impersonate enabled backend IDs.
- KK requires transcript-bound key selection and backend transport confirmation before operations.
- Step 2 selects/reviews the dependency and proves standalone relocation. Step 3 creates the
  modules and completes the final platform artifact/classloader matrix before production transport.
- Neither production mode nor the revised pairing workflow is implemented.

## Completed work

| Step | Status | Implementation and evidence |
| --- | --- | --- |
| 1 — Freeze the feature contract | Complete | Commit `a0af646`: separate protocol version 1, stable server-ID validation, exact `RemoteWaypointKey`, catalog states, public export policy, permission constants, normative command/security contract, and identity tests. The transport contract was subsequently revised to KK/plaintext. |
| 2 — Select and prove the Noise dependency | Complete | New isolated `tools/noise-spike/kk`: published Signal artifact pinned by SHA-256, source review, exact KK vectors, fail-closed nonce boundary tests, confirmation/replay checks, 26 two-process cases, and standalone relocation. Selected for step-3 integration, not production release approval. |
| 3 — Modules and proxy interfaces | Complete | `proxy-common`, inert `velocity`, common transport/proxy-neutral lifecycle and transfer contracts, 11 fake-adapter contract tests, private Noise shading on all platforms, and development-only artifact/native-classloader probes. See [validation](cross-server-step3-validation.md). |
| 4–19 | Not started | Catalog models, codecs, production transport, pairing, registration, synchronization, commands, permissions, handoffs, real Velocity integration, client/GUI integration, and release hardening remain pending. |

The [standalone spike](../tools/noise-spike/README.md) is not included in root project settings,
runtime dependencies, or release tasks. Its unchanged NKpsk0 root/candidate projects preserve the
historical no-go evidence from `e33cc62`; the new `kk` project has a separate runtime classpath.

## Verification from the KK continuation

Freshly run on macOS arm64, OpenJDK 17.0.19 for compilation/tests/child JVMs, Gradle 9.5.1.

| Verification | Result | Scope and limitations |
| --- | --- | --- |
| `./gradlew -p tools/noise-spike :kk:check --console=plain` | 35 KK checks passed | Eight candidate/harness cases, one artifact audit, and 26 two-process cases. |
| Cacophony exact KK vector | Passed on default and pure-Java fallback paths | Checks both handshake messages, four transport messages, and handshake hash against published values. |
| Nonce exhaustion | Fail-closed assertions passed on both AES-GCM paths | Last permitted nonce succeeds; encryption/decryption reject the reserved nonce repeatedly without writing output. |
| Two-process loopback matrix | Passed unshaded and relocated | Wrong keys, unknown/revoked fixture IDs, transcript mismatch, handshake/confirmation/transport tampering, early/missing confirmation, and replay reject. Success reconnects three times with fresh hashes, bounded bidirectional records, and clean shutdown. |
| KK first-message replay | Passed | A repeated initial handshake cannot authenticate the previous transport confirmation with a fresh responder. |
| Standalone shaded JAR | Passed | Runs without original Noise classes; checks Java 17 bytecode, private relocation, license, and exclusion of historical/test dependencies. |
| `./gradlew -p tools/noise-spike check :kk:dependencies --configuration runtimeClasspath --console=plain` | Passed | Also freshly reran the 18 historical checks; KK checks were already current. KK runtime has only Signal Noise-Java 0.1.1. |
| Source/artifact review | Completed | Published sources match inspected Signal HEAD; pinned runtime JAR hash verified. Sparse upstream activity remains a documented dependency risk, not a maintenance guarantee. |
| `git diff --check` and new-file whitespace checks | Passed | Step-2 continuation. |

The isolated step-2 run did not verify final platform artifacts or startup. Step-3 platform
evidence is recorded separately below. Pairing, production registry/revocation, and end-to-end
cross-server integration remain unimplemented.
The fixture identity and confirmation checks do not implement these later services.

## Step-3 verification

The initial full build and all 41 final artifact checks passed. Eight of ten live platform runs
passed startup, native-classloader cryptography, and clean shutdown. Two inherited startup defects
were reproduced on `feature/upload-3.1.0`, fixed there in `f0d8281`, and the cross-server branch
was rebased onto that commit at the user's direction. The post-rebase full build passed, with
316 common tests and 11 proxy contract tests. All 41 final artifact checks and all 10 native
startup/classloader/shutdown cases passed against matching artifact hashes. The final contract
fixture refinement also passed all 11 tests. See [detailed evidence](cross-server-step3-validation.md).

## Historical evidence retained

- Step 1: `./gradlew :common:test --console=plain` passed all 25 identity cases during the original
  implementation. It was not rerun for this isolated dependency change.
- NKpsk0: all 18 historical checks still pass, including a test that deliberately reproduces the
  jchambers nonce-exhaustion defect. That is evidence against adopting that candidate, not KK
  approval or production permission. See the [archived decision](cross-server-noise-nkpsk0-investigation.md).
- Earlier implementation commits and this step-2 continuation passed their staged whitespace checks.

## Next work, in order

1. Steps 4–5: implement immutable catalog/message models and bounded canonical codecs.
2. Step 6: implement both transport modes, endpoint/mode rejection, transcript binding, confirmation
   gating, serialized session lifecycle, terminal failures, no downgrade, and bounded fragmentation.
3. Step 7: prove the authenticated pairing bootstrap, canonical key import, rotation, and revocation.
4. Continue the remaining registration/catalog/handoff steps in order. Plaintext backend identity
   must never be described as cryptographically authenticated.

Update this file when a step's status changes, a blocker is resolved, or new validation is run.
Keep completed implementation, experimental evidence, and unperformed validation separate.
