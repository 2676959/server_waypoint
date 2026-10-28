# Xaero reconciliation implementation plan

**Spec:** [Selected repair](../specs/2026-10-27-xaero-reconciliation-design.md).
**Execution:** Inline, using test-driven development. No commit or push requested.

- [x] Add regressions for ready-before/after handshakes, repeated readiness signals,
  preference cancellation, target independence, and stale readiness after disconnect.
- [x] Observe focused failures in the existing implementation.
- [x] Generalize pending state by upload target, separate Xaero preferences from
  readiness, and remove direct writes from its readiness callback.
- [x] Reset Xaero readiness at connection boundaries and update the affected comment.
- [x] Run focused tests and full representative suites; review the diff and record
  results and live-test limits in the
  [validation record](../validation/2026-10-27-xaero-reconciliation.md).
