# World marker platform/API checks

Date: 2026-11-12. Revision: `55d0dd6c` plus uncommitted acceptance harness and documentation changes. No product Java or Stonecutter source edits were required.

```sh
rtk proxy ./gradlew :common:test \
    :paper:1.21-paper:compileJava :paper:26.2-paper:compileJava \
    :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava \
    :mods:26.1.2-fabric:compileJava :mods:1.21.11-forge:compileJava \
    :mods:26.1.2-neoforge:compileJava --console=plain
```

**PASS**: Gradle succeeded in 17 seconds; 734 common JUnit tests passed with zero failures/errors/skips. Log: `/tmp/world-marker-promotion-platforms.log` (temporary local artifact).

| Target | Evidence |
| --- | --- |
| Paper 1.21 | Compilation passed; legacy teleport-packet branch compiled. |
| Paper 26.2 | Compilation passed; current entity-type branch compiled. |
| Fabric 1.20.1 | Compilation passed; generated adapter leaves `teleport_duration` inactive and uses the legacy packet constructor. |
| Fabric 1.20.2 | Compilation passed; generated adapter enables `teleport_duration=1` and uses the legacy packet constructor. |
| Fabric 26.1.2 | Compilation passed against the active unobfuscated target. |
| Forge 1.21.11 | Compilation passed; generated adapter uses the current teleport factory and enables duration. |
| NeoForge 26.1.2 | Compilation passed; generated adapter uses the current teleport factory and enables duration. |

The generated mod adapters under each target's `build/generated/stonecutter/main/java/` were inspected for these interpolation/packet boundaries. Neither active project changed: `mods` remains `26.1.2-fabric`; `paper` remains `26.2-paper`. Alternate-version source branches were preserved.

Gradle reported deprecations; Forge compilation emitted unchecked/deprecated API warnings (68 warnings). These checks reported no source compilation errors. They do not establish startup, gameplay, rendering or native behavior on those targets. [Current native evidence](2026-11-12-promotion-native.md) covers Paper 1.21.11 only; Folia and modded runtime acceptance remain pending.
