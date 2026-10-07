# VoxelMap Sync on Forge and NeoForge: Validation

Date: 2026-10-07. Covers the implementation of the
[Forge and NeoForge design](../specs/2026-10-07-voxelmap-forge-neoforge-design.md).

## Environment limits

The implementing session ran in a cloud container whose network policy denies Minecraft's Maven
repositories (`maven.neoforged.net`, `maven.minecraftforge.net`, `maven.fabricmc.net`,
`repo.spongepowered.org`) and Modrinth (`api.modrinth.com`, `cdn.modrinth.com`). Maven Central was
reachable. No Gradle configuration, `stonecutterPrepare`, compile, `:mods:<target>:test`, jar staging
or client launch could run there. Steps 1, 2, 3 (jar staging and `verify-release-artifacts.sh`), 4, 5 (jar
contents) and 6 of the design's verification plan (§7) are **still open** and must run on a machine with
those repositories.

## What ran

| Check | Result |
| --- | --- |
| `python3 tools/test_release_artifacts.py -v CheckReleaseJarTest` | 20 tests pass, including the 4 new `missing-mixin-class` / `unreadable-mixin-config` tests. |
| `python3 tools/test_release_artifacts.py` (whole file) | 29 tests, 10 fail. The same 10 fail on `77d548e` without this change: `tools/verify-release-artifacts.sh` expects 42 release jars while `settings.gradle.kts` now yields 43 (the `26.3-fabric` target). Unrelated to this feature. |
| `META-INF/mods.toml` and `META-INF/neoforge.mods.toml` rendered with Groovy 4 `SimpleTemplateEngine` (the engine behind Gradle's `expand`), `voxelmap` true and false | `true` adds the optional `voxelmap` client dependency and the `server_waypoint-voxelmap.mixins.json` `[[mixins]]` entry; `false` renders the previous content plus blank lines. |
| `VoxelMapMixinTargetsTest` compiled with `javac` and run with the JUnit 5 console launcher against stub VoxelMap and Mixin classes (ASM 9.7, Gson 2.11) | Passes on matching stubs, including a bridge overload of a bare-name target. Each mutation fails with the expected message: a removed `Waypoint.name` read in `updateFilter`, `acceptWaypoint`'s first `getValue()` reading another field, a renamed `init`, a changed `render()V` descriptor, a missing target class. |
| Stonecutter marker count (`//? if ... {` against closers) on every touched Java file | Balanced. |

## Open items

1. `stonecutterPrepare` on one Fabric, one Forge and one NeoForge target (the NeoForge client edit sits inside
   the file-wide `//? if neoforge` comment), then the full matrix compile.
2. `:mods:<target>:test` on Forge 1.21.11, 26.1.2, 26.2; NeoForge 1.21.2, 1.21.3, 1.21.11, 26.1.2, 26.2, 26.3;
   and Fabric 1.20.1, 1.21.11, 26.1.2. `26.3-neoforge` and `1.21.2-neoforge` were never compile-checked.
3. Release-jar staging, `bash tools/verify-release-artifacts.sh`, and jar/toml inspection for one supported
   and one unsupported target per loader.
4. The Modrinth dependency-list check (§6.5 of the design).
5. The live boot on Forge 1.21.11 and NeoForge 1.21.11 with VoxelMap, including whether Forge accepts the
   repeated `-mixin.config=` run argument.
