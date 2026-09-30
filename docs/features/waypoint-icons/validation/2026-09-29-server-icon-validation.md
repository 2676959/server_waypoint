# Server icon validation

New icon selections from commands and client edit packets now require a registered non-air server item or a supported VoxelMap image. Rejected selections leave waypoint data and list revisions unchanged. Stored and remote icon IDs keep their existing fallback behavior.

- Before the fix, the new command and serialized edit-packet regression tests both failed because unknown IDs were accepted.
- `rtk proxy ./gradlew :common:test :mods:26.3-neoforge:compileJava --console=plain` passed: 587 common tests, no failures, errors, or skips.
- Compilation passed for `:paper:1.21-paper:compileJava`, `:paper:26.2-paper:compileJava`, `:mods:1.20.1-fabric:compileJava`, and `:mods:26.1.2-fabric:compileJava` in the earlier combined run. That run exposed an incorrect waypoint name in the new test fixture; correcting the fixture produced the passing common suite above.
- `rtk git diff --check` passed. The active Stonecutter projects were unchanged.

The builds reported existing unchecked/deprecation warnings. No live Minecraft or Paper server test was run.
