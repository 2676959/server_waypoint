# Hopper transfer-check removal

Date: 2026-10-26.

The user requested removal of the hopper transfer check after reviewing the
[enabled/disabled measurements](2026-10-26-protection-cost.md). The shared Paper/Folia
source now has no Server Waypoint handler for `InventoryMoveItemEvent`.

## Behavior and scope

Removed `NavigationProtectionListener.onInventoryMove` and its event import: 13
deleted lines. Automatic container-to-container transfers no longer scan for
navigation items, cancel their movement, clean their source inventory or schedule
player reconciliation through this hook. This includes direct tagged items and
tagged items inside shulkers or bundles. No configuration flag was added.

The shared Paper/Folia build receives the same removal. Source inspection found no
equivalent hopper-transfer hook in the mod backend, so Fabric/Forge/NeoForge source
was not changed. The native item reader remains necessary for other protection
hooks. The `InventoryPickupItemEvent` ground-item pickup handler and world-spawn,
player inventory, drop and other protection handlers were not removed.

The earlier performance report describes the pre-removal artifact. Its no-check
mode retained a diagnostic wrapper and event dispatch; those measurements are not
new timing/allocation results for the build with this registration deleted.

## Verification

A new external native Paper probe was run unchanged against both frozen artifacts:

- Before removal, it fails with `automatic transfer allowed push=true item=1`:
  the protected compass transfer is cancelled by the existing handler. The failed
  command exits 1 and its raw server log is retained.
- After removal, all 27 assertions pass. Synthetic Bukkit event checks allow ordinary
  stone, direct protected compasses, protected shulker contents and protected bundle
  contents in both push/pull directions, preserving event and source contents.
- Native handler discovery confirms zero Server Waypoint `InventoryMoveItemEvent`
  registrations. Direct protected world-spawn and inventory ground-item pickup events
  still cancel; ordinary counterparts remain allowed. These retained-hook checks use
  Bukkit event dispatch with detached native item entities, not real ground pickups
  or physical player actions.
- A tagged compass physically circulates through all four hoppers in one fixture
  cell with its complete item data preserved. Over 200 native ticks, the complete
  128-cell/512-hopper fixture records 3,584 observed inventory increases, retains all
  128 top-level carriers, and stays unpaused with zero connected players. The other
  127 carriers are the original shulkers containing 27 Mending books each. These are
  real native hopper movements, separate from synthetic event checks.
- All 37 Paper 1.21.11 unit tests pass without failures, errors or skips. Production
  API compilation passes for Paper 1.21, 1.21.9, 1.21.11 and 26.2. The active
  Stonecutter targets remain Paper 26.2 and Fabric 26.1.2.

Executed build:

```sh
rtk ./gradlew :paper:1.21.11-paper:test :paper:1.21.11-paper:shadowJar :paper:1.21-paper:compileJava :paper:1.21.9-paper:compileJava :paper:26.2-paper:compileJava --console=plain --max-workers=2
```

This is a focused policy-removal check on Paper 1.21.11 build 111 / Java 21. It does
not claim new Fabric/Folia runtime measurements, a complete protection audit or a
fix for the previously retained nested world-spawn failure. The earlier failures
and original-release audit evidence remain retained. Localization impact: none.
No commit, push or deployment accompanies this change.

## Artifacts and reproducibility

- Before JAR SHA-256:
  `d9447d77c08040b535958a985bfa30d17c9ed9583b1c8cf3a43528b52dd3fa95`.
- After Paper 1.21.11 JAR SHA-256:
  `8e7a375cbf7132e064f6b8a670afb1671f779d1ccfaf8cf342046e5ffaf15f45`.
- External root: `/Volumes/ssd/sw-hopper-transfer-removal-20261026/`.
- `before/console.log`: retained expected failure; `after/console.log` and
  `after/result.json`: all native assertions, visited hopper indices and physical
  counters. Per-run `inputs.json` records exact JVM arguments and artifact/probe hashes.
- `src/diag/`, `tools/compile.py`, `tools/run.py`, `inputs/`: the unchanged native
  probe, Java-17-compatible compilation, owned-process runner and frozen JARs.
- `evidence/build-result.json` records the actual successful Gradle command/result;
  `evidence/paper-test-results/` retains the JUnit XML. `evidence/manifest.json`
  seals the probe/artifacts, source change, results and logs and verifies owned test
  JVM cleanup. Production JARs exclude diagnostic classes.
