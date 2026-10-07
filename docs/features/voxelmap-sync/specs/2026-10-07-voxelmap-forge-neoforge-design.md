# VoxelMap Sync on Forge and NeoForge Design

Status: design agreed on 2026-10-07; implemented on 2026-10-07. The evidence is in
[validation](../validation/2026-10-07-voxelmap-forge-neoforge.md).

VoxelMap sync works only on Fabric today, although VoxelMap-Updated publishes Forge and NeoForge builds
for most recent Minecraft versions and commit `e88a02b6` already added them as dependencies. This change
gates the VoxelMap code and its loader metadata on one new Stonecutter constant, `voxelmap`, true on every
target whose `gradle.properties` pins a VoxelMap build. That makes the integration work on 3 Forge and
6 NeoForge targets, leaves the other 17 Forge and NeoForge targets exactly as they behave, and adds a
bytecode test and a release-gate rule that fail the build when a VoxelMap build or a jar stops matching
the mixins.

## 1. Goal and success criteria

Goal: the VoxelMap integration works on every Forge and NeoForge target for which VoxelMap-Updated
publishes a build, as it does on all 14 Fabric targets today. The integration is:

- auto-sync of server waypoints into VoxelMap;
- six mixins that show synced names without the internal prefix, mark synced waypoints with the sync icon,
  and redirect editing to Server Waypoint's edit screen;
- `/wp upload voxelmap` collection;
- the client-config row.

Success means:

- (a) every supported target compiles and its tests pass, or skip through the documented guard (§5.6);
  unsupported targets behave as before;
- (b) supported Forge and NeoForge jars contain the VoxelMap classes, `server_waypoint-voxelmap.mixins.json`,
  its registration and an optional `voxelmap` dependency; unsupported ones contain none of them. This also
  removes the unreferenced config they ship today;
- (c) the release gate, including the new `missing-mixin-class` rule, passes on all release jars;
- (d) a Forge and a NeoForge client with VoxelMap boot without mixin errors, and sync works in a
  singleplayer world;
- (e) the documentation no longer calls VoxelMap Fabric-only.

## 2. Current state

Checked against the `4.0.0` checkout at `e88a02b6`.

- **Source gating.** The VoxelMap code sits behind Stonecutter `//? if fabric {` in:
  `common/client/integrations/VoxelMapIntegration.java` and `VoxelMapWaypointHelper.java`; the six mixins
  in `mixin/voxelmap/` (`VoxelMapGuiAddWaypointMixin`, `VoxelMapGuiListWaypointsItemMixin`,
  `VoxelMapGuiListWaypointsMixin`, `VoxelMapGuiWaypointsMixin`, `VoxelMapPersistentMapMixin`,
  `VoxelMapWaypointContainerMixin`); and a one-line `//? if fabric` before
  `integrations.add(new VoxelMapIntegration());` in `MapModIntegrations.createIntegrations()` (line 61).
  All paths are under `mods/src/main/java/_959/server_waypoint/`.
- **Test gating.** Six test classes start with `//? if fabric {`, under `mods/src/test/java/_959/server_waypoint/`:
  `mixin/voxelmap/VoxelMapWaypointContainerMixinTest`, `mixin/voxelmap/VoxelMapGuiListWaypointsItemMixinTest`,
  `common/client/integrations/VoxelMapWaypointEditTargetTest`, `VoxelMapWaypointDimensionTest`,
  `VoxelMapUploadTest`, and `common/client/gui/render/VoxelMapIconResourceTest`. Two more conditions are
  inline `//? if fabric`: `MapModIntegrationsTest.voxelMapIsSupportedOnlyOnFabric` (line 25) and
  `IntegratedWorldMapModSyncTest.initialSyncEventDoesNotStartVoxelMapFullSync` (line 61).
- **Mixin config.** `mods/src/main/resources/server_waypoint-voxelmap.mixins.json` has `required: true`,
  `defaultRequire: 1`, client mixins only and the refmap `server_waypoint-common.refmap.json`. Only
  `fabric.mod.json` registers it. `META-INF/mods.toml`, `META-INF/neoforge.mods.toml` and the Forge jar
  manifest attribute `MixinConfigs` (`mods/forge.gradle.kts` line 384, value `mixinConfig`, line 32) name
  only `server_waypoint-common.mixins.json`. The locally staged 4.0.0 Forge and NeoForge release jars
  therefore ship `server_waypoint-voxelmap.mixins.json` unreferenced.
- **Runtime flag.** Only `ServerWaypointFabricClient` sets `ClientConfig.isVoxelMapLoaded`
  (`FabricLoader.getInstance().isModLoaded("voxelmap")`, line 32). `ServerWaypointForgeClient.ensureClientStarted()`
  (line 122) and `ServerWaypointNeoForgeClient.ensureClientStarted()` (line 140) set only
  `isXaerosMinimapLoaded`.
- **Client config UI.** `ClientConfigScreen.addMapModRows` already hides a map mod's rows when
  `MapModIntegrations.find(target)` is empty and shows "not installed" when it is absent, so unsupported
  targets need no UI change.
- **Documentation.** `README.md` (lines 25, 45, 475), `README_zh.md` (lines 25, 44, 450) and
  `CHANGELOG.md` line 46 (the 4.0.0 section; 4.0.0 is the unreleased version in `gradle.properties`) call
  VoxelMap Fabric-only.
- **Upload.** The root `build.gradle.kts` Modrinth `dependencies` lists `ModDependency("wkzK5379", "optional")`
  (VoxelMap-Updated) only for Fabric (line 122); the `"forge", "neoforge"` list (lines 124-127) has only the
  two Xaero projects. The CurseForge relations (`addOptional`, lines 189-197) never list VoxelMap:
  CurseForge's `voxelmap` is MamiyaOtaru's original mod, not VoxelMap-Updated, and the mixins are
  `required`, so pointing players at the wrong build could crash the game. That stays.

## 3. Decisions

| # | Decision |
| --- | --- |
| D1 | One new Stonecutter constant `voxelmap`, true when the target's `gradle.properties` defines `voxelmap_<loader>`, gates the code. Not per-file version predicates. |
| D2 | The VoxelMap mixin config and an optional `voxelmap` dependency are registered in loader metadata per target, templated in `processResources`. Unsupported Forge and NeoForge jars drop `server_waypoint-voxelmap.mixins.json`. Not an empty stub config. |
| D3 | In scope: the new test `VoxelMapMixinTargetsTest`, a new release-gate rule `missing-mixin-class`, the per-target Modrinth optional dependency, and the documentation updates. CurseForge relations stay unchanged. |
| D4 | `26.3-neoforge` is included: add `voxelmap_neoforge=uY5ysfSO` to `mods/versions/26.3-neoforge/gradle.properties`. |
| D5 | The mixins stay `required: true` with `defaultRequire: 1`, as on Fabric. No softening. |
| D6 | `VoxelMapWaypointDimensionTest` gets a Minecraft-bootstrap skip guard, so it skips instead of failing where plain JUnit cannot bootstrap Minecraft (NeoForge). |
| D7 | Verification includes a live client boot on Forge 1.21.11 and NeoForge 1.21.11 with VoxelMap. If headless launch cannot be made to work, the validation record says so and lists what did run. |

## 4. Support matrix

Each target's VoxelMap build is pinned by Modrinth version ID, because VoxelMap version numbers collide
across loaders.

| Loader | Targets | Pinned build (version ID, VoxelMap version) |
| --- | --- | --- |
| Fabric | all 14 targets | already define `voxelmap_fabric`; unchanged |
| Forge, supported | 1.21.11 | `dmqPnWux` (1.21.11-1.16.6) |
| | 26.1.2 | `BIg5fFNJ` (26.1-1.16.6) |
| | 26.2 | `NLswCC00` (26.2-1.16.10) |
| NeoForge, supported | 1.21.2, 1.21.3 | `DHNZyECS` (1.21.3-1.14.3) |
| | 1.21.11 | `gL7uH4rU` (1.21.11-1.16.6) |
| | 26.1.2 | `HOwAYwIt` (26.1-1.16.6) |
| | 26.2 | `yZoJT8l2` (26.2-1.16.10) |
| | 26.3 (new) | `uY5ysfSO` |
| Forge, unsupported (9) | 1.20.1, 1.20.2, 1.20.4, 1.20.6, 1.21, 1.21.3, 1.21.5, 1.21.6, 1.21.9 | none |
| NeoForge, unsupported (8) | 1.20.2, 1.20.4, 1.20.6, 1.21, 1.21.5, 1.21.6, 1.21.7, 1.21.9 | none |

The count is 3 Forge and 6 NeoForge supported targets, 9 new in total, out of 12 Forge and 14 NeoForge
targets in `settings.gradle.kts`.

- 1.21.3-neoforge is a development-only target. The 1.21.2-neoforge target publishes for 1.21.2-1.21.4, and
  1.21.3 is its runtime-test overlap; both use the same VoxelMap jar.
- The `26.3-neoforge` file is `voxelmap-neoforge-26.3-1.16.13.jar`, although Modrinth labels the version
  `26.3-1.16.12`. That is the version-number collision, which is why IDs are pinned.
- "Unsupported" means VoxelMap-Updated publishes no build for that loader and Minecraft version, checked on
  the Modrinth API on 2026-10-07.
- Provenance: the eight properties other than `26.3-neoforge` were added by the maintainer in `e88a02b6`:
  `voxelmap_forge` or `voxelmap_neoforge` in each target's `mods/versions/<target>/gradle.properties`, plus
  `compileOnly` and `runtimeOnly` entries in `mods/forge.gradle.kts` (lines 308-313) and
  `mods/neoforge.gradle.kts` (lines 179-184), guarded by `project.hasProperty`.
- `settings.gradle.kts` has no Forge 26.3 target, because Forge has not published a 26.3 loader. VoxelMap-Updated
  has a Forge 26.3 build (`hvGMwAAg`); add `voxelmap_forge` when that target exists.
- Fabric 26.2 is pinned to the older `OXZIqj2I` (1.16.8) while Forge and NeoForge 26.2 use 1.16.10. This
  change leaves Fabric's pin alone.

## 5. Evidence from the VoxelMap-Updated jars

Measured 2026-10-07 by the implementing maintainer's local session (`javap` over the jars in its Gradle
cache; `javac` and the JUnit launcher over prebuilt classpaths). It was not re-run while writing this spec,
and the implementation plan should treat it as the baseline to reproduce.

- **Same code on every loader.** The mod id is `voxelmap` and the package is `com.mamiyaotaru.voxelmap` on
  all three loaders. For Minecraft 1.21.11 and 26.1.x the Fabric, Forge and NeoForge jars contain the same
  classes apart from the loader-specific `fabric`/`forge`/`neoforge` packages, mixin configs and metadata.
  Forge and NeoForge jars use Mojang names, so nothing needs remapping. Fabric jars are intermediary-named up
  to 1.21.11 (Loom remaps them for development) and Mojang-named for 26.x.
- **Injection sites.** Ten injection-site checks mirror the six mixins. All pass on all seven distinct
  Forge and NeoForge jars (Forge 1.21.11, 26.1.2, 26.2; NeoForge 1.21.3, 1.21.11, 26.1.2, 26.2):
  1. `GuiAddWaypoint.init` reads `Waypoint.name` (the FIELD redirect).
  2. In `GuiAddWaypoint.acceptWaypoint` the first `EditBox.getValue()` call reads field `waypointName` (the
     `ordinal = 0` redirect).
  3. `GuiListWaypoints` (Minecraft 1.21.11 and later) or `GuiSlotWaypoints` (older): `setSelected` and
     `updateFilter` read `Waypoint.name`.
  4. The list's `$WaypointItem` row-render method reads `Waypoint.name` and calls the centered-text draw:
     `extractContent(GuiGraphicsExtractor, int, int, boolean, float)` for 26 and later,
     `renderContent(GuiGraphics, int, int, boolean, float)` for 1.21.9 to 1.21.11, and
     `render(GuiGraphics, 7 ints, boolean, float)` below 1.21.9.
  5. `GuiWaypoints.deleteClicked` reads `Waypoint.name`, and `editWaypoint(Waypoint)` exists.
  6. `GuiPersistentMap.drawWaypoint` and `popupAction` read `Waypoint.name`.
  7. `WaypointContainer.renderSign` (1.21.11 and later) or `renderWaypoints` (older) reads `Waypoint.name`.
- **Helper API.** The API `VoxelMapWaypointHelper` uses exists identically in the Fabric and Forge/NeoForge
  jars of the same Minecraft version: `VoxelConstants.getVoxelMapInstance()`; `VoxelMap.getWaypointManager()`
  and `getDimensionManager()`; `DimensionManager.getDimensionContainerByIdentifier(String)`;
  `WaypointManager.getWaypoints()`, `addWaypoint`, `deleteWaypoint`, `getCurrentSubworldDescriptor(boolean)`;
  `WaypointManager.isCoordinateHighlight` (present from the 1.21.11 builds on; the helper uses it only for
  Minecraft 26 and later); and `Waypoint`'s public fields and 11-argument constructor.
- **Icons.** The 39 waypoint images under `assets/voxelmap/images/waypoints/` and their per-version layout
  are identical across loaders, so `WaypointIconRenderer`'s resource-manager lookup works unchanged.
- **26.2 skew** (Fabric 1.16.8 against Forge/NeoForge 1.16.10): `WaypointContainer.renderSign`'s parameters
  changed (the mixin matches by name and has `require = 0`), and `GuiAddWaypoint.isWaypointAcceptable` was
  renamed `isWaypointInputValid` (no mixin touches it). No injection point is affected.
- **Compile.** The unchanged Stonecutter-generated Fabric-form sources of `VoxelMapIntegration`,
  `VoxelMapWaypointHelper` and the six mixins compile with `javac`, with no source change, against the
  compile classpaths of Forge 1.21.11, 26.1.2, 26.2 and NeoForge 1.21.3, 1.21.11, 26.1.2, 26.2. Not
  checked: NeoForge 1.21.2 (shares the jar with 1.21.3) and 26.3 (that jar was not yet downloaded).
- **Tests.** The VoxelMap test classes known then (five of the six in §2; `VoxelMapIconResourceTest` was
  not part of the measurement) ran unchanged, one class per JVM, through the JUnit launcher over those
  classpaths, not through Gradle.
  - Forge 1.21.11 and 26.2: all 25 tests pass (`VoxelMapWaypointContainerMixinTest` 3,
    `VoxelMapGuiListWaypointsItemMixinTest` 3, `VoxelMapUploadTest` 8, `VoxelMapWaypointDimensionTest` 5,
    `VoxelMapWaypointEditTargetTest` 6).
  - NeoForge 1.21.3, 1.21.11 and 26.2: 20 pass. `VoxelMapWaypointDimensionTest` errors in its `@BeforeAll`
    `Bootstrap.bootStrap()` with `IllegalStateException: There is no current FML Loader` (1.21.11, 26.2) or
    a `NullPointerException` from `LoadingModList.get()` (1.21.3).
  - Not run: Forge and NeoForge 26.1.2.

## 6. Design

### 6.1 Capability constant

Each of the five build scripts that configure mods targets already calls
`constants.match(loader, "fabric", "neoforge", "forge")` in its `stonecutter { }` block:
`mods/fabric.gradle.kts` (line 28), `fabric-unobfuscated.gradle.kts` (28), `forge.gradle.kts` (119),
`neoforge.gradle.kts` (49) and `neogradle.gradle.kts` (30). `settings.gradle.kts` `mapBuilds` routes
`1.20.2-neoforge` to `neogradle` and the 26.x Fabric targets to `fabric-unobfuscated`.

Add one line after that call in each script:

```kotlin
constants.put("voxelmap", project.hasProperty("voxelmap_<loader>"))
```

where `<loader>` is that script's loader id: `fabric` in the two Fabric scripts, `forge` in
`forge.gradle.kts`, `neoforge` in `neoforge.gradle.kts` and `neogradle.gradle.kts`. Stonecutter 0.9.7's
`ConstantContainer` has `put(String, boolean)`. On Fabric the constant is always true, because every Fabric
target defines `voxelmap_fabric`. `1.20.2-neoforge` defines no property, so it is false.

`AGENTS.md` gets a short "Constants" note listing the loader constants `fabric`, `neoforge` and `forge` and
`voxelmap`.

### 6.2 Source gating

Replace `fabric` with `voxelmap` in every `//? if fabric` listed in §2: the three integration files, the six
mixins, the six test classes and the two inline test conditions. Keep each closing `//?}` and any leading
`//~` replacement-token line in place; `AGENTS.md` requires such tokens to stay before the first
non-comment line (`VoxelMapIconResourceTest` starts with `//~ resource_location_import`).

`MapModIntegrationsTest.voxelMapIsSupportedOnlyOnFabric` becomes `voxelMapIsSupportedOnlyWhereVoxelMapIsPinned`,
asserting that `MapModIntegrations.find(UploadTarget.VOXELMAP)` is present when `voxelmap` is true and empty
otherwise, using the same `//? if voxelmap {` ... `//?} else {` form it uses now.

### 6.3 Runtime wiring

In `ServerWaypointForgeClient.ensureClientStarted()` and `ServerWaypointNeoForgeClient.ensureClientStarted()`,
next to the existing Xaero line, add a closed scope:

```java
//? if voxelmap {
ClientConfig.isVoxelMapLoaded = <ModList lookup>.isLoaded("voxelmap");
//?}
```

- Forge uses `ModList/*? if < 26 {*//*.get()*//*?}*/.isLoaded("voxelmap")`, the form on its Xaero line.
- NeoForge uses `ModList.get().isLoaded("voxelmap")`.
- The NeoForge client file is wrapped in `//? if neoforge { /* ... */` on non-NeoForge builds, so the new
  scope there must use the escaped nested forms Stonecutter expects (`/^ ... ^/`; follow the existing ones
  in that file). A literal `*/` inside it breaks `stonecutterPrepare` on every target, so the plan runs
  `stonecutterPrepare` on one Forge, one NeoForge and one Fabric target after the edit.

Nothing else in the integration changes: sync events, the helper and the mixins are loader-neutral.

### 6.4 Metadata, resources and packaging

Supported targets register the config and the dependency; unsupported ones do neither.

- **Forge `mods.toml`.** Add a `[[mixins]]` entry `config = "server_waypoint-voxelmap.mixins.json"` and an
  optional dependency block:

  ```toml
  [[dependencies.${id}]]
  modId = "voxelmap"
  mandatory = false
  ordering = "NONE"
  side = "CLIENT"
  ```

  It has no `versionRange`, because VoxelMap-Updated's own versions look like `1.21.11-1.16.6`.
- **NeoForge `neoforge.mods.toml`.** The same two blocks in NeoForge syntax (`type = "optional"` in place of
  `mandatory`). The file is also used by `neogradle.gradle.kts` (`1.20.2-neoforge`).
- **Templating.** Add a boolean `"voxelmap"` key to the map each script passes to `expand` for its toml, and
  wrap the blocks in Groovy template conditionals (`<% if (voxelmap) { %> ... <% } %>`; Gradle's `expand`
  uses `SimpleTemplateEngine`). The three expand sites are `replaceProperties` in `tasks.processResources`
  in `forge.gradle.kts` (line 322), and the maps in the `filesMatching("META-INF/neoforge.mods.toml")` blocks
  of `neoforge.gradle.kts` (line 203) and `neogradle.gradle.kts` (line 130). `neogradle.gradle.kts` passes
  `false` through its constant; omitting the key there would fail the template. Register the value with
  `inputs.property` where the script does so for its other keys (the Forge script does that through
  `inputs.properties(replaceProperties)`).
- **Forge manifest and run configuration.** The jar manifest attribute `MixinConfigs` becomes a
  comma-separated list when `voxelmap` is true, and the run configuration (line 215) gets a second
  `-mixin.config=` argument for the VoxelMap config. If the live check shows Forge rejects the repeated
  argument, drop only that argument; `mods.toml` registers the config in development runs too.
- **Resource exclusion.** Unsupported Forge and NeoForge targets exclude `server_waypoint-voxelmap.mixins.json`
  through `sourceSets.main.resources.exclude(...)` in all three scripts, guarded by `!voxelmap`. Supported
  targets and Fabric keep it.
- **Existing mistake.** `mods/forge.gradle.kts` line 159 excludes `server_waypoint-fabric.mixins.json`, which
  does not exist. The implementer touching that block corrects it.

The plan must inspect the rendered `build/resources` toml for one supported and one unsupported target per
loader.

### 6.5 Build, upload and release gate

- **Forge test classpath.** Forge's `compileOnly` dependencies are not on its test compile classpath. Add,
  inside the existing `voxelmap_forge` guard in `mods/forge.gradle.kts`,
  `testImplementation("maven.modrinth:voxelmap-updated:$voxelmap_forge")` beside the existing `compileOnly`
  and `runtimeOnly` lines (308-313); the Xaero `testImplementation` at line 306 is the model. NeoForge's test classpath inherits its main compile classpath, so it
  needs nothing.
- **Modrinth.** Build the dependency list so `ModDependency("wkzK5379", "optional")` is added if and only
  if the project has `voxelmap_<targetLoader>`. Fabric behaviour is unchanged; Paper and Velocity lists are
  untouched. CurseForge is unchanged.
- **Checking the upload lists** needs neither tokens nor a build: run
  `./gradlew help --no-configure-on-demand -I <init script>` (`gradle.properties` enables
  configure-on-demand, which would skip the other projects). The init script hooks `gradle.projectsEvaluated`
  and reads `extensions.findByName("modrinth").getDependencies()` by reflection on each mods project.
- **Release gate.** Add the rule `missing-mixin-class` to `tools/check_release_jar.py`: for every
  `*.mixins.json` at a jar's root, every class named in its `mixins`, `client` and `server` arrays
  (`package` + `"."` + name, as a `.class` path) must exist in the jar. Violations read
  `missing-mixin-class: <path> (named by <config>)`. Add tests to `tools/test_release_artifacts.py`
  (`CheckReleaseJarTest` builds fixture jars with `write_jar`): a config whose classes are all present
  passes, one naming an absent class fails with the rule, and `client` and `server` arrays are both read.
  `tools/verify-release-artifacts.sh` needs no change.

### 6.6 Tests

- **Switch.** The six gated test classes and the two inline conditions switch from `fabric` to `voxelmap`.
- **Bootstrap guard for `VoxelMapWaypointDimensionTest`.** Follow `TextHelperTest` and
  `WaypointFormStateTest`: `@BeforeAll` wraps `SharedConstants.tryDetectVersion()` and
  `Bootstrap.bootStrap()` in `try { ... } catch (Throwable failure)` and calls
  `Assumptions.abort("Minecraft could not be bootstrapped in this test runtime: " + failure)`. Add a
  `@BeforeEach` that calls `MinecraftTestRuntime.assumeEntityTypesAreRegistered()`. A bootstrap that
  fails still marks itself done, so a later class in the same JVM would see no failure and empty registries;
  the shared guard makes that class skip regardless of order. `CommandChatIconsTest` and
  `ModMessageSenderTest` use the same two pieces and are not changed.
- **New `VoxelMapMixinTargetsTest`** in `mods/src/test/java/_959/server_waypoint/mixin/voxelmap/`, under
  `//? if voxelmap {`. It uses only ASM, with no Minecraft bootstrap (ASM and Mixin are on every mods test
  classpath; `VoxelMapWaypointContainerMixinTest` already uses them). For every mixin class listed in
  `server_waypoint-voxelmap.mixins.json` it reads the `@Mixin` targets and the injector annotations from the
  class bytes, loads each target class's bytes from the VoxelMap jar on the test classpath, and asserts:
  1. the target class exists;
  2. every injector `method` name exists in it; an injector with `require = 0` may be absent (today only
     `VoxelMapWaypointContainerMixin` has one);
  3. every `@At(value = "FIELD", target = ...)` has a matching `GETFIELD` or `PUTFIELD` inside the named
     methods;
  4. for `GuiAddWaypoint.acceptWaypoint`, the first `EditBox.getValue()` call's receiver is `waypointName`.

  It runs on every `voxelmap` target, so a VoxelMap bump that breaks an injection fails `./gradlew test`
  instead of crashing the game.
- **Relation to `VoxelMapWaypointContainerMixinTest`.** That test checks one mixin finely: the exact
  `Waypoint.name` reads in `renderSign` or `renderWaypoints`, compared with the redirect. The new test
  checks all six mixins more coarsely, from the mixin side. Both stay; the new test does not replace it.

### 6.7 Documentation, made at implementation time

- `README.md`, `README_zh.md` and the 4.0.0 `CHANGELOG.md` entry stop saying VoxelMap is Fabric-only and
  name the supported versions, taking ranges from each target's `mcVersionRange` (for example NeoForge
  1.21.2-1.21.4, 1.21.11, 26.1-26.1.2, 26.2 and 26.3; Forge 1.21.11, 26.1-26.1.2 and 26.2).
- `AGENTS.md` gets the constants note from §6.1.
- A validation record goes under `docs/features/voxelmap-sync/validation/`, and this feature's README gains
  links to the plan and the record.

## 7. Verification plan

1. Compile every mods target (the full Stonecutter matrix), including `stonecutterPrepare` after the
   NeoForge client edit.
2. `:mods:<target>:test` for the 9 new targets (Forge 1.21.11, 26.1.2, 26.2; NeoForge 1.21.2, 1.21.3,
   1.21.11, 26.1.2, 26.2, 26.3) and a Fabric regression run on at least 1.20.1, 1.21.11 and 26.1.2. The new
   `VoxelMapMixinTargetsTest` must pass on all of them. When a Forge-family test fails, run each class on
   its own, because a failed Minecraft bootstrap poisons later classes in the same JVM.
3. Stage the release jars, then run `bash tools/verify-release-artifacts.sh` and
   `python3 tools/test_release_artifacts.py`.
4. The upload-dependency check from §6.5.
5. Inspect the rendered `mods.toml` or `neoforge.mods.toml` and the jar contents for one supported and one
   unsupported target per loader.
6. Live check (D7), on Forge 1.21.11 and NeoForge 1.21.11 with VoxelMap: boot to the title screen with a
   1024M heap as in [client loading](../../client-loading/); open VoxelMap's waypoint list, add-waypoint and
   world-map screens without a mixin error; in a singleplayer world, `/wp add` shows up in VoxelMap with the
   synced name and the sync chat message. VoxelMap is `runtimeOnly` on Forge, and the ForgeGradle 7
   development-run module caveat in the
   [jar packaging validation](../../jar-packaging/validation/2026-10-03-jar-packaging-boundaries.md)
   makes a Forge dev-run module conflict possible, so the live check must cover it.

Evidence goes under `docs/features/voxelmap-sync/validation/`.

## 8. Alternatives considered

- **Gating by per-file version predicates**, such as
  `fabric || (neoforge && (=1.21.2 || =1.21.3 || ...)) || (forge && >=1.21.11)`. Rejected: the expression
  repeats in about 17 files, and a target added later is easy to miss in one of them. The constant has one
  definition, which follows `gradle.properties`.
- **Registering the config on every target and shipping an empty stub config where unsupported.** Rejected:
  the metadata would be static, but every jar would claim an integration it lacks, and the stub would
  have to stay in step with the real config.
- **Reflection against VoxelMap with no compile dependency.** Rejected: it means rewriting the helper and
  the mixins, and the mixins cannot be expressed by reflection.

## 9. Risks and non-goals

Risks:

- The mixins are `required`, so a VoxelMap build other than the one compiled against can still crash the
  game on an injection mismatch. For example, the NeoForge 1.21.2-1.21.4 jar is compiled against VoxelMap
  1.21.3, and 1.21.4 has its own builds. Fabric has the same exposure today, and `VoxelMapMixinTargetsTest`
  guards only the pinned builds.
- The Forge dev-run caveat in §7.
- Plain-JUnit Minecraft bootstrap limits on NeoForge: `VoxelMapWaypointDimensionTest` skips there, and its
  behaviour is covered only by the live check.
- `26.3-neoforge` and `1.21.2-neoforge` were not compile-checked before this spec, and Forge and NeoForge
  26.1.2 test runs were not done.
- A repeated `-mixin.config=` argument may be rejected by Forge's launcher; §6.4 gives the fallback.

Non-goals:

- CurseForge relations.
- Softening `required`.
- Adding a Forge 26.3 target.
- Bumping Fabric's 26.2 pin.
- VoxelMap versions outside the pinned builds.
- Any backward-compatibility code (`AGENTS.md` forbids it unless asked).
- Any change to what the VoxelMap integration does.
