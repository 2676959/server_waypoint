# Client config

The client settings screen (`ClientConfigScreen`): its layout, the reusable `SettingsListWidget`,
reset to defaults, map-mod sync, and opening it from the loaders' mod lists.

Under **Waypoint rendering**, **Render waypoints under F1** controls whether in-world
waypoints remain visible when F1 hides the HUD. It defaults to off, applies immediately,
and is saved as `renderWaypointsUnderF1` in the client config. Loading screens always hide waypoints.

- [Screen design](specs/2026-09-28-client-config-screen-design.md)
- [Implementation plan](plans/2026-09-28-client-config-screen.md)
- [Validation results](validation/2026-09-28-results.md)
- [Loading-screen and F1 waypoint visibility validation](validation/2026-09-29-waypoint-visibility.md)
- [Xaero default-list set selection validation](validation/2026-10-08-xaero-default-set.md)
- [Full live client matrix](validation/2026-10-08-live-client-matrix.md)

Under **Map mods**, **Sync into Xaero's default set** defaults to off and is saved as
`xaeroDefaultListDirectSync`. On sync, the exact server list name `gui.xaero_default` uses
`sw␟gui.xaero_default` when off. When on, it replaces Xaero's default set contents, including
personal waypoints. Other lists always use separate server-owned sets. The setting applies to
manual sync, automatic sync, and incremental updates; use **Sync now** after changing it to rebuild the complete list in the chosen set.

Server entries placed in the default set carry hidden `sw␟` name markers for highlighting,
server editing, uploads, and cleanup. Removing the server list or syncing the complete list in separate mode
removes those marked entries while retaining the default set and any later personal additions.
Syncing the complete list in direct mode removes the previous separate server-owned default-list set.
