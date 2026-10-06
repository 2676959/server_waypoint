# Adventure Text Component Tips

Adventure component styles and events are inherited by descendant components. This matters when
building clickable feedback messages: if text is appended directly to a button component, the
button becomes that text's parent, so its bold decoration, color, hover event, and click event can
overflow into the text that follows it.

Build each line from a neutral parent and append buttons and ordinary text as siblings:

```java
Component line = Component.empty();
line = line.append(editButton).appendSpace();
line = line.append(propertyText);
```

Compose immutable `Component` values for Paper feedback instead of calling
`TextComponent.Builder.build()`. Adventure versions can differ in that builder
method's binary return type, causing a `NoSuchMethodError` on the server.

Do not build the same line by appending ordinary text to the button:

```java
Component line = editButton.appendSpace().append(propertyText);
```

For text after a styled control, set any required decoration state explicitly, such as
`TextDecoration.BOLD` to `TextDecoration.State.FALSE`. Likewise, give independently styled child
segments their own colors. For example, a hex code following a colored swatch must set its neutral
text color explicitly instead of inheriting the swatch color.

Regression tests for interactive feedback should verify both component order and effective
inherited state: the property text must not inherit the button's click event, while its decoration
and color states must match the intended presentation.

## Minecraft text in the mods

The mods convert between vanilla and Adventure text through JSON: `TextHelper.toMinecraft` and
`ModMessageSender.toVanillaText` go to vanilla, `TextHelper.toAdventure` comes back. All of them use
`TextHelper.JSON`, never `GsonComponentSerializer.gson()` directly: before 1.20.3 vanilla reads a hover
entity's id only as a string, and Adventure's default writes the int array that later versions accept as
well, so a message with a player's hover fails to convert on 1.20.1 and 1.20.2.

Bring text that comes from Minecraft, such as a player's display name, in with `toAdventure`, so it keeps its
colours, hover, click and insertion. `Component.text(text.getString())` drops all of them. `TextHelperTest` and
`ModMessageSenderTest` check both directions wherever the unit-test runtime can bootstrap Minecraft: every
Fabric and Forge target, and NeoForge up to 1.21.2. NeoForge's runtime cannot from 1.21.3 on, so they skip
there (`MinecraftTestRuntime` keeps the entity-hover cases from failing in a half-bootstrapped JVM).

## Chat kit

### Native chat icons

`Viewer.icons()` supplies platform-owned `ChatIcons`. Both platform command adapters select
their implementation using Stonecutter `>=1.21.9`; older builds use `ChatIcons.NONE`, with no
references to Adventure's object API in their compiled adapters. Keep common on its existing
Adventure baseline. Plain-text viewers always use `NONE`; when the console, RCON or a command
block reads a copy of a player's view (`/execute as`), the platform senders pass it through
`ChatIcons.withoutIcons`, which leaves the objects out.

Waypoint references keep their initials and teleport/details actions and prepend a white
(untinted) sprite when the waypoint has a mapped item icon. Player references in command
broadcasts and teleport confirmations prepend UUID-based player heads; broadcasts name the player
by account name on every build, and teleport confirmations by display name. The space after a
sprite or head is the object's child, so leaving the object out takes the space with it.
Cross-server arrival feedback passes the destination platform's `ChatIcons` through
`BackendRuntime.arrivalMessage` to `RemoteScreens.arrival`, so it uses the same waypoint sprites.

Chat sprites are flat textures, not rendered item models.
`assets/server_waypoint/chat-sprites.json` contains 1,383 representative vanilla textures derived
from the 1.21.9 client item/model definitions and present in every supported newer client. Dynamic
items use a representative frame; blocks use a face; display-context items use their GUI model.
Textures an item tints (potions, leaves, grass, dyed leather), entity-rendered items, unmapped
newer items, custom items, and VoxelMap icons retain initials/text. No resource pack is required.
The platform factories use the blocks atlas through 1.21.10 and the separate items atlas from
1.21.11 onward. Regenerate the mapping with `python3 tools/generate_chat_sprites.py
<1.21.9 client.jar> <every newer supported client.jar...>
common/src/main/resources/assets/server_waypoint/chat-sprites.json`: the first jar's item
definitions choose each texture, and every jar must contain it. The generator copies identifiers
only, not texture images.

### Feedback composition

Command feedback is built from the pieces in `common/src/main/java/_959/server_waypoint/text/chat/`
(see the [command feedback design](../features/command-feedback/specs/2026-10-01-command-feedback-design.md)):

- **Separators.** `Chat.SEPARATOR` (` · ` in dark gray) and `Chat.CRUMB` (` › `) are components of
  their own. Translations never contain separators or glyphs; builders join the pieces with
  `Chat.join` and `Chat.concat`, which also skip pieces that are null.
- **The 256-character guard.** Every click goes through `Click`. A command longer than 256
  characters loses its click, because the client refuses it, keeps its text, and its tooltip loses
  the click hint.
- **Tooltips** come from `Tooltip`: a white first line naming the object or the action, gray detail
  lines, and aqua click hints and typing instructions last.
- **Plain-text viewers.** `Viewer.plainText()` is true when the output goes to the console, RCON or a
  command block and no player reads it. The feedback is the view of the player a command runs as, so
  `/execute as` from the console is that player's view, and the console gets a copy under a
  `Chat.viewedAs` line (`PlatformMessageSender.forCommander`, spec 15). Plain-text viewers get no
  clicks, tooltips or controls: `Chat.link` keeps the label, `Chat.control` and `Chat.button` return
  null, and builders write out identifiers and coordinates instead.
- **Plain text keeps the colours of chat.** Every piece has the colour players see, and text written
  out in place of a link, a tooltip or a distance takes that piece's colour: `… N more: <command>` is
  aqua, row coordinates are gray, help usages are coloured like their links and `Tooltip.textLines()`
  is white, then gray. Inside a link that sets its own colour, use `DimensionStyle.label`, since
  `DimensionStyle.name` carries the dimension colour. Paper's console shows the colours; the Fabric and
  NeoForge console and RCON print `Component.getString()`, which drops them. `ScreenAuditTest` checks
  that every plain-text piece is coloured and that pieces shared with chat have chat's colour.
- **Screens.** A screen is structured feedback that players read apart from the next message: the
  menu, help, lists, details, pickers, navigation, upload and remote screens. Builders mark their
  screens, with `Chat.screen` or `ChatLines.buildScreen` for a message built line by line; results,
  errors, broadcasts and prompts stay unmarked and read like chat lines. The platform sender ends a
  marked message with one newline for players (`PlatformMessageSender.forPlayer`); builders never end
  a message with a newline. Mark a path only when it returns a screen: an empty state or a search
  without matches is one, a page that doesn't exist is an error line. The mark is an empty piece
  after the screen, found anywhere in a message, so a hint above a list screen makes the whole
  message a screen. `ScreenAuditTest` and `ScreenMarksTest` check that exactly the screens are
  marked, so a new screen builder goes into their sample data.
- **Layout.** `ChatFont` holds the advances of every glyph in the vanilla bitmap font, accented
  Latin and Hebrew included; anything else, such as Chinese, counts as 9 px. `ChatAssert.assertFitsChat`
  checks a message against the 320 px width, the 20-line window, bold text and the vanilla glyphs, and
  `ScreenAuditTest` checks every screen, in every locale too (`ChatAssert.fitProblems`, which leaves
  the glyph check to English). A translation can wrap where the English line fits, so measure it:
  shorten it, or accept the wrap by adding the line the test reports to `ScreenAuditTest.ACCEPTED_WRAPS`.
