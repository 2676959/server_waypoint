# Xaero default-list set selection validation

The exact server list `gui.xaero_default` can sync to a separate owned set (Off, default) or directly
replace Xaero's default set contents (On). The toggle is persisted as `xaeroDefaultListDirectSync`.
Use Sync now after changing the toggle to rebuild the complete list in the selected destination.

## Automated checks

Passed:

```sh
./gradlew :mods:26.1.2-fabric:test :common:test :mods:1.20.1-fabric:test :mods:1.20.1-forge:test :mods:1.20.2-neoforge:test :mods:26.3-neoforge:test :mods:26.3-fabric:compileJava --continue
```

XML reports contain 4,031 tests: 4,014 passed, 17 skipped, no failures or errors. The common
suite was up to date. Existing skips cover vanilla text/form fixtures on Forge/NeoForge and
VoxelMap dimension fixtures on NeoForge 26.3. All new default-set tests ran successfully.
The active Stonecutter project stayed at `26.1.2-fabric`.

Coverage includes direct versus separate set routing, default-set content replacement,
personal-set preservation, full-sync cleanup, retained default-set selection, incremental
replacement/removal without deleting personal duplicates, list deletion after changing modes,
ambiguous waypoint-name rejection before destructive replacement, marked-default server edit
resolution, setting persistence/reset/availability, and six-locale key coverage.

Six representative targets compiled: Fabric 1.20.1, 26.1.2, 26.3; Forge 1.20.1;
NeoForge 1.20.2 and 26.3. `git diff --check`, changed-locale JSON parsing and tab checks passed.
Existing Gradle, dependency deprecation, and Forge mixin annotation-processor warnings remain.
An initial offline NeoForge check could not resolve the dynamic Netty dependency listing;
the final online command above succeeded.

## Live-client checks

Ran real Minecraft clients on 2026-10-08 with freshly assembled production JARs and external
Java 17-compatible probes. All four profiles passed, with successful Mixin audits, independent
server JSON validation, and exit code 0:

| Runtime | Xaero Minimap | Xaero World Map | Scenario steps |
| --- | --- | --- | --- |
| Fabric 26.1.2 | 25.3.12 | 1.40.18 | 20 passed |
| Fabric 1.20.1 | 25.2.10 | 1.39.12 | 20 passed |
| Forge 1.20.1 | 25.3.13 | 1.40.2 | 17 passed |
| NeoForge 1.20.2 | 25.2.10 | 1.39.12 | 17 passed |

The runner invoked real screen mouse-click handlers for the new toggle and Sync now confirmation.
Assertions verified Off preserving personal default entries, On replacing default contents and
removing the old owned copy, retained default-set selection, server highlighting, decoded waypoint
identity, server Edit routing in Minimap and World Map, live upload collection with decoded list
and waypoint names, incremental add/rename/remove, both mode transitions, and server waypoint/list
deletion preserving later personal additions and another personal set. The existing suite also
verified editor save/cancel, owning dimensions, native map behavior, stale targets, sharing, and
save/reload. VoxelMap operations ran on both Fabric profiles; these repository targets do not pin
VoxelMap on Forge 1.20.1 or NeoForge 1.20.2.

After closing and reloading each world, native Xaero assertions passed. Independent disk checks
confirmed `xaeroDefaultListDirectSync: true`, the default set containing only `personal-after`,
`Default-test-personal` retaining `other-personal`, absence of the old owned default set, and absence
of the removed server default list. All 74 scenario steps passed. No production source changed and
the active Stonecutter project remained `26.1.2-fabric`.

Evidence under the repository's ignored build directory:

- `build/live-game-test/default-set-20261008/results-third/report.md`
- `build/live-game-test/default-set-20261008/results-third/summary.json`
- `build/live-game-test/default-set-20261008/results-third/default-set-disk-checks.json`
- Per-profile step results, `events.log`, game logs, Mixin audit, isolated worlds and copied server JSON.
- `build/live-game-test/default-set-20261008/prepared-third/` contains production/probe hashes and build logs.
- `build/live-game-test/default-set-20261008/tool/` contains the scoped runner/probe additions;
  `matrix.json` and `manifest.json` retain the selected runtime inputs.

Commands used (repository root; JDK argument is its home):

```sh
python3 build/live-game-test/default-set-20261008/tool/run.py prepare \
    --repo /Volumes/ssd/fabric_mods_repo/server_waypoint \
    --manifest build/live-game-test/default-set-20261008/manifest.json \
    --jdk /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
    --output build/live-game-test/default-set-20261008/prepared-third
python3 build/live-game-test/default-set-20261008/tool/run.py run \
    --prepared build/live-game-test/default-set-20261008/prepared-third \
    --output build/live-game-test/default-set-20261008/results-third \
    --jobs 2 --heap 1024
```

Earlier attempts exposed two probe issues: World Map still owned the preceding Nether fixture,
so the probe now waits for Overworld ownership; list removal initially tried a non-empty server
list, so cleanup now removes its waypoint first. In the final run, the two new operations emitted
`PASS` without the runner's required trailing newline. Those already-completed success records
were normalized only after confirming the matching `PASS` event from the live probe. The correction
log is `results-third/result-format-corrections.log`; the retained probe source now writes the
canonical newline. These corrections did not alter assertions or production behavior.

## Permanent regression coverage and fresh rerun

The new scenarios are now tracked in `tools/live-game-test/java/LiveChecks.java.template` and
run automatically in the editor suite. They use the existing canonical result writer; no result
normalization was needed in the fresh tracked-suite run. Additional live assertions reject an
ambiguous full-list replacement without destroying existing default entries, ensure a personal
same-name duplicate is not highlighted, and reload the saved client setting from disk.

`persistence.verify_default_set` automatically checks client config, native Xaero sets, personal
waypoint names and coordinates, removal of the owned default copy, and deletion of the server
list. Twelve new disk-validator regression cases cover valid state and deliberately corrupted
state. Two runner regression cases check complete versus incomplete probe success records.
The full Python suite passes 41 tests. Coordinate-loss cases were observed failing before the
validator was extended, then passing after it checked the saved coordinates.

Three new Java regression tests in `XaeroMinimapHelperTest` verify owned default markers versus
personal duplicates, detached same-name markers, and marked names in another personal set.
All three passed on all four targets. The complete four-target Java suites completed 2,685 tests:
2,683 passed, 2 existing skips, zero failures/errors:

```sh
./gradlew :mods:26.1.2-fabric:test :mods:1.20.1-fabric:test \
    :mods:1.20.1-forge:test :mods:1.20.2-neoforge:test --continue --console=plain
python3 -m unittest discover -s tools/live-game-test -p 'test_*.py'
```

Fresh preparation and live execution used the tracked tool:

```sh
python3 tools/live-game-test/run.py prepare \
    --repo /Volumes/ssd/fabric_mods_repo/server_waypoint \
    --manifest build/live-game-test/default-set-20261008/manifest.json \
    --jdk /opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
    --output build/live-game-test/default-set-20261008/prepared-tracked
python3 tools/live-game-test/run.py run \
    --prepared build/live-game-test/default-set-20261008/prepared-tracked \
    --output build/live-game-test/default-set-20261008/results-tracked \
    --jobs 2 --heap 1024
```

All four profiles in the table above passed again: 74 live steps, Mixin audits, normal result
formatting, clean exits, server JSON and automatic default-set disk checks. The latest disk
validator, including coordinate assertions, was also rerun independently against all four saved
game directories. Evidence: `results-tracked/report.md`, `summary.json`, per-profile results,
and `default-set-disk-checks.json`, under `build/live-game-test/default-set-20261008/`.
Production source and the active Stonecutter target were unchanged by this test addition.

## Checks still needed

- Visually inspect readable waypoint labels, highlighting, the new setting, tooltip and sync dialog
  at small window sizes. Native GUI-handler assertions do not establish rendered appearance or
  operating-system mouse input.
- Verify remote multiplayer incremental synchronization and a complete `/wp upload` command/network
  round trip. Live upload collection was exercised, but the complete command flow was not.
- The [full live client matrix](2026-10-08-live-client-matrix.md) now records all 40 supported targets,
  including the three targets without matching Xaero releases. Other map-mod releases remain unverified.
