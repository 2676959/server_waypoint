# Top-level navigation-item protection only

Date: 2026-10-27.

**Result:** Paper/Folia and shared Fabric/Forge/NeoForge protection now check only
an item's own protection marker. Recursive bundle/shulker/container detection and
cleanup have been removed. Normal player insertion guards remain enabled.

## Current behavior

- A top-level tagged item remains subject to existing movement, drop, equipment,
  ownership, duplicate and lifecycle rules.
- An untagged bundle, shulker or custom carrier is treated as unprotected even if
  a protected item already exists inside. Relevant clicks, menu close, inventory
  validation, session cleanup and Paper spawn/pickup/death hooks no longer traverse
  or sanitize its contents.
- Normal player attempts to insert a protected item into a bundle or opened
  shulker remain blocked. Paper storage recognition now checks the native item
  type, avoiding Bukkit metadata/block-state construction. A new regression test
  demonstrated that the old metadata-dependent guard missed an empty bundle;
  the native type check fixes that gap without restoring recursive inspection.
- Mod click validation still uses pre- and post-click involved stacks; its
  predicate now reads only the top-level marker. Ordinary clicks continue to skip
  unrelated inventory validation. Lifecycle operations still inspect direct slots.
- No replacement background scan, compatibility layer or language changes were
  introduced. Paper and Folia continue to share one production implementation.

The deliberately removed nested protections are no longer current acceptance
requirements. Earlier release-audit nested failures and measurements remain in
historical reports and raw artifacts; this change does not claim to repair those
old recursive-policy failures. The historical nested-spawn audit still tests its
original sanitization policy. Its optional product telemetry now calls
`isNavigationItem` and reports `installedTopLevelTagBefore`, so it does not reflect
against a removed recursive API. Independent diagnostic item graphs may still
traverse fixture contents; they are outside production protection.

## Verification

- **37 Paper and 933 Fabric 1.21.11 unit tests pass**, zero failures,
  errors or skips. Tests cover direct marker types/values, ignored nested markers,
  tagged carriers, preserved carrier contents, direct-slot cleanup and empty
  portable-storage recognition.
- **25 native Fabric checks pass**: actual transformed menu clicks with a native
  server player and dummy connection; nested carriers survive relevant clicks,
  close, validation and purge; direct unowned items are removed. Pre-click
  shift-move and newly marked result-cursor protection remain effective. Active
  compass pickup, chest/hotbar/offhand denial, both bundle insertion directions
  and an opened-shulker deposit are covered.
- **35 native Paper checks pass**: production listener dispatch on the loaded
  plugin, native player/dummy connection, actual world item spawning, preservation
  through inventory/cursor validation and purge, nested pickup-event acceptance,
  direct-tag cleanup/spawn cancellation, both bundle insertion directions and an
  opened-shulker deposit. Pickup and insertion event checks dispatch events;
  they do not simulate network clicks or physical hopper movement.
- Native runtimes: Minecraft 1.21.11, Paper build 111 and Fabric loader 0.18.2,
  Java 21.0.11. Both checks assert zero connected clients. These are functional
  checks, with no new timing/allocation measurement or multiplayer/UI claim.
- Production compilation passes for Fabric/Forge/NeoForge 1.21.11, Fabric 1.20.1
  and 26.1.2, and Paper 1.21.11/26.2. Test sources also compile for Fabric 1.20.1/
  26.1.2 and Paper 26.2. The Paper performance probe compiles with the new direct
  API. Active targets remain Fabric 26.1.2 and Paper 26.2.
- Existing mapping, unchecked, deprecated API and Gradle warnings are retained.
  No Folia/Forge/NeoForge runtime launch, full matrix or broad audit gate is claimed.

Expected failures and setup attempts are retained: initial Fabric policy tests
fail three assertions; the Paper carrier-preservation test fails against the
old recursive cleaner after native registry initialization. Its first attempt
lacked the CraftRegistry registry bridge. The empty-bundle guard test fails before
its type-check fix. Native Paper attempt 1 lacked a loaded spawn chunk; attempt 2
passes the loaded-chunk checks and exposes the empty-bundle guard gap. Attempt 3
passes after the guard repair. One intermediate Paper 26.2 test compilation fails
because a colored-item constant differs there; the unnecessary constant was
removed from the cross-version unit fixture, and the final compilation passes.

## Commands and retained evidence

Full commands and output are in `green-build.log`, `guard-green-build.log` and
`final-build.log` under `/Volumes/ssd/sw-top-level-protection-20261027/`. The final command is:

```sh
rtk ./gradlew :paper:1.21.11-paper:test :mods:1.21.11-fabric:test :paper:26.2-paper:compileJava :paper:26.2-paper:compileTestJava :paper:1.21.11-paper:compilePerformanceProbeJava --console=plain --max-workers=2
```

Native checks:

```sh
rtk proxy python3 /Volumes/ssd/sw-top-level-protection-20261027/tools/compile.py
rtk proxy python3 /Volumes/ssd/sw-top-level-protection-20261027/tools/run.py fabric fabric-run-2
rtk proxy python3 /Volumes/ssd/sw-top-level-protection-20261027/tools/run.py paper paper-run-3
```

- Frozen production Fabric development JAR SHA-256: `0bbb38a9eb42135d679259864dec4a1013d33fab945955390bb8e269553235a1`.
- Frozen production Paper JAR SHA-256: `41cd3ca7a7dd9fa3fabe16c371d513b5671814cbc9baaea419089df0506cdee2`.
- `baseline-source/`, `final-source/`, `source-change.patch`: scoped source evidence.
- `src/diag/`, `tools/`, `inputs/`: native probe sources, launch/compile code,
  classpaths and frozen production/probe artifacts. Diagnostics are excluded from
  production JARs.
- `fabric-run-2/` and `paper-run-3/`: accepted raw checks, logs, JVM input hashes,
  launch arguments and stopped-process outcomes.
- `paper-run-1/`, `paper-run-2/`, `attempts/` and RED/build logs: unsuccessful
  attempts and their original inputs, retained separately from passing runs.
- `test-results/`, `junit-totals.json`: exact JUnit evidence. `manifest.json` seals
  scoped inputs/results and confirms owned test-JVM cleanup.

Changes are uncommitted. No push, deployment or localization change.
