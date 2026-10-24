# Server performance acceptance checklist

Status: required checks only. No runtime acceptance item has been executed by writing
the design, specification or implementation plan. Each checked item needs a run ID,
artifact/runtime hashes, command and retained evidence link in `progress.md`.

M0 evidence: [Paper reproduction and scoped validation](2026-10-24-m0-paper.md).
Only the specific known-reproduction item is checked below; full-matrix and
three-tier performance acceptance remain pending.

## Original release audit

- [ ] All 44 published 4.0.0 JAR bytes verified against the frozen manifest.
- [ ] Runtime anchors, native Azalea versions, probes and profiler capabilities verified;
      every unsupported/missing requirement is visible as blocked coverage.
- [ ] Querz/NBT fixtures validated by target runtime load/save/reopen and independent
      expected block/item/metadata state, including negative coordinates/boundaries.
- [ ] Zero-player idle and small/medium/stress physical hopper/container worlds prove
      zero clients, active ticking, sustained transfers and conservation throughout.
- [x] Known Paper synthetic inventory reproduction rerun with separate physical counters,
      correct allocation scope, control pairing and local diagnostic evidence.
- [ ] 1/10/50 Azalea connections and offered/completed actions verified server-side.
- [ ] Java/Rust golden vectors match the published release's actual codecs/protocol.
- [ ] Every release command leaf/suggestion, protection event/mixin, configuration branch
      and background task maps to executed cases or a source-backed applicability reason.
- [ ] All applicable server feature families executed small/medium on every required
      artifact/runtime anchor, including Folia region and actual Velocity A/B topologies.
- [ ] Stress and 30-minute mixed soaks executed for each distinct loader/API family,
      Folia and Velocity, with post-cleanup resource/state assertions.
- [ ] Independent failures do not abort discovery; original-release report distinguishes
      complete/failing coverage from incomplete/blocked coverage and retains raw evidence.

## Repairs and candidates

- [ ] Each demonstrated issue has a minimal reproduction, source cause and behavior test.
- [ ] Repairs preserve item protections, authorization, data integrity and version branches.
- [ ] Original/rebuilt-development/repaired binaries and findings remain separately identified.
- [ ] Candidate matrix/soak rerun after final relevant changes, including development-only
      Private owner/lifecycle checks where that code is present.
- [ ] Healthy baseline candidates meet absolute/behavior budgets on recorded controlled
      hardware; no failed candidate auto-promotes itself or relaxes its thresholds.
- [ ] Localization impact and build/runtime/visual evidence boundaries reported for repairs.

## Reusable gates

- [ ] Later component benchmarks have real consumed outputs, warm-up, forks and units.
- [ ] Future PR selection includes shared dependencies and mandatory affected regressions;
      partial/smoke runs cannot satisfy a release gate.
- [ ] Missing/stale/malformed/inconclusive/partial evidence and changed final JARs are rejected.
- [ ] Trusted same-workflow release evidence binds all final hashes, host and fixture/budget
      identities before draft/upload prerequisites, without actually publishing in tests.
- [ ] Production JARs exclude bot, worldgen, probe, test and benchmark artifacts.
- [ ] Interrupted/crashed runs retain evidence and clean up only test-owned resources.
