# Paper transfer protection: enabled versus no-check baseline

Date: 2026-10-26.

**Result:** real-transfer protection adds 11.38 microseconds and 1,016 allocated bytes per event in this fixture. Full mean MSPT changes from 1.7805 to 1.9129; full p95 changes from 2.5774 to 3.8414 ms. Physical movement is identical in both modes. Protection is measurably more expensive than no checking, even though tick rate stays near 20 TPS.

This follow-up isolates the remaining cost of the optimized inventory-transfer protection. The earlier before/after repair comparison did not measure that cost against no checking. Both modes here load exactly the same optimized plugin and dispatch exactly the same transfer events; only execution of the production `InventoryMoveItemEvent` protection callback changes.

## Full native server tick duration

Three matched pairs, 600 measured native ticks per case per run. Each mode therefore contributes 1,800 measured ticks per case. Means and nearest-rank p95 values below pool all three runs; pair-level mean differences follow separately. Values are full completed native processing durations, including the common diagnostic observer, and exclude the server tick sleep.

| Workload | No check mean MSPT | Protected mean MSPT | No check p95 ms | Protected p95 ms | Mean delta ms | Pair mean delta range ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 synthetic events/tick | 1.4684 | 1.7281 | 2.0245 | 2.3778 | +0.2597 | +0.1582 to +0.3881 |
| 1,000 synthetic events/tick | 1.5572 | 3.3326 | 2.0449 | 4.5556 | +1.7755 | +1.6311 to +1.9100 |
| Physical hopper circulation | 1.7805 | 1.9129 | 2.5774 | 3.8414 | +0.1324 | +0.0872 to +0.1988 |

## Timed workload and allocation

Synthetic batch timing includes Bukkit event dispatch, the identical measurement wrapper and its production callback when enabled. Event construction and item setup occur before timing. These synthetic calls do not move items. Allocation is the exact `ThreadMXBean` delta on the server thread, not total JVM allocation. Byte counts below are exact thread-allocation deltas.

| Synthetic workload | No check mean / p95 ms | Protected mean / p95 ms | No check mean bytes/tick | Protected mean bytes/tick | Added mean bytes/event |
| --- | ---: | ---: | ---: | ---: | ---: |
| 100 synthetic events/tick | 0.0375 / 0.0522 | 0.2982 / 0.4680 | 0.4 | 101,600.0 | 1,016.0 |
| 1,000 synthetic events/tick | 0.3069 / 0.4479 | 2.3085 / 3.3208 | 0.0 | 1,016,000.0 | 1,016.0 |

The no-check 100-event baseline includes one 720-byte allocation sample; it is retained in the distributions. The baseline protection wrapper itself allocates zero bytes in every measured callback.

For the real physical workload, the timed quantity is the sum of protection-callback durations in each native tick. Hopper movement itself remains inside the full MSPT metric above. The baseline wrapper still runs and measures its empty branch, so its timer/counter overhead is present in both modes.

| Workload | No check callback mean / p95 ms/tick | Protected callback mean / p95 ms/tick | No check callback bytes/tick | Protected callback bytes/tick | Net callback time/event across pairs |
| --- | ---: | ---: | ---: | ---: | ---: |
| 100 synthetic events/tick | 0.00337 / 0.00467 | 0.26331 / 0.42384 | 0.0 | 101,600.0 | +2.0912 to +3.3220 microseconds |
| 1,000 synthetic events/tick | 0.03126 / 0.04550 | 2.02628 / 2.94008 | 0.0 | 1,016,000.0 | +1.8036 to +2.1097 microseconds |
| Physical hopper circulation | 0.00060 / 0.00525 | 0.20930 / 1.57954 | 0.0 | 18,640.2 | +10.5990 to +12.2178 microseconds |

A second allocation interval runs from the LOWEST tick-start event handler to the MONITOR tick-end handler, after observing physical inventory increases and before constructing the sample snapshot/JSON row. It includes server/world processing and common probe work within those bounds. It excludes the remaining native tick tail and sample construction, so it is not labeled exact full-tick or whole-JVM allocation.

| Workload | No check mean tick-event interval bytes | Protected mean tick-event interval bytes | Pair mean delta range bytes |
| --- | ---: | ---: | ---: |
| 100 synthetic events/tick | 6,762.3 | 108,311.8 | +101513.6400 to +101574.8400 |
| 1,000 synthetic events/tick | 6,548.4 | 1,022,592.9 | +1015973.5467 to +1016141.9067 |
| Physical hopper circulation | 138,881.9 | 157,588.5 | +18565.1333 to +18819.4933 |

## Real transfer activity and elapsed workload duration

The sealed medium fixture has 128 cells, 512 force-loaded hoppers and 128 ordinary shulker boxes, each holding 27 Mending books. Every run opens a fresh verified clone of the same saved world. Hoppers stay powered during the synthetic phases, then circulate the boxes for the physical phase. Each case warms for 200 native ticks and measures 600 ticks. Zero players, unpaused ticking, 512 force-loaded hoppers and conservation of all 128 carriers are checked throughout.

| Pair / mode | 100-event window seconds | 1,000-event window seconds | Physical window seconds | Physical transfer events | Observed inventory increases | Native physical ticks/s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 / disabled | 30.00021 | 30.00020 | 30.00094 | 11,008 | 11,008 | 19.9997 |
| 1 / enabled | 30.00130 | 29.99974 | 30.00057 | 11,008 | 11,008 | 19.9997 |
| 2 / disabled | 30.00073 | 30.00765 | 29.99992 | 11,008 | 11,008 | 20.0006 |
| 2 / enabled | 30.00359 | 30.00095 | 30.00209 | 11,008 | 11,008 | 19.9976 |
| 3 / disabled | 29.99895 | 29.99614 | 30.00033 | 11,008 | 11,008 | 20.0003 |
| 3 / enabled | 30.00068 | 29.99987 | 30.00114 | 11,008 | 11,008 | 19.9991 |

Enabled and disabled runs match **every per-tick transfer-event count and every per-tick observed physical inventory-increase count**, not just the aggregate totals. The physical counter is the conservative count of inventory increases, separate from the number of dispatched events. Raw movement counters, tick records and final quiesced state are retained. This fixture transfers in bursts (up to 128 events on a measured tick); consequently callback/full-tick p95 can rise more than the mean.

## How the variable is isolated

- Paper 1.21.11 build 111, Java 21.0.11, Apple M4 / 16 GiB, `-Xms1G -Xmx2G -XX:+UseG1GC`. All six JVMs run sequentially, with no concurrent compiler or owned benchmark. Order: disabled/enabled, enabled/disabled, disabled/enabled. Background system activity is not under complete experimental control.
- The same optimized development JAR is loaded in every JVM. All other plugin listeners, scheduled tasks and configuration remain enabled. No production source or configuration was changed for this experiment. Configuration comparison excludes only the game port and the generated secret of the disabled management server.
- The probe replaces exactly one registered `InventoryMoveItemEvent` listener with the same wrapper in both modes, retaining listener identity, priority, plugin, cancellation policy and event dispatch. Enabled forwards to the original executor; disabled skips only that invocation. This retains Paper's listener-present hopper event path in the no-check baseline. It is a no-check Paper control, not a different vanilla server implementation.
- Before either measured mode, the probe proves that a marked-item transfer is cancelled when enabled and not cancelled when disabled. Both JVM modes then prewarm the enabled implementation with the same 20,000 ordinary events. There are zero active navigation sessions. These setup assertions and prewarming are outside all measurement windows.
- Synthetic event objects, source/destination inventories and ordinary item contents are the same. Both modes reuse the event, reset cancellation before dispatch and verify that it is not cancelled or mutated. The synthetic item is cloned from the verified physical fixture.
- Full MSPT comes from Paper's completed native `TickTime` record on the following callback. Every record must contain the entire corresponding workload interval; tick numbers must be contiguous. Callback spacing is used only for elapsed window/tick rate, never as processing duration.
- Callback nanoseconds use `System.nanoTime`; allocation uses exact per-thread `ThreadMXBean` deltas. Instrumentation adds a common baseline cost. The enabled-minus-disabled result applies to this instrumented workload; it is not a universal constant for all item types, nesting depths, protected-item cleanup or server hardware.

## Interpretation

The listener still has a measurable synchronous cost. The batch and callback tables quantify that cost against dispatch with no item checking; the full-MSPT table shows its effect on the complete tick under these server conditions. Pair ranges expose run-to-run drift rather than treating one p95 difference as an exact causal estimate. Three paired JVM runs support this workload comparison, not a broad statistical confidence interval or production-incident attribution.

The physical workload must be interpreted separately from 1,000 synthetic events per tick: its observed transfer rate is much lower. The synthetic case reuses one event/item object; real hopper events process the circulating fixture items. Their measured per-event timings are not interchangeable even with identical item contents. Tick-rate caps alone can hide CPU/allocation overhead even when all runs sustain 20 TPS.

This isolates the Paper/Folia shared plugin's Paper inventory-transfer listener. It does not rerun Fabric or Folia, measure all protection hooks, change the protection policy, or resolve the separately retained depth-nine nested world-spawn failure. The earlier Paper/Fabric functional and component evidence remains separate. No commits, pushes or deployments accompany this measurement. Localization impact: none.

## Exact inputs, raw evidence and verification

External evidence root: `/Volumes/ssd/sw-performance-protection-cost-20261026/`.

- Same optimized plugin SHA-256: `d9447d77c08040b535958a985bfa30d17c9ed9583b1c8cf3a43528b52dd3fa95`. This is the accepted `paper-bounded.jar` from the [priority repair report](2026-10-26-priority-repairs.md).
- Probe SHA-256: `4b7f063d70a4ea1fc00df704176ca7a86cfa429b3c889514fd515f48233bcaea`. Sources and Java-17-compatible compilation command are retained in `src/diag/` and `tools/compile.py`. The native runtime is Java 21.
- `evidence/input-lock.json` seals the initial probe/tool/JAR inputs; `evidence/fixture-lock.json` seals the fixture and its original saved-world manifest. Per-run input files additionally record exact runtime/JVM, artifact, probe and fixture hashes. `evidence/manifest.json` seals final sources, analysis, reporting, raw samples/logs and explicitly excluded attempts.
- `pair-*/samples.json`: every raw native tick, batch time/allocation, callback time/allocation, interval allocation, event/check count and movement counter. `windows.json`: exact elapsed measurement windows. `inputs.json`: actual JVM arguments and hashes. `console.log`, `gc.log`, `fixture-verified.json`, `quiesced.json`, `listener-identity.json` and `outcome.json`: setup/health/cleanup and comparability evidence.
- `evidence/summary.json`: distributions from all six accepted runs and all nine paired case comparisons. `tools/analyze.py` independently checks input hashes, equal dispatch/listener/physical counts, matching server configuration, native containment/continuity, item conservation and owned JVM cleanup before writing this summary. It validates **10,800 measured native ticks**.
- Setup attempt `pilot-disabled` failed because the diagnostic constructed its fixture adapter before worlds existed. It produced no accepted measurements; the source/JAR/lock are retained under `evidence/setup-failure-1/`. Fixture initialization was moved to delayed setup. Corrected `pilot-disabled-2` / `pilot-enabled-2` verify the harness with short windows; they are excluded from all result tables. All six full runs use the same final probe bytes.

Executed commands:

```sh
rtk proxy python3 /Volumes/ssd/sw-performance-protection-cost-20261026/tools/compile.py
rtk proxy python3 /Volumes/ssd/sw-performance-protection-cost-20261026/tools/run.py pilot
rtk proxy python3 -u /Volumes/ssd/sw-performance-protection-cost-20261026/tools/run.py
rtk proxy python3 /Volumes/ssd/sw-performance-protection-cost-20261026/tools/analyze.py
```

Run roots are deliberately non-reusable; a replay must select fresh labels/roots. The per-run `inputs.json` preserves the original executed arguments. Owned JVMs are stopped and checked after the accepted runs.
