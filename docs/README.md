# Server Waypoint documentation

Documentation is organized **one folder per feature**. Each feature folder holds its own contracts,
implementation records, validation evidence, plans and specs, so everything about a feature lives in
one place.

| Folder | What it covers |
| --- | --- |
| [`cross-server/`](cross-server/) | Cross-server waypoint discovery and teleportation over Velocity |
| [`upload/`](upload/) | Chunked upload and download transport (3.1.0) |

## Conventions

- **One folder per feature.** A feature folder owns its `plans/`, `specs/` and `validation/`.
- **Plans and specs are per feature** — `docs/<feature>/plans/` and `docs/<feature>/specs/`.
  Superpowers skills write there, never to the repository root or `docs/superpowers/`.
- **`validation/` holds raw evidence** — logs, JSON results and test reports, archived as produced.
  Evidence is not edited after the fact, so paths recorded inside it may predate a reorganization.
- **Cross-cutting material stays at this root** — see [Other material](#other-material).

## cross-server

Cross-server support is opt-in and disabled by default. Read the
[administrator guide](cross-server/cross-server-admin.md) to run it, or the
[release notes](cross-server/cross-server-release-notes.md) for what shipped.

### Contracts

Normative wire and model definitions. Read these before changing anything that crosses the
backend/proxy boundary.

| Document | Scope |
| --- | --- |
| [Protocol v1](cross-server/cross-server-protocol-v1.md) | Normative feature contract (step 1) |
| [Module contracts](cross-server/cross-server-proxy-module-contracts.md) | Module boundaries and packaging (step 3) |
| [Catalog models](cross-server/cross-server-catalog-models.md) | Shared catalog domain types (step 4) |
| [Application codec v1](cross-server/cross-server-application-codec-v1.md) | Backend/coordinator wire format (step 5) |
| [TCP transport v1](cross-server/cross-server-tcp-transport-v1.md) | Reusable TCP channels (step 6) |
| [Pairing v1](cross-server/cross-server-pairing-v1.md) | Credentials, pairing and rotation (step 7) |
| [Connection lifecycle](cross-server/cross-server-connection-lifecycle.md) | Presence, registration and reconnect (step 8) |

### Feature areas

**Catalog** — publishing and querying remote waypoints

- [Backend catalog publication](cross-server/cross-server-catalog-publication.md) (step 9)
- [Aggregation and distribution](cross-server/cross-server-catalog-distribution.md) (step 10)
- [Remote queries and suggestions](cross-server/cross-server-catalog-queries.md) (step 11)

**Permission and handoff** — who may teleport, and how the switch happens

- [Permissions and authorization](cross-server/cross-server-authorization.md) (step 12)
- [Coordinator handoff state machine](cross-server/cross-server-handoffs.md) (step 13)
- [Destination preparation and arrival](cross-server/cross-server-destination.md) (step 14)
- [Remote teleport initiation](cross-server/cross-server-source-teleport.md) (step 15)

**Runtime and client**

- [Velocity runtime integration](cross-server/cross-server-velocity-runtime.md) (step 16)
- [Remote catalogs on modded clients](cross-server/cross-server-client-sync.md) (step 17)
- [Remote waypoint manager GUI](cross-server/cross-server-gui.md) (step 18)

### Release

- [Release readiness](cross-server/cross-server-release-readiness.md) — step 19 hardening and release gates
- [Release notes](cross-server/cross-server-release-notes.md)
- [Step 3 platform validation](cross-server/cross-server-step3-validation.md)
- [Validation evidence](cross-server/validation/) — logs and results for steps 3, 16–19

### Decisions

- [Noise dependency selection](cross-server/cross-server-noise-dependency-decision.md) — KK selected (step 2)
- [NKpsk0 investigation](cross-server/cross-server-noise-nkpsk0-investigation.md) — archived, superseded

### Status and history

- [Implementation plan](cross-server/cross-server-waypoint-teleportation-plan.md) — the original design
- [Progress record](cross-server/cross-server-waypoint-teleportation-progress.md) — step-by-step commit log

### By step

The documents cross-reference each other by step number. Use this table to resolve a step to its record.

| Step | Document |
| --- | --- |
| 1 | [Protocol v1](cross-server/cross-server-protocol-v1.md) |
| 2 | [Noise dependency decision](cross-server/cross-server-noise-dependency-decision.md) |
| 3 | [Module contracts](cross-server/cross-server-proxy-module-contracts.md) · [validation](cross-server/cross-server-step3-validation.md) |
| 4 | [Catalog models](cross-server/cross-server-catalog-models.md) |
| 5 | [Application codec v1](cross-server/cross-server-application-codec-v1.md) |
| 6 | [TCP transport v1](cross-server/cross-server-tcp-transport-v1.md) |
| 7 | [Pairing v1](cross-server/cross-server-pairing-v1.md) |
| 8 | [Connection lifecycle](cross-server/cross-server-connection-lifecycle.md) |
| 9 | [Catalog publication](cross-server/cross-server-catalog-publication.md) |
| 10 | [Catalog distribution](cross-server/cross-server-catalog-distribution.md) |
| 11 | [Catalog queries](cross-server/cross-server-catalog-queries.md) |
| 12 | [Authorization](cross-server/cross-server-authorization.md) |
| 13 | [Handoffs](cross-server/cross-server-handoffs.md) |
| 14 | [Destination](cross-server/cross-server-destination.md) |
| 15 | [Source teleport](cross-server/cross-server-source-teleport.md) |
| 16 | [Velocity runtime](cross-server/cross-server-velocity-runtime.md) |
| 17 | [Client sync](cross-server/cross-server-client-sync.md) |
| 18 | [Remote GUI](cross-server/cross-server-gui.md) |
| 19 | [Release readiness](cross-server/cross-server-release-readiness.md) |

## upload

Chunked upload and download transport, shipped in 3.1.0.

- [Release notes 3.1.0](upload/release-notes-3.1.0.md)
- [Forge and NeoForge startup fixes](upload/upload-startup-fixes.md)
- [Transport progress](upload/upload-transport-progress.md)
- [Validation evidence](upload/validation/)

## Other material

| Path | Notes |
| --- | --- |
| [adventure-text-tips.md](adventure-text-tips.md) | Adventure component tips for clickable and styled feedback |
| [architecture/](architecture/) | Whole-project architecture HTML + JSON — **local only, not in git** |
| [gui-tips/](gui-tips/) | Client GUI API reference — **local only, not in git** |
