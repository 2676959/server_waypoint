# Remote server selector validation

## Automated checks

Passed without switching the active Stonecutter targets:

```sh
rtk ./gradlew :common:test :proxy-common:test :mods:26.1.2-fabric:test :mods:26.3-fabric:compileJava :mods:26.3-neoforge:compileJava :mods:1.20.1-fabric:compileJava :paper:26.2-paper:compileJava --console=plain
```

901 tests passed: common 557, proxy-common 116, Fabric 26.1.2 228.
Coverage includes constrained opposing rail allocation, exact remote server/dimension scoping,
item identifier validation, metadata round-trips, and icon-only updates at an unchanged catalog
revision over the real coordinator distribution channel. The existing transport, handoff and
client catalog suites also passed. Existing Xaero deprecation and JOML Unsafe warnings remain.

After restoring both protocol constants to 1, the focused check
`rtk ./gradlew :common:test :proxy-common:test :mods:26.1.2-fabric:test --console=plain`
passed again: 557 common, 116 proxy-common, and 228 Fabric tests, with no failures.

## Native GUI probe

The updated `tools/cross-server-gui-test/RemoteGuiProbe.java` passed on Fabric 26.1.2,
Fabric Loader 0.19.5, Fabric API 0.148.2+26.1.2, and Java 25 through HeadlessMC 2.10.0.
The isolated game and launcher directories were under `/tmp/server-selector-native`.

The probe populated 13 remote servers and used native screen mouse dispatch to change servers,
verified that the dimension catalog changes with the selected server, scrolled the server rail
without changing dimension selection, checked the bottom anchor above the controls and the gap
between rails, checked hidden/restored add-button state, resized to 320x240 and 960x540, and
exercised filtering, sorting, stale catalog and session-reset teleport guards.

The game emitted `REMOTE_GUI_PROBE PASS`, wrote `remote-gui-result.txt` with `PASS`, and the
HeadlessMC command test completed successfully with a clean launcher exit.

The native probe ran before the protocol constants were restored to 1. Its production JAR SHA-256
was `196d741ac5d4bd88e652fd8986bda6854be6e9cb5ad4e0700f509c4bd11a3e77`.

This is native interaction validation with stubbed graphics, not screenshot approval. It does not
exercise a live cross-server GUI transfer. Both wire formats now report protocol 1 while carrying
the new icon field, so matched client/backend/coordinator builds are required. The native probe
above does not validate the restored version-1 artifact.
