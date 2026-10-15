# Map server actions and chat shares

The approved behavior redirects a single server-synced Minimap Add/Edit selection to the server
editor, adds server Edit/Add actions to VoxelMap's persistent-map popup, and offers the local
sender a server add form after native Xaero or VoxelMap chat sharing. Personal and multiple
Minimap selections retain native editing. Existing encoded shares are checked by owning
dimension, list and name, including shares whose coordinates have become stale.

Shared drafts retain native dimension coordinates, names, initials, color, rotation and supported
VoxelMap icons. Confirmation and opening the add form do not write a waypoint. The ordinary
Add button performs the existing server command and waits for synchronization.

Both popup labels use Minecraft translation keys in all six asset locales. Xaero World Map uses
the same labels. Custom Xaero dimensions decode the native directory escaping for namespace,
underscores, slashes and trailing dots.

## Automated validation

- `./gradlew build --continue --max-workers=2 --console=plain`: passed across all 40 mod targets
  and the remaining project modules. Mod test reports contain 26,576 tests, zero failures or
  errors, and 101 skipped tests.
- Parser regression tests first failed for encoded identity and native custom dimension escaping,
  then passed after implementation. They also cover malformed/external shares, fallback Y,
  native waypoint-world suffixes in pinned older Xaero builds, and modern dimensions ending
  in the same text.
- `python3 -m unittest discover -s tools/live-game-test -p 'test_*.py'`: 27 passed.
- Independent source review found the custom-dimension escaping issue; the correction and
  native share/fallback scenarios were reviewed again with no outstanding findings.

## Runtime loading repairs

The first native launches exposed existing optional-dependency issues that prevented the new
mixin checks from running. Forge and early NeoForge reject an omitted optional dependency
version range, so World Map and VoxelMap now declare `[0,)` in both loader metadata templates, retaining optional installation.
The Forge 1.21.9 World Map pin is updated from 1.39.17 to 1.39.18 after the older release failed
its Forge event-bus registration. NeoForge 1.21.7 uses the dedicated Minimap 25.2.10 and
World Map 1.39.12 builds after the former 1.21.6 pins failed client payload-handler registration.
The complete build passed again after these runtime repairs and the native format correction.

The live runner now reads the exact archive name from Gradle instead of selecting among stale
builds by filename glob. Its API adapter covers the full project matrix, including Forge's
unobfuscated cutoff, older world flows, disconnect signatures and Xaero's component labels.
Loader error screens fail promptly instead of spending the full title readiness timeout.

Native launches also exposed a missing Minimap `init` mapping on older Forge. The mixin now
extends `Screen` and uses the full `init()V` selector, producing the required SRG refmap entry.
The 26.3 probe uses Minecraft's left-button constant, and map fixtures restore view state and
reset geometry after native screen reinitialization.

## Runtime validation

Final applicable runtime checks passed for all 40 build targets: 39 native map editor suites
and one core-only suite. Every run loaded a freshly built production JAR, audited transformed
mixin targets, and created an isolated synchronized world. The 39 editor suites also saved and
reloaded the waypoint store and passed independent persisted JSON checks. All 23 VoxelMap
profiles passed their popup and native public/private sharing checks. No probe ships in production.

The expanded suite covers single synced Minimap editing, personal and multiple native fallbacks,
VoxelMap popup Edit/Add, native public/private share handlers, confirmation choices, form defaults,
encoded identity suppression with stale coordinates, server saves and persistence.

The main batch passed 38 editor profiles. Forge 1.21.6 needed an explicitly core-scoped probe
correction: older optional Xaero mixins lack `@Pseudo`, so the core target inventory permits
missing Xaero/VoxelMap classes while retaining required game targets. An extractor regression
first failed and then passed; exact-line assertions protect the required-target classification.
The independent core rerun passed. Normal editor inventories retain their original classification.

The NeoForge 1.20.6 launch export came from a partial loader installation and invoked vanilla Main.
Its failure occurred before game startup. HMCL's version-list request returned HTTP 502; installing
the official pinned `20.6.139` installer into a separate local profile and saving the NeoForge patch
produced a bootstrap launch. The fresh full editor rerun passed on Minecraft 1.20.6.

Ignored local evidence, retained with all earlier attempts:

- `build/map-actions/full-build-final.log`
- `build/map-actions/all-prepared-v10/` and `build/map-actions/all-live-v10/`
- `build/map-actions/core-prepared-v11/` and `build/map-actions/core-live-v11/`
- `build/map-actions/neoforge1206-prepared-v12/` and `build/map-actions/neoforge1206-live-v12/`
- `build/map-actions/core-audit-regression/{red,green-exact}.log`
- `build/map-actions/final-runtime-coverage.{json,md}` combines the passing result for each target;
  original results were not overwritten. Production artifact hashes still match all final results.

## Coverage constraints

- Both `1.21.2-fabric` and `1.21.2-neoforge` intentionally pin Xaero builds for 1.21.3. Their
  `gradle.properties` document using Minecraft 1.21.3 for runtime testing. Exact 1.21.2 native
  launches reject those dependencies; the final suite uses the same production target JARs on
  1.21.3 and records the actual version. Fabric uses a mod-ID-checked, SHA-256-bound matching
  Fabric API override. The pinned VoxelMap builds support 1.21.3.
- `1.21.6-forge` pins NeoForge Xaero artifacts for compilation because no matching Forge
  release is published. The core profile omits optional map JARs, audits available targets,
  creates a synchronized world and checks server Edit/Add forms and cancellation. This does
  **not** verify Xaero map actions on Forge 1.21.6.
- VoxelMap checks apply to the 23 targets that pin VoxelMap. The other targets have no VoxelMap
  integration; absent optional targets are recorded explicitly.

Screens are opened programmatically. Buttons dispatch through `Screen.mouseClicked`; map-menu
actions are invoked directly. VoxelMap popup checks call `createPopup`, inspect its entries and
call `popupAction` within one game-thread task, before a rendered frame between those actions.
They verify popup construction and action dispatch, but do not verify visible popup rendering
or the right-click input path. Chat-share checks inspect the add draft and cancel it; actual
server add/save coverage comes from separate map fixtures.

Native GUI handler and state assertions do not certify visual appearance, operating-system
mouse/keyboard input, opening maps through keybindings, or all six locales in game. These
isolated integrated-world checks do not claim multiplayer transfer coverage.

## Target results

| Build target | Actual Minecraft | Suite | VoxelMap | Result |
| --- | --- | --- | --- | --- |
| 1.20.1-fabric | 1.20.1 | editor | yes | PASS |
| 1.20.1-forge | 1.20.1 | editor | no | PASS |
| 1.20.2-fabric | 1.20.2 | editor | yes | PASS |
| 1.20.2-forge | 1.20.2 | editor | no | PASS |
| 1.20.2-neoforge | 1.20.2 | editor | no | PASS |
| 1.20.4-fabric | 1.20.4 | editor | yes | PASS |
| 1.20.4-forge | 1.20.4 | editor | no | PASS |
| 1.20.4-neoforge | 1.20.4 | editor | no | PASS |
| 1.20.6-fabric | 1.20.6 | editor | yes | PASS |
| 1.20.6-forge | 1.20.6 | editor | no | PASS |
| 1.20.6-neoforge | 1.20.6 | editor | no | PASS |
| 1.21-fabric | 1.21 | editor | yes | PASS |
| 1.21-forge | 1.21 | editor | no | PASS |
| 1.21-neoforge | 1.21 | editor | no | PASS |
| 1.21.2-fabric | 1.21.3 | editor | yes | PASS |
| 1.21.2-neoforge | 1.21.3 | editor | yes | PASS |
| 1.21.3-fabric | 1.21.3 | editor | yes | PASS |
| 1.21.3-forge | 1.21.3 | editor | no | PASS |
| 1.21.3-neoforge | 1.21.3 | editor | yes | PASS |
| 1.21.5-fabric | 1.21.5 | editor | yes | PASS |
| 1.21.5-forge | 1.21.5 | editor | no | PASS |
| 1.21.5-neoforge | 1.21.5 | editor | no | PASS |
| 1.21.6-fabric | 1.21.6 | editor | yes | PASS |
| 1.21.6-forge | 1.21.6 | core | no | PASS |
| 1.21.6-neoforge | 1.21.6 | editor | no | PASS |
| 1.21.7-neoforge | 1.21.7 | editor | no | PASS |
| 1.21.9-fabric | 1.21.9 | editor | yes | PASS |
| 1.21.9-forge | 1.21.9 | editor | no | PASS |
| 1.21.9-neoforge | 1.21.9 | editor | no | PASS |
| 1.21.11-fabric | 1.21.11 | editor | yes | PASS |
| 1.21.11-forge | 1.21.11 | editor | yes | PASS |
| 1.21.11-neoforge | 1.21.11 | editor | yes | PASS |
| 26.1.2-fabric | 26.1.2 | editor | yes | PASS |
| 26.1.2-forge | 26.1.2 | editor | yes | PASS |
| 26.1.2-neoforge | 26.1.2 | editor | yes | PASS |
| 26.2-fabric | 26.2 | editor | yes | PASS |
| 26.2-forge | 26.2 | editor | yes | PASS |
| 26.2-neoforge | 26.2 | editor | yes | PASS |
| 26.3-fabric | 26.3 | editor | yes | PASS |
| 26.3-neoforge | 26.3 | editor | yes | PASS |
