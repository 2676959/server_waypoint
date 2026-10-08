# Live Minecraft verification

Run the map editor and persistence checks from the October 2026 mixin review against freshly built production JARs. The runner launches real Minecraft clients, creates isolated singleplayer worlds, operates native GUI handlers on the game thread, checks command responses and save/reload behavior, and validates the saved JSON independently.

The test agent is external to the mod. It does not register bytecode transformers or ship inside production JARs.

## Requirements

- Python 3.9 or newer, a POSIX desktop with a working Minecraft display, and JDK 25 or newer for compiling probes. The probe source uses Java 17 features.
- Installed client profiles with the correct Minecraft version, loader, Java runtime and native libraries. The launch exports must use absolute paths and contain one literal Java invocation, such as HMCL's exported launch scripts.
- This repository's Gradle dependencies and production build toolchain.

The tool does not install Minecraft or loader profiles. Each manifest names an existing launch export. Exports are parsed as data and never executed as shell scripts. Keep account-bearing exports private; the runner substitutes an offline test identity and removes account tokens and identifiers.

## Run

From the repository root:

```sh
python3 tools/live-game-test/run.py manifest \
    --exports /absolute/path/to/launch-exports \
    --fabric-api 1.21.1-Fabric=/absolute/path/to/compatible-fabric-api.jar \
    --output build/live-game-test/manifest.json

python3 tools/live-game-test/run.py prepare \
    --manifest build/live-game-test/manifest.json \
    --jdk /absolute/path/to/jdk-25 \
    --output build/live-game-test/prepared

python3 tools/live-game-test/run.py run \
    --prepared build/live-game-test/prepared \
    --output build/live-game-test/results \
    --jobs 2
```

Use new output directories for each preparation and run. Existing results are never reused. `prepare` assembles each selected Stonecutter target, obtains its compile classpath and runtime map dependencies from Gradle, compiles and remaps fresh probes, and hashes the resulting artifacts. It leaves the active Stonecutter target unchanged.

The default matrix in `top-downloads.json` contains the top five Server Waypoint download-ranked Minecraft versions for each of Fabric, Forge and NeoForge from the **2026-10-06 analytics snapshot**. It is a historical preset, not a live ranking query. Launch export filenames match profile names, for example `1.21.5-Fabric.sh`. Repeat `--exports` to search multiple directories. Repeat `--profile NAME` on any subcommand to select profiles.

For another matrix, pass `manifest --matrix /path/to/matrix.json`. Its format is:

```json
[
    {"name": "1.21.5-Fabric", "target": "1.21.5-fabric", "minecraft": "1.21.5"}
]
```

The `target` selects a repository build. `minecraft` identifies the actual client runtime: for example Minecraft 1.21.1 uses the 1.21 build. The runner checks the actual game version after startup.

When the runtime differs from its build target, its Fabric API may need a different version. Declare it when creating the manifest, for example `--fabric-api 1.21.1-Fabric=/absolute/path/fabric-api-0.116.8+1.21.1.jar`. Preparation verifies the JAR's mod ID and hash and replaces only that profile's Fabric API. All other dependencies retain the repository pins. This is required for the preset's 1.21.1 Fabric profile because the 1.21 build's API excludes 1.21.1.

Exports with a launcher Java helper require explicit `manifest --allow-launcher-agent /absolute/path/helper.jar`. Its hash is bound into the private manifest and checked again before launch. Other external agents, remote server and Quick Play launches are rejected.

## Checks and evidence

`run --suite editor` is the default. It verifies:

- Production mixin target loading, Mixin environment audit, applied map handlers, native Xaero set identity and the injected access interface. Absent `@Pseudo` targets are recorded as optional.
- Integrated world startup and client synchronization; full fixture metadata in two dimensions.
- Minimap Add/Edit routing, VoxelMap popup Edit/Add actions, native outgoing Xaero and VoxelMap share flows, confirmation choices, shared form defaults, and encoded server identity duplicate suppression.
- World Map edit/save, owning dimension selection, native marker ownership, add/cancel/save, and stale target feedback.
- VoxelMap edit/save, fallback handling and explicit metadata clearing on targets that include VoxelMap. Other targets skip those operations.
- Default-list direct sync: native settings toggle and Sync now confirmation, separate/direct set routing,
  ambiguous-name rejection without destructive replacement, personal-waypoint preservation, server highlighting,
  native Minimap/World Map Edit routing, decoded upload collection, incremental add/rename/remove, mode
  transitions and server-list deletion. Reload checks include the saved setting and personal sets.
- Save response completion, returning to the original map screen, world close/reload and persisted JSON contents.
  The default-set scenario also validates the client config, native Xaero store and deleted server list independently.

For a target with no matching optional map release, declare `"suite": "core"` on its manifest profile. Preparation omits optional map JARs and uses the independent `java/CoreChecks.java.template`; the run audits available mixins, creates a synchronized integrated world and checks server Edit/Add draft defaults and cancellation. Missing map targets are recorded explicitly. Core and editor profiles for the same target require separate preparations. This suite cannot certify map actions or sharing. Do not use it to bypass failures when matching map releases exist.

`run --suite audit` checks startup and mixins only. Each client normally quits through Minecraft. Timeouts, failures and interruption stop only process groups started by this invocation. `--jobs` accepts 1–4, defaults to 1, and a lock prevents concurrent invocations against the same preparation. `--heap` accepts 512, 1024 or 2048 MB. Startup and step timeouts are configurable.

The result directory contains `report.md`, `summary.json`, per-profile `result.json`, step results, attach logs, game logs, mixin exports, test worlds, a `default_set_disk` result, and a copied `persisted-waypoints` store. Preparation retains build, compile, remap and extractor regression logs. A failure exits 1, a complete pass exits 0, and interruption exits 130. Failed attempts remain available for diagnosis.

These checks invoke actual game-thread GUI handlers and assert runtime state. They do not certify screen appearance, operating-system mouse input, every gameplay branch, or multiplayer server transfers. Isolated test clients start with master sound volume set to zero; source-profile options remain unchanged. Minecraft still renders with LWJGL; `java.awt.headless=true` prevents AWT from taking the native event loop.

## Maintain

Shared assertions live in `java/LiveChecks.java.template`. `scenarios.py` generates the narrow Minecraft API adapter and excludes VoxelMap steps for targets without that dependency. Add an operation to the template and the ordered steps in `runner.py`; preserve independent disk assertions in `persistence.py`. Update the matrix explicitly when refreshing download rankings.

```sh
python3 -m unittest discover -s tools/live-game-test -p 'test_*.py'
```

Preparation also runs the Java target extractor regression fixture. A new version needs a successful fresh preparation and a real live run; compiling its helper alone is insufficient.
