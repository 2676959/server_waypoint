# Remove continuous modded protection scans

Date: 2026-10-26.

The user requested removal of continuous allay and dropped-item protection scans
on modded platforms. The shared Fabric/Forge/NeoForge source now has neither
`AllayNavigationMixin`'s `tick` injection nor `ItemEntityNavigationMixin`'s `tick`
injection. This is a focused policy change with functional validation; no new
MSPT, allocation or throughput result is claimed.

## Source and behavior

The change deletes 33 lines across three Java files:

- `AllayNavigationMixin`: remove the per-tick `sanitizeNavigationItems` injection
  and its unused `CallbackInfo` import. Allay held items and storage are no longer
  scanned and cleared on every entity tick.
- `ItemEntityNavigationMixin`: remove `discardNavigationItemBeforeTick`. A dropped
  item whose existing stack acquires a navigation marker is no longer polled and
  discarded on every tick.
- `ModNavigationHooks`: remove the now-unused `sanitizeAllay` method, its private
  `removeNavigationItems` helper, and the two imports used only by that cleanup.

The event-driven `mobInteract`, `setItem` and `playerTouch` injections remain. A
tagged player handoff to an allay is still denied, assigning a tagged stack to a
dropped item still discards it, and player contact with an existing tagged dropped
item still discards it. The mixin classes remain registered for those handlers.
No feature flag, fallback scan or replacement scheduled work was introduced.

Local Minecraft sources for 1.21.11 and active 26.1.2 confirm the separate entity
`tick` and interaction/assignment/pickup entry points. Remaining inline Stonecutter
markers are balanced; compilation covers both sides of the `>=26` remap predicate.
Active targets remain Fabric 26.1.2 and Paper 26.2. Paper sources are not changed by
this task. Localization impact: none.

## Runtime regression check

An external native Fabric 1.21.11 probe runs unchanged against frozen before/after
named-development artifacts, with the production mixins active:

- Before removal, the 200-server-tick check records four expected failed assertions:
  the allay loses its held and stored marked items, the in-place-tagged dropped
  item is discarded, and that discarded item stops receiving native ticks.
  The probe writes its complete result and exits with a failing command status.
- After removal, all **16 assertions pass**. The allay retains its tagged compass
  and tagged stored map; the dropped item retains the complete tagged stack. Both
  the allay and the tagged/ordinary dropped items receive **196 native entity
  ticks** during the 200-server-tick window. The four-tick difference is
  retained in the raw result rather than treated as 200 entity ticks.
- An ordinary allay holding a diamond and storing an apple remains intact; an
  ordinary dropped stone stays alive and ticks. No real players connect.
- Setup independently proves that assigning an already-tagged dropped stack still
  discards the entity, player contact with a stack tagged in place still discards
  it, and a tagged player-to-allay handoff still returns `InteractionResult.FAIL`.
  A native fake player supplies those method contexts; this is not physical client
  input or a multiplayer test.

Allays use `NoAI`, disabled loot pickup and no gravity; dropped items use no gravity
and are separated to avoid merging. Their chunks are force-loaded. For the
continuous dropped-item fixture, the marker is applied in place **after** entity
creation/addition so the retained assignment guard does not preempt the tick test.
The same setup guards pass on both artifacts before observing the tick behavior.

## Build verification

- **927 Fabric 1.21.11 tests pass**, with zero failures, errors or skips.
- Compilation passes for Fabric 1.21.11, Forge 1.21.11, NeoForge 1.21.11 and Fabric
  26.1.2. Compiler/mapping/deprecation warnings remain in the retained successful
  build log. No Forge/NeoForge runtime launch or all-version matrix is claimed.
- Content comparison of the before/after Fabric archives finds only the three
  intended class entries changed: `AllayNavigationMixin`, `ItemEntityNavigationMixin`
  and `ModNavigationHooks`. Diagnostic classes are excluded from the mod archive.

```sh
rtk ./gradlew :mods:1.21.11-fabric:test :mods:1.21.11-fabric:shadowJar :mods:1.21.11-neoforge:compileJava :mods:1.21.11-forge:compileJava :mods:26.1.2-fabric:compileJava --console=plain --max-workers=2
```

The existing original-release audit ledger and its failures remain retained.
This scoped removal does not complete the broader performance audit. No commit,
push or deployment accompanies this change.

## Artifacts and evidence

Root: `/Volumes/ssd/sw-modded-tick-scan-removal-20261026/`.

- Before Fabric named-development JAR SHA-256:
  `6930edcb24853a142285d73dca88fa22c9b86ff1640eaaa474277c97da188c49`.
- After Fabric named-development JAR SHA-256:
  `b2c02638d0d20bb1b58a578af7855f327b267f0bf1841871a7ae197d3f69a322`.
- `before/result.json`, `before/console.log`: expected policy failures and retained
  successful controls. `after/result.json`, `after/console.log`: all passing
  controls and native entity tick counts. Per-run `inputs.json` records exact JVM
  arguments and artifact/probe hashes.
- `src/diag/`, `tools/`, `inputs/`: native probe sources, compilation/launch scripts,
  exact classpath and frozen artifacts. The probe is Java-17-compatible; the native
  runtime uses Java 21 and Fabric development mappings.
- `evidence/build.log`, `build-command.json`, `build-outcome.json`,
  `fabric-test-results/`, `junit-totals.json`: exact build result and retained JUnit
  evidence. `archive-diff.json` records the three changed archive entries.
- `evidence/manifest.json`: sealed inputs, source diff, logs, results and verification
  of owned test-JVM cleanup. A first build-wrapper launch used a nonexistent absolute
  `rtk` path and did not invoke Gradle; its command/error record is retained. The
  corrected command resolves `rtk` from PATH and succeeds.
