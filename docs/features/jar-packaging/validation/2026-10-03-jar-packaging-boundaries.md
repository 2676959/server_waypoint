# Jar packaging boundaries: validation

Evidence for the [design](../specs/2026-10-03-jar-packaging-boundaries-design.md) and the
[implementation plan](../plans/2026-10-03-jar-packaging-boundaries.md).

## Baseline

Recorded on 2026-10-04 at `bc782937c09acb8c7bc03745a2bf2015a2a28fca` (branch `4.0.0`) before any
product change. Only the new checker and the release gate's wiring were in the working tree.

- **Build.** `./gradlew build --continue --max-workers=2` reached every target and reported
  `BUILD FAILED` for one reason that predates this change and has nothing to do with packaging. On
  the six NeoForge targets from 1.21.6 (1.21.6, 1.21.9, 1.21.11, 26.1.2, 26.2 and 26.3), both
  `WaypointDetailsWidgetInputTest` tests fail with `NoSuchMethodError:
  JsonReader.setStrictness(Strictness)` while `net.minecraft.locale.Language` initializes: the
  NeoForge test classpath puts `common`'s Gson 2.10.1 ahead of the newer Gson that Minecraft needs.
  The test arrived in `5b1c5e7a`. Every other task passed, and every release jar was up to date.
- **Checker.** `tools/check_release_jar.py` over the 42 staged jars reported no `missing-class` and
  no `unreadable-class` violations, so every internal class reference already resolves inside its
  jar. All 327 violations are `forbidden-entry` hits on rules this change introduces:

| Jars | Forbidden entries in each jar |
| --- | --- |
| Velocity | 115: `lang/` (6 files), `assets/server_waypoint/chat-sprites.json`, `SERVER_WAYPOINT_CREDITS.txt`, Noise's `META-INF/maven/` (2), `core/WaypointServerCore`, and the classes under `command/` (16), `config/` (5, including bStats' relocated `MetricsConfig`), `navigation/` (23), `text/` (58, including `VanillaChatSprites`) and `translation/` (2) |
| 24 backends below 1.21.9: Fabric, Forge and NeoForge up to 1.21.6, and Paper 1.21 | 6: `TcpCoordinator`, `CoordinatorTransport`, `META-INF/maven/` (2), `chat-sprites.json` and `VanillaChatSprites` |
| 17 backends from 1.21.9 | 4: `TcpCoordinator`, `CoordinatorTransport` and `META-INF/maven/` (2) |

- **Test results.** Test-result XML files: `common` 75 (776 tests), `proxy-common` 9 (116 tests),
  `velocity` 2 (8 tests), Fabric 26.1.2 95 and Fabric 1.20.1 94.

## Result

Recorded on 2026-10-04 against `bc782937` plus the uncommitted change, after the final full build.

- **Velocity.** The jar shrank from 1,178,859 to 386,829 bytes (32.8% of its size). It no longer
  contains `lang/`, `assets/`, the credits file, the waypoint core or `common`'s command,
  configuration, navigation, text and translation classes.
- **Release gate.** `bash tools/verify-release-artifacts.sh` on the staged jars printed
  `Verified 42 release JARs for Server Waypoint 4.0.0: Fabric=13 Forge=12 NeoForge=12 Paper=4 Velocity=1.`
  The checker reports no violation of any rule in any of the 42 jars, so every internal class
  reference resolves inside its jar.
- **Gate tests.** `python3 tools/test_release_artifacts.py` ran 25 tests: OK.
- **Noise audit.** `python3 tools/noise-platform-test/audit.py` printed `Verified 42 artifacts; shared
  modules contain Java 17 bytecode and no platform API references.` The audit now skips the two
  development-only 1.21.3 targets, as the gate does, and checks `cross-server` among the shared modules.
- **Full build.** `./gradlew build --continue --max-workers=2` finished with `BUILD SUCCESSFUL`. The six
  NeoForge test failures described in the baseline no longer occur: during this work a concurrent change
  outside this feature added `addModdingDependenciesTo(sourceSets.test.get())` to `mods/neoforge.gradle.kts`,
  so the NeoForge test classpath now resolves together with Minecraft's libraries and carries only Gson 2.13.2.

### Boots

- **Velocity 4.1.0**, a copy of the local proxy jar and `velocity.toml` in a scratch directory, bound to
  `127.0.0.1:25598`, with the new plugin and a freshly generated forwarding secret. The first boot wrote
  `plugins/server_waypoint/cross-server.json` and printed bStats' first-run notice, so the relocated
  `Metrics.Factory` was injected. With `"enabled": true` and `"listen": "127.0.0.1:25591"`, the second
  boot logged `Server Waypoint coordinator startup: SUCCESS`, and `serverwaypoint status` printed
  `Server Waypoint cross-server: running`, `Transport mode: NOISE_KK (encrypted)`,
  `Coordinator listening on port 25591` and `Online servers (0): none`. `cross-server-public-key.txt`
  and `plugins/bStats/` exist, and no exception was logged.
- **Paper 1.21.11** (`runServer`): Server Waypoint 4.0.0 enabled, loaded its translations, configuration
  and waypoints, reported the cross-server backend disabled, and saved on `stop`. No exception mentions
  `_959` or bStats, so the relocated bStats passed its own relocation check. The run directory was
  restored afterwards.
- **NeoForge 1.21.11** (`runServer`): the `server_waypoint` mod group lists the `common` and
  `cross-server` class directories, the mod was constructed (`Cross-server backend startup: DISABLED`),
  and the server reached `Done` with no `ResolutionException`, split-package, `NoClassDefFoundError` or
  `IllegalAccessError`. The NeoForge development server skips the EULA check and does not forward
  standard input through Gradle, so it was stopped with SIGTERM, which saved all dimensions. The run
  directory was restored afterwards.
- **Forge 1.21.11** (`runServer --no-parallel`): `server_waypoint` was discovered with no module error,
  and the run ended at the EULA prompt, before mods are constructed, exactly as at `bc782937`. Mod
  construction on Forge stays unverified until `eula.txt` is accepted in that run directory. Two points
  were found along the way:
  - In Gradle's default parallel mode, `runServer` fails before launching with `Resolution of the
    configuration ':common:runtimeClasspath' was attempted without an exclusive lock`. This also happens at
    `bc782937` and is unrelated to this change.
  - ForgeGradle 7 does not merge the mod group the way ModDevGradle does: its bootstrap loads each library
    jar on the run classpath as its own module, so the separate `common` and `cross-server` jars failed
    with `ResolutionException: Modules common and cross.server export package _959.server_waypoint.util`.
    `mods/forge.gradle.kts` now gives the development runs one merged jar of both modules, named
    `common.jar`, in their place; release jars and tests are unaffected. Legacy Forge 1.20.1 also
    resolves its modules with this; it then stops on an older Mixin failure in `ItemEntityNavigationMixin`
    that its September logs already show.

### Test results

| Module | Before: result files / tests | After: result files / tests | Moved tests |
| --- | --- | --- | --- |
| `cross-server` | — | 8 / 112 | 8 classes from `common` |
| `common` | 75 / 776 | 65 / 616 | 8 to `cross-server` (112 tests), `TcpTransportTest` to `proxy-common` (45), `WaypointRevisionSequenceTest` to `mods` (3) |
| `proxy-common` | 9 / 116 | 10 / 161 | `TcpTransportTest` from `common` |
| `velocity` | 2 / 8 | 2 / 8 | — |
| Fabric 26.1.2 | 95 files | 96 / 545 | `WaypointRevisionSequenceTest` from `common` |
| Fabric 1.20.1 | 94 files | 95 / 542 | `WaypointRevisionSequenceTest` from `common` |

All 900 `common`, `proxy-common` and `velocity` tests are accounted for: 616 + 112 + 161 + 8 = 897,
plus the 3 that run in every mods target. `PlatformArrivalSchedulingTest` compiles the platform
adapters against `common`'s class directory, so it now adds `cross-server`'s classes to that compile.

### Deviations from the plan

- **Forge development runs.** Spec §1.6 expected the ForgeGradle mod group to absorb the split packages,
  as ModDevGradle's does. It does not, so `mods/forge.gradle.kts` adds the merged development jar described
  under Boots. Forge runs also need `--no-parallel` until the older Gradle lock error is fixed.
- **Noise audit.** Besides expecting four Paper jars, the audit skips the development-only `1.21.3-fabric`
  and `1.21.3-neoforge` targets, which it previously counted; without that it cannot reach 42 artifacts.
- **`PlatformArrivalSchedulingTest`.** It compiles the real platform adapters with `javac` against
  `common`'s classes, and their signatures now include `cross-server` types, so its classpath gained
  `cross-server`'s classes.
- **Checker.** The final review found three gaps in the algorithm the plan prescribed, now closed. Classes
  under `META-INF/versions/<n>/` count only in a jar whose manifest says `Multi-Release: true`, and only up
  to the oldest Java the jar's own classes run on. The content rules judge versioned copies by their logical
  path. The parser checks the whole class structure, not just the constant pool, so a class cut short after
  its constant pool is unreadable. Besides the planned cases, the tests cover these, other malformed class
  files and the command-line exit codes. Directory entries are ignored by the content rules, since they
  carry no bytes. All 45,813 class files in the 42 final jars still parse, none of them is versioned, and
  every jar stays clean.

### Jar sizes

| Jar | Before (bytes) | After (bytes) | Change |
| --- | ---: | ---: | ---: |
| `server_waypoint-4.0.0-velocity.jar` | 1,178,859 | 386,829 | -792,030 (-67.2%) |
| `server_waypoint-4.0.0-fabric-mc1.20-1.20.1.jar` | 2,332,995 | 2,313,768 | -19,227 (-0.8%) |
| `server_waypoint-4.0.0-forge-mc1.20-1.20.1.jar` | 2,934,971 | 2,915,743 | -19,228 (-0.7%) |
| `server_waypoint-4.0.0-fabric-mc1.20.2.jar` | 2,332,775 | 2,313,548 | -19,227 (-0.8%) |
| `server_waypoint-4.0.0-forge-mc1.20.2.jar` | 2,934,314 | 2,915,088 | -19,226 (-0.7%) |
| `server_waypoint-4.0.0-neoforge-mc1.20.2.jar` | 2,298,569 | 2,279,385 | -19,184 (-0.8%) |
| `server_waypoint-4.0.0-fabric-mc1.20.3-1.20.4.jar` | 2,334,318 | 2,315,091 | -19,227 (-0.8%) |
| `server_waypoint-4.0.0-forge-mc1.20.3-1.20.4.jar` | 2,935,708 | 2,916,482 | -19,226 (-0.7%) |
| `server_waypoint-4.0.0-neoforge-mc1.20.3-1.20.4.jar` | 2,301,781 | 2,282,597 | -19,184 (-0.8%) |
| `server_waypoint-4.0.0-fabric-mc1.20.5-1.20.6.jar` | 2,372,206 | 2,352,988 | -19,218 (-0.8%) |
| `server_waypoint-4.0.0-forge-mc1.20.6.jar` | 2,949,779 | 2,930,774 | -19,005 (-0.6%) |
| `server_waypoint-4.0.0-neoforge-mc1.20.5-1.20.6.jar` | 2,341,155 | 2,322,001 | -19,154 (-0.8%) |
| `server_waypoint-4.0.0-fabric-mc1.21-1.21.1.jar` | 2,372,341 | 2,353,123 | -19,218 (-0.8%) |
| `server_waypoint-4.0.0-forge-mc1.21-1.21.1.jar` | 2,949,949 | 2,930,944 | -19,005 (-0.6%) |
| `server_waypoint-4.0.0-neoforge-mc1.21-1.21.1.jar` | 2,341,331 | 2,322,177 | -19,154 (-0.8%) |
| `server_waypoint-4.0.0-fabric-mc1.21.11.jar` | 2,468,331 | 2,460,780 | -7,551 (-0.3%) |
| `server_waypoint-4.0.0-forge-mc1.21.11.jar` | 2,415,983 | 2,408,599 | -7,384 (-0.3%) |
| `server_waypoint-4.0.0-neoforge-mc1.21.11.jar` | 2,441,811 | 2,434,310 | -7,501 (-0.3%) |
| `server_waypoint-4.0.0-fabric-mc1.21.2-1.21.4.jar` | 2,338,911 | 2,319,693 | -19,218 (-0.8%) |
| `server_waypoint-4.0.0-neoforge-mc1.21.2-1.21.4.jar` | 2,308,153 | 2,288,999 | -19,154 (-0.8%) |
| `server_waypoint-4.0.0-forge-mc1.21.3-1.21.4.jar` | 2,998,739 | 2,979,734 | -19,005 (-0.6%) |
| `server_waypoint-4.0.0-fabric-mc1.21.5.jar` | 2,438,967 | 2,419,749 | -19,218 (-0.8%) |
| `server_waypoint-4.0.0-forge-mc1.21.5.jar` | 3,020,464 | 3,001,459 | -19,005 (-0.6%) |
| `server_waypoint-4.0.0-neoforge-mc1.21.5.jar` | 2,412,718 | 2,393,564 | -19,154 (-0.8%) |
| `server_waypoint-4.0.0-fabric-mc1.21.6-1.21.8.jar` | 2,464,477 | 2,445,259 | -19,218 (-0.8%) |
| `server_waypoint-4.0.0-forge-mc1.21.6-1.21.8.jar` | 3,046,043 | 3,027,038 | -19,005 (-0.6%) |
| `server_waypoint-4.0.0-neoforge-mc1.21.6-1.21.8.jar` | 2,438,484 | 2,419,330 | -19,154 (-0.8%) |
| `server_waypoint-4.0.0-fabric-mc1.21.9-1.21.10.jar` | 2,467,973 | 2,460,422 | -7,551 (-0.3%) |
| `server_waypoint-4.0.0-forge-mc1.21.9-1.21.10.jar` | 3,049,135 | 3,041,751 | -7,384 (-0.2%) |
| `server_waypoint-4.0.0-neoforge-mc1.21.9-1.21.10.jar` | 2,441,387 | 2,433,886 | -7,501 (-0.3%) |
| `server_waypoint-4.0.0-fabric-mc26.1-26.1.2.jar` | 2,458,632 | 2,451,134 | -7,498 (-0.3%) |
| `server_waypoint-4.0.0-forge-mc26.1-26.1.2.jar` | 2,419,036 | 2,411,653 | -7,383 (-0.3%) |
| `server_waypoint-4.0.0-neoforge-mc26.1-26.1.2.jar` | 2,444,957 | 2,437,460 | -7,497 (-0.3%) |
| `server_waypoint-4.0.0-fabric-mc26.2.jar` | 2,458,757 | 2,451,259 | -7,498 (-0.3%) |
| `server_waypoint-4.0.0-forge-mc26.2.jar` | 2,419,157 | 2,411,774 | -7,383 (-0.3%) |
| `server_waypoint-4.0.0-neoforge-mc26.2.jar` | 2,445,068 | 2,437,571 | -7,497 (-0.3%) |
| `server_waypoint-4.0.0-fabric-mc26.3.jar` | 2,458,732 | 2,451,237 | -7,495 (-0.3%) |
| `server_waypoint-4.0.0-neoforge-mc26.3.jar` | 2,445,047 | 2,437,553 | -7,494 (-0.3%) |
| `server_waypoint-4.0.0-paper-mc1.21-1.21.8.jar` | 1,196,804 | 1,174,408 | -22,396 (-1.9%) |
| `server_waypoint-4.0.0-paper-mc1.21.11-26.1.2.jar` | 1,198,550 | 1,188,055 | -10,495 (-0.9%) |
| `server_waypoint-4.0.0-paper-mc1.21.9-1.21.10.jar` | 1,198,211 | 1,187,716 | -10,495 (-0.9%) |
| `server_waypoint-4.0.0-paper-mc26.2-26.3.jar` | 1,198,573 | 1,188,078 | -10,495 (-0.9%) |
| **Total (42 jars)** | **100,004,151** | **98,613,009** | **-1,391,142 (-1.4%)** |
