# Local profile screen refinement plan

Superseded by the [Local profile list plan](2026-10-30-local-profile-list.md).

Use inline execution. Do not commit or delegate.

**Goal:** Make Local profile selection discoverable and group actions by their actual target.
**Spec:** [Layout design](../specs/2026-10-29-local-profile-layout-design.md).

- [x] Add failing tests for case-insensitive names, separator-free ID prefixes, duplicate-name
  ambiguity, stable identity and centered panel/title/footer bounds.
- [x] Implement screen-owned profile-choice and geometry helpers in LocalWaypointProfilesScreen;
  retain UUID identity separately from the editable query.
- [x] Compose existing panels/widgets into Switch profile, active-profile name, teleport and
  removal sections. Put creation in its own dialog; retain status and Done outside the scroll area.
- [x] Test the real screen's explicit switching and stale dialogs; update all six client locales
  and GUI API guidance, and check active/older generated sources.
- [x] Run focused and full active Fabric tests, older Fabric tests and loader compile checks;
  review input/layering/layout and document the evidence. Leave all prior Task 7 work intact.

## Config-screen refinement — 2026-10-30

- [x] Follow the user's correction: replace the two-column design with a single config-style panel.
- [x] Use hybrid labelled control/action rows and full-width command input; move guidance into tooltips.
- [x] Match title/row-button scale, section dividers, content centering and constrained scrolling.
- [x] Update geometry and actual widget-alignment checks; preserve name matching and operation tests.
- [x] Run full active Fabric, focused older Fabric and representative Forge/NeoForge compilation.
