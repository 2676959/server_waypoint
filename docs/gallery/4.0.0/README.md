# Server Waypoint 4.0.0 gallery

Screenshots supplied for the [project description](../../project-description.md), captured on 2026-10-14 from the Minecraft 26.3 Fabric client. The client-settings capture shows Server Waypoint 4.0.0. All original captures are 2560 × 1440 PNGs.

The files below are renamed, byte-for-byte copies. The source screenshots remain in `/Volumes/ssd/minecraft_clients/versions/26.3-Fabric/screenshots/`.

| Gallery image | Original capture | Subject |
| --- | --- | --- |
| [in-world-waypoint-markers.png](in-world-waypoint-markers.png) | `2026-10-14_00.05.34.png` | In-world markers over a village |
| [waypoint-manager.png](waypoint-manager.png) | `2026-10-14_01.35.40.png` | Local waypoint manager with Lantern Market selected |
| [server-navigation-original.png](server-navigation-original.png) | `2026-10-14_01.41.53.png` | Original capture showing all five server navigation methods |
| [cross-server-waypoint-manager.png](cross-server-waypoint-manager.png) | `2026-10-14_01.43.23.png` | Remote building catalogue, destination details and teleport action |
| [cross-server-chat-browser.png](cross-server-chat-browser.png) | `2026-10-14_01.45.14.png` | Four connected remote servers in chat |
| [interactive-chat-waypoint-browser.png](interactive-chat-waypoint-browser.png) | `2026-10-14_01.48.30.png` | Clickable waypoint list with a destination tooltip |
| [interactive-chat-waypoint-details.png](interactive-chat-waypoint-details.png) | `2026-10-14_01.49.38.png` | Waypoint properties, edit controls and navigation actions in chat |
| [shared-waypoint-map-integrations.png](shared-waypoint-map-integrations.png) | `2026-10-14_02.37.18.png` | Shared destination in both minimaps and the world |
| [map-integration-sync-settings.png](map-integration-sync-settings.png) | `2026-10-14_02.37.43.png` | Xaero and VoxelMap automatic and manual synchronization controls |
| [json-styled-waypoint-marker.png](json-styled-waypoint-marker.png) | `2026-10-14_03.02.05.png` | Colourful bold italic display name on an in-world marker |
| [json-styled-waypoint-details.png](json-styled-waypoint-details.png) | `2026-10-14_03.19.40.png` | JSON-styled display name and multicoloured description in the manager |

## Navigation labels

[server-navigation-labeled.png](server-navigation-labeled.png) is a locally annotated, lossless **2560 × 1440 PNG** based on [server-navigation-original.png](server-navigation-original.png). Blender draws the labels using the Minecraft 26.3 client's actual vanilla ASCII bitmap glyphs at an integer scale. The flat, translucent rectangles and six-pixel solid black pointers identify the compass, map, boss bar, action bar and floating label.

The original is preserved. A comparison of decoded PNG pixels verified that every pixel outside the label and pointer mask is identical to the original; the screenshot is not resized or regenerated.

The local annotation tool is intentionally excluded from Git.

## Publishing

The description uses absolute raw GitHub image URLs under the `4.0.0` ref so the same Markdown can be pasted into Modrinth and CurseForge. Publish the gallery and documentation at that ref before saving the platform descriptions, then check both platform previews. Alternatively, upload these images to each platform and replace the URLs with its hosted image URLs.
