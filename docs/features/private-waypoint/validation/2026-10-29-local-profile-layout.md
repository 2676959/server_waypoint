# Local profile screen redesign validation

Date: 2026-10-29. Scope: the user's minimalist-ui redesign request for
`LocalWaypointProfilesScreen`. All previous Task 7 changes remain uncommitted and intact.

## Delivered

- Name-based, case-insensitive profile input and suggestions matching names or ID prefixes.
  Users can enter a unique exact name or a unique ID prefix without typing the separator.
  UUID identity is separate from display labels. Duplicate names and colliding prefixes do not
  resolve arbitrarily; dropdown ID labels expand until distinct.
- Explicit Use profile action; typing does not activate another profile. Unique names appear in
  the input without an ID suffix. The active profile's settings are identified separately.
- At wide viewports, a selector column sits beside the active profile's settings. Narrow windows
  stack the sections in one scrollable panel. A stronger title, muted guidance, section dividers,
  field-adjacent Rename action and restrained native theme establish the hierarchy. Status and
  Done stay outside the scroll area.
- New profile uses a separate name-entry dialog. Delete confirms the active profile's name and
  captured UUID. Rename and command saving preserve the other field's draft. Invalid command
  templates disable Save and receive the existing validation styling/tooltip.
- Reused ComboBoxWidget, SettingsListWidget, ScalableText, visual-bound WidgetStack rows and
  ConfirmationDialog. No new generic widgets, fonts, assets, theme variables or storage formats.
  Six client locales and the GUI guide describe the updated flow.

## Verification

```sh
rtk proxy ./gradlew :mods:26.1.2-fabric:test :mods:1.20.1-fabric:test \
    --tests '*LocalWaypointProfilesScreen*Test' --tests '*WaypointFormTranslationTest' \
    :mods:1.20.1-forge:compileJava :mods:26.3-neoforge:compileJava
```

- Active Fabric 26.1.2: full suite, **1,048 tests**, zero failures/errors/skips.
- Older Fabric 1.20.1: **16 focused tests**, zero failures/errors/skips.
- Forge 1.20.1 and NeoForge 26.3: compilation successful; final unchanged-source compile tasks
  were up-to-date after the prior successful compile.
- [Final build log](execution/profile-layout/final.log), [focused log](execution/profile-layout/focused.log),
  [initial missing-contract failure](execution/profile-layout/red.log).
- No active project switch; mods remains 26.1.2-fabric and Paper remains 26.2-paper.
- Whitespace, Java indentation, closed Stonecutter scopes, locale duplicates and feature-document
  links checked. Locale tests verify translation key/format argument coverage.

`LocalWaypointProfilesScreenTest` tests separator-free name/ID resolution, duplicate names and
colliding short IDs, and wide/narrow viewport/footer bounds. Real-screen interaction tests use
actual Local profile stores and callbacks: partial input leaves the active profile alone, Use
switches by name, independent property saves retain the other draft, creation uses its own name,
and stale screens/delete confirmations cannot mutate old or replacement profiles.

Inline review covered modal input registration, popup layering and hover suppression, scrolling,
footer/status wrapping, explicit UUID targets, generation invalidation, preservation of drafts,
restricted action enablement, and source-independent theme roles. No delegation or commit.

## Proof boundaries

No live Minecraft visual or physical-input check was run for this redesign. Unit geometry tests
and native callback tests do not prove pixel layout, clipping, IME behavior, actual mouse/focus
behavior or translation fluency in game. The default full-size and constrained in-game views,
long labels, popup overlap, creation dialog, wheel/Tab/Escape and native theme appearance remain
manual acceptance checks. Task 8 remains pending.
