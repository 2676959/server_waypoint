# VoxelMap reconciliation implementation plan

> Execute inline with test-driven development and verification before completion.

**Goal:** Restore VoxelMap managed markers after unchanged handshakes without
writing before its current world is loaded.

**Architecture:** A mods-only synchronization coordinator coalesces deferred
VoxelMap events. A client-only accessor identifies VoxelMap's loaded world.

**Tech stack:** Java 17, Stonecutter, Mixin, JUnit 5.

**Spec:** [Selected design](../specs/2026-10-27-voxelmap-reconciliation-design.md).

## Constraints and review focus

Preserve integrated entry and Xaero behavior, auto-sync preferences, manual sync
semantics, optional-mod loading, and the active Stonecutter targets. Discard
pending work on server changes. Reconcile the latest model once, not saved event
payloads. Commit only when the user requests it; no pushes are authorized.

## Task 1: Reconcile and defer automatic VoxelMap synchronization

- [x] Add failing coordinator tests for unchanged-handshake restoration, delayed
  readiness, coalescing, disabled/uninstalled maps, integrated entry, cancellation,
  and unchanged Xaero behavior.
- [x] Run the focused Fabric tests and confirm the missing behavior fails.
- [x] Add `MapModSyncCoordinator` under client integrations. Route automatic and
  unchanged events through it; drain pending work on the existing client tick and
  clear it on join/leave.
- [x] Add failing readiness tests; implement a world accessor, register it only
  in the VoxelMap mixin config, and use it in the readiness predicate.
- [x] Run focused tests, then the Fabric suite and representative older Fabric,
  NeoForge and Forge verification; inspect the diff and optional-mod boundaries.
- [x] Record actual verification and remaining live-test boundaries in validation.
