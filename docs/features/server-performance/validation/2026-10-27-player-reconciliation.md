# Per-player Paper reconciliation requests

Deferred navigation-item reconciliation now keeps one pending entry per player
UUID. The first request schedules one task on that player's entity scheduler;
later requests merge their restoration flags with OR. The task reads the current
inventory when it executes. Requests made during execution merge into one further
pass on the next tick, with a fresh restoration flag for that pass.

Inventory-close validation uses the same queue. Top-inventory cleanup and cursor
sanitation still run synchronously in the close event.

The queue removes completed entries and clears rejected or retired tasks by entry
identity. An old callback cannot remove a new connection's entry with the same
UUID. Disconnect invalidates entries before cleanup and again in a `finally`
block, because cleanup can emit more inventory events. Plugin shutdown closes the
queue before navigation services shut down, clears its entries, and rejects new
requests. Already queued callbacks become harmless when their entry is removed.
No inventory work or scheduler calls run under the queue's lock.

## Verification

All 16 `PendingPlayerReconciliationsTest` cases pass on Paper 1.21, 1.21.9,
1.21.11, and 26.2. They cover five requests producing one scheduled scan of the
latest state, restoration OR, independent players, requests during execution,
concurrent bursts before and during execution, completion, failed scans,
rejection, scheduler exceptions, retirement, disconnect/reconnect, and shutdown.
The scheduling-per-request baseline fails the burst assertion: expected one task,
received five.

Initial full suite results from October 27, 2026, before the test-fixture fix:

| Target | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Common | 687 | 0 | 0 | 0 |
| Paper 1.21 | 52 | 0 | 0 | 0 |
| Paper 1.21.9 | 53 | 0 | 0 | 0 |
| Paper 1.21.11 | 53 | 0 | 0 | 0 |
| Paper 26.2 | 58 | 6 | 0 | 0 |

The six initial failures were in the then-untouched `PaperNavigationItemProtectionTest` fixture
on 26.2. Each throws `NullPointerException: Components not bound yet` inside the
native `ItemStack` constructor before reaching the item-protection assertions:

- `portableStorageGuardsRecognizeEmptyBundlesAndShulkers`
- `ignoresNestedMarkersWithoutChangingCarrierContents`
- `carrierTopLevelMarkerCountsRegardlessOfItsContents`
- `markerRequiresTheBukkitByteTypeAndTreatsEveryNonzeroByteAsTrue`
- `invalidInventoryCleanupPreservesNestedMarkersAndRemovesOnlyDirectMarkers`
- `invalidInventoryCleanupLeavesAnUntaggedCarrierUntouched`

All four Paper targets compile. Paper 1.21.11 `shadowJar` succeeds. The standalone
queue compiles with `javac --release 17`. Active Stonecutter projects are unchanged.
Existing Gradle and native-library warnings remain.
Read-only concurrency and lifecycle review found no actionable issues. Whitespace
checks pass.

```sh
rtk ./gradlew :common:test :paper:1.21.11-paper:test :paper:1.21.11-paper:shadowJar :paper:1.21-paper:test :paper:1.21.9-paper:test :paper:26.2-paper:test --console=plain --max-workers=2
```

The initial combined command exited unsuccessfully because of the six 26.2
fixture failures above.

## Item-fixture initialization fix

The six failures were reproduced with a focused 26.2 run before changing the
fixture. Minecraft 26.2 registers item defaults during bootstrap but binds them
later through `DataComponentInitializers` during resource loading. The test now
builds these initializers against `VanillaRegistries.createLookup()` and applies
the real default components before creating item stacks. This setup is gated
with `>=26.2`; production code and test assertions are unchanged.

All six previously failing tests pass. The full suites also pass after the fix:

| Target | Tests | Failures | Errors | Skips |
| --- | ---: | ---: | ---: | ---: |
| Common | 687 | 0 | 0 | 0 |
| Paper 1.21 | 52 | 0 | 0 | 0 |
| Paper 1.21.9 | 53 | 0 | 0 | 0 |
| Paper 1.21.11 | 53 | 0 | 0 | 0 |
| Paper 26.2 | 58 | 0 | 0 | 0 |

```sh
rtk ./gradlew :common:test :paper:1.21-paper:test :paper:1.21.9-paper:test :paper:1.21.11-paper:test :paper:26.2-paper:test --console=plain --max-workers=2
```

The post-fix command exits successfully. Active Stonecutter projects are unchanged.
These are queue and unit-suite checks, not live Paper/Folia,
physical player-action, TPS, or production-incident evidence. No deployment or
publication occurred. Localization impact: none. Changes remain uncommitted.
