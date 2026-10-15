# 服务器路径点 Server Waypoint

[English](README.md) · [中文](README_zh.md)

[![License: MIT](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](https://opensource.org/licenses/MIT)
![Modrinth Version](https://img.shields.io/modrinth/v/server_waypoint?style=flat-square&label=Version)
![both](https://img.shields.io/badge/Environment-Server%26Client-4caf50?style=flat-square)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/server_waypoint?style=flat-square&logo=modrinth&logoColor=%2300AF5C&label=Modrinth%20Downloads&color=%2300AF5C)](https://modrinth.com/plugin/server_waypoint)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1416929?style=flat-square&logo=curseforge&logoColor=%23F16436&label=CurseForge%20Downloads&color=%23F16436)](https://www.curseforge.com/minecraft/mc-mods/server-waypoint)

[![Fabric](https://img.shields.io/badge/1.20.x%20%201.21.x%20%2026.1--26.3-555555?style=flat-square&label=Fabric&labelColor=dbb69b)](https://modrinth.com/plugin/server_waypoint/versions?l=fabric)
[![Forge](https://img.shields.io/badge/1.20.x%20%201.21.x%20%2026.1--26.2-555555?style=flat-square&label=Forge&labelColor=959eef)](https://modrinth.com/plugin/server_waypoint/versions?l=forge)
[![NeoForge](https://img.shields.io/badge/1.20.2--1.20.6%20%201.21.x%20%2026.1--26.3-555555?style=flat-square&label=NeoForge&labelColor=f99e6b)](https://modrinth.com/plugin/server_waypoint/versions?l=neoforge)
[![Paper](https://img.shields.io/badge/1.21.x%20%2026.1--26.3-555555?style=flat-square&label=Paper&labelColor=eeaaaa)](https://modrinth.com/plugin/server_waypoint/versions?l=paper)
[![Velocity](https://img.shields.io/badge/1.20.x%20%201.21.x%20%2026.1--26.3-555555?style=flat-square&label=Velocity&labelColor=8ec9ea)](https://modrinth.com/plugin/server_waypoint/versions?l=velocity)

[![discord-singular](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-singular_vector.svg)](https://discord.com/invite/tKtSSYDkHx)

保存常用地点，与其他玩家分享，轻松找到回去的路。Server Waypoint 支持 Fabric、Forge、NeoForge、Paper、Folia 和 Purpur。可选的 Velocity 插件让你跨服务器使用路径点。

**玩家无需安装客户端模组，即可使用命令、可点击的聊天菜单和导航。** 安装客户端模组后，还能使用路径点管理界面、世界内标记和地图模组集成。你也可以在单人游戏中使用本模组。

本指南适用于 **4.0.0**。新增功能和升级注意事项请参阅[更新日志（英文）](CHANGELOG.md)。

## 主要功能

- 将共享路径点保存在命名列表中，并设置颜色、图标和描述。
- 通过可点击的聊天菜单或客户端路径点管理界面浏览、添加和编辑路径点。
- 使用指南针、地图、Boss 栏、动作栏或悬浮文字导航。
- 将路径点同步到 Xaero 小地图或受支持的 VoxelMap 版本，并将自己的地图路径点上传到服务器。
- 通过 Xaero 世界地图的菜单添加或编辑服务器路径点。
- 管理员启用 Velocity 支持后，浏览其他服务器的路径点并传送前往。

## 各 Minecraft 版本的功能支持

以下范围适用于受支持的 **4.0.0** 构建。请始终使用与你的 Minecraft 版本和平台对应的 JAR 文件；即使某版本处于某项功能的适用范围内，也仍需有对应的构建才能使用。

| 功能 | 平台 | 可用的 Minecraft 版本 | 不可用的版本或限制 |
| --- | --- | --- | --- |
| 聊天中的路径点物品图标 | Fabric、Forge、NeoForge、Paper/Folia/Purpur | 各平台支持范围内的 1.21.9 及以上版本 | 1.21.9 之前使用文字或缩写。只有已建立映射的原版物品/方块纹理可显示为聊天图标；自定义图标和 VoxelMap 图标使用文字或缩写。 |
| 聊天反馈中的玩家头像 | Fabric、Forge、NeoForge、Paper/Folia/Purpur | 各平台支持范围内的 1.21.9 及以上版本 | 1.21.9 之前仅显示文字，不显示玩家头像。 |
| 文本展示实体导航（`text_display`，即悬浮文字） | Fabric、Forge、NeoForge、Paper/Folia/Purpur | 所有受支持的版本：Fabric 1.20–26.3、Forge 1.20–26.2、NeoForge 1.20.2–26.3、Paper/Folia/Purpur 1.21–26.3 | 在受支持的版本中没有额外的版本限制。 |
| VoxelMap 同步、上传和编辑 | Fabric 客户端 | 所有受支持的 Fabric 版本，1.20–26.3 | 需要对应版本的 VoxelMap-Updated 和 Server Waypoint 客户端模组。 |
| VoxelMap 同步、上传和编辑 | Forge 客户端 | 1.21.11、26.1–26.1.2、26.2 | 受支持的 Forge 版本中，1.21.11 之前不可用。需要 VoxelMap-Updated 和 Server Waypoint 客户端模组。 |
| VoxelMap 同步、上传和编辑 | NeoForge 客户端 | 1.21.2–1.21.4、1.21.11、26.1–26.1.2、26.2、26.3 | 1.20.2–1.21.1 和 1.21.5–1.21.10 不可用。需要 VoxelMap-Updated 和 Server Waypoint 客户端模组。 |

聊天图标、玩家头像和悬浮文字导航无需客户端模组。路径点管理界面、世界内客户端标记和 Xaero 集成适用于所有受支持的客户端模组版本；Xaero 世界地图中的操作还需要安装 Xaero 小地图。这些客户端功能可以连接任何受支持的服务端，包括 Paper、Folia 和 Purpur。

## 创建你的第一个路径点

1. 加入服务器或打开单人世界，然后运行 `/wp`。
2. 使用聊天按钮浏览路径点，或在当前位置添加一个路径点。如果还没有路径点列表，请先创建列表。例如，在主世界中运行：

    ```text
    /wp add minecraft:overworld Public
    /wp add 100 64 200 Public Home
    ```

3. 运行 `/wp list`，选择 **Home**，然后使用导航按钮找到它。拥有相应权限时，也可以直接传送。
4. 使用路径点的编辑按钮修改名称、颜色、描述或图标。删除路径点后，会出现一个 **恢复** 链接，可在 10 分钟内使用一次。

名称中有空格时，请加上引号，例如 `"Village square"`。需要命令示例时，可随时运行 `/wp help`。

## 使用客户端路径点管理界面

安装客户端模组后，按 **右 Shift** 或运行 `/wp_gui` 打开管理界面。你可以在 Minecraft 的控制设置中更改快捷键。

选择维度，再选择路径点即可查看详情。你可以搜索、排序或分组显示列表，然后使用按钮添加、编辑、导航或传送。传送需要服务器授予相应权限。

在管理界面中按 **C** 打开客户端设置。你可以调整标记的大小、范围和不透明度，选择颜色主题，或更改地图同步设置。将鼠标悬停在设置上可查看说明；使用重置按钮可恢复默认值。Fabric 用户也可通过 [Mod Menu](https://modrinth.com/mod/modmenu) 打开设置，Forge 和 NeoForge 用户则可通过模组列表打开。

启用跨服务器支持后，切换到 **远程服务器路径点** 并选择服务器。你可以浏览其共享路径点并传送前往；编辑操作需在路径点所属的服务器上进行。

## 使用地图模组

地图模组是可选的，需要与 Server Waypoint 一起安装在客户端：

- [Xaero 小地图（Xaero's Minimap）](https://modrinth.com/mod/xaeros-minimap)：显示共享路径点，并允许上传你的地图路径点。
- [Xaero 世界地图（Xaero's World Map）](https://modrinth.com/mod/xaeros-world-map)：在右键菜单中添加 Server Waypoint 操作。此集成还需要安装 Xaero 小地图。
- [VoxelMap-Updated](https://modrinth.com/mod/voxelmap-updated)：在下列版本中支持共享路径点同步和上传。

| VoxelMap 集成 | 4.0.0 支持的 Minecraft 版本 |
| --- | --- |
| Fabric | 所有受支持的 Fabric 版本 |
| Forge | 1.21.11、26.1–26.1.2、26.2 |
| NeoForge | 1.21.2–1.21.4、1.21.11、26.1–26.1.2、26.2、26.3 |

在多人服务器中，自动同步会让共享路径点保持最新。你的个人地图路径点会单独保留。在单人游戏中，打开世界不会自动替换地图已保存的路径点；需要完整刷新时，请使用客户端设置中的 **立即同步**。已同步的路径点可通过 Server Waypoint 的界面编辑。

**Xaero 的默认路径点集合：** `gui.xaero_default` 是 Xaero 小地图 **默认** 集合的翻译键和内部名称。上传该集合后，会创建一个名为 `gui.xaero_default` 的 Server Waypoint 列表。在 4.0.0 中，将此列表同步回地图时，会创建一个由服务器管理的独立集合，不会合并到你的个人默认集合中。

要将自己的地图路径点复制到服务器，请运行 `/wp upload xaero` 或 `/wp upload voxelmap`。上传需要管理员授予权限。普通上传会添加缺失的路径点，发生冲突时保留服务器上的版本。使用覆盖或删除选项前，请先阅读[上传指南（英文）](docs/tips/server-guide.md#uploading-from-client-map-mods)。

## 从 MCDR 插件迁移路径点

如果你使用 MCDReforged（MCDR）的 **Locations** 或 **LocationMarker** 插件，可以使用 [`locations2waypoints.py`](locations2waypoints.py) 转换其 `locations.json`。需要 Python 3，无需安装额外的软件包。

1. 备份插件的 `locations.json` 和现有的 Server Waypoint 文件，然后停止服务器。
2. 下载脚本，在脚本所在文件夹打开终端，并指定地点文件的路径运行：

    ```sh
    python3 locations2waypoints.py "/path/to/locations.json"
    ```

    也可以直接运行 `python3 locations2waypoints.py`，再按提示输入文件路径。
3. 检查输出中是否有被跳过的条目。脚本会在 `locations.json` 旁创建 `waypoints/` 文件夹，每个维度生成一个 JSON 文件，例如 `minecraft$overworld.json`。每个文件包含一个名为 `locations` 的列表。名称和描述会保留，坐标会向下取整为方块坐标，路径点对所有玩家共享。
4. 将生成的 JSON 文件复制到服务器的路径点文件夹。以下路径均相对于服务器根目录：

    | 服务端平台 | 路径点文件夹 |
    | --- | --- |
    | Fabric | `config/server_waypoint/waypoints/` |
    | Forge 或 NeoForge | `defaultconfigs/server_waypoint/waypoints/` |
    | Paper、Folia 或 Purpur | `plugins/ServerWaypoint/waypoints/` |

    如果目标文件已经存在，请手动将生成的 `locations` 列表合并到其 JSON 数组中；如果已有名为 `locations` 的列表，请使用其他列表名称。直接替换文件会删除其中现有的路径点列表。转换脚本不会合并文件，也会拒绝覆盖它自己已生成的文件。
5. 启动服务器，运行 `/wp list` 查看导入的 `locations` 列表。`/wp reload` 不会重新加载路径点文件。

无效条目和同一维度中的重复名称会被跳过，并输出提示。完成迁移前，请保留源文件并检查这些提示。

## 常用命令

`/waypoint` 和 `/wp` 可以互换使用。大多数操作都可以通过 `/wp` 菜单完成。

| 命令 | 功能 |
| --- | --- |
| `/wp list` | 浏览路径点 |
| `/wp add` | 在当前位置添加路径点 |
| `/wp navigate` | 查看导航目标并切换引导方式 |
| `/wp upload` | 打开地图上传菜单；需要客户端模组 |
| `/wp remote` | 启用跨服务器支持后，浏览已连接的服务器 |
| `/wp help` | 查看命令帮助和示例 |

完整命令格式请参阅[命令参考（英文）](docs/tips/server-guide.md#commands)。

## 服务器管理员指南

默认设置允许玩家添加、编辑、删除路径点以及使用导航。传送和上传默认需要管理员级别的权限；通过上传删除服务器路径点需要单独授予更高的权限。你可以在 `config.json` 中或通过受支持的权限管理工具修改权限。[LuckPerms](https://modrinth.com/plugin/luckperms) 是 Fabric 和 Paper 上的可选依赖；Forge 和 NeoForge 使用配置中的权限等级。

| 服务端平台 | 设置文件 |
| --- | --- |
| Fabric | `config/server_waypoint/config.json` |
| Forge 或 NeoForge | `defaultconfigs/server_waypoint/config.json` |
| Paper、Folia 或 Purpur | `plugins/ServerWaypoint/config.json` |

首次启动时会生成这些文件。修改普通设置或自定义翻译后，请运行 `/wp reload`。跨服务器设置需要重启才能生效。权限、备份和需要重启的设置请参阅[服务端参考（英文）](docs/tips/server-guide.md#server-configurations)。

### 跨服务器传送配置

跨服务器路径点是可选功能，且 **默认关闭**。需要 Velocity、至少两个安装了 Server Waypoint 的独立服务器，以及各端相互匹配的版本。Velocity 插件需要 **Java 25**。玩家无需客户端模组即可使用远程聊天命令。

请按照[分步配置指南（英文）](docs/tips/server-guide.md#cross-server-teleport-setup)连接服务器。指南涵盖同一台机器上的配置，以及不同机器之间的加密连接。[管理员指南（英文）](docs/features/cross-server/cross-server-admin.md)介绍了权限和故障排查。

## 语言与帮助

即使没有安装客户端模组，消息也会按每位玩家的 Minecraft 语言设置显示。内置语言包括英语、简体中文、繁体中文、香港中文、西班牙语和希伯来语。部分消息会回退为英语。

4.0.0 的许多翻译由 AI 生成，可能存在错误。早期社区译者因过去的贡献而获得署名，不对这些错误负责。详情请参阅[翻译致谢与审核状态（英文）](TRANSLATOR_CREDITS.md)，也欢迎在 [Crowdin](https://crowdin.com/project/server-waypoint) 上帮助翻译。

- [Discord](https://discord.com/invite/tKtSSYDkHx) — 提问和寻求社区帮助。
- [GitHub issues](https://github.com/2676959/server_waypoint/issues) — 报告问题时，请提供 Minecraft 版本、平台和 Server Waypoint 版本。
- [服务器配置与命令参考（英文）](docs/tips/server-guide.md) — 面向管理员的详细说明。
- [开发者文档（英文）](docs/README.md) — 功能规格与验证记录。
