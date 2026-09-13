# Project Rules

- Language: Always use Java 17 compatible features. Use Kotlin DSL for gradle build scripts.
- Formatting: Indent with 4 spaces, never tabs.
- Libraries: Use GSON for JSON processing.
- Do not commit the changes you made directly, except if you are asked to do this.
- When implementing new features or making changes on existing features, do not write any code for handling backward compatibility unless you are asked to do this.

# Project Structure

This Minecraft modding project has three subprojects: "common", "paper", and "mods".
- "common": includes the server-side core logic and some common utilities that are shared with "paper" and "mods"
- "paper": implements the plugin that runs on Paper servers
- "mods": implements the mod that runs with Fabric and NeoForge, has both server-side and client-side logic
Projects "paper" and "mods" are independent projects to each other.

# Helper Scope

- Before creating or moving helper classes, identify their usage scope first. Helpers used only by one project must stay in that project instead of `common`.

# Project Documentation

- Read [`docs/tips/gui/local-guide.md`](docs/tips/gui/local-guide.md) before changing GUI render entry points, manual widget rendering, hover behavior, or the related Stonecutter render-method swaps.
- When creating a new API or changing an existing API under `mods/src/main/java/_959/server_waypoint/common/client/gui`, update its documentation in [`docs/tips/gui/local-guide.md`](docs/tips/gui/local-guide.md) in the same change.
- Read [`docs/tips/adventure-text.md`](docs/tips/adventure-text.md) before composing Adventure feedback with interactive or styled components.

# Documentation Artifacts

- Superpowers skills must write their artifacts under the owning feature's folder in `docs/`, never the repository root:
  - Plans: `docs/features/<feature>/plans/YYYY-MM-DD-<feature-name>.md`
  - Design specs: `docs/features/<feature>/specs/YYYY-MM-DD-<topic>-design.md`
- Every feature must have a README index and `plans/`, `specs/`, and `validation/` directories. Use `.gitkeep` for empty directories.
- Shared implementation guidance belongs in `docs/tips/`; whole-project diagrams belong in `docs/architecture/`.
- Create the feature folder if it does not exist. `docs/superpowers/` is retired; do not add artifacts there.

# Stonecutter Rules

- Stonecutter is used for versioned `mods` and `paper` builds. Check `settings.gradle.kts` for the version matrix and active project files (`mods/stonecutter.gradle.kts`, `paper/stonecutter.gradle.kts`) before changing version-specific code.
- Prefer Stonecutter predicates for small API differences instead of duplicating whole classes. Keep predicates broad and readable, such as `>=1.20.5`, `<1.21.11`, `>=26`, or loader constants from `constants.match(loader, "fabric", "neoforge", "forge")`.
- Do not treat inactive Stonecutter branches as dead comments. Preserve alternate-version branches unless support for that version is intentionally removed.
- Use the narrowest readable Stonecutter feature: inline block comments for expression-level differences, line scopes for one statement, closed scopes for blocks or branch chains, and swaps/replacements only for repeated text fragments.
- When adding or changing a Stonecutter swap or replacement, update the inventory below in the same change. Keep identifiers in `snake_case` and reuse existing identifiers when they already describe the fragment.
- Replacement tokens such as `//~ id` must stay before any non-empty, non-comment line in a file. Do not move or delete them as ordinary comments.
- After editing Stonecutter comments, check the touched file for balanced `//? if`, `//?}`, `/*?`, `/*?}*/`, and `*//*?` markers, then run the relevant Stonecutter/Gradle check when practical.

# Stonecutter Swaps And Replacements

Configured in the `stonecutter { ... }` blocks under `mods/*.gradle.kts`.

## Swaps

- `render_widget_method_swap`: `extractWidgetRenderState` when `current.version >=26`; otherwise `renderWidget`.
- `render_method_swap`: `extractRenderState` when `current.version >=26`; otherwise `render`.
- `payload_s2c_registry_swap`: `clientboundPlay` when `current.version >=26`; otherwise `playS2C`.
- `payload_c2s_registry_swap`: `serverboundPlay` when `current.version >=26`; otherwise `playC2S`.
- `resource_location_type_swap`: `ResourceLocation` when `current.version <1.21.11`; otherwise `Identifier`.
- `mouseScrolled_swap`: `mouseScrolled($1, $2, $3)` when `current.version <=1.20.1` in the regular Fabric/Forge/NeoForge scripts; otherwise `mouseScrolled($1, $2, $3, $4)`. The unobfuscated Fabric and legacy NeoGradle scripts always use the four-argument form.

## Replacements

- `gui_graphics_26` regex replacement: when `current.version >=26`, replaces `\bGuiGraphics\b` with `GuiGraphicsExtractor` and reverses `\bGuiGraphicsExtractor\b` back to `GuiGraphics`.
- `gui_render_state_26` string replacement: when `current.version >=26`, replaces `net.minecraft.client.gui.render.state.GuiElementRenderState` with `net.minecraft.client.renderer.state.gui.GuiElementRenderState`.
- `resource_location_import` string replacement: when `current.version <1.21.11`, replaces `net.minecraft.resources.Identifier` with `net.minecraft.resources.ResourceLocation`.
- `fabric_key_mapping_import_26` string replacement: Registered by every mods script because Stonecutter 0.9 validates replacement tokens before loader-specific source exclusions. In Fabric source when `current.version >=26`, replaces `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper` with `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper`.
- `fabric_key_mapping_call_26` string replacement: Registered by every mods script because Stonecutter 0.9 validates replacement tokens before loader-specific source exclusions. In Fabric source when `current.version >=26`, replaces `KeyBindingHelper.registerKeyBinding` with `KeyMappingHelper.registerKeyMapping`.
