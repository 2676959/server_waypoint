# Publishing

Server Waypoint publishes Fabric, Forge and NeoForge artifacts to Modrinth and CurseForge. Paper and Velocity artifacts publish to Modrinth only; the CurseForge project is a Minecraft mod project.

- [4.0.0 release checklist](plans/2026-10-15-4.0.0-release.md)
- [4.0.0 platform metadata](specs/4.0.0-metadata.md)
- [4.0.0 preparation results](validation/2026-10-15-4.0.0.md)
- [Complete changelog](../../../CHANGELOG.md) — detailed documentation and release history.
- [Publishing changelog](../../../CHANGELOG_PUBLISH.md) — simplified notes for the current release, used by both upload services and the GitHub draft workflow.
- [Project description](../../project-description.md) — shared platform description with current screenshots.
- [Artifact packaging](../jar-packaging/) — release JAR content rules.

## Changelogs

Update both changelogs when preparing a release. Keep the full history, technical details, behavior changes and upgrade instructions in `CHANGELOG.md`. Replace `CHANGELOG_PUBLISH.md` with notes for the current release only, written for players: focus on new features, improvements and bug fixes, and include essential update instructions in plain language. Avoid implementation details and benchmark figures in publishing notes.

Use `CHANGELOG_PUBLISH.md` for manual Modrinth and CurseForge uploads, with Markdown selected on CurseForge. Automated uploads and GitHub release drafts use this file by default. The complete changelog remains available as repository documentation.

## Upload tasks

Run from the repository root. Prefix commands with `rtk` in the local Codex environment.

| Platform | All uploads | Loader groups |
| --- | --- | --- |
| Modrinth | `./gradlew publishModrinth` | `publishModrinthFabric`, `publishModrinthForge`, `publishModrinthNeoForge`, `publishModrinthPaper`, `publishModrinthVelocity` |
| CurseForge | `./gradlew publishCurseForge` | `publishCurseForgeFabric`, `publishCurseForgeForge`, `publishCurseForgeNeoForge` |

Upload tasks depend on each target's `build` task. They upload the exact final JAR under that target's `build/libs/`, rather than reading the collected `builds/` directory. Each service serializes its uploads. The build has no transaction spanning uploads or services: an earlier file may already be public when a later upload fails.

`./gradlew publishModrinth publishCurseForge --dry-run --console=plain` only checks configuration and the task graph. It does not build files, validate credentials or submit uploads.

The plugin debug flags, `-PmodrinthDebug=true` and `-PcurseforgeDebug=true`, execute plugin validation without submitting files. They can still access provider APIs. CurseForge debug mode still requires a token in this repository. Do not mistake an unflagged publishing command for a rehearsal.

## Credentials and overrides

Supply secrets through the shell or CI secret store; keep them out of commands, logs and Git. Modrinth reads `MODRINTH_TOKEN`; CurseForge reads `CURSEFORGE_TOKEN` or the Gradle property `curseforgeToken`. A Modrinth token needs `CREATE_VERSION` for version uploads. Editing the project description is a separate action; `PROJECT_WRITE` is needed if using Minotaur's body-sync task.

| Setting | Default / override |
| --- | --- |
| Modrinth release type | `release`; override with `-PmodrinthVersionType=beta` or `alpha` |
| CurseForge release type | `release`; override with `-PcurseforgeReleaseType=beta` or `alpha` |
| Modrinth changelog | `-PmodrinthChangelog`, then `MODRINTH_CHANGELOG`, then root `CHANGELOG_PUBLISH.md` |
| CurseForge changelog | `-PcurseforgeChangelog`, then `MODRINTH_CHANGELOG`, then root `CHANGELOG_PUBLISH.md` |

The changelog properties accept the actual Markdown text, not a file path. `MODRINTH_CHANGELOG` currently affects **both** services. Clear it for the standard release notes. File uploads do not update the project summary, description, gallery, links or categories; update these separately in the project dashboards.

## Recovering from an interrupted upload

Record each successful platform version/file ID, filename, loader, game versions and SHA-512. Compare the public files with the local artifact manifest before retrying. Retry only a missing target with its fully qualified task, for example `./gradlew :mods:26.3-fabric:modrinth` or `./gradlew :mods:26.3-fabric:curseforge`. All 4.0.0 variants share `version_number=4.0.0`; identify them by loader, game versions and artifact hash, not that field alone.

For CurseForge, also check the author dashboard for files awaiting moderation before treating a publicly absent file as missing. If a release must be corrected, record which files were replaced and why; do not blindly rerun either aggregate or delete an entire version family.

## Provider references

- [Minotaur usage, token scopes, debug mode and separate body sync](https://github.com/modrinth/minotaur)
- [CurseForgeGradle usage and documentation](https://github.com/Darkhax/CurseForgeGradle)
- [CurseForge file upload API](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-upload-api)
