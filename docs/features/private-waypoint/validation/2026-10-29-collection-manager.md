# Collection manager and composed markers — Task 7

Date: 2026-10-29. Branch: `feature/private-waypoint`. Base: `48793922`.
Scope: Task 7 of the [implementation plan](../plans/2026-10-16-private-waypoint.md).
Changes are uncommitted; no push, publication or live-game acceptance was performed.

## Implemented behavior

- Shared, Private, Local and Combined manager views, persisted in client configuration. Combined
  is the initial view; its creation destination is Local. Single-source views default to themselves.
  Unavailable sources remain identifiable, with loading/denied/unavailable status, while available
  Local data keeps the manager usable. Remote remains a separate read-only browser.
- Composed queries retain source/generation/profile identities, duplicate list/waypoint names,
  selected-source dimensions and search suggestions, existing distance rules and cross-source flat
  sorting. Combined headers/rows, details and suggestion tooltips identify sources. Stores remain
  independent and writable only through their original owners.
- Source-aware add/edit/remove/teleport actions, an explicit destination field, and detached Copy
  drafts. Permission changes are rechecked. Explicit destination changes use the opening generation;
  a profile/connection change preserves entered text and prevents saving into a replacement store.
- Local profile selection/create/rename/delete, confirmed deletion and per-profile teleport-template
  editing. Profile controls reuse the scrollable settings list and existing popup/dialog components.
- Source-owned marker composition and visibility. Shared snapshots preserve Local/Private handles;
  Private tombstones remove only Private markers. Equal coordinates remain distinct. Unchanged
  markers avoid presentation rebuilds. Renderer capacity drops render handles without deleting or
  hiding stored collection data. The existing renderer `local` flag still means `!global`.
- Six client locales and the [GUI API guide](../../../tips/gui/local-guide.md) were updated.
  Existing map integrations continue to receive Shared events only.

## Automated evidence

The final command passed:

```sh
rtk proxy ./gradlew :common:test :mods:26.1.2-fabric:test :mods:1.20.1-fabric:test \
    --tests '*ClientWaypoint*Test' --tests '*WaypointManagerScreen*Test' \
    --tests '*WaypointAddScreenCollectionTest' --tests '*WaypointForm*Test' \
    --tests '*ClientManualDownloadTest' \
    :mods:1.20.1-forge:compileJava :mods:26.3-neoforge:compileJava
```

The filters belong to the preceding `:mods:1.20.1-fabric:test` task; active Fabric ran its full suite.

| Target | Result |
| --- | --- |
| Fabric 26.1.2 full client suite | 1,039 tests; zero failures/errors/skips |
| Fabric 1.20.1 focused client suite | 175 tests; zero failures/errors/skips |
| Common suite | task up-to-date; existing 720 passing test results; Common production sources unchanged |
| Forge 1.20.1 | compilation passed |
| NeoForge 26.3 | compilation passed |

[Final build log](execution/task-7/final-verification.log).
The build reports existing Gradle/dependency deprecations and test `Unsafe` warnings.
No active-project switch was used: mods remains `26.1.2-fabric`, Paper `26.2-paper`.

New query, scene, manager-default/readiness and real-form tests cover duplicate identifiers,
immutable row/group containers, source tie-breakers, search/dimension provenance, unavailable
sources, captured actions after view changes, destination permission checks, and profile changes
with entered drafts. Existing access/copy/template/form tests ran in the full suite.

Real dispatch and renderer coverage in `ClientManualDownloadTest` proves that a Shared download
cannot re-enable a hidden marker, a Shared world resync preserves Local/Private render IDs, Private
tombstones remove only Private, and Private snapshots generate no map integration event. A real
renderer command-drain test submits 10,001 markers across three stores: capacity stops at 10,000,
all 10,001 stored entries remain and the rejected Local entry is still present, visible in its
list, and saved to disk.

Regression evidence: [missing query contracts](execution/task-7/query-red.log),
[Shared hidden-marker failure](execution/task-7/shared-refresh-red.log),
[unchanged-scene update failure](execution/task-7/scene-updates-red.log), and
[form destination contracts](execution/task-7/form-red.log) followed by
[passing real-form tests](execution/task-7/form-green.log).
Early query/scene fixtures were corrected to use actual writable roots and owner-attached
waypoint references; render/form fixtures bootstrap Minecraft registries and supply the test
camera before creating registry-backed components. Compatibility generation exposed and corrected
render swaps that initially had invocation arguments on the swapped method-name line.

## Review and proof boundaries

Final review was performed inline, following the plan's explicit no-delegation instruction. The
review covered source/list identity (including `WaypointList` name-based equality), mutation and
copy routing, stale form selections, permission revocation, source-only scene updates, integrated
Shared hooks, popup input/layering, constrained layout, six locale keys, Java 17 language features
and generated Stonecutter branches. No unresolved code-review findings remain.

Whitespace and feature-document links, duplicate locale keys and active targets were checked.
The generated older Fabric/Forge and current NeoForge sources compile, including the new versioned
mouse input/render entry points. No new swaps or replacements were introduced.

Task 8 remains unchecked. These checks do not prove visible marker composition, overlapping marker
hover behavior, dropdown placement, physical wheel/keyboard/Escape/focus handling, constrained or
translated in-game layouts, live profile confirmation behavior, real-server owner isolation,
proxy transfers, or successful teleport arrival. No screenshot or live/manual result is claimed.
