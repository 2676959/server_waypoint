# Remove reconciliation requests after protected-item removal

Date: 2026-10-27.

**Result:** the selected Paper/Folia removal paths retain cancellation and item
removal, and no longer request reconciliation for the actor or all active
navigators. In a native Paper fixture with two registered active navigation
sessions, each removal path queues zero tasks after the change. A 24-spawn burst
changes from 48 queued player tasks to zero.

## Behavior and scope

- Protected item spawning remains cancelled.
- Protected entity/player and inventory pickup remains cancelled; the entire
  dropped-item entity is still removed. Player denial feedback remains.
- An allay's existing tagged main-hand item and an armor stand's existing tagged
  manipulated equipment item are still cleared; carriers survive.
- Protected item-frame non-PLACE actions remain cancelled and clear the frame's
  item, including both REMOVE and ROTATE. The frame survives.
- These removal paths no longer schedule player repair/restoration. The unused
  `reconcileActivePlayers` helper has been deleted.
- Actor-only reconciliation remains for denied allay/armor-stand placement when
  no existing protected equipment item is removed, item-frame interaction/PLACE,
  and unrelated inventory/lifecycle triggers. Immediate cancellation still occurs.
- Consequently, these removal events themselves no longer restore a missing
  navigation item. Later retained inventory/lifecycle triggers may still restore
  it. This is the user-directed policy change, not request batching.

Only `NavigationProtectionListener.java` changes in production. The shared Paper/
Folia implementation remains one build. No modded source, marker format, nested
protection policy, translation or scheduling abstraction is changed.

## Native before/after verification

The same external probe loads the real plugin in fresh Paper 1.21.11 build 111
servers. It creates two native players with dummy connections, registers them in
navigation, and starts two real ACTIONBAR sessions. It dispatches production
Bukkit/Paper events, reads the actual native `EntityScheduler.oneTimeDelayed`
queues immediately before/after each case, and records deltas. No test replaces
the production listener or scheduler.

| Selected removal case | Before queued tasks | After queued tasks |
| --- | ---: | ---: |
| Protected item spawn | 2 | 0 |
| Player pickup removal | 3 | 0 |
| Inventory pickup removal | 2 | 0 |
| Allay held-item removal | 3 | 0 |
| Armor-stand equipment removal | 3 | 0 |
| Frame REMOVE | 3 | 0 |
| Frame ROTATE | 3 | 0 |
| 24 protected item spawns | 48 | 0 |

The frozen baseline passes all 23 cancellation/removal/retained-path controls and
fails exactly the eight intended no-reconciliation assertions. Its runner exits
1; full logs, failed assertion names and queue receipts remain retained.
After the change, **all 31 native checks pass**. Each retained allay/armor-stand
placement denial, frame PLACE/interaction and protected inventory-click case
still queues exactly one actor task. Ordinary pickup is uncancelled and preserves
its full stack. A separate actual native `World.dropItem` confirms protected
world spawning remains prevented. Allay, stand and frame survival is checked.

There are zero connected clients; the native players are not online. Their real
scheduler queues accept the requests, but this fixture does not execute online
player inventory scans or measure physical client actions. Queue deltas measure
scheduling demand, not completed reconciliations, full MSPT, allocation or TPS.
No Folia runtime, production incident attribution or broader performance gate is
claimed. Java 21.0.11, Apple M4, `-Xms1G -Xmx2G`.

## Builds and evidence

**37 Paper and 687 common unit tests pass**, zero failures, errors or skips.
Paper 1.21, 1.21.9, 1.21.11 and 26.2 compile successfully. Existing Gradle/API
warnings remain in the successful logs. Active Stonecutter targets are unchanged.
Read-only review found no actionable findings. Whitespace checks pass.

Evidence root: `/Volumes/ssd/sw-removal-reconcile-20261027/`.

- `baseline-source/`, `final-source/`, `source-change.patch`: isolated source diff.
- `inputs/before.jar`, `inputs/after.jar`: frozen production plugin artifacts;
  `archive-diff.json` confirms that only the listener class differs.
- Before SHA-256: `a8ec15a2d054453af43cb791eb80e5ed2ce0aa786de5b7c99501d3fe74a44265`.
- After SHA-256: `f38aa16536c46b983f4faa77ff3526c20452cd1b86b3a0dd7a47655cd5193534`.
- `src/diag/RemovalProbe.java`, `inputs/paper-probe.jar`, `tools/`: probe source,
  unchanged compiled probe and exact compilation/launch logic. Diagnostic classes
  are excluded from production JARs.
- `before/`, `after/`: actual queue receipts, every assertion, console logs, input
  hashes, JVM arguments and stopped-process outcomes. The baseline expected FAIL
  remains distinct from the passing after run.
- `build-commands.json`, `baseline-build.log`, `build.log`, `final-build.log`:
  exact Gradle arguments and successful results. The final build follows an
  indentation-only correction; both compilation attempts pass.
- `test-results/`, `junit-totals.json`, `review.json`, `manifest.json`: exact test,
  review and sealed artifact evidence with owned test-JVM cleanup.

Native commands:

```sh
rtk proxy python3 /Volumes/ssd/sw-removal-reconcile-20261027/tools/compile.py
rtk proxy python3 /Volumes/ssd/sw-removal-reconcile-20261027/tools/run.py before
rtk proxy python3 /Volumes/ssd/sw-removal-reconcile-20261027/tools/run.py after
```

Changes remain uncommitted. No push or deployment. Localization impact: none.
Original audit failures and other pending audit tasks remain separate.
