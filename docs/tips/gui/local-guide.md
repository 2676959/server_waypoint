# GUI Development Guide

This guide describes the GUI code shared by the Fabric, Forge, and NeoForge targets in the `mods` project. Read it before adding a widget or screen, changing render entry points, manually rendering widgets, or editing GUI-related Stonecutter branches.

Before implementing any GUI change, including a small bug fix, follow the mandatory
[component reuse check](#mandatory-component-reuse-check). Do not start with custom drawing code
until you have inspected the existing component that owns the required behavior.

## Scope and source location

The GUI source root is:

```text
mods/src/main/java/_959/server_waypoint/common/client/gui
```

The `common` segment in this Java package means loader-shared code inside the `mods` project. It is not the top-level `common` Gradle subproject. GUI code depends on Minecraft client classes and must remain in `mods`; only UI-neutral logic that is also needed by `paper` or another non-client consumer belongs in the top-level `common` project.

Keep new helpers at the narrowest useful scope:

- A helper used by one widget stays beside that widget, usually as a private method or nested type.
- A helper shared only by GUI code belongs in one of the `client/gui` packages below.
- A helper moves to the top-level `common` project only when it has real non-GUI consumers and no Minecraft client dependency.

## Package structure

| Package | Responsibility | Typical contents |
| --- | --- | --- |
| `client.gui.api` | Small GUI-facing contracts and callbacks | Button, toggle, color-picker, and dimension-selection callbacks; `Colorable` |
| `client.gui.layout` | Positioning, sizing, padding, and flow | `WidgetStack`, `WidgetPack`, `ExpandableManager`, `LayoutFlow`, `Padding`, `AnchorMode`, `VisualBounds` |
| `client.gui.render` | Cross-version drawing helpers, semantic theme state, and presentation constants | `DrawContextHelper`, `PaddingBackground`, `WidgetTheme`, `WidgetThemeManager`, `WaypointTextures` |
| `client.gui.screens` | Screen lifecycle and feature composition | Manager, add/edit, configuration, theme-editor, and movement-aware screens |
| `client.gui.widgets` | Reusable visible and interactive components | Buttons, fields, sliders, dialogs, color pickers, lists, and tree views |

Related resources live under:

```text
mods/src/main/resources/assets/server_waypoint/lang
mods/src/main/resources/assets/server_waypoint/textures/gui
```

Use translatable `Component` values for player-facing text. Add GUI textures to `textures/gui` and expose shared identifiers through `WaypointTextures` rather than scattering resource identifiers across widgets.

## How the packages work together

A screen normally composes the packages in this direction:

```text
screen
  -> widgets for display and input
  -> layout objects for position and size
  -> render helpers for version-compatible drawing
  -> callbacks to pass user actions back to the screen or feature
```

Keep domain operations out of generic widgets when possible. A widget should report an action through a callback or a small UI-neutral value. The screen or client feature can then update configuration, send a command, or change screens.

Three responsibilities must be handled separately:

1. **Layout** decides widget positions and dimensions.
2. **Screen registration** gives interactive widgets input, focus, and narration through `addRenderableWidget`.
3. **Rendering** decides what is drawn and in which layer/order.

Adding a child to `WidgetStack` or `ExpandableManager` does not automatically register it with the screen. Likewise, registering a widget does not make a manually rendered screen draw it if that screen does not call the vanilla screen renderer. New screens must deliberately cover all three responsibilities.

## `api`: callbacks and small contracts

Use the existing interfaces when their meaning matches the new component:

- `ButtonClickCallback` reports a button click.
- `ToggleButtonCallback` reports the new boolean state.
- `ColorPickerCallback` reports an updated ARGB/RGB value.
- `DimensionListCallback` reports a selected dimension name.
- `Colorable` standardizes `getColor` and `setColor` for color-aware widgets.

Keep callbacks small and synchronous. Prefer passing the callback into a widget constructor, as `TranslucentButton`, `ToggleButton`, and `DimensionListWidget` do. Do not make a reusable widget reach into a particular screen through static fields merely to report an ordinary click or value change.

## `layout`: coordinates, padding, and containers

### Shifted coordinates

`Shiftable`, `ShiftableWidget`, and `ShiftableClickableWidget` separate a widget's base position from an offset. This is useful for building a layout at `(0, 0)` and moving the complete layout later.

- `setX` and `setY` update the base position.
- `setXOffset` and `setYOffset` move the displayed position relative to that base.
- `getShiftedX` and `getShiftedY` return the displayed position.

Widgets derived from the shiftable bases expose the shifted position through `getX` and `getY`. Initialize the shifted coordinates by calling `setX` and `setY` in a new clickable widget's constructor. When overriding position methods, update both the anchor/base value and the shifted value; `TranslucentButton` and `TranslucentTextField` are the reference implementations.

### Content bounds and visual bounds

Some widgets draw borders or backgrounds outside their Minecraft content rectangle. The project models their outer rectangle with `Padding`:

- `getX`, `getY`, `getWidth`, and `getHeight` describe the content bounds used by the widget.
- `getVisualX`, `getVisualY`, `getVisualWidth`, and `getVisualHeight` describe the complete drawn bounds, including padding or outlines.
- `VisualBounds` converts between content and visual dimensions for fixed padding.
- `PaddingBackground` draws a padded background and optionally a border around a `LayoutElement`.

Implement `Padding` whenever pixels extend outside the content bounds and the widget may participate in layout. If the widget can be resized by a container, also implement `Expandable` and make `setVisualWidth`, `setVisualHeight`, or `setVisualDimensions` convert the requested outer size back to the content size.

`AnchorMode` determines what constructor and setter coordinates mean for padded widgets:

- `CONTENT` preserves the historical behavior: the supplied coordinate anchors the content rectangle.
- `OUTLINE` anchors the outer outline/visual rectangle.

Existing constructors default to `CONTENT` for compatibility. For new edge-aligned or responsive layouts, `OUTLINE` is usually clearer because the layout coordinate matches the visible edge. Choose the mode explicitly when the distinction matters; do not change the default globally.

### Choosing a container

Use vanilla `SpacerElement.width(...)` or `SpacerElement.height(...)` for an explicit non-rendering
gap between layout children. Do not use an empty layout container as a spacer.

Use `WidgetStack` for intrinsic, fixed-size rows or columns:

- It stacks children horizontally or vertically in `FORWARD` or `REVERSE` order.
- Each child keeps its existing size.
- The `pdx` value is spacing inserted before that child along the main axis.
- `setOffsets` can move the whole stack after construction.
- `addClickable` also adds a child to the stack's internal click-routing list; `addChild` only lays out and renders it.

Use `addClickable` primarily inside a composite that deliberately forwards clicks, such as `DialogWidget`. A screen should still register each interactive child with `addRenderableWidget`.

The enum constructor keeps legacy content-bound behavior by default:

```java
WidgetStack row = new WidgetStack(
        0,
        0,
        8,
        LayoutFlow.Orientation.HORIZONTAL,
        LayoutFlow.Direction.FORWARD
);
```

When padded children must be aligned and measured by their visible edges, use `addPadded`/`addPaddedClickable`, or opt the stack into visual bounds with the constructor's `useVisualBounds` argument. Do not silently convert existing stacks: many old layouts intentionally use content bounds.

Use `ExpandableManager` for responsive layouts that must consume available width or height:

- A ratio of `0` on the stacking axis keeps the child's current visual size fixed.
- Positive ratios split the space remaining after fixed children are measured.
- A positive ratio on the cross axis fills that axis; the exact positive value is not weighted there.
- Nested managers relayout their children when the parent changes their size.
- Padded children are measured and positioned by visual bounds.

`ExpandableManager` is layout-only and does not render its children. Register and render the managed widgets separately, as `WaypointManagerScreen` does.

For example, a fixed-width rail next to a flexible main panel is:

```java
ExpandableManager layout = new ExpandableManager(
        0,
        0,
        LayoutFlow.Orientation.HORIZONTAL,
        LayoutFlow.Direction.FORWARD
);
layout.addChild(dimensionRail, 0, 1);
layout.addChild(mainPanel, 1, 1);
```

Use `WidgetPack` when a fixed-size area must accept children from both ends of one axis. Each child
chooses `LayoutFlow.Direction.FORWARD` or `LayoutFlow.Direction.REVERSE`: these mean left/right in a
horizontal pack and top/bottom in a vertical pack. Children accumulate inward from their selected
side, use visual bounds when padded, and never change the pack's dimensions. Resize the pack
explicitly through its `Expandable` API; resizing or moving it relayouts both anchored groups.

```java
WidgetPack toolbar = new WidgetPack(
        0,
        0,
        availableWidth,
        buttonHeight,
        LayoutFlow.Orientation.HORIZONTAL
);
toolbar.addChild(backButton, LayoutFlow.Direction.FORWARD);
toolbar.addChild(doneButton, LayoutFlow.Direction.REVERSE);
```

Like `ExpandableManager`, `WidgetPack` is layout-only. Its children still need to be registered for
input and rendered by their owning screen or composite.

Use `DimensionIconLayout` only for the geometry of an oriented, scrollable dimension-icon strip.
It is a pure layout helper, not a widget or input handler. Its optional non-negative icon spacing
participates in positioning and scroll extent; spacing gaps are deliberately non-interactive.
Legacy `DimensionListWidget` constructors preserve zero icon spacing, while every pre-existing
constructor retains 3-pixel vertical padding and 4-pixel horizontal padding. Its full overload lets
a particular screen opt into non-negative icon spacing and symmetric vertical/horizontal padding;
use zero padding for a strip whose parent already owns the complete outer inset.

### Directional dropdown menus

Extend `AbstractDropdownMenuWidget` for a control whose popup can grow along any `LayoutFlow`:

| Orientation | Direction | Popup growth |
| --- | --- | --- |
| `HORIZONTAL` | `FORWARD` | Right |
| `HORIZONTAL` | `REVERSE` | Left |
| `VERTICAL` | `FORWARD` | Down |
| `VERTICAL` | `REVERSE` | Up |

Add choices by extending its nested `AbstractMenuItem` class and calling `addMenuItem` from the
concrete dropdown constructor. A menu item owns its complete renderer, so it may draw text, icons,
or richer content rather than being restricted to a label. The dropdown routes left clicks and
direction-aware arrow-key selection to those internal choices; they are not separately registered
or focused by the screen. Items are centered on the trigger's cross axis and retain logical
insertion order even when expanding in reverse.

If a concrete dropdown tracks a selected value, override `getSelectedMenuItemIndex()`. The selected
item is represented by the dropdown control and omitted from the popup; the remaining visible
items are compacted in logical insertion order. Use `getPopupItemCount()` when positioning the
popup based on its displayed size. The initial keyboard highlight starts at the first selectable
remaining choice and skips hidden or inactive items. Opening the popup with the mouse does not
pre-highlight an item; arrow-key navigation starts from the first choice in that direction.

The dropdown is a single registered composite: register the dropdown, not its menu items, and
render it exactly once through the screen's rendering owner. A `MovementAllowedScreen` calls its
high-level wrapper explicitly; a normal vanilla-rendered screen may already render registered
widgets. The dropdown forwards rendering through each item's high-level wrapper and expands
`isMouseOver` to cover visible popup items. That expanded hit area is required because newer
Minecraft screens resolve a child by `isMouseOver` before dispatching the click.

When popup items may overlap another registered widget, route the open dropdown before
`super.mouseClicked`; render layers do not define input priority. For an outside click, call
`closeMenuIfOutside` and then continue normal dispatch so the same click can reach its target. To
make Escape close the menu instead of the screen, call `closeMenuIfOpen` and return before
`super.keyPressed`, because vanilla handles Escape before forwarding keys to the focused child.

Use `InputConstants.KEY_*` and `InputConstants.MOUSE_BUTTON_*` for input comparisons and test
events. Minecraft 26.3 uses SDL codes, so raw GLFW values and numeric mouse buttons no longer
match the dispatched input. A `KeyEvent` carries its secondary code as `keycode()` on 26.3 and
`scancode()` on earlier targets; keep that accessor behind a Stonecutter predicate.

`ComboBoxWidget` combines editable text with a separately opened list of choices. It reuses
`SuggestingTextInput` for cursor movement, selection, clipboard shortcuts, text rendering, and
completion (also shared by `TranslucentTextField`), and
`AbstractDropdownMenuWidget` for the popup. Register only the composite; it owns its internal field's
position, size, focus, input, and rendering. Click the text area to edit and the arrow to toggle the
list. Arbitrary text is accepted, including values absent from the list. Choosing an item replaces
the field text. Enter opens/selects, Up/Down navigates the open popup, and typing or losing focus
closes it. Text scrolls with the cursor; popup labels are clipped. The control and choice rows do
not show hover tooltips because their values are already presented by the input and suggestions.

Pass choices, initial text, field label, font, and `Consumer<String>` to the constructor. Duplicate
choices are removed in insertion order. `getValue` returns the current text; `setValue` accepts any
non-null text without calling the callback. `setValues` replaces the popup choices, closes an open
popup, and preserves the current text without invoking the callback. User edits and popup selections invoke the callback.
Suggestions default to the current choices; `setSuggestionsProvider(Supplier<List<String>>)` can
supply a separate dynamic catalog, and `null` disables suggestions. Matching is case-insensitive
prefix matching, deduplicated and sorted, with an inline suffix and up to five popup rows.
Up/Down selects a suggestion and Tab/Shift-Tab accepts/cycles completions. Clicking a suggestion
also accepts it through the normal user-change callback. The full choice popup suppresses
suggestions while open. `renderPopup(...)` draws whichever popup is active, including when rendered
separately. Route popup clicks before overlapping controls using `isMouseOver(...)`, and call
`closeSuggestionsIfOpen()` after `closeMenuIfOpen()` when intercepting Escape at screen level.

`SuggestingTextInput` is the reusable surface-free input base. It owns editing, shifted layout,
completion state, inline text, and suggestion rendering/hit testing; `TranslucentTextField` adds
only its themed surface. Composites can override `getSuggestionsX()`, `getSuggestionsY()`, and
`getSuggestionsWidth(int maxTextWidth)` to anchor suggestions to their outer bounds. The combobox
uses its full control width, including the arrow area, and clips suggestion text inside that outline.
Drawing and hit testing use the same bounds. Use `setSuggestionsEnabled(...)` to temporarily suppress completion without
losing focus. `refreshSuggestions()` invalidates a completion cycle after catalog changes; replacing
the provider also refreshes it. `renderSuggestions(...)` remains an explicit overlay pass for standalone inputs.
Escape dismissal persists until editing or refocusing, and disabled/hidden inputs do not accept
suggestion clicks. `AbstractDropdownMenuWidget.renderPopup(...)` may be overridden to provide
another popup when the full menu is closed; preserve its separate-rendering contract.

An exact matching choice is omitted from the popup. Resizing also resizes the field and choice rows.
Combobox popup rows draw side and bottom borders; the preceding control or row supplies the
shared top edge, keeping separators one pixel thick without overlapping row hit areas.
`DrawContextHelper.renderOutlineWithoutTop(...)` provides this three-sided outline for both
combobox and theme-selector popup rows; use it when the preceding row owns the top separator.
The constructor and position setters anchor the text, matching standalone text inputs and labels.
The widget derives its default height from `font.lineHeight + 2`, matching `TranslucentTextField`,
and places the outline two pixels above and left of the text. Callers supply width, not height.
`getX`/`getY` and their shifted equivalents report the outer control position used for drawing,
hit testing, and popup placement. Offsets and height changes preserve the text anchor.
Width and height describe the complete control, including its outline;
the popup is excluded from layout dimensions.
Treat a focused combobox as text entry when deciding whether to forward movement keys, as
`AbstractWaypointPropertiesScreen` does.

When a dropdown appears early in a manually rendered layout, call
`setRenderPopupSeparately(true)` and then `renderPopup(...)` once after the other controls.
The default still renders the popup with its control. This prevents later controls from covering
popup choices, including on newer render strata APIs. `AbstractWaypointPropertiesScreen` exposes
`renderTitleRowOverlays(...)` after suggestions and before the swatch for this purpose.
`WaypointAddScreen` uses that hook for its dimension combobox, populated from the same complete
integrated-server or remote-suggestion dimension catalog as `WaypointManagerScreen`, with the
supplied starting dimension retained. List and waypoint-name
suggestions and submission read the current selection. Popup clicks have priority over overlapping
fields, outside clicks continue to their targets, and Escape closes the popup before the screen.

## `render`: drawing and presentation

Use `DrawContextHelper` for drawing operations whose Minecraft API changes across supported versions. It centralizes text, texture, item, matrix, layer, outline, and custom-quad differences. Before adding a new Stonecutter branch at every call site, check whether the difference belongs in this helper.

Use the other render classes as follows:

- `WidgetThemeVariable`, `WidgetTheme`, `WidgetThemes`, and `WidgetThemeManager` define and expose the runtime color theme.
- `WidgetThemeColors` is a compatibility facade over `WidgetThemeManager`; it does not hold fixed color constants.
- `WidgetThemeJson` serializes complete theme snapshots and performs file persistence with GSON.
- `WidgetTextures` owns identifiers for reusable GUI icons, including the Waypoint Manager's
  single/all-dimension scopes, flat/grouped display modes, default/name/distance/color sort modes,
  and ascending/descending sort directions.
- `PaddingBackground` renders the background/outline associated with a padded widget or layout.
- Small pure presentation calculations, such as `WaypointSortButtonLabel`, belong here when they are reusable and easy to unit test.

Rendering helpers should not own screen navigation, networking, or feature state.

### Runtime color themes

Theme colors are semantic ARGB roles, not widget-specific constants. Choose the role by what a pixel means:

- Text uses roles such as `TEXT_PRIMARY`, `TEXT_MUTED`, `TEXT_DISABLED`, and `TEXT_ON_ACCENT`.
- Surfaces use `SCREEN_BACKGROUND`, `PANEL_BACKGROUND`, `POPUP_BACKGROUND`, `DIALOG_BACKGROUND`,
  and the control background roles.
- Interaction states use `FOCUS_RING`, `SELECTION_BACKGROUND`, `ROW_HOVER_BACKGROUND`, and the scrollbar roles.
- Feedback uses `SUCCESS`, `WARNING`, `DANGER`, and their background variants.

These roles apply to GUI chrome and state. A waypoint's user-selected color is domain data, and RGB/HSV picker gradients visualize a color space; those values can remain direct colors rather than theme roles.

`WidgetThemeVariable.getJsonName()` is the stable external name for a role. Use it in JSON and derive the matching translation key as `server_waypoint.theme.variable.<jsonName>`. Do not persist enum names or introduce a raw color constant when an existing semantic role already fits.

`WidgetTheme` is an immutable, complete snapshot. A builder created with `WidgetTheme.builder()` must assign every variable before `build()`. For a partial change, start from an existing theme or use `withColor`:

`WidgetThemes.DEFAULT` points to `WidgetThemes.TRANSLUCENT_DARK`, the built-in neutral grayscale
glass palette. The screen overlay is 65% opaque and panels are 70% opaque, preserving a
view of the world while limiting bright-scene washout. Controls use 85–90% opacity and
lighter charcoal fills so their shape remains visible at night. Opaque gray borders and a
brighter focus ring distinguish control boundaries, hover, and keyboard focus. Popups are
98% opaque and dialogs 99% opaque to suppress underlying labels showing through. Accent
fills are dark gray with light text; selected rows have a stronger neutral fill. Success,
warning, and danger use muted sage, amber, and dusty red foregrounds and backgrounds.
Validate text and control contrast after compositing the screen, panel, and control over
both black and white world backgrounds; checking raw RGB against black misses daylight failures.
`WidgetThemes.MODERN_DARK` follows the same opacity progression with blue-gray surfaces,
slate borders, and cyan focus highlights. Its accent and selected fills use dark teal to keep
light labels readable, including the hovered accent state. Green, amber, and rose status text
sits on darker matching fills. Both palettes are checked over black, white, and sky-blue
world backgrounds, including status text on its matching background. Glassmorphism remains
the shared design style; text, accents, and focus outlines stay crisp.

`WidgetThemes.HIGH_CONTRAST` uses stronger black and near-black glass tints, white primary
text and borders, a yellow focus ring, and cyan accents. Its screen overlay is roughly 30%
opaque, panels 50%, popups 60%, and dialogs 80%. Controls and status fills also remain
translucent; contrast varies with the world behind the glass. Selected controls and status backgrounds stay dark to support white
`TEXT_ON_ACCENT`; muted, disabled, and placeholder text remain bright enough to read. Apply the
preset through the editor dropdown or `WidgetThemeManager.setTheme(WidgetThemes.HIGH_CONTRAST)`;
it does not change the default theme.

`DIALOG_BACKGROUND` is darker and more opaque than `POPUP_BACKGROUND` because `DialogWidget`
always renders above other controls. Keep suggestion lists, color-picker popups, and other
non-modal floating surfaces on `POPUP_BACKGROUND`. Hovered controls and rows use lighter
translucent gray fills than their resting surfaces so hover remains visible against the dark theme.

```java
WidgetTheme updatedTheme = WidgetTheme.builder(WidgetThemeManager.getTheme())
        .setColor(WidgetThemeVariable.ACCENT, 0xFF5BC3DF)
        .build();

WidgetThemeManager.setTheme(updatedTheme);
```

`WidgetThemeManager` owns the active immutable snapshot and supports whole-theme replacement,
single-color changes, atomic `updateTheme` operations, and reset to `WidgetThemes.DEFAULT`.

Resolve theme values when drawing so an open screen reacts immediately to a runtime update:

```java
int backgroundColor = WidgetThemeManager.getColor(
        isHovered()
                ? WidgetThemeVariable.CONTROL_HOVER_BACKGROUND
                : WidgetThemeVariable.CONTROL_BACKGROUND
);
context.fill(x, y, x + width, y + height, backgroundColor);
```

Do not cache a resolved color in a `static final int` or a long-lived widget field. When an API needs a deferred value, use `WidgetThemeManager.getColorSupplier`. Inside the widgets package, reuse `WidgetThemeState` for the standard active/disabled, hover, focus, and text combinations. Components such as `ScalableText` should retain a `WidgetThemeVariable`, not a resolved integer, when they need live updates.

When adding a theme variable, update all of these together:

1. `WidgetThemeVariable`, including its unique JSON name.
2. Every built-in theme in `WidgetThemes`.
3. The English and Chinese `server_waypoint.theme.variable.<jsonName>` translations.
4. Theme completeness, JSON, and translation-coverage tests.

### Theme JSON persistence

`WidgetThemeJson` is the only theme JSON/file-I/O seam. The current format uses `formatVersion: 1`, a `colors` object, semantic JSON names, and `#AARRGGBB` strings:

```json
{
  "formatVersion": 1,
  "colors": {
    "text.primary": "#FFE8F0F7",
    "background.screen": "#FF0B1016",
    "accent.default": "#FF5BC3DF"
  }
}
```

The parser also accepts `#RRGGBB` and supplies opaque alpha. Missing known roles inherit from the fallback theme, and unknown roles are ignored for forward compatibility. Invalid structure, versions, or color strings fail parsing instead of silently applying a damaged theme. Serialization writes every known role.

Use `toJson`/`fromJson` for strings, `save`/`load` for snapshots, `saveCurrent` for the active theme, and `loadAndApply` when a successfully loaded theme should become active. `save` writes through a temporary file and then replaces the destination. In normal client startup, `WaypointClientMod` loads:

```text
config/server_waypoint/widget-theme.json
```

A missing file leaves `WidgetThemes.DEFAULT` active. An unreadable or invalid file is logged and
the manager is reset to `WidgetThemes.DEFAULT`.

## `widgets`: choosing and extending components

### Mandatory component reuse check

Before adding or changing GUI rendering, layout, or interaction behavior:

1. Identify the behavior needed and consult the component table below.
2. Search `client/gui/widgets`, `client/gui/layout`, and `client/gui/render` for an existing
   implementation. Read the relevant component's API and implementation, plus an existing usage
   when available. A nearby screen's custom drawing code is not sufficient evidence that a reusable
   component is missing.
3. Reuse the existing component when it provides the behavior. Do not duplicate its wrapping,
   scaling, layout, clipping, hover, or input logic in a screen, callback, or private helper.
4. If reuse cannot satisfy a concrete requirement, identify the missing capability before writing
   custom code. Prefer a focused extension to the owning component when the capability belongs
   there, and update this guide for any API change. When specialized rendering must remain local,
   document the requirement and why the existing component cannot handle it near that code.

This check applies to small fixes as well as new features. Convenience, fewer lines, or copying an
existing low-level renderer is not a reason to bypass a suitable reusable component.

### Text labels and wrapped messages

Use `ScalableText` for standalone labels and explanatory, status, or empty-state messages that
need scaling or wrapping. In particular, messages such as "No remote servers are cached…" must
use `ScalableText`; do not add a local `Font.split(...)` loop and repeated `drawText(...)` calls.

- Create and retain the `ScalableText` instance with the owning screen or widget, rather than
  constructing it on every render.
- Set its maximum width to the actual available content width after padding and scrollbar space.
  Update that width when layout or viewport size changes, and use `setText` when the message changes.
- Use a semantic theme role or color supplier so it follows theme changes.
- Render it once through `render_method_swap` in the owner's coordinate space. It is
  non-interactive and does not require separate input registration.

Direct text drawing remains appropriate inside the text widget itself and for specialized row or
document renderers whose per-run formatting or geometry cannot be expressed by `ScalableText`.
Those cases do not justify duplicating standalone message rendering elsewhere.

### Component selection

| Need | Start with |
| --- | --- |
| Text label, optional scaling/wrapping | `ScalableText` |
| Text action | `TranslucentButton` |
| Icon action | `IconButton` |
| Boolean state | `ToggleButton` or `TrueFalseToggleButton` |
| Text input with optional suggestions | `TranslucentTextField` |
| Editable text with a popup choice list | `ComboBoxWidget` |
| Bounded integer input | `IntegerField` |
| Absolute/relative/local coordinate input | `CoordinateField` |
| Integer slider plus field | `IntegerSlider` |
| Hex color input | `ColorHexCodeField` |
| Color selection | `ColorSquareButton`, `SwatchWidget`, `RGBColorPicker`, or `HSVColorPicker` |
| Scrollable hierarchical rows | Extend `TreeViewWidget<T>` |
| Dimension icon strip | `DimensionListWidget` |
| Directional popup with custom items | Extend `AbstractDropdownMenuWidget` and `AbstractMenuItem` |
| Confirmation overlay | `ConfirmationDialog` |

`IconButton` keeps its full configured hitbox while drawing its texture with a 2-pixel inner inset.
Screen-local icon controls should use the same inset so adjacent icon actions remain visually consistent.

The main base classes have distinct roles:

- Extend `ShiftableWidget` for a non-interactive `LayoutElement`/`Renderable`.
- Extend `ShiftableClickableWidget` for a normal `AbstractWidget` with input.
- Extend `ShiftableScrollableWidget` when the widget has a vertically scrollable viewport.
- Extend `TreeViewWidget<T>` when the content is a flattened visible view of expandable hierarchical data. Implement child lookup, expansion state, empty rendering, and row rendering; the base class handles scroll bounds, hit testing, visible-row calculation, clipping, and scrollbar drawing.

`WaypointListWidget` supports both one-dimension and all-dimensions query scopes. Call
`setShowAllDimensions(true)` to use `WaypointQueryEngine.queryAll`; grouped mode then renders
dimension roots above list and waypoint rows, while flat mode retains each row's dimension so it
can show the dimension on the first line and the list plus waypoint name on the second, without slash
separators. Waypoints in the player's current dimension show their 3D distance in a shared aligned
column, displayed as whole meters below one kilometer and compact one-decimal kilometers at longer
ranges. Cross-dimension distance labels are enabled only between the vanilla Overworld and Nether,
use the waypoint dimension's display color, and use the same coordinate conversion as distance
sorting. End and modded dimension labels remain hidden unless the player is currently in that
dimension. In an all-dimensions flat distance sort, waypoints from the current and convertible
dimensions are sorted first. Other dimensions follow in `dimensionNameComparator` order while
preserving their default waypoint order.
Dimension, list, and distance metadata render smaller than the waypoint name. Row actions must use
the retained dimension rather than the sidebar's selected dimension. The displayed dimension omits
the `minecraft:` namespace for vanilla dimensions but preserves namespaces for modded dimensions,
and uses the shared dimension-color mapping in both grouped dimension roots and flat waypoint rows
while list metadata remains muted. The owning screen must disable `DimensionListWidget` while the
all-dimensions scope is active and reapply that disabled state after layout or screen reinitialization.

`WaypointListWidget` reports row-body selection through the `Consumer<WaypointSelection>` supplied
to its constructor. Its action columns remain independent: visibility, edit, and remove clicks do
not replace the current selection. `WaypointSelection` carries the source dimension, plain and
display list names, and live waypoint; the widget reconciles that identity after queries so sorting
and refreshes preserve a still-visible selection while filtering or removal clears it.
`WaypointDetailsWidget` consumes that selection and presents every stored waypoint field plus its
dimension/list context in a separately scrollable viewport. Keep formatted display names and
descriptions parsed only at this render boundary, and reserve scrollbar width while wrapping so
content does not relayout when overflow begins. The dimension row uses the same shared
dimension-color mapping as the waypoint list, and boolean render state uses the theme's semantic
`SUCCESS` / `DANGER` text roles. `WaypointManagerScreen` always renders this panel and centers the
left rail, middle waypoint list, and right details panel as one group with two-pixel inter-panel
gaps. Calculate the middle content width as 38% of the scaled viewport clamped to 180–360 pixels,
and the details content width as 32% clamped to 150–320 pixels. When their combined desired width
exceeds the space inside the 12-pixel screen margins, proportionally shrink both while keeping all
three panels visible. Content height uses 82% of the viewport clamped to 120–400 pixels and the
available vertical margins. Resize the search field and waypoint list by visual width together with
their panels so drawing and hitboxes continue to match the calculated geometry.

The manager's sidebar `HOME_ICON` / `LAN_SERVERS_ICON` toggle switches its middle list and right
details panel between current-server and remote waypoints without opening another screen.
`RemoteWaypointPanel` is a package-private screen composition helper: the manager registers its
widgets once, supplies layout and visibility, forwards ticks, and owns its manual render pass.
Search, list/flat mode, name/color/default sorting, and sort direction control both views. Remote
flat mode sorts across servers while retaining server, dimension, list, and waypoint identity;
grouped mode uses server/dimension/list roots. Remote distance sorting is unavailable because there
is no shared player origin across servers (entering remote mode from distance sorting selects name).
The local dimension scope and add controls are disabled while remote waypoints are shown.
Remote data stays in `RemoteClientCatalogs`; no remote row creates a local waypoint or mutation
handle. The remote details remain read-only. The teleport button sends immediately without a
confirmation dialog, after rechecking session, catalog revision, exact identity, and waypoint data.
Both lists use 20-pixel rows and `WaypointRowRenderer.background(...)` / `initials(...)` for waypoint
color washes, hover/selection outlines and initials badges. The initials method returns the badge
width for label placement. The remote tree reuses `WidgetTextures` expand/collapse/empty icons,
formatted display names and the tree's clipping and hit testing; exact identities remain in tooltips.
Remote identity tooltips use the current hovered entry and vanilla cursor positioning after the
panel render pass, rather than attaching a widget tooltip to the entire tree rectangle.
Switching
views preserves separate local and remote selection/scroll state; filtering/removal clears an
invisible remote selection. Remote scope is owned by the screen instance, not persisted to disk.

Waypoint and waypoint-list `name` fields are always unformatted plain-text identities used by
commands, lookup, sorting, searching, initials, and external map integrations. They are also the
default display names. An optional serialized `display_name` override retains raw Minecraft JSON
input and must be converted with `TextHelper.parseFormattedText(...)` only at the client render boundary. `WaypointListWidget`
renders these display components directly and exposes a waypoint's optional formatted description
as its row tooltip. `OptimizedWaypointRenderer` keeps parsed display components in its render-thread
snapshot and draws up to six wrapped description lines below the hovered HUD label. Xaero's Minimap
and VoxelMap must continue to receive the plain `name` because they cannot render formatted text.

Composite widgets must forward all relevant behavior to their children: position and offsets, dimensions where supported, rendering, focus, input, and `visitWidgets`. `DialogWidget`, `IntegerSlider`, and `SwatchWidget` are useful references.

### New interactive widget checklist

1. Put it in `client.gui.widgets` unless it is private to a single screen.
2. Extend the narrowest base class.
3. Accept callbacks and initial state through the constructor.
4. Implement `Expandable` if a parent may resize it.
5. Implement `Padding` if its actual drawing extends beyond its content bounds.
6. Override the low-level widget renderer through `render_widget_method_swap`.
7. Use `DrawContextHelper` and semantic `WidgetThemeVariable` values for shared drawing behavior.
8. Keep hit testing consistent with the intended interactive bounds.
9. Implement meaningful narration when practical.
10. Add focused unit tests for pure geometry, layout, parsing, or state transitions.

A minimal interactive widget follows this shape:

```java
//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.ButtonClickCallback;
import _959.server_waypoint.common.client.gui.layout.Expandable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class ExampleButton extends ShiftableClickableWidget implements Expandable {
    private final ButtonClickCallback callback;

    public ExampleButton(int x, int y, int width, int height, Component message, ButtonClickCallback callback) {
        super(x, y, width, height, message);
        this.callback = callback;
        this.setX(x);
        this.setY(y);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.callback.onClick();
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        // Draw content using getX(), getY(), width, height, and prepared hover/focus state.
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // Describe the control and its state.
    }
}
```

If this widget draws outside `x/y/width/height`, add a `VisualBounds` constant and implement the complete `Padding` contract instead of compensating with unexplained offsets in every screen.

## `screens`: lifecycle and composition

### Choosing a base screen

- Extend `MovementAllowedScreen` for the normal in-world GUI behavior used by this project. It provides the themed `SCREEN_BACKGROUND`, centering helpers, and optional movement-key forwarding.
- Extend `AbstractWaypointPropertiesScreen` for a new add/edit-style waypoint properties flow. Shared fields, coordinate rules, suggestions, color selection, layout, and overlay behavior belong in this base; subclasses provide the title row, action row, and operation-specific action. Pass `showDisplayNameField = true` only for edit flows that expose the identifier and formatted display-name override separately. Override `onSwatchClosed()` when modal restoration must reapply operation-specific disabled states after the base screen re-enables its controls.
- Extend vanilla `Screen` directly only if movement forwarding and the shared centering contract are deliberately not wanted.

Use current screens as focused examples:

- `WaypointManagerScreen` demonstrates nested `ExpandableManager` layouts, fixed and flexible children, a `TreeViewWidget`, sorting controls, and responsive resizing.
- `WaypointManagerScreen` forwards screen ticks to `WaypointListWidget.refreshDistanceSortIfPlayerMoved()`. The widget caches the last query origin and only rebuilds distance-sorted rows after the player's block position or relevant dimension changes.
- `WaypointManagerScreen` separates full refreshes, dimension-list changes, and ordinary waypoint mutations. `updateAllWidgets()` rebuilds the dimension rail and refreshes waypoint rows exactly once. `updateWidgetsForDimensionListChange(...)` rebuilds the dimension rail but refreshes waypoint rows only when selection fallback or the active viewing scope requires it. `updateWaypointWidget(...)` skips dimension-name copying and sorting entirely, and it ignores changes outside the selected dimension unless all-dimensions mode is active. The selection is preserved by name and falls back to the current or first available dimension. Callers report the changed dimension instead of passing waypoint-list snapshots because `WaypointListWidget` owns the active search, sort, grouping, and dimension-scope query state.
- The manager's dimension rail includes empty dimensions that have no synchronized waypoint file. In an integrated world it reads the integrated server's level keys directly. On a remote connection it asynchronously extracts fully namespaced dimension identifiers from the `/wp list ` command suggestions, merges them with the synchronized client cache as a fallback, and ignores the command's literal list/search/sort options.
- In all-dimensions mode, the waypoint-list scroll position and grouped dimension-node expansion choices are session-scoped static widget state, so both survive closing and reopening the manager as well as ordinary dimension changes. Scroll restoration is deferred until the reconstructed widget has rows and a real maximum scroll range. Selected-dimension mode never remembers scroll and resets to the top when its scope is selected. `WaypointClientMod.onJoinServer()` calls `WaypointManagerScreen.resetSessionWidgetStates()` so connecting to another server or opening another local save also starts at the top with every dimension expanded.
- `AbstractWaypointPropertiesScreen`, `WaypointAddScreen`, and `WaypointEditScreen` demonstrate shared form behavior, `WidgetStack` rows, suggestion fields, and subclass extension points. `WaypointEditScreen` captures the list revision, tracks an explicit display-name clear state, submits one atomic edit payload, and keeps entered values until a matching server result accepts the edit. Client transport reset and handshake paths must call `WaypointEditScreen.handleTransportReset()` so a lost correlated result cannot leave the update action disabled. The add screen treats its name field only as the exact identifier and creates no display-name override.
- `ClientConfigScreen` demonstrates a scrollable `TreeViewWidget` of configuration rows and a modal `ConfirmationDialog` that disables the underlying controls.
- `WidgetThemeConfigScreen` demonstrates a live-preview editing transaction, a two-column theme-variable editor, a screen-local widget gallery, separate RGB/opacity controls, and a modal `SwatchWidget`.

### Recommended screen lifecycle

#### 1. Constructor: create stable state

Construct widgets, callbacks, and layout relationships that do not depend on the current window size. Prefer fields for controls whose state must survive `init` calls. Do not perform network actions merely because the screen object was constructed.

#### 2. `init`: size, position, and register

Call `super.init()` first. Then:

- Recalculate responsive layout dimensions from `this.width` and `this.height`.
- Position the root layout, usually with `getCenteredX` and `getCenteredY`.
- Register every interactive widget with `addRenderableWidget`.
- Register interactive children of composites through `visitWidgets` when appropriate.
- Restore focus or other state that must survive a resize/reinitialization.

Layout containers do not replace screen registration.

#### 3. Render: use one rendering owner

`MovementAllowedScreen` owns the final cross-version screen render entry point and the themed full-screen background. Its subclasses implement `renderScreenContents` and must render every visible content element themselves, usually through a root `WidgetStack` or explicit widget calls. Do not override the high-level screen render method in those subclasses or draw a second full-screen background.

Do not both render a widget through a container and render it again explicitly. Use a predictable order:

1. The themed screen background, owned by `MovementAllowedScreen`.
2. Screen-specific panels and main content.
3. Text-field suggestion lists.
4. Modal or color-picker overlays on a later layer.

Use `nextLayer`/`previousLayer` around suggestions and overlays when they must appear above normal controls.

#### 4. Input: preserve focus and text entry

Let registered widgets receive ordinary input through the screen. Intercept only behavior the screen must prioritize:

- Suggestion clicks must be checked before delegating to `super.mouseClicked`.
- Screen shortcuts should normally be disabled while the focused listener is an `EditBox`.
- Call `acceptMovementKeys(false)` while text entry or another control must own movement-key input.
- A modal should disable underlying controls and move focus into the modal; restore both when it closes.

#### 5. `onClose`: return and clean up

Return to the supplied parent screen with `MinecraftClientHelper.setScreen` when the feature has a parent. Save configuration or clear static screen state here when required. Avoid leaving registered modal buttons active after their overlay is hidden.

If a screen mutates shared state for live preview, also make `removed()` restore uncommitted state. Minecraft may replace a screen without taking its explicit `onClose` path. Cleanup should be idempotent because an explicit close can be followed by `removed()`.

### Minimal screen structure

```java
//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ExampleScreen extends MovementAllowedScreen {
    private final Screen parent;
    private final WidgetStack layout = new WidgetStack(
            0,
            0,
            8,
            LayoutFlow.Orientation.VERTICAL,
            LayoutFlow.Direction.FORWARD,
            true
    );
    private final TranslucentButton doneButton = new TranslucentButton(
            0,
            0,
            80,
            11,
            Component.translatable("server_waypoint.done"),
            this::onClose
    );

    public ExampleScreen(Screen parent) {
        super(Component.translatable("server_waypoint.example.title"));
        this.parent = parent;
        this.layout.addChild(this.doneButton);
    }

    @Override
    protected void init() {
        super.init();
        this.layout.setPosition(this.getCenteredX(), this.getCenteredY());
        this.addRenderableWidget(this.doneButton);
    }

    @Override
    int getContentWidth() {
        return this.layout.getWidth();
    }

    @Override
    int getContentHeight() {
        return this.layout.getHeight();
    }

    @Override
    protected void renderScreenContents(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float deltaTicks
    ) {
        this.layout.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public void onClose() {
        MinecraftClientHelper.setScreen(this.minecraft, this.parent);
    }
}
```

This example deliberately shows all three responsibilities: the stack positions and renders the button, while `addRenderableWidget` registers it for input and focus.

The example translation keys are placeholders; add real keys to the language files when creating the screen.

### In-game theme editor

The Theme button on `ClientConfigScreen` opens `WidgetThemeConfigScreen`. The screen exposes every `WidgetThemeVariable`, previews edits immediately through `WidgetThemeManager`, and keeps opacity separate from the RGB field and swatch. Keep that separation: the existing RGB-oriented color controls normalize their own values to opaque RGB, while many theme surfaces intentionally use translucent ARGB colors.

The editor body follows a two-column layout. The left column keeps the scrollable variable list above the selected variable's RGB and opacity controls; the list's declared rectangle is its complete visual rectangle, including its two-pixel decoration, so it shares the lower panel's left/right edges without narrowing the column gutter. The outer, column, and body-to-footer gutters use the same eight-pixel rhythm, and the two left panels share their adjoining border. The RGB field and `ColorSquareButton` are composed with visual bounds so their outlines stay adjacent and exactly aligned. The right column is a live widget gallery built from the existing text, text-field, button, toggle, and slider implementations, plus small semantic surface/status samples. Gallery controls use deferred theme-variable values, remain interactive so hover/focus/selected states can be inspected, and update immediately with the draft theme. Reset and save-error feedback occupies the otherwise-unused bottom strip inside the gallery rather than reserving a larger empty band above the footer.

The gallery is screen-local rather than a reusable widget API. Its interactive children are registered individually for input, while its `WidgetStack` owns their one manual high-level render pass. When the modal swatch opens, disable the interactive gallery samples along with the editor controls; a deliberately disabled gallery sample must remain disabled when the modal closes.

The package-private `WidgetThemeEditorSession` owns the editing transaction; it is an implementation seam for the screen, not a public theme API:

- `setColor` updates the immutable draft and publishes it for live preview.
- The theme dropdown selects Custom, Translucent Dark, Modern Dark, or High Contrast and previews immediately. It reuses `AbstractDropdownMenuWidget`, registers once, routes popup clicks before covered controls, and renders the popup after the body. Escape closes the dropdown first; the swatch modal disables it.
- Its trigger and choices use `font.lineHeight + 2` height and a two-pixel text inset, matching text fields and comboboxes. The screen positions its full-width outline alongside the body panels. Popup rows share single-pixel separators; labels are clipped within the outline, with the trigger reserving space for a right-aligned open/closed arrow. This selection-only control has no suggestion popup.
- Switching presets retains the custom palette. Editing a preset copies its colors into Custom; subsequent preset switches retain those edits.
- Reset selects the default Translucent Dark preset without erasing Custom; it does not write the file by itself.
- Save atomically writes the selected preset ID and custom palette to `widget-theme.json` and keeps the preview active. `WidgetThemeSelection` resolves presets; `WidgetThemeJson.Settings` contains the selection and custom colors. `loadSettings` reads both, while `load`/`fromJson` resolve the selected effective theme for startup and callers. The `colors` object always stores the custom palette; the optional `selection` defaults to `custom`.
- Cancel, Escape, or removal before a successful save restores the original snapshot.
- Cancel is idempotent so explicit close and subsequent `removed()` calls are safe.

Keep file I/O and rollback behavior in the session rather than scattering it through button callbacks. If saving fails, leave the editor open, show the translated failure state, and keep the draft available for another attempt.

When a modal swatch is open, disable the underlying editor controls, move focus into the modal, and render it on a later layer. Normalize focus after mouse dispatch where necessary because vanilla click handling can replace focus after a callback runs. Escape should close the modal first and only leave the screen when no modal is active.

The editor uses fixed, visual-bounds-aware geometry sized for the normal in-game GUI viewport. Treat those dimensions as screen-specific rather than a general layout API. Future layout changes should preserve the two-column information hierarchy, editor transaction, dynamic theme resolution, single render ownership, input registration, modal layering, and cross-version render paths.

## Text-field suggestions

Calling `setSuggestionsProvider` is only the data step. A suggestion-enabled field needs three pieces:

1. A provider that returns the current suggestions.
2. `renderSuggestions` called after the field, usually on a later layer.
3. `mouseClickedSuggestion` checked before normal screen click dispatch while that field is focused.

`AbstractWaypointPropertiesScreen` is the reference for several fields, while `WaypointManagerScreen` shows the same pattern for a single search field. If any one of the three pieces is missing, suggestions may exist internally but fail to appear or accept clicks.

## Widget render entry points

Minecraft `AbstractWidget` has two render layers. Their names differ by Minecraft version, but their responsibilities are consistent.

| Layer | Minecraft 26+ | Earlier versions | Purpose |
| --- | --- | --- | --- |
| High-level wrapper | `extractRenderState` | `render` | Checks visibility, recalculates `isHovered`, delegates to the widget renderer, and updates tooltip state. |
| Widget renderer | `extractWidgetRenderState` | `renderWidget` | Draws the widget using state already prepared by the high-level wrapper. |

The project maps these names with Stonecutter swaps:

- `render_method_swap`: `extractRenderState` on Minecraft 26+, otherwise `render`.
- `render_widget_method_swap`: `extractWidgetRenderState` on Minecraft 26+, otherwise `renderWidget`.

### Rule of thumb

Use these rules:

- A widget class overriding its drawing implementation uses `render_widget_method_swap`.
- A screen or composite manually rendering an `AbstractWidget` normally calls the high-level method through `render_method_swap`.
- A non-`AbstractWidget` `Renderable`, such as `WidgetStack` or `ScalableText`, exposes its render entry through `render_method_swap`.
- Do not call the low-level widget renderer directly from a screen unless that widget deliberately calculates all required interaction state itself.

Correct manual rendering from a screen or composite:

```java
button.
//$ render_method_swap
extractRenderState
        (context, mouseX, mouseY, deltaTicks);
```

Correct drawing override inside a widget:

```java
@Override
public void
//$ render_widget_method_swap
extractWidgetRenderState
        (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
    // The high-level wrapper has already updated hover and tooltip state.
}
```

### Common hover failure

Calling `extractWidgetRenderState` or `renderWidget` directly bypasses the vanilla wrapper that updates `isHovered`. Widgets that choose colors or outlines using `isHovered()` will then appear not to react to the mouse.

Some custom widgets hide this error because they calculate hover directly from `mouseX` and `mouseY`. A text field can update its own suggestion/hover state, and a list can calculate its hovered row independently. Do not assume that behavior for ordinary buttons.

The high-level wrapper updates hover state; it does not automatically draw a background. A widget still needs to render its normal and hovered backgrounds itself:

```java
int backgroundColor = WidgetThemeManager.getColor(
        isHovered()
                ? WidgetThemeVariable.CONTROL_HOVER_BACKGROUND
                : WidgetThemeVariable.CONTROL_BACKGROUND
);
context.fill(x, y, x + width, y + height, backgroundColor);
```

## Stonecutter and version compatibility

Before changing version-specific GUI code:

1. Check the supported matrix in `settings.gradle.kts`.
2. Check the active `mods` target in `mods/stonecutter.gradle.kts`.
3. Prefer a small, broad Stonecutter predicate over duplicating a whole widget or screen.
4. Preserve inactive branches; they are source for other generated targets.
5. Keep replacement tokens such as `//~ gui_graphics_26` before the first non-empty, non-comment line.
6. Keep `//$ render_method_swap` or `//$ render_widget_method_swap` directly attached to the controlled method name.
7. Recheck balanced `//? if`, `//?}`, `/*?`, `/*?}*/`, and `*//*?` markers after editing.

Do not call version-drifting draw APIs directly at many sites when `DrawContextHelper` can provide one shared compatibility seam.

## Testing and validation

Put pure GUI tests in the matching package under:

```text
mods/src/test/java/_959/server_waypoint/common/client/gui
```

Good test targets include:

- Anchor and content/visual-bound conversions.
- Fixed and weighted layout allocation.
- Nested relayout after resize.
- Tree expansion, visible rows, viewport ranges, and scroll clamping.
- Input parsing and callback-driven state changes.
- Pure label or presentation calculations.
- Theme completeness, runtime updates, JSON round trips, invalid input, and file persistence.
- Theme-editor preview, reset, save, cancel, and idempotent rollback transitions.
- Translation coverage for every `WidgetThemeVariable` JSON name.

After a GUI change:

1. Run `git diff --check`.
2. Run the exact versioned `mods` test or compile task for the active target, for example `./gradlew :mods:26.1.2-fabric:test` when that is the selected target.
3. For render, input-signature, or Stonecutter changes, compile at least one older supported target such as `./gradlew :mods:1.20.1-fabric:compileJava`.
4. Compile each affected loader when a predicate or API branch differs by loader.
5. Inspect generated Stonecutter source if a branch or replacement does not behave as expected.
6. Test the screen in game when the change depends on hover, focus, clipping, layering, tooltips, or resize behavior.

## Review checklist

- New code is in the narrowest correct package and project.
- The mandatory component reuse check was completed before implementation; any custom alternative
  identifies the existing component inspected and the concrete requirement it cannot satisfy.
- Standalone wrapped/scaled labels and messages use `ScalableText`, with available width updated
  after layout changes; screens and callbacks do not duplicate its wrapping loop.
- Layout, input registration, and rendering are all wired exactly once.
- Padded drawing has an explicit `Padding`/visual-bounds contract.
- `WidgetStack` versus `ExpandableManager` matches fixed versus responsive layout needs.
- Screen construction, `init`, rendering, input, focus, and `onClose` responsibilities are separated.
- `MovementAllowedScreen` subclasses render content through `renderScreenContents` and rely on the inherited themed background.
- Suggestion-enabled fields provide data, rendering, and click handling.
- Screen call sites use `render_method_swap` for ordinary `AbstractWidget` rendering.
- Widget drawing overrides use `render_widget_method_swap`.
- Hover-dependent drawing reads state only after the high-level wrapper has run.
- Normal and hovered backgrounds are both explicit when the widget should not be transparent while idle.
- Theme-aware drawing resolves semantic roles at render time instead of caching raw colors.
- Built-in themes and translation resources cover every theme variable.
- Live-preview screens restore shared state from `removed()` when edits were not committed.
- Translation keys, textures, and theme roles use their shared resource locations.
- Stonecutter markers are balanced and replacement tokens remain in valid positions.
- The exact active target and a relevant compatibility target compile or test successfully.
