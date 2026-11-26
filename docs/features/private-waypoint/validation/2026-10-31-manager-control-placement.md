# Manager control placement validation

Date: 2026-10-31. Scope: the user asked for better places for the waypoint manager's floating
"Local profile" status line and the Add, Copy and Local profile icons above the details panel, and
approved a short in-chat design: put each control beside what it acts on. No spec or plan; no commit.

## Delivered

- **Add** ends the search row in the list column: an 11-pixel `IconButton` (1-pixel icon inset)
  packed with the search field in a horizontal `WidgetPack`. The field stops one gap short of it and
  takes the full width back in the remote view, where Add is hidden. Add no longer turns inactive when
  the sidebar is too short for its controls.
- **Copy waypoint** is a fitted text button at the bottom right of the details panel, shown only while
  a waypoint is selected; it appears with the selection. The details end one gap above it and otherwise
  fill the panel from its top, as the remote details already did.
- **Local profile** merges the status label and the house icon: a fitted text button on the line above
  the panels, at their left edge, naming the active profile and opening `LocalWaypointProfilesScreen`.
  Without an active profile it names that screen. Its tooltip names the screen, or gives the whole label
  when a long name is cut with an ellipsis. Unavailable sources follow it on the same line; the button
  leaves that status its one-line width, up to half the line.
- Tab order follows the layout: profile button, sidebar, search, Add, list, details, Copy.
- `TranslucentButton` gains `setFittedText` and `fittedLabelWidth`; `ManagerLayoutGeometry` gains
  `searchFieldWidth`, `detailsHeight`, `detailsFooterX`, `detailsFooterY`, `headerY` and
  `profileButtonMaxWidth`. The GUI guide describes the placement and the new API, and four stale
  mentions of an add button in the sidebar were corrected. No new translations, textures or theme roles.

## Verification

- RED: the new layout, profile-button text and fitted-label tests failed to compile on the missing
  methods only ([log](execution/manager-control-placement/red.log)); GREEN after the pure helpers
  ([log](execution/manager-control-placement/green.log)).
- Fabric 26.1.2 full suite: **1,094 tests**; Fabric 1.20.1 full suite: **1,082 tests**; zero failures,
  errors or skips ([26.1.2](execution/manager-control-placement/full-26.1.2-fabric.log),
  [1.20.1](execution/manager-control-placement/full-1.20.1-fabric.log)).
- Main and test sources compile for all 41 `mods` targets: 14 Fabric, 14 NeoForge and 13 Forge
  ([log](execution/manager-control-placement/matrix-compile.log)). `git diff --check` is clean.

## Live check

A scratch copy of `tools/live-game-test` ([patch](execution/manager-control-placement/live-probe.patch))
replaced the core form step with a manager step on the `26.1.2-Fabric` test client. It added a Local
waypoint, opened the manager in the Local view, asserted every moved control's bounds against
`ManagerLayoutGeometry`, clicked the waypoint row, read tooltips from a rendered frame, clicked each
moved control and saved frames with the game's own screenshot code
([evidence](execution/manager-control-placement/live-probe-evidence.txt)).

- At the user's 427×240 GUI size and at 854×480, the profile button sits at the panels' left edge above
  them, Add ends the search row level with the field, the details start at the content top and fill it
  until a selection shows Copy at the bottom right. The 854×480 bounds equal the unit tests' values.
- The profile button's tooltip reads "Local waypoint profiles" and Add's reads "Add". The profile button
  opens `LocalWaypointProfilesScreen`; Copy and Add open `WaypointAddScreen`.
- A 69-character profile name is cut with an ellipsis: the button is 369 pixels wide on the panels'
  374-pixel line, and the tooltip gives the whole label.
- The step passed in both runs after the probe was fixed to click the waypoint's row; in the first run
  the placement checks passed before it clicked the list header instead. Each run is reported FAIL only
  for the offline client's known "Failed to fetch user properties" and "Couldn't connect to realms" log
  errors.

## Collection row fix

The first frames showed the details panel's source row reading "Collection" without its value. The
row reused the add form's `waypoint.form.destination` label, but detail rows pass their value as a
translation argument and that label has no placeholder, so the value was dropped in every locale. The
user asked for it to be fixed here.

- The row uses the new `waypoint.details.collection` ("Collection: %1$s"), in all six client locales
  with each locale's label punctuation.
- RED: `WaypointDetailsWidgetTextTest` reads the text the panel hands its font, with the mod's English
  strings over the game's, and saw "Collection" beside "Name: Home base"; a translation test found no
  `waypoint.details.collection` ([log](execution/manager-control-placement/collection-red.log)).
  GREEN after the fix ([log](execution/manager-control-placement/collection-green.log)). The panel test
  bootstraps Minecraft for the display name's text codecs and aborts where that fails, as
  `WaypointFormStateTest` does.
- Fabric 26.1.2 full suite **1,096 tests** and Fabric 1.20.1 full suite **1,084 tests**, zero failures,
  errors or skips ([26.1.2](execution/manager-control-placement/collection-full-26.1.2-fabric.log),
  [1.20.1](execution/manager-control-placement/collection-full-1.20.1-fabric.log)); all 41 targets
  compile with their tests ([log](execution/manager-control-placement/collection-matrix-compile.log)).
- The live probe passed again on a fresh build, and its frame shows "Collection: Local"
  ([evidence](execution/manager-control-placement/collection-live-probe-evidence.txt)).

## Details panel translations

Spanish, Hebrew and both Traditional Chinese locales had none of the details panel's strings, so its
title, hint and row labels fell back to English there. The user asked for them to be translated.

- The 14 strings the panel uses besides the Collection row are added to `es_es`, `he_il`, `zh_hk` and
  `zh_tw` in English's order after `waypoint.local`, and the Collection row's string moved into that
  block. Each reuses its locale's form labels and wording: "waypoint" in Spanish titles, נקודת ציון
  and plural imperatives in Hebrew, 路徑點 and 清單 in Traditional Chinese, and zh_tw's 顔色.
- RED: a translation test requiring the panel's 15 strings in every locale with English's arguments
  failed on `es_es: waypoint.details.title`
  ([log](execution/manager-control-placement/details-translations-red.log)); GREEN after the strings
  ([log](execution/manager-control-placement/details-translations-green.log)). The Collection-only check
  became a check that every row label in English takes the row's value.
- Fabric 26.1.2 full suite **1,105 tests** and Fabric 1.20.1 full suite **1,093 tests**, zero failures,
  errors or skips ([26.1.2](execution/manager-control-placement/details-translations-full-26.1.2-fabric.log),
  [1.20.1](execution/manager-control-placement/details-translations-full-1.20.1-fabric.log)). These
  counts include eight tests that another session was adding to the same checkout for the form subtitle.
- Outside the panel, 12 manager control strings and a network error were still English-only in the
  four locales; the next section covers them.

## Manager strings and the unused key

The user then asked for the remaining strings and for `waypoint.details.none`, which nothing uses, to
be removed.

- `es_es`, `he_il`, `zh_hk` and `zh_tw` gained the 12 manager control strings (the search hint and
  entry, the sort modes and order, the grouping and dimension tooltips) after `waypoint.local`, and
  `waypoint.network.encoding_failed` after the last edit error, as English orders them. The sort modes,
  "Search" and "Flat" follow the server's `/wp list` translations; list stays 清單 in Traditional Chinese
  and zh_tw keeps 顔色. Hebrew names the dimension toggles with nouns ("הצגת…") and Spanish with
  infinitives ("Mostrar…"), as their settings do.
- `waypoint.details.none` is removed from `en_us` and `zh_cn`, the only locales that had it. All six
  client locales now hold the same 316 keys.
- RED: the manager key test failed on `es_es: waypoint.dimension.show_selected`, the retired key test
  on `en_us: waypoint.details.none` and a new network message test on `es_es`
  ([log](execution/manager-control-placement/manager-strings-red.log)); GREEN after the change
  ([log](execution/manager-control-placement/manager-strings-green.log)).
- Fabric 26.1.2 full suite **1,110 tests** and Fabric 1.20.1 full suite **1,098 tests**, zero failures,
  errors or skips, again including the other session's concurrent tests
  ([26.1.2](execution/manager-control-placement/manager-strings-full-26.1.2-fabric.log),
  [1.20.1](execution/manager-control-placement/manager-strings-full-1.20.1-fabric.log)).

## Proof boundaries

The live check covered 26.1.2 Fabric only, in singleplayer: the remote view, the status beside the
button for unavailable Shared or Private sources and other loaders ran only in unit tests and
compilation. Clicks went through the screen's `mouseClicked`, not physical input; hover looks, Tab
order and keyboard activation were not checked in game. Forge-family test suites were not run; where
plain JUnit can't bootstrap Minecraft, the new panel test aborts like the existing bootstrapping tests.
The new translations were checked against each locale's existing wording, not by native speakers, and
were not viewed in game.
