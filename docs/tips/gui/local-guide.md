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
| `client.gui.api` | Small GUI-facing contracts and callbacks | Button, toggle, color-picker, and dimension-selection callbacks; `Colorable`; `PopupOwner` |
| `client.gui.layout` | Positioning, sizing, padding, and flow | `WidgetStack`, `WidgetPack`, `ExpandableManager`, `LayoutFlow`, `Padding`, `AnchorMode`, `VisualBounds`, `VisualPositioning`, `TooltipPlacement` |
| `client.gui.render` | Cross-version drawing helpers, semantic theme state, and presentation constants | `DrawContextHelper`, `PaddingBackground`, `WidgetTheme`, `WidgetThemeManager`, `WaypointTextures` |
| `client.gui.screens` | Screen lifecycle and feature composition | Manager, add/edit, configuration, theme-editor, and movement-aware screens |
| `client.gui.widgets` | Reusable visible and interactive components | Buttons, fields, sliders, dialogs, color pickers, lists, tree views, and tooltips (`TranslucentTooltip`, `TooltipLayer`) |

Related resources live under:

```text
mods/src/main/resources/assets/server_waypoint/lang
mods/src/main/resources/assets/server_waypoint/textures/gui
```

Use translatable `Component` values for player-facing text. Add GUI textures to `textures/gui` and expose shared identifiers through `WaypointTextures` rather than scattering resource identifiers across widgets.

`WaypointAddScreen.fromWaypoint(parent, dimensionName, listName, defaults)` opens the ordinary add
form populated from a detached `SimpleWaypoint` draft, including its name, initials, position,
color, yaw and icon. Map-share confirmation uses this API; opening or cancelling the form never
adds a waypoint. The existing constructors remain suitable for empty forms and map-location adds.

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

- `ButtonClickCallback` reports a button press, from a left click or an activation key.
- `ToggleButtonCallback` reports the new boolean state.
- `ColorPickerCallback` reports an updated ARGB/RGB value.
- `DimensionListCallback` reports a selected dimension name.
- `Colorable` standardizes `getColor` and `setColor` for color-aware widgets.
- `PopupOwner` marks a control with a transient popup, such as a menu or suggestion list.
  `closePopupIfOpen` closes it and reports whether it was open, so Escape can dismiss it (see
  [Input](#4-input-preserve-focus-and-text-entry)). `scrollPopupIfOver` scrolls the popup with the
  mouse wheel when the pointer is over it and reports whether the wheel was used; the default
  answer is no, so a popup that has no wheel behavior of its own needs no override.

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
- `VisualPositioning.getVisualX`, `getVisualY`, `getVisualWidth` and `getVisualHeight` read any
  `LayoutElement`'s visual bounds: a `Padding` element's complete drawn rectangle, and any other
  element's own bounds. Use them instead of repeating the `instanceof Padding` check.
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

`ExpandableManager` is layout-only and does not render its children. Register and render the managed widgets separately.

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

Children touch the top of a horizontal pack, or the left of a vertical pack. Call
`setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER)` to center them across the pack by
their visual bounds instead, rounding toward the start; changing the alignment lays the children out
again. `SettingsListWidget` rows use this to line up labels and controls of different heights.

Use `IconListLayout` for the geometry of an oriented, scrollable icon strip shared by dimension
and server lists.
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
`closeMenuIfOutside` and then continue normal dispatch so the same click can reach its target.
A dropdown is a `PopupOwner`, so Escape closes an open menu and the dropdown yields focus before
the screen can close; screens need no Escape code of their own for it (see
[Input](#4-input-preserve-focus-and-text-entry)). Losing focus closes the menu, so an open menu
always belongs to the focused control.

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
non-null text without calling the callback. `setMaxLength` limits the text length, which is
otherwise unlimited, and cuts longer text without calling the callback. `setValues` replaces the popup choices, closes an open
popup, and preserves the current text without invoking the callback. User edits and popup selections invoke the callback.
Suggestions default to the current choices; `setSuggestionsProvider(Supplier<List<String>>)` can
supply a separate dynamic catalog, and `null` disables suggestions. Matching is case-insensitive
prefix matching, deduplicated and sorted, with an inline suffix and up to five popup rows.
`setSuggestionsMatcher(BiPredicate<String, String>)` on either `ComboBoxWidget` or
`SuggestingTextInput` overrides matching for that input only. Its arguments are the candidate and
current input text; changing it refreshes completion state. Exact matches remain omitted, and
accepting a match replaces the entire input with the candidate. Inline suffixes appear only when
the candidate starts with the current text.
`setTextColorProvider(IntSupplier)` on either input or combobox supplies the active input's text
color on every render; disabled text and placeholder colors remain theme-owned. Use it for input
validation feedback instead of calling vanilla `setTextColor` once, which a theme refresh replaces.
`ComboBoxWidget.useMatchingValueColors()` opts into colors based on exact, case-sensitive membership
in the current popup choice catalog: yellow (`#FFFF55`) for a matched value while focused,
normal `TEXT_PRIMARY` when matched and unfocused, and red (`#FF5555`) for a non-empty unmatched
value regardless of focus. Empty text keeps its normal theme color, and disabled text keeps
`TEXT_DISABLED`. Replacing choices with `setValues` automatically changes the color on the next
render without altering the entered text. The waypoint-add list-name combobox uses standard
themed text colors.

`ComboBoxWidget.useResourceIdMatching()` opts into vanilla resource-ID suggestions and the same
focus-dependent validation colors. Without a colon, a query matches either the namespace or path,
including words after underscores; with a colon it matches the full ID. This works for vanilla
and custom dimensions alike: `over` suggests `minecraft:overworld`, while `moon` and `base` can
suggest `examplemod:moon_base`. Suggestion matching ignores case, but a complete valid ID must use
its exact canonical spelling. Bare complete IDs default to `minecraft`, so `overworld` matches
`minecraft:overworld`; a custom ID requires its explicit namespace for validation. The full choice
popup includes both vanilla and custom dimension IDs.

`getValue()` retains the entered text. `getResolvedValue()` returns the canonical full ID for a
complete known resource match, and the entered text otherwise; ordinary comboboxes return their
entered text. Use the resolved value for catalog lookups, form checks and submission. The waypoint-add
dimension field uses this mode and refreshes list choices after an asynchronous dimension-catalog
update, so a newly resolved bare vanilla name uses the correct dimension's lists.

`ComboBoxWidget.getHoveredValue(mouseX, mouseY)` returns the current visible popup choice or
suggestion under the pointer, excluding the trigger and scrollbar. It is available before popup
rendering, observes the current scroll position, and does not edit text, accept a choice, or move
keyboard selection. `SuggestingTextInput.getHoveredSuggestion` provides the suggestion-only query;
`AbstractDropdownMenuWidget.getHoveredMenuItem` is the protected choice-only counterpart. Hidden,
disabled, closed or dismissed popups return no hovered value.
Up/Down selects a suggestion and Tab/Shift-Tab accepts/cycles completions, and the mouse wheel
scrolls a longer list while the pointer is over it. Clicking a suggestion
also accepts it through the normal user-change callback. The full choice popup suppresses
suggestions while open. `renderPopup(...)` draws whichever popup is active, including when rendered
separately. Route popup clicks before overlapping controls using `isMouseOver(...)`.
`isMouseOverPopup(...)` tests only the visible choice rows/scrollbar or suggestion list, excluding
the input and arrow. The shared waypoint form lays out popups before this hit test and renders
ordinary content with `DrawContextHelper.NO_MOUSE` while the pointer is over a popup; popup
rendering keeps the real pointer so row hover still works. Standalone field suggestions use
`isMouseOverSuggestion(...)` for the same isolation. A modal swatch suppresses both layers.
The base dropdown also exposes `isMouseOverPopup(...)` for its visible menu alone.
`closePopupIfOpen()` closes the choice list, or the suggestions when the list is closed, so
Escape dismisses whichever is showing.

Call `ComboBoxWidget.layoutPopup(screenHeight, maxRows)` after positioning the control and before
rendering its popups to opt into screen-aware vertical placement (the waypoint form's
comboboxes use eight rows). Refresh it as the screen size, control position, or choices change. The choice list
prefers downward expansion whenever its visible rows fit within a four-pixel screen margin;
otherwise it uses the side with more room and limits its scrollable height. Suggestions choose
their direction independently using their actual height, so a shorter suggestion list can still
open downward when the full choice list needs to open upward. Other comboboxes retain their
explicit expansion direction until this method is used.

`SuggestingTextInput` is the reusable surface-free input base. It owns editing, shifted layout,
completion state, inline text, and suggestion rendering/hit testing; `TranslucentTextField` adds
only its themed surface, whose fill `surfaceColor()` returns for the field's state. A field on a panel
that already paints a translucent fill overrides it to leave out the resting fill, as
`WaypointSearchBarWidget` does on the manager's list panel; hovering and the disabled look still fill.
Such a field shows its hint through `setPlaceholder`, as the search bar does, because vanilla's fixed
dark gray hint is unreadable on a bare panel (1.2:1 against a light world). By default the list takes the field's outline: it starts at `getVisualX()`
and is `getVisualWidth()` wide, so its edges line up with the field's, and it widens to the right
only when a suggestion's text needs more room. Composites can override `getSuggestionsX()`,
`getSuggestionsY()`, and `getSuggestionsWidth(int maxTextWidth)` to anchor suggestions to their outer
bounds. The combobox uses its full control width, including the arrow area, and clips suggestion text
inside that outline.
Suggestion rows share vanilla `EditBox`'s horizontal text viewport through `EditBoxAccessor`, so
their visible prefixes line up with the scrolled input. The inline completion follows the visible
input text and is clipped to the input area. Moving the cursor back to the start restores the
suggestion prefixes; acceptance always uses the complete suggestion value.
Drawing and hit testing use the same bounds. Use `setSuggestionsEnabled(...)` to temporarily suppress completion without
losing focus. `refreshSuggestions()` invalidates a completion cycle after catalog changes; replacing
the provider also refreshes it. `renderSuggestions(...)` remains an explicit overlay pass for standalone inputs.
Escape closes the suggestion list and the input yields focus. A dismissal through
`closeSuggestionsIfOpen()` persists until editing or refocusing, and disabled/hidden inputs do not
accept suggestion clicks. `AbstractDropdownMenuWidget.renderPopup(...)` may be overridden to provide
another popup when the full menu is closed; preserve its separate-rendering contract.

`setPlaceholder(Supplier<Component>)` shows themed text while the field is empty and unfocused, in
`TEXT_PLACEHOLDER` (`TEXT_DISABLED` while inactive). It goes through vanilla's `EditBox` hint, which
gives text without a color of its own a fixed gray, so the input styles the text with the theme's
color each frame. The supplier is read every frame too, so the text can follow another field, as the
waypoint form's Display name follows Name. `ComboBoxWidget.setPlaceholder` passes it to its input,
and `ColorHexCodeField` uses it for `RRGGBB`, or `AARRGGBB` in its ARGB mode.
`TranslucentTextField.setInvalid(true)` draws the outline in `DANGER` instead of the border color
until it is cleared, for a value a form rejects.
`isSuggestionListOpen()`, also on `ComboBoxWidget` for its input's list, reports whether a list is
showing. `acceptHighlightedSuggestion()` takes the highlighted suggestion the way clicking it does
and reports whether a list was showing. Text fields don't handle Enter themselves; a screen that
gives Enter this meaning while a list is open, as the waypoint form does, calls it before using
Enter for anything else.

An exact matching choice is omitted from the popup when other choices exist. A sole choice remains
in the popup even when it matches the current text or resolved resource ID, so the arrow still opens
the list. Resizing also resizes the field and choice rows.
Resting combobox popup rows draw side and bottom borders; the preceding control or row supplies
the shared top edge, keeping separators one pixel thick without overlapping row hit areas.
Hovered and keyboard-highlighted rows draw their own complete four-sided outline. Their surface,
outline, and label reserve the scrollbar column when the popup overflows.
`AbstractDropdownMenuWidget.getPopupContentWidth()` reports this row area, excluding the visible
three-pixel scrollbar and its right inset. Popup rendering passes `NO_MOUSE` to the choice rows
while the pointer is over the scrollbar, so it does not highlight a row underneath the thumb.
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
popup choices, including on newer render strata APIs. `AbstractWaypointPropertiesScreen` draws the
popup of every dropdown it holds, the icon picker's and Add's dimension and list comboboxes, after the rows,
the suggestions and the field tooltip, and calls `layoutPopup(height, 8)` on each first, so a popup
shows at most eight rows and opens on the side with room. The dimension combobox is populated from the same complete
integrated-server or remote-suggestion dimension catalog as `WaypointManagerScreen`, with the
supplied starting dimension retained. The list combobox offers the waypoint lists of the chosen
dimension: the screen replaces its choices with `setValues` whenever the dimension changes, so the
text already typed stays and a name that isn't a list yet still creates one. `setMaxLength` caps
it at `MAX_NAME_LENGTH`. Waypoint-name suggestions and submission read the current selection. Popup clicks have priority over overlapping
fields, outside clicks continue to their targets, and Escape closes the popup before the screen.

## `render`: drawing and presentation

Use `DrawContextHelper` for drawing operations whose Minecraft API changes across supported versions. It centralizes text, texture, item, matrix, layer, outline, and custom-quad differences. Before adding a new Stonecutter branch at every call site, check whether the difference belongs in this helper.

To draw widgets with no hover state or tooltip, as under a dialog or for a clipped settings row,
pass `DrawContextHelper.NO_MOUSE` as both mouse coordinates.
`AbstractWaypointPropertiesScreen` does this for the form and its popups while its modal
`SwatchWidget` is visible, and passes the real mouse coordinates only to the swatch.
`WaypointManagerScreen` does it for everything drawn before its sort dropdown while the pointer is
over the dropdown's popup (see the manager's sort popup below). Disabling controls alone does not
suppress hover or cursor requests during rendering.

`DrawContextHelper.texture` has an overload with a trailing ARGB color that multiplies every pixel
of the texture, as `IconButton` does to tint an inactive icon. It uses the colored blit on 1.21.2
and later, and a shader color around the blit before that.
Its source-region overload takes separate source and destination dimensions for scaling a cropped
region while preserving the same tint behavior.
The manager's sidebar icon controls use `TEXT_DISABLED` for inactive icons and leave the panel
background visible, including the sort-order toggle when default sorting is selected. Popup rows
keep their own disabled fill because they float over other controls.

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
- Divider and settings-header lines use `DECOR_LINE` (`decor.line`), independently of `BORDER` (`border.default`).
- Feedback uses `SUCCESS`, `WARNING`, `DANGER`, and their background variants.

These roles apply to GUI chrome and state. A waypoint's user-selected color is domain data, and RGB/HSV picker gradients visualize a color space; those values can remain direct colors rather than theme roles.

`WidgetThemeVariable.getJsonName()` is the stable external name for a role. Use it in JSON. The theme editor shows it raw, so theme keys have no translations. Do not persist enum names or introduce a raw color constant when an existing semantic role already fits.

`WidgetTheme` is an immutable, complete snapshot. A builder created with `WidgetTheme.builder()` must assign every variable before `build()`. For a partial change, start from an existing theme or use `withColor`:

`WidgetThemes.DEFAULT` points to `WidgetThemes.TRANSLUCENT_DARK`, the built-in neutral grayscale
glass palette. The screen overlay is 65% opaque and panels are 70% opaque, preserving a
view of the world while limiting bright-scene washout. Controls use 85–90% opacity and
lighter charcoal fills so their shape remains visible at night. Default borders are transparent;
a brighter focus ring identifies keyboard focus. Popups are
98% opaque and dialogs 99% opaque to suppress underlying labels showing through. Accent
fills are dark gray with light text; selected rows have a stronger neutral fill. Success,
warning, and danger use muted sage, amber, and dusty red foregrounds and backgrounds.
Validate text and control contrast after compositing the screen, panel, and control over
both black and white world backgrounds; checking raw RGB against black misses daylight failures.
`WidgetThemes.MODERN_DARK` follows the same opacity progression with blue-gray surfaces,
transparent default borders, and cyan focus highlights. Its accent and selected fills use dark teal to keep
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

`WidgetThemes.CLASSIC` retains the previous release's (3.0.4) translucent surfaces:
panels are 60% black and controls 53% black, with transparent default borders, and it is more translucent than
Translucent Dark and Modern Dark on every surface. Nothing dims the world behind a screen. Popups, dialogs
and scrollbar tracks are as translucent as the panels, so labels beneath a popup show through.
Hovering and selecting are white washes (`ROW_HOVER_BACKGROUND` 19%, `SELECTION_BACKGROUND` 35%,
`CONTROL_HOVER_BACKGROUND` 40%). The selected and On/Off fills are the old blue, green and red toggle
colors laid over the old control fill and flattened into one layer, because a toggle paints a single
fill. Status text uses Minecraft's own light green, yellow and red, and the accent is a neutral gray
that keeps white `TEXT_ON_ACCENT` readable. Contrast is as low as it was on a bright world, where muted
text and hovered controls fade, so only the main text and the toggle labels are tested for
readability. The `WidgetThemeTest` pixel check pins its panel, control, scrollbar and row-hover
layers to the previous release's pixels and checks that default borders leave panel pixels unchanged. Apply it through the editor dropdown; it does not change the
default theme.

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
3. A `PreviewSample` (sample or marked element) that uses it.
4. Theme completeness, JSON, and preview-coverage tests.

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

While wrapping, `getWidth()` is the maximum width and `getTextWidth()` is the width of the widest
wrapped line, scaled the same way. Without a maximum width `getTextWidth()` is the whole text's width,
and with no lines it is 0. Use it to size a surface to its text, as `TranslucentTooltip` does.

Direct text drawing remains appropriate inside the text widget itself and for specialized row or
document renderers whose per-run formatting or geometry cannot be expressed by `ScalableText`.
Those cases do not justify duplicating standalone message rendering elsewhere.

### Component selection

| Need | Start with |
| --- | --- |
| Text label, optional scaling/wrapping | `ScalableText` |
| Text action | `TranslucentButton` |
| Icon action | `IconButton` |
| Boolean state | `ToggleButton` or `OnOffToggleButton` |
| Text input with optional suggestions | `TranslucentTextField` |
| Editable text with a popup choice list | `ComboBoxWidget` |
| Bounded integer input | `IntegerField` |
| Absolute/relative/local coordinate input | `CoordinateField` |
| Integer slider plus field | `IntegerSlider` |
| Hex color input | `ColorHexCodeField`, or `ColorHexCodeField.argb` for a color with an alpha |
| Color selection | `ColorSquareButton`, `SwatchWidget`, `RGBColorPicker`, or `HSVColorPicker` |
| Scrollable hierarchical rows | Extend `TreeViewWidget<T>` |
| Scrollable settings rows with section headers | `SettingsListWidget` with `Row`, or `WideRow` for a control without a label |
| Selectable item icon strip | `IconListWidget<T>`, `DimensionListWidget`, `ServerListWidget` |
| Directional popup with custom items | Extend `AbstractDropdownMenuWidget` and `AbstractMenuItem` |
| Confirmation overlay | `ConfirmationDialog` |
| Tooltip for a control or a hovered item | `setTooltip(Component)`, or `TooltipLayer.scheduleAtPointer` |

`TranslucentButton.fitted(label, callback)` makes an 11-pixel-high text button as wide as its label
plus 5 pixels on each side, and at least 50 pixels wide, so short labels line up and long
translations still fit. Dialog and footer buttons use it.

`ColorHexCodeField` edits an opaque RGB color by default: six digits after the `#`, the placeholder
`RRGGBB`, and `getColor()` and `setColor(int)` use RGB (`setColor` ignores the alpha). The waypoint
form uses it that way. `ColorHexCodeField.argb(x, y, text, font)` makes an ARGB field for a color
with an alpha, such as the theme's: eight digits, the placeholder `AARRGGBB`, a content width of 51
instead of 39, and an ARGB `getColor()` and `setColor(int)`.

- **Current color:** an ARGB field's `getColor()` reads the text only while it has all eight digits.
  Otherwise it returns the last complete value: the last `setColor(argb)` or the last time the text held
  eight digits, typed, pasted or set. It changes only when the text becomes complete, not on the way
  there, so a responder that applies the value at eight digits never applies the color that the first
  six digits of an unfinished edit would make.
- **`commit()`** finishes an unfinished value. In an ARGB field, six digits become RGB under the alpha of
  the current color (`B31C1C1C`, then `FF0000`, gives `B3FF0000`), and any other incomplete value, an
  empty one included, is replaced by the current color; eight digits are left alone. An RGB field pads
  a short value with leading zeros to six digits. `setFocused(false)` calls it, and a screen calls it
  for Enter, since text fields don't handle Enter themselves. The completed text is set with
  `setValue`, so the responder sees it.
- **Typing and pasting:** `charTyped` stops at the mode's digit count. `insertText` takes hexadecimal
  digits only and drops one leading `#` in both modes, so a color copied from `widget-theme.json`, such
  as `#D9262626`, can be pasted; any other text is ignored.

`TranslucentButton`, `TranslucentTextField`, `ColorHexCodeField`, and `ToggleButton` paint the full background first,
then overlay their outline on the same rectangle. Button and toggle visual bounds are
`getX()`, `getY() - 1`, `width`, and `height`; the text field retains its two-pixel text inset
and paints its full `width` by `backgroundHeight` surface beneath the border.

`ColorHexCodeField` includes its `#` prefix in that full background. `ColorSquareButton` keeps
its opaque swatch at the configured size and backs its outline with a same-RGB ring at 50% opacity.
The themed outline is drawn last over that ring; borderless swatches add the ring only while
active and hovered or focused.

`IconButton` keeps its full configured hitbox while drawing its texture with a 2-pixel inner inset.
Use `withIconPadding(int)` to choose a smaller non-negative inset, and
`withIconRegion(x, y, width, height, textureWidth, textureHeight)` to fit and center the visible
source region while preserving its proportions. The reset and clear buttons both use a 3-pixel
inset and exclude their textures' unequal transparent margins, matching the add button's visible
inset (its 2-pixel drawing inset plus transparent texture margin).
Screen-local icon controls should use the same inset so adjacent icon actions remain visually consistent.
An inactive `IconButton` multiplies its icon by the theme's `TEXT_DISABLED`, the color a disabled
button's label takes, so an unavailable action, such as resetting a setting that's already at its
default, doesn't look pressable. Draw icons in a light gray or white so the tint shows.

An `IconButton` uses the control background at rest and the hover fill while active and hovered.
Disabled buttons keep their resting background and dim only their icon. On a panel that already
paints a translucent fill, call `withoutRestingFill()` so
the button paints none of its own while idle and the panel shows through (the manager's add button does).
The fill painted for the current state is `surfaceColor()`.
Use `withoutBackground()` to remove the fill in every state, including hover and disabled,
as the reset and clear buttons do. Their outlines and icon tints still follow the theme.

`ToggleButton` takes each state's fill either as a `WidgetThemeVariable`, which follows the theme (as
`OnOffToggleButton` does with `SUCCESS_BACKGROUND` and `DANGER_BACKGROUND`), or as an int ARGB, which is
used as given and never follows the theme. The waypoint form's Visibility toggle uses fixed colors on
purpose: Local is green and Global is blue in every theme, so keep it off the theme roles. A toggle paints
only its one fill, so pass the alpha you want over the panel. The form's `LOCAL_TOGGLE_COLOR` and
`GLOBAL_TOGGLE_COLOR` are the previous release's #04E500 and #005AE5 at 60% over its 53%-black button fill,
flattened into one layer, and `WaypointFormToggleColorsTest` pins them to those pixels. The outline, the
hover and focus ring, the label color and the disabled look still follow the theme.

The main base classes have distinct roles:

- Extend `ShiftableWidget` for a non-interactive `LayoutElement`/`Renderable`.
- Extend `ShiftableClickableWidget` for a normal `AbstractWidget` with input. Its `keyPressed`
  ignores keys unless overridden, so controls that own their key handling (text fields,
  `IntegerSlider`, `ComboBoxWidget`, dropdowns, and color pickers) build on it directly.
- Extend `ShiftableButtonWidget` for a pressable control, as `TranslucentButton`, `ToggleButton`,
  and `IconButton` do. Implement `onPress()`: like vanilla `AbstractButton`, a left click and
  Enter, Space or keypad Enter while the button is focused, active and visible both play the
  click sound and run it. `onClick` is final so the two paths cannot diverge, and
  `activatesOn(keyCode)` reports whether a key would press the button. Screens route those keys
  as described in [Input](#4-input-preserve-focus-and-text-entry).
- Extend `ShiftableScrollableWidget` when the widget has a vertically scrollable viewport. Its
  scrollbar track, thumb, click area, and reserved content column are 3 GUI pixels wide. Combobox,
  dropdown-menu, and input-suggestion scrollbars use their own geometry.
- Extend `TreeViewWidget<T>` when the content is a flattened visible view of expandable hierarchical data. Implement child lookup, expansion state, empty rendering, and row rendering; the base class handles scroll bounds, hit testing, visible-row calculation, clipping, and scrollbar drawing. A tree paints its own `PANEL_BACKGROUND` fill, which is right when nothing else paints under it, as in the theme editor's variable list. A tree on a panel that already paints that fill overrides `backgroundColor()` to return 0, as the waypoint list and the remote browser tree do on the manager's list panel: a translucent layer composited twice (60% black twice is 84%) makes the tree look darker than the panels beside it.

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
Local rows prefer a shared aligned distance column. As the list narrows, the gap after each
formatted name/context shrinks to a minimum of 3 GUI pixels; a longer label pushes its distance
rightward instead of overlapping it. Names scale only after the minimum gap and distance width
have been reserved inside the row. Distance text keeps its metadata scale independently of the
name. Non-hovered rows can use the space occupied by hidden action buttons. On hover, a distance
that would enter the three action columns is hidden, and the name fits before the buttons.
Distances already clear of the buttons remain visible. Button positions and hitboxes are unchanged.
Long initials shrink to the same 16-pixel slot as item and VoxelMap icons to avoid overlapping names.
An empty waypoint name and context use an unshrunk label scale so the distance still renders
whenever the distance column is visible.
Dimension, list, and distance metadata render smaller than the waypoint name. Row actions must use
the retained dimension rather than the sidebar's selected dimension. The displayed dimension omits
the `minecraft:` namespace for vanilla dimensions but preserves namespaces for modded dimensions,
and uses the shared dimension-color mapping in both grouped dimension roots and flat waypoint rows
while list metadata remains muted. The dimension rail remains selectable in all-dimensions mode;
its selection is retained for returning to selected-dimension scope.
When a query leaves no rows, `WaypointListWidget.resolveEmptyReason(query, showAllDimensions)`
picks a search-miss message, or a no-waypoints message for all dimensions or the selected one with
a hint to use the + button, and the list renders it through a retained muted `ScalableText` at the
first row's text position.

`WaypointListWidget` reports row-body selection through the `Consumer<WaypointSelection>` supplied
to its constructor. Its action columns remain independent: visibility, edit, and remove clicks do
not replace the current selection. `WaypointSelection` carries the source dimension, plain and
display list names, and live waypoint; the widget reconciles that identity after queries so sorting
and refreshes preserve a still-visible selection while filtering or removal clears it.
In the ready local view, `WaypointManagerScreen.keyPressed` offers keys to the list before
`super.keyPressed` only when `canUseShortcuts(focused)` permits them: neither an `EditBox`
(including the search field) nor a `ComboBoxWidget` may be focused. The same guard controls the
manager's C shortcut and movement keys. With non-text focus, pressing T while a waypoint row is
hovered sends that waypoint's teleport command; typing T in a text control leaves input to that
control even while the mouse hovers over a waypoint.
`isTeleportKey` compares against `InputConstants.KEY_T`, which is 23 on 26.3 and 84 before it.
`WaypointDetailsWidget` consumes that selection and presents every stored waypoint field plus its
dimension/list context in a separately scrollable viewport. Keep formatted display names and
descriptions parsed only at this render boundary, and reserve scrollbar width while wrapping so
content does not relayout when overflow begins. Empty keywords and descriptions retain their labels
with blank values, without a placeholder. Local and remote descriptions start below their
label. Parse their JSON before decoding literal `\n` and actual line separators through
`TextHelper.parseDescription`, keeping styles on nested text; the same wrapped component determines
both rendering and scroll height. Its content ignores clicks without playing a button
sound; only the visible scrollbar accepts clicks for dragging, while the mouse wheel scrolls the
viewport. Local and remote selections share a color row with a bordered swatch beside the saved
hex value. Its fill and outline start one GUI pixel above the text origin, matching the inline icon's
alignment. Reserve the swatch width during wrapping and content-height calculation.
Remote status values use `RemoteRefs.stateColor`, matching `/wp remote`: green for available,
yellow for stale, red for unavailable, and dark gray for unauthorized; the status label stays muted.
Its optional icon row uses the same `Icon:` label
as the waypoint form, draws the resolved item or VoxelMap image beside the saved ID, and shows
initials if the image cannot be resolved. Keep the preview one font line high and align its image
with the text baseline. Reserve its width in both rendering and content-height calculations so
wrapped IDs and following rows do not overlap it. The dimension
row uses the same shared dimension-color mapping as the waypoint list. `WaypointManagerScreen`
always renders this panel and centers the
left rail, middle waypoint list, and right details panel as one group with two-pixel inter-panel
gaps. Calculate the middle content width as 38% of the scaled viewport clamped to 180–360 pixels,
and the details content width as 32% clamped to 150–320 pixels. When their combined desired width
exceeds the space inside the 12-pixel screen margins, proportionally shrink both while keeping all
three panels visible. Content height uses 82% of the viewport clamped to 120–400 pixels and the
available vertical margins. Resize the search field and waypoint list by visual width together with
their panels so drawing and hitboxes continue to match the calculated geometry. The manager paints
each panel's fill once, over the whole panel; the waypoint list, the remote tree and both rails sit on
that fill and paint none of their own, so they match the other panels at any theme translucence. The
list keeps its own outline, which frames it inside the list panel. A widget added to a panel follows
the same rule. The same goes for controls: the sidebar's icon buttons (the scope, grouping and sort-order
toggles, the sort dropdown's button and the add button) and the search field paint no fill while idle, only
their border, so a control isn't a second translucent layer stacked on the panel. Hovering, the disabled
look and a selected control still fill. The sort dropdown's popup rows float over other widgets, so they keep
their resting fill; `WaypointManagerScreen.resolveIconControlFill` makes that choice.

On a narrow screen, where the choices don't fit left of the controls, the sort popup shifts right and up one
row, over the sort-order toggle. That toggle is drawn before the dropdown, so with the real pointer it would be
hovered under the hovered choice and request its tooltip first, and a hovered choice never replaces an earlier
request. While `sortingModeDropdown.isMouseOverPopup(...)` holds, the manager therefore draws everything before
the dropdown (the search field, the lists, the details, the rails and the other controls) with `NO_MOUSE`,
through the pure `WaypointManagerScreen.resolveMouseBeneathPopup`. The dropdown keeps the real pointer, so its
popup shows the hovered choice's tooltip. The all-dimensions toggle is drawn after the dropdown, asks later than
the choice does and needs no such treatment.

The manager's sidebar `HOME_ICON` / `LAN_SERVERS_ICON` toggle switches its middle list and right
details panel between current-server and remote waypoints without opening another screen. Its
tooltip shows `Waypoints in current server` in local mode and `Waypoints in remote servers`
in remote mode. The toggle is shown only while `RemoteWaypointPanel.servers()` is non-empty
or the remote view is open, so singleplayer and servers without cross-server never show it.
`RemoteWaypointPanel` is a package-private screen composition helper: the manager registers its
widgets once, supplies layout and visibility, forwards ticks, and owns its manual render pass.
Search, list/flat mode, name/color/default sorting, and sort direction control both views. Remote
flat mode sorts within the selected server and dimension scope while retaining server, dimension,
list, and waypoint identity; grouped mode shows list roots for a selected dimension or dimension/list roots for all dimensions. Remote distance sorting is unavailable because there
is no shared player origin across servers (entering remote mode from distance sorting selects name,
and the distance sort choice is hidden).
In remote mode, `ServerListWidget` appears immediately above the scope toggle and the add button
is hidden, releasing its layout slot. The dimension rail shows dimensions from the selected remote
server's catalog (including exported empty dimensions); `RemoteBrowserModel.dimensionNames` leaves
it empty for an unavailable server, whose retained snapshot the tree also hides. The all-dimensions
toggle applies within that server. One-pixel themed separator lines separate the dimension and server rails, and the server
rail from the control buttons, when each adjacent pair is visible. `SeparatorWidget` owns these
non-interactive lines; the manager positions each midway in its gap and renders it explicitly.
The four-argument `SeparatorWidget(x, y, width, height)` constructor resolves `DECOR_LINE`
at render time. All manager and waypoint-form dividers and the lines beside
`SettingsListWidget.Header` titles use this role, which is editable as
`decor.line` in the theme editor and JSON. Built-in palettes give dividers their own
visible color even when default borders are transparent. The reusable widget also accepts
an explicit theme color or color supplier,
and its width and height allow either horizontal or vertical separators. Returning to local mode
restores the local dimension selection.

`IconListWidget<T>` owns per-instance selection, scroll, clipping, item positioning, padding,
hover labels and left-click dispatch for both `DimensionListWidget` and `ServerListWidget`. It paints no
background: a rail sits on the manager's left panel, which already paints the fill, and its
`backgroundColor()` returns 0 so the translucent role isn't composited twice.
Hover labels are scheduled at the cursor after resolving the hovered icon, so scrolling or
resizing either rail does not anchor a label to the full widget bounds.
Dimension hover labels use the shared dimension color mapping and the same GUI color scale as
dimension labels in waypoint rows; server hover labels show the server name and exact ID.
Specializations supply `drawIcon` and optionally `entryLabel`. `setEntries` copies its catalog,
preserves selection by identity, and falls back to the first entry; an empty catalog clears selection.
`setSelectedEntry` does not fire callbacks; `resetSelection` replaces the former static dimension
reset. Register and manually render each rail once through its high-level wrapper.
Specializations may also override `entryBadgeColor` to return a theme role. After drawing every
icon, the rail draws a 5×5 dot (a 3×3 fill in that role inside a one-pixel `BORDER` edge) flush
with the top-right corner of each badged icon cell, above the icons through
`DrawContextHelper.nextItemOverlayLayer` and inside the rail's scissor. `ServerListWidget` badges
stale servers with `WARNING` and unavailable ones with `DANGER`; available servers get no badge.
Its hover label adds the state on a second line, colored by `RemoteRefs.stateColor` to match
`/wp remote` and the remote details pane. `ServerListWidget.stateColor` supplies the theme roles
for icon badges.

`preferredHeight()` reports the icon strip's natural content height with a one-icon minimum.
`OpposedExpansionLayout.allocate` reserves the fixed minimum for each rail and shares constrained
space equally, giving unused space from a short rail to the longer rail. The manager anchors the
dimension rail at the sidebar top and the server rail just above the controls; they grow down and
up respectively, stop at their content sizes, and scroll when constrained. Icons in both rails flow
top to bottom, so their wheel scrolling follows the same direction. The helper returns zero
sizes if even both minima plus their gap cannot fit; the screen hides the rails at that tiny size.
The pure `WaypointManagerScreen.calculateSidebarLayout` combines this allocation with the number of
visible controls, which stack upward from the content bottom with 4-pixel gaps, so a hidden toggle
or add button releases its slot to the rails. Catalog changes, mode switches, resize and changes in
remote-server availability all recalculate it. Local mutation callbacks
must not replace the remote dimension catalog.

Server item icons resolve from `CatalogReceiver.View.iconItem()` through the client's item registry.
Missing registry entries and air use a compass; tooltip labels include the exact server ID.
Remote data stays in `RemoteClientCatalogs`; no remote row creates a local waypoint or mutation
handle. The remote details remain read-only; the read-only tip appears only while no waypoint is
selected. The teleport button sends immediately without a
confirmation dialog, after rechecking session, catalog revision, exact identity, and waypoint data.
The bottom of its outline lines up with the details content bottom, 4 pixels below the details
viewport. Its tooltip shows `teleport_hint` while disabled, the chat-feedback note while enabled,
and the failure message after a failed attempt until the selection changes. Below the remote tree,
a `ScalableText` footer shows the selected server's display name in `TEXT_MUTED` and its state in
`RemoteRefs.stateColor`; `RemoteWaypointPanel.splitListArea` gives the tree the rest of the
list area and hides the footer when no server is selected or the tree would drop below one row.
Both lists use 20-pixel rows and `WaypointRowRenderer.background(...)` / `initials(...)` for waypoint
color washes, hover/selection outlines and initials badges. The initials method returns the badge
width for label placement. `WaypointRowRenderer.icon(...)` centers a 16-pixel item or VoxelMap image
over the minimum initials badge width so icon centers stay aligned across rows. Pass the waypoint
RGB to tint VoxelMap images in both local and remote rows. The remote tree
reuses `WidgetTextures` expand/collapse/empty icons,
formatted display names and the tree's clipping and hit testing; exact identities remain in tooltips.
Remote identity tooltips are scheduled at the pointer with `TooltipLayer.scheduleAtPointer` for the hovered entry after the
panel render pass, rather than attaching a widget tooltip to the entire tree rectangle.
Switching
views preserves separate local and remote selection/scroll state; filtering/removal clears an
invisible remote selection. Remote scope is owned by the screen instance, not persisted to disk.
An empty remote tree explains itself: `RemoteBrowserModel.emptyReason(catalogState, hasServers,
selectedServer, query, dimensionScope)` returns an `EmptyReason` (unauthorized, no servers, an
unavailable selected server, a search miss, an empty server or an empty dimension, checked in that
order), and the tree renders its translation through a retained muted `ScalableText` at the first
row's text position, wrapped to the content width minus 5 pixels on each side.

Waypoint and waypoint-list `name` fields are always unformatted plain-text identities used by
commands, lookup, sorting, searching, initials, and external map integrations. They are also the
default display names. An optional serialized `display_name` override retains raw Minecraft JSON
input and must be converted with `TextHelper.parseFormattedText(...)` only at the client render boundary. `WaypointListWidget`
renders these display components directly and exposes a waypoint's optional formatted description
as its row tooltip. `OptimizedWaypointRenderer` keeps parsed display components in its render-thread
snapshot and draws up to six wrapped description lines below the hovered HUD label. Xaero's Minimap
and VoxelMap must continue to receive the plain `name` because they cannot render formatted text.

Composite widgets must forward all relevant behavior to their children: position and offsets, dimensions where supported, rendering, focus, input, and `visitWidgets`. `DialogWidget`, `IntegerSlider`, and `SwatchWidget` are useful references.

### Settings lists

`ClientConfigSettings.RENDERING` declares the client screen's waypoint rendering rows. Its
`RENDER_UNDER_F1` boolean defaults to off and uses the existing toggle and reset controls.
The world renderer reads `ClientConfig.isRenderWaypointsUnderF1()` each frame, so both a toggle
and a reset apply immediately; the option never bypasses loading-screen suppression.

Use `SettingsListWidget` for a scrollable panel of settings. It holds `SettingsListWidget.Header`
entries (a title followed by a line) and `SettingsListWidget.Row` entries: a label, a control, an
optional muted unit (`suffix`), an optional last-column widget (`action`, such as a reset button)
and an optional `tooltip` supplier. Entries have their own heights, so long labels wrap onto more
lines instead of being clipped. The unit and action columns are as wide as their widest entry, so
every control's right edge lines up. A control can be one widget, a non-interactive element such as
a `ScalableText`, or a composite such as a `WidgetStack` of buttons: the row owns every widget the
control's `visitWidgets` reports, and it can take focus while any of them is active.
Row labels use 85% text scale and a 6-pixel indent beneath full-size section headers. Label wrapping
and preferred width account for both the scale and indent. `ClientConfigScreen` keeps numeric units
in hover text rather than adding suffixes, leaving a 4-pixel gap between controls and reset buttons.
`SettingsListWidget.ROW_TEXT_SCALE` also sets its row buttons, toggles, status text and numeric fields
to 85% scale, with smaller control
heights and reset icons. The screen leaves slider track widths unchanged for pointer precision.
Per-row reset buttons are 9 pixels square with a 2-pixel icon inset, shrinking the arrow along
with the button bounds and matching the toggle height.
`TranslucentButton.setTextScale` and `ToggleButton.setTextScale` scale their centered text; callers
size the controls separately. `DrawContextHelper.drawScaledText` draws at a screen-space anchor.
The `IntegerSlider` overload with `controlScale` scales its number field, track height and field
hit bounds together, transforming pointer coordinates for cursor placement. Its internal field
keeps native dimensions during drawing and input so vanilla text selection matches the visual scale.
`Row.action(widget, BooleanSupplier)` conditionally shows an action while reserving its column,
so controls do not shift when it disappears. Hidden actions are excluded from rendering, input,
tooltips and Tab navigation. Call `refreshWidgetVisibility()` after the condition changes; scrolling
and relayout also recheck it. `ClientConfigScreen` uses this for reset buttons, which appear only
when their setting differs from its default and disappear immediately after a reset.

`SettingsListWidget.WideRow` is a third entry type: a control with no label column, unit, action or
tooltip, built as `new WideRow(control)`. The control starts at the row's left edge, can use the
whole row width and is centered vertically. The row is as tall as the control plus 3 pixels above
and below, and at least 17 pixels (`SettingsListLayout.wideRowHeight`); `getPreferredWidth()` counts
the control's visual width. A wide row gets no `ROW_HOVER_BACKGROUND` fill and no tooltip
(`SettingsListWidget.highlightsOnHover` is true only for a `Row`). Otherwise it follows the `Row`
rules below: it owns every widget its control's `visitWidgets` reports, those widgets are visible
only while entirely in view, a partly visible control is drawn clipped with the mouse off-screen,
the row is a Tab stop while any of its widgets is active, and `reveal` and `revealTabTarget` treat
it like a `Row`.

- **Layout:** `setEntries` copies the entries and lays them out. Call `relayout()` after a label,
  unit or control size changes. `getPreferredWidth()` is the width at which nothing wraps, including
  the always-reserved scrollbar column; `getContentHeight()` is the total entry height. The widget's
  bounds are the rows' area; its `Padding` adds `PANEL_PADDING` on each side for the themed panel,
  so size it with `setVisualWidth`/`setVisualHeight` and position it `PANEL_PADDING` inside the
  panel. The pure geometry lives in `SettingsListLayout`.
- **Registration:** call `list.visitWidgets(this::addRenderableWidget)`. It visits every row's
  control and action, then the list itself; that order keeps the list from taking clicks meant for
  its rows. The list renders all of them once, inside its scissor. Don't render row widgets again.
- **Visibility:** the list owns `visible` for its row widgets. A widget is visible only while it's
  entirely inside the rows' area, so clipped parts can't be clicked or focused. Partly visible
  widgets are still drawn, clipped, with no hover state, including those of a composite control:
  while any widget its `visitWidgets` reports is hidden, the list makes those widgets visible for
  one draw of the whole control with the mouse off-screen. Its fully visible widgets then lose
  their hover state and tooltips too, until the control scrolls fully into view; clicks still reach
  them. Screens set only `active`. A control that handles input itself, like `IntegerSlider`, must
  check `isActive()`, which includes `visible`: before 1.21.5, vanilla offers every click to every
  child, hidden or not.
- **Input:** offer the mouse wheel to the list before `super.mouseScrolled`, so it scrolls while the
  list overflows and reaches a slider under the cursor only when it doesn't. The list isn't a Tab
  stop. Vanilla Tab skips invisible and inactive widgets, so before the screen handles Tab, call
  `revealTabTarget(screen, forward)`, with `forward` false for Shift-Tab: when the next stop in the
  screen's Tab order, wrapping around, is a hidden row widget, it scrolls that row into view. Tab
  then reaches every row, and wraps from the last stop to the first row even when the list is
  scrolled. After a key press moves focus to a row widget, call `reveal(focused)`, which scrolls the
  row and the nearest rows with an active widget into view. If the wheel hides the focused widget, clear focus. A relayout or a focus change
  made in code can hide it too, as when a longer status message shrinks the list; then reveal it
  instead, and clear focus only if its row can't be shown. `ClientConfigScreen.keepFocusVisible`
  does this after every relayout and every focus request.
- **Tooltips:** the hovered row gets `ROW_HOVER_BACKGROUND`. After the pointer rests on a row for
  500 ms, the list schedules the row's tooltip at the cursor, except over the row's action, which
  shows its own tooltip. Headers and wide rows get neither.
- **Limitations:** row controls can't open popups, because the scissor would clip them, unless the
  screen renders the popup separately (`setRenderPopupSeparately(true)`, then `renderPopup(...)`
  after the list) and closes it when the list scrolls.

### Confirmation dialogs

`ConfirmationDialog` takes an optional confirm label; the shorter constructor keeps "Confirm". Both
buttons come from `TranslucentButton.fitted`, so they're at least 50 pixels wide and grow to fit
their text, and longer translations don't overflow. `getCancelButton()` returns the Cancel button so a screen can focus it when the dialog
opens. `DialogWidget` receives its buttons through its constructor, and the first one sits on the
right. Register the buttons through the dialog's `visitWidgets`, keep them inactive while the dialog
is hidden, and render the open dialog on a later layer. Escape should close an open dialog before the
screen; `ClientConfigScreen` shows the pattern.

### Color pickers

`HSVColorPicker` and `RGBColorPicker`, which `SwatchWidget` combines, extend
`Abstract3ChannelColorPicker<T>`. It owns three sliders, sends mouse, wheel and key input to the
slider under the cursor, and reports each change through its `ColorPickerCallback`. A subclass
implements `getColor()`, `setColor(int)` and the three protected `onChannelNUpdate()` hooks, which
run after a channel changes and refresh the other sliders' gradients.

Its sliders extend `Abstract3ChannelColorPicker.ColorGradientSlider`, not `AbstractColorBgSlider`
directly. Every gradient shows the picker's current color at its handle, so a fixed or inverted
handle color can vanish against it: the inverse of `#808080` is `#7F7F7F`. The picker wires each
slider to its `getColor()`, and the slider draws its handle in `ColorUtils.getContrastColor(...)`
of that color, black or white with at least 4.5:1 contrast. The color is read whenever the handle is
drawn, so no input path can leave a stale one. Use `getContrastColor` for a marker over an arbitrary
color and `getSafeTextColor` for text, which favors white.

`AbstractColorBgSlider.getHandleColor()` is the hook that picks a slider's handle color. Its default
is the themed `ACCENT`, or `SLIDER_THUMB_DISABLED` while the slider is inactive, and a
`ColorGradientSlider` keeps the disabled color. The handle is one pixel wide and stays inside the
slider on every version.

### Tooltips

`TranslucentTooltip` is the only tooltip surface in the mod's screens. `TooltipLayer` holds the
frame's request and `MovementAllowedScreen` draws it last, so a screen needs no tooltip drawing of its
own. A widget drawn outside a `MovementAllowedScreen` shows no tooltip, because nothing draws the layer.
`TooltipLayer` is static frame state for the render thread, so a test that schedules a tooltip
clears it before and after.

- **Look:** the `POPUP_BACKGROUND` fill, a one-pixel `BORDER` outline and `TEXT_PRIMARY` text with a
  shadow, all resolved each time the tooltip is drawn, so a theme change shows in the next frame. The
  text wraps at 170 pixels like vanilla's tooltip, and the box extends 4 pixels past the text on every
  side. The two dark themes and Classic have a transparent default border, so their tooltips have no
  outline.
- **A control's tooltip:** call `setTooltip(Component)` on the control, and remove it with
  `setTooltip((Component) null)`: a bare `null` is ambiguous next to vanilla's `setTooltip(Tooltip)` and
  does not compile. A class shows its tooltip only if it calls `scheduleTooltip(mouseX, mouseY)` at the
  end of its renderer and `narrateTooltip(output)` at the end of `updateWidgetNarration`. These classes
  do: `TranslucentButton`, `IconButton`, `ColorSquareButton` (and `RandomColorSquareButton`),
  `AbstractDropdownMenuWidget` with its `AbstractMenuItem`s, and the manager's `IconToggleButton`. Any
  other class ignores `setTooltip` without an error. Toggles, sliders, text fields and the swatch widget
  are such classes today, so add the two calls to a class before giving it a tooltip.
- **A hovered item's tooltip:** `TooltipLayer.scheduleAtPointer(text, mouseX, mouseY)` takes
  screen-space coordinates, never coordinates after a render translation. Its owner decides when to call
  it, as the rails and the remote tree do at once, and settings rows and form fields do after the
  pointer rests for 500 ms.
- **One tooltip per frame:** the first request wins, and only a focused control's request replaces it.
  A control requests its tooltip while hovered, and while focused by keyboard after Tab or an arrow key.
  A control focused by a mouse click and no longer hovered requests nothing. An inactive control shows its
  tooltip too, as in vanilla. A control drawn with `NO_MOUSE` is not hovered, so it requests no hover
  tooltip; one focused by keyboard still requests its tooltip below or above, so move focus off a control
  that a dialog or popup covers.
- **Placement:** vanilla's three rules, anchored to the control's visual bounds so distances are
  measured from the visible outline. At the pointer, the tooltip sits right of it and above it. For a
  hovered control it sits right of the pointer and below the control, lower the further down the
  control the pointer is, or above the control near the screen's bottom. For a control focused by
  keyboard it sits below the control, or above it near the screen's bottom. Each rule flips or shifts
  left at the right edge. Besides the visual-bounds anchor, the rules change from vanilla in two ways: the
  box's top stays on the screen, and a control with no height counts as one pixel high.
- **Layering:** `MovementAllowedScreen` clears the layer when a frame starts and draws it after
  `renderScreenContents`, through `nextTooltipLayer`/`previousTooltipLayer`, so a tooltip is above
  popups, the swatch and item icons on every version.
- **Narration:** `narrateTooltip` adds the tooltip as a hint, as vanilla's tooltip does. Pointer
  tooltips are not narrated.
- **Never vanilla's `Tooltip`:** do not use `Tooltip`, `setTooltipForNextFrame` or
  `setTooltipForNextRenderPass` in the mod's screens. Vanilla's `setTooltip(Tooltip)` still exists on
  every widget and would bring back the vanilla box.

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
10. If it can show a tooltip, end its renderer with `scheduleTooltip(mouseX, mouseY)` and `updateWidgetNarration` with `narrateTooltip(output)`; without both calls, `setTooltip` does nothing.
11. Add focused unit tests for pure geometry, layout, parsing, or state transitions.

A minimal interactive widget follows this shape:

```java
//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.ButtonClickCallback;
import _959.server_waypoint.common.client.gui.layout.Expandable;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public final class ExampleButton extends ShiftableButtonWidget implements Expandable {
    private final ButtonClickCallback callback;

    public ExampleButton(int x, int y, int width, int height, Component message, ButtonClickCallback callback) {
        super(x, y, width, height, message);
        this.callback = callback;
        this.setX(x);
        this.setY(y);
    }

    @Override
    protected void onPress() {
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

- Extend `MovementAllowedScreen` for the normal in-world GUI behavior used by this project. It provides the themed `SCREEN_BACKGROUND`, centering helpers, and optional movement-key forwarding. When no world is loaded, as when a screen opens from a mod list, it draws vanilla's background for screens outside a world (the panorama on 1.20.5 and later) under the themed overlay. It inherits vanilla's singleplayer pause behavior. `WaypointManagerScreen` always returns `false` from `isPauseScreen()`. `WaypointEditScreen` pauses while editing, but returns `false` while an edit request is pending so the integrated server can tick and send its queued result. A result, send failure, or timeout clears that pending state and restores pausing if the edit form remains open. Add, client settings, and theme settings screens pause regardless of their parent.
- Extend `AbstractWaypointPropertiesScreen` for a new add/edit-style waypoint properties flow. Shared fields, coordinate rules, suggestions, color selection, checks, layout, and overlay behavior belong in this base. A subclass supplies what differs through methods the base calls from `init()`, after both constructors have finished, so the constructors call none of them: `leadingRows()` (Add's Dimension and List rows above Name), `subtitle()`, `hasDisplayNameRow()`, `footerButtons()` and `primaryButton()`, `checkInput()`, `submit()`, `pendingMessage()` and `refreshButtons(...)`, and optionally `hasChanges()`, `initialFocus()` and `onTick()`. `refreshControlStates()` sets every control's `active` flag and the footer message from the color picker, a pending request, the check result and whether Edit's form differs from the saved waypoint, so no screen keeps a list of controls to disable. `setControlActive(...)` also locks and restores text editability when activation changes, because older vanilla text fields accept typing even with `active = false`. Text length limits are set before saved values are loaded, preserving long names, keywords and descriptions.
- Extend vanilla `Screen` directly only if movement forwarding and the shared centering contract are deliberately not wanted.

Use current screens as focused examples:

- `WaypointManagerScreen` demonstrates pure, unit-tested geometry (`calculateLayoutGeometry` and `calculateSidebarLayout`), a `WidgetPack` for the search field and list, `TreeViewWidget` lists, sorting controls, and responsive resizing.
- `WaypointManagerScreen` forwards screen ticks to `WaypointListWidget.refreshDistanceSortIfPlayerMoved()`. The widget caches the last query origin and only rebuilds distance-sorted rows after the player's block position or relevant dimension changes.
- `WaypointManagerScreen` separates full refreshes, dimension-list changes, and ordinary waypoint mutations. `updateAllWidgets()` rebuilds the dimension rail and refreshes waypoint rows exactly once. `updateWidgetsForDimensionListChange(...)` rebuilds the dimension rail but refreshes waypoint rows only when selection fallback or the active viewing scope requires it. `updateWaypointWidget(...)` skips dimension-name copying and sorting entirely, and it ignores changes outside the selected dimension unless all-dimensions mode is active. The selection is preserved by name and falls back to the current or first available dimension. Callers report the changed dimension instead of passing waypoint-list snapshots because `WaypointListWidget` owns the active search, sort, grouping, and dimension-scope query state.
- The manager's dimension rail includes empty dimensions that have no synchronized waypoint file. In an integrated world it reads the integrated server's level keys directly. On a remote connection it asynchronously extracts fully namespaced dimension identifiers from the `/wp list ` command suggestions, merges them with the synchronized client cache as a fallback, and ignores the command's literal list/search/sort options.
- In all-dimensions mode, the waypoint-list scroll position and grouped dimension-node expansion choices are session-scoped static widget state, so both survive closing and reopening the manager as well as ordinary dimension changes. Scroll restoration is deferred until the reconstructed widget has rows and a real maximum scroll range. Selected-dimension mode never remembers scroll and resets to the top when its scope is selected. `WaypointClientMod.onJoinServer()` calls `WaypointManagerScreen.resetSessionWidgetStates()` so connecting to another server or opening another local save also starts at the top with every dimension expanded.
- `WaypointManagerScreen.resolveViewState(integratedServer, networkState)` picks `LOADING` (`NOT_READY` or `HANDSHAKE_FINISHED`), `UNSUPPORTED`, `INCOMPATIBLE` or `READY` (`SYNC_FINISHED`, or any state in an integrated world). A non-ready build registers no widgets and shows one centered `ScalableText` message. `tick()` calls vanilla `rebuildWidgets()` when the resolved state changes, so a manager opened during sync builds itself in place when sync finishes. In the remote view a catalog session change still closes the screen; in the local view it rebuilds the screen, and the ready build rebinds `RemoteWaypointPanel` to the new session, clearing its selection and the requested dimension catalog. `removed()` clears the static `isRendering`/`activeScreen` registration, so a manager closed by teleport or `setScreen(null)` stops receiving refresh calls; returning from a child screen re-runs `init()`, which re-queries the list. Because the client reports `NO_SERVERSIDE_SUPPORT` until a dedicated server's handshake arrives, a manager open in that window briefly shows the unsupported message.
- `AbstractWaypointPropertiesScreen`, `WaypointAddScreen`, and `WaypointEditScreen` demonstrate a compact, fixed form. `WaypointFormLayout` is pure and unit-tested: from measured sizes it works out the label and control columns, the gap between rows (9 pixels, down to 5 when the screen is short), the dividers and the footer, and the screen places its widgets from the answers with `placeOutline` and `placeInRow`, which position any widget by its outline whatever its anchor. Layout runs in `init()`, when the footer message changes because a wrapped message changes the footer's height, and on resize through `repositionElements()`, which keeps the widgets, so values, focus, the message and a pending request survive; it never runs every frame. `WaypointFormCheck` runs on every edit and tick and reports the first problem: a hint or an error blocks Add and Save and the footer says why, an error's field gets a `DANGER` outline through `setInvalid`, and a note doesn't block. `WaypointAddScreen` sends `/wp add`, locks the form with a `PendingAdd`, closes when the waypoint appears in the synced data and unlocks with a message after 5 seconds. `WaypointEditScreen` captures the list revision, builds one atomic patch with `WaypointFormPatch`, keeps entered values until a matching server result accepts the edit, and unlocks with that result's message otherwise. Its Display name field holds only the override, and an empty field over a saved override clears it. The add screen treats its name field only as the exact identifier and creates no display-name override. Resting the pointer on a field's label or controls for 500 ms shows that field's tooltip at the pointer through `TooltipLayer.scheduleAtPointer`, but not over the remove-icon button, which has its own, nor while a popup, the color picker or a pending request is open.
- `ClientConfigScreen` demonstrates `SettingsListWidget` with per-row reset buttons, a footer built with a `WidgetPack`, and confirmation dialogs that disable the underlying controls, close on Escape and return focus to the button that opened them. Its Map mods rows depend on which map mods the loader supports (`MapModIntegrations.find`) and the player installed; the pure rules live in `ClientConfigSync`, and the settings themselves in `ClientConfigSettings`. It saves the config in `removed()`, which every exit reaches.
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
5. The tooltip, drawn last by `MovementAllowedScreen`.

Use `nextLayer`/`previousLayer` around suggestions and overlays when they must appear above normal controls.
Use `nextItemOverlayLayer`/`previousItemOverlayLayer` for marks drawn over GUI item icons, such as the
server rail badges. On 1.21.6 and later both pairs start a new render stratum, but before 1.21.6
`nextLayer` moves drawing up by only 1 in z while vanilla draws GUI item models near z 150;
`nextItemOverlayLayer` translates 200, the depth vanilla uses for item stack counts.
The waypoint form's modal `SwatchWidget` uses this item-overlay pair so its background and
controls cover the item preview on versions before 1.21.6.
`nextTooltipLayer`/`previousTooltipLayer` are for the tooltip alone: `TooltipLayer` calls them, and screens
do not. Before 1.21.6 `nextTooltipLayer` translates 400, vanilla's tooltip depth, above the item overlay
layer; from 1.21.6 it starts a stratum.

#### 4. Input: preserve focus and text entry

Let registered widgets receive ordinary input through the screen. Intercept only behavior the screen must prioritize:

- `MovementAllowedScreen.setFocused(...)` changes focus only when the target listener changes.
  Older vanilla containers unfocus and refocus even the same listener after a handled click, which
  closes a dropdown immediately after its arrow opens it. Reassigning the focused control preserves
  its popup; moving focus to another control or clearing focus still closes it.
- Suggestion clicks must be checked before delegating to `super.mouseClicked`.
- The wheel needs no such check: vanilla gives it to the widget under the pointer, and a popup hangs
  outside its owner, so `MovementAllowedScreen.mouseScrolled` first offers it to the focused
  `PopupOwner` through `scrollPopupIfOver`. A screen that overrides `mouseScrolled` keeps that
  behavior by calling `super.mouseScrolled` for the events it does not use itself, as
  `AbstractWaypointPropertiesScreen` and `ClientConfigScreen` do. `mouseScrolled` takes three
  doubles up to 1.20.1 and four from 1.20.2, so split the override with a Stonecutter predicate.
- Vanilla closes the screen on Escape before the focused child sees the key. Before that,
  `MovementAllowedScreen.keyPressed` calls `dismissFocusedInput()` to close the focused
  `PopupOwner`'s open menu or suggestion list and clear focus. A focused `EditBox` or
  `ComboBoxWidget` also yields focus and consumes Escape when no popup is open, and so does an
  `IntegerSlider` whose number field rather than its track has focus (`isEditingNumber()`); an
  `IntegerField` commits its number when it loses focus. So the first Escape leaves text entry
  and a second Escape closes the screen. Do not add per-widget Escape
  intercepts. A screen whose Escape handling does not reach `super.keyPressed` calls
  `dismissFocusedInput()` first, as `WidgetThemeConfigScreen` does. Compare against
  `InputConstants.KEY_ESCAPE`, never 256, which is not Escape on 26.3.
- A focused `ShiftableButtonWidget` is pressed by Enter, Space and keypad Enter, but
  `MovementAllowedScreen` also forwards movement keys, and Space is the default jump key. Each
  press is handled once. After Tab or arrow-key navigation (vanilla's
  `getLastInputType().isKeyboard()`), the focused button takes its activation keys and they are
  not forwarded as movement. A mouse click also leaves the clicked button focused, so after one an
  activation key that is bound to movement (Space) only moves the player, while Enter and keypad
  Enter still press the button. Inactive or hidden buttons claim no keys, and other movement keys
  keep moving the player while a button is focused.
- Screen shortcuts should normally be disabled while the focused listener is an `EditBox` or
  a `ComboBoxWidget`, whose editable field is owned by the composite.
  Shortcuts that run before `super.keyPressed`, such as the manager's `C` binding and the list's
  T teleport, must leave Enter, Space and keypad Enter to the focused widget.
- Call `acceptMovementKeys(false)` while text entry or another control must own movement-key input.
  On Forge and NeoForge, `MovementAllowedScreen` temporarily extends the seven movement bindings'
  conflict contexts so they stay active while this screen accepts movement. Modifier checks and
  original conflict rules remain in effect. Rebuilds retain the original contexts, and `removed()`
  restores them; overrides must call `super.removed()`.
- A modal should disable underlying controls and move focus into the modal; restore both when it closes.
  On a `MovementAllowedScreen`, override `hasOpenModal()` to report it: from 1.20.5, vanilla moves
  focus to the next Tab stop after every rebuild, such as a resize, when the keyboard was used last,
  which would take it from a dialog's Cancel button to its confirm button. The screen skips that
  while a modal is open, so the focus `init()` gives the modal stands. `ClientConfigScreen` shows the
  pattern.

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

The Configure button in the Appearance section of `ClientConfigScreen` opens `WidgetThemeConfigScreen`. The screen exposes every `WidgetThemeVariable`, previews edits immediately through `WidgetThemeManager`, and keeps opacity separate from the RGB field and swatch. Keep that separation: the existing RGB-oriented color controls normalize their own values to opaque RGB, while many theme surfaces intentionally use translucent ARGB colors.

The editor body follows a two-column layout. The left column keeps the scrollable variable list above the selected variable's RGB and opacity controls; the list's declared rectangle is its complete visual rectangle, including its two-pixel decoration, so it shares the lower panel's left/right edges without narrowing the column gutter. The outer, column, and body-to-footer gutters use the same eight-pixel rhythm, and the two left panels share their adjoining border. The RGB field and `ColorSquareButton` are composed with visual bounds so their outlines stay adjacent and exactly aligned. The right column is a live widget gallery built from the existing text, text-field, button, toggle, and slider implementations, plus small semantic surface/status samples. Gallery controls use deferred theme-variable values, remain interactive so hover/focus/selected states can be inspected, and update immediately with the draft theme. Reset and save-error feedback occupies the otherwise-unused bottom strip inside the gallery rather than reserving a larger empty band above the footer.

The gallery is screen-local rather than a reusable widget API. Its interactive children are registered individually for input, while its `WidgetStack` owns their one manual high-level render pass. When the modal swatch opens, disable the interactive gallery samples along with the editor controls; a deliberately disabled gallery sample must remain disabled when the modal closes.

The package-private `WidgetThemeEditorSession` owns the editing transaction; it is an implementation seam for the screen, not a public theme API:

- `setColor` updates the immutable draft and publishes it for live preview.
- The theme dropdown selects Custom, Translucent Dark, Modern Dark, High Contrast, or Classic and previews immediately. It reuses `AbstractDropdownMenuWidget`, registers once, routes popup clicks before covered controls, and renders the popup after the body. Escape closes the dropdown first; the swatch modal disables it.
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
3. `mouseClickedSuggestion` checked for the left mouse button before normal screen click dispatch
   while that field is focused. A consumed click must establish screen drag state with
   `setDragging(true)`, so subsequent drag and release events reach the focused field.

`AbstractWaypointPropertiesScreen` is the reference for several fields, while `WaypointManagerScreen` shows the same pattern for a single search field. If any one of the three pieces is missing, suggestions may exist internally but fail to appear or accept clicks. Escape needs no fourth piece on a `MovementAllowedScreen`: the focused field closes its list before the screen closes. Nor does the mouse wheel: the screen offers it to the focused field through `scrollPopupIfOver`, so a list of more than five suggestions scrolls while the pointer is over it.

The wheel moves the list at least one row per event, like the choice dropdown, and stops at both ends. The row under the pointer becomes the selected one, as when the pointer moves, so the suggestion Tab accepts stays in view; a selection left outside the window would be scrolled back to at the next redraw. A list that fits still takes the event, so it does not scroll whatever it covers. Hover selection counts only the pixels inside the list, so the row just below a scrolled list cannot select a hidden suggestion.

`SuggestingTextInput.getSuggestionsY(int suggestionHeight)` positions a popup after its visible height is known. A `ComboBoxWidget` uses its expansion direction for both its choice list and typed suggestions: upward suggestions end at the top of the field, and downward suggestions begin below it. Keep render, hover, and click bounds on that same computed rectangle.

Lists with more than five matches show a three-pixel themed scrollbar inside their right border.
Labels and selection fills reserve space for it; standalone lists widen when needed to keep the
longest label readable. Clicking the track or dragging the thumb scrolls without accepting a match.
Dragging keeps the thumb's grab position, clamps at both ends, and stops on release, dismissal,
or focus loss. Hovering the scrollbar does not select a row; wheel and thumb scrolling keep the
keyboard selection within the visible window. `ComboBoxWidget` forwards drag and release events
to its input only while that input has captured a scrollbar click, after offering them to its
choice dropdown. Other comboboxes must leave the focused field's release event alone.

## Waypoint icon picker and renderer

The icon combobox uses vanilla `/give` item-ID matching through
`SharedSuggestionProvider.matchesSubStr`: a query without a colon matches the namespace or item
path, including word starts after underscores; a query with a colon matches the full ID. Matching
is case-insensitive. VoxelMap IDs require a non-empty namespace match: `vox` and `voxelmap:st`
can suggest `voxelmap:star`, while bare `star` and an empty query cannot. The full choice popup
still includes VoxelMap icons. Typing `diam` suggests
`minecraft:diamond`, and `sword` suggests `minecraft:diamond_sword`; acceptance inserts the full
ID. All matches stay reachable through the suggestion list without a result cap.

Complete known icon IDs use yellow input text (`#FFFF55`) while focused and the normal field text
color (`TEXT_PRIMARY`, white in the default theme) when unfocused. Invalid and partial non-empty
inputs remain red (`#FF5555`) regardless of focus. Bare item IDs use the `minecraft` namespace,
so `diamond` is valid while
bare `star` does not select a VoxelMap icon. Empty input retains the normal themed placeholder.
Valid typed text updates the selection without replacing the text, so entering `diamond_sword`
is not interrupted when the intermediate text reaches `diamond`. Partial input keeps the saved
selection, as before.

`WaypointIconPicker.preview(mouseX, mouseY)` resolves a hovered menu/suggestion value first, then
the current input. Invalid, partial and empty inputs fall back to initials when no candidate is
hovered. Hover previews never change the saved selection or fire an edit callback. The shared
add/edit screen passes real popup coordinates to the preview even while the popup suppresses
underlying hover, and `NO_MOUSE` while the color modal covers the form.

`WaypointIconPicker` owns a searchable `ComboBoxWidget` whose placeholder reads "None — shows the initials", a 13×13 `IconButton` that removes the icon, and the selected nullable `NamespacedId`. The button draws `WidgetTextures.CLEAR_ICON`, is inactive while no icon is selected, which tints its icon with `TEXT_DISABLED`, and has a "Remove icon" tooltip. Add and edit screens register the menu and button once for input, render the menu's popup after the main form, and read `getSelectedIcon()` when submitting. `setSelectedIcon()` restores a saved choice, including an ID missing from the current client registry; it does not send an edit. The picker lists the current item registry and known VoxelMap image IDs. Search filters the catalog while a partial query leaves the saved choice intact.

The icon combobox keeps the full catalog in its popup. `AbstractDropdownMenuWidget.setMaxPopupHeight()` limits the visible vertical rows; the remaining choices stay reachable with the wheel, arrow keys, or draggable scrollbar. `setExpansionDirection()` lets the owning screen place the popup above or below its control. The add/edit screen chooses the roomier side, caps the popup to eight rows and the available screen space, and routes wheel input to the open popup before other controls. When it handles a popup click before vanilla dispatch, the screen must establish drag focus and forward release events so scrollbar dragging works. Popup click and hover handling must use only the visible rows so covered form buttons cannot accidentally receive a click intended for the popup.

Vertical popup choices retain their logical top-to-bottom order when opening upward; Up and Down follow that visual order. Resting upward combobox rows draw their top border and share the bottom border with the next row or trigger via `renderOutlineWithoutBottom`; resting downward rows use `renderOutlineWithoutTop`. Hovered or keyboard-highlighted rows draw all four edges within the row area to the left of any scrollbar. In the waypoint form, the icon row is the 11-pixel preview, 4 pixels, the stretched dropdown, 4 pixels and the remove button, starting at the control column.

`WaypointIconRenderer.resolve()` converts a stored ID to an item stack, a packaged VoxelMap texture, or the initials fallback on the client thread. Use `drawForWaypoint()` for saved waypoint icons in local and remote rows, details, and the add/edit preview; pass the waypoint RGB or the form's selected color. It leaves item icons unchanged and multiplies VoxelMap texture pixels by that color. The picker catalog lists icon IDs and has no waypoint color to apply. Use `drawScaledWorldItem()` for an in-world item and `drawScaledVoxelMap()` for an in-world VoxelMap texture. Both use the waypoint background alpha setting and become opaque for the waypoint whose hover details are shown; GUI icons remain opaque. The VoxelMap tint matches its in-world rendering. Before Minecraft 1.21.6, the immediate draw uses a scoped shader color and alpha; on Fabric through Minecraft 1.21, `WaypointBlockItemAlphaMixin` also replaces the vanilla cutout block sheet with the translucent block sheet within that draw scope when alpha is below 255, because the cutout sheet disables blending. Ordinary GUI icons and fully opaque world icons retain their original render types; on newer versions, item render states carry a scoped premultiplied tint to their atlas or oversized-item blits while VoxelMap textures use a colored GUI blit. The world marker stacks either icon above its colored initials badge, with both centered horizontally; the original badge stays at its projected position. The 16×16 icon background is temporarily disabled with `DRAW_ICON_BACKGROUND` for comparison testing; the initials badge keeps its colored background. Neither part has an outline. Use the full stacked bounds for projection culling, and let either visible part of the stack trigger the existing name and distance hover without making the gap hoverable. Expand the name at the colored badge's position and put the distance below it, leaving the icon above. Keep the stored ID when resolution falls back to initials. World rendering caches the resolved handle in per-waypoint state and clears it when the waypoint or scene is removed. Re-resolve after resource or scene rebuild so resource-pack changes are reflected.

Through Minecraft 1.21, VoxelMap texture draws explicitly enable standard alpha blending and
disable it after the immediate blit. The vanilla uncolored texture blit does not configure blending,
so shader alpha and transparent image pixels otherwise depend on the previous draw's blend state.
This applies to world markers and GUI previews; later GUI texture pipelines configure blending.

VoxelMap resource paths follow the configured dependency's layout: before Minecraft 1.21.11,
`voxelmap:star` resolves to `voxelmap:images/waypoints/waypointstar.png`; from 1.21.11 onward,
it resolves to `voxelmap:images/waypoints/selectable/star.png`. Both `voxelmap:waypoint` and
`voxelmap:point` use the default image (`waypoint.png` in the earlier layout, `selectable/point.png`
in the newer layout). Keep this version selection in the shared resolver so the picker, forms,
lists, details, and world markers all use the same resource handle.

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

### Tooltip position for scrollable widgets

`setTooltip(Component)` describes a whole control, and `TooltipLayer` places it beside the control's
visual bounds (see [Tooltips](#tooltips)). For a hovered item inside a tall or scrollable widget,
resolve the item from the current mouse coordinates and schedule its tooltip at the cursor instead;
otherwise the tooltip can appear far from the item, especially after scrolling or resizing.

- Calculate the hovered item using the widget's current viewport and scroll position. Schedule
  nothing when the pointer is outside an item, the widget is inactive, or the item is clipped.
- Pass the screen-space `mouseX` and `mouseY` to `TooltipLayer.scheduleAtPointer(...)`, which needs
  no version branch. Do not pass coordinates after a render translation or the item's local position.
- Schedule the tooltip during the hovered content's owning render pass. `IconListWidget`,
  `RemoteWaypointPanel.BrowserTree`, `SettingsListWidget` and the waypoint form all schedule through
  `TooltipLayer`, and each decides for itself when its tooltip shows.
- Check the result in game with the first and last visible items, a scrolled list, a resized screen,
  and items near screen edges. Compilation cannot confirm tooltip placement.

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
- Keyboard activation, and routing a key between the focused widget and movement forwarding.
  `MovementAllowedScreenButtonKeyTest` shows a screen double that overrides
  `isKeyboardNavigating()` and `testMovementKeysDown(int)` instead of reading the game's input
  state and key mappings.
- Composite widgets built around a test double. `IntegerSlider`'s protected constructor takes the
  `IntegerField` to use, so `IntegerSliderTest` and `MovementAllowedScreenPopupEscapeTest` pass a
  number-field double instead of setting private fields by name through reflection, which a rename
  would break only at run time. From 26.1, an editable `EditBox` asks the game to start text input
  when it takes focus, so a unit test can't focus a real text field. `MovementAllowedScreenPopupEscapeTest`
  stands in for a combo box with a `ComboBoxWidget` subclass that skips the constructor and reports
  no suggestions open, which is why `ComboBoxWidget` isn't `final`.
- A suggestion list. `SuggestingTextInputTest` builds a real `TranslucentTextField` with `TestFont`
  and overrides `isFocused()` to report focus, since a list shows only for a focused field. It
  checks the list's rectangle with `isMouseOverSuggestion(...)`, reads which suggestions the rows
  show by clicking one, and drives hover through the package-private `layoutSuggestions(...)`, the
  layout half of `renderSuggestions(...)`, so nothing needs a graphics context.
- Drawing decisions that don't need a real graphics context. `SettingsListWidget.renderPart` is
  package-private and static, so `SettingsListWidgetTest` calls it with a null context and a
  `WidgetStack` double whose render method records its widgets' `visible` flags and the mouse
  position instead of drawing.
- Pure label or presentation calculations.
- A form's geometry, checks, patch and pending-request rules as helpers without Minecraft text
  classes: `WaypointFormLayout`, `WaypointFormCheck`, `WaypointFormPatch`, `WaypointFormInitials` and
  `PendingAdd` have their own tests, and `WaypointFormTranslationTest` checks that every key the
  form uses exists in all six locales with the arguments of English.
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
- Tooltips use `setTooltip(Component)` on a class that makes both calls, or `TooltipLayer.scheduleAtPointer`; nothing imports vanilla's `Tooltip`.
- Hover-dependent drawing reads state only after the high-level wrapper has run.
- Normal and hovered backgrounds are both explicit when the widget should not be transparent while idle.
- Theme-aware drawing resolves semantic roles at render time instead of caching raw colors.
- Built-in themes cover every theme variable, and every theme variable has a `PreviewSample`.
- Live-preview screens restore shared state from `removed()` when edits were not committed.
- Translation keys, textures, and theme roles use their shared resource locations.
- Stonecutter markers are balanced and replacement tokens remain in valid positions.
- The exact active target and a relevant compatibility target compile or test successfully.

### Xaero default-list client setting

`ClientConfigSettings.XAERO_DEFAULT_LIST_DIRECT_SYNC` defines the persisted toggle for the exact
`gui.xaero_default` list. Off (the default) uses a separate owned set; On syncs directly into
Xaero's default set. `forScreen(...)` includes it only for installed Xaero Minimap, immediately
after its auto-sync setting. Use `createSettingRow(...)` for its toggle, tooltip and reset button.
The Xaero Sync now dialog explains that direct mode replaces default-set contents, including
personal waypoints. The choice changes the next synchronization rather than starting one itself.
