# Client loading validation — 2026-10-07

Corrected the NeoForge payload registration/client-send API boundary, stopped language discovery
from closing loader-owned filesystems, and updated the incompatible installed JECharacters mod.

## Tests and builds

- Filesystem regression: original code failed `leavesTheLoaderOwnedJarFileSystemOpenAndReadable`; fixed code passed all 3 language-discovery tests.
- Complete suites: common 645, cross-server 115, proxy-common 162, NeoForge 1.21.6 607 (4 skipped), NeoForge 1.21.7 607 (4 skipped), NeoForge 1.21.9 609 (10 skipped), Fabric 1.21.9 665, Fabric 26.2 670. Zero failures/errors in the final runs.
- Initial new-target compile exposed the removed `PacketDistributor.sendToServer` method. After updating both `NetworkHelper` gates to >=1.21.7, the final command passed:

```sh
rtk proxy ./gradlew :mods:1.21.7-neoforge:build :mods:1.21.6-neoforge:build :mods:1.21.9-neoforge:test :mods:1.21.9-fabric:build :mods:26.2-fabric:build --console=plain
```

- The preceding command also ran `:common:test :cross-server:test :proxy-common:test` successfully.
- Copy preflight selected exactly one artifact for every supported profile: 47 selected, 3 skipped. Only the four affected profiles received corrected installed JARs.
- Generated source/JAR metadata, balanced Stonecutter scopes, and `git diff --check` passed. Independent read-only review found no actionable issues.

## Installed startup reruns

| Profile | Loader | Result |
|---|---|---|
| 1.21.6-NeoForge | 21.6.20-beta | passed-loading |
| 1.21.7-NeoForge | 21.7.25-beta | passed-loading |
| 1.21.10-Fabric_0.17.3 | Fabric 0.17.3 | passed-loading; JECharacters 4.6.4 and DynamicCrosshair 9.10 loaded |
| 26.2-Fabric | Fabric 0.19.3 | passed-loading |

Each real graphics client used a 1024M heap and reached a title screen without a loading overlay
for at least 15 seconds, verified by read-only runtime screen probes. No closed Server Waypoint
filesystem warning appears in either fresh Fabric run. Native Java windows could not be controlled
by the UI tool. All tracked test JVMs exited. Original JARs were backed up before replacement.

[Full report, hashes and evidence](/Users/mini/.codex/visualizations/2026/10/07/01a114d3-5994-7843-8ec9-271d82d431f8/client-loading-fixes/report.md).

Gameplay, packet exchange, server integration, NeoForge 1.21.8 startup, and the other 43 profiles
remain unverified by this rerun. The original report/evidence is unchanged. Changes remain
uncommitted; nothing was pushed and no active Stonecutter target was switched.
