# Native chat icons

The platform factories are guarded with Stonecutter `>=1.21.9`. Shared feedback receives
only the `ChatIcons` interface, so common retains Adventure 4.16.0 and older builds do not
link to the object-component API. Item sprites switch atlases at `>=1.21.11`.

The checked-in mapping (`assets/server_waypoint/chat-sprites.json`) contains 1,383 vanilla
item-to-texture identifiers chosen by the 1.21.9 client's item definitions and present in the
1.21.9, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3 client jars. Tinted textures, custom,
VoxelMap, special entity models, and unmapped newer items retain text/initials.

## Automated checks

On 2026-10-03 all checks below passed. Full suites: common 774 tests, Fabric 1.21.9 and 26.1.2
542 tests each, Paper 1.21 27 tests, Paper 1.21.9, 1.21.11 and 26.2 28 tests each; no skipped
tests. Focused `CommandChatIconsTest` and `ModMessageSenderTest` runs passed on Fabric 1.20.1,
1.21.6, 1.21.11, 26.2 and 26.3, and `CommandChatIconsTest` on Forge and NeoForge 1.21.9, where
NeoForge aborts the codec test because its JUnit runtime cannot bootstrap Minecraft. Active
projects remained 26.1.2-fabric and 26.2-paper.

- Common feedback suite: sprite colour and click isolation, plain-text suppression, texture
  mapping (tinted, renamed and display-context items), remote rows, and command delivery of
  player heads in teleport confirmations and broadcasts.
- Fabric 1.21.9, 1.21.11, 26.1.2, 26.2 and 26.3: object serialization, UUID identity, atlas
  selection, leaving objects out for plain-text readers, and conversion through the actual
  Minecraft component codec.
- Fabric 1.20.1 and 1.21.6: factories return no native objects.
- Paper 1.21, 1.21.9, 1.21.11 and 26.2: each build accepts the api-version it is built for;
  from 1.21.9, the console's copy of a player's view leaves chat objects out.
- Compiled icon adapters and message senders in Fabric 1.20.1/1.21.6 and Paper 1.21 contain no
  references to `ObjectComponent` or `ObjectContents`.
- Regenerating the sprite mapping produces a byte-identical file; `git diff --check` passes.

Commands (Fabric and Paper with `--offline`; 26.x targets also pass the JDK 25 path through
`org.gradle.java.installations.paths`):

```sh
./gradlew :common:test :mods:26.1.2-fabric:test :mods:1.21.9-fabric:test
./gradlew :mods:<1.20.1|1.21.6|1.21.11|26.2|26.3>-fabric:test --tests '*CommandChatIconsTest' --tests '*ModMessageSenderTest'
./gradlew :paper:1.21-paper:test :paper:1.21.9-paper:test :paper:1.21.11-paper:test :paper:26.2-paper:test
./gradlew :mods:1.21.9-forge:test :mods:1.21.9-neoforge:test --tests '*CommandChatIconsTest'
python3 tools/generate_chat_sprites.py <1.21.9 … 26.3 client jars> common/src/main/resources/assets/server_waypoint/chat-sprites.json
```

Existing Gradle deprecations, JOML Unsafe warnings, and Forge mapping/unchecked warnings
were emitted; none prevented these checks.

## Live validation still required

- Read `/wp list` and `/wp details waypoint` with diamond, compass, and block icons on 1.21.9
  and a version with the separate items atlas; check line wrapping and sprite colours.
- Click a waypoint sprite/initials to teleport and its name to open details.
- Check a teleport confirmation and an add/edit/remove broadcast for the correct player's head.
- Check custom/VoxelMap icons and plain console feedback remain readable, including the console's
  copy of `/execute as <player> run wp list`.
- Verify older builds retain their previous text appearance.

Automated codec and compilation checks do not establish live skin resolution or visual layout.
