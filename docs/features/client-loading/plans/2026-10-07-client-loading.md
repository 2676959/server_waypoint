# Client Loading Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Correct the reported startup failures and filesystem warning, then rerun affected clients.

**Architecture:** Keep separate Stonecutter targets across the NeoForge registration API boundary.
Language discovery uses its own JAR filesystem. The unrelated transformer failure is corrected
in the installed client dependency, with the old JAR backed up.

**Tech Stack:** Java 17-compatible source, Stonecutter, Gradle Kotlin DSL, JUnit 5, installed graphics clients.

**Spec:** ../specs/2026-10-07-client-loading-design.md

## Global Constraints

- Preserve Java 17 compatibility, unrelated files, loader branches, and active targets.
- No commits; retain original logs and back up replaced installed JARs.
- Real graphics startup with 1024M heap; title screen stable for 15 seconds.

## Review Focus

- Borrowed JAR filesystems must remain open and readable after language discovery.
- Languages still load from a fresh JAR and from separate development resources.
- NeoForge 1.21.6 must retain its existing registration API.
- The copy preflight must select exactly one artifact for NeoForge 1.21.6 and 1.21.7.
- The custom Fabric profile must still load JECharacters and DynamicCrosshair together.

### Task 1: Filesystem ownership

- [x] Add packaged-JAR tests to `common/src/test/java/_959/server_waypoint/translation/LanguageFilesManagerTest.java`, asserting translations load and a loader-owned filesystem stays readable.
- [x] Run `:common:test --tests '*LanguageFilesManagerTest'` and observe the ownership failure.
- [x] Change `LanguageFilesManager.getInternalLanguageFilesFromJar` to open its own path-based filesystem; remove the borrowed-filesystem helper.
- [x] Run the complete common test suite.

### Task 2: NeoForge registration boundary

- [x] Use the archived loading failure and API bytecode as the failing integration case.
- [x] Add `1.21.7-neoforge` to `settings.gradle.kts` and create its version properties with NeoForge 21.7.25-beta and range 1.21.7–1.21.8.
- [x] Restrict the existing NeoForge 1.21.6 artifact to 1.21.6; gate the four-argument registration at >=1.21.7.
- [x] Build/test both targets and build Fabric 1.21.10 and 26.2; verify generated source and JAR metadata.
- [x] Run the copy preflight, install only the affected corrected builds with backups, and rerun NeoForge 1.21.6 and 1.21.7.

### Task 3: Fabric dependency and readiness

- [x] Select a published Fabric 1.21.10 JECharacters build, verify its download hash, and back up the old client JAR before replacement.
- [x] Rerun the custom Fabric profile and Fabric 26.2, checking active screen state and absence of the closed-filesystem warning.
- [x] Record hashes, outcomes, evidence, and remaining limits in the feature validation record and a new local report.

## Execution notes

- The filesystem regression failed on the original code (1 failure in 3 tests) and all three tests passed after the path-based filesystem change. Full common suite: 645 tests, no failures/errors.
- The initial 1.21.7 build exposed the related client-send API migration: `PacketDistributor.sendToServer` was moved to `ClientPacketDistributor`. Cached NeoForge 21.7.25-beta sources confirmed the move; both `NetworkHelper` gates now begin at >=1.21.7. The second build passed.
- Cross-server, proxy-common, both affected NeoForge targets, NeoForge 1.21.9, and Fabric 1.21.9/26.2 suites passed; existing skipped tests are retained in the validation record.
- Copy preflight selected 47 profiles and skipped 3; only the four affected profiles received new JARs. Replaced JARs and JECharacters 4.5.20 were backed up before replacement.
- Independent read-only review found no actionable issues. Real startup passed on all four profiles after the Fabric clients returned to their title screens. All tracked JVMs exited.
- Original evidence remains unchanged. No commits or active-target switches were made.
