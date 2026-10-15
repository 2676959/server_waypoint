# In-world waypoint visibility validation

## Changes

- Milestone 1 (`b1fd5e2`): hide projected waypoints during level loading and resource overlays,
  without a world/player, and when F1 hides the HUD. Suppressed rendering clears hover state
  after processing queued waypoint changes.
- Milestone 2: add **Render waypoints under F1**, default off, to the existing rendering section.
  The `renderWaypointsUnderF1` preference persists through GSON, applies on the next frame,
  and participates in the existing row reset and reset-all controls. Enabling it never bypasses
  the loading-screen or global waypoint-rendering checks.

## Automated checks

The following command passed with the final implementation:

```sh
./gradlew :mods:26.1.2-fabric:test \
  :mods:1.20.1-fabric:compileJava \
  :mods:1.21.9-fabric:compileJava \
  :mods:26.2-fabric:compileJava \
  :mods:26.3-fabric:compileJava \
  :mods:26.3-neoforge:compileJava
```

- 26.1.2 Fabric: 417 tests, zero failures, errors, or skipped tests. Coverage includes visibility
  precedence, the default-off preference and JSON round trip, settings/reset behavior, and the
  new label/tooltip keys in all six locales.
- The compile targets cover the legacy `ReceivingLevelScreen`, its replacement by
  `LevelLoadingScreen`, and the 26.2+ `Gui`/`Hud` API. The active source version remained
  `26.1.2-fabric`; Stonecutter generated the other targets without switching it.
- `git diff --check` passed. Source review confirmed every loader loads the config before
  initializing the renderer.

## In-game checks

Not run: no HeadlessMC installation was found. Automated tests and compilation do not prove
the visual behavior in a live client. Before release, check joining a world, changing dimensions,
resource reloads, F1 with the option off/on, returning to normal gameplay, and saving/resetting
the preference through the settings screen.
