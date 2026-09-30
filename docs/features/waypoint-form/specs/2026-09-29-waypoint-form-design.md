# Waypoint Form Design

Status: design agreed on 2026-09-29. Implemented; see the [validation record](../validation/2026-09-29-waypoint-form-validation.md).

## Intent

`WaypointAddScreen` and `WaypointEditScreen` share `AbstractWaypointPropertiesScreen`, a form that
predates the redesigned manager and client settings screens. It was never designed as a whole:

- Every row is `Label: [field]`, with the gap built into the translation (`"Identifier: "`), so each
  field starts at a different x. The panel is a flat fill without an outline, the title is the size of
  the labels, and labels use Title Case with colons.
- Add stacks its Dimension and List inputs under the title as if they were headings. Edit shows them
  in a small muted line, and its list name is always blank: the base constructor calls
  `createTitleRow()` before `WaypointEditScreen` has assigned `listDisplayName`.
- Edit puts a field action, "Use identifier" / "Keep override", among Update, Reset and Cancel. Its
  Display name field shows the name when there is no override, so players can't tell whether one
  exists.
- Tab moves through the fields in a different order from what's on screen: Edit visits Display name
  before Identifier, and Reset before "Use identifier".
- Add sends `/wp add` and stays open, with no sign of success, even when the name or list is empty.
  Edit reports its errors only as hover tooltips on a field or the Update button. The server's
  `UPLOAD_BUSY` result has no translation at all.
- Typing a name overwrites the initials on every keystroke, including on Edit.
- Keywords and descriptions are part of every waypoint, show in the manager's details panel and can
  be set by `/wp add` and the edit request, but neither screen edits them.
- The layout is recalculated every frame, opening the color picker disables controls from
  hand-written lists, and offsets such as 13, 4 and 5 are unexplained.

This document describes both screens as they should look and behave, and the code changes that
support them. It is the reference for the implementation plan.

## Scope

In scope:

1. Layout and spacing.
2. The Add screen.
3. The Edit screen.
4. Checks, submitting and feedback.
5. Keyboard, focus and tooltips.
6. Code structure.
7. Text and translations.
8. Documentation.

Out of scope:

- Moving a waypoint to another list or dimension from Edit.
- A display name on Add. `/wp add` has no display-name argument.
- Creating an empty display-name override, or an empty list or waypoint identifier, from the form.
  Commands keep both abilities.
- A correlated add request and result message.
- A preview of formatted display names and descriptions.
- Selecting the new waypoint in the manager after Add.
- Changes to `SwatchWidget`, coordinate parsing and suggestions, dropdown popups, `/wp add` or the
  edit request and result messages.
- Narration beyond the labels already passed to the widgets.

## 1. Layout

```text
Add waypoint
┌────────────────────────────────────────────────────────────┐
│ Dimension    [minecraft:overworld                       ▾] │
│ List         [Base                                       ] │
│ ────────────────────────────────────────────────────────── │
│ Name         [Home                  ]        Initials [H ] │
│ Icon         ◆ [minecraft:diamond                   ▾] [✕] │
│ Color        ■ #[FFAA00]               Visibility [Global] │
│ Position     X [120 ]  Y [64  ]  Z [-35 ]        Yaw [0  ] │
│ ────────────────────────────────────────────────────────── │
│ Keywords     [home, base                                 ] │
│ Description  [Optional                                   ] │
└────────────────────────────────────────────────────────────┘
(status)                                        [Cancel] [Add]

Edit waypoint: Home Base
In Base · minecraft:overworld
┌────────────────────────────────────────────────────────────┐
│ Name         [home                  ]        Initials [H ] │
│ Display name [Home Base                                  ] │
│ Icon         ◆ [minecraft:diamond                   ▾] [✕] │
│ Color        ■ #[55AAFF]               Visibility [Global] │
│ Position     X [120 ]  Y [64  ]  Z [-35 ]        Yaw [0  ] │
│ ────────────────────────────────────────────────────────── │
│ Keywords     [home, base                                 ] │
│ Description  [Beds upstairs, farm to the east            ] │
└────────────────────────────────────────────────────────────┘
(status)                               [Reset] [Cancel] [Save]
```

The mockups agreed during design are kept under `.superpowers/brainstorm/` in the workspace, which
is not committed.

### Structure

The header, the panel and the footer form one group, centered on screen.

- **Header:** the title, a `ScalableText` at 1.2× scale in `TEXT_PRIMARY`. Edit adds a subtitle in
  `TEXT_MUTED` 2 pixels below it (section 3). Each is a single line as wide as the panel; text that
  doesn't fit is cut and ends with "…".
- **Panel:** a `PANEL_BACKGROUND` fill with a 1-pixel `BORDER` outline and 7 pixels of padding inside
  the outline. It holds the rows.
- **Rows:** a label column and a control column. Labels are `ScalableText` in `TEXT_PRIMARY`,
  vertically centered on their row. Each row is as tall as its tallest control, and its controls are
  centered vertically.
- **Dividers:** a 1-pixel `SeparatorWidget` in `BORDER` across the row width, like the manager's
  sidebar separators. Add has one after List and one before Keywords. Edit has one before Keywords.
- **Footer:** as wide as the panel. The status message sits on the left, and the buttons sit on the
  right, placed from the right with a `WidgetPack`. It follows the client settings footer: the status
  wraps to the width left of the buttons minus 8 pixels, and when that leaves it less than 100 pixels
  it takes its own full-width line above the buttons (`ClientConfigScreen.statusAboveButtons`). The
  footer is as tall as its taller side.

### Rows

| Row | Add | Edit | Controls, left to right |
| --- | --- | --- | --- |
| Dimension | Yes | | Dimension `ComboBoxWidget`, stretched to the column |
| List | Yes | | List `TranslucentTextField`, stretched |
| Name + Initials | Yes | Yes | Name field, stretched; at the right, the "Initials" label and a 26-pixel field |
| Display name | | Yes | Display name field, stretched |
| Icon | Yes | Yes | 11-pixel icon preview; icon dropdown, stretched; 13×13 remove-icon button |
| Color + Visibility | Yes | Yes | 9-pixel `ColorSquareButton` (11 with its outline); `ColorHexCodeField`; at the right, the "Visibility" label and a 48-pixel `ToggleButton` |
| Position + Yaw | Yes | Yes | Axis letter and coordinate field for X, Y and Z; at the right, the "Yaw" label and a 26-pixel `IntegerField` |
| Keywords | Yes | Yes | Keywords field, stretched |
| Description | Yes | Yes | Description field, stretched |

- Axis letters keep today's colors and their switch to R, U and F while the coordinates are local.
- The three coordinate fields share the space left in their row equally, between 30 and 48 pixels
  each. The space between the Z field and the Yaw label absorbs the rest, at least 10 pixels.
- The space between the left and right groups of the Name, Color and Position rows is at least
  10 pixels.

### Sizes

| Item | Value |
| --- | --- |
| Screen margin | 10 px |
| Gap between header and panel, and between panel and footer | 6 px |
| Title to subtitle | 2 px |
| Panel padding | 7 px inside the 1-px outline |
| Gap between rows | 9 px; less, down to 5 px, only when the group doesn't fit the screen height |
| Divider | 1 px, with the row gap minus 2 above and below it (7 px at a 9-px gap) |
| Label column | Widest label plus 10 px (see Widths) |
| Control column | 240 px when the screen allows |
| Gap between fields in one row | 10 px |
| Inline label to its field | 5 px; 4 px after an axis letter |
| Swatch to hex field; preview to dropdown to remove button | 4 px |
| Footer buttons | `TranslucentButton.fitted`: `max(50, text width + 10)` wide, 6 px apart |
| Status | 8 px from the buttons; 4 px above them when on its own line |

The widgets keep their heights: text fields, dropdowns, toggles and the color button are 11 pixels
tall with their outlines, `TranslucentButton` and `IconButton` controls are 13, the title is 11 and a
text line is 9.

### Widths

- The panel is `label column + 240 + 16` wide, at most the screen width minus the margins.
- The control column is never narrower than the Position row needs with 30-pixel coordinate fields.
  When that and the widest label don't both fit, the label column narrows and a longer label wraps
  onto a second line inside it; its row grows to fit.
- Stretched controls fill the control column after the fixed parts of their row.

### Heights

The gap between rows is the largest value from 9 down to 5 pixels at which the group fits inside the
screen margins. It is 9 at every size with a one-line status and one-line labels:

| Screen | Group | Space inside the margins |
| --- | --- | --- |
| Add at 320×240 | 11 + 6 + 181 + 6 + 13 = 217 | 220 |
| Edit at 320×240 | 22 + 6 + 155 + 6 + 13 = 202 | 220 |

A status that wraps onto more lines, which happens mainly at 320 pixels wide, lowers the gap. If
the group doesn't fit even at 5 pixels, it starts at the top margin.

### Layout lifecycle

- Layout runs in `init()` and whenever the status message changes, because a wrapped status changes
  the footer's height. It no longer runs every frame.
- The color picker (`SwatchWidget`) stays centered on the panel.

### Rendering

`MovementAllowedScreen` draws nothing itself, so the screen keeps drawing its widgets. The order
follows the [GUI guide](../../../tips/gui/local-guide.md):

1. The panel background and outline, the header, the labels and the dividers.
2. The row widgets and the icon preview.
3. The text-field suggestions, between `nextLayer` and `previousLayer`.
4. The dimension and icon dropdown popups.
5. The hover tooltip (section 5).
6. The color picker, between `nextLayer` and `previousLayer`.

## 2. The Add screen

- **Title:** "Add waypoint".
- **Footer:** Cancel and Add.
- **Starting values:**

  | Field | Value |
  | --- | --- |
  | Dimension | From the caller |
  | List | From the caller: the list row's list, or empty from the manager's + button and from Xaero's World Map |
  | Name | Empty |
  | Initials | Follow the name (section 5) |
  | Icon | None |
  | Color | Random, as today |
  | Position | The caller's position, or the camera entity's block position, as today |
  | Yaw | 0 |
  | Visibility | Global |
  | Keywords, Description | Empty |

- The dimension choices and their asynchronous refresh are unchanged. So are the suggestions: list
  names for the chosen dimension, waypoint names for the chosen list, initials candidates,
  coordinates and yaw.
- Add sends the same command as today, built by `StringCommandBuilder.addCmd`, now with the parsed
  keywords and the description. The command already carries both.

## 3. The Edit screen

- **Title:** "Edit waypoint: *display name*", with the saved display name as formatted text. When the
  saved display name is empty, the title shows the name instead.
- **Subtitle:** `waypoint.edit.screen.location`, "In *list* · *dimension*". The list is its display
  name as formatted text; the dimension is its name in the dimension color that the details panel
  uses (the dimension color scaled by 0.8). It replaces the Dimension and List rows, because Edit
  can't change either. `listDisplayName` is stored before anything reads it.
- **Footer:** Reset, Cancel and Save. "Update" becomes "Save".
- **Reset and Save** are active only when the form would change the waypoint, meaning its patch has
  a field to set or clear. Reset restores every field, removes the field highlights and clears the
  status.

### Display name

The field holds only the override: `SimpleWaypoint.displayNameOverride()`, not `displayName()`.

| Saved | Field shows |
| --- | --- |
| No override | Empty, with the Name field's current text as its placeholder |
| An override | The override |
| An empty override | Empty, with the placeholder "Empty: the marker shows no name" |

The display-name part of the patch:

| Saved | Field on Save | Patch |
| --- | --- | --- |
| No override | Empty | Unchanged |
| No override | Text | Set to the text |
| Override *X* | *X* | Unchanged |
| Override *X* | Empty | Clear |
| Override *X* | Other text | Set to the text |
| Empty override | Empty | Unchanged |
| Empty override | Text | Set to the text |

The server still turns a display name equal to the name into no override, as today.

### Other fields

Each field is set in the patch when it differs from the saved waypoint and left unchanged otherwise,
as today. Keywords compare as parsed lists, and the description compares as text. Both were always
sent unchanged before.

## 4. Checks, submitting and feedback

### Checks

The form checks what it can before sending. The checks run in this order on every edit and every
tick, because synced data can change while the form is open, and the first problem found is shown.

| Check | Screens | Kind | Message | Field |
| --- | --- | --- | --- | --- |
| The dimension is empty | Add | Hint | Choose a dimension. | |
| The list is empty | Add | Hint | Enter a list name. | |
| The name is empty | Both | Hint | Enter a name. | |
| Another waypoint in the list has this exact name | Both | Error | *List* already has a waypoint named "*name*". | Name |
| The display name isn't valid formatted text | Edit | Error | The display name is not valid formatted text. | Display name |
| A keyword appears twice, ignoring case | Both | Error | "*keyword*" appears twice in the keywords. | Keywords |
| There are more than 32 keywords | Both | Error | At most 32 keywords. | Keywords |
| A keyword is longer than 64 characters | Both | Error | A keyword can have at most 64 characters. | Keywords |
| The description isn't valid formatted text | Both | Error | The description is not valid formatted text. | Description |
| The list doesn't exist yet | Add | Note | Adding creates the list "*list*". | |

- Hints and errors block: Add or Save is inactive, and the footer says why. The note doesn't block
  and shows only when nothing else does.
- Hints and notes are drawn in `TEXT_MUTED`. Errors are drawn in `DANGER`, and their field gets a
  `DANGER` outline until it changes and the checks run again.
- The checks use the server's rules and shared helpers: `WaypointList.getWaypointByName` for exact,
  case-sensitive names; `FormattedTextHelper.isValidInput`, `hasDuplicateKeywords`, `MAX_KEYWORDS`
  and `MAX_KEYWORD_LENGTH`; and keyword parsing moved into `FormattedTextHelper` (section 6).
- The data comes from `WaypointClientMod.getWaypointFileManager(dimension)`. In singleplayer, that is
  the integrated server's data.
- In the name message, *list* is the list's display name as formatted text. On Edit, the check
  ignores the waypoint's own saved name.
- Vanilla `EditBox` accepts 32 characters by default. Keywords accept 2110, enough for 32 keywords
  of 64 characters with their separators, and the description accepts 2048
  (`MAX_DESCRIPTION_LENGTH`). The name, display name and list keep today's limits.

### Submitting

Add:

1. Add, or Enter in a text field, runs the checks again and sends the command with
   `ClientCommandUtils.sendCommand`. If it can't be sent, the footer shows "Couldn't reach the
   server." in `DANGER`, and nothing locks.
2. Otherwise the form records a `PendingAdd` of the dimension, list and name, shows "Adding…" in
   `TEXT_MUTED` and makes every control inactive except Cancel.
3. Every tick, once the list has a waypoint with that exact name, the screen closes and returns to
   the previous screen.
4. After 5 seconds without it, the form unlocks and shows "The server didn't add the waypoint. Check
   the chat." in `DANGER`. If the waypoint arrives later, the name check then reports it.

Save:

1. Save, or Enter in a text field, runs the checks again, builds the patch and sends the
   `WaypointEditRequestMessage` as today. If it can't be sent, the footer shows "Couldn't reach the
   server." and nothing locks. Otherwise the form locks the same way, with "Saving…".
2. `SUCCESS` closes the screen. Any other result unlocks the form and shows its existing
   `waypoint.edit.error.*` message in `DANGER`. `IDENTIFIER_COLLISION` highlights Name,
   `INVALID_DISPLAY_TEXT` highlights Display name, and `DUPLICATE_KEYWORD` highlights Keywords.
3. The 30-second timeout is unchanged and shows its existing message.

- A new message replaces the previous one. Editing any field removes a message from a server result
  or a timeout, and the checks decide what shows next.
- Tooltips no longer carry errors. The `setTooltip(error)` calls go away.
- Closing through Cancel or Escape while "Adding…" leaves the sent command to finish on its own.

## 5. Keyboard, focus and tooltips

### Keyboard and focus

- **Tab order** follows the screen, row by row and left to right, then the footer buttons. Widgets
  are registered in that order.
  - Add: Dimension, List, Name, Initials, icon dropdown, remove-icon button, color button, hex field,
    Visibility, X, Y, Z, Yaw, Keywords, Description, Cancel, Add.
  - Edit: Name, Initials, Display name, icon dropdown, remove-icon button, color button, hex field,
    Visibility, X, Y, Z, Yaw, Keywords, Description, Reset, Cancel, Save.
- **Enter** in one of the form's text fields submits when Add or Save is active. While a suggestion
  list is open, Enter picks the highlighted entry instead, as today. The dropdowns keep their own use
  of Enter.
- **Escape** closes an open popup, then leaves the focused field, then closes the screen, as today.
  While the color picker is open, Escape closes it and focus returns to the color button.
- **First focus:** Add focuses the first empty required field: List when it's empty, otherwise Name.
  Edit starts with nothing focused, so movement keys keep working and nothing is changed by accident.
- **Movement keys** reach the player while no text field or dropdown has focus, as today.
- **Resizing** the window keeps the values, the focus, the status and a pending Add or Save.
- **Color picker:** while it's open, every other control is inactive. The same state check that
  handles pending requests and failed checks sets this.

### Initials

The initials follow the name while they still equal what `WaypointInitials.getDefaultInitials`
produces for the previous name. Once the player types other initials, changing the name leaves them
alone. On Edit, the initials follow the name only if the saved initials equal the default for the
saved name. Reset restores this state along with the values.

### Tooltips

Resting the pointer for 500 ms on a label, or on the control it labels, shows that field's tooltip at
the pointer, as settings rows do. The area of a field is its label and its controls. In the rows with
two fields, the Initials, Visibility and Yaw labels and their controls have their own tooltips.

No field tooltip shows:

- over the remove-icon button, which has its own vanilla tooltip, "Remove icon";
- while a suggestion list, a dropdown popup or the color picker is open;
- while Add or Save is pending.

| Field | Tooltip |
| --- | --- |
| Dimension | The dimension the waypoint is saved in. |
| List | The list to add it to. A new name creates the list. |
| Name | Identifies the waypoint in its list and in commands. Must be unique in the list. |
| Initials | Shown on the marker when it has no icon. Filled in from the name until you change them. |
| Display name | Formatted text shown instead of the name. Leave empty to show the name. |
| Icon | An item or VoxelMap icon for the marker. Remove it to show the initials. |
| Color | The marker's color. Click the swatch to pick one. |
| Visibility | Global waypoints are always drawn. Local ones only within your local waypoint range (*n* chunks). |
| Position | Block coordinates. ~ is relative to you. ^ is relative to where you look (right, up, forward). |
| Yaw | The direction you face after teleporting, in degrees. |
| Keywords | Comma-separated words that searches also match. |
| Description | Formatted text shown in the waypoint's details. |

The Visibility tooltip reads *n* from `ClientConfig.getViewDistance()` when it shows.

## 6. Code structure

### Screens

- **`AbstractWaypointPropertiesScreen`** stays the shared base, as the GUI guide documents.
  - The constructor builds the shared widgets and their callbacks and stores the saved values. It
    calls no method a subclass overrides.
  - The subclasses supply their parts through methods that the base calls from `init()`, after both
    constructors have finished: the subtitle, the rows above Name (Add's Dimension and List), whether
    there is a Display name row, the footer buttons in visual order with the primary one, the submit
    action and the context for the checks.
  - `refreshControlStates()` sets every control's `active` flag from the current state: the color
    picker, a pending request, the check result and whether Edit's form differs from the saved
    waypoint. It replaces the hand-written lists in `openSwatch` and `closeSwatch` and the
    `onSwatchClosed()` hook.
  - `showDisplayNameField`, `getTitleRowClickableWidgets`, `getButtonRowClickableWidgets` and
    `renderTitleRowOverlays` go away. The base draws the popups of every dropdown it holds after the
    rows.
  - The stored keywords and description, unused today, feed the form.
- **`WaypointAddScreen`** and **`WaypointEditScreen`** keep their constructors, so
  `WaypointManagerScreen`, `WaypointListWidget` and the Xaero's World Map mixins call them unchanged.
  `WaypointClientMod` keeps calling `WaypointEditScreen.handleResult`.

### New helpers

Package-private classes in `screens`, used only by these screens and free of Minecraft text classes
so they can be unit tested:

- **`WaypointFormLayout`:** from the screen size, the label widths, the row heights, the divider
  positions, the header height and the footer's parts, it calculates the panel bounds, the label and
  control columns, each row's position, the row gap, the coordinate field width and where the status
  and buttons go. It applies the rules in section 1.
- **`WaypointFormCheck`:** runs the checks in section 4 and returns the first problem: its kind
  (hint, error or note), its field and its argument, as enums and strings. The screens turn it into a
  translatable `Component`.
- **`PendingAdd`:** the dimension, list and name being added and a 5-second deadline, in the style of
  `EditResponseDeadline`, without a wall clock.
- **An edit-patch builder:** the `WaypointPatch` from the saved waypoint and the form's values,
  following section 3, and whether it changes anything.
- **The initials rule** from section 5.

### Widgets

The GUI guide documents each change in the same change, as `AGENTS.md` requires.

- **`SuggestingTextInput.setPlaceholder(Supplier<Component>)`:** a themed placeholder, drawn in
  `TEXT_PLACEHOLDER`, or `TEXT_DISABLED` while inactive, through vanilla's hint, which shows while
  the field is empty and unfocused. The supplier lets Display name follow the Name field.
  `ComboBoxWidget` passes it to its input, and `ColorHexCodeField` moves its `RRGGBB` hint onto it.
- **`TranslucentTextField.setInvalid(boolean)`:** draws the outline in `DANGER` instead of the normal
  border color.
- **`WaypointIconPicker`:** the remove button becomes a 13×13 `IconButton` with a new 9×9
  `textures/gui/clear.png` (`WidgetTextures.CLEAR_ICON`), inactive while no icon is selected, which
  tints it with `TEXT_DISABLED` like the settings screen's reset icons. The dropdown gets the
  placeholder "None — shows the initials".
- **`DrawContextHelper`** gains the method that schedules a tooltip at the pointer for the current
  frame: `setTooltipForNextFrame` from 1.21.6 and the screen's `setTooltipForNextRenderPass` before.
  `SettingsListWidget` has this branch inline today and switches to the helper.

### Common

`CoreWaypointCommand`'s private keyword parsing, which splits on commas, trims and drops empty
entries, moves to `FormattedTextHelper.parseKeywords`, so `/wp add` and the form split keywords the
same way.

## 7. Text and translations

Text follows the sentence case of the client settings and theme screens: no trailing colons and no
Title Case. Every change applies to all six locales: `en_us`, `es_es`, `he_il`, `zh_cn`, `zh_hk` and
`zh_tw`. The tables give English and Simplified Chinese; the implementation plan carries the other
four. All non-English strings are machine drafts that need a native speaker's review before release.
Each locale keeps its existing terms, such as "waypoint" in Spanish and 路径点, 缩写, 偏航角, 局部
and 全局 in Chinese.

### New keys

| Key | English | Chinese |
| --- | --- | --- |
| `waypoint.form.dimension` | Dimension | 维度 |
| `waypoint.form.list` | List | 列表 |
| `waypoint.form.name` | Name | 名称 |
| `waypoint.form.initials` | Initials | 缩写 |
| `waypoint.form.display_name` | Display name | 显示名称 |
| `waypoint.form.color` | Color | 颜色 |
| `waypoint.form.visibility` | Visibility | 可见范围 |
| `waypoint.form.position` | Position | 位置 |
| `waypoint.form.yaw` | Yaw | 偏航角 |
| `waypoint.form.keywords` | Keywords | 关键词 |
| `waypoint.form.description` | Description | 描述 |
| `waypoint.form.optional` | Optional | 可选 |
| `waypoint.form.no_icon` | None — shows the initials | 无（显示缩写） |
| `waypoint.form.empty_display_name` | Empty: the marker shows no name | 空：标记不显示名称 |
| `waypoint.form.dimension.tooltip` | The dimension the waypoint is saved in. | 路径点所在的维度。 |
| `waypoint.form.list.tooltip` | The list to add it to. A new name creates the list. | 要添加到的列表。输入新名称会创建该列表。 |
| `waypoint.form.name.tooltip` | Identifies the waypoint in its list and in commands. Must be unique in the list. | 在列表和命令中用于识别路径点，在列表中必须唯一。 |
| `waypoint.form.initials.tooltip` | Shown on the marker when it has no icon. Filled in from the name until you change them. | 没有图标时显示在标记上。在你修改之前会根据名称自动填写。 |
| `waypoint.form.display_name.tooltip` | Formatted text shown instead of the name. Leave empty to show the name. | 代替名称显示的格式化文本。留空则显示名称。 |
| `waypoint.form.icon.tooltip` | An item or VoxelMap icon for the marker. Remove it to show the initials. | 标记使用的物品或 VoxelMap 图标。移除后显示缩写。 |
| `waypoint.form.color.tooltip` | The marker's color. Click the swatch to pick one. | 标记的颜色。点击色块进行选择。 |
| `waypoint.form.visibility.tooltip` | Global waypoints are always drawn. Local ones only within your local waypoint range (%s chunks). | 全局路径点始终绘制。局部路径点只在你的局部路径点范围（%s 区块）内绘制。 |
| `waypoint.form.position.tooltip` | Block coordinates. ~ is relative to you. ^ is relative to where you look (right, up, forward). | 方块坐标。~ 相对于你的位置，^ 相对于你的视线方向（右、上、前）。 |
| `waypoint.form.yaw.tooltip` | The direction you face after teleporting, in degrees. | 传送后面朝的方向，单位为度。 |
| `waypoint.form.keywords.tooltip` | Comma-separated words that searches also match. | 用逗号分隔的词语，搜索时也会匹配。 |
| `waypoint.form.description.tooltip` | Formatted text shown in the waypoint's details. | 显示在路径点详情中的格式化文本。 |
| `waypoint.form.status.choose_dimension` | Choose a dimension. | 请选择维度。 |
| `waypoint.form.status.enter_list` | Enter a list name. | 请输入列表名称。 |
| `waypoint.form.status.enter_name` | Enter a name. | 请输入名称。 |
| `waypoint.form.status.name_taken` | %1$s already has a waypoint named "%2$s". | %1$s 中已有名为“%2$s”的路径点。 |
| `waypoint.form.status.duplicate_keyword` | "%s" appears twice in the keywords. | 关键词“%s”重复。 |
| `waypoint.form.status.too_many_keywords` | At most %s keywords. | 最多 %s 个关键词。 |
| `waypoint.form.status.keyword_too_long` | A keyword can have at most %s characters. | 每个关键词最多 %s 个字符。 |
| `waypoint.form.status.invalid_description` | The description is not valid formatted text. | 描述不是有效的格式化文本。 |
| `waypoint.form.status.new_list` | Adding creates the list "%s". | 添加时将创建列表“%s”。 |
| `waypoint.form.status.adding` | Adding… | 正在添加… |
| `waypoint.form.status.saving` | Saving… | 正在保存… |
| `waypoint.form.status.add_timeout` | The server didn't add the waypoint. Check the chat. | 服务器未添加该路径点。请查看聊天栏。 |
| `waypoint.form.status.send_failed` | Couldn't reach the server. | 无法连接到服务器。 |
| `waypoint.edit.screen.location` | In %1$s · %2$s | 位于 %1$s · %2$s |
| `waypoint.save.button` | Save | 保存 |
| `waypoint.edit.error.upload_busy` | The server is busy with a waypoint upload. Try again in a moment. | 服务器正在处理路径点上传，请稍后再试。 |

- The Display name check reuses `waypoint.edit.error.invalid_display_text`.
- The Icon label reuses `waypoint.icon.label`.
- `waypoint.edit.error.upload_busy` fills a gap: the edit screen builds `waypoint.edit.error.<status>`
  for every result, and the server can answer `UPLOAD_BUSY`.

### Kept keys with new text

| Key | English | Chinese |
| --- | --- | --- |
| `waypoint.add.screen.title` | Add waypoint | 添加路径点 |
| `waypoint.icon.clear` | Remove icon | 移除图标 |

`waypoint.icon.clear` becomes the remove-icon button's tooltip.

### Keys added to the other locales

The 13 existing `waypoint.edit.error.*` messages exist only in `en_us` and `zh_cn`. They now show in
the footer, so `es_es`, `he_il`, `zh_hk` and `zh_tw` get them too.

### Removed keys

Removed from every locale that has them:

- `waypoint.edit.screen.name.entry`, `waypoint.edit.screen.identifier.entry`,
  `waypoint.edit.screen.display_name.entry`, `waypoint.edit.screen.initials.entry`,
  `waypoint.edit.screen.color`, `waypoint.edit.screen.coords_yaw` and
  `waypoint.edit.screen.visibility`, replaced by `waypoint.form.*`.
- `waypoint.display_name.clear.button` and `waypoint.display_name.keep.button`, because the toggle is
  gone.
- `waypoint.update.button`, replaced by `waypoint.save.button`.
- `waypoint.dimension.info` and `waypoint.list_name.info`, which only these screens use.

`waypoint.edit.screen.previous_color.hover` and `waypoint.edit.screen.current_color.hover` stay,
because `SwatchWidget` uses them.

## 8. Documentation

- **This feature folder:** a README index, this spec, and `plans/` and `validation/` folders that
  hold a `.gitkeep` until they have documents. The folder is listed in `docs/features/README.md`.
- **GUI guide** (`tips/gui/local-guide.md`), updated in the same change as the code:
  - The "Choosing a base screen" bullet for `AbstractWaypointPropertiesScreen`: the new subclass
    methods instead of `showDisplayNameField` and `onSwatchClosed()`.
  - The bullet on the add and edit screens: the layout helper, the checks, `PendingAdd` and the
    patch rules. It drops the claim that transport resets call a
    `WaypointEditScreen.handleTransportReset()`, which doesn't exist.
  - The dropdown section: the base draws every dropdown's popup after the rows, instead of
    `renderTitleRowOverlays(...)`.
  - `setPlaceholder`, `setInvalid`, the icon picker's `IconButton` and the new `DrawContextHelper`
    tooltip method.
- **README and README_zh:** one bullet in the usage list: the add and edit screens also set keywords
  and a description.

## Constraints

- Java 17 and four-space indentation. No build script changes are expected.
- Every Stonecutter target from 1.20.1 to 26.3 must keep working. Version differences stay in
  `DrawContextHelper` and the existing branches. No new swap or replacement is planned; if one
  becomes necessary, update the inventory in `AGENTS.md` in the same change.
- No backward-compatibility code, per `AGENTS.md`. Removed translation keys need no migration.
- No test or probe reads these screens' members, so their fields may be renamed.
- Commit only when asked, per `AGENTS.md`.

## Validation

### Unit tests

In the mods test source set unless noted:

- `WaypointFormLayoutTest`:
  - The label column from the widest label, and its narrowing and wrapping when the Position row
    needs the room.
  - The control column at 240 pixels, and shrunk to fit a 320-pixel screen.
  - The coordinate fields clamped to 30–48 pixels.
  - A 9-pixel gap for Add and Edit at 480×270 and 320×240 with a one-line status, a smaller gap with
    a wrapped status, never below 5, and the group at the top margin when it can't fit.
  - Divider spacing, the status beside or above the buttons, and centering.
- `WaypointFormCheckTest`: every check in its order, which one wins, exact and case-sensitive names,
  Edit accepting its own saved name, the note not blocking, the keyword rules and invalid formatted
  text.
- `PendingAddTest`: arrival before the deadline, expiry at 5 seconds, and nothing pending after
  clearing.
- An edit-patch test: every row of the display-name table, keywords and description set only when
  they change, and "no change" when nothing differs.
- The initials rule: it follows the name, stops after other initials are typed, and on Edit starts
  only when the saved initials are the default.
- `WaypointFormTranslationTest`: every key the screens use exists in all six locales with the same
  placeholders as `en_us`, the 13 edit errors and `upload_busy` exist everywhere, and the removed keys
  are gone.
- In `common`, `FormattedTextHelperTest`: `parseKeywords` splits on commas, trims and drops empty
  entries.

### Gradle

- `:mods:26.1.2-fabric:test`, the active Stonecutter target, and `:common:test`.
- Compile `1.20.1-fabric`, `1.20.1-forge`, `1.21.6-fabric` (the first version that uses
  `setTooltipForNextFrame`), `26.3-fabric` and `26.3-neoforge`.
- `git diff --check`, and balanced Stonecutter markers in every touched file.

### In game

A manual pass on 26.1.2 Fabric, with results recorded in `validation/`. Compiling can't prove these:

- Both screens at 480×270, 378×245 and 320×240, including a status that wraps.
- Add from the manager's + button, from a list row and from Xaero's World Map. It closes when the
  waypoint arrives, in singleplayer and on a dedicated server, and shows the timeout message when the
  server refuses, for example without permission.
- Edit: saving, each field highlight, a permission error, and Reset.
- Tab order, Enter, Escape, first focus, movement keys with nothing focused, and resizing during
  "Adding…" and while a field has focus.
- The color picker making every other control inactive, and the tooltips after 500 ms but not over
  popups.
- The three built-in themes.
