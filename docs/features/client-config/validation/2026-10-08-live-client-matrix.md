# Xaero default-set live client matrix

All 40 supported Stonecutter version/loader targets were exercised against the current changes.
The earlier four profiles passed 74 editor steps. The remaining 36 profiles ran muted and
completed 627 steps: 33 editor suites and three core suites. Across both runs, there were
701 successful steps, 37 editor-suite passes and three core-suite passes, with clean exits.

**Xaero feature coverage is unavailable on Fabric 1.21.2, NeoForge 1.21.2 and Forge 1.21.6.**
Their core suites verified startup, available mixins, integrated-world synchronization and
server Edit/Add form defaults and cancellation. They did not exercise Xaero behavior.

| Target | Final suite | Result | Steps |
| --- | --- | --- | --- |
| 1.20.1-fabric | editor | PASS | 20 |
| 1.20.2-fabric | editor | PASS | 20 |
| 1.20.4-fabric | editor | PASS | 20 |
| 1.20.6-fabric | editor | PASS | 20 |
| 1.21-fabric | editor | PASS | 20 |
| 1.21.2-fabric | core | PASS | 3 |
| 1.21.3-fabric | editor | PASS | 20 |
| 1.21.5-fabric | editor | PASS | 20 |
| 1.21.6-fabric | editor | PASS | 20 |
| 1.21.9-fabric | editor | PASS | 20 |
| 1.21.11-fabric | editor | PASS | 20 |
| 26.1.2-fabric | editor | PASS | 20 |
| 26.2-fabric | editor | PASS | 20 |
| 26.3-fabric | editor | PASS | 20 |
| 1.20.1-forge | editor | PASS | 17 |
| 1.20.2-forge | editor | PASS | 17 |
| 1.20.4-forge | editor | PASS | 17 |
| 1.20.6-forge | editor | PASS | 17 |
| 1.21-forge | editor | PASS | 17 |
| 1.21.3-forge | editor | PASS | 17 |
| 1.21.5-forge | editor | PASS | 17 |
| 1.21.6-forge | core | PASS | 3 |
| 1.21.9-forge | editor | PASS | 17 |
| 1.21.11-forge | editor | PASS | 20 |
| 26.1.2-forge | editor | PASS | 20 |
| 26.2-forge | editor | PASS | 20 |
| 1.20.2-neoforge | editor | PASS | 17 |
| 1.20.4-neoforge | editor | PASS | 17 |
| 1.20.6-neoforge | editor | PASS | 17 |
| 1.21-neoforge | editor | PASS | 17 |
| 1.21.2-neoforge | core | PASS | 3 |
| 1.21.3-neoforge | editor | PASS | 20 |
| 1.21.5-neoforge | editor | PASS | 17 |
| 1.21.6-neoforge | editor | PASS | 17 |
| 1.21.7-neoforge | editor | PASS | 17 |
| 1.21.9-neoforge | editor | PASS | 17 |
| 1.21.11-neoforge | editor | PASS | 20 |
| 26.1.2-neoforge | editor | PASS | 20 |
| 26.2-neoforge | editor | PASS | 20 |
| 26.3-neoforge | editor | PASS | 20 |

## Editor assertions

- Production Mixin audits and native map handlers.
- Both settings-toggle modes and native Sync now confirmation clicks.
- Direct/default set replacement, separate-set routing and old owned-set cleanup.
- Ambiguous full-list rejection without destroying the existing default contents.
- Server highlighting versus personal duplicates, decoded names and upload collection.
- Minimap and World Map server Edit routing, owning dimensions, editor save/cancel and stale targets.
- Incremental add/rename/remove, mode transitions and server-list deletion preserving later personal additions.
- World close/reload, client-config reload, native Xaero set persistence and independent JSON/native-file checks.
- VoxelMap editor/share/fallback checks only on targets whose repository properties pin VoxelMap.

## Fixture preparation and failures

The available-client batch passed 27 editor suites. Forge 1.21.6 reached startup but its
editor audit failed with `ClassNotFoundException: xaero.common.gui.GuiWaypoints`: Forge
ignored the repository-pinned NeoForge Xaero JARs. This failed attempt is retained. Its
subsequent core-only run passed; that does not resolve or certify map integration.

Modrinth version API queries returned zero matching Minimap and World Map releases for
Fabric/NeoForge 1.21.2 and Forge 1.21.6. Those queries were saved as evidence, so core
profiles were used only for verified unavailable optional map releases.

Seven missing client profiles were installed into an isolated test store using the
repository's loader pins. Existing profile mod directories and settings were preserved.
All 36 new final runs used a 1,024 MB heap cap, at most two simultaneous game clients, and
master volume zero. Saved options independently confirmed the mute setting on every new
final profile. The active Stonecutter project remained `26.1.2-fabric`.

The first missing-profile build preparation hit an upstream NeoForge Maven HTTP 502.
A fresh retry succeeded; both logs are retained. No production source fixes were made.

## Evidence and reproduction

Evidence is under the ignored `build/live-game-test/remaining-20261008/` directory:

- `report.md`, `remaining-summary.json`, `full-matrix-summary.json` and `all-attempts.json`.
- `results-available/`: 27 editor passes and the preserved Forge 1.21.6 map-audit failure.
- `results-final-nine/`: six editor passes and three core passes.
- Per-profile step results, Mixin audits, game logs, isolated worlds, saved config and native map stores.
- `prepared-available/`, `prepared-missing-retry/`, `prepared-core-fabric/`, `prepared-core-forge/`.
- `prepared-final-nine/` combines the seven missing-profile preparations and the two additional core preparations, preserving scenario/target hashes and `origin_preparation` metadata.
- `xaero-1.21.2-metadata.json` and `xaero-forge-1.21.6-metadata.json` retain the public API query URLs and zero-match counts.
- `installer/` retains isolated profile installation/export logs. No authentication tokens are used by these offline clients.

The available editor run selected every profile from `prepared-available` except Fabric
1.21.2, which required a separate core preparation. Run commands used the tracked tool:

```sh
python3 - <<'PYRUN'
import json
import subprocess
from pathlib import Path
root = Path("build/live-game-test/remaining-20261008")
rows = json.loads((root / "prepared-available/prepared.json").read_text())["profiles"]
args = ["python3", "tools/live-game-test/run.py", "run",
        "--prepared", str(root / "prepared-available"),
        "--output", str(root / "results-available"), "--jobs", "2", "--heap", "1024"]
for row in rows:
    if row["target"] != "1.21.2-fabric":
        args.extend(["--profile", row["name"]])
raise SystemExit(subprocess.call(args))
PYRUN
python3 tools/live-game-test/run.py run \
    --prepared build/live-game-test/remaining-20261008/prepared-final-nine \
    --output build/live-game-test/remaining-20261008/results-final-nine \
    --jobs 2 --heap 1024
```

Use fresh output directories for another run. The runner intentionally refuses existing
result directories. Preparation/manifest inputs, runtime JAR hashes, Java paths, probe hashes
and original preparation paths are retained alongside the results.

## Limits

This validates the recorded Minecraft versions and pinned mod sets. Native GUI-handler
assertions do not certify rendered appearance, operating-system mouse input, other map-mod
releases, or remote multiplayer transfers and complete `/wp upload` command/network round trips.
The three core targets remain untested for the new Xaero feature.
