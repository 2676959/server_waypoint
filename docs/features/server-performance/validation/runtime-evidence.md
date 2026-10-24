# M0 runtime evidence

Date: 2026-10-24. Runtime claims below are scoped to Paper 1.21.11 build 111.

## Immutable inputs

- Release target: `d9f532f8b5a14c9be4bc47531b05b44fd1dc5773`; all 44 original names/digests copied into schema 1 release manifest.
- Audited Paper asset: `server_waypoint-4.0.0-paper-mc1.21.11-26.1.2.jar`, SHA-256 `0a1d2ddc07dff13dee1af78c8e1f3b0a50024f5d3b8686f733a512db0fa8ad57`; staged bytes rehashed successfully.
- Server: `/Volumes/ssd/minecraft_servers/paper/1.21.11/paper-1.21.11-111.jar`, SHA-256 `82a487ab8c2d0f6f072b8dbaedc9218c85ae3a7d167808124462b248955beec3`. Read-only source; existing worlds/plugins are never used.
- Java: `/opt/homebrew/Cellar/openjdk@21/21.0.11/libexec/openjdk.jdk/Contents/Home/bin/java`, executable SHA-256 `04005388bac0c272ea914210ca519ce94b2f873ea3962b9874a6859f74d7f279`, OpenJDK 21.0.11 ARM64.
- Querz candidate: `com.github.Querz:NBT:6.1`; binary resolution/hash is Task 4. [Pinned source](https://github.com/Querz/NBT/tree/6.1).
- Azalea candidate: `0.15.1+mc1.21.11`; native actions remain unverified and block bot selections. No Rust default was changed.

## Inspected Paper API

Source archive: Gradle cached `paper-api:1.21.11-R0.1-SNAPSHOT` at hash directory `a3a811fd1e99bce749c4e6db9991e45294438d00`.

- `ServerTickStartEvent.getTickNumber()`.
- `ServerTickEndEvent.getTickDuration()` reports native processing duration in milliseconds; callback interval is not used.
- `StructureManager.loadStructure(File)`.
- `Structure.place(Location, boolean, StructureRotation, Mirror, int, float, Random)`.

## Initial doctor

45 runtime requirement rows account for 44 assets plus a separate Folia anchor. The inventory expands 10,800 cases. These are requirements, not completed coverage.

Paper zero-player doctor initially exits 2: fixture, tick-processing, zero-player-ticking and probe are unverified. Other runtime Java/server/probe mappings remain explicit blockers. Full selections also require verified native bot/action/wire capabilities.
