# Verified current-release scope

Verified on 2026-10-24 through GitHub release metadata using `gh release view`.
This is scope/provenance evidence only; no new performance scenarios have run.

- Repository: `2676959/server_waypoint`.
- Latest published release: [Server Waypoint 4.0.0](https://github.com/2676959/server_waypoint/releases/tag/4.0.0).
- Published at: `2026-10-15T23:34:11Z`.
- Release target: `d9f532f8b5a14c9be4bc47531b05b44fd1dc5773`.
- Published JAR assets: 44 (13 Fabric, 13 Forge, 13 NeoForge, 4 Paper, 1 Velocity).
- Current development HEAD: `20d4b86fc0bf9e0ef9ad93df1d9fa4c6466853f6` on
  `feature/private-waypoint`, also identifying itself as 4.0.0.

## Published Paper/Velocity hashes

| Artifact | Published SHA-256 |
| --- | --- |
| `server_waypoint-4.0.0-paper-mc1.21-1.21.8.jar` | `0bd15bf981d3d91250b575e3d812860ccf2ccc5a5f8d47e733435787d0423161` |
| `server_waypoint-4.0.0-paper-mc1.21.9-1.21.10.jar` | `cb8425f89a2f8e8fd7a23049bab50197bfe414ee59a1bf19bf14f09257602ecb` |
| `server_waypoint-4.0.0-paper-mc1.21.11-26.1.2.jar` | `0a1d2ddc07dff13dee1af78c8e1f3b0a50024f5d3b8686f733a512db0fa8ad57` |
| `server_waypoint-4.0.0-paper-mc26.2-26.3.jar` | `691bca27c90ec7b42db23ed79d8cca2a47b1028f515da7e8a33655c73601a87f` |
| `server_waypoint-4.0.0-velocity.jar` | `69e12d7cfbd0560c94e3bec1ccef78ce102b0a84b84b81121e06fd93d3ecc4cf` |

The Paper 1.21.11 asset hash matches the artifact identity recorded in the referenced
TPS investigation. Verify bytes again when preparing an audit run. Other local files
with a 4.0.0 filename are not presumed to match the published release.

The complete [44-asset manifest](2026-10-24-release-assets.json) records published hashes,
sizes, and download URLs. Before execution, verify downloaded/copied bytes and attach
the exact server/loader/JVM runtime anchors. No runtime compatibility or performance
conclusions follow from these metadata checks alone.
