# Client Config Screen Design

Status: design agreed on 2026-09-28. Not implemented yet.

## Intent

`ClientConfigScreen` edits the client settings in `ClientConfig`. Players reach it only by pressing
`C` in the waypoint manager. It predates the redesigned manager and theme editor and was never
designed as a whole:

- `ClientConfig.autoSyncToVoxelMap` has no control, so players can't turn VoxelMap sync off.
- Without Xaero's Minimap, its two rows are grayed out with no explanation.
- The Xaero's Minimap sync dialog says personal waypoint sets that share a server list's name are
  lost. Since 2026-08-08, sync rewrites only the sets Server Waypoint manages (the `sw␟` prefix), so
  the warning is wrong. The README repeats it.
- Escape with the dialog open closes the whole screen, and so does Escape while typing a number.
  Closing the dialog focuses the render toggle instead of the Sync button. Two hand-written lists
  enable and disable every control.
- Eight rows sit in one flat, transparent list that mixes rendering, map sync and theme settings.
  Units in the labels make every row about 390 pixels wide, and nothing handles a narrower GUI.
  Nothing explains what a setting does, and "Background Transparence" actually sets opacity.
- There is no Done button and no way to reset a setting. Changes are lost when the screen is
  replaced without `onClose()`, for example on a disconnect.
- The layout is recalculated every frame, and one slider is nudged 2 pixels by hand.

This document describes the finished screen, the reusable `SettingsListWidget` it's built on, and
the supporting changes: reset to defaults, a manual VoxelMap sync, and opening the screen from the
loaders' mod lists. It's the reference for the implementation plan.

## Scope

In scope:

1. Layout and content.
2. `SettingsListWidget`.
3. Settings, reset and map-mod sync.
4. Dialogs, focus, lifecycle and opening from mod lists.
5. Text and translations.
6. Documentation.

Out of scope:

- Narration for the settings list.
- Setting ranges. For example, scale can still go down to 0%.
- The manager's sort, grouping and all-dimensions state, which the manager controls itself.
- Popups inside settings rows.
- Catching up automatically when auto sync is turned back on. The Sync button covers that.

## 1. Layout and content

```text
Server Waypoint 4.0.0 client settings
┌────────────────────────────────────────────────────────────┐
│ Waypoint rendering ──────────────────────────────────────  █
│ Show in-world waypoints                [  On  ]          ↺ █
│ Scale                        [====|-----] [100] %        ↺ █
│ Vertical offset              [----|-----] [  0] %        ↺ █
│ Background opacity           [======|---] [128]          ↺ ▒
│ Local waypoint range         [|---------] [ 12] chunks   ↺ ▒
│                                                            ▒
│ Map mods ────────────────────────────────────────────────  ▒
│ Xaero's Minimap: auto sync             [  On  ]          ↺ ▒
│ Xaero's Minimap: sync now             [ Sync… ]            ▒
│ VoxelMap                          Not installed            │
│                                                            │
│ Appearance ──────────────────────────────────────────────  │
│ Color theme                       [ Configure ]            │
└────────────────────────────────────────────────────────────┘
Settings reset to defaults.       [ Reset to defaults… ] [ Done ]
```

### Structure

The title, the panel and the footer form one group, centered on screen as today.

- **Title:** a `ScalableText` at 1.2× scale in `TEXT_PRIMARY`, left-aligned with the panel and
  wrapped to the panel width.
- **Panel:** the `SettingsListWidget`, which draws its own themed panel (section 2).
- **Footer:** as wide as the panel. A status message sits on the left, and "Reset to defaults…" and
  Done sit on the right. Done returns to the previous screen, like Escape. A `WidgetPack` places the
  buttons from the right. The status is a `ScalableText` wrapped to the width left of the buttons
  minus an 8-pixel gap, and the footer is as tall as its taller side. When that leaves the status
  less than 100 pixels, as with Spanish at a 320-pixel GUI, the status takes its own full-width line
  above the buttons instead.

### Sections and rows

| Section | Row | Control | Unit | Default |
| --- | --- | --- | --- | --- |
| Waypoint rendering | Show in-world waypoints | On/Off toggle | | On |
| | Scale | Slider and field, 0–500 | % | 100 |
| | Vertical offset | Slider and field, −100–100 | % | 0 |
| | Background opacity | Slider and field, 0–255 | | 128 |
| | Local waypoint range | Slider and field, 0–1024 | chunks | 12 |
| Map mods | *Mod*: auto sync | On/Off toggle | | On |
| | *Mod*: sync now | "Sync…" button | | |
| Appearance | Color theme | "Configure" button, which opens the theme editor | | |

- The Map mods section lists Xaero's Minimap on every loader and VoxelMap on Fabric only. A mod
  that isn't installed takes a single row: its name, and a muted "Not installed" in the control
  column (section 3).
- Setting rows end with a reset icon (↺). Action rows (Sync, Configure) and "Not installed" rows
  have none.
- Hovering a row shows its description tooltip, followed by "Default: …" for a setting.
- Labels use sentence case, like the theme editor. Units move out of the labels into their own
  column. Toggles read On and Off instead of True and False.

### Sizes

| Item | Value |
| --- | --- |
| Screen margin | 10 px |
| Gap between title and panel, and between panel and footer | 6 px |
| Panel padding | 6 px on each side |
| Scrollbar reserve | 6 px scrollbar plus a 2 px gap, always reserved |
| Gap before each section header except the first | 6 px |
| Section header | 13 px: 9 px of text and 4 px below |
| Setting row | Tallest part plus 4 px above and below, at least 21 px |
| Column gaps: label to control, control to unit, unit to reset | 8, 3 and 4 px |
| Reset button | 13×13 `IconButton` with a 9×9 icon |
| Footer buttons | `TranslucentButton`, 11 px content height, `max(50, text width + 10)` wide, 6 px apart |
| Dialog text | Wrapped to 220 px |

- **Panel width:** `min(list.getPreferredWidth() + 12, screen width − 20)`. In English the
  preferred width is about 320 px, so the panel fits the 378-pixel GUI of a Retina MacBook and the
  480-pixel GUI of a 1080p window at auto GUI scale. At vanilla's 320-pixel minimum, the panel
  shrinks to fit the margins and long labels wrap onto a second line, which makes their rows taller.
- **Panel height:** `min(list.getContentHeight() + 12, the space left after the title, footer, gaps
  and margins)`. The list scrolls when its content doesn't fit.

## 2. `SettingsListWidget`

A public, reusable widget in `client.gui.widgets` for scrollable settings rows with section
headers. It extends `ShiftableScrollableWidget`, which already owns the scroll position, scrollbar
drawing and scrollbar dragging, and adds what `TreeViewWidget` can't do: entries of different
heights with their own layout and hit testing.

```java
public class SettingsListWidget extends ShiftableScrollableWidget implements Padding, Expandable {
    public SettingsListWidget(Font font);
    public void setEntries(List<Entry> entries);   // copies the list, then lays it out
    public void relayout();                         // after a label, unit or control size changes
    public int getPreferredWidth();                 // widest entry without wrapping, plus scrollbar space
    public int getContentHeight();                  // total entry height, for sizing the panel
    public void reveal(GuiEventListener widget);    // scroll a row widget into view (keyboard focus)
    public void visitWidgets(Consumer<AbstractWidget> consumer); // row widgets first, then the list

    public abstract static sealed class Entry permits Header, Row { }

    public static final class Header extends Entry {
        public Header(Component title);
    }

    public static final class Row extends Entry {
        public <C extends LayoutElement & Renderable> Row(Component label, C control);
        public Row suffix(Component unit);               // muted text, left-aligned in the unit column
        public Row action(AbstractWidget action);        // last column, such as the reset button
        public Row tooltip(Supplier<Component> tooltip); // read on hover, so it can reflect state
        public Row labelColor(WidgetThemeVariable color);
    }
}
```

### Layout

- Entries stack from top to bottom, with a 6-pixel gap before every header except the first.
- **Header:** the title in `TEXT_PRIMARY`, then a 1-pixel `BORDER` line from 4 pixels after the
  title to the right edge of the rows. A title that has to wrap draws no line.
- **Row:** four columns: label, control, unit and action.
  - The control is any `LayoutElement & Renderable`: usually a registered `AbstractWidget`, or a
    non-interactive element such as the "Not installed" `ScalableText`. Padded elements are placed
    by their visual bounds. A composite control, such as a `WidgetStack` of buttons, counts every
    widget its `visitWidgets` reports as the row's: `reveal` finds the row from any of them, and the
    row is a Tab stop while any of them is active.
  - The unit column is as wide as the widest unit in the list, and the action column as wide as
    the widest action. A row without a unit or action keeps the empty column, so every control's
    right edge lines up. A column of width zero also drops its gap.
  - The label is drawn by a `ScalableText` in `TEXT_PRIMARY`, or in the row's label color. It wraps
    to the width left after the control, the columns and the 8-pixel gap.
  - A row is as tall as its tallest part plus 8 pixels, and at least 21 pixels. Every part is
    centered vertically.
- The widget's bounds are the visible area. Its `Padding` bounds add 6 pixels on each side for the
  panel, which a `PaddingBackground` draws with `PANEL_BACKGROUND` and a `BORDER` outline.
  `setVisualWidth` and `setVisualHeight` subtract the padding.
- Rows are laid out in the width minus the scrollbar reserve, so text doesn't wrap again when the
  list starts to overflow.
- Layout runs only on `setEntries`, a width change or `relayout()`, never while rendering. The
  scroll position is clamped to the new content height afterwards.

### Registration and rendering

- The screen calls `list.visitWidgets(this::addRenderableWidget)`. It visits every row's control
  and action in entry order, then the list itself. Registration order matters: from 1.21.5, vanilla
  sends a click only to the first registered child under the cursor, and it routes the mouse wheel
  that way on every version. A list registered before its rows would take their clicks.
- The list renders everything once: the panel; then, inside a scissor, the hovered row's
  background, the headers, and each row's label, control, unit and action; then the scrollbar. The
  screen must not render the row widgets itself.

### Clipping and input

- The list owns `visible` for its row widgets. After every layout and scroll change, a widget is
  visible, so it can be clicked and focused, only while it's entirely inside the visible area.
  Screens set only `active`.
- A partly visible widget is still drawn, clipped by the scissor. The list marks it visible just
  for the draw call and passes a mouse position outside the screen, so it shows no hover state or
  tooltip.
- Clicks on empty list space do nothing and make no sound. The list handles only scrollbar
  dragging.
- **Mouse wheel:** screens offer the wheel to the list before `super.mouseScrolled`. While the list
  overflows, the wheel scrolls it by 10 pixels per notch. When it doesn't overflow, the wheel
  reaches the slider under the cursor.
- **Keyboard:** the list itself isn't a Tab stop; `nextFocusPath` returns null. Vanilla's Tab skips
  invisible widgets, so after a key press moves focus to a row widget, the screen calls `reveal`.
  It scrolls by the smallest amount that fully shows that row. When they fit, it also shows the
  nearest rows above and below that have an active widget, extended over any header between them.
  Tab and Shift-Tab then always reach the neighboring rows, even past a row whose widgets are
  disabled, such as a blocked Sync button. Mouse clicks never scroll the list.

### Hover and tooltips

- The row under the pointer gets a `ROW_HOVER_BACKGROUND` fill across the row width. Headers don't.
- After the pointer rests on the same row for 500 ms, the list schedules that row's tooltip at the
  cursor during its render pass: `setTooltipForNextFrame` on 1.21.6 and later, and the screen's
  `setTooltipForNextRenderPass` before that, as in `IconListWidget` and
  `RemoteWaypointPanel.BrowserTree`.
- There's no row tooltip while the pointer is over the row's action, because the reset button keeps
  its own vanilla tooltip. There's none while the list is inactive either, or when the tooltip
  supplier returns null.

### Limitations

- Row controls must not open popups, because the scissor would clip them.
- Entries can't be inserted or removed one at a time. Call `setEntries` again.
- A composite control draws its own widgets, and a hidden widget draws nothing, so its partly
  visible widgets disappear instead of being drawn clipped.

### Building blocks

- **`layout/SettingsListLayout`:** a pure helper in the style of `IconListLayout`. It calculates the
  unit and action column widths, a row's label width, entry offsets with header gaps, the scroll
  position that reveals a row and its neighbors, and whether an item is fully inside the visible
  area.
- **`WidgetPack` cross-axis alignment:** each row lays out through a `WidgetPack` of the row's size.
  The label is added from the left; the action (or a `SpacerElement` of the action column's width),
  the unit (a `ScalableText` as wide as the unit column, or a spacer) and the control are added from
  the right, with spacers for the 4- and 3-pixel gaps. `WidgetPack` gains a cross-axis alignment
  option: `START`, today's behavior and the default, or `CENTER`.

## 3. Settings, reset and map-mod sync

### Settings model

- **`ClientConfig`** gets named default constants, and its field initializers use them:
  `DEFAULT_ENABLE_WAYPOINT_RENDER = true`, `DEFAULT_WAYPOINT_SCALING_FACTOR = 100`,
  `DEFAULT_WAYPOINT_VERTICAL_OFFSET = 0`, `DEFAULT_WAYPOINT_BACKGROUND_ALPHA = 128`,
  `DEFAULT_VIEW_DISTANCE = 12`, `DEFAULT_AUTO_SYNC_TO_XAEROS_MINIMAP = true` and
  `DEFAULT_AUTO_SYNC_TO_VOXELMAP = true`. The setters keep applying changes to the renderer
  immediately.
- **`ClientConfigSettings`** is a new package-private pure model in `screens`, used only by this
  screen. It lists the settings the screen shows: the five rendering settings, and auto sync for
  each installed map mod. Each setting has its label and description keys, default, range, unit,
  getter and setter, and offers:
  - `isDefault(config)`.
  - `reset(config)`, which goes through the setter, so the change applies immediately.
  - Its formatted default, such as "100%", "12 chunks" or "On".

  Settings of mods that aren't installed are left out, so they never enable a reset button.

| Setting | Label key | Range | Default | Unit | `ClientConfig` accessors |
| --- | --- | --- | --- | --- | --- |
| Show in-world waypoints | `config.enable_waypoint_render` | | On | | `isEnableWaypointRender`, `setEnableWaypointRender` |
| Scale | `config.waypoint_scale_factor` | 0–500 | 100 | % | `getWaypointScalingFactor`, `setWaypointScalingFactor` |
| Vertical offset | `config.waypoint_vertical_offset` | −100–100 | 0 | % | `getWaypointVerticalOffset`, `setWaypointVerticalOffset` |
| Background opacity | `config.waypoint_bg_opacity` | 0–255 | 128 | | `getWaypointBackgroundAlpha`, `setWaypointBackgroundAlpha` |
| Local waypoint range | `config.local_waypoint_view_distance` | 0–1024 | 12 | chunks | `getViewDistance`, `setViewDistance` |
| *Mod*: auto sync | `config.map_mod.auto_sync` | | On | | `isAutoSyncToXaerosMinimap`, `setAutoSyncToXaerosMinimap`, and the VoxelMap pair |

Label keys are relative to `server_waypoint.`.

- **Controls:** `OnOffToggleButton` for booleans, and `IntegerSlider` with its 100-pixel track and
  30-pixel field for integers, as today. Vertical centering in the row replaces the slider's -2
  pixel offset.
- **Refreshing controls:** after a reset, the controls read `ClientConfig` again. An
  `updatingControls` guard keeps their change callbacks from calling the setters again while that
  happens, as in the theme editor.

### Reset

- **Row reset icon:** a 13×13 `IconButton` with a new 9×9 texture, `textures/gui/reset.png`,
  exposed as `WidgetTextures.RESET_ICON`.
  - It's active only when the setting differs from its default and no dialog is open.
  - Clicking it resets the setting and moves focus to the row's control.
  - Its vanilla tooltip reads "Reset to default: 100%", with the setting's formatted default.
- **"Reset to defaults…":** active when any shown setting differs from its default and no dialog is
  open. It opens a confirmation dialog:
  - Title: "Reset settings?"
  - Text: "Waypoint rendering and map sync settings go back to their defaults. Your waypoints and
    color theme don't change."
  - Buttons: Cancel and Reset.

  Confirming resets every shown setting and shows "Settings reset to defaults." in the footer, in
  `SUCCESS`.
- The config file is still written when the screen goes away (section 4).

### Map mods

- **`MapModIntegration`** gains:
  - `isInstalled()`: `ClientConfig.isXaerosMinimapLoaded` or `isVoxelMapLoaded`.
  - `isReady()`: `WaypointClientMod.isXaerosMinimapReady` for Xaero's Minimap, and always true for
    VoxelMap, which is treated as ready once in a world, as auto sync already assumes.
  - `syncAll(WaypointClientMod)`: the mod's existing `replaceAll`.
- **`MapModIntegrations`** gains `find(UploadTarget)`, which is empty when this loader has no
  integration for the mod. VoxelMap's Fabric-only support therefore comes from the integration
  list, not from a loader check in the screen. `syncNow(UploadTarget, WaypointClientMod)` replaces
  `syncXaerosMinimap`, and `MinimapWorldStateUpdaterMixin` calls it with `XAERO`.
- **Mod names:** `server_waypoint.map_mod.xaeros_minimap` and `server_waypoint.map_mod.voxelmap`.
- **Rows:** a pure function maps whether the loader supports a mod and whether it's installed to
  `MapModRowState`:

  | State | When | Rows |
  | --- | --- | --- |
  | `HIDDEN` | This loader has no integration | None |
  | `NOT_INSTALLED` | Supported, not installed | "*Mod*" with a muted "Not installed". Tooltip: "Install *Mod* to sync this server's waypoints to it." |
  | `INSTALLED` | Supported and installed | "*Mod*: auto sync" with its reset icon, and "*Mod*: sync now" |

### Sync now

A pure function returns the `SyncBlocker` that stops a sync, or null when the mod can sync. An
enum keeps it testable without Minecraft text classes; the screen turns it into a translatable
component. This function, the `MapModRowState` mapping and both enums live in a package-private
`ClientConfigSync` class next to the screen. The first matching row wins:

| Condition | Blocker | Message |
| --- | --- | --- |
| `minecraft.level` is null | `NO_WORLD` | `server_waypoint.config.sync.no_world` |
| The manager's view state is `LOADING` | `WAYPOINTS_LOADING` | `server_waypoint.manager.loading` |
| The manager's view state is `UNSUPPORTED` | `NO_SERVERSIDE_SUPPORT` | `server_waypoint.no_serverside_support` |
| The manager's view state is `INCOMPATIBLE` | `INCOMPATIBLE_SERVER` | `server_waypoint.incompatible_protocol_version` |
| `isReady()` is false | `MAP_MOD_LOADING` | `server_waypoint.config.sync.map_mod_loading` |

- The manager's view state comes from `WaypointManagerScreen.resolveViewState(
  WaypointServerMod.runsWithClient(), WaypointClientMod.getNetworkState())`, so Sync is available
  exactly when the manager shows its full UI.
- `isXaerosMinimapReady` stays true for the rest of the session once set, so the world check has to
  come first.
- The screen checks again every tick. A blocked Sync button is disabled, and its row tooltip adds the
  blocker's message after the description.
- **Confirmation dialog:** Sync opens "Sync to *Mod*?" with the same line colors as today's dialog:

  | Line | Color |
  | --- | --- |
  | Replaces the waypoints Server Waypoint added to *Mod* with this server's current waypoints. | `TEXT_PRIMARY` |
  | What stays: | `SUCCESS` |
  | Everything you created yourself, even with the same name as a server list. | `TEXT_PRIMARY` |
  | What is lost: | `DANGER` |
  | Changes you made to waypoints Server Waypoint added, and waypoints from lists removed on the server. | `TEXT_PRIMARY` |

  The buttons are Cancel and Sync. `ConfirmationDialog` gains a constructor that takes the confirm
  button's label, and exposes its Cancel button so a screen can focus it. The existing constructor
  keeps "Confirm". Both buttons are at least 50 pixels wide and grow to fit their text, because
  labels such as "Restablecer" don't fit in 50. `DialogWidget` builds its buttons in an abstract
  method called from its own constructor, before a subclass can store the label, so it takes the
  buttons as a constructor argument instead. `ConfirmationDialog` is its only subclass.
- **Confirming** checks the blocker again, in case the connection changed while the dialog was
  open. A blocker shows its message in the footer in `DANGER` instead of syncing. Otherwise:
  - Success shows "Synced waypoints to *Mod*." in `SUCCESS`.
  - A `RuntimeException` is logged with the mod's name and shows "Couldn't sync to *Mod*. See the
    game log." in `DANGER`.
- A new status message replaces the previous one. The status stays until it's replaced or the
  screen closes.

## 4. Dialogs, focus, lifecycle and mod lists

### Dialogs

- At most one dialog is open: Reset settings, Sync to Xaero's Minimap, or Sync to VoxelMap. It's
  centered on screen and drawn between `nextLayer` and `previousLayer`.
- While a dialog is open, the list, its row widgets and the footer buttons are inactive. The content
  under the dialog is drawn with a mouse position outside the screen, so no hover state or tooltip,
  including a reset icon's vanilla tooltip, shows through the dialog.
- `refreshControlStates()` sets every control's `active` flag from the current state: an open
  dialog, each value against its default, sync blockers and installed mods. It replaces the two
  hand-written lists in `openXaerosSyncConfirmationDialog` and `closeXaerosSyncConfirmationDialog`,
  which re-enable every control without checking.

### Escape and focus

- **Escape:** with a dialog open, Escape cancels the dialog and the screen stays. Otherwise
  `MovementAllowedScreen.dismissFocusedInput()` applies: the first Escape closes a focused popup or
  leaves a focused text field, combo box or number field, and the next one closes the screen.
- **Number fields:** an `IntegerSlider` is one focusable widget that holds a slider track and an
  `IntegerField`. `dismissFocusedInput()` treats it as text entry while its field, not its track,
  has focus, which a new `IntegerSlider.isEditingNumber()` reports. Leaving the field commits the
  typed number. With the track focused, Escape closes the screen at once. The theme editor's
  sliders behave the same way, because it calls `dismissFocusedInput()` too.
- **Activation keys:** Enter, Space and keypad Enter press the focused button, because the GUI's
  buttons extend `ShiftableButtonWidget`. The rules below hold for key presses as well as clicks. As
  on every `MovementAllowedScreen`, Space presses a button only after Tab or arrow-key navigation;
  after a mouse click it jumps.
- Opening a dialog focuses its Cancel button.
- Closing a dialog focuses the button that opened it. If that button is now inactive, as "Reset to
  defaults…" is right after a reset, focus goes to Done.
- A row's reset icon moves focus to that row's control.
- Vanilla focuses the clicked widget after its callback runs. Callbacks therefore set the focus they
  want and record it, and the screen applies it again after `super.mouseClicked`, like the theme
  editor's `normalizeModalFocus`. A key press doesn't move focus after the callback, so the focus it
  set stands.
- After a key press moves focus to a row widget, the screen calls `list.reveal(...)`.
- If scrolling hides the focused row widget, the screen clears focus. That commits a half-typed
  number.

### Lifecycle

- **Constructor:** builds the settings model, the rows and the dialogs, so they survive resizes.
- **`init()`:** sizes and positions the title, panel, footer and dialog from the window size.
  Registers the row widgets, then the list, then the footer buttons, then the dialog buttons. Then
  refreshes the control states. An open dialog stays open through a resize, and the list keeps its
  scroll position.
- **Rendering** only draws. Layout no longer runs every frame. Besides `init()`, it runs again only
  when the status message changes, because a wrapped status can change the footer's height.
- **`tick()`** checks the sync blockers again.
- **Saving** moves to `removed()`, which every exit reaches: Done, Escape, opening the theme editor,
  or a disconnect with the screen open. `onClose()` only returns to the parent screen.

### Opening from mod lists

| Loader | Targets | Hook | Registered in |
| --- | --- | --- | --- |
| Fabric | All | Mod Menu's `modmenu` entrypoint: `ServerWaypointModMenu implements ModMenuApi` returns `parent -> new ClientConfigScreen(parent)` | `fabric.mod.json`, new class in `_959.server_waypoint.fabric` |
| NeoForge | 1.20.2, 1.20.4 | `ConfigScreenHandler.ConfigScreenFactory` through `ModLoadingContext` | `ServerWaypointNeoForgeClient.initialize` |
| NeoForge | 1.20.6 and later | `IConfigScreenFactory` through `ModLoadingContext` | `ServerWaypointNeoForgeClient.initialize` |
| Forge | 1.20.1–26.2 | `MinecraftForge.registerConfigScreen`; on 1.20.2, whose Forge lacks it, `ConfigScreenHandler.ConfigScreenFactory` through `ModLoadingContext` | `ServerWaypointForgeClient.initialize` |

- Mod Menu is a compile-only dependency: `maven.modrinth:modmenu`, through `modCompileOnly` in
  `fabric.gradle.kts` and `compileOnly` in `fabric-unobfuscated.gradle.kts`, with a `modmenu`
  property in every `mods/versions/*-fabric/gradle.properties`. A target without its own Mod Menu
  release uses the nearest release whose `ModMenuApi` compiles. Without Mod Menu installed, the
  entrypoint is never loaded.
- The plan checks the exact registration calls and factory signatures against each target's
  sources. NeoForge 26.3 declares `createScreen(ModContainer, Screen)`; the build uses NeoForge
  20.2.93, 20.4.251, 20.6.139, 21.0.167 and later, and Forge 47.4.20 through 65.0.0.
- The NeoForge and Forge factories call the client class's `ensureClientStarted()` before building
  the screen, because those loaders create the client mod on the first client tick. On Fabric it
  exists from `CLIENT_STARTED`, before any mod list can open.

### Without a world

- When `minecraft.level` is null, `MovementAllowedScreen` draws vanilla's background layers for
  screens outside a world (the panorama on newer versions) before its themed overlay. The theme
  editor opened from there gets the same background.
- Both Sync rows are disabled with "Join a world to sync."
- Done and Escape return to the mod list.

### Known behavior

- On servers where VoxelMap asks the player to choose a world, a sync before that choice goes to
  VoxelMap's current world. Auto sync already behaves this way.
- Changes apply to the renderer immediately, but the config file is written only when the screen is
  removed.

## 5. Text and translations

Text follows the theme editor's sentence case. Every change applies to all six locales: `en_us`,
`es_es`, `he_il`, `zh_cn`, `zh_hk` and `zh_tw`, as in the waypoint manager. The tables below give
the English and Simplified Chinese text; the implementation plan carries the exact strings for the
other four locales. All non-English strings are drafts that need a native speaker's review before
release. Keys are relative to `server_waypoint.`.

- The Chinese strings call the mod 本模组 (本模組 in Traditional Chinese), "this mod", wherever a
  sentence names it. Its Chinese name, 服务器路径点, also reads as "server waypoints".
- Each locale keeps its existing terms: for example "puntos de ruta" and "minimapa de Xaero" in
  Spanish, נקודות ציון and מפת המיני של Xaero in Hebrew, and its own words for local and global
  visibility.

### Kept keys with new text

Their meaning doesn't change, so the keys stay. All six locales get the new text, which drops the
units now shown in their own column.

| Key | English | Chinese |
| --- | --- | --- |
| `config.screen.title` | Server Waypoint %1$s client settings | 服务器路径点（%1$s）客户端设置 |
| `config.enable_waypoint_render` | Show in-world waypoints | 在世界中显示路径点 |
| `config.waypoint_scale_factor` | Scale | 缩放 |
| `config.waypoint_vertical_offset` | Vertical offset | 垂直偏移 |
| `config.local_waypoint_view_distance` | Local waypoint range | 局部路径点范围 |
| `config.theme` | Color theme | 颜色主题 |

`config.theme.open` ("Configure", 配置) and `config.confirm_sync` ("Sync", 同步) keep their text.
`config.confirm_sync` becomes the sync dialog's confirm button. `config.theme` and
`config.theme.open` exist only in `en_us` and `zh_cn` today, so they're added to the other four
locales.

### New keys

| Key | English | Chinese |
| --- | --- | --- |
| `config.waypoint_bg_opacity` | Background opacity | 背景不透明度 |
| `config.section.rendering` | Waypoint rendering | 路径点渲染 |
| `config.section.map_mods` | Map mods | 地图模组 |
| `config.section.appearance` | Appearance | 外观 |
| `map_mod.xaeros_minimap` | Xaero's Minimap | Xaero的小地图 |
| `map_mod.voxelmap` | VoxelMap | VoxelMap |
| `config.map_mod.auto_sync` | %s: auto sync | %s：自动同步 |
| `config.map_mod.sync_now` | %s: sync now | %s：立即同步 |
| `config.map_mod.sync_button` | Sync… | 同步… |
| `config.map_mod.not_installed` | Not installed | 未安装 |
| `config.on` | On | 开 |
| `config.off` | Off | 关 |
| `config.unit.percent` | %% | %% |
| `config.unit.chunks` | chunks | 区块 |
| `config.value.percent` | %s%% | %s%% |
| `config.value.chunks` | %s chunks | %s 区块 |
| `config.default` | Default: %s | 默认值：%s |
| `config.reset` | Reset to default: %s | 恢复默认值：%s |
| `config.reset_all` | Reset to defaults… | 全部恢复默认… |
| `config.reset_all.title` | Reset settings? | 要恢复默认设置吗？ |
| `config.reset_all.body` | Waypoint rendering and map sync settings go back to their defaults. Your waypoints and color theme don't change. | 路径点渲染和地图同步设置将恢复为默认值。你的路径点和颜色主题不会改变。 |
| `config.reset_all.confirm` | Reset | 恢复 |
| `config.reset_all.done` | Settings reset to defaults. | 已恢复默认设置。 |
| `config.sync.title` | Sync to %s? | 要同步到%s吗？ |
| `config.sync.body` | Replaces the waypoints Server Waypoint added to %s with this server's current waypoints. | 用此服务器当前的路径点替换本模组添加到%s的路径点。 |
| `config.sync.stays` | What stays: | 保留的内容： |
| `config.sync.stays.detail` | Everything you created yourself, even with the same name as a server list. | 你自己创建的所有内容，即使与服务器列表同名。 |
| `config.sync.lost` | What is lost: | 丢失的内容： |
| `config.sync.lost.detail` | Changes you made to waypoints Server Waypoint added, and waypoints from lists removed on the server. | 你对本模组所添加路径点的修改，以及服务器上已删除列表中的路径点。 |
| `config.sync.done` | Synced waypoints to %s. | 已将路径点同步到%s。 |
| `config.sync.failed` | Couldn't sync to %s. See the game log. | 无法同步到%s。请查看游戏日志。 |
| `config.sync.no_world` | Join a world to sync. | 进入世界后才能同步。 |
| `config.sync.map_mod_loading` | %s is still loading. | %s仍在加载。 |
| `config.enable_waypoint_render.tooltip` | Draws waypoint markers in the world. Turning this off only hides them. | 在世界中绘制路径点标记。关闭后只会隐藏它们。 |
| `config.waypoint_scale_factor.tooltip` | Size of waypoint markers in the world, as a percentage of their normal size. | 路径点标记在世界中的大小，以正常大小的百分比表示。 |
| `config.waypoint_vertical_offset.tooltip` | Moves waypoint markers up or down by up to half a block. | 将路径点标记上移或下移，最多半格。 |
| `config.waypoint_bg_opacity.tooltip` | Opacity of waypoint marker backgrounds and icons, from 0 (clear) to 255 (solid). | 路径点标记背景和图标的不透明度，从 0（透明）到 255（不透明）。 |
| `config.local_waypoint_view_distance.tooltip` | Waypoints with local visibility are drawn only within this many chunks. Global waypoints are always drawn. | 可见范围为局部的路径点只在此区块数范围内绘制。全局路径点始终绘制。 |
| `config.map_mod.auto_sync.tooltip` | Keeps the waypoints Server Waypoint adds to %s up to date with the server. Your own waypoints are never changed. | 让本模组添加到%s的路径点与服务器保持同步。你自己的路径点永远不会被修改。 |
| `config.map_mod.not_installed.tooltip` | Install %s to sync this server's waypoints to it. | 安装%s后即可将此服务器的路径点同步到其中。 |
| `config.theme.tooltip` | Colors of Server Waypoint's screens. | 本模组界面的颜色。 |

- The "sync now" row's tooltip is `config.sync.body`.
- `%%` is the literal percent sign, in `config.unit.percent` as well as the value formats. A lone
  `%` is an invalid Minecraft format string.

### Removed keys

Removed from all six locales, because their meaning changed and the old text would be wrong:

- `config.waypoint_bg_alpha`, replaced by `config.waypoint_bg_opacity`. Some translations say
  "transparency", the opposite of what the setting does.
- `config.auto_sync_to_xaeros` and `config.sync_to_xaeros`, replaced by the `config.map_mod.*`
  patterns.
- `config.sync_to_xaeros.warn.1` to `.warn.5`, replaced by `config.sync.*`, because the old warning
  is no longer true.
- `config.true` and `config.false`, replaced by `config.on` and `config.off`.
  `TrueFalseToggleButton` is renamed `OnOffToggleButton`, including the theme editor's gallery
  toggle.

### Reused keys

- Done uses vanilla's `gui.done` (`CommonComponents.GUI_DONE`), which every language already has.
- Cancel uses `server_waypoint.cancel.button`.
- The waypoint-state blockers use the manager's `server_waypoint.manager.loading`,
  `server_waypoint.no_serverside_support` and `server_waypoint.incompatible_protocol_version`.

## 6. Documentation

- **This feature folder:** a README index, this spec, and `plans/` and `validation/` folders that
  hold a `.gitkeep` until they have documents. The folder is listed in `docs/features/README.md`.
- **GUI guide** (`tips/gui/local-guide.md`), updated in the same change as the code, as AGENTS.md
  requires:
  - A `SettingsListWidget` section, and a component-table row for scrollable settings rows with
    section headers.
  - `WidgetPack`'s cross-axis alignment option.
  - `ConfirmationDialog`'s confirm label and Cancel button accessor.
  - `MovementAllowedScreen`'s background when no world is loaded.
  - `IntegerSlider` number fields in the Input section's Escape rule (`isEditingNumber()`).
  - The `ClientConfigScreen` and theme-editor bullets, and `OnOffToggleButton` in the component
    table.
- **README and README_zh:**
  - The "Client Configurations" section: the new names and defaults, the VoxelMap rows, reset, and
    a corrected manual-sync description. The current one says personal Xaero's Minimap sets can be
    lost.
  - "Keybinds": the screen also opens from Mod Menu and the NeoForge and Forge mod lists.

## Constraints

- Java 17 and four-space indentation. Gradle changes use the Kotlin DSL: the Mod Menu dependency
  lines in `fabric.gradle.kts` and `fabric-unobfuscated.gradle.kts`, and the per-target `modmenu`
  properties.
- Every Stonecutter target from 1.20.1 to 26.3 must keep working. Tooltip scheduling follows the
  existing 1.21.6 branch, and the background change stays in `MovementAllowedScreen`'s existing
  version branches. No new swap or replacement is planned; if one becomes necessary, update the
  inventory in `AGENTS.md` in the same change.
- No backward-compatibility code, per AGENTS.md. Removed translation keys need no migration, and
  `TrueFalseToggleButton` and `syncXaerosMinimap` are replaced outright.
- The GUI probe (`tools/cross-server-gui-test/RemoteGuiProbe.java`) doesn't use this screen, so it
  needs no change.
- Commit only when asked, per AGENTS.md.

## Validation

### Unit tests

In the mods test source set:

- `SettingsListLayoutTest`: column widths, label width, entry offsets with header gaps, reveal
  scrolling at the top, middle and bottom, and full visibility.
- `WidgetPackTest`: `CENTER` cross-axis alignment, with and without padded children.
- `ClientConfigSettingsTest`:
  - Every default is inside its range.
  - A config parsed from `{}` is at its default for every setting.
  - A changed value isn't at its default.
  - Resetting an auto-sync setting restores it and leaves the manager's sort, grouping and
    all-dimensions state unchanged. The rendering setters can't run in a unit test:
    `OptimizedWaypointRenderer` reads `Minecraft.getInstance().font` when its class loads, so their
    resets are checked in game.
  - The settings of a missing mod are left out.
  - The formatted defaults are correct.
- `ClientConfigSyncTest`: every blocker in its order, and the `MapModRowState` mapping.
- `MapModIntegrationsTest`: `find` returns Xaero's Minimap on every loader and VoxelMap only on
  Fabric, and `syncNow` skips a map mod that isn't installed.
- `ClientConfigTranslationTest`: every key the screen uses exists in all six locales with the same
  placeholders as `en_us`, and the removed keys are gone from all six.
- `MovementAllowedScreenPopupEscapeTest`: Escape leaves a focused `IntegerSlider`'s number field
  before closing the screen, and closes the screen at once when the slider's track has focus.

### Gradle

- `:mods:26.1.2-fabric:test`, the active Stonecutter target.
- Compile every Fabric target, because each gets its own Mod Menu version.
- Compile the NeoForge targets where the factory API differs: 1.20.2, 1.20.4, 1.20.6, 1.21 and
  26.3.
- Compile Forge 1.20.1, 1.20.2, 1.21.11 and 26.2.
- `git diff --check`, and balanced Stonecutter markers in every touched file.

### In game

A manual pass on 26.1.2 Fabric, plus one NeoForge and one Forge target for the mod-list hooks, with
results recorded in `validation/`. Compiling can't prove these:

- The layout at GUI widths of 480, 378 and 320 pixels, including wrapped labels at 320, and at
  heights of 240 and 270 pixels, including scrolling.
- Tooltips after 500 ms, the reset icon's tooltip, and no tooltip through a dialog.
- Tab and Shift-Tab through every row while the list scrolls, and Enter and Space on focused
  buttons, including in the dialogs.
- The mouse wheel over a slider, with the list overflowing and not overflowing.
- Row reset and "Reset to defaults…", with the changes visible live in the world.
- Both sync dialogs: personal waypoints stay untouched, the status message appears, and each
  blocker shows on the title screen, while waypoints sync, and on a server without Server Waypoint.
- Escape and focus return for every dialog, and Escape in a number field.
- Opening the screen from each mod list, on the title screen and in game.
- A disconnect with the screen open keeps the changes.
- The three built-in themes.
