# Theme Editor Design

Status: implemented; design agreed on 2026-10-09. Verification is recorded in
[the 2026-10-10 validation record](../validation/2026-10-10-theme-editor.md).

Implementation errata (the approved design below is retained):

- Key-list values appear from a 205-pixel left column, rather than 207: `TreeViewWidget` reserves
  3 pixels for its scrollbar without the settings list's additional 2-pixel gap. The three-size table
  below still holds.
- `PreviewSample` declarations follow the widgets' actual drawing, including reachable hover, focus,
  popup and toggle states. Panel markers additionally respect overflow and value-column visibility;
  the key list includes its focus ring and excludes its per-key color chips.

## Intent

`WidgetThemeConfigScreen` edits the runtime color theme. Players reach it from the Appearance section of
the client settings. It was designed in July 2026, before the client settings screen, the waypoint form
and the waypoint manager were redesigned, and it doesn't follow their style:

- The panel is a fixed 408 pixels wide. It is 30 pixels wider than the 378-pixel GUI of a Retina MacBook
  and 88 pixels wider than vanilla's 320-pixel minimum, so both of its sides are cut off.
- The title is centered inside one big panel, and the footer is three fixed 60-pixel buttons centered
  inside it. Status messages show at 0.7× inside the gallery. The other screens put a left-aligned title
  above the panel and a footer below it, with the status on the left and fitted buttons on the right.
- The variable list, the editor box and the gallery each paint `PANEL_BACKGROUND` over the outer panel's,
  so they are darker than the frame. The waypoint manager paints each panel's fill once, so its panels
  look the same at any theme translucence.
- The layout is recalculated every frame. A hand-written list enables and disables the controls, and
  nothing reports the color picker as a modal, so a resize can take focus out of it.
- The editor shows a role's translated name next to its JSON key, but nothing says where a key is used,
  and the value of a key other than the selected one can't be seen.
- Reset previews the default theme. In the Edit waypoint form, Reset undoes the changes, and it is active
  only when something changed. Save is always active, although `WidgetThemeEditorSession.isDirty()`
  exists.
- Editing a role while a built-in theme is selected copies that theme into Custom and silently replaces
  the saved Custom colors.
- The gallery has no sample for the scrollbar thumbs, the disabled slider thumb, the Off toggle, tooltips,
  comboboxes or dropdowns.
- 54 of the editor's keys exist only in `en_us` and `zh_cn`, and the title is in Title Case.

The redesigned screen is a developer tool for players with a technical background. It works with the
theme's raw keys and `#AARRGGBB` values, as `widget-theme.json` stores them, and shows where each key is
used in a live preview of the mod's widgets. This document is the reference for the implementation plan.
It builds on the [themed tooltips](../../gui-tooltips/README.md) change, committed as `9a264046`.

## Scope

In scope:

1. Layout and sizes.
2. The key list.
3. The key editor.
4. The preview, its markers, and the widget changes it needs.
5. The footer, editing and status messages.
6. Input, focus and lifecycle.
7. Code structure.
8. Text and translations.
9. Documentation.

Out of scope:

- New theme keys, changes to the built-in palettes, and changes to the `widget-theme.json` format.
- Translated names or descriptions for theme keys. The screen shows the raw keys.
- A separate switch for the preview's markers. Clearing the key selection hides them (section 2).
- Changes to `SwatchWidget`, and tooltips for text fields and sliders, which the themed tooltips leave
  out.
- Searching or filtering the key list.
- An undo history. Reset goes back to the state when the editor opened.
- Narration beyond what the widgets already provide.

## 1. Layout

```text
Color theme                                            [Theme: Custom            ▾]
┌─────────────────────────────────────┐ ┌─────────────────────────────────────────┐
│ ■ text.primary          #FFE8E8E8  █│ │ Preview ─────────────────────────────── │
│ ■ text.muted            #FFC4C4C4  █│ │ Primary  Muted  Disabled                │
│ ■ text.disabled         #FF999999  █│ │ [Editable text          ]               │
│ ■ text.placeholder      #FFBDBDBD  █│ │ [minecraft:overworld ▾]  [Choice 1 ▾]   │
│ ■ text.onAccent         #FFF5F5F5  █│ │ [Button]  [Disabled]                    │
│ ■ background.screen     #A60F0F0F  ▒│ │ [Selected]  [On]  [Off]                 │
│ ■ background.panel      #B31C1C1C  ▒│ │ [==|----][65]  [==|----][40]            │
│ ■ background.popup      #FA202020  ▒│ │ [Accent]  [Hovered]   (Tooltip)         │
│ ■ background.dialog     #FC181818  ▒│ │ [Popup]  [Dialog]                       │
│ ■ control.background    #D9262626  ▒│ │ [Success]  [Warning]  [Danger]   ┃┃┃    │
└─────────────────────────────────────┘ │                                         │
┌─────────────────────────────────────┐ │                                         │
│ control.background ──────────────── │ │                                         │
│ ■ #[D9262626]                     ↺ │ │                                         │
│ Alpha [=========|-------] [ 217 ]   │ │                                         │
└─────────────────────────────────────┘ └─────────────────────────────────────────┘
(status)                                                    [Reset] [Cancel] [Save]
```

The mockups agreed during design are kept under `.superpowers/brainstorm/` in the workspace, which is not
committed. `59501-1791577993/content/layout-c-v2.html` draws this layout at 480×270, 378×245 and
320×240 with the game's font and the Translucent Dark colors, with a key selected and its markers, and
with no key selected.

### Structure

The header line, the panels and the footer form one group, centered on screen.

- **Header line:** the title "Color theme", a `ScalableText` at 1.2× scale in `TEXT_PRIMARY`, on the
  left; the theme dropdown on the right, as wide as its widest choice ("Theme: Translucent Dark"). When
  the dropdown leaves the title too little room, the title is cut and ends with "…".
- **Panels:** three panels, 2 pixels apart, with no outer panel. Each panel's fill is painted once, by
  the widget that owns it:
  - The key list at the top of the left column (section 2). It paints its own panel.
  - The key editor at the bottom of the left column, 53 pixels high (section 3). The screen paints its
    panel.
  - The preview, which takes the whole right column (section 4). `SettingsListWidget` paints its panel.
- **Footer:** as wide as the group. The status sits on the left, and Reset, Cancel and Save on the
  right, placed from the right with a `WidgetPack`. It follows the client settings footer: the status
  wraps to the width left of the buttons minus 8 pixels, and when that leaves it less than 100 pixels it
  takes its own full-width line above the buttons (`ClientConfigScreen.statusAboveButtons`).

### Sizes

| Item | Value |
| --- | --- |
| Screen margin | 10 px |
| Header line | 11 px: the title at 1.2× and the dropdown, `font.lineHeight + 2` |
| Title to dropdown | At least 8 px |
| Header to panels, and panels to footer | 6 px |
| Between panels | 2 px, as in the waypoint manager |
| Panel padding | 6 px |
| Group width | `min(screen width − 20, 420)` |
| Left column width | `clamp(round(group width × 0.5), 152, 210)` |
| Panels height | `min(space left after the margins, header, footer and gaps, 230)` |
| Key editor height | 53 px: padding, the 9-px key line, 5 px, two 11-px rows 5 px apart, padding |
| Footer buttons | `TranslucentButton.fitted`: `max(50, text width + 10)` wide, 6 px apart |
| Status | 8 px from the buttons; 4 px above them when on its own line |

At the three sizes checked during design, with a one-line status:

| Screen | Group | Left column | Preview | Panels height | Keys in view | Values | Preview in view |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 480×270 | 420 | 210 | 208 | 216 | 12 | Shown | All of it |
| 378×245 | 358 | 179 | 177 | 191 | 10 | Hidden | About 77% |
| 320×240 | 300 | 152 | 146 | 186 | 9 | Hidden | About 70% |

The key list keeps at least four rows and the preview at least 40 pixels, its header and one row. When
the screen is too short for that, the group starts at the top margin.

### Layout lifecycle

- Layout runs in `init()`, which a resize also runs, and when the status message changes, because a
  wrapped status changes the footer's height. It never runs while rendering.
- The color picker (`SwatchWidget`) stays centered on the group.

### Rendering

`MovementAllowedScreen` draws the themed background and the tooltip layer. The screen draws, in order:

1. The key list, the key editor's panel and content, and the preview with its markers.
2. The title, the theme dropdown, the status and the footer buttons.
3. The popups of the theme dropdown and of the preview's combobox and dropdown, rendered separately after
   everything else.
4. The color picker, between `nextLayer` and `previousLayer`.

While the color picker is open, everything else is drawn with `DrawContextHelper.NO_MOUSE`, so nothing
under it shows a hover state or a tooltip. While the pointer is over an open popup, the content under it
is drawn with `NO_MOUSE` too, as the waypoint form does for its popups.

## 2. The key list

A screen-local `KeyList` extends `TreeViewWidget` with one root per theme key and no children.

- **Order:** `WidgetThemeVariable.values()`, the order `WidgetThemeJson` writes `widget-theme.json` in.
  There are no group headers; the keys' prefixes (`text.`, `background.`, `control.`, …) group them.
- **Panel:** the list paints its own panel: `PANEL_BACKGROUND` with a `BORDER` outline and 6 pixels of
  padding. Nothing else paints under it.
- **Rows:** 12 pixels high. Each row shows, 2 pixels from the left, an 8×8 chip of the key's color over a
  2-pixel checkerboard, so translucent keys show their opacity; 4 pixels; the raw key
  (`getJsonName()`) in `TEXT_PRIMARY` at 85%; and, right-aligned, the value as `#AARRGGBB` in
  `TEXT_MUTED` at 85%.
- **Values:** shown when the row is wide enough for the widest key, 8 pixels and a value, which needs a
  left column of 207 pixels. Narrower lists show only the keys; the key editor still shows the selected
  key's value. At the 152-pixel minimum, every key fits without being cut.
- **Selection:** the selected key gets a `SELECTION_BACKGROUND` fill, and the key under the pointer a
  `ROW_HOVER_BACKGROUND` fill. The editor starts on `text.primary`, as today. Clicking a key selects it;
  clicking the selected key again clears the selection, so the preview can be judged without markers.
  With no key selected, the preview marks nothing and the key editor shows its empty state (section 3).
- **No tooltip:** rows have none.
- **Keyboard:** the list is a Tab stop. While it has focus, Up and Down select the previous and next key
  and stop at the first and last key; with no key selected, Down selects the first key and Up the last.
  The list scrolls by the least amount that shows the selected row.
- **Scrolling:** the wheel scrolls the list while the pointer is over it, as `TreeViewWidget` does.

## 3. The key editor

The key editor shows the selected key under the key list: a `PANEL_BACKGROUND` fill with a `BORDER`
outline and 6 pixels of padding, painted once by the screen.

- **Key line:** the selected key in `TEXT_PRIMARY` at full size, followed by a `DECOR_LINE` line to the
  right edge, like a section header.
- **Color row,** 5 pixels below:
  - The `ColorSquareButton`, which shows the key's RGB, opens the color picker and has the tooltip "Open
    color picker".
  - 4 pixels, then a `ColorHexCodeField` in its new ARGB mode (section 4) showing the value as
    `#AARRGGBB`.
  - At the right end, the reset icon.
- **Alpha row,** 5 pixels below: the label "Alpha" in `TEXT_PRIMARY` at 85%, then an `IntegerSlider`
  from 0 to 255 with `controlScale` 0.85, a 64-pixel track and a 30-pixel number field. Like the settings
  rows' sliders, the track has a fixed width, here one that fits every locale's label in the 152-pixel
  column; resizing a track would mean changing `AbstractColorBgSlider`, which the color pickers share.
- **Reset icon:** a 9×9 `IconButton` with `WidgetTextures.RESET_ICON`, no background, a 2-pixel icon
  inset and the settings screen's icon region. It shows only while the selected key's value differs from
  its value when the editor opened; its column stays reserved, so nothing moves when it appears. Its
  tooltip is "Undo changes to this key". It sets the key back to that value, which counts as an edit
  (section 5).
- **What each control changes:** the color picker changes the RGB and keeps the alpha, as today; the
  slider changes the alpha and keeps the RGB; the ARGB field changes both.
- **Empty state:** with no key selected, the key line reads "No key selected" in `TEXT_MUTED`, followed by
  the line, and below it "Click a key to edit it. Click it again to hide the markers." in `TEXT_MUTED`
  at 85%, wrapped. The color button, the ARGB field, the reset icon and the slider are hidden, so they
  take no clicks and aren't Tab stops. The panel keeps its height, so nothing else moves.

## 4. The preview

The preview is a `SettingsListWidget`, which scrolls real widgets and hides clipped ones from clicks and
Tab. It holds a `Header` titled "Preview" and then rows of samples, at full size.

### Samples

The samples come in ten families. Each family is a horizontal `WidgetStack` with 6 pixels between its
samples:

| Family | Samples | Keys |
| --- | --- | --- |
| Text | "Primary", "Muted" and "Disabled", `ScalableText`s | `text.primary`, `text.muted`, `text.disabled` |
| Field | A 104-pixel `TranslucentTextField` showing the placeholder "Editable text" | `control.background`, `text.placeholder`, `text.primary`, `border.default`, `border.focusRing` |
| Choices | A 112-pixel `ComboBoxWidget` holding `minecraft:overworld`, with the three vanilla dimensions as choices; a 72-pixel dropdown with "Choice 1" to "Choice 3" | Combobox: `control.background`, `text.primary`, `border.default`, `border.focusRing`, `background.popup`, `row.hoverBackground`. Dropdown: `background.popup`, `text.primary`, `border.default`, `row.hoverBackground` |
| Buttons | "Button" and an inactive "Disabled", `TranslucentButton.fitted` | Button: `control.background`, `control.hoverBackground`, `text.primary`, `border.default`, `border.focusRing`. Disabled: `control.disabledBackground`, `text.disabled` |
| Toggles | A 64-pixel `ToggleButton` "Normal"/"Selected" that starts selected, and 30-pixel `OnOffToggleButton`s showing On and Off | Selected: `control.selectedBackground`, `text.onAccent`. On: `status.successBackground`, `text.onAccent`. Off: `status.dangerBackground`, `text.onAccent` |
| Sliders | Two `IntegerSlider`s with 60-pixel tracks and 26-pixel fields, the second inactive | First: `accent.default`, `control.background`. Second: `slider.thumbDisabled`, `control.disabledBackground`, `text.disabled` |
| Accent | "Accent" and "Hovered" chips | `accent.default` or `accent.hover`, and `text.onAccent` |
| Popups | A `TranslucentTooltip` reading "Tooltip", and "Popup" and "Dialog" chips | Tooltip: `background.popup`, `border.default`, `text.primary`. Chips: `background.popup` or `background.dialog` |
| Status | "Success", "Warning" and "Danger" chips, each in its text key on its background key | The six `status.` keys |
| Scrollbars | Three 3×20 scrollbars: at rest, being dragged and inactive | `scrollbar.track`, and `scrollbar.thumb`, `scrollbar.thumbActive` or `scrollbar.thumbDisabled` |

- The real widgets react to the pointer, take focus and show the focus ring. Their callbacks do nothing,
  except that the combobox and dropdown keep the value chosen. The inactive samples stay inactive.
- Chips and scrollbar samples are a screen-local, non-interactive `ShiftableWidget` that resolves its keys
  every time it is drawn. The tooltip sample is a real `TranslucentTooltip`, drawn in place rather than
  through `TooltipLayer`.
- Nothing in the preview has a row tooltip or a row hover fill.

### Packing

A pure `PreviewPacking` function puts the families into rows, in order, with 12 pixels between families
that share a row. A family stays on one row when it fits; a family wider than the row wraps its samples
onto as many rows as it needs, so nothing is cut off. The screen packs again and calls `setEntries` when
the preview's width changes, which keeps the widgets and their state.

### Markers

The preview marks where the selected key is used: every sample and panel that draws the key gets a solid
1-pixel outline 2 pixels outside it, drawn with `DrawContextHelper.renderOutline`. With no key selected,
nothing is marked.

- The marker color is a fixed magenta, `#FFFF4FD8`, that doesn't follow the theme, so a marker stays
  visible whatever the theme looks like.
- The samples' keys are the ones in the table above. The panels and the screen add these:

  | Element | Keys |
  | --- | --- |
  | The key list | `background.panel`, `border.default`, `text.primary`, `text.muted`, `selection.background`, `row.hoverBackground`, `scrollbar.track`, `scrollbar.thumb`, `scrollbar.thumbActive` |
  | The key editor | `background.panel`, `border.default`, `decor.line`, `text.primary` |
  | The preview | `background.panel`, `border.default`, `decor.line`, `text.primary` |
  | The screen | `background.screen`, marked 2 pixels inside the screen's edge |

- Every theme key is used by at least one sample or element; a test checks it.
- A marked sample that is scrolled out of view has no marker. A partly visible one is clipped with it.

### Popups in the preview

`SettingsListWidget` rows can't draw popups themselves, because the list's scissor would clip them. The
preview's combobox and dropdown draw theirs separately, after the screen's content, as the waypoint form
does:

- The screen calls `setRenderPopupSeparately(true)` on both, `ComboBoxWidget.layoutPopup(screenHeight,
  6)` after positioning the combobox, and `renderPopup(...)` on both after the footer.
- A click on an open popup is routed to it before any other widget. A click elsewhere closes it and
  continues to its target.
- The wheel over an open popup scrolls the popup (`MovementAllowedScreen`'s `PopupOwner` handling). The
  wheel elsewhere over the preview closes the popup, then scrolls the preview.
- Opening the color picker closes the popup. So does losing focus, as it does for every dropdown.

### `SettingsListWidget.WideRow`

`SettingsListWidget` gains a third entry type, a row without a label column:

```java
public static final class WideRow extends Entry {
    public <C extends LayoutElement & Renderable> WideRow(C control);
}
```

- The control starts at the row's left edge and can use the whole row width. The row is as tall as the
  control plus 3 pixels above and below, and at least 17 pixels (`SettingsListLayout`).
- It has no hover fill and no tooltip.
- Otherwise it follows the `Row` rules: it owns every widget its control's `visitWidgets` reports, its
  widgets are visible only while they are entirely in view, a partly visible control is drawn clipped
  with the mouse outside the screen, it is a Tab stop while any of its widgets is active, and `reveal`
  and `revealTabTarget` treat it like a `Row`.
- `getPreferredWidth()` counts its control's width.
- The GUI guide's "Row controls can't open popups" limitation gains the exception above: a row control can
  open a popup when the screen renders it separately and closes it when the list scrolls.

### `ColorHexCodeField` ARGB mode

`ColorHexCodeField` is RGB-only today: six digits and the placeholder `RRGGBB`. It gains an opt-in ARGB
mode; the waypoint form and the color picker keep the RGB mode.

- Up to eight digits and the placeholder `AARRGGBB`. `setColor(argb)` shows all eight digits, and
  `getColor()` returns ARGB.
- While typing, the value applies when it has eight digits, so typing an eight-digit value never flashes
  the color its first six digits would make.
- On Enter or when the field loses focus, a six-digit value applies as RGB and keeps the current alpha.
  Any other incomplete value is replaced by the current value, as the RGB mode completes short values
  today.

## 5. Footer, editing and status

### Footer buttons

- **Reset** is active while the session differs from when the editor opened: the selected theme or the
  Custom colors. It puts both back, keeps the editor open and clears the status. Its tooltip is "Undo
  every change since this screen opened."
- **Cancel** puts everything back and closes the screen, without asking, as the waypoint form does.
- **Save** is active under the same condition as Reset. It writes the selected theme and the Custom
  colors to `widget-theme.json`, as today, and closes the screen. If writing fails, the screen stays
  open, the failure is logged, and the status shows "Couldn't save the theme. Your changes are still
  here." in `DANGER`.
- Escape, and any other way of leaving the screen without saving, puts everything back, as today.

### Editing

- Changing the ARGB field, the color picker or the slider updates the draft theme and previews it at
  once: on this screen, in the key list's chips and values, in the preview and in every tooltip.
- Choosing a theme in the dropdown previews it and keeps the Custom colors. Editing a key while a
  built-in theme is selected copies that theme into Custom with the edit, and the dropdown shows Custom,
  as today.
- When that copy replaces Custom colors that differ from the built-in theme, the status shows "Custom
  now starts from *theme*." in `TEXT_MUTED`. Reset or Cancel bring the old colors back until Save.
- The theme dropdown's tooltip is "Pick a built-in theme or your Custom colors. Editing a built-in
  theme turns it into Custom."

### Status

| Event | Status |
| --- | --- |
| An edit replaces Custom colors that differ | "Custom now starts from *theme*." in `TEXT_MUTED` |
| Saving fails | "Couldn't save the theme. Your changes are still here." in `DANGER` |
| Reset, or a choice in the theme dropdown | Cleared |

A new message replaces the previous one. Editing doesn't clear it.

## 6. Input, focus and lifecycle

### Control states

`refreshControlStates()` sets every control's `active` flag from the current state and replaces
`setEditorControlsActive`:

| Control | Active |
| --- | --- |
| Theme dropdown, key list, Cancel, the preview's samples | While the color picker is closed |
| Color button, ARGB field, alpha slider, reset icon | While the color picker is closed. They are shown only while a key is selected, and the reset icon only while that key changed |
| Reset, Save | While the color picker is closed and the session differs from when the editor opened |
| The preview's Disabled button and second slider | Never |

It runs after every change that affects them: an edit, a theme choice, Reset, selecting a key or
clearing the selection, opening and closing the color picker, and a failed save. The color picker can
only be opened while a key is selected, because its button is hidden otherwise.

### Color picker

- Opening it closes every popup, makes every other control inactive and focuses the picker.
- `hasOpenModal()` reports it, so a resize after keyboard use keeps focus in the picker.
- Escape, or confirming a color, closes it and returns focus to the color button.
- Focus is normalized after a click, as today, because vanilla focuses the clicked widget after its
  callback runs.

### Keyboard and mouse

- **Tab order** follows the screen: the theme dropdown, the key list, then, while a key is selected, the
  color button, the ARGB field, the reset icon when shown and the alpha slider, then the preview's samples
  row by row, then Reset, Cancel and Save.
- **Escape** closes an open popup, then leaves text entry (`dismissFocusedInput()`), then closes the
  color picker, then cancels and closes the screen.
- **Up and Down** move the selection while the key list has focus (section 2).
- **The mouse wheel** goes to an open popup under the pointer first, as `MovementAllowedScreen` does,
  then to the preview while it overflows and the pointer is over it, as in `ClientConfigScreen`, and
  otherwise to the widget under the pointer.
- Movement keys stay off while the screen is open, as today.

### Lifecycle

- **Constructor:** the session, every widget, the key list's entries and the preview's families, so they
  survive resizes.
- **`init()`:** lays the group out, positions the widgets, packs the preview again when its width
  changed, registers the widgets in Tab order (the preview's through `visitWidgets`), refreshes the
  control states, and focuses the color picker when it is open.
- **`removed()`:** puts everything back unless the session was saved. It stays idempotent.
- Pausing doesn't change: the screen pauses singleplayer exactly when the screen that opened it does.

## 7. Code structure

### `WidgetThemeConfigScreen`

The screen is rebuilt around the parts above. Screen-local classes: `KeyList`, the key editor's
composition, the preview's sample element and its sample dropdown, the marker drawing, and the existing
theme dropdown, which moves into the header line and gets its tooltip. `WidgetThemeConfigScreenLayoutTest`
and the current geometry records go away.

### New helpers

Package-private classes in `screens`, used only by this screen and free of Minecraft text classes, so they
can be unit tested:

- **`WidgetThemeEditorLayout`:** from the screen size, the title and dropdown widths, the footer's
  buttons, and the status height as a function of its width, it calculates the group, the header line,
  the three panels, the button row and the status, following section 1. It also decides whether the key
  list shows values, from the list's width and the widths of the widest key and of a value.
- **`PreviewPacking`:** packs the families' sample widths into rows, following section 4.
- **`KeySelection`:** the key list's selection rules from section 2: a click selects a key or, on the
  selected key, clears the selection, and Up and Down move it, starting from the first or last key when
  nothing is selected.
- **`PreviewSample`:** an enum of the preview's samples and of the elements that carry markers (the key
  list, the key editor, the preview and the screen), each with its family and its theme keys. The screen
  builds a widget for each sample and draws the markers from it.

### `WidgetThemeEditorSession`

```java
final class WidgetThemeEditorSession {
    WidgetTheme getDraftTheme();
    WidgetThemeSelection getSelection();
    void select(WidgetThemeSelection selection);
    Optional<WidgetThemeSelection> setColor(WidgetThemeVariable variable, int color);
    Optional<WidgetThemeSelection> revert(WidgetThemeVariable variable);
    void revertAll();
    boolean isChanged(WidgetThemeVariable variable);
    boolean isDirty();
    void save() throws IOException;
    void cancel();
}
```

- `setColor` returns the built-in theme that was selected before the edit when the Custom colors it
  replaces differ from that theme in any key. It is empty when Custom was already selected, and when
  the replaced Custom colors equal the built-in theme. Setting the color the draft already has changes
  nothing, so committing an unchanged value never turns a built-in theme into Custom.
- `revert` sets the key to its color in the theme in effect when the editor opened, through `setColor`,
  and returns what `setColor` returns, so the screen shows the same status.
- `revertAll` restores the selection and Custom colors from when the editor opened, and previews them.
- `isChanged` compares the key's color in the draft with the theme in effect when the editor opened.
- `reset()`, which selected Translucent Dark, goes away.

### Widgets

- `SettingsListWidget.WideRow` (section 4). `Entry` permits it, and `SettingsListLayout` gets its
  minimum height and padding.
- `ColorHexCodeField`'s ARGB mode (section 4).
- No other widget changes. The theme keys, `WidgetThemes` and `WidgetThemeJson` don't change.

## 8. Text and translations

The theme keys are shown raw, so they have no translations. The rest of the screen's text exists in all
six locales: `en_us`, `es_es`, `he_il`, `zh_cn`, `zh_hk` and `zh_tw`. The tables give English and
Simplified Chinese; the implementation plan carries the other four locales. All non-English strings are
drafts that need a native speaker's review before release. Text uses sentence case. The built-in themes'
names stay names, such as "Translucent Dark". Keys are relative to `server_waypoint.theme.`.

- The 20 editor keys that stay, which exist only in `en_us` and `zh_cn` today, are added to the other four
  locales: `screen.title`, `variables`, `color_picker`, `save`, `save.failed` and the 15 `preview.` keys.
- Reused: Cancel is `server_waypoint.cancel.button`, Reset is `waypoint.reset.button`, and the On and Off
  toggles use `server_waypoint.config.on` and `server_waypoint.config.off`. All four exist in every
  locale. The theme dropdown keeps `selector`, `selector.value` and the `preset.` keys, which exist in
  every locale.
- The combobox sample's choices are the raw IDs `minecraft:overworld`, `minecraft:the_nether` and
  `minecraft:the_end`.

### New keys

| Key | English | Chinese |
| --- | --- | --- |
| `alpha` | Alpha | Alpha 值 |
| `no_key` | No key selected | 未选择键 |
| `no_key.hint` | Click a key to edit it. Click it again to hide the markers. | 点击一个键进行编辑。再次点击可隐藏标记。 |
| `reset_key` | Undo changes to this key | 撤销对此键的更改 |
| `reset.tooltip` | Undo every change since this screen opened. | 撤销打开此界面以来的所有更改。 |
| `selector.tooltip` | Pick a built-in theme or your Custom colors. Editing a built-in theme turns it into Custom. | 选择内置主题或你的自定义颜色。编辑内置主题会将其变为自定义。 |
| `custom_replaced` | Custom now starts from %s. | 自定义颜色现在基于%s。 |
| `preview.tooltip` | Tooltip | 提示框 |
| `preview.choice` | Choice %s | 选项 %s |

### Kept keys with new text

| Key | English | Chinese |
| --- | --- | --- |
| `screen.title` | Color theme | 颜色主题 |
| `variables` | Theme keys | 主题键 |
| `save.failed` | Couldn't save the theme. Your changes are still here. | 无法保存主题。你的更改仍然保留。 |
| `preview.title` | Preview | 预览 |
| `preview.primary` | Primary | 主要 |
| `preview.muted` | Muted | 次要 |
| `preview.hover` | Hovered | 悬停 |

`variables` is the key list's narration label.

### Removed keys

Removed from every locale that has them:

- The 31 `variable.<json name>` keys. The screen shows the raw key.
- `rgb` and `opacity`, replaced by the ARGB field and `alpha`.
- `reset.preview`, because Reset no longer previews the default theme.

## 9. Documentation

- **This feature folder:** a README index, this spec, and `plans/` and `validation/` folders that hold a
  `.gitkeep` until they have documents. The folder is listed in `docs/features/README.md`.
- **GUI guide** (`tips/gui/local-guide.md`), updated in the same change as the code, as `AGENTS.md`
  requires:
  - The "In-game theme editor" section describes this design: the layout, the key list, the key editor,
    the preview and its markers, Reset and Save, and the session's methods.
  - The `screens` example bullet for `WidgetThemeConfigScreen`.
  - "Settings lists" documents `WideRow` and the popup exception, and the component table mentions
    `WideRow` and `ColorHexCodeField`'s ARGB mode.
  - "Runtime color themes": adding a theme key no longer needs translations, but needs a `PreviewSample`
    or marked element that uses it. The guide's "derive the matching translation key" sentence goes.
- **`CHANGELOG.md`**, 4.0.0, "Client settings and themes": the redesigned theme editor.

## Constraints

- Java 17 and four-space indentation. No build script changes are expected.
- Every Stonecutter target from 1.20.1 to 26.3 must keep working. The screen keeps its existing
  `mouseClicked` and `mouseScrolled` version branches. No new swap or replacement is planned; if one
  becomes necessary, update the inventory in `AGENTS.md` in the same change.
- No backward-compatibility code, per `AGENTS.md`. Removed translation keys need no migration, and the
  session's `reset()` is removed outright.
- The themed tooltips are committed (`9a264046`), but their in-game checks haven't run. This feature's
  in-game pass covers the theme editor's part of them.
- Commit only when asked, per `AGENTS.md`.

## Validation

### Unit tests

In the mods test source set:

- `WidgetThemeEditorLayoutTest`, replacing `WidgetThemeConfigScreenLayoutTest`: the group and column width
  rules; the 2-pixel panel gaps and 6-pixel section gaps; everything inside the screen at 480×270,
  378×245 and 320×240; the key editor's 53 pixels; the status beside or above the buttons; a cut title
  when the dropdown needs the room; values shown from a 207-pixel column and hidden below; the minimums
  and the group at the top margin when they can't fit.
- `PreviewPackingTest`: families stay whole and in order when they fit, a family wider than a row wraps
  onto the next rows, and rows respect the width.
- `PreviewSampleTest`: every `WidgetThemeVariable` is used by at least one sample or element, and every
  family has a sample.
- `ColorHexCodeFieldTest`: the ARGB mode applies eight digits, applies six digits as RGB with the current
  alpha on Enter and on focus loss, and restores other incomplete values; the RGB mode is unchanged.
- `WidgetThemeEditorSessionTest`: `revertAll` restores the selection and the Custom colors; `revert`
  restores one key; `isChanged` for a changed and an unchanged key; `setColor` reports the built-in theme
  only when the Custom colors it replaces differ; `isDirty` after an edit and after `revertAll`.
- `WidgetThemeTranslationTest`: every key the screen uses exists in all six locales with the same
  placeholders as `en_us`, and no locale has a `variable.` key or the other removed keys.
- `SettingsListLayoutTest` and `SettingsListWidgetTest`: a `WideRow`'s height and width, that it owns
  its control's widgets, hides clipped ones and is a Tab stop while one is active, and that it gets no
  hover fill.
- `KeySelectionTest`: a click selects a key, a click on the selected key clears the selection, Up and Down
  stop at the ends, and with nothing selected Down selects the first key and Up the last.

### Gradle

- `:mods:26.1.2-fabric:test`, the active Stonecutter target, and `:mods:1.20.1-fabric:test`, which runs
  Java 17 and the tooltip path before 1.21.6.
- `compileJava` for every `mods` target, because the screen keeps its input version branches.
- `git diff --check`, and balanced Stonecutter markers in every touched file.

### In game

A manual pass on 26.1.2 Fabric and 1.20.1 Fabric, with results recorded in `validation/`. Compiling
can't prove these:

- The layout at 480×270, 378×245 and 320×240, including a status that wraps, the value column at 480
  and its absence below, and in all four built-in themes.
- Editing keys through the ARGB field, the color picker and the slider, and seeing the change on the
  screen, in the key list, in the preview and in tooltips at once.
- The markers for a sample key, a panel key and `background.screen`, and none for a scrolled-out sample.
- Clicking the selected key: the markers go, the key editor shows its empty state, and Tab skips its
  hidden controls. Clicking a key again brings them back.
- The combobox and dropdown popups: they draw over everything, take their clicks first, scroll with the
  wheel, and close when the preview scrolls or the color picker opens.
- The color picker: other controls inactive, no hover or tooltips through it, Escape, and a resize with
  it open after keyboard use.
- Reset, Cancel and Save; a failed save, for example with a read-only `widget-theme.json`; and the
  Custom-replaced message.
- The control tooltips, and no tooltips on key rows or in the preview.
- Tab order, Up and Down in the key list, and the mouse wheel over the list and the preview.
