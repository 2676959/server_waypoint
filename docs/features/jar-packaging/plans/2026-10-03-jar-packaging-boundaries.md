# Jar Packaging Boundaries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every release jar ships only the classes and resources its platform uses, and the release gate proves it.

**Architecture:** A release-jar checker lands first and records the baseline. A new `cross-server` Gradle module then takes the code backends and the proxy share. `proxy-common` and Velocity stop depending on `common`, and the mods-only helpers move into `mods`. Small packaging rules cover credits, Maven metadata, bStats and the chat-sprite table. Each packaging task is driven by a checker rule that fails before the change and passes after it.

**Tech Stack:** Gradle Kotlin DSL, Stonecutter 0.9, Shadow 9, Java 17/21/25, Python 3 (standard library only), Bash 3.2.

**Spec:** [2026-10-03-jar-packaging-boundaries-design.md](../specs/2026-10-03-jar-packaging-boundaries-design.md). Its Appendix A is the authoritative move list.

## Global Constraints

- **Language and style:** Java 17 language features, Kotlin DSL, 4-space indentation, no tabs, GSON for JSON (AGENTS.md).
- **No compatibility code:** add no backward-compatibility code. No public API, configuration or file format changes.
- **The new module:** Gradle path `:cross-server`, directory `cross-server/`, Java 17 toolchain.
- **Package names:** moved files keep their packages, except the four mods helpers in Task 6.
- **bStats target:** relocate `org.bstats` to `${project.group}.internal.bstats`, which is `_959.server_waypoint.internal.bstats`.
- **Credits:** `CREDITS.txt`, renamed `SERVER_WAYPOINT_CREDITS.txt`, goes only into Jar tasks of `:mods:*` and `:paper:*` version projects.
- **Chat-sprite gate:** the Stonecutter predicate is `<1.21.9`.
- **No commits:** do not commit (AGENTS.md). Each task ends with a checkpoint instead.
- **Concurrent sessions:** other sessions may edit, switch branches or commit in this checkout. Run `git branch --show-current` and `git status --short` at the start of each task and before reporting. Never revert files you did not touch; name them in the report.
- **Shell quirks:** the rtk hook rewrites `grep`/`find`. Use `/usr/bin/grep` for `-o`/`-h`, use literal paths instead of shell variables, and put pipelines in a scratch `.zsh` file run with `zsh <file>`.
- **Gradle wrapper:** run Gradle only through this wrapper, saved as `gw.zsh` in your scratch directory. Its log goes to a file: `zsh <scratch>/gw.zsh <tasks> > <scratch>/<name>.log 2>&1`. `--tests X` binds only to the task just before it. Never pass `--offline`, because it breaks the Forge and NeoForge targets.

```zsh
export GRADLE_USER_HOME=/Volumes/ssd/gradle_home
cd /Volumes/ssd/fabric_mods_repo/server_waypoint || exit 1
( while sleep 5; do
    ssd=$(df -k /Volumes/ssd | awk 'NR==2 {print $4}'); sys=$(df -k /System/Volumes/Data | awk 'NR==2 {print $4}')
    if (( ssd < 1572864 || sys < 2097152 )); then
      print -u2 "disk watchdog: stopping Gradle"; pkill -f org.gradle.wrapper.GradleWrapperMain; ./gradlew --stop; exit 1
    fi
  done ) &
watchdog=$!
./gradlew "$@" --console=plain -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
gradle_exit=$?
kill $watchdog 2>/dev/null
exit $gradle_exit
```

- **Staging jars:** stage release jars with `./move_builds.sh <scratch>/<name>-jars`, never into the repository's `builds/` folder, which holds older releases.
- **EULA:** never create `eula.txt` in a run directory without the user's permission. The NeoForge and Forge run directories have none.
- **Live boots:** run them only in scratch directories. Treat `/Volumes/ssd/minecraft_servers/velocity` as a read-only source of the Velocity jar and `velocity.toml`, and never copy its `forwarding.secret`.

## Review Focus

1. **Unshaded `cross-server`.** A loader route that doesn't shade `cross-server` produces a jar that only fails once an admin enables cross-server. Task 3 stages all 42 jars and requires zero `missing-class` violations.
2. **Split packages in dev runs.** Forge and NeoForge dev runs reject packages split between `common` and `cross-server` unless both are in the mod group. Task 3 smoke-runs `runServer` on both.
3. **Single-target builds.** Building one target alone under `org.gradle.configureondemand=true` must still configure `:cross-server` before its source set is read. Task 3 assembles the NeoForge and Forge targets in separate invocations.
4. **Relocated bStats.** It must pass its own relocation self-check, and Velocity must still inject `Metrics.Factory`. Task 5 boots Velocity, and Task 8 boots Paper.
5. **Moved tests.** Moved tests must keep running, with none lost or duplicated. Tasks 3, 5 and 6 compare test-result XML counts before and after each move.

---

### Task 1: Release jar checker

**Files:**
- Create: `tools/check_release_jar.py`
- Modify: `tools/test_release_artifacts.py`: add `minimal_class` and `CheckReleaseJarTest`

**Interfaces:**
- Produces:
  - `class_references(data: bytes) -> set[str]` returns the internal names under `_959/server_waypoint/` that one class file references. It raises `ValueError` on bad magic, an unknown constant-pool tag or truncated data.
  - `check_jar(path: Path, loader: str, minecraft_version: str) -> list[str]` returns violations formatted as `"<rule>: <detail>"`, or `[]` when the jar is clean.
    - `loader` is one of `fabric`, `forge`, `neoforge`, `paper` or `velocity`; anything else raises `ValueError`.
    - `minecraft_version` is the Stonecutter version, such as `1.21.11`, or `-` for Velocity.
  - Rules: `missing-class`, `unreadable-class`, `forbidden-entry`, `missing-entry`.
  - CLI: `python3 tools/check_release_jar.py <jar> <loader> <minecraft-version>` prints `<jar file name>: <violation>` lines to stderr and exits 1. It exits 0 silently when the jar is clean, and 2 on bad arguments.

- [ ] **Step 1: Write the failing tests** in `tools/test_release_artifacts.py`

  - **Import:** add `sys.path.insert(0, str(ROOT / "tools"))` and `import check_release_jar`.
  - **Class builder:** add `minimal_class(name: str, references=(), descriptors=()) -> bytes`. It builds a Java 17 class file (major 61) with access `0x21`. The constant pool holds Class entries for `name` (this class) and `java/lang/Object` (super), one Class entry per `references` item and one Utf8 entry per `descriptors` item. It has no fields, methods or attributes.
  - **Test case:** add `CheckReleaseJarTest` with `violations(entries: dict[str, bytes], loader, version) -> list[str]`, which writes a temporary zip.
  - **Base dicts:**
    - `BACKEND` = `lang/en_us.json`, `SERVER_WAYPOINT_CREDITS.txt`, `assets/server_waypoint/chat-sprites.json`.
    - `VELOCITY` = `velocity-plugin.json`.
  - **Class paths:** `A` and `B` below are `minimal_class` entries at `_959/server_waypoint/A.class` and `_959/server_waypoint/B.class`.

| Test | Jar contents | Loader, version | Expected |
| --- | --- | --- | --- |
| `test_clean_jars_pass` | `BACKEND` (Fabric) or `VELOCITY` + `A` referencing `B`, `B` | `fabric 1.21.11`; `velocity -` | `[]` |
| `test_missing_internal_class` | `BACKEND` + `A` referencing `_959/server_waypoint/crossserver/transport/TcpChannel` | `fabric 1.21.11` | exactly one item, starting `missing-class: _959/server_waypoint/crossserver/transport/TcpChannel` |
| `test_descriptor_and_annotation_references` | `VELOCITY` + `A` with descriptors `(L_959/server_waypoint/core/waypoint/WaypointPos;)V` and `L_959/server_waypoint/config/NavigationMethodSetJsonAdapter;` | `velocity -` | two `missing-class` items naming those classes |
| `test_multi_release_entry_provides_class` | `VELOCITY` + `A`→`B`, with `B` at `META-INF/versions/17/_959/server_waypoint/B.class` | `velocity -` | `[]` |
| `test_unreadable_class` | `VELOCITY` + `_959/server_waypoint/Broken.class` = `b"fixture"` | `velocity -` | one item starting `unreadable-class: _959/server_waypoint/Broken.class` |
| `test_velocity_rejects_backend_content` | subTests: `VELOCITY` + one of `lang/en_us.json`, `assets/server_waypoint/chat-sprites.json`, `SERVER_WAYPOINT_CREDITS.txt`, `META-INF/maven/org.signal.forks/noise-java/pom.xml`, or a `minimal_class` at `_959/server_waypoint/` + `command/CoreWaypointCommand`, `config/Config`, `navigation/NavigationService`, `text/chat/Chat`, `translation/AdventureTranslator`, `core/WaypointServerCore` | `velocity -` | contains `forbidden-entry: <that entry>` |
| `test_velocity_requires_plugin_descriptor` | empty jar | `velocity -` | contains `missing-entry: velocity-plugin.json` |
| `test_backends_reject_proxy_classes` | subTests over loaders `fabric`, `forge`, `neoforge`, `paper` × `BACKEND` + a `minimal_class` at `_959/server_waypoint/proxy/ProxyPlayerRouter`, `.../crossserver/transport/TcpCoordinator`, `.../TcpCoordinator$1`, `.../CoordinatorTransport`; `.../crossserver/transport/TcpChannel` is the control | `1.21.11` | `forbidden-entry: <entry>`; the control gives `[]` |
| `test_backends_require_translations_and_credits` | `BACKEND` minus `lang/en_us.json`, or minus `SERVER_WAYPOINT_CREDITS.txt` | `paper 1.21.11` | contains `missing-entry: <removed entry>` |
| `test_chat_sprites_follow_version` | `paper 1.21` with the table; `fabric 1.20.1` without the table plus `minimal_class` `_959/server_waypoint/text/chat/VanillaChatSprites`; `neoforge 1.21.9` and `forge 26.1.2` without the table; `fabric 1.21.6` without the table | as listed | `forbidden-entry` for the table and for the class; `missing-entry: assets/server_waypoint/chat-sprites.json` twice; `[]` for 1.21.6 |
| `test_maven_metadata_rejected` | `BACKEND` + `META-INF/maven/x/pom.xml` | `fabric 1.21.11` | contains `forbidden-entry: META-INF/maven/x/pom.xml` |

- [ ] **Step 2: Run the tests and confirm they fail**

  Run: `python3 tools/test_release_artifacts.py CheckReleaseJarTest`
  Expected: an error, `ModuleNotFoundError: No module named 'check_release_jar'`.

- [ ] **Step 3: Implement `tools/check_release_jar.py`**

  Parse the constant pool exactly like this:

  ```
  u4 magic 0xCAFEBABE, u2 minor, u2 major, u2 count; entries 1..count-1
  tag 1 Utf8: u2 length + bytes     tags 7 Class, 8 String, 16 MethodType, 19 Module, 20 Package: 2 bytes
  tags 3 Integer, 4 Float: 4 bytes  tags 9, 10, 11 refs, 12 NameAndType, 17 Dynamic, 18 InvokeDynamic: 4 bytes
  tags 5 Long, 6 Double: 8 bytes and two slots   tag 15 MethodHandle: 3 bytes
  any other tag, or data ending early: ValueError
  ```

  - **References:** each Class entry's name, with leading `[` stripped and any `L…;` unwrapped, plus every match of `rb"L(_959/server_waypoint/[^;<>]+)"` in any Utf8 entry. Keep only names starting with `_959/server_waypoint/`.
  - **Provided classes:** every `.class` entry name without the `.class` suffix and without any `META-INF/versions/<n>/` prefix. Don't open nested `.jar` entries.
  - **Missing classes:** report each missing class once, as `missing-class: <name> (referenced by <entry>)`.
  - **Every jar:** forbid entries under `META-INF/maven/`.
  - **Velocity:**
    - Forbid prefixes `lang/` and `assets/`, plus `_959/server_waypoint/` followed by `command/`, `config/`, `navigation/`, `text/` or `translation/`.
    - Forbid the entries `SERVER_WAYPOINT_CREDITS.txt` and `_959/server_waypoint/core/WaypointServerCore.class`.
    - Require `velocity-plugin.json`.
  - **Other loaders:**
    - Forbid `_959/server_waypoint/proxy/` and entries matching `^_959/server_waypoint/crossserver/transport/(TcpCoordinator|CoordinatorTransport)(\$[^/]*)?\.class$`.
    - Require `lang/en_us.json` and `SERVER_WAYPOINT_CREDITS.txt`.
    - When `tuple(map(int, version.split("."))) < (1, 21, 9)`, forbid `assets/server_waypoint/chat-sprites.json` and `_959/server_waypoint/text/chat/VanillaChatSprites.class`; otherwise require the JSON.

- [ ] **Step 4: Run the tests and confirm they pass**

  Run: `python3 tools/test_release_artifacts.py CheckReleaseJarTest`
  Expected: `OK`. The older `ReleaseArtifactsTest` still fails on the 41/42 count; Task 2 fixes that.

- [ ] **Step 5: Checkpoint.** `git status --short` lists `tools/check_release_jar.py` and `tools/test_release_artifacts.py` as changes from this task.

### Task 2: Release gate wiring and baseline

**Files:**
- Modify: `tools/verify-release-artifacts.sh`
- Modify: `tools/test_release_artifacts.py`: `ReleaseArtifactsTest`
- Create: `docs/features/jar-packaging/validation/2026-10-03-jar-packaging-boundaries.md`. Delete `validation/.gitkeep`.

**Interfaces:**
- Consumes: the Task 1 CLI.
- Produces: `bash tools/verify-release-artifacts.sh <dir>` runs the checker on every jar. On success it prints `Verified 42 release JARs for Server Waypoint 4.0.0: Fabric=13 Forge=12 NeoForge=12 Paper=4 Velocity=1.`

- [ ] **Step 1: Update the `ReleaseArtifactsTest` fixtures**
  - Every jar gets `minimal_class("_959/server_waypoint/internal/noisekk/protocol/HandshakeState")` and `META-INF/LICENSE-noise-java`.
  - Backend jars also get `lang/en_us.json` and `SERVER_WAYPOINT_CREDITS.txt`. From 1.21.9 they also get `assets/server_waypoint/chat-sprites.json`; the version is the target folder name before its last `-`.
  - The Velocity jar also gets `velocity-plugin.json`.
  - Expect `Verified 42` and `Expected 42` instead of 41.
  - Write the probe entries in `test_development_probes` and `test_unrelocated_crypto` as `minimal_class` bytes.
  - Add `test_content_rule_fails_gate`: adding `lang/en_us.json` to the Velocity fixture makes the gate fail with `forbidden-entry: lang/en_us.json`.

- [ ] **Step 2: Run the tests and confirm they fail**

  Run: `python3 tools/test_release_artifacts.py`
  Expected: failures, including `Expected 41 release JARs, found 42`.

- [ ] **Step 3: Wire the gate in `tools/verify-release-artifacts.sh`**
  - Set `EXPECTED_TOTAL=42` and `EXPECTED_PAPER=4`.
  - While building `EXPECTED_NAMES`, also append `"$name|$loader|${target%-*}"` to an `EXPECTED_TARGETS` array, plus `server_waypoint-$MOD_VERSION-velocity.jar|velocity|-`. Bash 3.2 has no associative arrays.
  - After the existing per-artifact Noise checks, look up the jar's loader and version and run `python3 "$SCRIPT_DIRECTORY/check_release_jar.py" "$artifact" "$loader" "$version"`. If it exits non-zero, print `Release content check failed: <artifact>` to stderr and exit 1.

- [ ] **Step 4: Run the tests and confirm they pass**

  Run: `python3 tools/test_release_artifacts.py`
  Expected: `OK`.

- [ ] **Step 5: Record the baseline before any product change**
  1. Build: `zsh <scratch>/gw.zsh build --continue --max-workers=2`. Expected: `BUILD SUCCESSFUL`.
  2. Stage the jars with `./move_builds.sh <scratch>/baseline-jars`.
  3. Run the checker on each staged jar with a scratch loop over the same targets the gate derives.
  4. Expected: zero `missing-class` and zero `unreadable-class`. Velocity reports forbidden `lang/`, `assets/`, credits, command, config, navigation, text and translation classes, `WaypointServerCore` and `META-INF/maven/`. Backends report `TcpCoordinator`, `CoordinatorTransport` and `META-INF/maven/`; targets below 1.21.9 also report the chat-sprite entries.
  5. A `missing-class` is either a checker bug or an existing packaging bug. If one appears, stop and report it.
  6. Write the validation file's **Baseline** section: the HEAD commit hash, each jar's size in bytes, and a table of violations by rule.
  7. Keep the build's test-result XML counts for `common`, `proxy-common` and `velocity`: count the files in each `build/test-results/test/`.

- [ ] **Step 6: Checkpoint.**

### Task 3: The `cross-server` module

**Files:**
- Modify: `settings.gradle.kts`: add `include("cross-server")` before `include("common")`.
- Create: `cross-server/build.gradle.kts`.
- Move with `git mv`: the spec's Appendix A main files and 8 tests, from `common/src/{main,test}/java/...` to `cross-server/src/{main,test}/java/...`, at the same relative paths.
- Modify: `cross-server/src/main/java/_959/server_waypoint/core/waypoint/WaypointIconPolicy.java`
- Modify: `common/build.gradle.kts`, `gradle/noise-packaging.gradle.kts` (comment), `paper/build.gradle.kts`, `velocity/build.gradle.kts`, `mods/fabric.gradle.kts`, `mods/fabric-unobfuscated.gradle.kts`, `mods/neoforge.gradle.kts`, `mods/neogradle.gradle.kts`, `mods/forge.gradle.kts`
- Modify: `tools/noise-platform-test/audit.py`, `AGENTS.md`

**Interfaces:**
- Produces:
  - `:cross-server` (`java-library`, Java 17 toolchain) with these dependencies:
    - `api`: `com.google.code.gson:gson:2.10.1`, `io.netty:netty-buffer:4.1.+`, `org.slf4j:slf4j-api:1.7.30`, `org.jetbrains:annotations:26.0.2`
    - `implementation`: `org.signal.forks:noise-java:${property("noise_version")}`
    - test: JUnit 5.10.2 on the JUnit Platform
  - The `updateModInfo` task moves verbatim from `common`, and `compileJava` depends on it.
  - `common` declares `api(project(":cross-server"))` and no longer declares Noise or `updateModInfo`.
  - In the `neoforge` and `forge` scripts, `crossServerMainSourceSet` exists beside `commonMainSourceSet`.

- [ ] **Step 1: Create the module and move the files**
  1. Write `cross-server/build.gradle.kts` as described above.
  2. In `common/build.gradle.kts`, remove the `updateModInfo` task, its `compileJava` dependency, the Noise dependency and the now-unused `Properties`/`FileInputStream` imports. Add `api(project(":cross-server"))`.
  3. Move the 46 main files and 8 tests with `git mv`.

- [ ] **Step 2: Give `WaypointIconPolicy` its own logger**

  Replace `import static _959.server_waypoint.core.WaypointServerCore.LOGGER;` with `private static final Logger LOGGER = LoggerFactory.getLogger("server_waypoint_core");`, using the SLF4J imports.

- [ ] **Step 3: Run the module tests and compare counts**

  Run: `zsh <scratch>/gw.zsh :cross-server:test :common:test :proxy-common:test :velocity:test`
  Expected:
  - `BUILD SUCCESSFUL`.
  - `cross-server/build/test-results/test` holds 8 XML files.
  - `common`'s count is its Task 2 baseline minus 8.
  - The `proxy-common` and `velocity` counts are unchanged.

- [ ] **Step 4: Shade `cross-server` wherever `common` is shaded**
  - **Paper:** add `&& it.moduleName != "cross-server"` to the shadow `exclude { … }` filter.
  - **Velocity and the five mods scripts:** add `include(project(":cross-server"))` right after `include(project(":common"))`.
  - **`neoforge` and `forge`:** after the `commonMainSourceSet` block, add `evaluationDependsOn(":cross-server")` and `crossServerMainSourceSet`, built the same way. Add it to the mod group: `sourceSet(crossServerMainSourceSet.get())` in NeoForge's `mods { register(mod_id) }`, and `source(crossServerMainSourceSet.get())` in Forge's `runs { configureEach { mods { create(mod_id) } } }`.

- [ ] **Step 5: Update the related text**
  - **`gradle/noise-packaging.gradle.kts`:** the comment now says `cross-server` owns the runtime dependency.
  - **`audit.py`:** expect `'paper': 4`, and loop over `['cross-server','common','proxy-common']` for the shared-module bytecode check.
  - **AGENTS.md "Project Structure":** list `cross-server` (the cross-server protocol, transport, catalog models and credentials shared by backends and the proxy; no waypoint core, Minecraft or Adventure types), `proxy-common` (proxy-side coordination; depends on `cross-server` only) and `velocity` (packages `cross-server` and `proxy-common` only), and say that `common` depends on `cross-server`.
  - **AGENTS.md "Helper Scope":** add: "Code both backends and the proxy need belongs in `cross-server`; backend-only code stays in `common`, proxy-only code in `proxy-common`."

- [ ] **Step 6: Check every jar's internal references** (Review Focus 1)

  Run: `zsh <scratch>/gw.zsh build --continue --max-workers=2`, which includes every target's tests, then stage with `./move_builds.sh <scratch>/task3-jars` and run the checker loop.
  Expected: `BUILD SUCCESSFUL`, zero `missing-class` violations, and the same content violations as the baseline.

- [ ] **Step 7: Configure single targets on demand** (Review Focus 3)

  Run `zsh <scratch>/gw.zsh :mods:1.21.11-neoforge:assemble`, then run `zsh <scratch>/gw.zsh :mods:1.21.11-forge:assemble` as a separate invocation.
  Expected: each prints `BUILD SUCCESSFUL`.

- [ ] **Step 8: Smoke-run the NeoForge and Forge dev servers** (Review Focus 2)

  Run `:mods:1.21.11-neoforge:runServer` and `:mods:1.21.11-forge:runServer` through `gw.zsh` with stdin from `/dev/null`.
  Expected:
  - Mod discovery lists `server_waypoint`.
  - No `ResolutionException`, `split package`, `NoClassDefFoundError` or `IllegalAccessError`.
  - Without `eula.txt`, the run ends at the EULA prompt. If that happens before the mods are constructed, report it and ask the user whether to accept the EULA in that run directory.

- [ ] **Step 9: Checkpoint.**

### Task 4: Credits, Maven metadata and bStats

**Files:**
- Modify: `build.gradle.kts`, `gradle/noise-packaging.gradle.kts`, `paper/build.gradle.kts`, `velocity/build.gradle.kts`

- [ ] **Step 1: Confirm the failing rules**

  Run the checker on `<scratch>/task3-jars`'s Velocity jar.
  Expected: it includes `forbidden-entry: SERVER_WAYPOINT_CREDITS.txt`, `forbidden-entry: META-INF/maven/…` and `forbidden-entry: _959/server_waypoint/config/MetricsConfig.class`.

- [ ] **Step 2: Make the three packaging changes**
  - **Credits:** in the root build, replace `subprojects { tasks.withType<Jar>()… }` with `configure(listOf(project(":mods"), project(":paper")).flatMap { it.subprojects }) { … }`, keeping the same body. Comment: "Translator credits ship only in the jars that ship translations."
  - **Maven metadata:** in `noise-packaging.gradle.kts`'s `shadowJar` block, add `exclude("META-INF/maven/**")`.
  - **bStats:** in Paper and Velocity, use `relocate("org.bstats", "${project.group}.internal.bstats")`.

- [ ] **Step 3: Rebuild and re-check**

  Run: `zsh <scratch>/gw.zsh :velocity:assemble :paper:1.21.11-paper:assemble :mods:26.1.2-fabric:assemble :common:jar :proxy-common:jar :cross-server:jar`
  Expected:
  - The Velocity checker no longer reports credits, Maven metadata or `config/MetricsConfig.class`. Its other `common` violations remain until Task 5.
  - Neither the Paper nor the Fabric jar reports Maven metadata.
  - The Paper jar has `_959/server_waypoint/internal/bstats/bukkit/Metrics.class`, and the Velocity jar has `_959/server_waypoint/internal/bstats/velocity/Metrics.class`. Neither has `_959/server_waypoint/MetricsBase.class`.
  - Only the Paper and Fabric jars contain `SERVER_WAYPOINT_CREDITS.txt`.

- [ ] **Step 4: Checkpoint.**

### Task 5: The proxy boundary

**Files:**
- Modify: `proxy-common/build.gradle.kts`, `velocity/build.gradle.kts`, `tools/noise-platform-test/audit.py`
- Move with `git mv`: `common/src/main/java/_959/server_waypoint/crossserver/transport/{TcpCoordinator,CoordinatorTransport}.java` and `common/src/test/java/_959/server_waypoint/crossserver/transport/TcpTransportTest.java`, to the same relative paths under `proxy-common/src/`.
- Modify: `docs/architecture/README.md`, `docs/architecture/server-waypoint.architecture.json`, `docs/architecture/server-waypoint-architecture.html` (regenerated), `docs/features/cross-server/specs/cross-server-proxy-module-contracts.md`

- [ ] **Step 1: Confirm the failing rules**

  Expected:
  - The Velocity jar from Task 4 still reports `forbidden-entry: lang/en_us.json`.
  - The Fabric 26.1.2 jar reports `forbidden-entry: _959/server_waypoint/crossserver/transport/TcpCoordinator.class`.

- [ ] **Step 2: Cut Velocity and `proxy-common` loose from `common`**
  - **`proxy-common`:** `api(project(":cross-server"))` replaces `api(project(":common"))`. Add `testImplementation(project(":common"))` and `testImplementation("org.signal.forks:noise-java:${property("noise_version")}")`, because `TcpTransportTest` uses Noise directly.
  - **Moves:** move the three files with `git mv`.
  - **Velocity shadow:** remove `include(project(":common"))`.

- [ ] **Step 3: Run the tests and compare counts** (Review Focus 5)

  Run: `zsh <scratch>/gw.zsh :cross-server:test :common:test :proxy-common:test :velocity:build`
  Expected:
  - `BUILD SUCCESSFUL`.
  - `common` has one fewer test-result XML file, and `proxy-common` one more.
  - The checker returns `[]` for the Velocity jar.
  - Rebuild `:mods:1.21.11-forge:assemble` and `:mods:1.21.11-neoforge:assemble`; their checker output has no `TcpCoordinator` or `CoordinatorTransport` violations.
  - Record the Velocity jar's size.

- [ ] **Step 4: Boot Velocity** (Review Focus 4)
  1. **Set up:** in `<scratch>/velocity-boot`, copy `velocity-4.1.0-SNAPSHOT-21.jar` and `velocity.toml` from `/Volumes/ssd/minecraft_servers/velocity`. Set `bind = "127.0.0.1:25598"`, after checking that `lsof -nP -iTCP:25598 -sTCP:LISTEN` shows nothing. Put the new jar in `plugins/`. If Velocity refuses to start without `forwarding.secret`, write a fresh random one; never copy the original.
  2. **First boot:** `(sleep 20; echo end) | /opt/homebrew/opt/openjdk@25/bin/java -jar velocity-4.1.0-SNAPSHOT-21.jar > boot1.log 2>&1`. This creates `plugins/server_waypoint/cross-server.json`.
  3. **Enable the coordinator:** set the top-level `"enabled": true` and `"listen": "127.0.0.1:25591"`. Leave the sample backend disabled.
  4. **Second boot:** pipe in `(sleep 15; echo "serverwaypoint status"; sleep 3; echo end)` and log to `boot2.log`.
  5. **Expected:**
     - The log has `Server Waypoint coordinator startup: SUCCESS`, `Server Waypoint cross-server: running`, `Transport mode: NOISE_KK (encrypted)`, `Coordinator listening on port 25591` and `Online servers (0): none`.
     - `plugins/server_waypoint/cross-server-public-key.txt` and `plugins/bStats/` exist.
     - No exception mentions `_959`, `bstats` or `NoClassDefFoundError`.

- [ ] **Step 5: Update the audit and the docs**
  - **`audit.py`:** assert `BackendTransport.class` only when `loader != 'velocity'`. For Velocity, assert that `_959/server_waypoint/crossserver/transport/TcpChannel.class` is present and `BackendTransport.class` is absent.
  - **Architecture README:**
    - Add a `cross-server` row to the module table and update the `common`, `proxy-common` and `velocity` rows to match spec §1.
    - Point the `CrossServerProtocol.java` link to `../../cross-server/src/main/java/...`.
    - Add "Module boundaries updated on 2026-10-03." after the first line.
  - **Architecture JSON:**
    - Add component `cross_server`, typed `backend`, with label `cross-server`, sublabel `Protocol / transport / catalog models`, and tag `JAVA 17 SHARED LIBRARY`.
    - Lay out the module row at x = 50, 330, 610 and 890 with width 240 and y = 760, ordered common, cross_server, proxy_common, build. Add `cross_server` to the module boundary's `wraps`.
    - Set `proxy_common`'s tag to `DEPENDS ON CROSS-SERVER`.
    - Rewrite the packaging card line to say: "mods and paper depend on common, which depends on cross-server. proxy-common depends on cross-server only; velocity packages cross-server and proxy-common, never common."
    - Change the subtitle date to `2026-10-03`.
  - **Regenerate the diagram** with `node /Users/mini/.codex/skills/archify/bin/archify.mjs render architecture …`, then `validate … --json` and `check …`, using the commands in the architecture README. Expected: both checks report no errors.
  - **Proxy module contracts spec:** the ownership table moves `TransportLifecycle` to `cross-server` and `CoordinatorTransport` to `proxy-common`.

- [ ] **Step 6: Checkpoint.**

### Task 6: Mods-only helpers

**Files:**

| Move (`git mv`) from `common/src/…/_959/server_waypoint/` | To `mods/src/…/_959/server_waypoint/` | New package |
| --- | --- | --- |
| `main/java/…/core/network/buffer/XaerosWorldIdBuffer.java` | `main/java/…/common/network/XaerosWorldIdBuffer.java` | `_959.server_waypoint.common.network` |
| `main/java/…/core/network/codec/XaerosWorldIdBufferCodec.java` | `main/java/…/common/network/XaerosWorldIdBufferCodec.java` | `_959.server_waypoint.common.network` |
| `main/java/…/core/network/WaypointRevisionSequence.java` | `main/java/…/common/client/WaypointRevisionSequence.java` | `_959.server_waypoint.common.client` |
| `main/java/…/util/MathUtils.java` | `main/java/…/common/client/util/MathUtils.java` | `_959.server_waypoint.common.client.util` |
| `test/java/…/core/network/WaypointRevisionSequenceTest.java` | `test/java/…/common/client/WaypointRevisionSequenceTest.java` | `_959.server_waypoint.common.client` |

- Modify these imports:
  - `mods/…/mixin/PlayerManagerMixin.java` and `mods/…/common/network/payload/s2c/XaerosWorldIdS2CPayload.java` import the new `common.network` classes.
  - `mods/…/common/client/gui/widgets/IntegerField.java` imports `common.client.util.MathUtils`.
  - In `ClientSynchronizationTracker`, the moved buffer and the moved codec, remove imports that are now same-package.
  - `MessagePayloadMapping` is already in `common.network`, so it needs no import.

- [ ] **Step 1: Confirm the starting point.** `unzip -l` of the Paper 1.21.11 jar lists all four classes at their `common` paths.
- [ ] **Step 2: Make the moves and edits.**
- [ ] **Step 3: Verify**
  1. Run `zsh <scratch>/gw.zsh :common:test`. Expected: one fewer test-result XML file.
  2. Run `zsh <scratch>/gw.zsh assemble --continue --max-workers=2`. Expected: `BUILD SUCCESSFUL`.
  3. Run `:mods:26.1.2-fabric:test --tests '*WaypointRevisionSequenceTest'` and `:mods:1.20.1-fabric:test --tests '*WaypointRevisionSequenceTest'`. Expected: both pass.
  4. Stage the jars and run the checker loop. Expected: zero `missing-class`.
  5. Expected: no Paper jar lists the four classes, and every mod jar lists them at the new paths.
- [ ] **Step 4: Checkpoint.**

### Task 7: Chat-sprite version gate

**Files:**
- Modify: `mods/fabric.gradle.kts`, `mods/neoforge.gradle.kts`, `mods/forge.gradle.kts`, `mods/neogradle.gradle.kts`, `paper/build.gradle.kts`

- [ ] **Step 1: Confirm the failing rule.** On the Task 6 jars for `1.21-paper` and `1.20.1-fabric`, the checker reports `forbidden-entry: assets/server_waypoint/chat-sprites.json` and `forbidden-entry: _959/server_waypoint/text/chat/VanillaChatSprites.class`.
- [ ] **Step 2: Add the exclusion**
  - **Predicate scripts:** add this block after the shadow configuration in `fabric`, `neoforge` and Paper. In `forge`, put the `exclude` inside `shadowJarTask.configure { }` instead of `tasks.shadowJar { }`.

    ```kotlin
    // Sprite objects in chat exist from 1.21.9; older targets never read the sprite table.
    if (stonecutter.eval(stonecutter.current.version, "<1.21.9")) {
        tasks.shadowJar {
            exclude("assets/server_waypoint/chat-sprites.json", "_959/server_waypoint/text/chat/VanillaChatSprites.class")
        }
    }
    ```

  - **`neogradle`:** builds only 1.20.2, so add the same `exclude` unconditionally inside its `tasks.shadowJar { }`, with the same comment.
- [ ] **Step 3: Rebuild and run the gate**
  1. Run `zsh <scratch>/gw.zsh assemble --continue --max-workers=2`.
  2. Stage with `./move_builds.sh <scratch>/task7-jars`.
  3. Run `bash tools/verify-release-artifacts.sh <scratch>/task7-jars`.
  Expected: `Verified 42 release JARs for Server Waypoint 4.0.0: Fabric=13 Forge=12 NeoForge=12 Paper=4 Velocity=1.`
- [ ] **Step 4: Checkpoint.**

### Task 8: Final verification and records

**Files:**
- Modify: `docs/features/jar-packaging/validation/2026-10-03-jar-packaging-boundaries.md` and `docs/features/jar-packaging/README.md`, setting its status to implemented and linking the plan and the validation record.

- [ ] **Step 1: Check the tree.** Run `git branch --show-current` and `git status --short`. Every change must be explainable by this plan; name any foreign files.
- [ ] **Step 2: Run the full build and the gate**
  1. Run `zsh <scratch>/gw.zsh build --continue --max-workers=2`. Expected: `BUILD SUCCESSFUL`.
  2. Stage with `./move_builds.sh <scratch>/final-jars`.
  3. Run `bash tools/verify-release-artifacts.sh <scratch>/final-jars`. Expected: `Verified 42 release JARs…`.
  4. Run `python3 tools/test_release_artifacts.py`. Expected: `OK`.
- [ ] **Step 3: Run the Noise audit**

  Run: `python3 tools/noise-platform-test/audit.py --java17-home /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home --output <scratch>/noise-audit`
  Expected: `Verified 42 artifacts; shared modules contain Java 17 bytecode and no platform API references.`
- [ ] **Step 4: Boot Paper** (Review Focus 4)
  1. Back up `paper/versions/1.21.11-paper/run/plugins/ServerWaypoint` and `ops.json`.
  2. Run `:paper:1.21.11-paper:runServer` with a feed that sends `stop` after `Done`.
  3. Expected: Server Waypoint enables, and there are no bStats relocation errors or exceptions mentioning `_959`.
  4. Restore the backups.
- [ ] **Step 5: Write the records**
  - **Validation file:** a before-and-after size table for all 42 jars with totals; the gate, gate-test and audit output; the Velocity, Paper, NeoForge and Forge boot results; and the test-count deltas.
  - **Feature README:** set its status and links.
- [ ] **Step 6: Final checkpoint.** Run `git status --short` again, rerun any check that predates a foreign change, and report.
