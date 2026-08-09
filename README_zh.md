# 服务器路径点 Server Waypoint

[中文](README_zh.md) [English](README.md)

[![License: MIT](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](https://opensource.org/licenses/MIT)
![Modrinth Version](https://img.shields.io/modrinth/v/server_waypoint?style=flat-square&label=Version)
![both](https://img.shields.io/badge/Environment-Server%26Client-4caf50?style=flat-square)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/server_waypoint?style=flat-square&logo=modrinth&logoColor=%2300AF5C&label=Modrinth%20Downloads&color=%2300AF5C)](https://modrinth.com/plugin/server_waypoint)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1416929?style=flat-square&logo=curseforge&logoColor=%23F16436&label=CurseForge%20Downloads&color=%23F16436)](https://www.curseforge.com/minecraft/mc-mods/server-waypoint)

[![Fabric](https://img.shields.io/badge/1.20.x%20%201.21.x%20%2026.1--26.2-555555?style=flat-square&label=Fabric&labelColor=dbb69b)](https://modrinth.com/plugin/server_waypoint/versions?l=fabric)
[![Forge](https://img.shields.io/badge/1.20.x%20%201.21.x%20%2026.1--26.2-555555?style=flat-square&label=Forge&labelColor=959eef)](https://modrinth.com/plugin/server_waypoint/versions?l=forge)
[![NeoForge](https://img.shields.io/badge/1.20.2--1.20.6%20%201.21.x%20%2026.1--26.2-555555?style=flat-square&label=NeoForge&labelColor=f99e6b)](https://modrinth.com/plugin/server_waypoint/versions?l=neoforge)
[![Paper](https://img.shields.io/badge/1.21.x%20%2026.1--26.2-555555?style=flat-square&label=Paper&labelColor=eeaaaa)](https://modrinth.com/plugin/server_waypoint/versions?l=paper)

[![discord-singular](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-singular_vector.svg)](https://discord.com/invite/tKtSSYDkHx)
[![crowdin](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/translate/crowdin_vector.svg)](https://crowdin.com/project/server-waypoint)

管理路径点并自动将其同步到其他玩家的客户端，兼容 Xaero 小地图 (Xaero's Minimap)。

## 主要功能
- 从服务端自动同步路径点。
- 自定义路径点渲染。
- 允许玩家通过图形界面（需要安装客户端）和命令（只需安装服务器）管理路径点。
- 命令自动补全。
- `/wp <选项>` 命令支持自定义权限。兼容 [LuckPerms](https://modrinth.com/plugin/luckperms)。
- 支持从 Xaero 小地图的聊天分享消息中便捷添加路径点，无需在客户端安装本模组。

## 依赖项
必需：
  - [Fabric API](https://modrinth.com/mod/fabric-api)
  
可选：
  - [LuckPerms](https://modrinth.com/plugin/luckperms)
  - [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)

## 快捷键
- 按下 `右 Shift` 或使用 `/wp_gui` 打开路径点管理界面。
- 在路径点管理界面按下 `T` 可传送至鼠标悬停的路径点（需要`/wp tp`命令权限）。
- 在路径点管理界面按下 `C` 可打开客户端配置界面。

## 命令
- `/wp add` 添加新路径点。无法添加同名路径点。会提示用户使用 `/wp edit` 替换现有路径点。
  - `/wp add <维度> <列表>` 添加一个路径点列表。
- `/wp download` 下载路径点并添加到 Xaero 小地图（需客户端安装本模组才生效）。
- `/wp upload` 从执行玩家客户端上的 Xaero 小地图导入路径点。冲突、强制覆盖和删除行为详见[从 Xaero 小地图上传](#从-xaero-小地图上传)。
- `/wp edit` 编辑路径点。
- `/wp list` 列出当前维度中的路径点。可使用 `all`、维度，或维度加列表名称来更改范围。结果按照服务端配置的每页数量分页（默认 10 个），并提供可点击的排序和翻页按钮。
  - 添加 `search <查询内容>` 可按路径点名称筛选。
  - 添加 `sort <default|name|distance|color>`；使用非默认排序时，还可选用 `order <ascending|descending>` 对结果排序。
  - 添加 `page <页码>` 和/或 `limit <1-100>` 可选择页码或更改每页数量。选项顺序为 `search`、`sort`、`order`、`page`、`limit`；包含空格的值，以及与选项名称相同的列表名称，需要加引号。
- `/wp reload` 重载 `config.json` 和 `\config\server_waypoint\lang` 目录下的翻译文件。`sendXaerosWorldId` 特性需要重启服务器才能生效。
- `/wp remove` 按名称删除路径点。显示已删除的路径点，点击该消息可恢复该路径点。
  - `/wp remove <维度> <列表>` 删除一个空的路径点列表。
- `/wp tp` 将执行该命令的玩家传送至指定路径点。

## 从 Xaero 小地图上传

上传由服务器发起，但读取的是执行命令玩家客户端中的 Xaero 数据。客户端必须已安装并正确加载 Server Waypoint 和 Xaero 小地图。服务器只接受命令所选维度以及可选列表/路径点范围内的数据。

只导入普通、已启用且非临时的 Xaero 路径点。上传会同步路径点名称、缩写、坐标、Xaero 颜色、yaw 和本地/全局可见性。Xaero 不保存 Server Waypoint 的显示名称、关键词和描述；更新已有路径点时会保留这些仅存在于服务器的字段。新路径点使用路径点名称作为显示名称，关键词和描述为空。

所有模式都支持相同的可选范围：

- 不指定选择器：命令执行者可用的所有服务器维度。
- `<维度>`：该维度中的所有路径点集。
- `<维度> <列表>`：一个 Xaero 路径点集。
- `<维度> <列表> <路径点>`：一个路径点。

### 普通上传 / force server

`/wp upload [<维度> [<列表> [<路径点>]]]` 与 `/wp upload force server [<维度> [<列表> [<路径点>]]]` 行为相同：添加服务器上缺少的路径点；相同路径点保持不变；如果同名路径点的 Xaero 支持属性不同，则保留服务器版本并报告冲突。不会删除任何数据。

### Force local

`/wp upload force local [<维度> [<列表> [<路径点>]]]` 会添加缺少的路径点，并用客户端值替换冲突路径点中 Xaero 支持的属性。服务器专有的显示名称、关键词和描述会被保留。不会删除任何数据。

### Force local delete

`/wp upload force local delete [<维度> [<列表> [<路径点>]]]` 会先执行 `force local`，然后删除 Xaero 中不存在的服务器数据，使所选范围与本地数据一致：

- 世界范围：在所有所选维度中删除缺少的路径点集和路径点。
- 维度范围：在该维度中删除缺少的路径点集和路径点。
- 列表范围：从该路径点集中删除缺少的路径点；如果 Xaero 路径点集不存在，则删除整个服务器列表。
- 路径点范围：仅在本地不存在时删除所选服务器路径点。

已禁用、临时或非普通类型的 Xaero 路径点不会被导出。因此在 `force local delete` 中它们会被视为不存在，并可能导致对应服务器路径点被删除。仅当所选 Xaero 范围应作为权威副本时使用此模式。

上传使用 `server_waypoint.command.upload`（默认原版权限等级 2）。破坏性的删除模式还需要 `server_waypoint.command.upload.delete`（默认等级 4）。

## 翻译
此模组发送的消息和命令反馈将根据玩家客户端的语言设置自动翻译。此功能完全在服务器端运行；玩家无需在客户端安装此模组即可看到翻译后的消息。目前，该模组支持英语和简体中文翻译。如果您有兴趣，可以在 [Crowdin](https://crowdin.com/project/server-waypoint) 上添加翻译，帮助我们完善翻译。

- ### 添加翻译
  将语言文件放置在目录 `<minecraft_root>\config\server_waypoint\lang\` 下。模组将在服务器启动时加载它们，如果服务器已运行，请使用 `/wp reload`。
  
- ### 创建语言文件
  请遵循 [`en_us.json`](./common/src/main/resources/lang/en_us.json) 或 [`zh_cn.json`](./common/src/main/resources/lang/zh_cn.json) 中的格式。

  使用[有效的语言代码](https://minecraft.wiki/w/Language#Languages)命名语言文件。
  
- ### 翻译顺序
  如果您添加的翻译文件使用的语言代码与内置语言相同，此模组会首先尝试在您添加的文件中查找翻译键。如果找不到该键，则会回退到使用内置翻译。如果您想使用自己的翻译版本，只需添加您自己的文件并覆盖内置翻译即可轻松实现。

社区翻译者署名与署名 pull request 指南请见 [`TRANSLATOR_CREDITS.md`](./TRANSLATOR_CREDITS.md)。

## 翻译鸣谢
感谢社区翻译者帮助 Server Waypoint 支持更多语言，让更多玩家可以使用本模组。

以下示例条目是占位内容，应在之后替换为真实的翻译贡献者。

- Example Translator
- Sample Localizer https://example.com/sample-localizer
- Demo Contributor https://example.com/demo-contributor

## 路径点
- #### 保存路径
  服务端：
  
  `<minecraft_root>\config\server_waypoint\waypoints\`

  客户端：

  `<minecraft_root>\saves\<world_name>\server_waypoint\waypoints\`
- #### 文件格式
  所有路径点均保存在 JSON 文件中。每个 JSON 文件包含一个维度的所有路径点，文件名为该维度转换后的完整注册名。
  例如，主世界的所有路径点存储在 `minecraft$overworld.json` 中。

## 服务端配置
配置文件存储在 `<minecraft_root>\config\server_waypoint\config.json`。

部分对 `config.json` 的更改将在服务器重启后生效。

- ### 默认每页数量 Default Page Limit
  设置 `/wp list` 命令未指定 `limit` 时每页显示的路径点数量。有效范围为 `1-100`，默认值为 `10`。使用 `/wp reload` 后此设置即可生效。

  ```json5
  {
    "defaultPageLimit": 10
  }
  ```
- ### 默认导航方式 Default Navigation Selection
  设置新会话在使用 `/wp navigate <dimension> <list> <waypoint>` 且未指定 `using` 时启用的导航方式。可用值为 `compass`、`map`、`bossbar`、`actionbar` 和 `all`，默认值为 `actionbar`。

  ```json5
  {
    "defaultNavigationSelection": "actionbar"
  }
  ```
- ### 命令权限 Command Permission
  修改执行命令所需的[原版权限等级](https://minecraft.wiki/w/Permission_level)。
  
  这将被 [LuckPerms](https://modrinth.com/plugin/luckperms) 设置的权限覆盖。

  上传默认需要等级 2。具有破坏性的 `force local delete` 需要等级 4，也可通过 `server_waypoint.command.upload.delete` 单独授予；普通上传使用 `server_waypoint.command.upload`。
  
  默认值：
  ```json5
  {
    "CommandPermission": {
      // /wp add
      "add": 0,
      // /wp edit
      "edit": 0,
      // /wp remove
      "remove": 0,
      // /wp navigate
      "navigate": 0,
      // /wp tp
      "tp": 2,
      // /wp reload
      "reload": 2,
      // /wp upload
      "upload": 2,
      // /wp upload force local delete
      "uploadDelete": 4
    }
  }
- ### 功能 Features
  - #### addWaypointFromChatSharing
    默认值：`true`
    
    提示用户添加他们在聊天中分享的路径点。需要`/wp add`权限。
    
    示例：
    ```json5
     {
       "Features": {
         "addWaypointFromChatSharing": true
       }
     }
     ```
  - #### sendXaerosWorldId
    默认值：`true`
    
    向客户端发送数据包，以帮助 Xaero 地图模组识别服务器。

    **如果在[Leaves](https://leavesmc.org/)服务端上启用了 `xaero-map-protocol` 或其他插件/模组提供了类似功能，则应将此项设置为 `false`。**
    Example:
    ```json5
     {
       "Features": {
         "sendXaerosWorldId": true
       }
     }
     ```

## 客户端配置
- #### 启用路径点渲染
  默认值：`true`
- #### 路径点缩放比例（百分比）
  默认值：`100`
- #### 路径点垂直偏移（百分比）
  默认值：`0`
- #### 路径点背景透明度
  默认值：`128`
- #### 局部路径点渲染视距（区块）
  默认值：`12`
- #### 自动同步至Xaero的小地图模组
  默认值：`true`
  
  需要安装Xaero的小地图模组。服务端管理的 Xaero 路径点集合使用内部 `sw␟` 前缀，因此自动同步仅更新这些集合并保留个人集合；上传时会将这些管理名称映射回服务端的列表和路径点名称。
- #### 手动同步至Xaero的小地图模组
  默认值：`无`

  手动触发，需要安装Xaero的小地图模组。
  
  此操作将替换Xaero的小地图中所有与服务器列表同名的路径点集合。
  - 保留的内容：
  名称唯一、且在服务器上不存在的集合。
  - 丢失的内容：
  您在与服务器同名的集合中添加的所有路径点。您自行创建的、但恰好与服务器列表重名的集合。
