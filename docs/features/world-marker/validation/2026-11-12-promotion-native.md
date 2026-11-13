# Current world marker native acceptance

Date: 2026-11-12. Plan/contract commit: `55d0dd6c`. The smoke ran with then-uncommitted runner changes, identified by the retained source hash. No product Java code changed.

## Result

**PASS** on Paper 1.21.11 build 111 / pinned Java 21.0.11 with two vanilla protocol bots. This is native configuration, packet-identity and lifecycle evidence. It does not render Minecraft, measure client FPS/capacity or certify other backend runtimes.

- [Compact retained receipt](2026-11-12-promotion-native.json): actual rows, transforms, requested settings, client entity IDs, disconnect resources and artifact/runtime/probe/bot hashes.
- Full raw evidence and logs: `/Volumes/ssd/sw-world-marker-promotion-2026-11-12-c/`.
- Failed harness attempt retained separately: `/Volumes/ssd/sw-world-marker-promotion-2026-11-12-a/`.
- Pre-review attempt B is retained at `/Volumes/ssd/sw-world-marker-promotion-2026-11-12-b/`; C supersedes its acceptance coverage.

## Commands and identity

```sh
rtk proxy ./gradlew :paper:1.21.11-paper:shadowJar :paper:1.21.11-paper:performanceProbeJar --console=plain
rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py \
    --root /Volumes/ssd/sw-world-marker-promotion-2026-11-12-c \
    --artifact paper/versions/1.21.11-paper/build/libs/server_waypoint-4.0.1-paper-mc1.21.11-26.1.2.jar \
    --count 2
```

The build and smoke exited successfully. The disposable server bound to localhost. Runtime source worlds/plugins were not reused; owned server and bot processes were stopped by the runner. The passing server log contained zero ERROR/SEVERE/FATAL records.

| Input | SHA-256 |
| --- | --- |
| Candidate Paper JAR | `a9b21e37d2deaddcea29c22e8763e6ac5b8ab65abf946b201fd50c3602c77915` |
| Paper runtime | `82a487ab8c2d0f6f072b8dbaedc9218c85ae3a7d167808124462b248955beec3` |
| Native probe | `987f0f0d827a3ee6b126db430c6b087d54cc967523256389fddc42366905d0c9` |
| Protocol bot | `95f5bb03dde1b4b6efe7c6275572be0d1f47c25398e4c4a4b97c62bfc41bf887` |

## Exercised behavior

- The final item-above-badge/distance layout and block-centre projection were checked on native entity metadata. Two text rows and one GUI-context diamond item were private to each owner; no marker entity was inserted into the server world.
- The two actors had distinct marker IDs. VoxelMap and absent icons removed the optional item, and returning to an item waypoint restored it. White badge contrast, text flags, default distance background, billboarding and interpolation were checked.
- Existing stationary cadence, head-turn, walking and bounded horizontal forecast assertions passed. Counts describe packet submissions, not delivery latency or tick-percentile performance.
- Thirteen setting cases passed: minimum size/radius `0.25`/`1`; maximum `4`/`32` against a target farther than 32 blocks; fractional `1.75`/`8.5`; near/far retargets; fractional-setting preservation across disable/re-enable without setters; configuration while disabled (`0.5`/`6.5`); explicit defaults; save before reconnect; restoration after reconnect; defaults after stop/new-session; Private navigation; and returning to Shared navigation.
- Each case checked fresh size/radius query replies after a queued-feedback barrier and a client-observed chat boundary. The retained receipt includes boundary sequences and exact responses, so older setter replies cannot satisfy readback acceptance.
- The stationary oracle computed expected positions from the native eye sample, raw waypoint block coordinates and requested radius. Each row's full translation/scale, position, entity identity, billboard/duration and absence from the world were checked. Setting-only changes kept IDs stable; disabling/re-enabling legitimately created new rows. Other owners' display records stayed unchanged.
- Both bots disconnected and reconnected with identical offline UUIDs. The owner restored size `1.75`/radius `8.5`; all players' identities were independently matched against the bot readiness event. Stopping navigation and starting a new session restored `1`/`12`.
- An owner created and navigated to a Private waypoint through real commands. The other actor's attempted Private lookup produced fresh client feedback, `Overworld has no lists yet.`, retained its own Shared target/rows and received none of the owner's Private marker IDs. This proves Private store/marker isolation for these operators, not a separate permission-node denial test.
- Enabling the mounted HUD alongside the marker, disabling either independently, stopping navigation and disconnecting all passed. After stop, sessions, display entities and motion tracks were zero; two per-player tick tasks remained while connected. After disconnect, all recorded resources, including those tasks, were zero.

## Failed attempt and correction

Attempt A passed all ten pre-Private setting/reconnect cases but failed creating the Private fixture: its command used the token `position` copied from a Java argument-provider double. The real command requires three numeric coordinates. Attempt B changed that input to `320 -61 320` and passed every phase. The failure is preserved as a harness error; no product defect or fix is claimed from invalid command syntax.

Independent review then found two acceptance gaps in B: its 32-block radius target was only about 28.3 blocks away, and disabled configuration overwrote both values before re-enable. Attempt C added a projection-active distant fixture, a disable/re-enable round trip without setters, and fresh readback assertions. It passed all thirteen cases. B's maximum snapshot accepted even an incorrect radius of 30 in a direct oracle check; C's rejects that value, confirming the repaired endpoint check. Two new readback tests failed before the helper existed, then all ten focused tests and all 247 Python tests passed.

## Proof limits

Native transforms and client entity IDs do not establish rendered row spacing, readability, occlusion quality or smoothness. The run did not separately test permission-node denial, cross-dimension/respawn behavior, destination-chunk load counts, modded/Folia native runtimes, packet bytes or capacity. Projection uses nearby absolute coordinates and does not access a destination world/chunk in the inspected handler, but this receipt does not measure chunk loading. These remain separate acceptance conditions.
