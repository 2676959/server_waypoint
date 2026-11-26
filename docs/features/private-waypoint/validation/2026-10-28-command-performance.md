# Private command performance validation

Date: October 28, 2026. Scope: Task 3 follow-up against the
[command-tree performance guide](../../../tips/command-tree-performance.md).

## Refactor

`ListCommandOptions` retains native bounded word/integer arguments and the existing documented
option ordering. `PreparableCommand` validates and resolves those values before Private schedules
work, resolves an owner UUID, or opens storage. The prepared handler captures the selected values
and runs through the existing owner gate. Invalid values now propagate the original Brigadier
syntax error and argument cursor rather than returning success with a generic private error.

Bounded value completion no longer loads an owner store. Only providers marked as collection
lookups borrow files; owner list/waypoint suggestions retain their scope, redirect context, and
replacement offsets. Existing permission checks remain at completion and execution.

During tree copying, a denied action root prunes its complete branch. The wrapper now checks the
action permission at that root rather than repeating the same lookup on every descendant.
Descendants retain their original requirements. A regression walks the 1,327-node Private list
branch and checks that its list permission is fetched once, and that denial prunes the branch.

No command spelling, argument names, supported ordering, collection ownership, or permission
defaults changed. No localization keys or MessageFormat placeholders changed in this follow-up.

## Identity-based size budgets

The common regression registers an empty catalog with all five navigation methods available.
It counts distinct node objects, then independently expands child paths into a copied tree.

| Scope | Registered | Expanded copy | Budget |
| --- | ---: | ---: | ---: |
| Private list | 1,327 | covered by complete Private copy | 1,536 |
| Private subtree | 1,469 | 1,469 | 1,536 |
| Complete dispatcher | 4,305 | 4,305 | 4,608 |

The refactor preserves the compact graph. It does not claim a node-count reduction. Earlier
counts of 1,439 Private nodes and 4,245 dispatcher nodes used a fixture without navigation handlers;
the regression now covers the larger registration with every navigation method available.

## Native Minecraft 26.3 comparison

The new full-tree probe measures the actual `CoreWaypointCommand` registration, including Shared,
Private, Remote, and all navigation methods, with an empty catalog and two redirect aliases.
Its platform adapter uses native Brigadier string arguments for dimension, position, and icon
inputs. These adapter types make the fixture serializable without creating a live Minecraft server;
the byte count is this fixture's packet size, not a production platform packet-size claim.

Both variants run on the same Apple M4 host, Java 25, with three warmups and seven measured samples
in a fresh JVM per variant. Host metadata and exact source hashes are in
[measurements.json](2026-10-28-command-tree/measurements.json). Probe source hashes match between
the baseline and candidate. The baseline is the Task 3 implementation before this refactor.

| Measurement | Before refactor | After refactor |
| --- | ---: | ---: |
| Registered nodes | 4,307 | 4,307 |
| Player-visible copied nodes | 4,306 | 4,306 |
| Decoded/reconstructed nodes | 4,306 | 4,306 |
| Encoded bytes | 73,381 | 73,381 |
| Median native copy time | 12.042 ms | 10.711 ms |
| Median packet construction/indexing time | 30.602 ms | 28.275 ms |

The native fixture adds two aliases to the common test's registration and filters the standalone
console-only key-generation command from the player copy. The same native copy method and original
vanilla hash maps are used for both variants. Packet timing excludes encoding/decoding; every
warmup and measured sample separately passes encode/decode, complete byte consumption, Private
subtree reconstruction, and direct/root redirect reconstruction. Denied Private list/add/tp branches
are checked before timing. The existing issue35 probe also still passes.

The small same-host timing samples support a fixture comparison. They do not establish a live
login speedup, LuckPerms cost, or production attribution. The deterministic changes are earlier
syntax validation, avoided owner-store completion loads, and fewer repeated permission lookups.

## Regression evidence

- [Initial failing syntax/redirect tests](2026-10-28-command-tree/red.log) and
  [JUnit failure details](2026-10-28-command-tree/red-CoreWaypointCommandPrivateTest.xml).
- [Completion storage regression](2026-10-28-command-tree/suggestion-red.log) and
  [repeated permission lookup regression](2026-10-28-command-tree/permission-red.log).
- [Passing Private command tests](2026-10-28-command-tree/green-CoreWaypointCommandPrivateTest.xml)
  and [all-navigation graph budgets](2026-10-28-command-tree/green-ListCommandOptionsTest.xml).
- Native [baseline](2026-10-28-command-tree/baseline.log),
  [candidate](2026-10-28-command-tree/candidate.log), and
  [existing issue35 probe](2026-10-28-command-tree/legacy-probe.log).
- [Full verification](2026-10-28-command-tree/full-verification-final.log): common 711 tests,
  cross-server 115, proxy-common 167, Paper 58; zero failures, errors, or skips. Fabric 26.1.2
  compilation succeeded. Deprecation/Unsafe/native-access warnings remain in the recorded logs.

```sh
rtk proxy ./gradlew :common:test :cross-server:test :proxy-common:test :mods:26.1.2-fabric:compileJava :paper:26.2-paper:test --console=plain --max-workers=2
rtk proxy ./gradlew :mods:26.3-fabric:privateCommandTreeProbe --init-script tools/server-performance/command-tree/probe.init.gradle.kts --console=plain --max-workers=2
rtk proxy ./gradlew :mods:26.3-fabric:issue35Probe --init-script tools/server-performance/command-tree/probe.init.gradle.kts --console=plain --max-workers=2
```

Active Stonecutter targets were not switched. No whole-version matrix, live player join,
multiplayer privacy, physical-input, or teleport-arrival validation was performed. No commit or
push was made. Tasks 4–8 remain pending.
