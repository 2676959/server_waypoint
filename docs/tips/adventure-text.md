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
