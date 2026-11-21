# Server waypoint update notifications validation

## Automated checks

The settings tests cover the Off default, the three dropdown selections, JSON persistence, and reset.
They exercise the Waypoint Manager group without initializing the renderer. The native check below
exercises the full reset. The translation tests check all six client locales and require one display
name argument in each success message.

The packet tests use the real chunk receiver and a recording notification sink. They verify one
dispatch for each applied Shared and Private add, edit, and removal, along with display names for
renames and removals. Replays, revision gaps, missing targets, snapshots, list changes, lost Private
authorization, and old-generation packets produce no confirmation. An edit result and its delta
produce one confirmation in either arrival order, and the edit future reports success.

The full Fabric 26.1.2 suite passed **1,136 tests**. Forge 1.20.1 and NeoForge 26.3 compiled, covering
the earlier and later toast manager API branches. The active targets stayed at `26.1.2-fabric` and
`26.2-paper`.

```sh
rtk proxy env JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
    ./gradlew :mods:26.1.2-fabric:test :mods:26.1.2-fabric:shadowJar \
    :mods:1.20.1-forge:compileJava :mods:26.3-neoforge:compileJava --console=plain \
    -Porg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
rtk git diff --check
```

## Native Fabric 26.1.2

Two disposable clients connected over loopback to a dedicated server with the same production mod JAR.
Both clients used Fabric Loader 0.19.3 and Fabric API 0.148.2+26.1.2. The local probe invoked real
client handlers, checked native chat history and the toast manager, and captured rendered frames.

| Scenario | Observed result |
| --- | --- |
| Actor Off, observer Chat, `broadcastFeedback=all` | `/wp add`, `/wp edit waypoint … set display-name …`, and `/wp remove` changed both clients’ Shared data. The actor saw command feedback. The observer saw one client confirmation for each change and the server announcements. |
| Actor Off, observer Chat, `broadcastFeedback=off` | After `/wp reload`, the same three operations still generated one confirmation per change for the observer. Server announcements were absent. |
| Broadcast-style system chat only | A `/tellraw` replay with no mutation appeared in chat, left waypoint data unchanged, and generated no confirmation. |
| Waypoint Manager open in Off mode | The visible row model reflected a Shared add, rename, and removal as the updates arrived. No client chat confirmations or toasts were added. |
| Private Toast | `/wp private add`, submission through the native waypoint edit form, and `/wp private remove` generated one compass toast each for the owner. The observer received no Private data or toast. |
| Rejected Private GUI edit | A concurrent `/wp private edit waypoint … set display-name …` advanced the revision before form submission. The stale submission showed the existing form error and generated no additional toast. |
| Restart and full reset | Toast survived a restart. Reconnect sync and a successful Local profile add produced no waypoint update toast. The full client settings reset switched to Off immediately, suppressed the next Private add confirmation, and persisted Off across a second restart. |
| Xaero’s Minimap 25.3.12 | The two preferences independently delivered Chat and Toast while the other preference was Off. Both used the same production compass toast and local chat paths. |

The production Fabric JAR had SHA-256 `e96aa9e75dfa7c83bd41138086d8fdd83cf5b00d5d1564ad93927a30bfc07ffe`.
Native evidence was collected under `/tmp/sw-update-notifications.D0m3dc`, including
`evidence.log` and the client screenshots `shared-chat.png` and `waypoint-toast-message.png`.
The client probe invoked GUI handlers directly. Forge and NeoForge verification was compilation only.

## Broadcast styling follow-up

The updated Fabric 26.1.2 suite passed **1,141 tests**. The formatter tests check colors, scoped
commands, hover events, the removed waypoint's marker, and the original Toast keys and arguments.
Client localization tests verify the new sentences and tooltips in all six locales. The Forge 1.20.1
and NeoForge 26.3 compiles passed again.

On the local dedicated Fabric server, the broadcast and Chat confirmation both rendered the pumpkin
sprite, gold initials, and matching reference colors. The announcement supplied the actor's name and
head. With broadcasts disabled, the observer still saw the styled rename and removal confirmations.
A broadcast-style `/tellraw` produced only its own line. The owner saw the Private confirmation, and
Toast still rendered “Waypoint added: Vista.” The captures and native chat evidence are under
`/tmp/sw-update-chat-style.JKrNfm`.
