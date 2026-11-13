# World marker formalization validation

Date: 2026-11-12. Baseline: `fbce8607` on `codex/experimental-world-marker`.

This record describes the initial documentation formalization. Subsequent implementation and acceptance work is tracked in the [promotion execution record](2026-11-12-execution.md).

## Change boundary

This change formalizes the existing `world_marker` navigation method in the feature contract, feature index, English/Chinese README additions, server guide and both 4.0.1 changelogs. Java sources, language JSON, command grammar, defaults, packet cadence, entity layout, session format and diagnostics are unchanged. Runtime translations are unchanged; the Chinese README receives equivalent player guidance.

The branch name and historical experiment reports are retained as provenance. Feature documentation uses the regular feature name. This is documentation of implemented behavior, not a declaration of completed visual or platform-matrix acceptance.

Promotion check: `NavigationMethod.WORLD_MARKER` is an ordinary `LIVE_DISPLAY` method. `ModNavigationRuntime` and `ServerWaypointPaperMC` already register their marker handlers unconditionally alongside the other navigation methods. Commands, the navigation toggle, `all` and configured defaults already expose it through the shared method model; no experimental runtime flag needs removing. Promotion therefore requires no new runtime switch or behavior change.

## Fresh automated verification

The following existing suites were rerun from source, using the Java 17 common module:

```sh
rtk proxy ./gradlew :common:test \
    --tests '_959.server_waypoint.navigation.AbstractWorldMarkerNavigationHandlerTest' \
    --tests '_959.server_waypoint.navigation.NavigationModelTest' \
    --tests '_959.server_waypoint.navigation.NavigationDisplayTextTest' \
    --tests '_959.server_waypoint.navigation.NavigationSessionCodecTest' \
    --tests '_959.server_waypoint.navigation.NavigationServiceTest' \
    --tests '_959.server_waypoint.command.CoreWaypointCommandNavigationTest' \
    --tests '_959.server_waypoint.text.feedback.NavigationScreensTest' \
    --rerun-tasks --console=plain
```

Result: **105 tests passed**, with zero failures, errors or skips across seven suites, confirmed from the JUnit XML. Gradle succeeded in 13 seconds. Build log for this run: `/tmp/server-waypoint-world-marker-formalization-tests.log` (local, temporary). No active Stonecutter switch was requested; `mods` remains `26.1.2-fabric` and `paper` remains `26.2-paper`.

| Contract area | Current source/test evidence |
| --- | --- |
| Badge/distance separation, optional item lifecycle, resize without respawn | `AbstractWorldMarkerNavigationHandlerTest` |
| Per-tick and same-tick behavior, bounded forecast, stop/teleport/world resets | `AbstractWorldMarkerNavigationHandlerTest` |
| Dimension hiding/restoration and cleanup | `AbstractWorldMarkerNavigationHandlerTest` |
| Size/distance command defaults, fractional distance, bounds and retarget preservation | `CoreWaypointCommandNavigationTest` |
| Method selection and configured defaults | `NavigationModelTest`, `CoreWaypointCommandNavigationTest` |
| Session encoding/restoration and independent display scheduling | `NavigationSessionCodecTest`, `NavigationServiceTest` |
| Text and navigation chat feedback | `NavigationDisplayTextTest`, `NavigationScreensTest` |

Platform-specific entity metadata and packet behavior were inspected in both current adapters, but no platform compile or native runtime was run for this documentation-only change. Unit fixtures prove their asserted contracts; they do not render Minecraft or exercise a real server connection.

Documentation verification passed: all 78 relative links in the nine changed Markdown files resolve, including linked Markdown heading anchors. The changed files have no tabs or trailing whitespace, and `git diff --check` passes. No source or active-version files changed.

## Historical native evidence

[Three-part marker verification, 2026-11-04](2026-11-04-three-part-marker.md) and its [retained JSON](2026-11-04-three-part-marker-native.json) record Paper 1.21.11 build 111 with two vanilla protocol bots, private two-text/optional-item delivery, metadata/identity, packet cadence, HUD independence and cleanup. Those receipts are bound to the candidate hash and configuration in that report.

The same report explicitly states that the final item-above-badge row order and subsequent block-centre anchor were not rerun natively. Configurable size and projection distance were added afterward. Therefore that native run is historical lifecycle/packet evidence for its tested revision, not a current acceptance result for the final feature.

## Remaining acceptance

The following list was captured before execution. The [current native report](2026-11-12-promotion-native.md) and [platform checks](2026-11-12-platform-checks.md) supersede it for exercised cases; the [execution record](2026-11-12-execution.md) tracks remaining rendered and runtime gates.

- Run the [native smoke runner](../README.md#native-smoke-runner) against a freshly built and hash-recorded current artifact. Verify owner isolation, no world insertion/chunk loading, optional-item transitions, HUD coexistence, same-tick cadence, dimension/respawn restoration and cleanup. Its current documented smoke scope does not cover interactive size/distance command acceptance; exercise those values explicitly.
- Use a rendered vanilla client to inspect the final item/badge/distance order, block-centre anchoring, close/far projection and size/radius endpoints and fractional settings. Check text through blocks and normal item occlusion, walking/sprinting, abrupt stops/turns and ordinary latency. Confirm no client mod or resource pack is needed.
- Exercise Private targets and access failures, configuration while the marker is disabled, target switches, reconnect restoration and stopping/restarting navigation with the default settings.
- Add native/version coverage for modded backends and Folia, including the 1.20.1 interpolation exception. Shared unit tests and Paper receipts do not certify those runtimes.
- If making a capacity or performance claim, measure representative player counts, packet traffic and tick percentiles separately. Handler averages and the short smoke runner establish no capacity limit.

These are explicit evidence gaps. Formalization adds no claim that these checks passed.
