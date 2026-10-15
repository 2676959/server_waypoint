# Manager sort popup over the sort-order toggle — 2026-10-09

The [themed tooltips review](2026-10-09-themed-tooltips.md#independent-review) found that the manager's sort
dropdown can open its popup over the sort-order toggle: when the choices don't fit left of the controls, the
popup shifts right and up one row. The toggle was drawn first with the real pointer, so it was hovered beneath
the hovered choice and its tooltip, requested first, hid the choice's. Vanilla's rules did the same, but the
[design](../specs/2026-10-09-themed-tooltips-design.md) says a control under a popup shows no tooltip.

## Change

While `sortingModeDropdown.isMouseOverPopup(...)` holds, `WaypointManagerScreen.renderScreenContents` draws
everything before the dropdown with `NO_MOUSE`. The decision is the pure `resolveMouseBeneathPopup`. The
dropdown keeps the real pointer, and the all-dimensions toggle, drawn after the dropdown, asks later than the
choice does. The [GUI guide](../../../tips/gui/local-guide.md) describes it in the manager's sidebar section.

## Unit test

`WaypointManagerScreenPopupHoverTest` has 2 tests and was written first. Against a stub that returned the
pointer unchanged, which is the behavior before the change, the first failed with
`expected: <-10000> but was: <57>` and the second passed. Both pass with the helper.

The test covers the decision only. The screen can't be built in plain JUnit, so the call sites in
`renderScreenContents` are covered by the in-game check below and by review.

## Build checks

They ran in a fresh worktree whose branch had been fast-forwarded to the tip of `4.0.0`, with the Stonecutter
target left at `26.1.2-fabric`.

| Check | Command | Result |
| --- | --- | --- |
| Tests | `:mods:26.1.2-fabric:cleanTest :mods:26.1.2-fabric:test :mods:1.20.1-fabric:cleanTest :mods:1.20.1-fabric:test` | `BUILD SUCCESSFUL`. 26.1.2-fabric: 123 test classes, 755 tests. 1.20.1-fabric: 122 classes, 749 tests. 0 failures, 0 errors and 0 skipped on both: the themed tooltips record's counts (753 and 747) plus the 2 new tests. |
| Compile every `mods` target | `compileJava` and `compileTestJava` for each directory in `mods/versions`, with `--continue` | 40 targets (14 Fabric, 14 NeoForge, 12 Forge). `BUILD SUCCESSFUL in 4m 19s`, which includes the first build of every target in the worktree. |
| Whitespace | `git diff --check` | No output. |
| Stonecutter markers | A balance check of `//? if`, `//?}`, `/*?` and `*///?}` in both touched Java files | Balanced. The change edits no marker. |

## In-game check

A throwaway extension of `tools/live-game-test`, not committed, drove the installed `26.1.2-Fabric` and
`1.20.1-Fabric` test clients. Each client created a world, opened the manager at a scaled size of 480 × 270,
clicked the sort dropdown, and drew one frame of `renderScreenContents` for each pointer below on the game
thread. The probe read the winning request from `TooltipLayer`. At that size the sort-order toggle is at
(54, 189), 16 × 16, and the popup row, shifted up one row, is at y 191 with the choices Name, Distance and
Color at x 48, 30 and 12. The Name choice covers 140 of the toggle's 256 pixels.

| Pointer | Popup | Before the change | After the change |
| --- | --- | --- | --- |
| On the toggle's centre, (62, 197) | closed | Ascending order | Ascending order |
| On the Name choice where it covers the toggle, (58, 197) | open | **Ascending order**, the toggle's | Name |
| On the part of the toggle the popup leaves free, (54, 189) | open | Ascending order | Ascending order |
| On the Distance choice, away from the toggle, (38, 199) | open | Distance | Distance |

Both versions gave these results, so the bug reproduced before the change and is gone after it. The "before"
runs used the stub described above, which draws exactly as the code did before the change.

The harness marks each profile `FAIL`, but only because the offline clients log `Failed to verify
authentication` (1.20.1) or `Failed to fetch user properties` and `Couldn't connect to realms` (26.1.2). The
three steps of each run, creating the world, the probe and closing it, passed, and the logs show no exception
from the mod or the probe.

Not checked: how the tooltip looks, where it sits and whether it is on top. The check reads which request wins,
not the pixels, so Task 10 of the [plan](../plans/2026-10-09-themed-tooltips.md) is still open for those.
