# Step 18: remote waypoint manager

Open the waypoint manager and choose **Server: Local / Remote…** above its dimension/list hierarchy.
The remote branch displays server → dimension → list → waypoint rows. Expand/collapse the hierarchy,
search by identifier/list/keyword, and reverse the name ordering. Select a waypoint to see its exact
server identity and read-only metadata. The Local button returns to the existing local manager.

Remote data stays in the Step 17 session cache. No remote row becomes a local waypoint, file,
renderer, map-mod export, or local navigation target. The details explain why mutation and local
rendering controls are absent. Stale data remains readable, while stale/unavailable/denied data cannot
initiate teleport. Server availability is shown beside its label and in the panel status.

Teleport sends immediately without a confirmation dialog. The click rechecks the cache session,
catalog revision, exact target and waypoint snapshot before submitting the existing remote teleport
command. The browser closes so authoritative preparation/progress/failure messages appear in chat.
Source and destination permission checks remain server-side, including destination authorization
before the proxy transfers the player. A changed or removed target requires a fresh selection;
server transfers, disconnects and handshakes invalidate browser sessions.

Rows use the local list's 20-pixel height, colored backgrounds, initials badges, hover and selection
outlines, formatted display names, and expand/collapse icons. Exact identities remain in tooltips;
remote rows do not expose local edit or visibility actions.

The GUI never truncates identities to fit a command packet. Unsupported chat characters or commands
longer than 256 characters disable the action with a tooltip; their read-only details remain visible.
There is no new command grammar or Minecraft protocol change in this step.

See [client synchronization](cross-server-client-sync.md). Native online proxy transfers, Folia/mod
handoffs, the full version matrix, screenshot review and soak/security release checks remain Step 19.

## Client API contracts

- `WaypointManagerScreen` owns `RemoteWaypointPanel(client, font)` and toggles the middle list and
  details panel in place. The remote panel retains its own selection and collapsed-node state.
- `WaypointDetailsWidget.setRemoteSelection(key, view)` accepts nullable arguments and builds
  read-only rows directly from `RemoteWaypointSnapshot`. It clears the local selection, preserves
  scrolling for the same exact key, and resets it on a different selection. The existing local
  `setSelection` clears the remote key. Neither path imports remote data into a local waypoint.
- The package-private `RemoteBrowserModel` owns immutable hierarchical identities and teleport request
  bindings. `roots` filters/sorts one immutable cache view; `prepare` and `isCurrent` bind and
  recheck the session generation, key, catalog revision, waypoint value and escaped command.
- `RemoteClientCatalogs.session()` increments on `clear()` and is independent of remote revisions.
  The screen closes on generation changes, including after a handshake.
- The remote screen renders each registered widget once through the high-level Stonecutter swap.
  Exact identities are available in hover details even when
  labels are clipped. No client API or payload is added for teleport results; existing server
  command feedback appears in chat after the teleport action closes the browser.

Shared GUI rendering APIs are documented in [the GUI guide](../../../tips/gui/local-guide.md).
