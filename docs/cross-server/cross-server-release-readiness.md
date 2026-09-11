# Cross-server release hardening (Step 19)

Status: **implementation, build hardening and the four representative native release gates passed**.
The feature remains opt-in and disabled by default. Nothing was published or uploaded.
The archived evidence records the tested snapshots and their distinct artifact hashes.
No release has been published.

## Changes

- Real-socket seeded property tests exercise 128 arbitrary fragment partitions and 64 invalid offsets
  in each transport mode. Exact retained-byte/message ceilings close a flooded session and permit
  a fresh admitted connection. Existing codec mutation, handoff race, slow-peer, reconnect and
  credential-revocation tests remain enabled.
- Native Paper 26.2 exposed an Adventure `TextComponent.Builder.build()` binary-linkage failure in
  remote browsing. Remote feedback now appends components to a neutral immutable root, preserving
  styling and avoiding that incompatible inherited builder call.
- Native Fabric 26.2 invokes its JOIN callback before `PlayerList` installs the player in its UUID
  lookup. Immediate arrival checks retired a valid handoff. Mod scheduling now always queues an
  owner task; Paper scheduling always uses the player's next entity tick. Both recheck player
  identity and shutdown when the task runs. Two API-double regressions execute the actual adapter
  sources with delayed registration, replacement and shutdown. Native transfers pass after the fix.
- Artifact collection selects exact final filenames, copies build outputs and includes the shaded
  Velocity plugin. It excludes Forge `jarjar-input` and other intermediate JARs by construction.
  The verifier checks all 39 exact targets, duplicates, loader distribution, relocated Noise and its
  notice, and accidental test/probe classes. Eight synthetic positive/negative tests cover the gate
  and collector; synthetic ZIPs are not runtime evidence.
- The [administrator guide](cross-server-admin.md) covers trusted public-pin exchange without
  certificates, plaintext local-process trust, binding/firewalls, mappings, permissions, rotation,
  revocation, recovery and troubleshooting. [Release notes](cross-server-release-notes.md) explain
  opt-in defaults and the independent protocol versions.

## Automated verification

On 2026-09-09, `./gradlew build --continue --max-workers=2 --console=plain` passed all 40
Stonecutter version projects (including two development-only targets), common, proxy-common and
Velocity. No active Stonecutter version changed. A subsequent common/proxy/Velocity check includes
both newly added scheduling regressions: 539 common, 106 proxy and 6 Velocity tests, no failures or
skips. Across the version matrix, XML reports contain 8,327 tests, zero failures/errors and 12 skips
of the existing Minecraft-bootstrap-dependent text conversion case on Forge-family JUnit runtimes.
The skips are listed individually in the evidence; they are not reported as passing native checks.

Fresh `./gradlew -p tools/noise-spike :kk:check --offline --rerun-tasks --console=plain` passed
35 checks, including external known-answer vectors, nonce boundaries and isolated JVM exchanges.
`python3 tools/test_release_artifacts.py` passed eight cases. `./move_builds.sh` followed by
`bash tools/verify-release-artifacts.sh` passed all 39 actual staged artifacts (12 Fabric, 12 Forge,
11 NeoForge, 3 Paper, 1 Velocity). `git diff --check` and shell syntax checks pass.

Earlier attempts were blocked by sandboxed Gradle-cache access and unavailable dynamic Maven
metadata. After network access was enabled, the full build succeeded without dependency overrides.
The initial artifact collector admitted 12 Forge intermediate JARs; the corrected exact-name
collector and its regression now reject that failure mode.

## Native evidence

Disposable loopback Paper 26.2-24, Fabric 26.2 with Fabric Loader 0.19.3 and API 0.152.1+26.2, Velocity 4.1.1,
Java 25.0.3 and MCC 26.2 build 499 (Minecraft protocol 776) exercised the production artifacts.
The exact installed loader/API versions and SHA-256 hashes are recorded with the logs.
The native backend JARs match staged release hashes. The native Velocity JAR predates the Adventure
fix; ZIP-entry comparison shows its only difference is the backend-only `RemoteWaypointCommand.class`.
Proxy runtime classes are identical, but the distinct artifact hashes remain explicit in the evidence.

- Fresh separate keys were generated; missing pins failed closed. Trusted public-pin exchange and
  exact Velocity mappings enabled KK startup. Fixture mapping and MCC-version errors were corrected
  before successful scenarios; earlier logs are retained separately from passing assertions.
- KK remote browsing and transfers succeeded in both directions between Paper and Fabric.
  Console queries confirmed destination coordinates (5.5, 80, 30.5) and (25.5, 80, 30.5).
- A live waypoint edit appeared in the other server's cached remote list. A later fresh transfer
  reached (45.5, 80, 30.5).
- Revoking the destination player's operator permission caused arrival denial and retained the
  previous coordinates. Restoring the fixture permission enabled a fresh successful handoff.
- Restarting Velocity while both backends stayed running caused reconnect and full republish;
  browsing and a new transfer succeeded afterward.
- Explicit plaintext succeeded in both directions. Existing private-key files were unchanged.
  Destination shutdown invalidated its availability and rejected a subsequent remote request.
- All disposable processes were stopped. Local fixtures were isolated from user worlds.

These are offline-account tests with forwarding NONE, not authenticated online forwarding evidence.
MCC supplies an ordinary vanilla protocol session, not a modded protocol-11 GUI session. Coordinates
and completion feedback were observed; exactly-once event counting is covered by component tests
and earlier Paper evidence, not a native Fabric teleport-event counter in this run.

## Current native gate status

Representative Folia, Forge and NeoForge transfers and reconnects have been recorded,
and the real protocol-11 GUI has completed a network catalog change and confirmed
proxy transfer. The follow-ups below retain each runtime, identity, artifact and
instrumentation limit. These representative versions do not imply native coverage
of every supported version.

Authenticated Folia with modern forwarding and NOISE_KK has also exercised destination
permission and waypoint changes between preparation and arrival, first-tick disconnect,
backend revocation, and backend key rotation. That run used the earlier race-fix proxy;
final-candidate validation repeated the mutation checks and both backend and coordinator
key rotation, including restart with one authenticated player connected. Old pins fail
closed; updated trusted pins restore authenticated bidirectional transfers. See the
[final-candidate evidence](validation/cross-server-step19-authenticated-final/results.json).

The final-candidate resource gate passed a 1,801.9-second authenticated soak after key
rotation: 60 clean reconnect sessions, 120 completed transfers and 62 resource samples.
File descriptors stayed at 151 per backend and 59 for Velocity; coordinator readers
stayed at 32. Retained heap after explicit GC decreased by 1,320/1,229 KiB on the
backends and increased by 247 KiB on Velocity. Thread counts stabilized, with no runtime
error or wrong-owner log matches. [Soak evidence](validation/cross-server-step19-soak/results.json).
The separate pressure check completed two authenticated transfers while the 32-connection
ceiling was held, expired 30 stalled handshakes and returned to two legitimate sockets.

Two earlier soak attempts stopped before new logins after 6 and 16 cycles. macOS reports
identify MCC CoreCLR SIGABRT before any proxy connection. They remain archived as failed
attempts. The passing run set `DOTNET_ReadyToRun=0` only for MCC, disabling precompiled
.NET code as documented by [.NET](https://github.com/dotnet/runtime/blob/main/docs/workflow/debugging/coreclr/debugging-runtime.md).
The exact upstream crash defect is unconfirmed; no server change was made for it.

These four gates are closed for the recorded representative configurations. The 30-minute
one-player workload does not establish overnight or high-player-count health, and the
headless GUI run does not provide visual screenshot approval. Forge/NeoForge ownership
is supported by the production guard and arrival observations, without independent
adapter callback instrumentation. All disposable processes stopped cleanly; no user
worlds or production services were modified by the fixtures. Private authentication
data is excluded from the archived evidence.

[Machine-readable results and artifact hashes](validation/cross-server-step19/results.json) and
adjacent logs preserve the evidence without credential contents. Build artifacts are in `builds/`.

## Folia follow-up — 2026-09-09

The representative Folia 1.21.11-14 run found a second arrival-order race: the
backend's next entity tick can still precede Velocity's route installation.
A diagnostic proxy observed `currentServer=Optional.empty` at the rejected claim.
The coordinator fix defers one validated claim per bounded transfer until the
proxy switch completes, then preserves the normal live-route checks.

After the fix, native bidirectional repeated transfers and two-player transfers
between separated regions reached the expected coordinates on two Folia tick
threads. Audit snapshots recorded current player identity and region ownership.
A controlled kick on the first destination tick produced the audit retirement
callback; removing the marker allowed a fresh successful transfer. Disconnecting
after arrival and reconnecting also allowed a fresh transfer. The test-only audit
did not receive Folia PLUGIN teleport events, so this is not native exactly-once
event-count evidence. Offline accounts, forwarding NONE and loopback PLAINTEXT
were used; authenticated forwarding, native KK and soak gates remain separate.

The Paper plugin matches the original staged release artifact. The fixed Velocity
plugin was rebuilt from the working tree; original, diagnostic and fixed hashes
are retained separately. See [Folia evidence](validation/cross-server-step19-folia/results.json).
The earlier full-matrix result belongs to the pre-fix snapshot; the follow-up runs
common/proxy/Velocity checks and a separate 39-artifact audit with the new proxy.


### Forge follow-up — 2026-09-10

Forge 26.2 / 65.1.3 with Proxy Compatible Forge 1.3.1 and Velocity 4.1.1 build 24
passed four synchronized bidirectional arrivals over two real client sessions,
including graceful disconnect/reconnect recovery. Protocol 11 reached `SYNC_FINISHED`
and the client tick event bus remained active. Destination positions were
`(5.5, 80, 30.5)` and `(8197.5, 80, 30.5)`.
[Logs, hashes and limitations](validation/cross-server-step19-forge/results.json).

The initial disposable control probe lacked resource-pack metadata. Forge's startup
warning screen disabled its event bus while Quick Play connected, leaving client
synchronization queued. Correcting the probe metadata restored the normal startup
path; final synchronized transfers had no chunked delivery warning. This was a
fixture defect. PCF was required for this native Forge/proxy configuration. These
runs used offline identities, modern forwarding and loopback plaintext coordinator
transport; they do not close authenticated identity or controlled handoff-disconnect
timing gates. Ownership is supported by the production guard and successful native
arrival, without independent adapter callback instrumentation.


### NeoForge follow-up — 2026-09-10

NeoForge 26.2.0.3-beta / Minecraft 26.2 with PCF 1.3.1 and Velocity 4.1.1 build 24
passed four bidirectional arrivals across two real client sessions, expected positions
on both backends, and graceful disconnect/reconnect recovery. Protocol 11 reached
`SYNC_FINISHED` with advancing ticks. [Evidence](validation/cross-server-step19-neoforge/results.json)
retains exact runtime/artifact versions. As with Forge, these offline modern-forwarding
runs do not establish online identity authentication or controlled in-handoff retirement;
no independent production ownership callback probe was installed.


### Live protocol-11 GUI follow-up — 2026-09-10

A real Fabric 26.2 client passed remote browser navigation, receipt of a backend
catalog mutation while the browser remained open, exact selection of the new entry,
teleport confirmation through native mouse dispatch, proxy transfer, and fresh
synchronization at `(45.5, 80, 30.5)`. [Evidence](validation/cross-server-step19-live-gui/results.json).
The probe injects neither catalogs nor network state. Headless rendering is not visual
approval; this run used an offline identity, forwarding NONE and loopback plaintext.


### Authenticated forwarding and failure timing — 2026-09-10

The selected online profile AuthenticatedTestPlayer reached both Folia backends with the same
UUID through Velocity modern forwarding and authenticated NOISE_KK coordinator sessions.
A destination entity task revoked teleport permission after preparation and arrival was
denied. Moving the target in that window used its fresh position; kicking on the first
entity tick retired the pending arrival and allowed source fallback and a fresh retry.
Disabling backend b and presenting a newly generated backend key against the old pin
both left its catalog stale and rejected requests. Installing the new trusted public
pin restored availability and bidirectional transfers.
[Recorded logs and exact artifact scope](validation/cross-server-step19-authenticated/results.json).

A separate 64-socket attempt hit the accepted coordinator connection ceiling of 32;
30 stalled handshakes closed by the deadline observation and the count returned to the
two legitimate backend connections. A subsequent authenticated transfer passed. The
transfer occurred after pressure was released; it is not a transfer-under-pressure claim.


### Final candidate checks — 2026-09-10

After the Folia route-installation fix and registry-scan refinement, scoped
common/proxy-common/Velocity checks passed 539/114/6 tests with no failures, errors
or skips. The final staged Velocity artifact has SHA-256
`be67bf9998b72b3fb3cb87aa2d402cf944edd2b3dd9d72eed4ba2f830ddd865f`.
All 39 staged release JARs passed the exact-target/content audit, and all eight
release-tool regression cases passed. The earlier full matrix remains the pre-fix
matrix result; only proxy runtime classes changed in this follow-up.
