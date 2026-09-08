# Cross-server waypoint teleportation progress

Last updated: 2026-09-08.
Prior implementation: `e33cc62`; original progress record: `b2f0c1c`;
KK/plaintext design revision: `631405e` on `feature/cross-server-tp`.
Step 2 is now `fd3ba53` after rebasing onto upload-branch fix `f0d8281`.
Step 3 was committed as `3fccc28`; step 4 as `6d7be5a`; step 5 as `4e806fa`; step 6 as `8752aaa`.
Step 7 was committed as `1858582`; step 8 as `b8a372d`. The current change implements step 9 catalog publication.

## Current status

**Steps 1–9 are complete. Step 2 selects `org.signal.forks:noise-java:0.1.1`
for `Noise_KK_25519_AESGCM_SHA256`, with 35 passing KK checks and a scoped source review.**
Step 3 adds the proxy modules/contracts and private dependency packaging; all 41 artifact checks
and 10 native runtime checks pass. Step 4 adds immutable catalogs and reader views.
Step 5 adds canonical messages and bounded codecs. Step 6 adds reusable bounded TCP channels in both
modes. Step 7 adds authenticated pairing, credential persistence, rotation and live revocation.
Step 8 adds asynchronous agents, registered presence, heartbeat and bounded reconnect.
Step 9 adds authoritative full/delta publication and stale-safe coordinator receipt.
Steps 10–19 have not started. No platform lifecycle starts these channels, and no remote commands,
catalog synchronization service, player transfers, or remote GUI behavior has been enabled.

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
- Both transport modes are implemented as reusable channels; pairing/credential APIs are implemented; platform startup and the bootstrap carrier remain pending.

## Completed work

| Step | Status | Implementation and evidence |
| --- | --- | --- |
| 1 — Freeze the feature contract | Complete | Commit `a0af646`: separate protocol version 1, stable server-ID validation, exact `RemoteWaypointKey`, catalog states, public export policy, permission constants, normative command/security contract, and identity tests. The transport contract was subsequently revised to KK/plaintext. |
| 2 — Select and prove the Noise dependency | Complete | New isolated `tools/noise-spike/kk`: published Signal artifact pinned by SHA-256, source review, exact KK vectors, fail-closed nonce boundary tests, confirmation/replay checks, 26 two-process cases, and standalone relocation. Selected for step-3 integration, not production release approval. |
| 3 — Modules and proxy interfaces | Complete | `proxy-common`, inert `velocity`, common transport/proxy-neutral lifecycle and transfer contracts, 11 fake-adapter contract tests, private Noise shading on all platforms, and development-only artifact/native-classloader probes. See [validation](cross-server-step3-validation.md). |
| 4 — Identity and catalog models | Complete | Immutable nested snapshots, exact-key lookup, independent catalog/list revisions, receiver-local receipt time, and validated reader views. See [model contract](cross-server-catalog-models.md). |
| 5 — Canonical messages and codecs | Complete | Fifteen typed message families with stable IDs, correlated/sequenced envelopes, strict canonical catalog encoding, bounded chunk/delta payloads, and malformed-input tests. See [wire specification](cross-server-application-codec-v1.md). |
| 6 — Bounded TCP transport | Complete | Explicit KK/plaintext modes, transcript-bound handshake and confirmation, bounded records/catalog assembly, sequence/replay tracking, admission limits, absolute deadlines and terminal cleanup. See [transport contract](cross-server-tcp-transport-v1.md). |
| 7 — Pairing and credentials | Complete | One-time authenticated bootstrap, canonical key import, private credential files, expected-pin rotation and durable per-ID/live-session revocation. See [bootstrap contract and review](cross-server-pairing-v1.md). |
| 8 — Lifecycle and registration | Complete | Asynchronous backend/coordinator owners, transcript-matched registration, explicit mode/status, heartbeat timeouts, bounded backoff, duplicate rejection, metrics and graceful shutdown. See [lifecycle contract](cross-server-connection-lifecycle.md). |
| 9 — Backend catalog publication | Complete | Atomic detached capture, exact PUBLIC selection, durable catalog/list revisions, bounded full/delta publication, gap resynchronization and stale-preserving receiver validation. See [publication contract](cross-server-catalog-publication.md). |
| 10–19 | Not started | Coordinator catalog aggregation/fan-out, commands, permissions, handoffs, real Velocity integration, client/GUI integration, and release hardening remain pending. |

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

## Step-4 verification

On 2026-09-07, `./gradlew :common:test :proxy-common:test --max-workers=2 --console=plain`
passed: 325 common tests (including 9 new catalog-model cases) and 11 proxy contract tests,
with no failures or skips. The common module uses the Java 17 toolchain. `git diff --check`
and explicit new-file whitespace checks passed. See [model semantics](cross-server-catalog-models.md).
No platform source/build configuration changed; the full artifact and live runtime matrix was
not rerun for this domain-only step. The step-3 results above remain historical evidence.

## Step-5 verification

On 2026-09-07, `./gradlew :common:test :proxy-common:test --max-workers=2 --console=plain`
passed: 385 common tests (including 60 new codec cases) and 11 proxy contract tests, with no
failures or skips. Compilation used the common module's Java 17 toolchain. Tests cover all
15 message families, every truncation prefix, canonical ordering, strict UTF-8, independent
budgets, catalog/handoff validation, defensive ownership, and 2,000 seeded byte mutations.
`git diff --check` and explicit new-file whitespace checks passed. See the
[wire specification](cross-server-application-codec-v1.md) for payloads and exact limits.
No runtime networking or platform configuration changed. Full artifact/native runtime checks were
not repeated, and these codec tests do not establish transport, replay, reassembly, or handoff safety.

## Step-6 verification

On 2026-09-07, `./gradlew :common:test :proxy-common:test --max-workers=2 --console=plain`
passed 433 common tests (48 new transport cases) and 11 proxy tests, without failures or skips.
Real loopback sockets exercise both modes, KK confirmation and transcript rejection, framing,
replay, resource ceilings, concurrent directions, stalled peers and cleanup. See the
[transport specification](cross-server-tcp-transport-v1.md) for exact scope and limits.
`:velocity:build` also passed; the final JAR contains the new Java 17 Noise caller with private
relocation and no original Noise namespace. Tracked/new-file whitespace checks passed.
The full backend artifact/native runtime matrix was not repeated. No game lifecycle starts these
channels; pairing/reconnect and live feature integration remain pending.

## Step-7 verification

On 2026-09-07, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passed. After adding the final live-session isolation and diagnostic scans, `:proxy-common:test`
also passed. Current totals are 439 common tests and 35 proxy tests (30 new Step-7 cases),
with zero failures/errors/skips. The final Velocity JAR contains Java 17 credential/coordinator
classes and the privately relocated Noise caller. Tracked/new-file whitespace checks pass.
Tests verify authenticated installation ordering, replay/expiry
rejection, credential permissions, explicit rotation and isolated revocation using real KK sockets.
See the [bootstrap specification and review](cross-server-pairing-v1.md) for the security boundary.
The feature is not enabled, and platform/bootstrap-carrier integration remains pending. Full backend
artifact/native runtime verification was not repeated.

## Step-8 verification

On 2026-09-07, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passed 439 common tests and 53 proxy tests (18 new lifecycle cases), without failures/errors/skips.
Real sockets cover both modes, automatic registration/heartbeat, duplicate IDs, reconnect after
coordinator restart, bounded commands/backoff, missing/mismatched registration, silent peers and
shutdown during handshake. A deliberately blocked listener factory verifies that start/stop return
without blocking the caller. See [ownership and limits](cross-server-connection-lifecycle.md).
Final Velocity JAR inspection confirms both agents use Java 17 bytecode. Tracked/new-file
whitespace checks pass. No platform lifecycle or bootstrap network dispatcher has been enabled.
The full backend artifact and live game/proxy matrix was not repeated.

## Step-9 verification

On 2026-09-08, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passed 444 common tests and 61 proxy tests, with zero failures/errors/skips. The 13 new cases cover
actual model edits, atomic detached export, independent list revisions, persistent high-water marks,
full/delta publication in both modes, missed-delta resynchronization, bounded full fallback,
explicit empty catalogs and preservation of stale data on publication failure. Two pre-existing
cleanup tests now wait for asynchronous cleanup/accounting as well as socket/presence closure.
See [publication ownership and limits](cross-server-catalog-publication.md). Full backend artifact
and native game/proxy integration checks were not repeated; no platform feature has been enabled.

## Historical evidence retained

- Step 1: `./gradlew :common:test --console=plain` passed all 25 identity cases during the original
  implementation. It was not rerun for this isolated dependency change.
- NKpsk0: all 18 historical checks still pass, including a test that deliberately reproduces the
  jchambers nonce-exhaustion defect. That is evidence against adopting that candidate, not KK
  approval or production permission. See the [archived decision](cross-server-noise-nkpsk0-investigation.md).
- Earlier implementation commits and this step-2 continuation passed their staged whitespace checks.

## Next work, in order

1. Step 10: build the coordinator catalog index, bound cache/expiry policy, and fan out catalogs to backends.
2. Continue the remaining command/handoff/platform steps in order. Plaintext backend identity
   must never be described as cryptographically authenticated.

Update this file when a step's status changes, a blocker is resolved, or new validation is run.
Keep completed implementation, experimental evidence, and unperformed validation separate.
