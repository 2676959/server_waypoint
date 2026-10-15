# Themed Tooltips Design

Status: design agreed on 2026-10-09. Not implemented yet.

## Intent

Every surface in the mod's screens follows the runtime color theme (`WidgetThemeManager`), except
tooltips. They are still vanilla's: a near-black box with a purple frame and white text, drawn by
vanilla's `Tooltip` and its deferred tooltip rendering. They look foreign in all four built-in themes,
and theme edits don't reach them.

Vanilla tooltips are used in 16 places in 9 files under `mods/.../client/gui`:

- 12 tooltips for a whole control, set with `setTooltip(Tooltip.create(...))`.
- 4 tooltips at the pointer, scheduled while rendering. `IconListWidget` and
  `RemoteWaypointPanel.BrowserTree` carry their own copies of the 1.21.6 version branch;
  `SettingsListWidget` and `AbstractWaypointPropertiesScreen` go through
  `DrawContextHelper.scheduleTooltipAtPointer`.

This design adds a tooltip widget in the GUI's own style, `TranslucentTooltip`, and replaces every
vanilla tooltip in the mod's screens with it. Only the look changes: the text, wrapping, timing,
placement and narration of each tooltip stay as they are. It is the reference for the
implementation plan.

## Scope

In scope:

1. The tooltip's look.
2. The new components and the API that schedules a tooltip.
3. When a tooltip shows, and which one wins.
4. Placement.
5. Layering on every supported version.
6. Narration.
7. Moving every call site off vanilla's tooltip.
8. Tests, verification and documentation.

Out of scope:

- Vanilla screens and other mods' screens. They keep vanilla tooltips.
- Item tooltips, such as the compass and map tooltips in inventories, and the in-world HUD labels.
- New theme variables. The tooltip uses existing roles.
- A delay API for control tooltips. No control uses one.
- Adding tooltips to controls that have none today, or changing any tooltip's text.
- Tooltips with images or items, and narration of pointer tooltips (vanilla doesn't narrate them
  either).
- A mixin. Widgets schedule their own tooltips; see [the limit this brings](#known-limits).

## 1. Look

`TranslucentTooltip` is a floating, non-modal surface, so it uses the popup roles that suggestion
lists and dropdown popups use:

- `background.popup` (`POPUP_BACKGROUND`) fills the whole box.
- `border.default` (`BORDER`) draws a 1-pixel outline on the box's edge.
- `text.primary` (`TEXT_PRIMARY`) is the text color, with a shadow. Styled spans keep their own
  colors, such as a server's state or a dimension's color.

Colors are resolved each time the tooltip is drawn, so a theme change, including a live preview in the
theme editor, applies to the next frame. The built-in themes give:

| Theme | Fill | Outline | Text |
| --- | --- | --- | --- |
| Translucent Dark | `#FA202020` | transparent | `#FFE8E8E8` |
| Modern Dark | `#FA202C38` | transparent | `#FFE8F0F7` |
| High Contrast | `#99141414` | `#FFFFFFFF` | `#FFFFFFFF` |
| Classic | `#99000000` | transparent | `#FFFFFFFF` |

The two dark themes and Classic have a transparent default border, so their tooltips are borderless
glass boxes, like their suggestion lists.

Geometry:

- The text wraps at 170 pixels, vanilla's `Tooltip` width. A `\n` in the text starts a new line, as it
  does in vanilla tooltips.
- Lines are `font.lineHeight` (9 pixels) apart, like `ScalableText`.
- The text block is as wide as its widest line and `9 × lines − 1` pixels high, so a single line is 8
  pixels high, as in vanilla.
- The box extends 4 pixels past the text block on every side: the 1-pixel outline plus 3 pixels of
  padding. This is vanilla's footprint, so vanilla's screen-edge margins in [Placement](#4-placement)
  still fit.
- Drawing order is fill, outline, text, as the suggestion list draws.

## 2. Components

### `client.gui.widgets.TranslucentTooltip`

The visible surface, named like `TranslucentButton` and `TranslucentTextField`. It extends
`ShiftableWidget` and implements `Padding`:

- `TranslucentTooltip(Font font)`.
- `setMessage(Component message)` replaces the text. It wraps the text again only when the message
  differs from the current one (`equals`) or the game's language (`Language.getInstance()`) has
  changed since the last wrap, as vanilla's `Tooltip` caches its lines.
- `isEmpty()` is true when the wrapped text has no characters.
- `getWidth()` and `getHeight()` are the text block. The `Padding` methods add 4 pixels on every
  side.
- Its render method (`render_method_swap`) draws the box and the text at its position.
- It reuses a retained `ScalableText` with a maximum width of 170 and `TEXT_PRIMARY` for wrapping and
  drawing the lines.

It is never registered with a screen and takes no input.

### `ScalableText.getTextWidth()`

A new method: the width of the widest wrapped line, scaled. Without wrapping (no maximum width), it is
the width of the whole text. `getWidth()` keeps its meaning: the maximum width while wrapping. The
tooltip needs the widest line to shrink to fit its text.

### `client.gui.layout.TooltipPlacement`

Pure geometry with no state. Each method returns the text block's top-left corner as a `Position(x, y)`
record. The rules are vanilla's three tooltip positioners, which are the same on every supported
version (1.20.1 through 26.3):

- `atPointer(screenWidth, screenHeight, mouseX, mouseY, width, height)`: vanilla's
  `DefaultTooltipPositioner`.
- `besideControl(screenWidth, screenHeight, mouseX, mouseY, width, height, ScreenRectangle control)`:
  vanilla's `MenuTooltipPositioner`.
- `belowOrAboveControl(screenWidth, screenHeight, width, height, ScreenRectangle control)`: vanilla's
  `BelowOrAboveWidgetTooltipPositioner`.

[Placement](#4-placement) gives the formulas.

### `client.gui.widgets.TooltipLayer`

The tooltip of the current frame. It is static and used on the render thread only:

- `scheduleAtPointer(Component text, int mouseX, int mouseY)` requests a tooltip at the pointer.
  Rails, tree entries, settings rows and form fields use it. The coordinates are screen-space, never
  coordinates after a render translation.
- `scheduleForControl(...)` is package-private. `ShiftableClickableWidget.scheduleTooltip` calls it
  with the text, the control's visual bounds, whether the control is hovered or focused, whether the
  keyboard was used last, and the pointer.
- `render(GuiGraphics context, Font font, int screenWidth, int screenHeight)` draws the request, if
  any, and clears it.
- `clear()` drops the request.
- `scheduled()` is package-private and returns the request, for tests.

`render` passes the text to one shared `TranslucentTooltip`, created on first use with the screen's
font. It does nothing when the request is missing or its text is empty. Otherwise it places the text
block with `TooltipPlacement`, positions the surface and draws it between
`DrawContextHelper.nextTooltipLayer` and `previousTooltipLayer`.

### `ShiftableClickableWidget`

- `setTooltip(@Nullable Component tooltip)` stores the control's tooltip; `null` removes it. Its
  Javadoc lists the classes that pass the tooltip on and the two calls a class needs to do so.
- `protected final void scheduleTooltip(int mouseX, int mouseY)` is the last step of a renderer that
  supports tooltips. Without a tooltip it returns at once, so a widget without one never reads the
  game's input state, which unit tests don't have.
- `protected final void narrateTooltip(NarrationElementOutput output)` adds the tooltip to narration;
  see [Narration](#6-narration).
- `protected boolean isKeyboardNavigating()` reads
  `Minecraft.getInstance().getLastInputType().isKeyboard()`. Tests override it, as
  `MovementAllowedScreenButtonKeyTest` does with the screen's method of the same name.

Vanilla's `setTooltip(Tooltip)` still exists on every widget, so `setTooltip(null)` doesn't compile:
the call is ambiguous. Remove a tooltip with `setTooltip((Component) null)`.

These classes call `scheduleTooltip` at the end of their renderer and `narrateTooltip` at the end of
`updateWidgetNarration`. They are the classes whose instances have a tooltip today:

| Class | Renderer | Narration |
| --- | --- | --- |
| `TranslucentButton` | after the label | was empty |
| `IconButton` | after the icon | was empty |
| `ColorSquareButton` (and `RandomColorSquareButton`, which inherits both) | at the end | was empty |
| `AbstractDropdownMenuWidget` | at the end of its final renderer, after the popup when the popup is drawn with the control | after `defaultButtonNarrationText` |
| `AbstractDropdownMenuWidget.AbstractMenuItem` | at the end of its final renderer | after `defaultButtonNarrationText` |
| `WaypointManagerScreen.IconToggleButton` | after the icon | was empty |

### `DrawContextHelper`

- `nextTooltipLayer(context)`: `nextStratum()` from 1.21.6; before 1.21.6, a z translation of 400.
- `previousTooltipLayer(context)`: nothing from 1.21.6; before 1.21.6, a z translation of −400.
- `scheduleTooltipAtPointer` is removed. Its callers use `TooltipLayer.scheduleAtPointer`, which needs
  no version branch.

### `MovementAllowedScreen`

Its final render method (`render_method_swap`) becomes:

1. `TooltipLayer.clear()`.
2. The themed background, before 1.21.6, as now.
3. `renderScreenContents(...)`.
4. `TooltipLayer.render(context, this.font, this.width, this.height)`.

Subclasses don't change. Only screens that extend `MovementAllowedScreen` draw the layer; all of the
mod's screens do, and none of its widgets is drawn anywhere else.

## 3. When a tooltip shows

### Control tooltips

`scheduleTooltip(mouseX, mouseY)` follows vanilla's widget tooltip rules:

- With no tooltip, nothing happens.
- While the control is hovered, it requests the tooltip beside the control.
- While the control is focused, isn't hovered and the keyboard was used last, it requests the tooltip
  below or above the control.
- Otherwise nothing happens. A control focused by a mouse click and no longer hovered shows nothing.
- A focused control's request replaces an earlier one in the same frame, whichever input focused it.
- Inactive controls show their tooltip too, as in vanilla. The remote teleport button relies on this:
  its tooltip explains why it is disabled.

`isHovered()` is prepared by vanilla's high-level wrapper before the renderer runs. A control drawn
with `DrawContextHelper.NO_MOUSE`, under a dialog, a popup or the swatch, is not hovered, so its
tooltip doesn't show.

The request carries the control's visual bounds (`VisualPositioning`), not its content bounds, so
"below" and "above" are measured from the visible outline. That outline is 1 pixel above the content
for `TranslucentButton` and 1 pixel outside it for `ColorSquareButton`.

### Pointer tooltips

Their owners keep deciding when to show them:

- `IconListWidget` and `BrowserTree` request the hovered entry's label at once.
- `SettingsListWidget` and the waypoint form keep their 500 ms rest timers, and still skip the row's
  action and the remove-icon button, which have tooltips of their own.

A pointer request never replaces an earlier one.

### One tooltip per frame

The first request in a frame wins; only a focused control's request replaces it. This is vanilla's
rule from 1.21.6. Before 1.21.6, vanilla let a later pointer tooltip replace an earlier one; the layer
uses the newer rule on every version. Two requests in one frame are rare: the pointer is over one
thing at a time.

Requests made while one of the mod's screens isn't rendering are dropped when the next frame starts,
and the layer is cleared after it draws, so a request from a frame that failed never shows later.

## 4. Placement

`w` and `h` are the text block's size, `W` and `H` the screen's, and `(mx, my)` the pointer. For a
control, `c` is its visual rectangle. Every rule returns the text block's top-left corner `(x, y)`; the
box is drawn 4 pixels outside it.

At the pointer (`atPointer`):

- `x = mx + 12`, `y = my − 12`.
- If `x + w > W`: `x = max(mx − 12 − w, 4)`.
- If `y + h + 3 > H`: `y = H − h − 3`.

Beside a control (`besideControl`), right of the pointer and below the control, or above it near the
bottom of the screen:

- `x = mx + 12`. If `x + w > W − 5`: `x = max(mx − 12 − w, 9)`.
- `y0 = my + 3` and `p = h + 6`.
- `off(a, b) = round(lerp(min(|a − b|, c.height) / c.height, c.height − 3, 5))`. The tooltip overlaps
  the control by at most 3 pixels and stays at most 5 pixels from it, depending on where the pointer
  is.
- If `c.bottom + 3 + off(0, 0) + p ≤ H − 5`: `y = y0 + off(y0, c.top)`.
- Otherwise: `y = y0 − p − off(y0, c.bottom)`.
- A control with a height below 1 counts as 1 pixel high.

Below or above a control (`belowOrAboveControl`), for keyboard focus:

- `x = c.left + 3`, `y = c.bottom + 4`.
- If `y + h + 3 > H`: `y = c.top − h − 4`.
- If `x + w > W`: `x = max(c.right − w − 3, 4)`.

Every rule then applies `y = max(y, 4)`, which keeps the box's top on the screen. Vanilla lets a
tooltip near the top edge run off it. Apart from this clamp, the zero-height guard and the
visual-bounds anchor from [section 3](#control-tooltips), the rules are vanilla's.

## 5. Layering

The tooltip is drawn last, after `renderScreenContents`, on its own layer:

- From 1.21.6, `nextStratum()` puts it above every earlier stratum, including popups, the form's swatch
  and its item preview.
- Before 1.21.6, a z translation of 400 matches vanilla's tooltip depth. It is above GUI item models
  (about 150) and the swatch's item-overlay layer (200).

## 6. Narration

`narrateTooltip(output)` adds the tooltip as `NarratedElementType.HINT`, the element vanilla's `Tooltip`
adds. `AbstractWidget.updateNarration` is final, so the supporting classes call it from
`updateWidgetNarration`. Four of them narrated nothing else, so the narrator keeps reading what it
reads now. Pointer tooltips stay unnarrated.

## 7. Call sites

After the change, nothing under `mods/src/main/java` imports `net.minecraft.client.gui.components.Tooltip`
or calls `setTooltipForNextFrame` or `setTooltipForNextRenderPass`.

| File | Now | After |
| --- | --- | --- |
| `RemoteWaypointPanel` | Teleport button: `setTooltip(Tooltip.create(...))` in the constructor and `updateTeleportAction` | `setTooltip(Component.translatable(...))` |
| `RemoteWaypointPanel.BrowserTree` | `renderHoveredTooltip` splits with `Tooltip` and has its own 1.21.6 branch | `TooltipLayer.scheduleAtPointer(identity, mouseX, mouseY)` |
| `WaypointManagerScreen` | `IconDropdownMenu` (constructor and `setMessage`), `IconMenuItem` and `IconToggleButton.updatePresentation` set vanilla tooltips | `setTooltip(message)`; `IconToggleButton` makes the two calls |
| `WidgetThemeConfigScreen` | Color picker button | `setTooltip(Component.translatable(...))` |
| `ClientConfigScreen` | Reset buttons | `setTooltip(resetLabel)` |
| `SwatchWidget` | Random, current and previous color buttons | `setTooltip(...)` with the same text |
| `WaypointIconPicker` | Clear button | `setTooltip(clearLabel)` |
| `IconListWidget` | `setTooltip(null)` every frame, then its own 1.21.6 branch | `TooltipLayer.scheduleAtPointer(entryLabel(entry), mouseX, mouseY)`; the dead `setTooltip(null)` goes |
| `SettingsListWidget` | `scheduleTooltipAtPointer` with `Tooltip`-split lines | `TooltipLayer.scheduleAtPointer(text, mouseX, mouseY)` |
| `AbstractWaypointPropertiesScreen` | `scheduleTooltipAtPointer` with `Tooltip`-split lines | `TooltipLayer.scheduleAtPointer(...)` |
| `ServerListWidget` | Comment: "Vanilla tooltip splitting treats the newline as a line break." | Names tooltip wrapping instead |

## 8. Tests

New tests under `mods/src/test/java/_959/server_waypoint/common/client/gui`:

- `layout/TooltipPlacementTest`: hand-computed cases for each rule. At the pointer: the offset,
  flipping left at the right edge and staying above the bottom. Beside a control: below it with the
  overlap or gap the pointer's position gives, above it near the bottom, flipping left. Below or above
  a control: both sides and the shift left at the right edge. The top clamp for each rule.
- `widgets/TooltipLayerTest`: the first request wins, a focused control's request replaces it and a
  pointer request doesn't; `clear()`; empty text. Through a test widget that sets its hover and focus
  and overrides `isKeyboardNavigating()`: hovered is beside the control, keyboard focus without hover
  is below or above, mouse focus without hover requests nothing, and the request carries the visual
  bounds. Each test clears the static layer before and after.
- `widgets/TranslucentTooltipTest`, with a font double local to the test, because `TestFont` never
  wraps: wrapping at 170 pixels, `\n` breaks, the width of the widest line, `9 × lines − 1` height,
  4-pixel insets, and no new wrap for an equal message.
- `PaddingWidgetContractTest` adds `TranslucentTooltip`.
- Narration: with a recording `NarrationElementOutput`, `updateNarration` adds the hint exactly when a
  tooltip is set, for `TranslucentButton`, `IconButton`, `ColorSquareButton` and test subclasses of
  `AbstractDropdownMenuWidget` and `AbstractMenuItem`. `IconToggleButton` is private to the manager
  screen, so review covers its two calls.

## 9. Verification

Builds:

1. `git diff --check`.
2. `:mods:26.1.2-fabric:test`, the active target, and `:mods:1.20.1-fabric:test`, which runs Java 17
   and the branch before 1.21.6.
3. `compileJava` and `compileTestJava` for every `mods` target. `nextTooltipLayer` branches at 1.21.6,
   and the removed inline branches touched both sides of it.
4. A search for vanilla tooltip use under `mods/src/main/java` finds nothing.

In game, compiling can't confirm placement or layering. Dev clients for `26.1.2-fabric` (the stratum
path) and `1.20.1-fabric` (the z-translation path) check, with a screenshot per item:

1. Manager: a dimension rail entry; the sort dropdown and one of its open choices; the scope, grouping
   and sort-order toggles; Tab to a toggle for the tooltip below or above it.
2. Client settings: a row's tooltip after 500 ms; a reset button; a tooltip near the right and bottom
   edges flips.
3. Add form: a field's tooltip after 500 ms; the remove-icon button; with the swatch open, its buttons'
   tooltips above the swatch and the item preview, and no field tooltips.
4. Theme editor: the color button's tooltip; switching to High Contrast and Classic changes the fill and
   outline at once.

The remote teleport button, server rail and remote tree need a cross-server catalog. Unit tests and
review cover them unless a Velocity setup is available. Results go in this feature's `validation/`
folder.

## 10. Documentation

- `docs/tips/gui/local-guide.md`:
  - A "Tooltips" section: the look; `setTooltip`, the two calls and the classes that make them;
    `TooltipLayer.scheduleAtPointer`; one tooltip per frame; placement and its one change from vanilla;
    layering; narration; and never vanilla's `Tooltip`.
  - "Tooltip position for scrollable widgets" uses `TooltipLayer.scheduleAtPointer` and no longer
    mentions inline version branches.
  - The package table, the `DrawContextHelper` notes, the component table, the new interactive widget
    checklist and the review checklist.
  - The sentences that still call a tooltip "vanilla": the settings row's action, the icon picker's
    clear button, remote identity tooltips and the form's tooltip helper.
- `CHANGELOG.md`, 4.0.0, "Client settings and themes": tooltips in the mod's screens follow the colour
  theme.
- `AGENTS.md` doesn't change: there is no new swap or replacement.

## Known limits

- A widget class that doesn't make the two calls ignores `setTooltip`, without an error. Toggles,
  sliders, text fields and the swatch widget are such classes today. Before giving one a tooltip, add
  the calls to its class. The Javadoc and both guide checklists say so.
- `TooltipLayer` is static frame state. It is safe because the GUI renders on one thread and each
  frame of the mod's screens clears it first.
- The two dark themes and Classic draw no tooltip outline, because their default border is
  transparent. Where a tooltip covers a popup of the same color, only its text and the popup's edge
  separate them. This follows the choice to reuse the popup roles.
