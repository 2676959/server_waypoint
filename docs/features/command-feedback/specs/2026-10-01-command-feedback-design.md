# /wp Command Feedback Design

Status: design agreed on 2026-10-01 and implemented; see the
[implementation plan](../plans/2026-10-01-command-feedback.md) and the
[validation record](../validation/2026-10-01-command-feedback-validation.md).

Amended on 2026-10-02: `/execute as <player>` shows the player's view to both the player and the
commander, on every platform (15). The rest of the design is unchanged.

Amended on 2026-10-03: only screens end with the trailing blank line; results, errors, broadcasts and
prompts read like chat lines and get none (2.3).

## Intent

The chat feedback of `/wp` grew one command at a time and was never designed as a whole:

- Many actions still start from a blank chat line: choosing a colour or facing, adding a waypoint
  where you stand and picking an upload mode all need a typed command.
- `/wp list` pages count waypoint rows only. List and dimension headings don't count, so a page
  can grow past the 20 lines an open chat shows.
- The vanilla font is not monospace, and some glyphs in use are not in it: the `⋯` details button
  falls back to Unifont. Bold headings crowd the letters, and the bold menu title leaks into the
  text after it.
- `WaypointCommandHelp` hard-codes the English menu labels.
- Errors are red on Fabric and NeoForge but plain on Paper, because only
  `ModMessageSender.sendError` colours them.
- Dimensions show as full IDs and are coloured by ID, so every extra world on a Paper server is
  yellow.
- Remote browsing has its own layout: one grouped tree, `Name [id]` labels that look like buttons,
  the `⋯` button and row-based pages.
- `waypoint.upload.cooldown` uses `%s`, and `waypoint.help.upload` and
  `waypoint.help.edit.example.icon` contain unescaped apostrophes.

Goals:

1. Players reach most features by clicking feedback instead of typing whole commands.
2. Feedback looks good in vanilla chat.
3. The console, RCON and command blocks still receive every essential detail.

Success means every feature is reachable from `/wp` by clicking, except where the player has to
type a name, coordinates or search text. Every chat line fits 320 px and every message fits the
20-line window in English with typical names. For plain-text viewers, nothing essential depends on
hover, click or colour.

This document describes every message as it should look and behave, and the code changes behind
them. It is the reference for the implementation plan.

## Scope

In scope:

1. Chat constraints.
2. Visual language and tooltips.
3. Dimension names, colours and order.
4. The menu.
5. Local lists, the dimension list and all dimensions.
6. Waypoint and list details.
7. Pickers, navigation, text display, upload and download.
8. Help.
9. Results, broadcasts and errors.
10. Remote waypoints.
11. Plain-text viewers.
12. Command changes.
13. Code structure.
14. Text and translations.
15. Documentation.

Out of scope:

- The waypoint manager and other GUI screens, apart from the `/wp gui` fallback under Risks.
- Dialog screens (1.21.6+).
- Editing remote waypoints. Remote browsing stays read-only.
- Waypoint storage, file formats and network payloads.

## 1. Chat constraints

- Chat is 320 GUI px wide by default and at most. Lines are 9 px high, and an open chat shows 20
  lines. Wrapping breaks at the last space and indents continuation lines by one space.
- The font is a bitmap font with variable widths. Advances include the 1 px gap: space 4, `[` and
  `]` 4, most letters 6, `i` 2, `l` 3, `t` 4. Bold adds 1 px to every character.
- Spaces cannot align columns. Only identical prefixes align, such as the `[✎] ` column in
  details.
- The client refuses click commands longer than 256 characters. An action whose command would be
  longer is rendered as plain text without a click.
- Characters missing from the bitmap font fall back to Unifont and look out of place.
- Targets, in English with typical names: every line at most 320 px, every screen at most 19 lines
  plus its trailing blank line, and every other message at most 20 lines.

## 2. Visual language

### 2.1 Colours

| Colour | Use |
| --- | --- |
| gold | titles, the current view and sort, the "you are here" `●` |
| aqua | links, click hints and typing instructions in tooltips |
| green | add and create actions, `✔` results, the available server dot |
| red | `✘` errors, destructive actions, the unreachable server dot |
| yellow | `[✎]` edit buttons, `Custom…`, `Prefer mine`, `Adjust text display`, the stale server dot, the player's name in the `Viewed as` line (15) |
| light purple | navigation and teleport actions |
| white | names of waypoints, lists and servers; property values |
| gray | labels, counts, distances, descriptions, status words, secondary links such as `Help` and `Back`, and the italic `Viewed as` line (15) |
| dark gray | separators, disabled controls and minor annotations (`(continued)`, `none`) |
| waypoint colour | `[AB]` initials and colour swatches |
| dimension colour | dimension names |

Dimension colours follow the dimension type: overworld green, the_nether red, the_end light
purple, anything else yellow. Informational text is never dark gray.

### 2.2 Glyphs

Only these glyphs are used. All are in the vanilla bitmap font; advances in px.

| Glyph | Advance | Use |
| --- | --- | --- |
| `✔` | 7 | results; navigation methods that are on |
| `✘` | 7 | errors |
| `●` | 5 | "you are here" marker; server status dots |
| `⏷` | 6 | after a name that opens a picker |
| `↑` `↓` | 6 | sort direction |
| `‹` `›` | 4 | previous and next page |
| `«` `»` | 7 | first and last page |
| `…` | 8 | `… N more`, `Custom…`, progress |
| `·` | 2 | separator |
| `█` | 9 | colour previews and picker buttons |
| `✎` | 8 | edit buttons |
| `×` | 6 | clear buttons |
| `−` `+` | 6 | nudge buttons; add |
| `°` | 5 | degrees |

`⋯`, `▸`, `▾`, `✓` and `✗` fall back to Unifont and are not used. `▼` is replaced by `⏷`.

### 2.3 Rules

- **No bold.** Titles are gold at regular weight.
- **Separators** are ` · ` in dark gray and always a component of their own. Translations never
  contain separators; builders join the pieces.
- **Links and buttons.** Browsing lines use links: coloured words. Panels about one object (waypoint
  details, list details, text display) use `[Buttons]`.
- **Waypoint references** are `[AB] Name`. The initials are bracketed, in the waypoint colour and
  undecorated, and clicking them teleports. Without teleport permission they are plain coloured text
  with the waypoint tooltip. The name is white and opens details.
- **`⏷`** after a name opens the picker for that level.
- **Screens end with a blank line.** A screen is structured feedback that players read apart from the
  next message: the menu, help, lists, details, pickers, navigation, upload and remote screens. A
  screen to a player ends with exactly one newline, which the game draws as a blank line before the
  next message. Every other message, such as a result, an error, a broadcast, a prompt or the upload
  report, reads like a chat line and gets none. Builders mark their screens (`Chat.screen`, or
  `ChatLines.buildScreen` for a message built line by line); the send boundary adds the newline, so
  builders never end with `\n`. A screen's empty state and its search without matches are still
  screens; a page that doesn't exist is an error line. Today's leading blank line on lists goes away.
  Plain-text viewers get no trailing newline.
- **Hidden controls.** Controls the viewer can't use because of a missing permission or client mod
  are left out. The one disabled control is Distance sorting outside the player's dimension (6.4).
- **Mod-only controls** are `Open GUI`, `Download` and `Upload`. Players count as having the mod
  when the handshake has completed or they host the singleplayer world.
- **Destructive actions** (remove, clear, reload, `Mirror`) suggest their command instead of running
  it. Their tooltip ends with `Press Enter to confirm`.
- **Edits** re-send the panel with a `✔ Updated …` line on top.
- **Results and errors.** Results start with a green `✔`, errors with a red `✘`. Builders apply the
  colour, so Paper and mods look the same, and `sendError` no longer adds red.

## 3. Tooltips

- The first line is white: the object's name or the action. Later lines are gray. Separators are
  dark gray and are split out of tooltip text automatically.
- Click hints and typing instructions are aqua: `Click for details`, `Click to open`,
  `Click to reverse`, `Click to type a page`, `Type its name, then press Enter`,
  `Press Enter to confirm`.
- A tooltip that describes an object on a clickable element (a waypoint, list, dimension, server or
  page number) ends with a click hint. A tooltip on an action names the action in its first line and
  needs no hint.

Waypoint name:

```
South Farm                            white
Trading hall with mending villagers   gray; only with a description
828, 69, 1233                         white
Nether 103, 69, 154                   red; Overworld-type dimensions only
1.5 km away                           gray; "In Nether" in other dimensions
Click for details                     aqua
```

Nether-type dimensions show `Overworld x×8, y, z×8` in green instead. Nether coordinates use
`Math.floorDiv(…, 8)`, like `BlockPosConverter`. In "In Nether", the dimension name has its
colour.

- `[AB]` initials: `Teleport to South Farm`, the coordinates, then the distance or dimension.
- Dimension: its name in its colour, the ID, the type (`Overworld type`, `Modded type <id>`),
  `12 waypoints in 3 lists` or `No lists yet`, `● You are here` in gold for the viewer's dimension,
  and a click hint.
- List: `Farms · 7 waypoints`, the identifier when a display name differs, and a click hint.
- Page number: `Page 2 of 5`, the page size and total, and `Click to type a page`.

## 4. Dimensions

- **Names.** Vanilla dimensions use translated names: Overworld, Nether, End. Other dimensions use
  their ID path, split on `_`, `-` and `/`, with each word capitalised:
  `twilightforest:twilight_forest` becomes `Twilight Forest` and `aether:the_aether` becomes
  `The Aether`. The ID is always in the tooltip.
- **Colours and converted coordinates** follow the dimension type, so a Paper world of Nether type
  is red and shows Overworld coordinates. When the type is unknown (an unloaded dimension or a
  remote catalog), they follow the ID.
- A dimension name always has its dimension colour, inside sentences and errors too.
- **Order**, wherever dimensions are listed: the viewer's dimension first, then Overworld, Nether
  and End, then the rest A–Z by display name.
- **The viewer's dimension** has a gold `●` after its count, with the tooltip `● You are here`. It
  always gets its own row, even with no waypoints.
- **Dimensions without waypoints** share one line: `No waypoints yet: Mars · Venus`. The label is
  white; the names are gray and clickable.
- **Counts** read `Nether · 4`. The tooltip has `4 waypoints in 1 list`.
- **Unloaded dimensions** with waypoint files appear in gray with `Not loaded` in the tooltip. Their
  lists still open.

## 5. Menu

`/wp`:

```
Server Waypoint   Open GUI · Help · Reload
Browse
  This dimension · All · Remote · Search
Create
  Waypoint here · List
Travel
  Navigation to [MH] Main Home · Stop
Transfer
  Download · Upload
```

- The title is gold. `Open GUI` (aqua, mod only) runs `/wp_gui`. `Help` (gray) runs `/wp help`.
  `Reload` (gray, permission) suggests `/wp reload`.
- Group labels are gray on their own line. Their links follow on the next line, indented two spaces.
- **Browse:** `This dimension` runs `/wp list`, `All` runs `/wp list all`, and `Remote` runs
  `/wp remote`, shown only when remote browsing is available to the player. `Search` suggests
  `/wp list all search `.
- **Create** (add permission): `Waypoint here` runs `/wp add`, the add picker. `List` suggests
  `/wp add <the player's dimension> `.
- **Travel** (navigate permission): `Navigation` runs `/wp navigate`. While navigating,
  ` to [AB] Name · Stop` follows.
- **Transfer** (mod): `Download` runs `/wp download`. `Upload` (upload permission) runs
  `/wp upload`.
- A group with nothing visible is left out.

## 6. Local lists

### 6.1 Header

```
Overworld ⏷  3 lists · 12 waypoints · All
```

The dimension name, in its colour, and `⏷` form one link to `/wp list dimensions`. The summary is
gray. `All` (aqua) runs `/wp list all`. A dimension without lists drops the summary: `Mars ⏷ · All`.

### 6.2 Views

Tree:

```
Overworld ⏷  3 lists · 12 waypoints · All
Home Bases  +
  [MH] Main Home · 25 m
  [GM] Gem Mine · 591 m
Farms  +
  [IF] Iron Farm · 263 m
  [WF] Wheat Fields · 63 m
  [CF] Cane Farm · 78 m
  … 4 more
Lists · Tree · Flat · Search · New list
Sort Default · Name · Distance · Color
```

- List headings are white and run `/wp details list …`.
- Rows are indented two spaces: `[AB] Name`, then ` · distance` in the viewer's dimension.
- A list with more than 5 waypoints shows its first 3 and an aqua `… N more` that opens the list.
- An empty list shows `  No waypoints yet` in gray italics.

The other views:

- **Lists**: one line per list, `Farms · 7  +`. The name opens the list.
- **Flat**: one row per waypoint, with the list name in gray after a separator:
  `[IF] Iron Farm · Farms · 263 m`. The list name opens the list.
- **Single list**, from `/wp list <dimension> <list>`:

  ```
  Overworld › Farms  7 waypoints · All
  [IF] Iron Farm · 263 m
  [WF] Wheat Fields · 63 m
  Search · Add here
  Sort Default · Name ↑ · Distance · Color
  ```

  The dimension name opens its list view. The gold list name opens list details.
- **Default view**: Tree when it fits on one page, otherwise Lists. A search uses Tree unless Flat
  is chosen.

### 6.3 Paging

`L` is the configured page limit, 10 by default.

- Tree pages hold whole lists within `L + 5` lines. A list counts its heading plus its rows: 4 when
  collapsed, at least 1.
- The Lists view has `L + 5` lists per page. Flat and single lists have `L` rows per page.
- When more follows, the page ends with an aqua `… N more lists` or `… N more waypoints` that opens
  the next page.
- The pager closes the last control line: `« ‹ 2/5 › »`. `«` and `»` appear only with 3 or more
  pages, and unavailable arrows are dark gray. The gray page number suggests `… page `.

### 6.4 Controls

- **View row**: `Lists · Tree · Flat · Search · New list`. The current view is gold and the others
  aqua. `Search` suggests `… search `. `New list` (green) suggests `/wp add <dimension> `. Flat and
  Search are hidden when the dimension has no waypoints.
- **Sort row**: `Sort Default · Name · Distance · Color`.
  - The selected mode is gold. Apart from Default, it carries `↑` or `↓`, and clicking it reverses
    the order. Its tooltip shows the direction, such as `Name · A to Z`, and `Click to reverse`.
  - Other modes are aqua and start ascending.
  - Distance is dark gray outside the viewer's dimension, with the tooltip
    `Distance needs you in Nether`.
  - The row is hidden when there are no waypoints.

### 6.5 Adding

- A green `+` follows each list name in the Tree and Lists views when the player can add. In the
  player's dimension it suggests `/wp add ~ ~ ~ "<list>" `. Elsewhere it suggests
  `/wp add <dimension> "<list>" `, where the player types coordinates and a name. The tooltip says
  which.
- Single lists offer `Add here` or `Add waypoint` with the same commands.

### 6.6 Search

Searches match waypoint names, keywords and list names. A results line follows the header:
`Search "farm" · 3 matches · Clear`. Without matches: `Nothing matches "farm". Clear search`.

### 6.7 Empty states

- **No lists**: `Mars ⏷ · All`, then `No lists yet. New list`.
- **Lists without waypoints**: Tree with placeholders. Flat, Search and the sort row are hidden.
- **An empty list**: `Nether › Storage · All`, then `No waypoints yet. Add waypoint · Remove list`.
  It reads `Add here` in the player's dimension. `Remove list` is red and suggests
  `/wp remove <dimension> "<list>"`.

## 7. Dimension list and all dimensions

`/wp list dimensions`:

```
Dimensions  12 on this server
Twilight Forest · 3 ●
Overworld · 12
Nether · 4
End · 1
Everbright · 5
No waypoints yet: Everdawn · Mars · Undergarden · Venus
All dimensions · 30
```

- Built from the platform's loaded levels or worlds, plus dimensions that have waypoint files.
- `L + 5` rows per page.
- Names run `/wp list <dimension>`. `All dimensions` (aqua) runs `/wp list all`.

`/wp list all`:

```
All dimensions ⏷
Overworld · 12 ●
  Home Bases · 4
  Farms · 4
Nether · 4
  Nether Hub · 4
  Storage · 0
… 2 more lists
Search    ‹ 1/2 ›
```

- Every dimension with lists appears in dimension order, including dimensions whose lists are all
  empty. Each list row is a white name that opens the list, then a gray count.
- `L + 5` lines per page. A page that starts inside a dimension repeats its heading with
  ` (continued)`.
- `All dimensions ⏷` (gold) opens the dimension list.
- `Search` suggests `/wp list all search `. Results are grouped under dimension headings, `L` rows
  per page.

## 8. Details

### 8.1 Waypoint details

```
Overworld › Home Bases › [MH] Main Home
[✎] Display name: Main Home
[✎] Identifier: Main Home
[✎] Initials: MH
[✎] Icon: none
[✎] Position: 120, 64, -35 [Here]
[✎] Color: █ #FFAA00
[✎] Yaw: 0° (south)
[✎] Visibility: Global
[✎] Keywords: home, base [×]
[✎] Description: Where the beds are [×]
[Navigate] [Teleport] [Download] [Remove] [Back]
```

- **Breadcrumb**: the dimension opens its list view and the white list name opens the list. `[AB]`
  teleports. The waypoint name is gold, with the waypoint tooltip.
- **`[✎]`** (yellow) suggests `set <property> <current value>`. Color and Yaw open their pickers
  instead, and Visibility runs the toggle. Labels are gray, values white, and `none` is dark gray
  italics.
- **`[×]`** (red) suggests `clear <property>` for a display name override, icon, keywords or
  description that is set. **`[Here]`** (green) runs `set position ~ ~ ~`.
- **Actions**: `[Navigate]` and `[Teleport]` light purple, `[Download]` aqua (mod only),
  `[Remove]` red (suggested), `[Back]` gray, which returns to the list.
- Without edit permission, the `[✎]` column, `[×]` and `[Here]` are hidden.
- An edit re-sends the panel with `✔ Updated the colour`, or the matching line, on top.

### 8.2 List details

```
Overworld › Home Bases  3 waypoints
[✎] Display name: Home Bases
[✎] Identifier: Home Bases
[Open list] [+ Waypoint here] [Download] [Remove] [Back]
```

- `[+ Waypoint here]` appears in the player's dimension. Elsewhere it is `[+ Waypoint]`, which uses
  the coordinates form.
- `[Remove]` is red only for an empty list. Otherwise it is dark gray, with the tooltip
  `Only empty lists can be removed`.

## 9. Pickers

Colour, from `set color` without a value:

```
Color · [MH] Main Home   now █ #FFAA00
█ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █
Random · Custom… · Back
```

- The 16 named colours are `█` swatches that run `set color <name>`. Each tooltip has the name and
  hex code.
- `Random` runs `set color random`.
- `Custom…` (yellow) suggests `set color <current hex>`.
- `Back` returns to details.

Facing, from `set yaw` without a value:

```
Facing · [MH] Main Home   now 0° (south)
South 0° · West 90° · North 180° · East -90°
Yours 37° · Custom… · Back
```

Add, from `/wp add` without arguments:

```
Add a waypoint at 100, 64, -20
Into  Home Bases · Farms · Exploration
New list · Back
```

- Each list (green) suggests `/wp add ~ ~ ~ "<list>" `.
- With more than `L` lists, the picker shows one list per line and pages them (6.3).
- Without lists, it reads `No lists yet. New list`.

## 10. Navigation and text display

`/wp navigate`:

```
Navigating to [MH] Main Home · 25 m
✔ Compass · Map · ✔ Bossbar · Actionbar · Text display
Stop · Change target · Adjust text display
```

- **Methods**: one that is on is a green `✔ Name` and runs `disable <method>`. One that is off is
  gray and runs `use <method>`. A toggle re-sends the panel with `✔ Compass on`.
- **`Stop`** (red) runs `/wp navigate disable`, which answers `✔ Stopped navigating   Resume`.
- **`Change target`** runs `/wp list`.
- **`Adjust text display`** (yellow) appears only while text display is on.
- **Not navigating**: `Not navigating`, then `Open a waypoint and choose Navigate:  This dimension · All`.

Text display, from `/wp navigate config text_display`:

```
Text display  offsets from the default placement
Move  X [−][+]  Y [−][+]  Z [−][+]  0, 0, 0 [✎]
Turn  X [−][+]  Y [−][+]  Z [−][+]  0°, 0°, 0° [✎]
Size  [−][+]  1× [✎]
[Reset] [Back]
```

Steps are 0.05 blocks, 5° and 0.05×. `[✎]` suggests the exact values.

## 11. Upload and download

`/wp upload` (mod and permission):

```
Upload from your map mod
Xaero's Minimap  Merge · Prefer mine · Mirror
VoxelMap  Merge · Prefer mine · Mirror
Uploads every dimension; add a dimension to limit it.
```

- `Merge` (aqua) runs `/wp upload <source>`.
- `Prefer mine` (yellow) suggests `… force local`.
- `Mirror` (red, delete permission) suggests `… force local delete`.

Result:

```
✔ Uploaded from Xaero's Minimap
3 added · 9 unchanged · 2 conflicts
2 conflicts kept the server's version.  Prefer mine
```

These replace the current upload messages, including the hard-coded `FORCE LOCAL` label.
Downloading answers `✔ Sent 12 waypoints to your map mod`.

## 12. Help

`/wp help`:

```
Server Waypoint help
Most things are a click away: open the menu.
Commands  List · Details · Add · Edit · Remove
  Teleport · Navigate · Upload · Download · Remote
  Reload
```

- **The index** lists the topics the viewer may use, five to a row. Every `/wp` subcommand has a
  usage in some topic; only the page links of pickers, such as `/wp add page <n>`, are left out.
- **Topics** show usage lines in aqua, with each `<argument>` in the colour of its type and the
  brackets and `|` of optional parts in gray; keywords inside optional parts stay aqua. Clicking a
  line suggests the command.
- **Long usages** break at argument boundaries, with continuation lines indented four spaces.
- **Tooltips** explain the arguments. A note about one argument names it in its colour:
  `<mode>: default, name, distance or color`.
- **Examples** follow the usages in topics that have them, in aqua with each value in the colour of
  the argument it fills, then `Help index · Menu`.

Argument types follow the command's argument types:

| Type | Arguments | Colour |
| --- | --- | --- |
| Text | `<list>`, `<waypoint>`, `<name>`, `<initials>`, `<keywords>`, `<description>`, `<text>`, `<server>`, `<token>`, `<value>` | yellow |
| Dimensions and IDs | `<dimension>`, `<id>` | green |
| Coordinates | `<position>` | light purple |
| Numbers | `<yaw>`, `<number>`, `<x>`, `<y>`, `<z>` | gold |
| Choices from a fixed set of words | `<mode>`, `<direction>`, `<view>`, `<method>`, `<source>`, `<property>`, `<topic>`, `<color>`, `<global>` | dark purple |

## 13. Results, broadcasts and errors

Results:

```
✔ Added [PP] Pumpkin Patch to Farms   Details · Navigate · Undo
✔ Created the list Farms in Overworld   Add a waypoint here · Open
✔ Removed [MH] Main Home from Home Bases   Restore
✔ Removed the list Storage from Nether   Undo
✔ Restored [MH] Main Home to Home Bases
✔ Teleported Steve to [MH] Main Home
```

`Undo` after adding suggests the remove command. `Restore` runs `/wp restore <token>`. `Teleported`
names the player by display name and keeps the format the name has, such as a team colour or the
hover and click a player's name carries; a name without a colour is white.

Broadcasts to other players:

```
Steve added [PP] Pumpkin Patch to Farms
Steve updated [MH] Main Home
Steve removed [MH] Main Home from Home Bases
Steve created the list Farms in Overworld
```

The player name is white, the verbs gray, and the waypoint references clickable. The Xaero
waypoint-sharing prompt (`ChatMessageHandler`) follows the same rules.

Errors are one red line starting with `✘`, plus a recovery link where one helps:

```
✘ No list called Farm in Overworld. Browse lists
✘ Page 5 does not exist; the last page is 2. Last page
✘ You aren't navigating. Browse waypoints
✘ Main Home changed while you were editing. Reload details
✘ You don't have permission to do that.
```

`/wp tp`, `/wp remove`, `/wp edit` and `/wp details` without a target show `/wp list` for the
player's dimension, with a gray first line that says what to click, for example
`Click a waypoint's initials to teleport.`

## 14. Remote waypoints

Remote browsing works like local lists with one more level: servers, then a server, a dimension, a
list, and read-only details.

### 14.1 Commands

```
/wp remote
/wp remote list [<server> [<dimension> [<list>]]] [list options]
/wp remote details <server> <dimension> <list> <waypoint>
/wp remote tp <server> <dimension> <list> <waypoint>
/wp help remote
```

`/wp remote` is the server picker. `/wp remote servers` and list details
(`/wp remote details <server> <dimension> <list>`) are removed. Remote list options gain
`view lists`. `sort distance` keeps its error, because coordinates from another server aren't
comparable.

### 14.2 Server status

- A coloured dot shows each server's state: green available, yellow stale, red unreachable, dark
  gray no access.
- In the picker, headers and breadcrumbs, the dot comes before the name, where the dots line up as
  a status column. In `/wp remote list` and remote search results, where rows are indented under a
  server, it comes after the name so the indentation lines up.
- The dot's tooltip names the state. For a stale server it adds
  `Teleporting is off until it refreshes`. That warning appears only in tooltips.
- Unreachable and no-access servers are not clickable and never show their last copy.

### 14.3 Screens

Server picker, `/wp remote`:

```
Remote servers  5 servers connected
● Creative Plots · 4
● Survival · 18
● Halloween Event · no access
● Lobby · unreachable
● Skyblock · nothing published
All servers · 22
```

Servers with waypoints come first, then the rest, A–Z within each group. Names are white, or gray
for empty and unreadable servers. Counts and status words are gray.

All servers, `/wp remote list`:

```
All servers ⏷
Creative Plots ● · 4
  Overworld · 4
Survival ● · 18
  Overworld · 14
  Nether · 3
  End · 1
Halloween Event ● · no access
Lobby ● · unreachable
Skyblock ● · nothing published
Search
```

A server, `/wp remote list survival`:

```
● Survival ⏷  3 dimensions · 18 waypoints
Overworld · 14
  Home Bases · 3
  Farms · 7
  Exploration · 4
Nether · 3
  Nether Hub · 3
End · 1
  End · 1
Search
```

A dimension has the local views (6.2), with Tree by default when it fits:

```
● Survival ⏷ › Overworld ⏷  3 lists · 14 waypoints
Home Bases
  [MH] Main Home
  [GM] Gem Mine
  [SV] Spawn Village
Lists · Tree · Flat · Search
Sort Default · Name · Color
```

- The server's `⏷` opens `/wp remote`. The dimension's `⏷` opens the server view.
- Default sorting keeps the published order.
- A list reads `● Survival › Overworld › Farms  7 waypoints`, followed by rows, `Search`, the sort
  row and the pager.
- Rows are `[AB] Name`, with no distance.
- The waypoint tooltip adds `On Survival in Overworld`. The `[AB]` tooltip adds
  `Switches you to the Survival server`.
- `[AB]` teleports only on an available server and for a player with remote teleport permission. On
  a stale server, its tooltip says `Off while Creative Plots is stale`.
- Paging uses the local budgets (6.3), replacing the row-based pages in `RemoteCatalogQuery`.
- Server, list and waypoint labels show display names, with identities in the tooltips. They stay
  literal text, at most 256 characters.

Details are read-only, without the `[✎]` column:

```
● Survival › Overworld › Farms › [IF] Iron Farm
Position: 300, 80, 150
Color: █ #AAAAAA
Yaw: 0° (south)
Visibility: Global
Keywords: iron
Description: none
[Teleport] [Back]
```

`Identifier:` appears first when the display name differs.

### 14.4 Teleport and errors

```
Switching you to Survival for [IF] Iron Farm…
✔ Arrived at [IF] Iron Farm on Survival
```

- The arrival line comes from the destination server.
- Every handoff failure result gets one `✘` line with `Try again` where retrying can help, or
  `Open Farms` when the waypoint is gone. For example: `✘ Survival can't be reached right now. Try again`.
- Unreadable servers: `● Lobby ⏷`, then `Lobby can't be reached right now. Servers` or
  `You don't have access to Halloween Event. Servers`.
- Missing identities: `✘ No server called x. Servers`, `✘ Survival has no dimension x. Browse`,
  `✘ Survival has no list x in Overworld. Browse`.

## 15. Plain-text viewers

The console, RCON and command blocks drop tooltips and clicks. The Fabric and NeoForge server log
and RCON also drop colour. A message goes to a plain-text viewer when no player reads it.

The feedback is the view of the player a command runs as, on every platform. In
`/execute as <player> run wp list` the player gets the feedback in their language, with a screen's
trailing newline and the controls they may use. The commander (the console, RCON, a command block
or another player) gets the same view under a first line, `Viewed as <player>`, gray and italic
with the name in yellow, in the commander's own language. A server admin sees what the player sees and can guide them. A
player commander's copy ends with the blank line when the view is a screen, as the player's does. The
copy for the console, RCON or a command block is plain text without the trailing newline. A command
that runs as the commander's own player, or as something that is not a player, sends one message to
the commander. On Fabric, NeoForge and Forge a suppressed stack, such as the one that runs a
datapack function (`/function`, a tick or load tag, a scheduled function), sends nothing, as in
vanilla; Paper does not check for one.

What the viewer may do comes from the view's own player (the platform's `viewingSource`), so the
commander's copy shows the help lines and buttons the player sees. Where the command runs (the
dimension, position and distances) still comes from the command source, as for any `/execute`; use
`execute as <player> at <player>` to read the feedback from the player's position.

For these viewers, nothing essential may depend on hover, click or colour. Builders:

1. **Inline the essentials.**
   - Dimensions and servers always show as `Display (id)`.
   - Lists and waypoints show `Display (identifier)` when the display name differs.
   - Every waypoint row shows its coordinates.
   - A server's state is a word: stale, unreachable, no access or nothing published.
2. **Drop controls that only work by clicking**: `⏷`, `All`, the view and sort rows, the pager
   arrows, the `[✎]` column, `[Buttons]`, `+`, recovery links and action links.
3. **Print commands where data continues**: `… 5 more lists: /wp list minecraft:overworld view tree page 2`,
   `… 4 more: /wp list minecraft:overworld Farms`, `Restore with /wp restore r12`.
4. **Show tooltip text as indented lines** where it is documentation: help argument details, and the
   accepted values when `set color` or `set yaw` runs without a value.
5. **Answer `/wp` with the help index**, since the menu is all links, and `/wp tp`, `/wp remove`,
   `/wp edit` and `/wp details` without a target with that command's help topic. Player-only actions
   (teleport, navigate, add here, upload, download) keep their player-only errors.
6. **Ignore the chat limits.** Plain-text lines may pass 320 px, and messages get no trailing
   newline. Paging stays the same, so page numbers match between chat and console.
7. **Keep the colours of chat.** Every piece keeps the colour players see, and text written out in
   place of a link, a tooltip or a distance takes its colour: `… N more: <command>` lines are aqua,
   row coordinates gray, help usages and examples coloured like their links (12), tooltip lines
   white, then gray, and accepted values gray. Paper's console shows the colours; the Fabric and
   NeoForge server log and RCON drop them.

```
Overworld (minecraft:overworld)  3 lists · 12 waypoints
Home Bases
  [MH] Main Home · 120, 64, -35
  [GM] Gem Mine · -210, 12, 480
Farms
  [IF] Iron Farm · 300, 80, 150
  … 4 more: /wp list minecraft:overworld Farms
… 2 more lists: /wp list minecraft:overworld view tree page 2
```

```
Dimensions  12 on this server
Overworld (minecraft:overworld) · 12
Twilight Forest (twilightforest:twilight_forest) · 3
No waypoints yet: Mars (ad_astra:mars) · Venus (ad_astra:venus)
```

```
Remote servers  5 servers connected
● Creative Plots (creative-1) · 4 · stale
● Survival (survival) · 18
● Lobby (lobby) · unreachable
```

```
✔ Removed [MH] Main Home from Home Bases. Restore with /wp restore r12
```

## 16. Commands

| Command | Change |
| --- | --- |
| `/wp add` | New without arguments: the add picker |
| `/wp edit waypoint … set color` | New without a value: the colour picker; new value `random` |
| `/wp edit waypoint … set yaw` | New without a value: the facing picker |
| `/wp navigate` | New without arguments: the navigation panel |
| `/wp navigate config text_display` | New: the text display panel |
| `/wp navigate status` | Removed; `/wp navigate` shows the same panel |
| `/wp upload` | New without arguments: the upload panel |
| `/wp list dimensions [page <n>]` | New: the dimension list |
| `view lists` | New list option, local and remote |
| `/wp tp`, `/wp remove`, `/wp edit`, `/wp details` | Without a target: `/wp list` for the player's dimension, with a hint |
| `/wp remote` | Now the server picker (was help; help stays at `/wp help remote`) |
| `/wp remote servers` | Removed |
| `/wp remote details <server> <dimension> <list>` | Removed; the waypoint form stays |

Clicking the selected sort mode uses the existing `order` option. No backward-compatible aliases are
kept.

## 17. Code structure

- **Chat kit** in `common/src/main/java/_959/server_waypoint/text/chat/`:
  - **Text pieces**: text, links that run or suggest a command, `[buttons]`, the separator, joining,
    `✔` and `✘` lines, the pager, `… N more` lines, the view and sort rows, the status dot and the
    screen mark. Every click goes through the 256-character guard.
  - **Tooltip builder**: white title, gray details, aqua hints, separator splitting.
  - **`Viewer` record**: permissions, has-the-mod, plain text, dimension, position and yaw, built once
    per command source.
  - **`DimensionStyle`**: names, colours, converted coordinates and order.
- **One builder per screen**:
  - **Local**: the menu, lists (Tree, Lists, Flat, single, empty states, search), the dimension list,
    all dimensions, waypoint details, list details, the pickers, navigation, text display, upload,
    help, results, broadcasts and errors.
  - **Remote**: the server picker, all servers, a server, a dimension, a list, details and teleport.

  Builders take a `Viewer` and the data, and return a component. They hold no command state.
- **Replaced**: `TextButtonBuilder`, `WaypointTextHelper`, `WaypointDetailsTextBuilder`, and the
  menu and help rendering in `WaypointCommandHelp`. Help topic content may stay as data.
- **`CoreWaypointCommand`** keeps parsing and actions. It builds the `Viewer` and calls the
  builders.
- **Paging**: `WaypointListPage` is rewritten for line budgets and whole lists. `RemoteCatalogQuery`
  moves from rows to the same budgets.
- **Dimension types**: a platform method next to `getAvailableDimensionNames` returns each
  dimension's type, from the level's dimension type on mods and the world environment on Paper.
- **Senders**: `PlatformMessageSender` adds a screen's trailing newline for players, and `sendError`
  stops colouring.
- **Remote**: `RemoteWaypointCommand` uses the remote builders and the new grammar.
- Builders follow `docs/tips/adventure-text.md`: lines are built from neutral parents, so click and
  hover events never spill onto neighbouring text.

## 18. Text and translations

- All visible text is translatable. The menu labels move to translation keys.
- Translations use `MessageFormat` placeholders such as `{0}` and escape apostrophes as `''`. Glyphs
  and separators are added by builders, not translations.
- Every new or changed key ships in all six locales: `en_us`, `zh_cn`, `es_es`, `he_il`, `zh_hk` and
  `zh_tw`. Today `es_es`, `he_il`, `zh_hk` and `zh_tw` translate 86 of the 301 keys and fall back to
  English for the rest.
- Keys that lose their last use are removed.
- Fix `waypoint.upload.cooldown` (`%s` becomes `{0}`), and the apostrophes in
  `waypoint.help.upload` and `waypoint.help.edit.example.icon`.

## 19. Documentation

- This feature's README, this spec, the implementation plan in `plans/` and evidence in
  `validation/`.
- The Commands section of the project README.
- `docs/features/cross-server/specs/cross-server-catalog-queries.md` and
  `docs/features/cross-server/cross-server-admin.md`, which show `/wp remote servers`. Release notes
  and the original cross-server plan stay as history.
- `docs/tips/adventure-text.md` gains the chat kit rules: separators, the 256-character guard,
  tooltips and plain-text viewers.

## 20. Risks

- **`/wp_gui` link.** A click that runs a client-side command may not reach client commands on some
  loaders and on 1.21.6+. Fallback: a server-side `/wp gui` that asks the client to open the manager
  with an S2C packet.
- **Translations** longer than English may wrap. The layout test measures every locale and lists the
  wraps the maintainers accepted.
- **Plain-text detection** must follow the viewer, the player a command runs as (15), including
  under `/execute`.

## 21. Testing

- **Builder unit tests** for every screen: content, click commands, tooltips, hidden controls and
  plain-text output.
- **Layout test**: lays out every screen with the vanilla font advances, in English and in every
  other locale. No line may pass 320 px, no screen may pass 20 lines with its trailing blank line
  and no other message may pass 20 lines, except the translated lines the test lists as accepted
  wraps.
- **Screen marks**: the audit's sample data, and the empty states, searches without matches and
  missing pages it doesn't reach, check that exactly the screens are marked. Command tests check
  what players receive: a blank line after screens, none after results and errors.
- **Glyph test**: every glyph used is in the vanilla bitmap font.
- **Brigadier tests** for added and removed commands.
- **Paging tests**: local and remote line budgets, `… N more` lines and continued headings.
- **Plain-text tests**: no essential detail exists only in a hover, click or colour; every piece is
  coloured, and pieces shared with chat have chat's colour.
- **Live click-through** on Paper and Fabric, and the same commands from the console.
