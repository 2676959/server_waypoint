# Themed tooltips validation — 2026-10-09

Build checks for the themed tooltips change, from the
[implementation plan](../plans/2026-10-09-themed-tooltips.md) and its
[design](../specs/2026-10-09-themed-tooltips-design.md). They ran in the main checkout on branch
`4.0.0`, with the active Stonecutter target left at `26.1.2-fabric`. Nothing is committed.

## Build checks

| Check | Command | Result |
| --- | --- | --- |
| Tree | `git branch --show-current`, `git status --short` | `4.0.0`. Before this record and the README link were added: 21 modified and 8 new files, all from the plan's tasks; no other file. |
| Whitespace | `git diff --check` | No output. It skips untracked files, so the 8 new files were scanned with `git diff --no-index --check /dev/null <file>` too: no output, and no tabs. |
| Tests, active target and Java 17 before 1.21.6 | `:mods:26.1.2-fabric:cleanTest :mods:26.1.2-fabric:test :mods:1.20.1-fabric:cleanTest :mods:1.20.1-fabric:test` | `BUILD SUCCESSFUL`. 26.1.2-fabric: 122 test classes, 753 tests. 1.20.1-fabric: 121 classes, 747 tests. 0 failures, 0 errors and 0 skipped on both. |
| Compile every `mods` target | `compileJava` and `compileTestJava` for each directory in `mods/versions`, with `--continue` | `targets: 40` (14 Fabric, 14 NeoForge including 1.21.7, 12 Forge). `BUILD SUCCESSFUL in 2m 3s`. |
| No vanilla tooltip left | `grep -rn "components\.Tooltip\|setTooltipForNextFrame\|setTooltipForNextRenderPass" mods/src/main/java` | No output. |
| Stonecutter markers | A balance check of `//? if`, `//?}`, `/*?`, `*///?}` in every touched main and test file | Balanced. |

The two Fabric suites differ by six tests that already differed before this change:
`MovementAllowedScreenInitialFocusTest` (2) and `ComboBoxPopupRenderTest` (5) run only on 26.1.2, and
`WaypointBlockItemAlphaTest` (1) runs only on 1.20.1.

## New tests

All pass on both Fabric targets.

| Class | Tests |
| --- | --- |
| `TooltipPlacementTest` | 13 |
| `ScalableTextTest` | 3 |
| `TranslucentTooltipTest` | 7 |
| `TooltipLayerTest` | 14 |
| `TooltipNarrationTest` | 5 |

`PaddingWidgetContractTest` gains one assertion, for `TranslucentTooltip`. Each test was written first and
seen failing before its code existed.

## Cases the design's test list leaves out

Each has a test. To check that the test can fail, the behavior was removed, the test failed, and the file was
restored.

| Case | Test | Change that made it fail |
| --- | --- | --- |
| An inactive but hovered control still shows its tooltip | `inactiveControlStillShowsItsTooltip` | Returning early from `scheduleTooltip` while the control is inactive |
| A control focused by a click and still hovered replaces an earlier request | `controlFocusedByAClickAndHoveredReplacesAnEarlierRequest` | Replacing only when the keyboard was used last |
| A language change wraps an equal message again | `languageChangeWrapsAnEqualMessageAgain` | Comparing only the message |
| A failed draw doesn't leave its request for a later frame | `renderDropsTheRequestEvenWhenDrawingFails` | Clearing the request after the draw instead of before it |
| A zero-height control counts as one pixel high | `besideControlCountsAZeroHeightControlAsOnePixelHigh` | Removing the guard |

## Not covered by a test

- `WaypointManagerScreen.IconToggleButton`'s two calls: the class is private to the screen.
- The remote teleport button, the server rail and the remote tree: they need a cross-server catalog.
- How a tooltip looks, where it sits and whether it is on top: only a running client shows that.

## Notes

- The guide describes the rule for tooltips beside a control without the design's "at most 3 pixels" and
  "at most 5 pixels" figures, because the formula does not give them. For a 20-pixel control, the text block
  lands 2 pixels above the control's bottom edge when the pointer is on the top edge, and 7 pixels below it when
  the pointer is on the bottom edge. Section 4 of the design still has the wording.

## Independent review

One fresh reviewer read the whole change against the design and the plan. It found no Critical defect, and
its verdict was "with fixes". It re-derived the placement tests by hand and compared the three formulas with
the decompiled vanilla positioners of 13 versions, from 1.20.1 to 26.3.

- **Fixed (documentation only):** the guide now documents `ScalableText.getTextWidth()`, says that a
  keyboard-focused control still requests its tooltip when drawn with `NO_MOUSE`, lists every change from
  vanilla's placement, and says a widget drawn outside a `MovementAllowedScreen` shows no tooltip. The
  `TooltipLayer` Javadoc no longer claims more than it does. Rerun afterwards: `git diff --check`, the
  26.1.2-fabric suite (753 tests, 0 failures) and `:mods:1.20.1-fabric:compileJava`.
- **Not fixed, for the maintainer to decide:** the manager's sort dropdown can open over the sort-order toggle.
  The toggle is drawn first with the real pointer, so it is hovered and its tooltip wins over the popup item's.
  Vanilla's tooltip rules did the same, so this change doesn't cause it, but it contradicts the design's
  statement that a control under a popup shows no tooltip. A fix would draw the controls before the dropdown
  with `NO_MOUSE` while the popup is open under the pointer. Fixed afterwards that way; see the
  [manager sort popup record](2026-10-09-manager-sort-popup-hover.md).
- **Deferred as minor:** an erratum for the design's "at most 3/5 pixels" sentence and its "Not implemented
  yet" status line; `TooltipLayer.render` ignores a different `Font` after the first call; `scheduleTooltip`
  could return early for a control that is neither hovered nor focused; no test covers which placement rule
  `render` chooses.

## In-game checks

Not run. The `26.1.2-fabric` dev client started, but it runs as a bare `java` process with no app bundle,
so the computer-use tools could not be granted access to its window, and the fallback of scripted keyboard
and mouse input was not permitted in the session. The checklist is in Task 10 of the plan. Until it runs,
placement, layering and theme colors are checked only by the unit tests above and by review.
