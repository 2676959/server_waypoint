# Server Waypoint documentation

| Category | Contents |
| --- | --- |
| [Features](features/) | Feature contracts, plans, progress and validation |
| [Tips](tips/) | Shared implementation guidance |
| [Architecture](architecture/) | Whole-project architecture material |

## Conventions

- Each feature lives under `docs/features/<feature>/` and has a README indexing its documentation.
- Every feature contains `plans/`, `specs/` and `validation/`; use `.gitkeep` for empty directories.
- Plans belong in `docs/features/<feature>/plans/`; normative contracts and feature designs belong in `specs/`.
- Superpowers artifacts use `plans/YYYY-MM-DD-<feature-name>.md` and `specs/YYYY-MM-DD-<topic>-design.md` under the feature, never the repository root or retired `docs/superpowers/`.
- Validation records and release gates belong in `validation/`, alongside raw evidence. Archived evidence retains its original bytes, including historical paths.
- Cross-feature implementation guidance belongs in `tips/`. Whole-project architecture belongs in `architecture/`; feature architecture belongs in that feature's `specs/`.
- Tracked indexes link only to repository content. Local-only material must have a README describing how to obtain or recreate it.
