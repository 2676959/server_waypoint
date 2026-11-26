# Config-style Local profile rows

Date: 2026-10-30. The user requested the config-screen design with hybrid rows.

`LocalWaypointProfilesScreen` now follows `ClientConfigScreen` with one centered, content-sized
panel, 1.2-scale heading, section headers/dividers, compact row buttons at the settings row text
scale, and a separate status/Done footer. Labelled `SettingsListWidget.Row` entries pair the profile
picker/name field with Use/Rename actions. `WideRow` preserves the full width for the teleport
command and active-profile context. Creation/removal guidance and dynamic picker feedback use row
tooltips; the removal label uses the semantic danger role. The previous split columns are removed.

Name/ID-prefix resolution, explicit switching, separate create/delete dialogs, captured-generation
checks and independent settings drafts are unchanged. No new locale keys, generic widgets, storage
formats, swaps or compatibility shims were added. The GUI guide and owning design record are updated.

## Verification

```sh
rtk proxy ./gradlew :mods:26.1.2-fabric:test :mods:1.20.1-fabric:test \
    --tests '*LocalWaypointProfilesScreen*Test' --tests '*WaypointFormTranslationTest' \
    :mods:1.20.1-forge:compileJava :mods:26.3-neoforge:compileJava
```

- Fabric 26.1.2 full suite: **1,049 tests**, zero failures/errors/skips.
- Fabric 1.20.1 focused suite: **17 tests**, zero failures/errors/skips.
- Forge 1.20.1 and NeoForge 26.3 compile successfully.
- Geometry tests cover centered content and title/panel/footer separation at constrained sizes.
  Actual screen layout tests check input/action alignment, action bounds, one registration per
  control and the wider command field. Existing real-screen operation tests still pass.
- [Final verification log](execution/profile-hybrid-rows/final.log),
  [focused checks](execution/profile-hybrid-rows/focused.log),
  [initial geometry contract failure](execution/profile-hybrid-rows/red.log).
- Whitespace, Java indentation, Stonecutter scope balance, locale duplicates and document links
  checked. Active targets remain mods 26.1.2-fabric and Paper 26.2-paper.

Reviewed inline for hybrid row reuse, global action-column alignment, scrolling/focus registration,
modal/popup layering and the stable footer. All prior uncommitted work is preserved; no commit.
No live Minecraft visual or physical-input check was run. Those acceptance checks remain pending.
