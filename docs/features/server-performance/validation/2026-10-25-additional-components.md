# Additional server-thread performance measurements

Date: 2026-10-25.

Status: component diagnostics, not full-release or multiplayer performance acceptance. No production repairs were made.

## Result and decision

**Prioritize reducing shared whole-catalog query work at large catalog sizes.** The measured operation costs and allocations justify investigating a targeted repair; native command arrival-rate/mixed-load evidence is still required to validate its benefit. Do not treat every synchronous path as a defect.

| Operation | Paper p95 ms | Fabric p95 ms | Decision |
| --- | ---: | ---: | --- |
| Fuzzy query, 10,000 waypoints; long sample | 13.45 | 12.89 | Prioritize shared query work for large catalogs. |
| Fuzzy query, 50,000 waypoints; three short forks | 21.80–23.06 | 21.59–23.04 | Large per-request cost; avoid repeated full scans/snapshots where semantics permit. |
| Client GUI suggestion helper, 10,000 waypoints; three short forks | 7.20–8.62 | 5.96–6.83 | Excluded from server conclusions: its production caller is the client GUI. |
| Actual native command completion, one 10,000-waypoint list; long sample | 14.00 | 13.76 | Prioritize large-list completion work; it runs the real Brigadier provider. |
| One warm terrain-region switch; long sample | 3.27 | 3.21 | Defer a storage rewrite on this evidence: occasional operation on this SSD, not per-tick work. |
| 50 native terrain-update calls; validated map sample | 11.88 | 12.03 | Material CPU use, but this is vanilla map sampling; no plugin-specific repair established. |
| 50 changing rich text-display updates; long sample | 3.36 | 5.93 | See display decision below. |

## Interpretation

Call-site correction: `WaypointQueryEngine.getSearchSuggestions` is called by the client `WaypointListWidget`; server `CoreWaypointCommand.WaypointNameSuggestion` uses a different path. Executing the GUI helper in a server diagnostic does not make it a real server workload. Native Brigadier completion is measured separately.

The actual completion diagnostic parses `wp details waypoint minecraft:overworld tab_10000 ` through each server's native command dispatcher and invokes the registered release suggestion provider. It verifies the returned future is already complete, without blocking waits, and asserts exactly 10,000 suggestions. Allocation per request is approximately 7.36 MB on Paper and 8.72 MB on Fabric. Smaller 1,000/4,096-waypoint-list screens are retained in roots 9. Network packet truncation/transmission is outside this measured provider path; the provider builds all suggestions before that later work. These are per-list counts, not an assumption that every 10,000-waypoint catalog has one 10,000-waypoint list.

The 10,000-waypoint fuzzy query allocates approximately 7.30 MB per request on Paper and 7.86 MB on Fabric. At 50,000 waypoints the short samples allocate approximately 36.48 MB/request. These are exact named-thread allocation-counter deltas for the measured component, not whole-JVM allocation sampling estimates.

The query workload searches every list in a dimension and returns every matching waypoint. It is deliberately broader than the previous storage audit’s bounded-list commands. That earlier audit passed at 50 reads/sec and five mutations/sec with 10,000 stored waypoints; the two measurements exercise different operations and do not contradict one another.

Each live-display batch contains 50 sequential update calls. Normal navigation refreshes every five ticks: this batch models update-burst cost, not 50 real connected clients and not a batch on every normal tick. The rich case includes the actual production text builder, a formatted JSON name, direction/distance/vertical components, locale translation and the production adapter setter. It excludes entity-data/passenger packet enqueue/transmission. Bare setter and standalone builder cases are also retained.

**Display decision: defer a production repair for now.** The modded rich-update burst is measurable at 50 navigators, but normal updates occur every five ticks. This remains a lower-priority optimization candidate until a real-player workload demonstrates material tick pressure. It is not evidence of a severe standalone TPS collapse.

Paper custom map composition over 50 render calls costs 3.91–7.28 ms p95 in the short runs. Native map terrain sampling costs approximately 11–14 ms for 50 calls in both implementations. The sampling baseline is a warm flat world and does not include movement/exploration/chunk generation, network patches or client rendering. Rendering batches reuse one player/map; these are component measurements, not a 50-player server benchmark.

Modded checks are cheaper than the demonstrated Paper metadata path in the measured ordinary-item cases: 100 untagged stone checks and 50 shulker-of-Mending-books cleanup calls have their own raw timings. No navigation item is present in those workloads. Large custom NBT, invalid-item cleanup, movement/mixin frequency and every other loader/version remain separate cases.

Terrain switches write the outgoing 128×128 snapshot and restore a known destination using the real storage implementation. Files/OS caches are warm. The 50-switch screening burst reuses one map ID and measures deliberately heavy repeated file operations, not an observed 50-player action. Slow/network storage and OS-cold caches were not tested. Do not infer that synchronous I/O is harmless on every host.

## Method and scope

- Native Paper 1.21.11 build 111 with the immutable cached published Server Waypoint 4.0.0 plugin; native Fabric 1.21.11 with loader 0.18.2, Fabric API 0.141.3, and a retained named-development 4.0.0 build. Fabric is not an immutable published-artifact audit.
- Actual production classes execute on each runtime’s server thread in disposable worlds. The shared query engine, terrain store and shared display-handler class bytes are identical between the retained Paper and Fabric artifacts. Minecraft map sampling uses each runtime’s own MapItem implementation.
- Apple M4, 10 cores, 16 GiB RAM, macOS 26.5, Java 21.0.11, -Xms1G -Xmx2G. No timing workloads ran concurrently with one another or with probe compilation.
- Short screens: three fresh processes per platform, 20 warm-up ticks and 40 measured callbacks per case. Long confirmations: 600 warm-up ticks and 1,200 measured callbacks per case (nominal 30s/60s at 20 TPS). These measure callback processing duration directly with nanoTime; callback spacing is never labeled native tick time.
- Per-thread allocated bytes are sampled before and after each workload batch. Harness row construction and JSON writes occur outside the measured interval. Statistics use the existing suite’s nearest-rank percentile helper. No profiler runs are mixed into these unprofiled timing distributions.
- Map corrections: initial root-1 terrain samples are excluded because chunks could unload; roots 4/5/7 force 361 chunks and verify 2,000 colored map pixels before sampling. Root 7 checks that every call increments the native map-update step. The first partial root 2 was deliberately interrupted before rerunning corrected inputs; all raw files remain retained.
- There are no connected clients. The probes create native ServerPlayer objects for APIs requiring players; packet delivery, permission/mixin event frequency, full-server native tick percentiles, sustained offered-rate fulfillment and mixed workloads are not measured here.
- Forge, NeoForge, other Minecraft versions, real multiplayer workloads and full matrix/soak gates remain open. Shared code findings apply to the common implementation; those runtime families still require their own acceptance evidence. The subsequent [Folia region diagnostic](2026-10-25-folia-regions.md) adds four-real-client owner-region measurements, matched Paper references and a regional overload experiment; full Folia acceptance remains open.

## Retained evidence

Root: `/Volumes/ssd/sw-performance-additional-20261025/`.

- `evidence/summary.json` and `evidence/summary.txt`: every included distribution and allocation summary.
- `paper-*/samples.json`, `fabric-*/samples.json`: per-batch raw processing/allocation data, checksums and thread names.
- `paper-*/console.log`, `fabric-*/console.log`, `argv.json`: runtime version, fixture activity, completion and exact launch arguments.
- `src/`, `evidence/source-v2/`, `evidence/source-v3/`, `evidence/probe-v*/`, `tools/`: reproducible harness source, compiled identities and orchestration.
- `evidence/manifest.json`: exact artifact, probe, source, classpath and raw-result hashes.

No production changes or commits accompany these findings. The general cross-platform audit and repair plan remains in force, with measured fix/defer decisions rather than automatic rewrites of every tick-thread path.
