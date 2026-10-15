# Top-download mixin and live editor verification — 2026-10-07

Branch: `4.0.0`. Active Stonecutter project: `26.1.2-fabric`. All 15 selected
Fabric, Forge and NeoForge profiles passed production loading, runtime mixin
audits, editor interactions, server save round trips and world reopening.

## Selection

Used Server Waypoint’s rankings from the referenced **Build download analytics
tool** chat, rather than the all-mod rankings. The complete Modrinth snapshot
was fetched on 2026-10-06 at 01:52:28 UTC: project `UyBaYBa7`, 161 releases,
37,253 raw release downloads, no failed or pending projects. The tool splits
each release’s downloads equally among its declared Minecraft/loader pairs.
These are estimated lifetime download rankings, not exact installations by
version. Tied versions remain separate game launches. The snapshot and selected
rows are archived as `rankings-source.json` and `matrix.json`.

The matrix covers the three loaders implemented by this branch. Quilt
compatibility tags were not exercised; no Quilt result is claimed.

## Runtime matrix

Each row used the current production 4.0.0 JAR and pinned Xaero dependencies.
VoxelMap was included wherever the target pins it. A dash means this target
does not configure VoxelMap, so its mixins and scenarios do not apply.

| Loader | Minecraft | Attributed downloads | Actual loader | Java | Target classes | VoxelMap | Result |
| --- | --- | ---: | --- | ---: | ---: | --- | --- |
| Fabric | 26.2 | 2,233.00 | 0.19.3 | 25 | 40 | Pass | Pass |
| Fabric | 1.21.1 | 1,913.25 | 0.18.1 | 21 | 38 | Pass | Pass |
| Fabric | 1.21 | 1,913.25 | 0.16.14 | 21 | 38 | Pass | Pass |
| Fabric | 1.21.11 | 902.50 | 0.18.6 | 21 | 41 | Pass | Pass |
| Fabric | 1.21.5 | 805.98 | 0.16.14 | 21 | 38 | Pass | Pass |
| Forge | 1.20.1 | 847.50 | 47.4.20 | 17 | 32 | — | Pass |
| Forge | 1.20 | 847.50 | 46.0.14 | 17 | 32 | — | Pass |
| Forge | 26.2 | 454.00 | 65.0.3 | 25 | 40 | Pass | Pass |
| Forge | 1.21.11 | 178.00 | 61.1.5 | 21 | 41 | Pass | Pass |
| Forge | 26.1.2 | 115.67 | 64.0.11 | 25 | 40 | Pass | Pass |
| NeoForge | 1.21.1 | 792.00 | 21.1.200 | 21 | 32 | — | Pass |
| NeoForge | 1.21 | 792.00 | 21.0.167 | 21 | 32 | — | Pass |
| NeoForge | 26.2 | 270.00 | 26.2.0.6-beta | 25 | 40 | Pass | Pass |
| NeoForge | 1.21.11 | 82.00 | 21.11.42 | 21 | 41 | Pass | Pass |
| NeoForge | 1.21.5 | 54.25 | 21.5.88 | 21 | 32 | — | Pass |

Actual Minecraft 1.21 and 1.21.1 were both launched, using their shared 1.21
production build. Likewise, Minecraft 1.20 and 1.20.1 were launched separately
with their shared Forge build.

## Live checks

All runs created isolated singleplayer worlds with an integrated server.
Temporary Java agents scheduled assertions on the game thread and inspected
production classes; they did not transform bytecode. Tests invoked real native
editor actions and button `mouseClicked` handlers, used the production network
message path, waited for server acknowledgements, and checked server storage.
Minecraft’s native OpenGL rendering remained active.

- World Map Edit opens the server form. Save changes the exact owning
  dimension/list/name; matching names in the other dimension remain unchanged.
  Nonedited display name, initials, color, yaw, global flag, keywords, description
  and icon survive the round trip, as does the list display name.
- Editing an owning Nether waypoint while the player and viewed map are in the
  Overworld saves to the Nether. Pending saves allow integrated server ticks
  and complete through the matching response.
- VoxelMap Edit opens the server form for tracked markers. Cancel discards edits
  and returns to its parent; reopening and Save update the correct waypoint.
  Clearing display name, keywords, description and icon persists the clearing.
- Stale, ambiguous and personal VoxelMap markers retain native editing and
  return correctly on Cancel.
- Xaero keeps the encoded `sw␟Bases` native set key. A same-label personal
  `Bases` set is untouched. Native Xaero Save updates its local mapped set
  without modifying server storage; opening a missing personal set succeeds.
- World Map Cancel discards changes. Add uses the owning dimension and actual
  command path, saves `[17, 85, -19]`, and returns to the map. Cancel returns too.
- A missing server target disables the World Map action. If a target vanishes
  after opening the menu, activating the earlier action shows the not-found
  screen; Back returns to the map.
- Normal vanilla disconnect and world reopening reload the saved edits.
  Independent checks of the final JSON files passed for all 15 profiles,
  including duplicate-name isolation, full metadata, explicit clearing and Add.

For every row, the runtime probe loaded all configured production mixin target
classes, checked the raw Xaero set key and actual injected dropdown/reader
handlers, and ran `MixinEnvironment.audit()`. The global audit emits existing
`ClassAlreadyLoadedException` diagnostics for packet/storage classes outside
Server Waypoint’s targets; the target checks passed.

This verifies the exercised editor behavior and runtime application of every
configured mixin. It does not certify every gameplay branch of every mixin,
visual appearance, hardware mouse/keyboard input, multiplayer deployment or
arbitrary map-mod versions.

## Failures resolved during verification

The current Xaero dependencies rejected the old Forge loader pins. Updated
Forge 26.1.2 from `64.0.8` to `64.0.11`, and Forge 26.2 from `65.0.0` to
`65.0.3`, the minimum versions required by their pinned map dependencies.
`XaerosDependencyCompatibilityTest` now checks the declared Forge minimums
against each selected loader. Both old pins reproduced failing regressions;
both updated targets passed test and assemble, then passed live checks.

NeoForge 1.21.11 later stalled on the loading overlay during isolated reruns.
Java thread dumps showed pending reload work and the render thread inside
GLFW event polling. A native sample showed AWT’s macOS application loop nested
inside that call. The user-requested force-kill and initial rerun are preserved
as attempts 3 and 4. Attempt 5 used `-Djava.awt.headless=true` to avoid AWT
owning the event loop, while retaining Minecraft’s native OpenGL window; all
41 target checks and live/persistence scenarios passed. This JVM flag is only
in the temporary test launcher; no production mixin change was made for it.
Earlier harness failures from versioned disconnect APIs, helper mapping names,
legacy duplicate options and native map context were corrected in the harness
and rerun. Failed attempts remain archived; none counts as a pass.

## Automated matrix and evidence

Final tests passed across all 40 Stonecutter targets: **26,132 passed, 101
existing conditionally skipped, zero failures**. Fabric: 9,568; Forge: 7,592;
NeoForge: 8,972. The complete assemble matrix passed; both updated Forge
loader targets were rebuilt afterward. The earlier structural ASM audit
completed 8,454 checks across all 40 targets with zero failures.

Evidence: `build/mixin-validation-2026-10-07/top5-live/` contains the ranking
snapshot, matrix, harness sources, all successful and failed attempt logs,
runtime target lists/audits, scenario results, persisted waypoint JSON files,
production JAR hashes, `summary.json`, and `automated-tests.json`. Successful
production JAR hashes were checked against the current build artifacts.
`verify_persisted.py` independently validates all final waypoint files.

Each client used a 1 GiB heap and its own scratch game directory. Existing
installed-client mods and worlds were preserved. All tracked test JVMs exited.
Implementation commits: `fa9ce01a`, `09f86c98` and `b78e1fd0`.
