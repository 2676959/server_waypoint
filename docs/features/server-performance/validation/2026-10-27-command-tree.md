# Issue #35: compact list command options

Date: October 27, 2026. Issue: [#35](https://github.com/2676959/server_waypoint/issues/35).

## Finding and repair

`ListCommandOptions` registered separate literal branches for every sort mode,
direction, and view. Each branch rebuilt the remaining option combinations. Four
local scopes and four remote scopes each registered the expanded grammar even
with no waypoints and cross-server support disabled.

Minecraft 26.3 `Commands.sendCommands` copies player-visible nodes using
`fillUsableCommands` and a `HashMap`. `ClientboundCommandsPacket` then indexes
nodes using an `Object2IntOpenHashMap`. Structural command-node comparisons make
the repeated subtrees expensive. Sharing server-side node instances alone would
not solve the problem: `fillUsableCommands` copies shared children again for each
path.

The shared backend grammar now uses native `StringArgumentType.word()` arguments
for sort mode, direction, and view, with bounded completion suggestions. Execution
validates those values before invoking the scope's command. Search, integer
page/limit bounds, documented option ordering, trailing page/search controls,
reserved exact list identities, and existing permission checks remain intact.
No Minecraft mixin or protocol change is required. The repair applies to all mod
loaders and Paper/Folia through `common`.

Invalid enum words now receive a Brigadier syntax error during server execution;
the client can initially highlight them as syntactically valid strings. The
shared sort subtree also exposes the structural `order` literal after
`sort default`, but offers no direction values there and rejects execution, as
before. No common localization entries or placeholders changed; errors reuse
Brigadier's built-in incorrect-argument message.

## Node counts

Counts use object identity, not structural equality, and exclude the dispatcher
root when describing one option entry.

| Registered branch | Original | Candidate | Reduction |
| --- | ---: | ---: | ---: |
| One list option entry | 2,447 | 331 | 86.5% |
| Complete local `/wp list` branch | 9,791 | 1,327 | 86.4% |
| Complete `/wp remote` branch | 9,801 | 1,337 | 86.4% |

The regression tests fail against the original expanded implementation and pass
with the compact implementation. They bound both server nodes and independently
copied player-visible nodes, rather than accepting server-only graph sharing.

## Native Minecraft 26.3 comparison

The isolated probe boots Minecraft registries, constructs eight list option
scopes with native string/integer argument types, and adds a redirect alias. It
calls the actual private `Commands.fillUsableCommands` method with the unmodified
vanilla `HashMap` and constructs the actual `ClientboundCommandsPacket` with its
unmodified fastutil map. Both variants run on Java 25, with three warmups and
seven measured samples per JVM.

| Measurement | Original | Candidate |
| --- | ---: | ---: |
| Fixture server/copied/decoded node count | 19,580 | 2,652 |
| Encoded command packet bytes | 284,320 | 44,227 |
| Median command-copy time | 249.771 ms | 5.418 ms |
| Median packet-construction time | 690.575 ms | 12.297 ms |

Packet time measures construction and indexing; it excludes encoding, decoding,
and reconstruction. Every warmup and measured sample separately passes packet
encode/decode, decoder validation, redirect reconstruction, and byte-consumption
checks. The native permission-filter check passes for denied sources in both
variants. This fixture isolates the list grammar and does not include all other
waypoint command families or a running server.

Raw evidence: [measurements and source hashes](2026-10-27-command-tree/measurements.json),
[original run](2026-10-27-command-tree/baseline.log),
[candidate run](2026-10-27-command-tree/candidate.log),
[original failing size tests](2026-10-27-command-tree/baseline-size-tests.xml), and
[current option tests](2026-10-27-command-tree/candidate-option-tests.xml).
An earlier probe invocation produced medians of 317.264/669.265 ms for the
original and 5.408/11.881 ms for the candidate (copy/packet respectively).
Both earlier runs are retained in the measurements JSON and raw
[original](2026-10-27-command-tree/baseline-initial.log) and
[candidate](2026-10-27-command-tree/candidate-initial.log) logs. The table uses the
final Kotlin-script invocations, rather than selecting the slowest original run.
These isolated results support the reported mechanism. They do not establish
login or dimension-transfer timings on the reporter's server with LuckPerms and
other installed mods.

## Verification

- `:common:test`: 684 tests passed, including option values/combinations,
  invalid values and error cursors, integer bounds, direct/nested redirect
  completion and execution, real local/remote scopes, generated chat links,
  permission revocation, and reserved identities.
- `:cross-server:test`: 115 tests passed; `:proxy-common:test`: 167 passed.
- Python server-performance suite: 119 tests passed. Native command-leaf and
  suggestion templates now supply values for the compact argument names.
- `:mods:26.3-fabric:assemble`: passed and produced the packaged JAR.
- `:mods:1.20.1-fabric:compileJava` and `:paper:26.2-paper:compileJava`: passed.
- Full `:mods:26.3-fabric:build`: packaging succeeded; 938 of 943 tests passed.
  The five failures are in the pre-existing, untracked `ModMenuClickCleanupTest`:
  `customContainerComponentsRetainTheExistingCleanupScope`,
  `ordinaryNestedContentsArePreservedAndResultsFollowChanges`,
  `nestedDetectionDoesNotSanitizeTheOriginalStack`,
  `ordinaryAndEmptyStacksDoNotNeedCleanup`, and `directMarkersNeedCleanup`.
  Each throws `NullPointerException: Components not bound yet` while constructing
  `ItemStack`, before exercising this command code. See the retained
  [failure report](2026-10-27-command-tree/unrelated-navigation-test-failures.xml).
  This task preserves those unrelated navigation changes.
- Independent code review found no Critical or Important issues. The probe script
  was converted to Kotlin DSL and its final invocation verified.
- Existing deprecation/Unsafe/unchecked warnings remain. Stonecutter active
  projects were not switched. No push, publication, or GitHub issue
  mutation was performed.

The assembled JAR contains the current worktree's pre-existing edits as well as
this repair; it is not an isolated release artifact. Live joins, dimension
transfers, client completion display, LuckPerms interaction, other modded loaders,
and the full platform matrix remain unverified.

## Reproduction

Run from the repository root. The probe is explicitly isolated from production
source sets and packaging:

```sh
rtk proxy ./gradlew :mods:26.3-fabric:issue35Probe \
    --init-script tools/server-performance/command-tree/probe.init.gradle.kts \
    --console=plain --max-workers=2
```

For the original implementation, extract just the original helper into a fresh
temporary directory. The baseline source compiles into a separate probe output
directory and precedes `common` on this probe's classpath; production files and
artifacts remain untouched:

```sh
rtk proxy mkdir -p /tmp/server-waypoint-issue35-original
rtk proxy git show 04c5ac185e285a92364307c667b4fe7c481817eb:common/src/main/java/_959/server_waypoint/command/ListCommandOptions.java \
    > /tmp/server-waypoint-issue35-original/ListCommandOptions.java
rtk proxy ./gradlew :mods:26.3-fabric:issue35Probe \
    --init-script tools/server-performance/command-tree/probe.init.gradle.kts \
    -Pissue35BaselineSource=/tmp/server-waypoint-issue35-original/ListCommandOptions.java \
    --console=plain --max-workers=2
```

This issue-specific measurement is separate from the broader M1 acceptance
ledger and does not complete its pending live-player or performance gates.
