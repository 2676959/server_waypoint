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
