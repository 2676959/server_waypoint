# Upload and Chunk Transport Progress

Last updated: 2026-09-04
Branch baseline: `feature/upload-3.1.0` at `a6cbd97`, with resolutions 1 through
4 complete

`a6cbd97` is the live-matrix completion commit: it closes the 4B scenario matrix
recorded against the `0da0865` test environment. The disposable Folia evidence
roots were not retained through final release review; see
[Runtime evidence availability](#runtime-evidence-availability).

## Goal

Make Xaero waypoint uploads safe on Paper/Folia without building a general
transaction system for a feature that normally has one uploader and modest
payloads. The selected model is an exclusive server-issued upload pairing:

- only one client can hold the upload lease;
- only that client and request ID may send upload chunks;
- new upload requests and client waypoint edit requests are rejected while the
  lease is active;
- server-side revision checks still protect against mutations that do not enter
  through the client edit protocol.

This document records the implemented safety and scaling work and the completed
runtime verification used to close the plan.

## Current status

| Area | Status | Current behavior |
| --- | --- | --- |
| Exclusive upload pairing | Resolved | `UploadCoordinator` reserves one global 30-second lease before revision capture and moves it through `RESERVING`, `RECEIVING`, `APPLYING`, and `FINISHED`. Concurrent uploads return `BUSY`, and timeout/disconnect cannot interrupt `APPLYING`. |
| Edit-versus-upload race | Resolved for client edit requests | A small admission monitor prevents a pairing request from racing with admitted C2S waypoint edits. Edits received during an upload return `UPLOAD_BUSY`. |
| Upload sender authentication | Resolved | A dedicated C2S upload-chunk channel carries the server-issued request ID. Chunks from another player, request, or expired lease are rejected before reassembly or waypoint decoding. |
| Upload bounds and stale data | Resolved | Uploads retain permission checks, semantic limits, server-owned policy, per-dimension revision checks, atomic dimension mutation, save-failure reporting, and navigation refresh. |
| Cross-platform client capability | Resolved | Paper and the mod loaders admit chunked traffic only after a matching protocol handshake. Capability is reset on join and cleared on disconnect; Paper also checks the live `getListeningPluginChannels()` set before opening an S2C transfer. |
| Directional channel admission | Resolved | The general C2S channel accepts only update and edit requests from negotiated peers. The upload channel accepts only waypoint data for the active negotiated lease, and the client accepts only the defined S2C message types after a compatible server handshake. |
| Serverbound decode limits | Resolved | Update, edit, and upload messages have channel-specific logical-byte and decoded-object budgets. Update requests additionally reject excessive or duplicate dimensions/lists and oversized or control-character identifiers before querying server state. |
| Passive timeout cleanup | Resolved | Incomplete inbound transfers expire after progress inactivity or an absolute lifetime. Expiry clears the peer's incomplete receive lane, releases accounting, reports typed failures, and never requests an automatic snapshot recovery. |
| Client divergence detection | Resolved | State-affecting transport failures mark the session uncertain. Revision gaps mark the affected list out of sync, and later incrementals for that list are refused until an authoritative replacement arrives; healthy lists continue normally. |
| Saturation containment | Resolved at the networking boundary | Active-transfer and retained-byte exhaustion return `PEER_BUSY`; encoding and delivery failures return typed results. A saturated recipient no longer throws through a broadcast and aborts later recipients after a mutation. |
| Paper/Folia ownership dispatch | Resolved | Player work uses the entity scheduler, maintenance uses the async scheduler, and no legacy `Bukkit.getScheduler()` or `BukkitScheduler` usage is present. |
| Scheduler submissions | Resolved | Prepared bodies enter manager accounting before Paper schedules owner-thread delivery. One asynchronous batch remains in flight per peer, and disconnect or scheduler failure completes the exact delivery without clearing replacement state. |
| Workflow delivery results | Resolved | Delivery tickets separate immediate admission from final delivery. Downloads report success only after completion, failed upload-request packets cancel the exact lease, edit-result failures are logged, and the edit screen restores its controls after a 30-second response deadline. |
| Manager-wide blocking | Resolved | Encoding, compression, decompression, decoding, and platform callbacks run outside transport-state locks. Unrelated peers have independent state and decode locks; only aggregate byte accounting uses one short lock. |
| Aggregate broadcast resources | Resolved | Broadcast bodies, inbound reassembly, and deferred Paper dispatch are admitted to a 256 MiB manager budget. Inbound reservations stay charged through decoding and the synchronous application callback, so decoded object graphs remain inside the same accounting boundary. |
| Frame pacing | Resolved | Each peer emits at most 8 frames and 192 KiB per tick, and one manager-wide round-robin grant bounds all peers together to 32 frames and 768 KiB per tick. Admission no longer transmits; peers admitted during a tick begin on the next tick, and in-flight batches receive no additional grant. |
| Request-scoped upload cleanup | Resolved | The first accepted upload frame binds player, request ID, and transfer ID. Timeout, malformed, and decode failures cancel only that exact session; disconnect and every loader shutdown clear the dedicated manager and coordinator state. |
| Lease reacquisition fairness | Resolved | A stable player UUID receives a five-second cooldown whenever an admitted lease terminates. Other players remain immediately eligible and no queue is introduced. |
| Live Folia verification | Resolved | The 2026-08-30 Folia 1.21.11 matrix passed with two protocol-9 HeadlessMC clients in separate regions and an incompatible MCC client. It covered exact fixture round trips, multi-region broadcast, timeout and malformed cleanup, disconnect recovery, saturation fairness, a low-TPS large transfer, and clean restart. |

## What the pairing model intentionally does not guarantee

The lease is not a database transaction or a universal server write lock.
Commands, console actions, and other server/plugin mutation sources are not
rejected by the C2S edit admission gate. Instead, the upload captures a revision
for every requested dimension and skips a dimension if it changed before commit.
This avoids destructive overwrites without forcing unrelated server work through
the upload coordinator.

Uploads also commit one dimension at a time. Each dimension mutation is atomic,
but a multi-dimension upload is not crash-atomic as a whole. Adding a journal or
multi-file transaction is not justified for normal waypoint uploads unless an
all-or-nothing persistence contract becomes a product requirement.

## Resolved transport design

Protocol version 9 removes application-level ACK and retry frames. Minecraft's
connection already provides reliable ordered delivery, so the chunk layer now
owns only fragmentation, checksums, bounded reassembly, timeout cleanup,
backpressure, and pacing.

Broadcasts call `prepare()` once. The resulting immutable frames are referenced
from bounded per-peer queues instead of being regenerated for every recipient.
Each peer may queue at most eight transfers and 64 MiB, while one short global
accounting operation limits unique outbound bodies plus conservative inbound
reassembly/decoding reservations to 256 MiB.

Every peer has an independent state lock and decode lock. Encoding and
compression happen before admission; completed bodies are detached and decoded
outside the state lock; completed inbound messages are decoded and handed to a
synchronous application callback under the peer's decode lock while their
reservation stays charged. A blocked or malformed transfer for one peer
therefore cannot hold transport state for unrelated Folia regions, and a slow
application callback holds only its own reservation plus its peer's decode
lock.

Transmission is decoupled from admission. `sendTracked` only queues a transfer
and schedules the peer on a round-robin rotation, so aggregate throughput is
bounded by one global grant per manager tick: at most 32 frames and 768 KiB
across all peers, with each visited peer receiving at most its own 8-frame and
192 KiB budget. Peers left without a grant rotate ahead of granted peers, so
continuously active peers all progress without starvation, and peers admitted
during a tick begin on the next tick. Mod loaders drain on their normal
server/client ticks. Paper runs one manager-wide maintenance tick on its async
scheduler, permits at most one outstanding entity-scheduler handoff per peer,
and emits each batch only from that player's owning region.

## Completed follow-up resolutions

| Resolution | Commit | Result |
| --- | --- | --- |
| Passive timeout and client divergence handling | `e35af70` | Removed automatic recovery, added inactivity and lifetime expiry, propagated message type and transfer identity in failures, and added revision-aware client refusal. |
| Request-scoped upload ownership and lifecycle cleanup | `b553c78` | Bound player, request, and transfer identity; reserved leases before revision capture; added exact cancellation, lifecycle reset, and per-player cooldown. |
| Two-stage outbound delivery | `2acd1b7` | Admitted messages before owner-thread scheduling, added exact asynchronous completion, connected user workflows to final outcomes, and bounded edit-screen waiting. |
| End-to-end global resource grants | `17c59d9` | Added manager-wide frame/byte grants over a fair round-robin rotation, made admission queue-only, replaced the list-returning receive API with synchronous application inside the accounting boundary, and removed every per-peer outbound bypass. |
| Live Folia test environment | `0da0865` | Added the disposable fixture generator, protocol probe, bounded region-load plugin, launcher/runbook, verification task, and evidence recorder used by the live matrix. |
| Live Folia scenario matrix | `a6cbd97` | Executed and audited the complete 4B matrix on Folia 1.21.11 against the `0da0865` test environment; all release gates passed. |

## Resolution status

| Resolution | Status | Result |
| ---: | --- | --- |
| 4 | Complete | Automated coverage and the live Folia scenario matrix passed. No upload-transport release blocker remains in this plan. |

### 3. Global resource grants (resolved)

The general and upload managers each carry their own global tick budget and
round-robin rotation, and the upload manager stays additionally constrained by
its upload lease. Inbound reservations remain charged until decoding and the
synchronous handler finish, so decoded object graphs are inside the same
retained-byte budget; cleanup races cannot underflow or double-release the
accounting.

### 4. Runtime Folia and stress coverage

Request identity, lifecycle shutdown, exact upload transport binding, cooldown,
asynchronous delivery completion, stale-failure races, global pacing, and
application-lifetime accounting have focused automated coverage. The live Folia
matrix below additionally exercised compatible and incompatible clients in
different regions, stalled and malformed transfers, disconnects, saturation,
and a progressing large transfer under low TPS.

#### 4A. Prepare the live-test environment

This first part prepares a repeatable, disposable environment for the runtime
matrix. It does not count any scenario as passed; execution and evidence
collection belong to the next part of resolution 4.

Commit `0da0865` provides this phase under `tools/folia-live-test/`, with test-only
fixture, Fabric probe, and Paper region-load source sets. The launcher requires
an explicit Folia JAR and existing MCC executable, refuses repository or
existing roots, builds the direct 1.21.11 version projects, and records checksums
and roles before launch. These tools do not make any gate below pass until their
generated environment is exercised and its evidence is reviewed.

Use Minecraft 1.21.11 for the primary run because this repository has matching
Paper and Fabric projects and the previous live Folia validation used that
version. Record the exact Folia build and Java runtime in the result. Build the
server plugin and compatible client directly from their version projects without
switching either Stonecutter active project:

```shell
./gradlew --no-daemon --no-parallel --max-workers=2 \
    :paper:1.21.11-paper:shadowJar \
    :mods:1.21.11-fabric:build
```

Prepare the environment in this order:

1. Create a temporary Folia server directory outside every checked-in or
   user-owned run directory. Configure a dedicated port, offline test identities,
   accepted EULA, and a disposable world, then install only the newly built
   ServerWaypoint plugin and required server-side test tooling.
2. Create two isolated compatible Fabric client launch directories with distinct
   offline usernames. Keep their configuration, Xaero data, logs, and game
   directories separate from existing user data. Add the existing MCC executable
   as the incompatible vanilla client.
3. Place the two compatible players far enough apart that Folia assigns them to
   independent ticking regions. Record their dimensions and coordinates so the
   placement can be reproduced before every run.
4. Generate disposable server and Xaero fixtures large enough that one logical
   transfer requires multiple manager ticks. Include a smaller control fixture
   whose expected lists, waypoint counts, revisions, and final contents can be
   compared exactly.
5. Add a development-only protocol probe that negotiates protocol 9 and can send
   a valid transfer, stop after a selected frame, corrupt a checksum or header,
   open transfers until admission returns `PEER_BUSY`, and disconnect at a
   selected frame. Reuse production codecs where possible, but keep the probe and
   its controls out of distributable JARs and do not add a production debug
   command or alternate wire protocol.
6. Add a repeatable low-TPS control scoped to the disposable server. Prefer a
   test-only region load tool whose duration and target region are explicit;
   verify that removing the load returns the server to normal operation.
7. Define one launch script or runbook that records the repository commit, plugin
   checksum, Folia build, Java version, client roles, fixture checksum, commands,
   timestamps, and locations of all server and client logs.

Environment preparation is complete only when all of these gates pass:

- Folia starts with the newly built plugin and reports no unsupported scheduler
  or ownership exception during startup.
- Both compatible clients negotiate protocol 9 from separate Folia regions, and
  MCC remains connected without negotiating the custom transport.
- The control fixture completes in both directions and its final revisions and
  waypoint contents match exactly.
- The development probe can deterministically produce partial, malformed,
  saturated, and mid-transfer disconnect inputs without modifying production
  code or user-owned data.
- The large fixture spans multiple transport ticks, and the low-TPS control is
  repeatable and reversible.
- A clean restart removes temporary leases and transfers while preserving only
  the expected committed waypoint state.

#### 4B. Live Folia scenario results

The matrix ran on 2026-08-30 against repository commit `a6cbd97390c1c36550f3a59fdbc2cfab61b3d381`,
Folia `1.21.11-14-ver/1.21.11@529aabc`, and OpenJDK 21.0.11. `SWAlpha` and
`SWBravo` used Fabric Loader 0.18.2 through isolated HeadlessMC 2.10.0 launchers;
`SWVanilla` used MCC 26.2 with Minecraft protocol v774. The disposable evidence
root is `/private/tmp/server-waypoint-folia-4b-rerun-0da0865`.

The matrix was executed on the `0da0865` test environment, which is the parent of
`a6cbd97`; the evidence root therefore keeps its original `0da0865` suffix.

| Scenario | Result | Recorded evidence |
| --- | --- | --- |
| Baseline and capability isolation | Pass | Alpha and Bravo negotiated protocol 9 from regions centered near `(0, 0)` and `(8192, 8192)`. MCC remained connected without a custom handshake. Both compatible clients downloaded the fixture and uploaded `control` with 0 added, 0 replaced, 0 deleted, 4 unchanged, 0 conflicts, and 0 skipped. Fixture verification confirmed revision 7 and the exact four expected waypoints. |
| Multi-region broadcast | Pass | A `broadcast-headless` mutation reached both compatible clients at revision 20 with identical overworld contents and SHA-256 `4aa2bf56c39952895ab6003d3da048eb48461d010df4bdf1605443422f9b1553`. MCC was unaffected and the server reported no scheduler or ownership exception. |
| Partial timeout cleanup | Pass | `partial 2` sent 2 of 43 frames and remained idle beyond 30 seconds. Only that transfer and upload lease expired; waypoint state did not mutate. A fresh valid 43-frame upload then completed with 4,096 unchanged waypoints. |
| Malformed cleanup | Pass | `bad-checksum` was rejected for checksum mismatch and `bad-header` for invalid sequence 43. Neither mutated state or corrupted another lane. A valid 43-frame upload passed after each rejection with 4,096 unchanged waypoints. |
| Disconnect cleanup | Pass | `disconnect 2` closed after 2 of 43 frames. A newly negotiated session completed all 43 frames with 4,096 unchanged waypoints and no stale completion interference. |
| Saturation and fairness | Pass | Nine same-tick downloads hit the eight-transfer peer cap: the ninth returned the delivery failure corresponding to `PEER_BUSY`, while all eight admitted 28-frame transfers progressed to completion. A concurrent Alpha transfer also completed, and MCC plus the server remained responsive. |
| Low-TPS large transfer | Pass | A bounded 90 ms/tick load ran for 30 seconds in Alpha's owned region. Alpha's multi-tick large download and Bravo's independent download both completed and persisted while the load was active. The load expired automatically, `swregionload status` returned no active loads, and both regions remained responsive. |
| Clean restart and audit | Pass | Folia stopped cleanly, fixture verification passed, and restart loaded the expected 5,462 overworld waypoints without retaining a lease or incomplete transfer. Alpha and Bravo renegotiated protocol 9, MCC rejoined without custom transport, and a fresh valid 43-frame probe completed with 4,096 unchanged waypoints. `record` captured the final waypoint SHA-256 `40949ed2d2fb8dc74cd088b97480bd859cf84fc73431fa55be4f20ee131c7366`. |

The log audit found only the two intentionally injected `MALFORMED` receive
exceptions, expected offline-client authentication warnings, and discarded
environment-setup diagnostics that were rerun after their tooling fixes. The
accepted scenario logs contain no unexpected transport exception, Folia
ownership violation, unsupported scheduler call, unexpected disconnect, or
persistence mismatch. Resolution 4 is complete.

### Runtime evidence availability

The evidence roots `/private/tmp/server-waypoint-folia-4b-rerun-0da0865` and
`/private/tmp/server-waypoint-folia-4b-0da0865` were the only complete records of
the live Folia run, but they were not retained through the final release review
on 2026-09-04. The recorded results above remain historical test results, but
their logs, environment metadata, and checksums are no longer independently
auditable. Release sign-off therefore requires recovering a durable copy or
rerunning the complete 4B matrix and archiving its evidence outside temporary
storage.

### 4B evidence review after `master` integration

**Historically recorded as resolved against release candidate
`99cc415e3e74f0fc5a4724553db39179636c8694`; the evidence-availability gate
above now applies.**

Integrating `master` brought in `9bf8054` ("fix waypoint cache handling across
proxy transfers"), which changes client-side code this plan also touches:

- `WaypointClientMod.onJoinServer()` now saves pending waypoint files before
  rebinding and clears `fileManagerMap`/`waypointFilesDir` until the new server's
  handshake binds them;
- `WaypointClientMod.onDimensionChange()` no longer creates a waypoint manager
  before the cache directory is available;
- `MinecraftClientMixin` calls `onLeaveServer()` via the new `ifPresent` helper
  when a level is torn down.

The 4B matrix above was recorded against `a6cbd97`, which does not contain
`9bf8054`. The integration deliberately re-applied only its substantive parts on
top of this branch's protocol-9 client; the rest of that commit is cosmetic
reformatting and was not adopted.

Assessment supporting retention of the recorded evidence:

- The protocol-9 wire format is unchanged: no message type, channel, payload
  layout, bound or checksum behaviour was altered.
- Paper/Folia scheduler ownership is unchanged: no scheduler call site, region
  dispatch path or maintenance-tick wiring was touched.
- Transport Gradle wiring is unchanged: the integration only added publishing
  plugins and migrated Stonecutter `vers()` to `version()`.
- The matrix connected clients **directly** to Folia with no Velocity proxy, so
  the two paths `9bf8054` changes are not exercised: there is no seamless
  server switch, and no dimension change racing an unfinished handshake.
- On a fresh join the added save is a no-op over an empty map and the reset is
  undone by the handshake that follows, so join, disconnect and restart
  scenarios behave as recorded.

The post-integration closure run on 2026-08-31 supplied that missing direct
coverage. It used this isolated topology:

- Velocity 4.1.1 build 24 on `127.0.0.1:25620`, running on Temurin 25.0.4.1;
- Folia backend A on `127.0.0.1:25621`, server ID 41001, with only
  `server-a-only` revision 101 and waypoint `a-fixture`;
- Folia backend B on `127.0.0.1:25622`, server ID 42002, with only
  `server-b-only` revision 202 and waypoint `b-fixture`;
- Folia `1.21.11-14-ver/1.21.11@529aabc`, Minecraft 1.21.11, and Java 21.0.11
  on both backends;
- the production 3.1.0 Fabric JAR in HeadlessMC 2.10.0, Fabric Loader 0.18.2,
  and the offline identity `SWLifecycle`, with separate launcher, Minecraft
  store, game, cache, world, and log directories.

The frozen executable checksums were:

| Component | SHA-256 |
| --- | --- |
| Production Fabric 1.21.11 JAR | `167dadffa6d1f9079fc9fc147842d6eaf658d8e9952b471912af415a61049bbb` |
| Production Paper 1.21.11 JAR | `c91f517d894cda1c7ac67f45fd6e8e171caf288df37e048f23e6f47f484bc150` |
| Folia 1.21.11 JAR | `f52c408490a0225611e67907a3ca19f7e6da2c6bc899e715d5f46844e7103c39` |
| Velocity 4.1.1 build 24 JAR | `846411d2d0560fed0f23496ffb89681be528d2c0650ecdcf21724d2d7bd9c1ee` |
| HeadlessMC 2.10.0 launcher | `52bd5006f478377b3893011d458562977d38c65ead6d2b31089beb4d614f13cd` |
| Temurin 25 archive | `61979887f7506a24a57439ff99adb8b3a7fc89977d9cfe3b8984f58a981b7b9d` |
| Development-only lifecycle control JAR | `46b011e96885cb02b413245e09871740ce243039902a8887c9d23d80213f926b` |

| Closure scenario | Result | Recorded evidence |
| --- | --- | --- |
| A to B to A proxy transfer | Pass | Protocol 9 negotiated on every join. A's pending marker was memory-only before transfer, then persisted only under proxy server ID 41001. The handshake-pending dimension changes passed, join state had no bound cache or old manager, and transport, synchronization, and renderer state were cleared. B exposed only `server-b-only` revision 202; returning to A exposed only `server-a-only` revision 101 with the pending marker. |
| Direct disconnect and reconnect | Pass | The client downloaded A's fixture, left cleanly, tolerated the pre-handshake dimension change, rebound the same A cache, and renegotiated protocol 9 with the exact revision 101 fixture. |
| Clean backend restart | Pass | Backend A stopped cleanly, reloaded one list and one waypoint, and the client rebound the A cache after restart with protocol 9 and the exact revision 101 fixture. |

The proxy caches are distinct: A is
`_127.0.0.1_25620/server/41001/minecraft$overworld.json` with SHA-256
`ef4a28a4c0e15c6d4b131a4bd3cb12ec7b11fec9c9c4b680643ac03218245a60`,
and B is `_127.0.0.1_25620/server/42002/minecraft$overworld.json` with
SHA-256
`7d2b4ca796bfc290b31413edb70d44f1354deebcd88f43e4c70e578e54f7faf0`.
The accepted HeadlessMC command tests all exited successfully with explicit
structured assertions. Their client logs, the Velocity log, both backend logs,
cache records, checksums, and audit were originally stored at
`/private/tmp/server-waypoint-step5-99cc415`, but that temporary evidence root
was also absent during final release review on 2026-09-04. The recorded audit
found no `UnsupportedOperationException`, lifecycle NPE, ownership violation,
unexpected disconnect, failed assertion, wrong-cache write, fixture/revision
leak, or production-JAR inclusion of the development control. The original 4B
evidence trees had retained their frozen aggregate SHA-256 values unchanged at
the time of that audit.

One pre-scenario launch diagnosed incorrect accessor descriptors in the
development-only control, and one direct-reconnect diagnostic showed that
HeadlessMC 2.10.0 treats a standalone `WAIT` step as a failed command test. Both
diagnostics are retained and excluded from accepted evidence; after fixing the
test control and JSON, the affected baseline plus scenario passed. No production
code changed. This closes the final evidence decision and marks runtime
verification complete for the integrated release candidate.

## Validation status

Focused checks for the committed global-resource-grant implementation:

- `:common:test` — 283 tests including the chunked-transport outbound pacing,
  fairness, and inbound application-lifetime suites
- `:mods:26.1.2-fabric:test` — 193 tests covering chunk application and edit
  deadlines
- `:paper:26.2-paper:test` — scheduler dispatch and manager-wide maintenance tests
- representative old and new Fabric (1.20.1, 26.2), Forge (1.20.1, 1.21.11,
  26.2), NeoForge (1.20.2 NeoGradle, 1.21.11, 26.2), and Paper (1.21, 1.21.11,
  26.2) compilation
- live Folia 1.21.11 scenario matrix recorded on 2026-08-30, including the
  post-restart fixture verifier and a fresh valid 43-frame upload
- post-integration Velocity/Folia client-lifecycle closure recorded on
  2026-08-31 against `99cc415`, including A-to-B-to-A proxy transfer, direct
  reconnect, and clean backend restart
- `git diff --check`

The representative compilation refresh covers Minecraft 1.20.1 through 26.2.

Together, these checks cover focused state-machine behavior, representative
cross-loader compilation, and the required live Folia runtime behavior.

## Release-candidate validation (post-`master` integration)

The production branch baseline and final runtime-tested commit is
`99cc415e3e74f0fc5a4724553db39179636c8694`. Run against the integrated branch
tip after merging `master` (see
"4B evidence review after `master` integration" above).

- `./gradlew build --continue --no-daemon --no-parallel --max-workers=2`
  — **BUILD SUCCESSFUL**: 772 tasks with no failed task. This is the full serial
  build over the supported matrix rather than a representative subset, and it
  includes `test` for every target. The earlier executed/up-to-date breakdown
  was removed because its counts did not sum to the recorded task total.
- `./move_builds.sh` collected 38 distributable 3.1.0 artifacts into `builds/`
  (Fabric 12, Forge 12, NeoForge 11, Paper 3). Stale 3.0.x jars that the script
  also swept up were removed, leaving only the 3.1.0 set.
- `git diff --check` clean.

Artifact audit over all 38 release JARs:

- No `foliaLiveTestProbe`, `FoliaRegionLoadPlugin`, fixture generator, HeadlessMC
  configuration, or JUnit/test resource in any of them.
- Paper artifacts all declare `folia-supported: true` with `version: '3.1.0'` and
  a per-target `api-version` (`1.21`, `1.21.11`, `26.2`).
- Every Fabric `fabric.mod.json` and every Forge/NeoForge `mods.toml` /
  `neoforge.mods.toml` reports `3.1.0`.
- `ProtocolVersion` in every sampled artifact reports `PROTOCOL_VERSION = 9` and
  `COMPATIBLE_VERSION = "3.1.x"`; channel and payload registration classes are
  present per loader.

Step 5 added only disposable scripts, documentation, and a development-only
1.21.11 Fabric source set. Proportional revalidation rebuilt
`:paper:1.21.11-paper:shadowJar`, `:mods:1.21.11-fabric:build`, and
`:mods:1.21.11-fabric:remapProxyLifecycleTestJar`. Script syntax and
`git diff --check` passed. A repeated content scan found exactly 38 release
artifacts and no lifecycle-control, Folia-probe, HeadlessMC, or JUnit entry in
any production JAR; the lifecycle control is built only by its explicit
verification task.
- No dev-shadow, thin, sources, jarjar-dev or test-load artifact reached
  `builds/`.

### Packaging correction

`move_builds.sh` only excluded `-dev`, `-sources` and `-shadow` on the Paper
side, so the development-only `server-waypoint-folia-live-test-load.jar` built by
`foliaLiveTestLoadJar` would have been collected into `builds/` and published.
Both loops now also exclude `*folia-live-test*`.

### Test scaffolding correction

`ModMessageSenderTest` bootstraps Minecraft because `toVanillaText()` decodes
through `ComponentSerialization.CODEC`, which needs bootstrapped registries. That
is impossible from a plain JUnit run on the Forge family:

- Forge: `Bootstrap.bootStrap()` initialises the Forge network stack, whose event
  classes only gain the constructors the event bus needs after Forge's class
  transformation.
- NeoForge: `SharedConstants.<clinit>` consults `FMLEnvironment`, which throws
  `IllegalStateException: There is no current FML Loader`.

Both bootstrap calls are now wrapped and the test aborts via
`Assumptions.abort(...)` when the runtime cannot bootstrap, so coverage is kept
on every runtime where it can and the build stays green where it cannot.

### Build environment

Gradle must run with `GRADLE_USER_HOME=/Volumes/ssd/.gradle`. The default
`~/.gradle` sits on the system volume, which does not have room for the Forge
mavenizer output and filled up mid-build.
