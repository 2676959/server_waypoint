# Cross-server release hardening (Step 19)

Status: **implementation and build hardening completed; production release approval remains open**.
The feature remains opt-in and disabled by default. Nothing was published or uploaded.
The archived evidence records the pre-commit validation snapshot.

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

## Remaining production release gates

Step 19 stays open until these are recorded against matching release artifacts:

1. Native Folia transfers with owner/retirement stress and Forge/NeoForge cross-server player sessions.
2. A real protocol-11 modded client browsing changing remote catalogs and initiating confirmed
   teleports through the proxy; Step-18 headless GUI input evidence remains a separate isolated probe.
3. Authenticated player forwarding/online identities and the complete native failure-timing matrix:
   disconnect during transfer/after arrival, target mutation and permission revocation specifically
   between preparation and arrival, and administrative key rotation/revocation under load.
4. Native soak/resource validation. Deterministic protocol, byte-budget and handoff concurrency
   tests establish bounded component behavior, not long-running platform resource health.

The existing real-TCP component suite covers fail-closed race and authorization semantics; it does
not replace these native lifecycle gates. Do not describe this build as production approved.

[Machine-readable results and artifact hashes](validation/cross-server-step19/results.json) and
adjacent logs preserve the evidence without credential contents. Build artifacts are in `builds/`.
