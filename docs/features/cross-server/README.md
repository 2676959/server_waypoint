# Cross-server waypoint documentation

Cross-server support is opt-in and disabled by default. Read the
[administrator guide](cross-server-admin.md) to run it, or the
[release notes](cross-server-release-notes.md) for what shipped.

## Contracts

Normative wire and model definitions. Read these before changing anything that crosses the
backend/proxy boundary.

| Document | Scope |
| --- | --- |
| [Protocol v1](specs/cross-server-protocol-v1.md) | Normative feature contract (step 1) |
| [Module contracts](specs/cross-server-proxy-module-contracts.md) | Module boundaries and packaging (step 3) |
| [Catalog models](specs/cross-server-catalog-models.md) | Shared catalog domain types (step 4) |
| [Application codec v1](specs/cross-server-application-codec-v1.md) | Backend/coordinator wire format (step 5) |
| [TCP transport v1](specs/cross-server-tcp-transport-v1.md) | Reusable TCP channels (step 6) |
| [Pairing v1](specs/cross-server-pairing-v1.md) | Credentials, pairing and rotation (step 7) |
| [Connection lifecycle](specs/cross-server-connection-lifecycle.md) | Presence, registration and reconnect (step 8) |

## Feature areas

**Catalog** — publishing and querying remote waypoints

- [Backend catalog publication](specs/cross-server-catalog-publication.md) (step 9)
- [Aggregation and distribution](specs/cross-server-catalog-distribution.md) (step 10)
- [Remote queries and suggestions](specs/cross-server-catalog-queries.md) (step 11)

**Permission and handoff** — who may teleport, and how the switch happens

- [Permissions and authorization](specs/cross-server-authorization.md) (step 12)
- [Coordinator handoff state machine](specs/cross-server-handoffs.md) (step 13)
- [Destination preparation and arrival](specs/cross-server-destination.md) (step 14)
- [Remote teleport initiation](specs/cross-server-source-teleport.md) (step 15)

**Runtime and client**

- [Velocity runtime integration](specs/cross-server-velocity-runtime.md) (step 16)
- [Remote catalogs on modded clients](specs/cross-server-client-sync.md) (step 17)
- [Remote waypoint manager GUI](specs/cross-server-gui.md) (step 18)

## Release

- [Remote UI, permission preflight and logging validation](validation/2026-09-13-remote-ui-permission-logging.md) — 2026-09-13

- [Release readiness](validation/cross-server-release-readiness.md) — step 19 hardening and release gates
- [Release notes](cross-server-release-notes.md)
- [Step 3 platform validation](validation/cross-server-step3-validation.md)
- [Validation evidence](validation) — logs and results for steps 3, 16–19

## Decisions

- [Noise dependency selection](specs/cross-server-noise-dependency-decision.md) — KK selected (step 2)
- [NKpsk0 investigation](specs/cross-server-noise-nkpsk0-investigation.md) — archived, superseded

## Status and history

- [Implementation plan](plans/cross-server-waypoint-teleportation-plan.md) — the original design
- [Progress record](cross-server-waypoint-teleportation-progress.md) — step-by-step commit log

## By step

The documents cross-reference each other by step number. Use this table to resolve a step to its record.

| Step | Document |
| --- | --- |
| 1 | [Protocol v1](specs/cross-server-protocol-v1.md) |
| 2 | [Noise dependency decision](specs/cross-server-noise-dependency-decision.md) |
| 3 | [Module contracts](specs/cross-server-proxy-module-contracts.md) · [validation](validation/cross-server-step3-validation.md) |
| 4 | [Catalog models](specs/cross-server-catalog-models.md) |
| 5 | [Application codec v1](specs/cross-server-application-codec-v1.md) |
| 6 | [TCP transport v1](specs/cross-server-tcp-transport-v1.md) |
| 7 | [Pairing v1](specs/cross-server-pairing-v1.md) |
| 8 | [Connection lifecycle](specs/cross-server-connection-lifecycle.md) |
| 9 | [Catalog publication](specs/cross-server-catalog-publication.md) |
| 10 | [Catalog distribution](specs/cross-server-catalog-distribution.md) |
| 11 | [Catalog queries](specs/cross-server-catalog-queries.md) |
| 12 | [Authorization](specs/cross-server-authorization.md) |
| 13 | [Handoffs](specs/cross-server-handoffs.md) |
| 14 | [Destination](specs/cross-server-destination.md) |
| 15 | [Source teleport](specs/cross-server-source-teleport.md) |
| 16 | [Velocity runtime](specs/cross-server-velocity-runtime.md) |
| 17 | [Client sync](specs/cross-server-client-sync.md) |
| 18 | [Remote GUI](specs/cross-server-gui.md) |
| 19 | [Release readiness](validation/cross-server-release-readiness.md) |

## Structure

- [Plans](plans/) — implementation sequencing.
- [Specs](specs/) — contracts and design decisions.
- [Validation](validation/) — release gates, validation records and archived evidence.
