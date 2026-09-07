# Forge and NeoForge startup fixes

Verified on 2026-09-06 while validating cross-server step 3.

## Origin

Both failures reproduce with packaged artifacts from `feature/upload-3.1.0` at `32d4498`,
without Noise, proxy modules, or the classloader observation agent. They also reproduce on the
cross-server step-2 baseline `ca167a1`. They are inherited backend defects, not introduced by
step 3. The duplicate NeoForge registration dates to `cc96eb5`; the legacy Forge reobfuscation
route used only vanilla mappings before this fix.

## Changes

Forge targets before 1.20.6 merge the Mixin processor's generated member mappings into the
vanilla official-to-SRG mapping file before final reobfuscation. This renames shadow declarations
and their bytecode references, including cartography `container`/`resultContainer` and inventory
members. Mixin processor outputs belong only to `compileJava` and are declared for build caching.
The injection behavior and target descriptors are unchanged.

NeoForge 1.20.4 and newer register `message_chunk` once with both directions. A NeoForge-local
`MessageChunkPayload` is used on both sending paths, including in-memory connections. Serverbound
frames still reach the server packet handler; clientbound frames still reach the client chunk
handler on its owning thread. The 1.20.4 direction builder, 1.20.6–1.21.6 shared handler, and
1.21.9+ separate handlers follow their respective loader APIs. The channel ID, encoded bytes,
and protocol version are unchanged. Forge, Fabric, and NeoForge 1.20.2 retain their existing
payload types and registration routes.

## Runtime evidence

Fresh loopback-only servers on macOS arm64, with fresh worlds/configuration and no player data:

| Runtime | Before | After |
| --- | --- | --- |
| Forge 1.20.1 / 47.4.20, Java 17.0.19 | `@Shadow field container was not located` in `CartographyTableMenuNavigationMixin`; startup aborts | Reaches `Done`, accepts `stop`, exits 0 |
| NeoForge 26.2 / 26.2.0.3-beta, Java 25.0.3 | Duplicate `server_waypoint:message_chunk` registration; startup aborts | Reaches `Done`, accepts `stop`, exits 0 |

[Baseline results](validation/upload-startup-fixes/baseline-results.json) and
[fixed results](validation/upload-startup-fixes/fixed-results.json) include artifact/log SHA-256
values and links by filename to the retained console logs in the same directory. Retained text
normalizes trailing whitespace and tabs; raw and retained log hashes are recorded separately.
Disposable runtime root: `/private/tmp/server-waypoint-step3-runtime-oxmlwttn`.

## Automated verification

Targeted builds pass for Forge 1.20.1 and NeoForge 1.20.4, 1.21.6, and 26.2.
`MessageChunkPayloadTest` checks the server mapping's runtime type, both original directional wire
encodings, defensive byte ownership, codec round-trip, and rejection of trailing bytes.

`./gradlew build --max-workers=2 --console=plain` passed across the full configured matrix.
The new payload tests passed on all 11 affected NeoForge targets (22 cases, none skipped); see
[payload test results](validation/upload-startup-fixes/payload-tests.json). Final Forge 1.20.1
bytecode also contains the expected SRG shadow names (`f_39135_`, `f_39138_`, `f_35978_`,
`m_36056_`). These checks establish startup and payload
representation; an actual modded client upload exchange was not performed for this fix.
