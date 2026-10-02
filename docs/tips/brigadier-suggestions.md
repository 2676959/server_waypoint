# Brigadier suggestions through redirects

Suggestion providers must handle redirected commands, such as:

```text
/execute as 7c00 run wp remote tp creative "minecraft:overworld"
```

Brigadier can pass the outer command context to a suggestion provider even though
the arguments for `wp` belong to a child context. Reading `getString(context,
"remote server")` from the outer context then throws:

```text
java.lang.IllegalArgumentException: No such argument 'remote server' exists on this command
```

This is a tab-completion failure, before the teleport command executes.

For waypoint suggestion providers, resolve the final child context before reading
the command's arguments:

```java
context = context.getLastChild();
String server = getString(context, "remote server");
```

Use `getLastChild()` rather than a single `getChild()` so nested redirects such as
`execute ... run execute ... run wp ...` also work. For direct commands,
`getLastChild()` returns the original context. Keep using the supplied
`SuggestionsBuilder` so replacement offsets remain relative to the full input.
Do not hide this error by catching `IllegalArgumentException` and returning empty
suggestions: the parsed arguments exist, but in a different context.

The shared provider in
[`RemoteWaypointCommand`](../../common/src/main/java/_959/server_waypoint/command/RemoteWaypointCommand.java)
applies this to `remote list`, `remote details`, and `remote tp`. Existing local
suggestion helpers in
[`CoreWaypointCommand`](../../common/src/main/java/_959/server_waypoint/command/CoreWaypointCommand.java)
also use the final child context.

## Regression checks

Build a Brigadier test tree with an `execute as <target>` fork and a `run` redirect
to the dispatcher root. Compare direct and redirected suggestions at each argument
depth, including nested redirects and incomplete quoted names. Assert expected
identities as well as equality so two empty results cannot accidentally pass.

[`RemoteWaypointCommandTest`](../../common/src/test/java/_959/server_waypoint/command/RemoteWaypointCommandTest.java)
contains this coverage in `redirectedSuggestionsResolveArgumentsAtEveryRemoteDepth`.
Run it with:

```sh
./gradlew :common:test --tests '*RemoteWaypointCommandTest'
```

These tests verify Brigadier parsing and completion behavior; they do not replace
live Paper command testing.
