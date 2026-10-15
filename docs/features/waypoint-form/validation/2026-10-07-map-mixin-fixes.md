# Map integration mixin fixes — 2026-10-07

Reviewed the production mixins on branch `4.0.0` and fixed five map integration
issues. The active Stonecutter project remains `26.1.2-fabric`.

| Issue | Result |
| --- | --- |
| World Map stripped the ownership prefix from `getSetName()`, breaking Xaero's native set lookup | Keep the encoded set key intact; the dropdown hooks still format its visible label |
| World Map server actions used the player's dimension | Use `SupportMods.xaeroMinimap.getWaypointWorld().getDimId()`, the world owning the waypoint, including projected markers |
| NeoForge 1.21.5 targeted the old dropdown package | Target XaeroLib's dropdown class for every configured build that uses it |
| World Map edits reconstructed an incomplete waypoint from the map marker | Resolve dimension/list/name against the server cache and copy the complete waypoint, including list display name |
| VoxelMap cancelled Edit even when its server target could not be resolved | Cancel only after opening the server form; stale or ambiguous markers fall through to the native editor |

World Map's server-edit option is disabled when its cached target is missing.
The action resolves the target again when clicked; if it disappeared while the
menu was open, it shows the existing waypoint-not-found message. Unknown source
worlds do not fall back to the player's dimension.

## Dependency pairs

The isolated Forge 1.20.1 launch exposed a loader rejection: Minimap 25.3.13
requires World Map 1.40.2 or newer, while the branch pinned 1.39.12. Checking the
other pinned JARs' metadata found five more incompatible pairs. These updates
use the minimum versions required by the existing counterpart:

| Target | Dependency update |
| --- | --- |
| Forge 1.20.1 | World Map [1.40.2_Forge_1.20](https://modrinth.com/mod/xaeros-world-map/version/U3stuFts) |
| NeoForge 1.20.4 | Minimap [25.3.2_NeoForge_1.20.4](https://modrinth.com/mod/xaeros-minimap/version/qJUbl49M) |
| NeoForge 1.21 | Minimap [25.3.2_NeoForge_1.21](https://modrinth.com/mod/xaeros-minimap/version/iTA5fl45) |
| NeoForge 1.21.2 | Minimap [25.3.2_NeoForge_1.21.3](https://modrinth.com/mod/xaeros-minimap/version/63tCp6uJ), retaining the existing development-version overlap |
| Forge 1.21.3 | Minimap [25.3.2_Forge_1.21.3](https://modrinth.com/mod/xaeros-minimap/version/GnbbSf21) |
| Forge 1.21.5 | Minimap [25.3.2_Forge_1.21.5](https://modrinth.com/mod/xaeros-minimap/version/I6HuS7lf) |

The dropdown predicate includes these updated Minimap builds. The new
`XaerosDependencyCompatibilityTest` reads Fabric JSON or Forge/NeoForge TOML
directly from the dependency JARs and checks both mods' declared minimums. Forge
now includes World Map in its test dependencies so this check runs there too.

## Automated verification

The native-set and VoxelMap cancellation tests failed before the corresponding
fixes and passed afterward. The dropdown test reproduced the absent target on
NeoForge 1.21.5 and on all five newly aligned Minimap builds. The dependency-pair
test reproduced the five minimum-version violations before updating those pins.
The World Map target tests cover duplicate names across dimensions and lists,
custom dimensions, missing targets, and every stored waypoint field in a
detached copy.

Ran every `:mods:<target>:test` and `:mods:<target>:assemble` task from the
40-target matrix in `settings.gradle.kts`, using `rtk proxy ./gradlew` with
`--continue --console=plain`. Both complete matrix runs passed.

| Loader | Targets | Tests passed | Skipped | Failures/errors |
| --- | ---: | ---: | ---: | ---: |
| Fabric | 14 | 9,568 | 0 | 0 |
| NeoForge | 14 | 8,972 | 91 | 0 |
| Forge | 12 | 7,592 | 10 | 0 |
| Total | 40 | 26,132 | 101 | 0 |

The skips belong to existing tests with runtime/bootstrap conditions. None of
the new regression tests was skipped. A temporary ASM audit checked compiled
mixin targets, members, injection points, callback signatures, and named locals
against each target's dependency bytecode: 8,454 checks, zero failures across all
40 targets. This structural audit is separate from runtime Mixin application.

## Runtime verification

Fresh production JARs and the repository's pinned map dependencies were staged
in isolated temporary game directories. Installed client mods and worlds were
preserved. Each client used `-Xmx1024m`, reached a title screen without a loading
overlay for at least 15 seconds, and logged Server Waypoint client initialization.
Runtime probes loaded the production mixins' target classes, checked the encoded
World Map set key and applied dropdown handlers, and ran `MixinEnvironment.audit()`.

| Minecraft | Loader | Java | Outcome |
| --- | --- | --- | --- |
| 26.1.2 | Fabric 0.18.6 | 25 | Loading and Mixin transformation passed |
| 1.21.5 | NeoForge 21.5.88 | 21 | Loading and Mixin transformation passed |
| 1.20.1 | Forge 47.4.20 | 17 | Loading and Mixin transformation passed after aligning World Map |
| 1.20.4 | NeoForge 20.4.251 | 17 | Loading and Mixin transformation passed after aligning Minimap |

The global audit logged `ClassAlreadyLoadedException` diagnostics for
`ClientboundSectionBlocksUpdatePacket` and/or `LevelStorageAccess` on the
Forge/NeoForge clients. These classes are outside Server Waypoint's mixin
targets. Its own target loading and changed-handler checks succeeded, with no
injection exceptions in the client logs.

The incompatible Forge attempt was retained separately. Each tracked test JVM
was stopped and its exit confirmed after verification.

Live editor interactions and save round trips were subsequently verified on the
top five downloaded Minecraft versions for each implemented loader. See the
[top-download verification record](2026-10-07-top-download-mixin-verification.md)
for all 15 profiles, owning-dimension checks, metadata preservation and clearing,
Cancel/native fallback, and world reopening. That expanded matrix also found
and fixed the Forge 26.1.2/26.2 loader minimums; regression checks cover both pins.

Evidence is retained in `build/mixin-validation-2026-10-07/` and the temporary
`/private/tmp/sw-mixin-review/` harness. Implementation commits: `fa9ce01a`
(dependency and loader compatibility), `09f86c98` (World Map ownership and
metadata), and `b78e1fd0` (VoxelMap fallback).
