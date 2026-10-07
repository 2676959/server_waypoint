# Reusable runner validation — 2026-10-07

Branch: `4.0.0`. Active Stonecutter project remained `26.1.2-fabric`.

The extracted CLI is documented in [the runbook](../../../../tools/live-game-test/README.md). Preparation assembled 12 production targets and compiled/remapped fresh external probes for 15 runtime profiles. The Java extractor regression passed for remapped slash-form target names and optional `@Pseudo` targets.

## Successful live attempts

All five Server Waypoint download-ranked versions per modded platform passed:

| Platform | Minecraft versions |
| --- | --- |
| Fabric | 26.2, 1.21.1, 1.21, 1.21.11, 1.21.5 |
| Forge | 1.20.1, 1.20, 26.2, 1.21.11, 26.1.2 |
| NeoForge | 1.21.1, 1.21, 26.2, 1.21.11, 1.21.5 |

The successful attempts include 15 runtime mixin audits, 240 editor steps, 15 independent persisted JSON validations, and 15 normal client exits with code 0. Five profiles omit the VoxelMap operations because their targets do not include VoxelMap. Actual game versions were checked against the manifest.

Evidence is retained locally under:

- `build/live-game-test/prepared-final/`: Gradle inputs, production artifact hashes, generated sources, compiled/remapped probes and preparation logs.
- `build/live-game-test/full-matrix/`: 14 successful editor profiles and the initial 1.21.1 Fabric dependency failure.
- `build/live-game-test/prepared-alias/` and `alias-live/`: the successful 1.21.1 Fabric rerun with an explicit compatible Fabric API override.
- `build/live-game-test/combined/`: the 15 successful attempts with their evidence paths; earlier failures remain available.
- `build/live-game-test/audit-smoke/`: successful audit-only mode on 1.21.1 Fabric, with normal client shutdown.
- `build/live-game-test/audit-final/`: successful audit-only run on 1.21.11 NeoForge using the final runner, including preparation provenance and probe hashes.

Initial extraction failures exposed target normalization, optional-target handling and asynchronous Xaero world readiness. The extracted code was corrected and freshly prepared before the successful matrix. The 1.21 build's Fabric API excludes runtime 1.21.1; the runner now accepts an explicit, mod-ID-validated, hash-bound per-profile API override. This rerun used `fabric-api-0.116.8+1.21.1`.

## Automated checks and boundaries

The tool's 27 Python regression tests passed, covering literal launch parsing, credential substitution, unsupported launches, artifact identity, independent disk assertions, output isolation, cancellation and owned-process cleanup. The repository's 33 existing Python tool tests also passed. Python syntax and whitespace checks passed.

The probes operate native GUI handlers on the game thread, exercise command/network responses, reopen the same saved world and inspect the persisted store independently. This verifies those runtime paths; it does not certify visual appearance, operating-system mouse input, every gameplay path or external server transfers. No probe is packaged into production JARs.
