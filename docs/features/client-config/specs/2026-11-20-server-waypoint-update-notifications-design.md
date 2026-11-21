# Server waypoint update notifications

Players can choose how their client confirms that it applied a server update that added,
edited, or removed a waypoint. A server broadcast can arrive even when the corresponding
waypoint update does not reach the client or cannot be applied.

## Client setting

Add **Waypoint update notifications** to Client Config using the existing enum dropdown.
The default is **Off**. The selection applies immediately, is saved with the client
config, and participates in **Reset to defaults**. The map sync notification preference is
independent.

| Mode | Display after a successful update |
| --- | --- |
| **Off** | No client notification |
| **Chat** | A local line such as “Added [HB] Home Base to Farms” with the broadcast's gray text, waypoint marker, and interactive references |
| **Toast** | A Minecraft toast such as “Waypoint added: Home Base” using the map sync toast style |

Use the applied display name for adds and edits, including renames, and the pre-removal
display name for removals. Use client-side Minecraft translations for the setting and messages.
Chat uses the removed waypoint's cached marker and leaves its actions unavailable. List links and
waypoint actions use the update's collection scope. The update carries no actor attribution.

## Approaches

- **Notify when the client applies an incremental server update.** This is the chosen scope.
  It confirms changes that actually reach the client's Shared or Private waypoint data,
  including changes initiated by the player and changes initiated elsewhere.
- **Also notify for local mutations.** This would cover Local profile saves and integrated
  singleplayer Shared changes by hooking their separate success paths. The requested scope
  is server updates only.

## Trigger and scope

Emit one notification on the client thread after the existing apply path successfully adds,
updates, or removes a waypoint from Shared or Private data. This covers restores delivered as
adds. A received packet, a successful edit acknowledgement, and a server broadcast are each
insufficient on their own. Reuse the existing revision and readiness checks and require an actual
mutation. Initial sync, repair snapshots, list changes, duplicate or skipped updates, and failed
applies produce no confirmation.

Server command messages, server broadcasts, and existing command, form, and network errors
continue through their current paths. If both broadcast chat and a corresponding update arrive,
**Chat** mode can display the server announcement and the client confirmation. The feature uses
the existing update packets and needs no server configuration or protocol changes.

## Verification

Verify the setting persists and resets, and that an applied add, edit, or removal dispatches
one notification in the selected channel for both Shared and Private. Check that **Off** leaves
updates and errors working, that a broadcast without an applied update creates no confirmation,
and that a successful update still produces a confirmation when server broadcasts are disabled.
Exercise duplicate or rejected updates and snapshot sync. Check chat and toast in a running
client to verify what the player sees.
