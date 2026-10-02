# Cross-server waypoint teleportation progress

Last updated: 2026-09-10.
Prior implementation: `e33cc62`; original progress record: `b2f0c1c`;
KK/plaintext design revision: `631405e` on `feature/cross-server-tp`.
Step 2 is now `fd3ba53` after rebasing onto upload-branch fix `f0d8281`.
Step 3 was committed as `3fccc28`; step 4 as `6d7be5a`; step 5 as `4e806fa`; step 6 as `8752aaa`.
Step 7 was committed as `1858582`; step 8 as `b8a372d`. Step 9 was committed as `d32d177`. Step 10 was committed as `d9ada50`. Step 11 remote queries and suggestions are complete. Step 12 permissions and authorization callbacks were committed as `bebdb30`. Step 13 coordinator handoffs were committed as `4122f2a`. Step 15 was committed as `1a0d7e8`. Step 16 was committed as `4d91de1`. Step 17 was committed as `1c2e74a`. Step 18 was committed as `4fe1a9b`. Step 19 hardening and the four representative native release gates are validated; the native follow-up is recorded below.

## Current status

**Steps 1–19 are implemented and the representative native release gates pass. Step 2 selects `org.signal.forks:noise-java:0.1.1`
for `Noise_KK_25519_AESGCM_SHA256`, with 35 passing KK checks and a scoped source review.**
Step 3 adds the proxy modules/contracts and private dependency packaging; all 41 artifact checks
and 10 native runtime checks pass. Step 4 adds immutable catalogs and reader views.
Step 5 adds canonical messages and bounded codecs. Step 6 adds reusable bounded TCP channels in both
modes. Step 7 adds authenticated pairing, credential persistence, rotation and live revocation.
Step 8 adds asynchronous agents, registered presence, heartbeat and bounded reconnect.
Step 9 adds authoritative full/delta publication and stale-safe coordinator receipt.
Step 10 adds bounded coordinator indexing, fan-out and backend replicas with stale expiry.
Step 11 registers read-only remote commands and cached suggestions in the shared backend command tree.
Step 12 adds permission-gated browsing and reusable source/destination authorization callbacks.
Step 13 adds bounded coordinator handoff state and reusable backend-message handlers.
Step 14 adds destination reservations, authoritative arrival validation and concrete mod/Paper teleport adapters.
Step 15 adds source initiation and final owner-thread checks. Step 16 wires the backend and Velocity
lifecycles, catalog synchronization and player transfers behind explicit configuration (disabled by
default). Step 17 adds bounded authorized remote client snapshots under Minecraft protocol 11.
Step 18 adds a read-only remote manager branch with exact-identity selection and teleport confirmation.
Step 19 hardening, administrator documentation and the full build/artifact matrix are implemented.
The four representative native gates now pass; see [Step-19 verification and scope](validation/cross-server-release-readiness.md). Nothing has been published.

The [implementation plan](plans/cross-server-waypoint-teleportation-plan.md) defines scope and order.
The [protocol v1 contract](specs/cross-server-protocol-v1.md) defines the feature semantics.
The [dependency decision](specs/cross-server-noise-dependency-decision.md) records the selected artifact,
source-review findings, maintenance risk, integration requirements, and outstanding platform checks.

## Accepted design

- Default `NOISE_KK`: unique static key pairs, paired public pins, no backend PSKs/certificates.
- Explicit `PLAINTEXT`: no encryption/authentication, no keys/pairing, literal loopback endpoints
  only, no automatic fallback. Local processes can impersonate enabled backend IDs.
- KK requires transcript-bound key selection and backend transport confirmation before operations.
- Step 2 selects/reviews the dependency and proves standalone relocation. Step 3 creates the
  modules and completes the final platform artifact/classloader matrix before production transport.
- Both transport modes and platform startup are implemented. Runtime administration uses trusted public-pin exchange; the pairing-code bootstrap carrier remains unimplemented.

## Completed work

| Step | Status | Implementation and evidence |
| --- | --- | --- |
| 1 — Freeze the feature contract | Complete | Commit `a0af646`: separate protocol version 1, stable server-ID validation, exact `RemoteWaypointKey`, catalog states, public export policy, permission constants, normative command/security contract, and identity tests. The transport contract was subsequently revised to KK/plaintext. |
| 2 — Select and prove the Noise dependency | Complete | New isolated `tools/noise-spike/kk`: published Signal artifact pinned by SHA-256, source review, exact KK vectors, fail-closed nonce boundary tests, confirmation/replay checks, 26 two-process cases, and standalone relocation. Selected for step-3 integration, not production release approval. |
| 3 — Modules and proxy interfaces | Complete | `proxy-common`, inert `velocity`, common transport/proxy-neutral lifecycle and transfer contracts, 11 fake-adapter contract tests, private Noise shading on all platforms, and development-only artifact/native-classloader probes. See [validation](validation/cross-server-step3-validation.md). |
| 4 — Identity and catalog models | Complete | Immutable nested snapshots, exact-key lookup, independent catalog/list revisions, receiver-local receipt time, and validated reader views. See [model contract](specs/cross-server-catalog-models.md). |
| 5 — Canonical messages and codecs | Complete | Fifteen typed message families with stable IDs, correlated/sequenced envelopes, strict canonical catalog encoding, bounded chunk/delta payloads, and malformed-input tests. See [wire specification](specs/cross-server-application-codec-v1.md). |
| 6 — Bounded TCP transport | Complete | Explicit KK/plaintext modes, transcript-bound handshake and confirmation, bounded records/catalog assembly, sequence/replay tracking, admission limits, absolute deadlines and terminal cleanup. See [transport contract](specs/cross-server-tcp-transport-v1.md). |
| 7 — Pairing and credentials | Complete | One-time authenticated bootstrap, canonical key import, private credential files, expected-pin rotation and durable per-ID/live-session revocation. See [bootstrap contract and review](specs/cross-server-pairing-v1.md). |
| 8 — Lifecycle and registration | Complete | Asynchronous backend/coordinator owners, transcript-matched registration, explicit mode/status, heartbeat timeouts, bounded backoff, duplicate rejection, metrics and graceful shutdown. See [lifecycle contract](specs/cross-server-connection-lifecycle.md). |
| 9 — Backend catalog publication | Complete | Atomic detached capture, exact PUBLIC selection, durable catalog/list revisions, bounded full/delta publication, gap resynchronization and stale-preserving receiver validation. See [publication contract](specs/cross-server-catalog-publication.md). |
| 10 — Catalog aggregation/distribution | Complete | Shared bounded index, coordinator fan-out with full/delta resynchronization, source-mode retention, separate backend replicas, stale expiry and retained revision fingerprints. See [distribution contract](specs/cross-server-catalog-distribution.md). |
| 11 — Remote queries/suggestions | Complete | Read-only bounded store facade, shared list grammar/filtering/sorting, vanilla-safe servers/list commands, cache-only identity suggestions and six-language help/status feedback. See [query contract](specs/cross-server-catalog-queries.md). |
| 12 — Permissions and authorization | Complete | Remote list/tp nodes, current source-player checks, permission-gated commands/help/suggestions, destination export and final live-player callbacks. See [authorization contract](specs/cross-server-authorization.md). |
| 13 — Coordinator handoffs | Complete | Atomic prepare/reserve/claim/complete/cancel/expiry, session and proxy-player binding, bounded replay retention and audit, and backend-message handlers. See [handoff contract](specs/cross-server-handoffs.md). |
| 14 — Destination preparation and arrival | Complete | Bounded connection-scoped reservations, exact claim/player binding, fresh local export/coordinate resolution, final permission checks and mod/Paper owner adapters. See [destination contract](specs/cross-server-destination.md). |
| 15 — Remote teleport command | Complete | Exact cached targets, permission-gated command/help/suggestions, bounded source preparation service and transfer adapter boundary. See [source teleport contract](specs/cross-server-source-teleport.md). |
| 16 — Velocity runtime integration | Complete | Coordinator/backend lifecycle, TCP readiness/claim dispatch, real Velocity switch, owner-thread arrivals, 16 real-socket cases and live Paper/Velocity tests in both modes. See [runtime contract](specs/cross-server-velocity-runtime.md). |
| 17 — Client catalog synchronization | Complete | Protocol 11, bounded authorized remote snapshots, session reset and client dispatcher isolation tests. See [client contract](specs/cross-server-client-sync.md). |
| 18 — Waypoint manager GUI | Complete | Read-only server/dimension/list/waypoint tree, exact selection, stale status, remote details, guarded command confirmation, seven regression tests and native GUI probe. See [GUI contract](specs/cross-server-gui.md). |
| 19 — Release hardening | Validated | Full build and 39-JAR gate, representative Folia/Forge/NeoForge transfers, real protocol-11 GUI, authenticated failure/rotation checks and 30-minute soak pass. See exact scope and limitations in the [release record](validation/cross-server-release-readiness.md). Native follow-up evidence is recorded below. |

The [standalone spike](../../../tools/noise-spike/README.md) is not included in root project settings,
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
fixture refinement also passed all 11 tests. See [detailed evidence](validation/cross-server-step3-validation.md).

## Step-4 verification

On 2026-09-07, `./gradlew :common:test :proxy-common:test --max-workers=2 --console=plain`
passed: 325 common tests (including 9 new catalog-model cases) and 11 proxy contract tests,
with no failures or skips. The common module uses the Java 17 toolchain. `git diff --check`
and explicit new-file whitespace checks passed. See [model semantics](specs/cross-server-catalog-models.md).
No platform source/build configuration changed; the full artifact and live runtime matrix was
not rerun for this domain-only step. The step-3 results above remain historical evidence.

## Step-5 verification

On 2026-09-07, `./gradlew :common:test :proxy-common:test --max-workers=2 --console=plain`
passed: 385 common tests (including 60 new codec cases) and 11 proxy contract tests, with no
failures or skips. Compilation used the common module's Java 17 toolchain. Tests cover all
15 message families, every truncation prefix, canonical ordering, strict UTF-8, independent
budgets, catalog/handoff validation, defensive ownership, and 2,000 seeded byte mutations.
`git diff --check` and explicit new-file whitespace checks passed. See the
[wire specification](specs/cross-server-application-codec-v1.md) for payloads and exact limits.
No runtime networking or platform configuration changed. Full artifact/native runtime checks were
not repeated, and these codec tests do not establish transport, replay, reassembly, or handoff safety.

## Step-6 verification

On 2026-09-07, `./gradlew :common:test :proxy-common:test --max-workers=2 --console=plain`
passed 433 common tests (48 new transport cases) and 11 proxy tests, without failures or skips.
Real loopback sockets exercise both modes, KK confirmation and transcript rejection, framing,
replay, resource ceilings, concurrent directions, stalled peers and cleanup. See the
[transport specification](specs/cross-server-tcp-transport-v1.md) for exact scope and limits.
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
See the [bootstrap specification and review](specs/cross-server-pairing-v1.md) for the security boundary.
The feature is not enabled, and platform/bootstrap-carrier integration remains pending. Full backend
artifact/native runtime verification was not repeated.

## Step-8 verification

On 2026-09-07, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passed 439 common tests and 53 proxy tests (18 new lifecycle cases), without failures/errors/skips.
Real sockets cover both modes, automatic registration/heartbeat, duplicate IDs, reconnect after
coordinator restart, bounded commands/backoff, missing/mismatched registration, silent peers and
shutdown during handshake. A deliberately blocked listener factory verifies that start/stop return
without blocking the caller. See [ownership and limits](specs/cross-server-connection-lifecycle.md).
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
See [publication ownership and limits](specs/cross-server-catalog-publication.md). Full backend artifact
and native game/proxy integration checks were not repeated; no platform feature has been enabled.

## Step-10 verification

On 2026-09-08, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passed 444 common tests and 67 proxy tests, with zero failures/errors/skips. Six new cases cover
A-to-B synchronization in KK/plaintext, duplicate local names, reconnect, immutable views,
stale/expiry fan-out, global/per-server/identity limits, source/session mismatch, retained revision
fingerprints, correlated delta recovery, oversized-delta full fallback and expiry during refresh.
The Velocity artifact includes Java 17 catalog services. Native Minecraft/Velocity integration
and the full backend artifact matrix were not run; platform lifecycle wiring remains pending.

## Step-11 verification

On 2026-09-08, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passed 452 common tests and 67 proxy tests, with zero failures/errors/skips. Eight new cases cover
shared-root attachment, preservation of local lists, cache-only exact/quoted/empty-name suggestions,
combined options and generated pagination commands, fuzzy/name/color query semantics, duplicate
identities across servers, stale/unavailable/empty distinctions, scoped errors, distance rejection,
unauthorized snapshot redaction and read-only Adventure actions. Existing local list tests continue
to pass; main-help expectations include the newly registered remote topic. One pre-existing reconnect
assertion now reads a single immutable presence snapshot rather than racing two status reads.
All six bundled locales contain the new feedback/help keys. Native game/platform execution and the
full backend artifact matrix were not run; platform transport startup remains a later step.

## Step-12 verification

On 2026-09-08, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passed 460 common tests and 67 proxy tests, with no failures/errors/skips. Eight new cases cover
permission/config changes, both source teleport nodes, console behavior, exact destination export
checks and revocation, callback failures, command/suggestion revocation after parsing, and the four
checked-in platform permission adapters against API doubles. Existing command tests additionally
verify denied remote help/browsing and unchanged local lists. See [scope and limitations](specs/cross-server-authorization.md).
The adapter harness exercises active sources, not legacy generated branches or a live permission
provider. Full backend builds/native runtime checks were not repeated; no handoff/platform startup
or teleport command is enabled.

## Step-13 verification

On 2026-09-08, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passed 460 common tests and 88 proxy tests with no failures/errors/skips. The 21 new cases exercise
canonical in-memory exchanges in both modes, proxy identity/source/destination checks, admission
revocation, exact bindings and session replacement, replay and terminal idempotence, concurrent
prepares/claims/cancellation, monotonic and capped expiry, bounded retention/audits, disconnect and
restart. See [handoff contract and integration boundary](specs/cross-server-handoffs.md).
No native platform or live TCP handoff dispatcher was enabled or tested. Destination reservation,
owning-thread arrival/teleport and real proxy/transport wiring remain steps 14–16.

## Step-14 verification

On 2026-09-08, common/proxy tests passed 484 common cases and 90 proxy cases with no failures,
errors or skips. The 26 new cases cover authoritative preparation/arrival, removal/rename/movement,
export and permission revocation, current player binding, scheduling/retirement, concurrent duplicate
arrivals, late/invalid claims, cancellation/expiry/disconnect, async outcomes and in-memory exchanges
with the coordinator in both modes. The Velocity build and production adapter compilation passed
for Fabric 1.20.1/26.1.2, NeoForge 1.21.2, Forge 26.1.2 and Paper 1.21/26.2. See the
[exact command and limits](specs/cross-server-destination.md). No active Stonecutter version was changed.
No native game/proxy session or full release artifact matrix was run. Remote command registration,
TCP lifecycle/claim dispatch and join integration remain steps 15–16.

## Step-15 verification

On 2026-09-08, `./gradlew :common:test :proxy-common:test :velocity:build --max-workers=2 --console=plain`
passed 520 common tests and 90 proxy tests with no failures/errors/skips. The 36 new common cases
cover remote command initiation, exact identities/revisions, permission/cache changes, all rejected
preparation results, owner scheduling, cancellation, bounded expiry, disconnect and async failures.
The existing two destination exchanges now run through source initiation and a fake transfer adapter
in both modes, asserting PREPARED before switching. Six-locale keys/placeholders, packaged Java 17
source-service bytecode and whitespace checks pass. See [source initiation](specs/cross-server-source-teleport.md).
No active version changed. Native game/proxy sessions and the full backend artifact matrix were
not run; real lifecycle/TCP dispatch/Velocity switching remain Step 16.

## Step-16 verification

On 2026-09-08, 523 common, 106 proxy and 6 Velocity tests passed with no failures/errors/skips.
The new runtime tests exercise real TCP in both modes and actual Velocity API doubles. Paper 1.21
build/tests and active Paper 26.2/Fabric 26.1.2 plus Fabric 1.20.1, NeoForge 1.21.2 and Forge 26.1.2
compilation passed. No active version changed. Live Velocity 4.1.1 with two Paper 1.21 servers and
MCC passed KK transfers in both directions, exactly-once destination event/position checks,
permission denial, explicit plaintext transfer and rejected-login recovery with source retention.
All disposable processes stopped cleanly. Logs and matching artifact hashes are archived in
[Step-16 evidence](validation/cross-server-step16/results.json). See [scope and deployment contract](specs/cross-server-velocity-runtime.md).
Native mod/Folia transfers, online forwarding and the full release/soak matrix remain Step-19 gates.

## Historical evidence retained

- Step 1: `./gradlew :common:test --console=plain` passed all 25 identity cases during the original
  implementation. It was not rerun for this isolated dependency change.
- NKpsk0: all 18 historical checks still pass, including a test that deliberately reproduces the
  jchambers nonce-exhaustion defect. That is evidence against adopting that candidate, not KK
  approval or production permission. See the [archived decision](specs/cross-server-noise-nkpsk0-investigation.md).
- Earlier implementation commits and this step-2 continuation passed their staged whitespace checks.

## Next work, in order

1. Review the Step-19 native follow-up and recorded deployment scope before release packaging/publication. The four requested representative native gates have passed; plaintext backend identity remains unauthenticated.

Update this file when a step's status changes, a blocker is resolved, or new validation is run.
Keep completed implementation, experimental evidence, and unperformed validation separate.


## Step-17 verification

Step 17 synchronizes bounded authorized catalog/status replacements over the existing Minecraft
chunked channel. Protocol 11 clients correlate refreshes by request UUID and clear remote state on
session changes; remote data remains separate from local files/managers. See the
[client synchronization contract](specs/cross-server-client-sync.md) for wire limits, polling semantics,
automated validation and remaining native release gates.

All 852 automated tests passed (common, proxy-common, Velocity, active Fabric and Paper 1.21),
with zero failures/errors/skips. [Machine-readable results](validation/cross-server-step17/results.json).

## Step-18 verification

On 2026-09-09, all 854 tests passed (531 common, 106 proxy, 6 Velocity, 211 Fabric),
with zero failures/errors/skips; the Velocity build passed.
Fabric 1.20.1, NeoForge 1.21.2 and Forge 26.1.2 compilation passed without changing the active
Stonecutter version. Seven new regression cases cover exact server separation, sorting/filtering,
quoted/empty/Unicode identifiers, stale/empty/denied states, confirmation invalidation, command
packet limits and six-locale placeholder coverage.

A disposable native Fabric 26.1.2 client (Fabric Loader 0.19.5, API 0.148.2+26.1.2, Java 25.0.3,
HeadlessMC 2.10.0) passed actual screen mouse dispatch, modal cancellation, 320×240/960×540 resize,
filter/stale action disabling, session-reset rejection and unchanged local manager/files in an
offline flat world. The explicit probe is excluded from production artifacts. Headless rendering
uses stubbed graphics and dummy assets: this is native GUI lifecycle/input evidence, not screenshot
approval or a live remote transfer. See [evidence](validation/cross-server-step18/results.json) and
[probe runbook](../../../tools/cross-server-gui-test/README.md).

## Step-19 verification

On 2026-09-09 the complete `./gradlew build --continue --max-workers=2 --console=plain` matrix
passed, with no active version change. Reports contain 8,327 test cases, zero failures/errors and
12 existing Forge-family Minecraft-bootstrap skips. Final common/proxy/Velocity checks include
539/106/6 cases with no skips; 35 fresh KK checks and eight release-tool tests pass. All 39 final
release JARs pass exact-target, relocation/license and test-content verification.

Native Paper/Fabric 26.2 with Velocity 4.1.1 and MCC protocol 776 reproduced and closed an Adventure
remote-command linkage defect and early join/player-lookup arrival defects. Both modes now pass
bidirectional arrival/coordinate checks. KK additionally passes catalog edits, destination denial
and coordinator restart/republish; plaintext leaves keys unchanged and denies stale destinations.
The run used disposable offline identities and forwarding NONE. All processes stopped cleanly.

At that snapshot, Step 19 remained **in progress for production release approval**.
The follow-ups below record subsequent native validation; current remaining gates are
tracked in the release record. See the [release record](validation/cross-server-release-readiness.md),
[administrator guide](cross-server-admin.md) and [machine-readable evidence](validation/cross-server-step19/results.json).

### Step 19 Folia native follow-up (2026-09-09)

Folia 1.21.11 exposed a claim-before-proxy-route-installation race beyond the
backend entity-tick fix. The coordinator now delays the validated claim until
transfer completion and rechecks the live route. Representative repeated and
separate-region two-player transfers, controlled destination retirement, and
reconnect recovery pass with the fixed proxy. [Evidence and limitations](validation/cross-server-release-readiness.md#folia-follow-up--2026-09-09)
remain explicit; this is not full Step 19 or production release approval.


### Step 19 native release follow-up (2026-09-10)

Representative Forge 26.2/65.1.3 and NeoForge 26.2.0.3-beta clients passed synchronized
bidirectional transfers and disconnect/reconnect recovery through modern forwarding with
PCF 1.3.1. A real Fabric 26.2 protocol-11 client received a remote catalog mutation while
browsing, selected the new exact target, confirmed through native mouse input and transferred
through Velocity to the expected position. These were disposable offline client sessions.

Online AuthenticatedTestPlayer sessions with modern forwarding and NOISE_KK have exercised Folia
arrival ownership, permission revocation and waypoint movement between preparation and
arrival, first-entity-tick disconnect and recovery. The final proxy candidate also passed
those mutation cases, restart-based backend revocation and old-pin denial after backend
and coordinator key rotation. The 32-connection native pressure check allowed two transfers
while held and cleaned up stalled handshakes.

Final-candidate common/proxy-common/Velocity reports contain 539/114/6 passing tests, with
no failures, errors or skips. All 39 staged release artifacts pass the audit with the final
proxy SHA-256 `be67bf9998b72b3fb3cb87aa2d402cf944edd2b3dd9d72eed4ba2f830ddd865f`.
Two final-candidate soak attempts stopped before new logins after 6 and 16 cycles.
macOS crash reports identify MCC CoreCLR SIGABRT, with no matching proxy connections.
The subsequent 1,801.9-second run with `DOTNET_ReadyToRun=0` for MCC passed all 60
reconnects and 120 transfers. The exact upstream crash defect remains unconfirmed.
Retained heap and descriptor observations stayed bounded; all disposable processes stopped.
[Soak evidence](validation/cross-server-step19-soak/results.json). Runtime scope,
artifact distinctions and remaining limitations are in the [release record](validation/cross-server-release-readiness.md).
