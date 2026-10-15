# Static special item chat sprites

The generator previously accepted ordinary item models only. Beds, chests, shulker boxes and
other special renderers therefore had no mapping, and `/wp list` retained only their initials.

Static special renderers now resolve their base model's textures through the existing parent,
alias and shared-client checks. The mapping adds 54 identifiers (1,437 total), including every
bed colour, every shulker box colour, chests, conduits, shields and copper golem statues.
Heads, banners and unknown special renderers remain excluded because their base particles do
not describe their dynamic appearance.

These are representative flat textures: beds show coloured wool, chests their material and
shulker boxes their shell. This does not provide a rendered inventory icon or require a resource
pack. The shared mapping applies to both Paper and mod-loader feedback.

## Automated validation

- Generator regression: bed parent/alias resolution and nested chest selection failed before
  the fix, then passed. Missing newer-client textures and dynamic/unknown models are excluded.
- All tool tests passed: 29 tests from `python3 -B -m unittest discover -s tools -p 'test_*.py'`.
- Common suite: 643 tests passed, including all 16 bed and shulker box colours.
- Fabric 1.21.9 and 1.21.11: focused `CommandChatIconsTest` passed; production adapters compiled.
- Paper 1.21.9 and 1.21.11: suites passed; production adapters compiled.
- Regeneration is identical to the checked-in mapping. Every selected texture exists in the
  1.21.9, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3 client jars.
- `git diff --check` passed. Active projects stayed 26.1.2-fabric and 26.2-paper.

```sh
python3 -B -m unittest discover -s tools -p test_generate_chat_sprites.py
./gradlew :common:test :mods:1.21.9-fabric:test --tests '*CommandChatIconsTest' :paper:1.21.9-paper:test --offline
./gradlew :mods:1.21.11-fabric:test --tests '*CommandChatIconsTest' :paper:1.21.11-paper:test --offline
```

Existing Xaero and Gradle deprecation warnings did not prevent these checks.

## Live validation

Visual appearance remains unverified. After rebuilding the server plugin/mod, read `/wp list`
and `/wp details waypoint` with bed, chest and shulker box icons. Confirm the representative
texture, untinted colour, existing initials, teleport/details clicks and console output.
