# World Marker Promotion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Promote the existing server-side display entity marker into a regular 4.0.1 feature with repeatable acceptance evidence for its final layout, configurable settings and lifecycle.

**Architecture:** Keep the existing shared navigation lifecycle and independent Paper/modded adapters. Extend the candidate-only native smoke runner rather than adding product behavior: native snapshots establish entity configuration and geometry, while vanilla protocol bot observations independently establish owner-visible entity delivery. Documentation and release notes describe the existing method and distinguish each evidence layer.

**Tech Stack:** Java 17-compatible source, Kotlin DSL/Stonecutter Gradle builds, GSON, Python standard-library unittest, pinned Paper 1.21.11/Java 21 and existing Azalea protocol bots.

**Spec:** [Server-side world marker contract](../specs/2026-11-12-server-world-marker-design.md).

## Global Constraints

- Preserve the existing commands, defaults, session storage, entity layout, motion prediction and diagnostics. Add no backward-compatibility implementation.
- Size: default `1`, inclusive range `0.25`–`4`; projection distance: default `12`, inclusive range `1`–`32` blocks. Both accept fractional values.
- Two private text displays plus an optional item display; item, initials badge, distance row order; waypoint block-centre anchor `(x + 0.5, y + 0.5, z + 0.5)`.
- Keep `world_marker` independent of the mounted `text_display` HUD. Built-in navigation defaults remain `actionbar`.
- Never insert marker entities into the server world or load the destination chunk. Runtime tests use a new disposable localhost server; preserve installed servers and active runtime state.
- Keep Java source compatible with Java 17, indent by four spaces, use GSON for JSON, and use zero-based `MessageFormat` placeholders for any server translations. This plan does not require translation changes.
- Run exact version-qualified Gradle tasks; preserve the active `26.1.2-fabric` and `26.2-paper` projects and alternate Stonecutter branches.
- Commit this plan and its referenced contract as requested. Leave implementation, test and documentation changes uncommitted unless separately authorized; do not push or publish.
- Keep plan/spec/validation artifacts under `docs/features/world-marker/`; temporary logs may live outside the checkout. Continue in the existing feature branch and preserve unrelated work.

## Review Focus

1. A false native PASS with a missing/duplicated row, stale size, wrong item offset or respawned IDs: Task 1 must reject each with deliberately corrupted fixtures.
2. A correct scalar setting with the wrong anchor or projection: Task 1 must reject a wrong block-centre height, uncapped far target, projected near target, and non-finite coordinates.
3. Settings leaking to a second owner, or disappearing on disable/retarget: Task 1 must verify unchanged observer state and preservation through those transitions.
4. Saved settings surviving only in memory: Task 2 must disconnect/reconnect the same actors and inspect restored displays, then prove stop/new-session resets defaults.
5. Private access and version-specific interpolation mistaken for general acceptance: Tasks 2–4 must explicitly exercise Private access and separate compilation, native packets and rendered observations.

---

### Task 1: Add native size and projection acceptance

**Files:**
- Modify: `tools/server-performance/bots/world_marker_smoke.py`.
- Create: `tools/server-performance/tests/test_world_marker_smoke.py`.
- Modify: `docs/features/world-marker/README.md` and `validation/2026-11-12-formalization.md`.
- Create: `docs/features/world-marker/validation/2026-11-12-execution.md` (progress, commands and rulings).

**Interfaces:**
- Consumes: existing `navigation.snapshot` player records, including `target.position`, `worldMarker.sampleEye`, `observedVelocity` and each display's `part`, `entityId`, `position`, `translation`, `scale`, `inWorld`, `billboard` and `teleportDuration`.
- Produces: `assert_marker_configuration(player, size, radius, expected_ids=None) -> None`, raising `AssertionError` on invalid native geometry/style/identity; `world-marker.json.settings` records setting commands and before/after native/client observations.

- [x] **Step 1:** Add unit fixtures with literal geometry: eye `(0.5, 0.5, 0.5)`, waypoint block `(20, 0, 0)`, radius `8.5`, expected anchor `(9, 0.5, 0.5)`. Assert acceptance of correct rows at size `1.75`; assert rejection of wrong scale/offset/anchor, NaN/infinite coordinates, missing/duplicated parts, changed/duplicated IDs and world-inserted entities. Add a near-target fixture that remains at its block centre.
- [x] **Step 2:** Run `rtk proxy python3 -m unittest discover -s tools/server-performance/tests -p test_world_marker_smoke.py -v`. Expected: RED because the configuration oracle is absent. Distinguish missing behavior from fixture/import mistakes.
- [x] **Step 3:** Implement the oracle in the existing smoke script. Validate stationary samples before comparing projected positions; compare every row against independent block-centre projection and fixed row transforms scaled by `size`, with finite-number and identity checks. Do not derive expected settings from observed display values.
- [x] **Step 4:** Add a near waypoint fixture and native phases for `(size, distance)` values `(0.25, 1)`, `(4, 32)`, `(1.75, 8.5)` and reset `(1, 12)`. Check fractional reads/sets, stable IDs during changes, per-owner isolation, retarget preservation, close-target anchoring, and changing settings while the method is disabled followed by re-enable. Restore the original far target/default settings before the existing movement/HUD checks. Retain command receipts and native/client snapshots in `settings` evidence.
- [x] **Step 5:** Run the focused unit file, then `rtk proxy python3 -m unittest discover -s tools/server-performance/tests -v`. Expected: PASS; missing runtime dependencies must be reported rather than converted to product fixes. Record red/green counts and the final full-suite result. Update smoke scope documentation; leave changes uncommitted.

### Task 2: Refresh Paper native lifecycle and saved-setting evidence

**Files:**
- Modify: `tools/server-performance/bots/world_marker_smoke.py` and its unit tests if a new reusable oracle is needed.
- Create: `docs/features/world-marker/validation/2026-11-12-promotion-native.md` and a compact retained JSON receipt tied to actual candidate/server/probe/bot hashes.
- Modify: feature README, formalization validation and execution progress.

**Interfaces:**
- Consumes: Task 1's oracle, setting evidence and existing bot/probe APIs.
- Produces: fresh native receipt for the final row order/anchor, setting changes and lifecycle; explicit PASS/FAIL/incomplete boundaries for Private and reconnect cases.

- [x] **Step 1:** Extend the runner to disconnect/reconnect the same offline actor identities after saving size `1.75`/radius `8.5`; await native readiness and restored displays, check identity and settings with Task 1's oracle. Assert zero sessions/entities/motion tracks while disconnected. Stop navigation, create a new session, and verify size `1`/radius `12`.
- [x] **Step 2:** Exercise an owner-created Private target through `/wp private navigate` and verify a different player's denied access through resulting native state and client feedback. Preserve command receipts and both owners' snapshots. Use the existing Private command grammar and storage lifecycle, not direct edits to a live store. Add fail-closed oracle fixtures for any new acceptance decision.
- [x] **Step 3:** Run `rtk proxy ./gradlew :paper:1.21.11-paper:shadowJar :paper:1.21.11-paper:performanceProbeJar --console=plain`. Expected: successful production/probe artifacts; verify exact artifact filenames and runtime inputs before launch.
- [x] **Step 4:** Run `rtk proxy python3 tools/server-performance/bots/world_marker_smoke.py --root <new-absolute-disposable-root> --artifact <fresh-paper-jar> --count 2`. Expected: successful exit, `passed: true`, recorded setting/reconnect/Private phases, distinct owner IDs and cleanup at zero. Retain failed attempts as evidence. Fix harness failures before judging product behavior.
- [x] **Step 5:** Write the native receipt with hashes, actual exercised cases, logs and observed cleanup. Record all unevaluated conditions explicitly. This run proves native configuration/packets, not rendered appearance, capacity or full platform acceptance. Leave implementation changes uncommitted.

### Task 3: Validate platform/API boundaries

**Files:**
- Create: `docs/features/world-marker/validation/2026-11-12-platform-checks.md`.
- Modify Java/Stonecutter sources and existing tests only if a verified marker defect is exposed.

**Interfaces:**
- Consumes: unchanged shared marker contract and version matrix in `settings.gradle.kts`.
- Produces: per-target compile/test results with precise scope, plus regression fixes only where supported by a failing check.

- [x] **Step 1:** Run `rtk proxy ./gradlew :common:test :paper:1.21-paper:compileJava :paper:26.2-paper:compileJava :mods:1.20.1-fabric:compileJava :mods:1.20.2-fabric:compileJava :mods:26.1.2-fabric:compileJava :mods:1.21.11-forge:compileJava :mods:26.1.2-neoforge:compileJava --console=plain`. Expected: all selected tasks succeed. Record dependency/environment failures independently of source failures.
- [x] **Step 2:** Inspect generated boundary adapters for the 1.20.1 interpolation exception, `>=1.20.2` duration setting and legacy/current teleport packets. Verify balanced Stonecutter predicates if edits are required. Do not switch the active source projects.
- [x] **Step 3:** Record which platforms have only compilation evidence and which have native evidence. Full modded/Folia runtime acceptance remains pending until exercised with available fixtures; compilation is never substituted for it.

### Task 4: Rendered acceptance and final review

**Files:**
- Create: `docs/features/world-marker/validation/2026-11-12-rendered-acceptance.md` when a rendered session is actually exercised.
- Finalize pending `README.md`, `README_zh.md`, `docs/tips/server-guide.md`, both changelogs and feature docs.

**Interfaces:**
- Consumes: Tasks 1–3 evidence and existing final layout/defaults.
- Produces: an honest final acceptance status and reviewable feature promotion diff.

- [x] **Step 1:** Inventory available vanilla clients and applicable client-test tools before launching a disposable rendered session. If unavailable, record the exact missing prerequisite and keep visual acceptance pending. Do not change installed player worlds or claim screenshots from protocol bots.
- [ ] **Step 2:** In an available rendered session, inspect item/badge/distance order, near block-centre alignment, size/radius endpoints and fractional values, text-through-blocks versus normal item occlusion, motion corrections, Private targets and independent HUD toggles. Retain screenshots and exact client/server configuration for exercised cases.
- [x] **Step 3:** Reconcile the feature contract, player/admin guides and both changelogs with actual behavior and evidence. Preserve historical reports. Check relative documentation links and `rtk git diff --check`; report tests, native results and remaining acceptance separately.
- [x] **Step 4:** Review the complete diff against the contract and Review Focus; repair important verified defects with regression tests. Report the plan commit, implementation status, evidence paths, uncommitted files and any outstanding runtime/visual gates. Do not push, publish or commit implementation without further authorization.
