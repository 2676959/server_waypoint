# Folia region performance validation

Date: 2026-10-25.

**Result:** Folia distributes the tested work across independent regions. A slow operation still occupies its owner region; it does not occupy a single global game tick as on Paper. At the regular tested loads, all four regions maintained approximately 20 ticks/s. The heavier single-region workload demonstrates how the blast radius differs.

Status: ten native comparative diagnostic runs. These are scoped original-plugin measurements, not full Folia release/matrix/soak acceptance. No production repair was made.

## Regular concurrent workloads

Four real Azalea clients occupied positions approximately 8,192 blocks apart in one fresh flat world. On Folia they had four distinct native region IDs and used four configured tick workers; their work intervals overlapped. On Paper the same four clients and callbacks shared the global server thread. All clients access the same immutable 10,000-waypoint list.

The table reports **full native tick-processing p95**, not merely operation time. Paper values are the most conservative p95 among observers of its one global tick; Folia values are the **worst regional p95** in each of three fresh-process repetitions. No cross-region average is used.

| Workload per actor/region | Paper global tick p95 ms | Folia worst-region tick p95 ms, three repeats |
| --- | ---: | ---: |
| Idle actor/control | 6.72 | 2.44–2.52 |
| One full-catalog fuzzy query per native tick | 25.06 | 8.67–9.16 |
| One native completion request per native tick | 19.29 | 7.21–8.46 |
| 50 rich-display setter/build calls every five ticks | 10.94 | 4.80–4.95 |
| One terrain-history switch every 20 ticks | 8.40 | 2.18–2.82 |
| 50 vanilla terrain-update calls per native tick | 31.05 | 8.98–9.15 |

The storage workload is intermittent (about 5% of ticks), so its p95 can omit I/O-heavy ticks; raw active-operation timings, p99 and maxima remain in the summary. Display/map batches are component call batches executed in real actor contexts, not a claim of 50 connected navigators per region. Maps are private to each actor, matching the navigation cache’s map-per-player design.

The single-actor reference was similar across engines: full-tick query p95 was 13.49 ms on Paper and 13.91 ms on Folia; completion was 13.16 and 13.97 ms respectively. These results do not establish that an individual query is faster under Folia. The four-region observation concerns work distributed across owners instead of its serial accumulation on one server thread.

## Rendering and the original inventory path

The separate four-actor render diagnostic measured Paper global p95 16.05 ms versus Folia worst-region p95 7.85 ms for 50 actual custom-map render calls every five ticks per actor.

The production inventory listener was invoked through native Bukkit event dispatch with an untagged shulker containing 27 Mending books. At 100 synthetic transfer events per native tick per actor, Paper global p95 was 27.20 ms and Folia worst-region p95 was 10.32 ms. These are synthetic event calls, not measured physical hopper throughput. Folia allocated approximately 0.362 MB per event, or 36.2 MB per regional tick at this demand. That remains a material shared-heap cost even while the short run retains 20 ticks/s. The allocation/metadata path remains a targeted optimization candidate; parallelism does not remove the work.

## Single-region overload demonstration

A separate run used 10-second warm-ups and approximately 30-second measurement windows for baseline and hotspot phases. Actor 0 made 20 actual registered command-completion calls per native tick; the other actors did no diagnostic work. This deliberately high internal invocation load bypasses packet admission/rate limits and is a bounded engine-isolation experiment, not an observed production traffic pattern.

| Runtime / observer | Native tick p95 ms | Achieved ticks/s |
| --- | ---: | ---: |
| Paper, all four actor observers of the global tick | 81.61 | 14.96 |
| Folia, busy region 0 | 79.94 | 15.10 |
| Folia, control region 1 | 0.71 | 20.00 |
| Folia, control region 2 | 0.74 | 20.00 |
| Folia, control region 3 | 0.75 | 20.00 |

The control regions’ continued ticking is measured directly, rather than inferred from a global TPS average. This does not prove complete isolation from shared heap/GC, CPU, storage or application locks. No contention-free or leak-free claim is made.

## Decisions

- User clarification, 2026-10-25: Paper and Folia retain one plugin source and build artifact. Every selected fix to their shared implementation applies to both. Folia's healthier regional timings do not remove a Paper-justified fix from scope or justify a separate Folia version.
- Use the regional results to assess impact and validate the shared candidate. In this test, regular four-region loads retained approximately 20 ticks/s per region; the overload experiment still demonstrated a busy region slowing to approximately 15 ticks/s.
- Retain the large-catalog query/completion and inventory metadata findings in the shared repair queue. They consume owner-thread time and allocate on the shared heap. Select and verify targeted repairs using the combined Paper/Fabric/Folia evidence and measured workload rates; Folia receives each applicable shared fix.
- Defer blanket display, storage and map rewrites on the combined measurements. This is a shared prioritization decision; any later repair justified on Paper also applies to the shared Folia implementation. Vanilla terrain sampling is separate from plugin-added rendering.
- Any accepted shared repair must still be validated on every affected platform and preserve owner-thread access. This diagnostic does not complete the implementation plan’s reusable Folia adapter, all-version matrix, 50-player navigation or long-soak requirements.

## Timing, correctness and provenance

- Folia 1.21.11 build 14, commit `529aabc`, official launcher SHA-256 `f52c408490a0225611e67907a3ca19f7e6da2c6bc899e715d5f46844e7103c39`. Paper reference is 1.21.11 build 111, commit `302c47c`. Same Java 21.0.11, -Xms1G/-Xmx2G, Apple M4/16 GiB and plugin binary as the prior diagnostics.
- Folia’s pinned source has `paperRef=79c77f5d7d409b692b67f75e03e5c138a9283fad`, different from the Paper reference. This is a matched harness/catalog/artifact comparison, not a binary-identical Paper-base experiment. Do not attribute every component timing difference solely to threading; JIT, CPU scheduling and runtime changes also matter. Within-Folia one-region/four-region and hotspot/control observations avoid that cross-runtime inference.
- The immutable published Server Waypoint 4.0.0 Paper artifact is unchanged: SHA-256 `0a1d2ddc07dff13dee1af78c8e1f3b0a50024f5d3b8686f733a512db0fa8ad57`.
- A region-owned entity-scheduler callback invokes the actual registered completion provider, real shared query engine, real terrain store, native map code, actual custom renderer and original inventory listener. Exact 10,000-result counts and absent blocking waits are asserted.
- Each operation is captured on its actor’s owner thread. The next callback reads the previous completed native `TickTime` from that region handle’s `tickTimes5s.timeData` deque, under the handle lock. The recorded native start/end must contain the entire prior operation interval. Source and binary signatures were checked for the pinned runtime. Native tick IDs must be contiguous; region IDs must remain stable and distinct.
- Native `TickTime.tickLength()` provides processing duration; throughput is independently calculated from completed native tick-start timestamps. Callback spacing is never substituted for processing duration. The same method reads Paper’s native tick history on its server thread.
- Ordinary runs use 5-second warm-ups and approximately 10-second samples per workload; a one-second margin is removed from both ends in summaries. Three fresh Folia four-region repetitions retain every region’s distribution. These short diagnostic windows do not satisfy the suite’s default 30s/60s full gate.
- The single-region and Paper comparisons and extra render/inventory phases have one diagnostic repetition each. The initial short smoke and failed join-time teleport setup are retained separately; neither is a passing comparative performance run.
- Fresh worlds only: 361 plugin-ticketed chunks per actor/region, peaceful difficulty and invulnerable test actors. UUIDs/counts are matched independently against the bot driver. No existing Folia network/world/configuration was modified.
- The setup correction defers teleport until the owner scheduler after join registration. The failed immediate-join teleport attempt is preserved at `folia-4-1`; no production behavior was changed to make the test pass.

## Retained evidence

Root: `/Volumes/ssd/sw-performance-folia-20261025/`.

- `evidence/summary.json` / `summary.txt`: per-run, per-case, per-region p50/p95/p99/max, CPU/allocation, throughput and work-interval overlap.
- Each run: `region-results.json`, `bot-ready.json`, `console.log`, `argv.json`, native configuration and exact plugin/probe/launcher bytes.
- `evidence/manifest.json`: verification results and input/source/output hashes; all owned processes are stopped.
- `evidence/minecraft-region.patch`, `region-base.patch`, `region-tps.patch`: pinned official Folia source used to establish native timing/ownership contracts.

[Official Folia scheduling overview](https://docs.papermc.io/folia/reference/overview/) and [pinned region scheduler source patch](https://github.com/PaperMC/Folia/blob/529aabc13852021f6081f8a6069959a449c33785/folia-server/minecraft-patches/features/0001-Region-Threading-Base.patch).
