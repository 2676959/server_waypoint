# Terrain history validation

Both backend regression tests failed before the fix when returning from a second map region to an explored first region without any intervening terrain update. They pass after adding persistent region snapshots.

The shared store tests cover returning to previous regions, blank new regions, reopening the store, isolation by map ID/dimension/center/scale, unchanged live regions and replacing snapshots after further exploration. Paper tests use native saved map data and its renderer. Mod tests also apply a native map packet to stale client map data to verify that a new region replaces the cached pixels.

Validation commands:

```sh
./gradlew :common:test :mods:26.1.2-fabric:test :paper:26.2-paper:test --offline --console=plain
./gradlew :mods:1.20.1-fabric:test :mods:26.3-fabric:compileJava :mods:26.3-neoforge:compileJava :mods:1.20.1-forge:compileJava :paper:1.21-paper:test :paper:1.21.9-paper:test :paper:1.21.11-paper:test --console=plain
```

Both commands passed. The active Stonecutter targets remain Fabric 26.1.2 and Paper 26.2. Existing dependency/deprecation warnings remain. In-game rendering and player movement were not tested, and neither live server restart nor Folia scheduling was exercised.

Earlier discarded terrain has no snapshot to restore and still requires re-exploration. The current map's pixels and all regions recorded by this implementation are retained.
