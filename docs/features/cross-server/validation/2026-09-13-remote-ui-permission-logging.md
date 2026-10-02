# Remote UI, destination permission preflight and coordinator logging

Validated on 2026-09-13. Changes remain uncommitted.

## Behavior

- Remote rows share local row colors, initials badges, outlines, expand/collapse icons and 20-pixel height.
- Teleport sends on the first click after the session and exact cached target are checked.
- Destination permission lookup completes before `HandoffPrepared`; failures, denial, expiry and close
  prevent preparation. Arrival still checks the current live permission and authoritative target.
- Dedicated backend/proxy logger categories record handoff activity and connection history. See
  [the administrator guide](../cross-server-admin.md#teleport-coordinator-logs).

## Automated validation

The final Gradle invocation passed:

```sh
rtk ./gradlew -I tools/cross-server-gui-test/probe.gradle.kts :mods:26.1.2-fabric:stageRemoteGuiProbe :mods:26.1.2-fabric:test :common:test :proxy-common:test :paper:26.2-paper:test :paper:1.21-paper:test :mods:1.20.1-fabric:compileJava :mods:1.21.2-neoforge:compileJava :mods:26.1.2-forge:compileJava --console=plain
```

| Suite | Tests | Failures/errors/skips |
| --- | ---: | --- |
| Common | 543 | 0 |
| Proxy common | 116 | 0 |
| Fabric 26.1.2 | 213 | 0 |
| Paper 26.2 | 8 | 0 |
| Paper 1.21 | 8 | 0 |

Destination rejection produces zero server switches in both NOISE_KK and PLAINTEXT socket tests.
Regression cases cover delayed permission responses, expiry, disconnect, provider failure, successful
arrival completion and permission revocation on arrival. Paper API tests cover deferred loading,
explicit denial overriding operator status, non-operator grants and undefined-node fallback.
Fabric 1.20.1, NeoForge 1.21.2, Forge 26.1.2 and both Paper targets compiled successfully.
Active Stonecutter projects were not switched. `git diff --check` passed.

## Native GUI validation

HeadlessMC 2.10.0 ran Fabric Loader 0.19.5, Minecraft 26.1.2, Fabric API 0.148.2+26.1.2,
Fabric Permissions API 0.7.0 and Java 25 in an isolated offline game directory. The final run passed
at 18:40:52 local time with a successful HeadlessMC command test and exit code 0. The probe exercised
actual screen input, immediate command submission, row selection, resize, shared sort/group controls,
stale-cache transitions, session invalidation and preservation of local waypoint files.

Final production JAR SHA-256: `4ded40058b81412df99b0c6900456d26d01d30003f1c3fff4c1df22bcbfe1e2d`.

Scratch log: `/private/tmp/server-waypoint-remote-update/native-final.log` (not a tracked artifact).
The fixture uses an unavailable remote coordinator, so it verifies command submission rather than
native player transfer. Rendering is headless with graphics stubs; screenshot review was not performed.
No live LuckPerms/Paper deployment or full supported-version runtime matrix was exercised. Paper
preflight uses LuckPerms static server context when installed, otherwise native operator status;
other plugins' dynamic player attachments are only observable on arrival. See
[provider behavior](../specs/cross-server-authorization.md#permission-check-before-transfer).
