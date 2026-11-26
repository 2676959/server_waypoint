# Command-tree delivery — minimal Step 3

Completed November 16, 2026, against the [current minimal plan](../plans/2026-10-24-server-performance.md).

**Step 3 is complete: three fresh Paper processes, twelve matched native sends/client installations, and expected permission views PASS.** Timings are reported without a latency-budget PASS. Steps 4 and 5 remain pending.

## Retained evidence

- Root: `/Volumes/ssd/sw-performance-minimal-step3-20261116-r2`.
- Run ID: `9badd572-4d89-4258-8a34-71edab04ec47`.
- Attempt: `3799c5cf-e80c-4833-aa58-b3f4515a3f29`.
- All samples and median/maximum summaries: `/Volumes/ssd/sw-performance-minimal-step3-20261116-r2/command-tree/3799c5cf-e80c-4833-aa58-b3f4515a3f29/delivery.json`.
- Each `session-N/` retains its server logs/argv/GC logs, native snapshots, decoded client graphs, client-local action/packet receipts, exact fixture/configuration, disconnected-player observations, and `session.json`.
- `inputs.json` and `source/` bind this attempt to the prepared source hashes. The root retains the empty-world save/reopen manifest, runtime lock, candidate manifest, environment and evidence seals.
- Build/test logs and exact retained probe/bot binaries: `/Volumes/ssd/sw-performance-minimal-step3-20261116-inputs/`. Candidate build provenance: `/Volumes/ssd/sw-performance-minimal-20261115-inputs/`.

| Input | Identity |
| --- | --- |
| Candidate revision | `0ea2d348df673cb9495a8f45b1d7e19a620a790f` |
| Candidate SHA-256 | `a9b21e37d2deaddcea29c22e8763e6ac5b8ab65abf946b201fd50c3602c77915` |
| Probe/agent SHA-256 | `5e66454735cefc0eb0945f4b1e4c43b36d6e2f90b93dd4b40ec4012e58d025e9` |
| Azalea binary SHA-256 | `bff7a17c5a3a4e62046565da786dd0e6613fdf8bb956cb6957f00224c9cac243` |
| Paper server SHA-256 | `82a487ab8c2d0f6f072b8dbaedc9218c85ae3a7d167808124462b248955beec3` |
| Java SHA-256 | `04005388bac0c272ea914210ca519ce94b2f873ea3962b9874a6859f74d7f279` |
| Empty fixture manifest SHA-256 | `105a4abe0f546036bb0754b34bdff9bae2df55cbdc522b2edfa4d53be1f8740a` |

The runtime is Paper 1.21.11 build 111 on the locked Java 21 runtime. All 286 production/build source hashes other than `paper/build.gradle.kts` match the candidate build identity. That one script change only adds ASM and the premain manifest to the separate performance-probe source set/JAR; production configuration is unchanged. The exact candidate JAR has no performance-probe or ASM entries. No production repair, candidate rebuild, commit, push or baseline promotion was performed.

## Measurement boundaries

The vanilla local 1.21.11 `Commands` source is insufficient for this runtime: the actual Paper build uses `sendCommands → sendAsync → runSync`. The premain transformer checks these exact method descriptors and the native connection-send call before enabling evidence. Only JDK-only hooks enter a small bootstrap bridge JAR, because Paperclip isolates the server from the system class loader. Production command code and dispatch behavior are unchanged.

Native send duration starts at `Commands.sendCommands` entry and ends just after `ServerGamePacketListenerImpl.send(packet)` returns. It includes dispatch, queue delays, asynchronous visible-tree copying, sync events and packet construction/indexing, plus observation overhead. This is preparation/connection submission, **not wire delivery** or separately measured asynchronous encoding. Dispatch, async and sync intervals are wall durations on the recorded threads, not CPU time. Their intervals may overlap; do not add them as a second elapsed total.

Client timings use only Rust `Instant`. Initial timing starts before swarm connection setup and ends after the decoded graph is validated/installed; it includes connection/login/configuration work and client installation overhead. Refresh timing starts when the sole pending request is registered immediately before the custom-payload request and ends at installation. Server and client timestamps are never subtracted.

Packet bytes cover the command packet body, excluding packet ID, transport framing and compression; compression is disabled. Deferred native `STREAM_CODEC` encoding and client reconstruction agree in byte length, CRC32 and node count for every sample. Client reconstruction preserves the restricted flag omitted by the pinned Azalea writer. Azalea itself ignores this packet, so the test driver installs and retains its own validated indexed graph and uses it to inspect command paths; this is not a graphical-client rendering claim.

## Permission and correlation checks

The fixed fixture is `tools/server-performance/config/command-tree.json`. Its native configuration is retained and checked: shared `tp=2`, private `tp=2`, ordinary mutation/navigation levels zero, upload/reload level two and upload-delete level four. Each process starts with an empty waypoint catalog. One ordinary connection is tested, disconnected and verified absent, then one operator connection is tested. The operator uses level four.

Expected `/wp` root branches are independently listed in the fixture and all checked. Ordinary login excludes `tp`; granting `server_waypoint.command.tp=true` adds it. Operator login includes `tp`; explicitly setting that node false removes it while keeping the other operator branches. The probe invokes the normal `Player.updateCommands()` path once for the refresh.

Connection token, player UUID, role, trigger, sequence, packet size/CRC and node count must match. Only one refresh may be outstanding. Missing/duplicate/unmatched observations, absent installation/instrumentation or mixed clock labels are BLOCKED. An independently observed wrong permission/path remains FAIL even if another observation is absent. Every final sample has all native timing boundaries.

## Measured results

Each role/trigger has three samples. Values below are **median / maximum**, in milliseconds; three samples do not support tail percentiles. Login client times and refresh client times have the different scopes stated above.

| Role | Trigger | Native entry → submission ms | Client-local ms | Packet body bytes | Nodes |
| --- | --- | ---: | ---: | ---: | ---: |
| ordinary | login | 152.234 / 176.948 | 896.098 / 1127.213 | 74,180 | 4,367 |
| ordinary | refresh | 116.698 / 135.201 | 142.463 / 171.050 | 74,300 | 4,372 |
| operator | login | 100.294 / 139.743 | 276.346 / 279.021 | 107,934 | 6,489 |
| operator | refresh | 76.817 / 94.387 | 103.663 / 139.615 | 107,813 | 6,484 |

| Role / trigger | Dispatch ms | Async interval ms | Sync-to-submission ms |
| --- | ---: | ---: | ---: |
| ordinary / login | 0.197 / 0.226 | 76.032 / 76.748 | 79.028 / 100.686 |
| ordinary / refresh | 0.120 / 0.154 | 55.907 / 70.617 | 60.626 / 64.376 |
| operator / login | 0.029 / 0.032 | 48.930 / 73.221 | 54.458 / 66.460 |
| operator / refresh | 0.027 / 0.036 | 33.151 / 37.105 | 43.602 / 57.228 |

All individual native/client timing pairs (milliseconds):

| Process | Role / trigger | Native ms | Client ms |
| --- | --- | ---: | ---: |
| 1 | ordinary / login | 152.234 | 1127.213 |
| 1 | ordinary / refresh | 103.276 | 131.504 |
| 1 | operator / login | 139.743 | 276.346 |
| 1 | operator / refresh | 94.387 | 139.615 |
| 2 | ordinary / login | 176.948 | 896.098 |
| 2 | ordinary / refresh | 135.201 | 171.050 |
| 2 | operator / login | 100.294 | 279.021 |
| 2 | operator / refresh | 76.817 | 103.663 |
| 3 | ordinary / login | 146.643 | 828.319 |
| 3 | ordinary / refresh | 116.698 | 142.463 |
| 3 | operator / login | 92.222 | 238.400 |
| 3 | operator / refresh | 72.460 | 99.517 |

Server-thread/tick observations are retained separately:

| Process PID | Tick samples | Maximum native tick ms | Maximum sync interval ms | Health / shutdown |
| --- | ---: | ---: | ---: | --- |
| 84922 | 59 | 17.177 | 75.191 | PASS / saved |
| 84987 | 51 | 28.633 | 100.686 | PASS / saved |
| 85047 | 46 | 19.190 | 79.028 | PASS / saved |

The recorded sync interval can exceed the native tick-body duration: command work can execute during Paper’s between-tick task processing. These are separate observations, and tick callback spacing is not substituted for processing duration. No unexplained watchdog/error stall occurred in the final sessions; no extra profiler run was required.

There is no established command-tree latency budget. The fixture records a proposal for later review: native elapsed below 1,000 ms, total owner-thread phases below one 20 TPS tick (50 ms), and client refresh below 1,000 ms. This proposal uses responsiveness/tick units and was sealed before these sessions; it does not award or deny acceptance.

## Verification and review

- 298 Python tests PASS, including missing/duplicate receipt, clock/correlation, wrong permission, partial refresh, close-timeout, abort-error and interruption cleanup regressions.
- Full pinned Rust test suite PASS; release binary build PASS. Native graph tests cover malformed roots/edges/redirects/child cycles and an independently enumerated restricted-flag packet size/CRC.
- `:cross-server:performanceProbeTest` and `:paper:1.21.11-paper:performanceProbeJar` PASS. The three new Java classes also compile separately with `javac --release 17`. No Stonecutter active project was switched.
- Supplemental whole-probe `--release 17` compilation fails on the pre-existing `ThreadMXBean.getTotalThreadAllocatedBytes()` call in `PaperPerformanceProbe`; the normal pinned Java 21 build passes. That unrelated existing API use was not changed.
- Independent review identified two Important failure-path findings; both now have regression coverage and fixes. Receipts are retained before subsequent polling, wrong views are judged independently, and server shutdown/evidence writing survive bot cleanup errors.
- Deferred Minor: hooks remove an active sample at handoff; an unusually delayed scheduling thread can subsequently lose its end boundary. This yields BLOCKED, never PASS. All twelve final samples contain the required ends.
- Three distinct measured PIDs, no native ERROR/SEVERE/watchdog verdict, saved shutdown for every measured process, and no remaining owned runtime process were verified. `git diff --check` and the prepared executable/evidence seals are clean.

## Earlier attempts and scope

`/Volumes/ssd/sw-performance-minimal-step3-smoke-20261116` and `...-r2` stopped on the storage-oracle metadata error; `...-r3` retains the instrumentation classloader crash. Those are harness failures, not candidate acceptance evidence. `...-r4` and `...-r5` are passing short checks. The first prepared root `/Volumes/ssd/sw-performance-minimal-step3-20261116` was not executed after review fixes changed its source seal. None of these earlier attempts was overwritten or relabelled.

Work remains in the existing feature checkout, with uncommitted changes. The actual Paper asynchronous path determines the measurement boundary; this evidence does not certify wire latency, GUI rendering, Step 4 load budgets, other runtimes or a healthy release.

**Next:** Step 4, the six bounded idle/ten-owner navigation-retarget runs.
