# Special item and block chat sprites

These 54 vanilla item identifiers now have representative flat textures in `/wp` feedback.
The tables below come from `common/src/main/resources/assets/server_waypoint/chat-sprites.json`.
They show the exact emitted sprite identifier. These textures do not reproduce the full inventory model.
No resource pack is required.

| Group | Entries |
| --- | ---: |
| Beds | 16 |
| Shulker boxes | 17 |
| Chests | 11 |
| Copper golem statues | 8 |
| Other special items | 2 |
| **Total** | **54** |

## Beds

| Item/block identifier | Chat sprite |
| --- | --- |
| `minecraft:black_bed` | `minecraft:block/black_wool` |
| `minecraft:blue_bed` | `minecraft:block/blue_wool` |
| `minecraft:brown_bed` | `minecraft:block/brown_wool` |
| `minecraft:cyan_bed` | `minecraft:block/cyan_wool` |
| `minecraft:gray_bed` | `minecraft:block/gray_wool` |
| `minecraft:green_bed` | `minecraft:block/green_wool` |
| `minecraft:light_blue_bed` | `minecraft:block/light_blue_wool` |
| `minecraft:light_gray_bed` | `minecraft:block/light_gray_wool` |
| `minecraft:lime_bed` | `minecraft:block/lime_wool` |
| `minecraft:magenta_bed` | `minecraft:block/magenta_wool` |
| `minecraft:orange_bed` | `minecraft:block/orange_wool` |
| `minecraft:pink_bed` | `minecraft:block/pink_wool` |
| `minecraft:purple_bed` | `minecraft:block/purple_wool` |
| `minecraft:red_bed` | `minecraft:block/red_wool` |
| `minecraft:white_bed` | `minecraft:block/white_wool` |
| `minecraft:yellow_bed` | `minecraft:block/yellow_wool` |

## Shulker boxes

| Item/block identifier | Chat sprite |
| --- | --- |
| `minecraft:black_shulker_box` | `minecraft:block/black_shulker_box` |
| `minecraft:blue_shulker_box` | `minecraft:block/blue_shulker_box` |
| `minecraft:brown_shulker_box` | `minecraft:block/brown_shulker_box` |
| `minecraft:cyan_shulker_box` | `minecraft:block/cyan_shulker_box` |
| `minecraft:gray_shulker_box` | `minecraft:block/gray_shulker_box` |
| `minecraft:green_shulker_box` | `minecraft:block/green_shulker_box` |
| `minecraft:light_blue_shulker_box` | `minecraft:block/light_blue_shulker_box` |
| `minecraft:light_gray_shulker_box` | `minecraft:block/light_gray_shulker_box` |
| `minecraft:lime_shulker_box` | `minecraft:block/lime_shulker_box` |
| `minecraft:magenta_shulker_box` | `minecraft:block/magenta_shulker_box` |
| `minecraft:orange_shulker_box` | `minecraft:block/orange_shulker_box` |
| `minecraft:pink_shulker_box` | `minecraft:block/pink_shulker_box` |
| `minecraft:purple_shulker_box` | `minecraft:block/purple_shulker_box` |
| `minecraft:red_shulker_box` | `minecraft:block/red_shulker_box` |
| `minecraft:shulker_box` | `minecraft:block/shulker_box` |
| `minecraft:white_shulker_box` | `minecraft:block/white_shulker_box` |
| `minecraft:yellow_shulker_box` | `minecraft:block/yellow_shulker_box` |

## Chests

| Item/block identifier | Chat sprite |
| --- | --- |
| `minecraft:chest` | `minecraft:block/oak_planks` |
| `minecraft:copper_chest` | `minecraft:block/copper_block` |
| `minecraft:ender_chest` | `minecraft:block/obsidian` |
| `minecraft:exposed_copper_chest` | `minecraft:block/exposed_copper` |
| `minecraft:oxidized_copper_chest` | `minecraft:block/oxidized_copper` |
| `minecraft:trapped_chest` | `minecraft:block/oak_planks` |
| `minecraft:waxed_copper_chest` | `minecraft:block/copper_block` |
| `minecraft:waxed_exposed_copper_chest` | `minecraft:block/exposed_copper` |
| `minecraft:waxed_oxidized_copper_chest` | `minecraft:block/oxidized_copper` |
| `minecraft:waxed_weathered_copper_chest` | `minecraft:block/weathered_copper` |
| `minecraft:weathered_copper_chest` | `minecraft:block/weathered_copper` |

## Copper golem statues

All statue variants share the base model's `minecraft:block/copper_block` texture,
including oxidized and waxed variants.

| Item/block identifier | Chat sprite |
| --- | --- |
| `minecraft:copper_golem_statue` | `minecraft:block/copper_block` |
| `minecraft:exposed_copper_golem_statue` | `minecraft:block/copper_block` |
| `minecraft:oxidized_copper_golem_statue` | `minecraft:block/copper_block` |
| `minecraft:waxed_copper_golem_statue` | `minecraft:block/copper_block` |
| `minecraft:waxed_exposed_copper_golem_statue` | `minecraft:block/copper_block` |
| `minecraft:waxed_oxidized_copper_golem_statue` | `minecraft:block/copper_block` |
| `minecraft:waxed_weathered_copper_golem_statue` | `minecraft:block/copper_block` |
| `minecraft:weathered_copper_golem_statue` | `minecraft:block/copper_block` |

## Other special items

| Item/block identifier | Chat sprite |
| --- | --- |
| `minecraft:conduit` | `minecraft:block/conduit` |
| `minecraft:shield` | `minecraft:block/dark_oak_planks` |

## Special models that still retain initials/text

- All 16 coloured banners: their template particle is wood and does not reflect the banner colour or patterns.
- Player heads, creeper heads, dragon heads, piglin heads, zombie heads, skeleton skulls and wither skeleton skulls: their skins are not represented by a suitable base texture.
- Decorated pots: their base texture belongs to the entity texture layout rather than the supported item/block textures.
- Unknown special renderers are excluded by the generator.

See [validation and live checks](validation/2026-10-06-special-chat-sprites.md).
