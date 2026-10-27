# Non-copying protection marker reads

Date: 2026-10-27.

**Result:** Paper reads borrowed native stacks instead of copying them. Modded
protection reads the existing custom-data compound and compares one top-level
string tag, without copying NBT. Both retain the existing marker formats and
protection policy. Marked-item read loops allocate zero measured bytes after the
change in this fixture. Fabric large-custom-data loops also show a substantial
processing-time reduction. Paper timing results are mixed across cases; only its
allocation reduction is treated as an established optimization benefit here.

## Implementation

- Paper `isNavigationItem` and `isPortableStorage` use `CraftItemStack.unwrap`.
  Installed runtime bytecode confirms it resolves the same native handle as
  `asNMSCopy`, without calling `ItemStack.copy`. The borrowed stack is used only
  for read-only component/type queries. Generic Bukkit delegates can still have
  first-use initialization costs; this does not claim every API operation allocates
  nothing. This API compiles against all four supported Paper targets.
- Modded `isNavigationItem` already receives the native stack. For component-based
  versions, a small `CustomDataAccessor` reads the existing private-final `tag`
  field. Legacy versions use `ItemStack.getTag` directly. `hasMarker` compares a
  cached `StringTag("true")` against `tag.get("server_waypoint:navigation_item")`.
  Missing, wrong-type and differently valued markers remain unprotected. No
  generalized matcher, NBT copy, serialization or marker-result cache is involved.
- The accessor exists and is registered only at Minecraft >=1.20.5. All five loader
  Gradle scripts filter its JSON registration on earlier versions; Stonecutter
  gates the Java source/import/call. Fabric release artifacts at 1.20.6 and 1.21.11
  remap its target to `class_9279` and field to `field_49306`; the legacy artifact
  contains neither class nor registration. Modern Forge/NeoForge and unobfuscated
  Fabric resources retain the registration; legacy Forge/NeoForge resources omit it.
- Existing item writes still use the normal component update API. Transactional
  inventory snapshots/restoration still copy stacks for rollback. The previously
  selected top-level-only policy, ordinary-click gating and bundle/shulker insertion
  guards remain unchanged. No compatibility reader or marker migration is added.

## Matched component measurements

One frozen before/after pair per platform, on Minecraft 1.21.11. Every JVM executes
100 warm-up and 150 measured native ticks for each of four cases; each tick reads
1,000 items by cycling over 32 prepared native stacks. The same probe bytes run
before/after. Cases are always ordered small ordinary, small marked, large
ordinary, large marked; compare matched cases, not different case orders.

Small ordinary items have no custom-data component; small marked items contain
only the marker (inside `PublicBukkitValues` on Paper). Large fixtures additionally
contain 32 ordinary string fields and one 1,024-element int array in custom data.
They are a synthetic payload-size screen, not a production item distribution.
Item creation, marker writes, sample reporting and JSON output are outside the
measured read loop. Prepared items and protection results match in both modes.

Each batch is directly timed with `System.nanoTime`; allocation is the exact named
server-thread `ThreadMXBean` delta around that loop. Values below are per 1,000
checks. They are component work, not full MSPT, total JVM allocation, event dispatch,
physical inventory traffic, client latency or proof of a production TPS change.

| Platform / case | Before mean / p95 ms | After mean / p95 ms | Before mean bytes | After mean bytes |
| --- | ---: | ---: | ---: | ---: |
| Paper / small-data-ordinary | 0.0526 / 0.0995 | 0.0706 / 0.1280 | 64,002.83 | 2.83 |
| Paper / small-data-marked | 0.1167 / 0.1657 | 0.0235 / 0.0634 | 64,000.00 | 0.00 |
| Paper / large-data-ordinary | 0.0872 / 0.1479 | 0.0750 / 0.1803 | 64,000.00 | 0.00 |
| Paper / large-data-marked | 0.1322 / 0.2054 | 0.0969 / 0.2208 | 64,000.00 | 0.00 |
| Fabric / small-data-ordinary | 0.0314 / 0.0549 | 0.0218 / 0.0373 | 48,002.83 | 2.83 |
| Fabric / small-data-marked | 0.0942 / 0.1730 | 0.0466 / 0.0635 | 192,000.00 | 0.00 |
| Fabric / large-data-ordinary | 1.6663 / 2.3094 | 0.0387 / 0.0689 | 5,728,000.00 | 0.00 |
| Fabric / large-data-marked | 1.7758 / 2.5025 | 0.0541 / 0.0803 | 5,792,000.00 | 0.00 |

Every marked and large-data after sample allocates zero bytes. The small ordinary
case has one 424-byte sample in each before/after runtime; all are retained,
producing a 2.83-byte mean after batch. No timing or allocation outliers are dropped.

The baseline fails the declared non-copying budget of at most 16,000 allocated
bytes per 1,000 reads. All four after cases pass on both platforms. This budget is
a focused component assertion, not a release gate or universal workload limit.
`baseline-budget-red.json` retains the expected RED results.

Paper plain ordinary mean/p95 and both large-data p95 results increase in this
single pair despite lower allocation. These mixed timings do not establish a
consistent Paper CPU/latency win. Fabric timings improve in all four matched cases;
no broad statistical confidence interval is claimed. Fresh JVMs, JIT warm-up and
uncontrolled background activity can affect these short distributions.

Runtimes: Java 21.0.11, Apple M4 / 16 GiB, `-Xms1G -Xmx2G`, Paper 1.21.11 build 111
and Fabric loader 0.18.2/API 0.141.3. Servers run sequentially; no owned compiler or
other benchmark runs during the timed windows. There are 2,400 measured batches /
2.4 million timed reads across the four JVMs, plus warm-up reads.

## Functional and packaging verification

- **37 Paper and 933 Fabric 1.21.11 unit tests pass**, zero failures, errors or skips.
  Six raw-NBT marker tests replace the six mod predicate tests that now require a
  transformed `CustomData` runtime. They cover exact string/type semantics, absent
  and nested markers, preservation of unrelated data, and changes between reads.
  This is not a production fallback for untransformed tests.
- The unchanged native integration controls run before and after: **35 Paper and
  25 Fabric checks pass** in each mode. They cover actual transformed mod menu
  behavior, direct-tag removal, preserved nested carriers, Paper native world
  spawning, both bundle insertion directions, opened-shulker insertion denial,
  inventory/cursor validation and purge. All runtimes assert zero connected clients.
  Native players use dummy connections; some Paper cases dispatch synthetic events.
  These controls do not prove physical client input or multiplayer behavior.
- Production compilation passes for all four Paper targets; Fabric/Forge/NeoForge
  1.21.11; Fabric 1.20.1, 1.20.6 and 26.1.2; and Forge 1.20.1. Extra test sources
  compile at Paper 26.2 and Fabric 1.20.1/26.1.2. Modern/legacy packaging and
  registration checks include the legacy NeoGradle script. Remapped Fabric JARs
  build at 1.20.1, 1.20.6 and 1.21.11. Active targets are unchanged.
- Read-only independent review found no actionable findings. Other loader runtime
  application and a full version matrix are outside the review/runtime evidence.
- Source/marker checks and whitespace validation pass. Forge mapping/deprecation,
  unchecked API and Gradle warnings remain in the successful build logs.

One initial packaging inspection looked at Forge's stale conventional
`build/resources/main` directory. Forge actually configures `build/sourceSets/main`;
checking its configured output confirms the accessor registration is present.
This was an inspection-path error, not a missing production registration.

## Evidence and commands

Root: `/Volumes/ssd/sw-marker-read-20261027/`.

- `inputs/*-before.jar`, `inputs/*-after.jar`: frozen development artifacts.
- `src/diag/`, `tools/compile.py`, `tools/run.py`: unchanged native controls plus
  the shared read benchmark and launch code. `inputs/*-classpath.txt` records the
  compile/runtime classpaths. Probe and artifact hashes are recorded per run.
- `paper-before/`, `paper-after/`, `fabric-before/`, `fabric-after/`: every raw
  sample, control result, console log, JVM arguments, input hashes and stopped
  process outcome. `summary.json` / `tools/analyze.py` validate and summarize them.
- `baseline-source/`, `final-source/`, `source-paths.json`, `source-change.patch`:
  this task's source scope, including the replaced unit test.
- `build-commands.json`: exact Gradle arguments and successful exit codes.
  `build.log`, `packaging-build.log`: full successful verification output.
  `packaging-check.json` includes release JAR hashes and remapped accessor bytecode.
  `archive-diff.json` shows only PaperNavigationItemManager changed in the frozen
  Paper JAR; Fabric differs only in ModNavigationItemData, CustomDataAccessor and
  the common mixin config. Diagnostic classes are excluded from production.
- `test-results/`, `junit-totals.json`, `review.json`, `manifest.json`: exact JUnit
  evidence, review outcome and sealed input/result hashes with owned-JVM cleanup.

Executed native commands:

```sh
rtk proxy python3 /Volumes/ssd/sw-marker-read-20261027/tools/compile.py
rtk proxy python3 /Volumes/ssd/sw-marker-read-20261027/tools/run.py paper paper-before before
rtk proxy python3 /Volumes/ssd/sw-marker-read-20261027/tools/run.py fabric fabric-before before
rtk proxy python3 /Volumes/ssd/sw-marker-read-20261027/tools/run.py fabric fabric-after after
rtk proxy python3 /Volumes/ssd/sw-marker-read-20261027/tools/run.py paper paper-after after
rtk proxy python3 /Volumes/ssd/sw-marker-read-20261027/tools/analyze.py
```

No commit, push or deployment. Localization impact: none. Broader performance-audit
and original-release failures remain separate; this does not complete Task 10,
M1 or a release gate.
