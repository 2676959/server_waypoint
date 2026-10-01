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

## Chat kit

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
  command block, decided by the receiver, not the executing entity. They get no clicks, tooltips or
  controls: `Chat.link` keeps the label, `Chat.control` and `Chat.button` return null, and builders
  write out identifiers and coordinates instead.
- **One trailing newline.** The platform sender adds it for players (`PlatformMessageSender.forPlayer`);
  builders never end a message with a newline.
- **Layout.** `ChatFont` holds the vanilla font advances. `ChatAssert.assertFitsChat` checks a
  message against the 320 px width, the 20-line window, bold text and the vanilla glyphs, and
  `ScreenAuditTest` checks every screen.
