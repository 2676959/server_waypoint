# Ordinary modded menu-click fast path

Date: 2026-10-26.

**Result:** ordinary cobblestone pickup no longer triggers full menu, nested-content
and player-inventory cleanup. Native counters fall from 53 unrelated container-slot
reads per click to zero. In the shulker-filled scenario, a ten-click batch changes
from 7.357 ms / 14.125 MB allocated to 0.034 ms / 2.191 KB allocated.

## Change and preserved behavior

`AbstractContainerMenuNavigationMixin` retains its existing HEAD denial check.
Allowed clicks remember whether their clicked stack, cursor or hotbar/offhand swap
source needs navigation cleanup. MixinExtras `@Share` stores that boolean within
the invocation, avoiding a persistent menu field or shared global state. TAIL
validation runs only when the saved flag or the corresponding post-click stacks
require it. This covers a source emptied by shift-click and a marker introduced
into the cursor during the click.

`ModNavigationHooks.menuClickNeedsValidation` reads only those involved stacks.
`ModNavigationItemManager.needsNavigationCleanup` recognizes direct tags and checks
possible nested carriers on a stack copy using the existing versioned cleanup
rules. Plain non-container items take the bounded negative path. Ordinary nested
carriers may still have their own contents inspected, but they do not trigger a
full menu/player scan when no navigation item is found. No click input is mutated
while deciding whether validation is needed.

Relevant direct/nested item actions still run the existing cleanup/restoration.
Blocked actions keep their existing denial handling. Menu close retains full
cleanup. Unrelated invalid items are deliberately no longer sanitized merely
because someone clicks cobblestone; the relevant-action and lifecycle hooks handle
them. No active-session bypass, item-result cache, off-thread inventory access or
replacement background sweep was introduced.

The change is in shared Fabric/Forge/NeoForge source. Paper/Folia sources are not
changed by this task. Localization impact: none.

## Native behavior verification

The same external Fabric 1.21.11 probe invokes the actual transformed native
`ChestMenu`/`InventoryMenu.clicked` methods on a server-thread native player with a
dummy connection. There are zero connected clients; this is not physical UI input
or a network/multiplayer test.

The frozen baseline has three expected failed assertions: an ordinary pickup reads
unrelated container contents, sanitizes a protected descendant in an unrelated
shulker, and removes an unrelated unowned player item. The runner retains exit 1,
the full assertion result and every measurement. Other protection controls pass.

After the change, all **18 native checks pass**, covering:

- Ordinary cobblestone pickup preserves its vanilla result, reads no unrelated
  container slots and leaves unrelated nested/player items untouched.
- Closing the menu still cleans those nested and unowned player items.
- Touching a protected shulker or bundle still cleans its nested marker.
- An unowned marked item moved by shift-click is cleaned even though its original
  slot becomes empty; the pre-click flag is needed for this case.
- A slot `onTake` callback that marks the newly picked-up cursor is caught by the
  post-click check.
- An active compass session retains an allowed protected pickup. Depositing that
  item into a chest and hotbar/offhand swaps into the chest remain denied.
- Both timed workloads conserve their clicked ordinary stack and empty cursor
  after each ten-click batch. No real players connect.

Five new unit tests cover the cleanup predicate: ordinary/direct items, read-only
nested detection, component mutation between calls and the existing generic
container-component cleanup scope. A read-only review found no actionable issues
in the scoped changes; the reviewer did not run builds or servers.

## Matched measurements

One frozen before/after development pair, two cases, 100 warm-up and 300 measured
native ticks per case. Each measured tick invokes ten alternating left-click
pickups on the same cobblestone slot, restoring its state after the batch. The
measurement period is nominally 15 seconds per case at 20 TPS. All samples and
outliers are retained; this pair is not a broad confidence interval or a measure
of human click latency.

Both cases use a native six-row chest menu and a 36-slot player inventory. The
plain case fills ordinary slots with cobblestone. The shulker case fills the 53
unrelated chest slots and all 36 player slots with ordinary shulkers containing 27
enchanted-book item stacks each; slot zero remains the clicked cobblestone stack.
These books have no added enchantments. This is a bounded synthetic
native-click load, not physical player traffic or hopper movement.

| Ten-click batch | Before mean / p95 ms | After mean / p95 ms | Before mean allocation | After mean allocation | Unrelated chest reads before / after |
| --- | ---: | ---: | ---: | ---: | ---: |
| Plain menu | 0.520 / 0.670 | 0.066 / 0.096 | 425,005 B | 2,815 B | 530 / 0 |
| Shulker-filled menu | 7.357 / 9.427 | 0.034 / 0.066 | 14,124,564 B | 2,191 B | 530 / 0 |

Mean timed-work allocation falls 99.34% in the plain case and 99.98% in the shulker
case. Allocation is the exact `ThreadMXBean` delta on the named server thread for
the ten-click loop; setup, reporting and item construction are outside it. MB/KB
are decimal. The second case occurs later in each JVM and can have warmer JIT
code; compare each case against its matched baseline rather than comparing the
two after-case timings to one another.

| Native server tick processing | Before mean / p95 ms | After mean / p95 ms |
| --- | ---: | ---: |
| Plain menu | 0.984 / 1.368 | 0.511 / 0.773 |
| Shulker-filled menu | 7.709 / 9.874 | 0.376 / 0.453 |

Work is dispatched inside the overworld end-tick callback. The vanilla
`MinecraftServer.tickServer` timing ring is updated later in that tick. The probe
reads the recorded tick's ring entry on the next world callback, checks consecutive
tick IDs and requires native duration to include the entire work duration. These
are native processing durations, not callback spacing; the observer and normal
world processing are included. Unlike the earlier Paper interval probe, this ring
does not provide native start/end timestamps, so timestamp containment is not
claimed. The largest after shulker native tick is 13.209 ms and remains included.

Java 21.0.11, Apple M4 / 16 GiB, `-Xms1G -Xmx2G`, Fabric 1.21.11 loader 0.18.2 /
API 0.141.3. No compiler or other owned workload ran during either timed server.
The full broader performance audit, production incident attribution and other
platform runtime workloads remain outside this focused comparison.

## Build checks and retained artifacts

**932 Fabric 1.21.11 unit tests pass**, with zero failures, errors or skips.
Production compilation passes for Fabric/Forge/NeoForge 1.21.11, Fabric 26.1.2,
and Fabric/Forge 1.20.1. New test sources also compile for Fabric 26.1.2 and 1.20.1.
Remaining Stonecutter markers are balanced; active targets remain Fabric 26.1.2
and Paper 26.2. Compiler/mapping/deprecation warnings are retained in the successful
build log. No Forge/NeoForge runtime or full version matrix is claimed.

```sh
rtk ./gradlew :mods:1.21.11-fabric:test :mods:1.21.11-fabric:shadowJar :mods:1.21.11-neoforge:compileJava :mods:1.21.11-forge:compileJava :mods:26.1.2-fabric:compileJava :mods:26.1.2-fabric:compileTestJava :mods:1.20.1-fabric:compileJava :mods:1.20.1-fabric:compileTestJava :mods:1.20.1-forge:compileJava --console=plain --max-workers=2
```

Root: `/Volumes/ssd/sw-menu-click-fast-path-20261026/`.

- Before named-development Fabric JAR SHA-256:
  `b2c02638d0d20bb1b58a578af7855f327b267f0bf1841871a7ae197d3f69a322`.
- After named-development Fabric JAR SHA-256:
  `a92edce9624f8903836c7168cf9578d4a37af068bf3805ad53a3fa28bdd02c66`.
- `before/result.json`, `after/result.json`, `*/console.log`, `*/inputs.json`: every
  check/sample, expected baseline failure, executed JVM arguments and hashes.
- `src/diag/`, `tools/`, `inputs/`: the unchanged native probe, exact classpath,
  frozen artifacts and Java-17-compatible compilation/launch scripts.
- `evidence/summary.json`, `tools/analyze.py`: all 1,200 measured native tick samples
  summarized and independently checked. Every after sample has zero unrelated reads.
- `evidence/build.log`, `build-command.json`, `build-outcome.json`,
  `fabric-test-results/`, `junit-totals.json`: successful build and exact JUnit XML.
- `evidence/baseline-source/`, `final-source/`, `source-change.patch`: source
  snapshots and this task's isolated production diff. `archive-diff.json` confirms
  that only the three intended production class entries changed. Diagnostic
  classes remain excluded from the mod JAR. `review.json` records read-only review.
- `evidence/manifest.json`: input/output hashes and owned test-JVM cleanup.

Original-release audit failures and the earlier removal measurements remain
retained. No commits, pushes or deployments accompany this change.
