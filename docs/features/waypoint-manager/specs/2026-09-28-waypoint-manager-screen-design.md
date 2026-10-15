# Waypoint Manager Screen Design

Status: design agreed on 2026-09-28. Implemented; see the [validation record](../validation/2026-09-28-results.md).

## Intent

`WaypointManagerScreen` has no design document of its own. Its behavior is spread across the
[GUI guide](../../../tips/gui/local-guide.md) and the
[Step 18 remote GUI spec](../../cross-server/specs/cross-server-gui.md). The Step 18 spec is out of
date: it describes a "Server: Local / Remote…" selector and a server → dimension → list → waypoint
tree, both of which the server rail replaced. Several states were never designed:

- While the client is still joining or syncing, `init()` returns before layout and widget
  registration, but the render pass still draws the panels and widgets with zero-size geometry.
- The remote tree says "No remote servers are cached…" for every empty result.
- Per-server availability disappeared when the server rail replaced the server rows.
- The local/remote toggle always appears, although singleplayer and servers with cross-server
  disabled never have remote servers.

This document describes the whole screen as it should behave: the behavior that stays, and the
changes that complete it. It is the reference for the implementation plan.

## Scope

In scope:

1. View states and lifecycle.
2. Sidebar, list panel and details panel layout.
3. Remote server availability.
4. Empty states for the local and remote lists.
5. Text, translations and documentation.

Out of scope:

- Splitting the class or removing its static widget fields.
- A local manager for servers without Server Waypoint (the TODO in `WaypointClientMod`).
- Keyboard navigation in the remote tree.
- Any change to remote data, wire formats or teleport rules.
- A title drawn on screen.

## Screen overview

This section records the behavior that stays as it is.

### Geometry

`calculateLayoutGeometry` is unchanged.

- Three panels (sidebar, list and details) are centered as one group, with 2-pixel gaps between
  them and 4 pixels of padding inside each.
- The sidebar content is one 16-pixel icon wide.
- The list content width is 38% of the scaled viewport width, clamped to 180–360 pixels. The
  details content width is 32%, clamped to 150–320 pixels. When both don't fit inside the
  12-pixel screen margins, they shrink proportionally and all three panels stay visible.
- The content height is 82% of the viewport height, clamped to 120–400 pixels and to the space
  inside the vertical margins.

### Local view

- **List panel:** the search field above `WaypointListWidget`. The list shows the selected
  dimension or all dimensions, flat or grouped by list, sorted by default order, name, distance
  or color, in either direction. The row actions (visibility, edit and remove) don't change the
  selection; clicking a row body selects the row.
- **Details panel:** `WaypointDetailsWidget` for the selected row.
- **Sidebar:** the dimension rail at the top and the controls at the bottom. The rail lists every
  available dimension, including dimensions without waypoint files. It stays selectable in
  all-dimensions mode; its selection is where new waypoints go, and it is the scope that returns
  when all-dimensions mode is turned off.

### Remote view

The local/remote toggle switches the list and details panels in place.

- **List panel:** the same search field above the read-only `RemoteWaypointPanel` tree, scoped to
  the selected server. Grouped mode shows list roots for one dimension, or dimension and list
  roots for all dimensions. Flat mode shows waypoints with their server, dimension and list.
- **Details panel:** a read-only `WaypointDetailsWidget` and the teleport button. Teleport sends
  the command immediately after rechecking the session, catalog revision, exact identity and
  waypoint data, then closes the screen so progress appears in chat.
- **Sidebar:** the server rail sits just above the controls and grows upward. The dimension rail
  shows the selected server's catalog and grows downward. `OpposedExpansionLayout` divides the
  space between the two rails. One-pixel separators divide the dimension rail, the server rail
  and the controls when both neighbors are visible.
- Remote data never becomes a local waypoint, file, render entry or map-mod export. The remote
  selection, scope and collapsed nodes belong to the screen instance and are not saved.

### Shared controls and saved state

- The all-dimensions, group, sort order and sort mode controls apply to both views. Distance
  sorting is unavailable remotely: entering the remote view while sorting by distance switches to
  name sorting, and the distance choice is hidden.
- `ClientConfig` saves the sort mode, sort direction, grouping and all-dimensions mode. The
  all-dimensions scroll position and expanded nodes last for the session.
- Escape closes an open sort dropdown before it closes the screen. `C` opens the client
  configuration when no text field has focus. Movement keys reach the player when no text field
  has focus. Clicks on the open dropdown and on search suggestions are handled before normal
  dispatch.

## 1. View states and lifecycle

A pure function picks the state:

```java
enum ManagerViewState { LOADING, UNSUPPORTED, INCOMPATIBLE, READY }

static ManagerViewState resolveViewState(boolean integratedServer, ClientNetworkState networkState)
```

| State | Condition | Screen content |
| --- | --- | --- |
| `READY` | An integrated server, or `SYNC_FINISHED` | The full manager |
| `LOADING` | `NOT_READY` or `HANDSHAKE_FINISHED` | `server_waypoint.manager.loading` |
| `UNSUPPORTED` | `NO_SERVERSIDE_SUPPORT` | `server_waypoint.no_serverside_support` |
| `INCOMPATIBLE` | `INCOMPATIBLE_PROTOCOL` | `server_waypoint.incompatible_protocol_version` |

### Building

`init()` always calls `super.init()` and records the state it built (`builtState`).

- **Not ready:** register no widgets. Show one retained `ScalableText` message, centered
  horizontally and vertically. Its maximum width is the smaller of the message width and the
  width inside the 12-pixel screen margins, so a short message centers exactly and a long one
  wraps across the available width. This replaces the two messages drawn with `drawText`.
- **Ready:** the current build (layout, registration and dimension sync), plus these steps:
  - Set `isRendering` and `activeScreen`.
  - If the catalog session differs from the session `RemoteWaypointPanel` is bound to, bind the
    new session, clear the remote selection, and discard the requested dimension catalog so the
    new server's dimensions are requested. An unchanged session keeps the remote selection
    across window resizes and returns from child screens.
  - Request the available dimension names if the current session hasn't requested them yet.

`renderScreenContents` draws the message for a non-ready build and the panels for a ready build.

### Switching states

`tick()` runs these checks in order:

1. In a ready build that shows the remote view, a catalog session change closes the manager. This
   is the Step 18 rule, handled by `RemoteWaypointPanel.tick()`.
2. If `resolveViewState(...)` differs from `builtState`, call vanilla `rebuildWidgets()`. Leaving
   `READY` also returns the screen to the local view, so a later ready build never resumes a
   remote view from an earlier session.
3. In a ready build that shows the local view, a catalog session that differs from the panel's
   bound session also calls `rebuildWidgets()`, which rebinds the panel.
4. Otherwise, in a ready build, run the existing per-view tick work and the remote-server check
   from section 2. A non-ready build has no other tick work.

A manager opened during sync therefore becomes the full UI when sync finishes, without being
reopened. Every transition to `NOT_READY` (joining or leaving a server) also clears the catalog
session, so in the remote view check 1 closes the screen before check 2 can rebuild it.

A proxy server switch while the local view is open rebuilds through the states the client reports:
unsupported until the new server's handshake arrives (see [Known behavior](#known-behavior)), then
loading, then ready with the new server's data. Vanilla may replace the manager with its own
loading screen during a switch; the manager is then removed like any closed screen.

### Lifecycle

- `removed()` clears `isRendering` and `activeScreen`, not only `onClose()`. A manager closed by a
  teleport, a session change or `setScreen(null)` then stops receiving the static refresh calls
  (`updateAllWidgets`, `updateWidgetsForDimensionListChange` and `updateWaypointWidget`).
- Opening the add or config screen also calls `removed()`. Returning calls `init()`, which already
  re-queries the list through `setSelectedDimension`, so changes made in the meantime appear.
- `onClose()` is otherwise unchanged. The static widget references stay until the next manager is
  constructed.

### Known behavior

On a dedicated server, the client reports `NO_SERVERSIDE_SUPPORT` from joining until the server's
handshake arrives, because a server without the plugin never answers. A manager that is open in
that window, whether it was opened right after joining or stayed open through a proxy switch,
briefly shows the unsupported message and then rebuilds as loading and ready. This design adds no
grace period.

## 2. Layout

Panel geometry doesn't change. The changes are inside the panels.

### Sidebar

- The local/remote toggle is visible when `RemoteWaypointPanel.servers()` isn't empty, or while the
  remote view is showing.
- The add button is visible only in the local view.
- Hidden controls release their slots. The visible controls stack upward from the content bottom
  with 4-pixel gaps, so the column is `n × 16 + (n − 1) × 4` pixels tall, and the rails get the
  freed space.

| View | Controls, top to bottom | Rails |
| --- | --- | --- |
| Local, no remote servers (including singleplayer) | All dimensions, group, sort order, sort mode, add | Dimension |
| Local, remote servers cached | Local/remote, all dimensions, group, sort order, sort mode, add | Dimension |
| Remote | Local/remote, all dimensions, group, sort order, sort mode | Dimension and server |

A pure function lays out the sidebar:

```java
static SidebarLayout calculateSidebarLayout(
        int contentY,
        int contentHeight,
        int visibleControlCount,
        int dimensionPreferredHeight,
        int serverPreferredHeight,
        boolean showingRemote
)
```

`SidebarLayout` reports whether the controls fit, the y position of the first visible control, the
dimension and server rail rectangles (zero height when hidden) and the positions of both
separators. The screen places each visible control at `controlsY + index × 20`. The rules match
the current `layoutSidebar()`:

- The controls hide when the content height can't fit them.
- The rails share the space above the controls, minus the 6-pixel section gap.
- In the local view, the dimension rail takes `min(available, preferred)`. In the remote view,
  `OpposedExpansionLayout.allocate` divides the space between the two rails.
- A rail hides below its one-icon minimum.
- Each separator sits midway in its 6-pixel gap and shows only when both neighbors are visible.

The function replaces `ManagerLayoutGeometry.dimensionListHeight()`, which only the tests use and
which no longer matches the real sidebar. The controls no longer sit in the `controlColumn` and
`controlAnchor` `WidgetPack` instances, because `WidgetPack` keeps a slot for a hidden child. Each
control is still registered once.

`tick()` tracks whether any remote servers are cached and lays out the sidebar again when that
changes. Hiding the toggle while it has keyboard focus clears the focus.

### List panel

- **Local view:** unchanged. The search field, a 4-pixel gap, then the list fills the rest.
- **Remote view:** the search field, a 4-pixel gap, the tree, a 4-pixel gap, then the footer.

`RemoteWaypointPanel.layout(...)` receives the whole area below the search field and the details
area. A pure helper splits the list area:

- The footer is a `ScalableText` wrapped to the panel width, so it can take two lines at narrow
  widths.
- The tree gets `height − 4 − footerHeight`.
- If no server is selected, or the footer would leave the tree less than one 20-pixel row, the
  footer hides and the tree gets the full height.

The panel keeps its bounds and splits the area again when the footer text changes. This replaces
the screen's hard-coded `- 14` and the `drawText` call at `tree bottom + 3`.

### Details panel

- **Local view:** unchanged. The details viewport fills the panel.
- **Remote view:** the details viewport, a 4-pixel gap, then the teleport button. The bottom of
  the button's outline lines up with the content bottom. The button's height comes from
  `getVisualHeight()`: 18 pixels, which is 16 pixels of content plus its 2-pixel top outline. The
  viewport is `contentHeight − buttonVisualHeight − 4` pixels tall. Named constants replace the
  `-24` and `-20` offsets, which left the button 2 pixels above the content bottom.

## 3. Remote availability

Availability comes from the selected server's `CatalogReceiver.View.state()`.
`RemoteClientCatalogs` stores servers only from an `AVAILABLE` catalog response, so whenever servers
are cached the overall catalog state is `AVAILABLE`. The overall status line under the tree is
removed. Unauthorized and unavailable catalogs appear as empty states instead (section 4).

### Footer

- The text is `waypoint.remote.gui.server_status` (`"%s · %s"`) with the server's display name and
  its `waypoint.remote.state.*` label, for example `Lobby · Stale — retained data may be outdated`.
- A color supplier resolves the theme role at render time, through a pure, unit-tested mapping:

  | State | Theme role |
  | --- | --- |
  | `AVAILABLE` | `TEXT_MUTED` |
  | `STALE` | `WARNING` |
  | `UNAVAILABLE` or `UNAUTHORIZED` | `DANGER` |

  `UNAUTHORIZED` servers are filtered out before they reach the rail. The mapping includes them
  only so that it covers every state.
- The footer is hidden when no server is selected.
- It updates when the selected server or that server's state changes, and the list area is split
  again.

### Server rail badges

- `IconListWidget` adds `protected @Nullable WidgetThemeVariable entryBadgeColor(T entry)`, which
  returns `null` by default.
- After drawing all visible icons, the rail calls a new `DrawContextHelper.nextItemOverlayLayer`
  once. It then draws a 5×5 dot flush with the top-right corner of each badged 16×16 icon cell: a
  3×3 fill in the badge color inside a 1-pixel `BORDER` edge. Then it calls
  `previousItemOverlayLayer`. The badges stay inside the rail's scissor, so icons scrolled out of
  view show no badge.
- The existing `nextLayer` can't be used here. Before 1.21.6 it moves drawing up by only 1 in z,
  while vanilla draws GUI item models at about z 150. The new pair starts a new render stratum on
  1.21.6 and later, like `nextLayer`, and before 1.21.6 translates 200 in z, the depth vanilla uses
  for item stack counts.
- `ServerListWidget` maps each state through a pure, unit-tested function:

  | State | Badge |
  | --- | --- |
  | `AVAILABLE` | None |
  | `STALE` | `WARNING` |
  | `UNAVAILABLE` or `UNAUTHORIZED` | `DANGER` |

- `DimensionListWidget` keeps the default and shows no badges.

### Server rail tooltip

`ServerListWidget.entryLabel` returns two lines: `<display name> [<id>]`, then the state label in
the state's color.

### Consistency fixes

- An `UNAVAILABLE` server's dimension rail is empty. `RemoteBrowserModel.roots` already hides an
  unavailable server's retained snapshot, so the rail no longer lists dimensions the tree can't
  show.
- The teleport button's tooltip explains its state:
  - While disabled: `waypoint.remote.gui.teleport_hint`.
  - While enabled: `waypoint.remote.gui.feedback`.
  - After a failed attempt: the failure message (`waypoint.remote.gui.changed` or
    `waypoint.remote.gui.send_failed`) until the selection changes.

  Currently, `rebuild()` replaces the hint with the feedback text on the first rebuild, so a
  disabled button never explains why it's disabled.

## 4. Empty states

### Rendering

- Each list keeps one `ScalableText` message in `TEXT_MUTED`, wrapped to the list's content width
  minus 5 pixels on each side.
- The message starts 5 pixels from the left edge of the list content (inside the local list's
  4-pixel padding), and its first line is centered in the first 20-pixel row. This is where the
  local list draws `<Empty>` today.
- `renderEmpty` already runs in the tree's translated, scissored space.
- An enum resolver picks the message, so it can be unit tested without Minecraft text classes.
  The widget turns the enum value into a translatable `Component`.

### Local list

`WaypointListWidget.resolveEmptyReason(String query, boolean showAllDimensions)` picks the message.
The first matching row wins:

| Condition | Message | Key |
| --- | --- | --- |
| The search text isn't blank | No waypoints match "*query*". | `waypoint.empty.no_matches` |
| All-dimensions mode | No waypoints yet. Use the + button to add one. | `waypoint.empty.all` |
| One dimension is selected | No waypoints in this dimension yet. Use the + button to add one. | `waypoint.empty.dimension` |

The add button's icon is a plus sign. In grouped mode, a dimension that has lists but no waypoints
still shows its list rows, so these messages appear only when there are no rows at all.
`EMPTY_INFO_TEXT` and its `waypoint.empty_mark` key are removed.

### Remote tree

This resolver lives in `RemoteBrowserModel`, next to the existing tested model functions:

```java
static EmptyReason emptyReason(
        RemoteCatalogState catalogState,
        boolean hasServers,
        @Nullable CatalogReceiver.View selectedServer,
        String query,
        @Nullable String dimensionScope
)
```

`dimensionScope` is the panel's current dimension filter. It is `null` in all-dimensions mode or
when the selected server has no dimensions. The first matching row wins:

| Condition | Message | Key |
| --- | --- | --- |
| `catalogState` is `UNAUTHORIZED` | You don't have permission to view remote waypoints. | `waypoint.remote.empty.unauthorized` |
| `hasServers` is false | No remote servers are cached. Catalog synchronization may be unavailable. | `waypoint.remote.no_servers` |
| The selected server is missing, `UNAVAILABLE` or has no snapshot | This server's waypoints are unavailable right now. | `waypoint.remote.empty.server_unavailable` |
| The search text isn't blank | No waypoints match "*query*". | `waypoint.empty.no_matches` |
| `dimensionScope` is `null` | This server has no waypoints. | `waypoint.remote.empty.server` |
| Otherwise | This dimension has no waypoints on this server. | `waypoint.remote.empty.dimension` |

Because the toggle hides when no servers are cached, a player sees the first two rows only when
the remote view is already open as the servers disappear or permission is revoked. The remote
messages have no "+" hint because the remote view is read-only.

## 5. Text and translations

Every change applies to all six locales: `en_us`, `es_es`, `he_il`, `zh_cn`, `zh_hk` and `zh_tw`.

New keys:

| Key | English text |
| --- | --- |
| `server_waypoint.manager.title` | Waypoint Manager |
| `server_waypoint.manager.loading` | Synchronizing waypoints… |
| `waypoint.remote.gui.server_status` | %s · %s |
| `waypoint.empty.no_matches` | No waypoints match "%s". |
| `waypoint.empty.all` | No waypoints yet. Use the + button to add one. |
| `waypoint.empty.dimension` | No waypoints in this dimension yet. Use the + button to add one. |
| `waypoint.remote.empty.unauthorized` | You don't have permission to view remote waypoints. |
| `waypoint.remote.empty.server_unavailable` | This server's waypoints are unavailable right now. |
| `waypoint.remote.empty.server` | This server has no waypoints. |
| `waypoint.remote.empty.dimension` | This dimension has no waypoints on this server. |

- `server_waypoint.manager.title` replaces the literal `"Server Waypoints"` as the screen title,
  which is used for narration and isn't drawn. The name matches the keybind, "Open waypoint
  manager screen".
- **Changed:** `waypoint.remote.gui.local` becomes "Local waypoints" instead of "Server: Local".
  The toggle's two tooltips then name the current view, like the other sidebar toggles. The
  "Remote waypoints" tooltip is unchanged.
- **Removed:** `waypoint.remote.gui.selector`, which is unused since the server rail replaced the
  selector, and `waypoint.empty_mark`, which the empty states replace.
- The non-English strings are machine drafts and need a native speaker's review before release.
- In the code this change touches, the distance sort item is kept in a field instead of being
  looked up with `iconItems.get(2)`.

## 6. Documentation

- **This feature folder:** a README index, this spec, and `plans/` and `validation/` folders that
  hold a `.gitkeep` until they have documents. The folder is listed in `docs/features/README.md`.
- **Step 18 spec** (`cross-server/specs/cross-server-gui.md`): replace the outdated selector and
  tree paragraph, and the sentence that says availability appears beside the server label, with
  the current behavior and a link to this spec. Keep its read-only, teleport and session rules.
- **GUI guide** (`tips/gui/local-guide.md`), updated in the same change as the code, as AGENTS.md
  requires:
  - `IconListWidget.entryBadgeColor`, the server states in `ServerListWidget`, and
    `DrawContextHelper.nextItemOverlayLayer`.
  - The `RemoteWaypointPanel` footer and area split, and both empty-state resolvers.
  - The manager's view states, its `removed()` lifecycle, and how hidden sidebar controls release
    their slots.
  - A fix for the outdated claim that the manager demonstrates nested `ExpandableManager` layouts.

## Constraints

- Java 17 and four-space indentation. No build script changes are expected.
- Every Stonecutter branch must keep working, from 1.20.1 to 26.3. Keep version differences in
  `DrawContextHelper` (the existing `nextLayer`, `previousLayer`, fills and outlines, plus the new
  `nextItemOverlayLayer` pair) instead of adding version branches at each call site. No new swap
  or replacement is planned; if one becomes necessary, update the inventory in `AGENTS.md` in the
  same change.
- The GUI probe (`tools/cross-server-gui-test/RemoteGuiProbe.java`) reads these members by
  reflection, so their names stay the same:
  - `WaypointManagerScreen`: `serverScopeToggle`, `remotePanel`, `showingRemote`,
    `addWaypointButton`, `serverListWidget`, the static `dimensionListWidget`, `groupModeToggle`,
    `sortOrderToggle`, `sortingModeDropdown` and `searchField`.
  - `RemoteWaypointPanel`: `tree`, `selected`, `grouped`, `reversed`, `sortMode`, `teleportButton`
    and the `teleport()` method.
- No backward-compatibility code, per AGENTS.md. Removed translation keys need no migration.
- Commit only when asked, per AGENTS.md.

## Validation

### Unit tests

In the mods test source set:

- `resolveViewState` for every network state, with and without an integrated server.
- `calculateSidebarLayout`: five versus six controls, the remote rail split, controls hiding when
  the content is too short, and separator positions. This replaces the `dimensionListHeight()`
  assertion in `WaypointManagerScreenLayoutTest`.
- The remote list-area split, including the footer hiding below one tree row.
- Both empty-state resolvers, row by row, including which condition wins.
- The server state mappings for badge colors and footer colors.
- Translation coverage for `server_waypoint.manager.*` and `waypoint.empty.*` in all six locales,
  with matching placeholders, alongside the existing `waypoint.remote.*` check.

### Gradle

- `:mods:26.1.2-fabric:test`, the active Stonecutter target.
- Compile `1.20.1-fabric`, `1.20.1-forge`, `1.21.6-fabric` (the first version with render strata),
  `26.3-fabric` and `26.3-neoforge`.
- `git diff --check`.

### GUI probe

The probe keeps working unchanged, because it fills the catalog cache before opening the screen
and every member it reads keeps its name. Add three checks: the toggle hides when the cache is
empty, the footer for a stale server, and one empty-state message.

### In game

Compiling can't prove these:

- Opening the manager during join and watching it build in place.
- A proxy server switch while the local view is open.
- The toggle hidden in singleplayer and on a server with cross-server disabled.
- Badges drawn above item icons on 1.20.1 and on a 1.21.6 or later target.
- Footer wrapping at a 427×240 scaled viewport, the size used by
  `completeLayoutFitsCompactGuiScaleFourViewports`.
- The teleport button's alignment.
- Each empty-state message.
