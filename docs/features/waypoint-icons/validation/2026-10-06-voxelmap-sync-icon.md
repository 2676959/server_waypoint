# VoxelMap waypoint list sync icon placement — 2026-10-06

The pre-26 redirect used the centered waypoint label coordinates as the sync
icon's upper-left corner. All configured VoxelMap dependencies from Minecraft
1.20.1 through 1.21.11 draw the label at half the screen width; that is not the
row's left edge. VoxelMap 1.21.11 places the label five pixels below the row top,
while earlier releases use three pixels. These differences were checked with
`javap -p -c` against each configured VoxelMap dependency JAR.

The icon now uses `(rowX - 12, rowY + 3)`, matching the existing 26.x behavior.
Minecraft 1.21.9+ exposes entry bounds through `getX()`/`getY()`. Before 1.21.9,
the redirect captures the enclosing `render` arguments in their original order:
graphics, index, row Y, row X, width, height, mouse X, mouse Y, hovered, delta.
The label keeps its original coordinates and color. Local waypoints still have
no sync icon. The production 26.x branch is unchanged.

`VoxelMapGuiListWaypointsItemMixinTest` invokes the real redirect and records
texture and text drawing. It checks placement independently of the label center,
moved/scrolled rows, and local waypoint behavior. Before the fix, the 1.21.11
placement tests failed (the first drew X=320 instead of X=88); after the fix,
all three tests passed on every supported Fabric target.

## Verification

All targets compiled both production and test sources. Complete versioned `test`
suites were executed using `rtk ./gradlew :mods:<version>-fabric:test`. The active
Stonecutter project remains `26.1.2-fabric`; no source switch was used.

| Minecraft | Sync icon regression | Full suite tests | Full suite failures |
| --- | --- | --- | --- |
| 1.20.1 | 3 passed | 606 | 5 |
| 1.20.2 | 3 passed | 606 | 5 |
| 1.20.4 | 3 passed | 606 | 5 |
| 1.20.6 | 3 passed | 608 | 5 |
| 1.21 | 3 passed | 608 | 5 |
| 1.21.2 | 3 passed | 608 | 5 |
| 1.21.3 | 3 passed | 608 | 5 |
| 1.21.5 | 3 passed | 608 | 0 |
| 1.21.6 | 3 passed | 608 | 0 |
| 1.21.9 | 3 passed | 610 | 0 |
| 1.21.11 | 3 passed | 610 | 0 |
| 26.1.2 | 3 passed | 615 | 0 |
| 26.2 | 3 passed | 615 | 0 |
| 26.3 | 3 passed | 615 | 0 |

The five failures on each of the seven oldest targets are in the existing
`XaeroMinimapWorldTest`:

- `missingAutomaticWorldResolvesRequestedDimension`
- `enteringNetherDoesNotResolveToStaleOverworld`
- `returningToOverworldDoesNotResolveToStaleNether`
- `matchingAutomaticWorldPreservesXaerosConnectedSubworld`
- `nonCurrentDimensionResolvesItsOwnWorld`

Each fails with `InstantiationException: xaero.hud.minimap.world.MinimapWorld`
at the test's `Unsafe.allocateInstance` call. The old dependency declares
`MinimapWorld` abstract, so the failure happens while constructing the fixture,
before the production code or VoxelMap render code runs. Those tests and the
unrelated pre-existing working-tree changes were preserved.

The targeted check can be rerun with:

```sh
rtk ./gradlew :mods:1.21.11-fabric:test --tests '*VoxelMapGuiListWaypointsItemMixinTest'
```

Stonecutter generation and compilation validated the conditional branches;
`git diff --check` passed. These checks record drawing calls without applying
Mixins in a running game or validating the visual appearance, clipping, or
texture loading. In-game verification remains: open VoxelMap's waypoint list
with tracked and local waypoints, scroll it, and resize the screen on 1.21.11
and an earlier release. Confirm tracked icons stay immediately left of their
rows and local rows have no sync icon.
