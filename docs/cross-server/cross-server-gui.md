# Step 18: remote waypoint manager

Open the waypoint manager and choose **Server: Local / Remote…** above its dimension/list hierarchy.
The remote branch displays server → dimension → list → waypoint rows. Expand/collapse the hierarchy,
search by identifier/list/keyword, and reverse the name ordering. Select a waypoint to see its exact
server identity and read-only metadata. The Local button returns to the existing local manager.

Remote data stays in the Step 17 session cache. No remote row becomes a local waypoint, file,
renderer, map-mod export, or local navigation target. The details explain why mutation and local
rendering controls are absent. Stale data remains readable, while stale/unavailable/denied data cannot
initiate teleport. Server availability is shown before its label so clipping cannot hide the status.

Teleport opens a separate confirmation naming all four exact identity fields. Confirming rechecks
the cache session, catalog revision and waypoint snapshot, then submits the existing remote teleport
command. The browser closes so authoritative preparation/progress/failure messages appear in chat.
Source and destination permission checks remain server-side. An unavailable connection is reported
in the browser. Cancelling sends nothing. A changed/removed target requires a fresh selection;
server transfers, disconnects and handshakes invalidate open confirmations and browser sessions.

The GUI never truncates identities to fit a command packet. Unsupported chat characters or commands
longer than 256 characters disable the action with a tooltip; their read-only details remain visible.
There is no new command grammar or Minecraft protocol change in this step.

See [client synchronization](cross-server-client-sync.md). Native online proxy transfers, Folia/mod
handoffs, the full version matrix, screenshot review and soak/security release checks remain Step 19.

## Client API contracts

- `WaypointManagerScreen` opens `RemoteWaypointManagerScreen(client, localScreen)` from the server
  selector. Returning to Local restores the same local screen and its preferences. The remote
  screen owns its own tree, selection, search, sort and collapsed-node state.
- `WaypointDetailsWidget.setRemoteSelection(key, view)` accepts nullable arguments and builds
  read-only rows directly from `RemoteWaypointSnapshot`. It clears the local selection, preserves
  scrolling for the same exact key, and resets it on a different selection. The existing local
  `setSelection` clears the remote key. Neither path imports remote data into a local waypoint.
- The package-private `RemoteBrowserModel` owns immutable hierarchical identities and confirmation
  bindings. `roots` filters/sorts one immutable cache view; `prepare` and `isCurrent` bind and
  recheck the session generation, key, catalog revision, waypoint value and escaped command.
- `RemoteClientCatalogs.session()` increments on `clear()` and is independent of remote revisions.
  The screen closes on generation changes, including confirmation callbacks after a handshake.
- The remote screen renders each registered widget once through the high-level Stonecutter swap.
  `ConfirmScreen` owns modal input. Exact identities are available in hover details even when
  labels are clipped. No client API or payload is added for teleport results; existing server
  command feedback appears in chat after the confirmed action closes the browser.

The required workspace-only `docs/gui-tips/README.md` is also updated. This repository's local
Git exclude rule keeps that guide outside the tracked patch; the API contracts above are tracked.
