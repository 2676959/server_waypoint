# Local profile screen layout refinement

Superseded by the [Local profile list design](2026-10-30-local-profile-list-design.md).

The user requested the minimalist-ui direction, name-based profile input, and a less crowded layout
that makes the next action clear. This is a scoped redesign of the existing Minecraft screen.

## Approach

Updated on 2026-10-30 at the user's request to imitate the config screen and use hybrid rows.
Follow `ClientConfigScreen`: one centered, content-sized settings panel, section headers,
compact row buttons, a 1.2-scale title and a separate status/Done footer. Use labelled `Row`
entries with inline actions for profile inputs and `WideRow` for long teleport-command input.
Guidance belongs in row tooltips instead of standalone text/button stacks. Constrained windows
scroll the one panel while preserving title and footer space.

The initial split selector/settings columns are superseded by this explicit user correction.
Name-based search, explicit Use profile, UUID identity and isolated creation/deletion dialogs
remain the interaction baseline. No changes to storage or source routing are needed.

## Interaction

- Match profile names without case sensitivity or the separator. Suggestions also match name
  substrings and ID prefixes. Keep the short ID as optional disambiguation, not required input.
- Resolve a unique exact name or unique ID prefix; ambiguous duplicate names require selecting a
  dropdown entry. Keep UUID identity separate from presentation and reject ambiguous prefixes.
- The picker only chooses a switch destination. Use profile saves/rebinds explicitly; its button
  is disabled for unresolved/current profiles. The settings area always names the active profile.
- Rename uses the active profile's name field. New profile has a dedicated name-entry dialog.
  Delete confirms the active profile's name. Neither operation borrows the switch query as a name.
- Teleport command editing has its own heading, token guidance and Save command action.
- Preserve failed-save data, captured-generation checks, popup priority, modal focus, source
  isolation and existing storage behavior. No migrations or storage-format changes.

## Verification

Test real name/ID resolution, duplicate names/prefixes, explicit switching with partial input,
responsive geometry, stale operations and locale coverage. Compile active and older Stonecutter
branches and run the client suite. Record visual/input proof separately from pure layout tests.
