# GUI development reference

The detailed GUI development guide is workspace-only and is not included in a clean clone. Existing workspaces keep it as `docs/tips/gui/local-guide.md`.

Obtain the guide from the workspace maintainer, or recreate it by inspecting `mods/src/main/java/_959/server_waypoint/common/client/gui`, the version matrix in `settings.gradle.kts`, and the render swaps in `mods/*.gradle.kts`. Verify Minecraft API details against the matching version sources.

Read the local guide before changing GUI render entry points, manual widget rendering or hover behavior. Update it when changing GUI APIs; keep the guide at the path above so workspace instructions remain consistent.
