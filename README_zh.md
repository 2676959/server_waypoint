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
- `/wp add` 添加新路径点。同一列表中的标识符不能重复。
  - `/wp add <维度> <列表标识符>` 添加一个路径点列表。
- `/wp download` 下载路径点并添加到 Xaero 小地图（需客户端安装本模组才生效）。
- `/wp details list <维度> <列表标识符>` 和 `/wp details waypoint <维度> <列表标识符> <路径点标识符>` 显示全部属性及可用操作。
- `/wp edit list ...` 和 `/wp edit waypoint ...` 每次设置一个属性，或清除一个可选属性。完整命令格式请运行 `/wp help edit`。
  - `/wp edit waypoint <维度> <列表> <路径点> set icon "minecraft:diamond"` 选用物品图标；使用 `clear icon` 恢复首字母标记。`voxelmap:star` 等 `voxelmap:` ID 可选用 VoxelMap 内置图片。`/wp add` 也接受在可选关键词和描述之后添加 `icon "<namespace:path>"`。
- `/wp upload <xaero|voxelmap>` 从执行玩家客户端上所选的地图模组导入路径点。冲突、强制覆盖和删除行为详见[从客户端地图模组上传](#从客户端地图模组上传)。
- `/wp list` 列出当前维度中的路径点。可使用 `all`、维度，或维度加列表名称来更改范围。结果按照服务端配置的每页数量分页（默认 10 个），并提供可点击的排序和翻页按钮。
  - 添加 `search <查询内容>` 可按路径点名称筛选。
  - 添加 `sort <default|name|distance|color>`；使用非默认排序时，还可选用 `order <ascending|descending>` 对结果排序。
  - 添加 `page <页码>` 和/或 `limit <1-100>` 可选择页码或更改每页数量。选项顺序为 `search`、`sort`、`order`、`page`、`limit`；包含空格的值，以及与选项名称相同的列表名称，需要加引号。
- `/wp remote servers [page <页码> [limit <1-100>]]` 显示缓存中的远程服务器标识及可用状态。
- `/wp remote list [<服务器> [<维度> [<列表>]]]` 浏览缓存中的远程路径点。其 `search`、`sort`/`order`、`page`、`limit` 和 `view tree|flat` 选项格式与 `/wp list` 相同。标识符必须精确匹配；包含空格或与选项名称相同的名称需要加引号。跨服务器距离不可用，因此按距离排序时会显示提示。
  - 结果仅供浏览，通过普通服务端聊天显示。过期数据会有标记；目录不可用与成功发布的空目录会分别显示。运行 `/wp help remote` 查看帮助。
  - 启用跨服务器配置后开始同步目录。参见[Velocity 运行时配置](docs/features/cross-server/specs/cross-server-velocity-runtime.md)和[远程目录查询](docs/features/cross-server/specs/cross-server-catalog-queries.md)。
- `/wp remote tp <服务器> <维度> <列表> <路径点>` 使用缓存中的精确标识符请求跨服务器传送（含空格的名称须加引号）。过期或不存在的目标会在准备阶段前被拒绝；目的地准备完成且重新检查权限通过前，玩家仍留在来源服务器。参见[远程传送发起流程](docs/features/cross-server/specs/cross-server-source-teleport.md)。
  - Velocity 与专用后端的运行时集成已实现，默认禁用。命令补全仅使用本地缓存。维度标识符也须加引号，例如 `"minecraft:overworld"`。参见[配置与验证](docs/features/cross-server/specs/cross-server-velocity-runtime.md)。
- `/wp reload` 重载 `config.json` 和 `<config-path>/server_waypoint/lang/` 目录下的翻译文件。`sendXaerosWorldId` 特性需要重启服务器才能生效。
- `/wp remove` 按标识符删除路径点，并返回临时且仅可使用一次的恢复操作。
  - `/wp remove <维度> <列表标识符>` 删除一个空的路径点列表。
- `/wp restore <令牌>` 在临时令牌有效期间恢复最近删除的路径点。
- `/wp tp` 将执行该命令的玩家传送至指定路径点。

## 跨服务器传送配置

跨服务器传送默认禁用，通过 Velocity 工作。请在 Velocity 和至少两台专用 Paper、Fabric、Forge 或 NeoForge 后端服务器上安装相匹配的 Server Waypoint 版本。Velocity 需要 Java 25；各后端使用其所需的 Java 版本。玩家无需安装客户端模组即可使用远程命令；远程图形界面需要匹配的客户端模组。单人游戏的集成服务器不参与跨服务器传送。

### 不加密：PLAINTEXT（同一主机）

此配置仅适用于运行在**同一主机**上的服务器。连接既不加密，也不通过密码学方式验证后端身份，只应在信任本机进程时使用。

1. 在 Velocity 中注册两台后端，例如 `survival` 和 `creative`。启动 Velocity 和各后端一次，让它们生成默认禁用的 `cross-server.json`，然后停止。配置文件分别位于 `<velocity-root>/plugins/server_waypoint/`、`<paper-root>/plugins/ServerWaypoint/`、`<fabric-root>/config/server_waypoint/` 或 `<forge-or-neoforge-root>/defaultconfigs/server_waypoint/`。
2. 将 Velocity 的 `cross-server.json` 设置为：

   ```json
   {
       "enabled": true,
       "transportMode": "PLAINTEXT",
       "listen": "127.0.0.1:25580",
       "backends": {
           "survival": {
               "enabled": true,
               "velocityServer": "survival"
           },
           "creative": {
               "enabled": true,
               "velocityServer": "creative"
           }
       }
   }
   ```

   每个 `velocityServer` 必须与 Velocity 注册的服务器名称一致。每个后端 ID 必须唯一且保持稳定。
3. 将 `survival` 后端的 `cross-server.json` 设置为：

   ```json
   {
       "enabled": true,
       "transportMode": "PLAINTEXT",
       "serverId": "survival",
       "coordinator": "127.0.0.1:25580",
       "catalogExport": "PUBLIC"
   }
   ```

   `creative` 使用相同配置，但将 `serverId` 改为 `creative`。`PUBLIC` 会将各后端的路径点列表分享给协调器及参与服务器上的授权浏览者。
4. 从明文模式配置中移除 `credentialsDirectory`、`coordinatorPublicKey` 和 `requiredSuite`，并从 Velocity 的后端条目中移除 `publicKey`。两端均须使用字面形式的环回 IP；`localhost`、通配地址及非环回地址都会被拒绝。编辑后重启 Velocity 和两台后端。配置正常的 Velocity 玩家转发，使各服务器上的玩家 UUID 一致。
5. 通过 Velocity 加入服务器，运行 `/wp remote servers`，再运行 `/wp remote list survival`。传送至一个已导出的路径点，例如 `/wp remote tp creative "minecraft:overworld" "Public list" "Home"`。检查目的地坐标和反馈，再测试反方向。仅发生服务器切换不能证明玩家已抵达路径点。

### 加密：NOISE_KK

后端运行在不同主机，或希望在同一主机上验证后端身份并加密连接时，请使用 `NOISE_KK`。为 Velocity 协调器指定后端可访问的私有 TCP 地址，仅允许后端主机访问路径点端口。此端口不同于 Velocity 的玩家端口和后端的 Minecraft 端口。下例使用环回地址，适用于同一主机；跨主机部署时将 `127.0.0.1` 替换为协调器的私有地址。

1. 在 Velocity 中注册 `survival` 和 `creative`。启动 Velocity 和各后端一次，让它们生成默认禁用的 `cross-server.json`，然后停止。配置路径与上面的明文模式相同。
2. 将 Velocity 的 `cross-server.json` 设置为以下内容，**首次启动时不要添加** `publicKey` 字段：

   ```json
   {
       "enabled": true,
       "transportMode": "NOISE_KK",
       "listen": "127.0.0.1:25580",
       "backends": {
           "survival": {
               "enabled": true,
               "velocityServer": "survival"
           },
           "creative": {
               "enabled": true,
               "velocityServer": "creative"
           }
       }
   }
   ```

3. 将 `survival` 后端的 `cross-server.json` 设置为以下内容，**首次启动时不要添加** `coordinatorPublicKey`。`creative` 使用相同配置，但将 `serverId` 改为 `creative`：

   ```json
   {
       "enabled": true,
       "transportMode": "NOISE_KK",
       "serverId": "survival",
       "coordinator": "127.0.0.1:25580",
       "catalogExport": "PUBLIC"
   }
   ```

4. 启动 Velocity 和两台后端，然后停止。此时启动日志会报告缺少公钥固定值，但每个组件都会在其 `cross-server.json` 旁生成 `cross-server-public-key.txt`。
5. 通过可信的管理渠道交换并核对每份公钥文件中的完整 Base64 值。将 Velocity 公钥作为 `coordinatorPublicKey` 加入**每台**后端的 `cross-server.json`；将 `survival` 公钥作为 `publicKey` 加入 Velocity 的 `backends.survival` 条目，并将 `creative` 公钥加入 `backends.creative`。例如，在现有对象中添加：

   ```text
   "coordinatorPublicKey": "<Velocity 公钥>"
   ```

   ```text
   "survival": {
       "enabled": true,
       "velocityServer": "survival",
       "publicKey": "<survival 公钥>"
   }
   ```

   将占位内容替换为完整的 Base64 公钥。各组件的 `credentials/static.key` 必须保密，绝不能复制到其他组件。无需运行配对命令。
6. 先重启 Velocity，再重启两台后端。配置经过身份验证的 Velocity 玩家转发，使各服务器上的玩家 UUID 一致。运行 `/wp remote servers` 和 `/wp remote list survival`，然后对已有的已导出路径点测试 `/wp remote tp creative "minecraft:overworld" "Public list" "Home"`。双向检查目的地坐标和反馈。

远程浏览使用 `server_waypoint.command.remote.list`（默认等级 0）。远程传送在来源服务器上同时需要 `server_waypoint.command.tp` 和 `server_waypoint.command.remote.tp`（默认等级 2），在目的地还需要本地传送权限。权限、公钥轮换和故障排查详见[管理员指南](docs/features/cross-server/cross-server-admin.md)。

## 从客户端地图模组上传

上传由服务器发起，但读取的是执行命令玩家客户端中的地图模组数据。必填的 `<source>` 为 `xaero` 或 `voxelmap`；客户端必须已安装并正确加载 Server Waypoint 和所选地图模组。服务器只接受命令所选维度以及可选列表/路径点范围内的数据。

在单人游戏（集成服务器模式）中，主机也使用相同的 `/wp upload xaero` 和 `/wp upload voxelmap` 命令。路径点在 Minecraft 客户端线程中收集，并直接传给服务端线程验证和应用，无需上传请求数据包或分块上传传输。此方式也适用于开放到局域网的世界主机；加入的其他玩家仍从各自客户端通过网络上传。权限、范围、冲突策略和删除规则均相同。

Xaero 只导入普通、已启用且非临时的路径点，并同步名称、缩写、坐标、颜色、yaw 和本地/全局可见性。VoxelMap 会跳过已禁用和坐标高亮路径点；服务器同步的名称会还原为原始列表和路径点名称，其他本地路径点会放入固定的 `VoxelMap` 列表，坐标会从维度缩放中还原，缩写和 yaw 使用空值/零值且可见性为本地。更新已有路径点时会保留服务器专有的显示名称、关键词和描述。

VoxelMap 的内置路径点图片会导入为 `voxelmap:` 图标 ID。Xaero 上传会保留现有图标；VoxelMap 上传提供可识别的图片时会替换现有图标。同步至 VoxelMap 时，物品图标及不可用的 VoxelMap 图片会显示为 VoxelMap 默认路径点图片，但 Server Waypoint 仍保留原图标 ID。路径点图标也会在本地及跨服务器路径点数据中传输，因此此次更新后客户端、后端和协调器版本必须匹配。

VoxelMap 上传使用当前子世界。如果某个请求维度的坐标缩放比例不可用，整个导出会中止；请先进入该维度后重试。上传按维度依次提交。如果后续维度处理失败，之前的更改仍会保留并同步，命令会报告部分完成的结果。

所有模式都支持相同的可选范围：

- 不指定选择器：命令执行者可用的所有服务器维度。
- `<维度>`：该维度中地图模组的所有路径点。
- `<维度> <列表>`：一个路径点列表（VoxelMap 本地路径点使用 `VoxelMap`）。
- `<维度> <列表> <路径点>`：一个路径点。

### 普通上传 / force server

`/wp upload <source> [<维度> [<列表> [<路径点>]]]` 与 `/wp upload <source> force server [<维度> [<列表> [<路径点>]]]` 行为相同：添加服务器上缺少的路径点；相同路径点保持不变；如果同名路径点中地图模组支持的属性不同，则保留服务器版本并报告冲突。不会删除任何数据。

### Force local

`/wp upload <source> force local [<维度> [<列表> [<路径点>]]]` 会添加缺少的路径点，并用客户端值替换冲突路径点中地图模组支持的属性。服务器专有的显示名称、关键词和描述会被保留。不会删除任何数据。

### Force local delete

`/wp upload <source> force local delete [<维度> [<列表> [<路径点>]]]` 会先执行 `force local`，然后删除所选地图模组中不存在的服务器数据，使所选范围与本地数据一致：

- 世界范围：在所有所选维度中删除缺少的路径点集和路径点。
- 维度范围：在该维度中删除缺少的路径点集和路径点。
- 列表范围：从该路径点列表中删除缺少的路径点；如果地图模组列表不存在，则删除整个服务器列表。
- 路径点范围：仅在本地不存在时删除所选服务器路径点。

地图模组中被跳过的路径点在 `force local delete` 中会被视为不存在，并可能导致对应服务器路径点被删除。仅当所选来源范围应作为权威副本时使用此模式。

上传使用 `server_waypoint.command.upload`（默认原版权限等级 2）。破坏性的删除模式还需要 `server_waypoint.command.upload.delete`（默认等级 4）。

### 标识符与显示名称

列表和路径点标识符是精确匹配的查找键。命令会原样保留这些值：它们可以为空（`""`）、在加引号后包含空格、以类似选项的文字开头，或看起来像 Minecraft JSON。`/wp add` 不创建显示名称覆盖，也不会将标识符解析为带格式的文本。

显示名称是可选的展示覆盖值，通过 `/wp edit ... set display-name` 单独编辑。清除显示名称会恢复为标识符；将其设为空字符串则会创建一个有意为空的覆盖值。命令补全插入的是标识符，显示名称仅可能出现在提示文本中。

## 翻译
此模组发送的消息和命令反馈将根据玩家客户端的语言设置自动翻译。此功能完全在服务器端运行；玩家无需在客户端安装此模组即可看到翻译后的消息。目前，该模组支持英语和简体中文翻译。如果您有兴趣，可以在 [Crowdin](https://crowdin.com/project/server-waypoint) 上添加翻译，帮助我们完善翻译。

- ### 添加翻译
  将语言文件放置在目录 `<config-path>/server_waypoint/lang/` 下。模组将在服务器启动时加载它们，如果服务器已运行，请使用 `/wp reload`。
  
- ### 创建语言文件
  请遵循 [`en_us.json`](./common/src/main/resources/lang/en_us.json) 或 [`zh_cn.json`](./common/src/main/resources/lang/zh_cn.json) 中的格式。

  使用[有效的语言代码](https://minecraft.wiki/w/Language#Languages)命名语言文件。
  
- ### 翻译顺序
  如果您添加的翻译文件使用的语言代码与内置语言相同，此模组会首先尝试在您添加的文件中查找翻译键。如果找不到该键，则会回退到使用内置翻译。如果您想使用自己的翻译版本，只需添加您自己的文件并覆盖内置翻译即可轻松实现。

社区翻译者署名与署名 pull request 指南请见 [`TRANSLATOR_CREDITS.md`](./TRANSLATOR_CREDITS.md)。

## 翻译鸣谢
感谢社区翻译者帮助 Server Waypoint 支持更多语言，让更多玩家可以使用本模组。

- #### 希伯来语
  [hotspotty](https://discord.com/users/575744894593663006)
- #### 西班牙语
  shimonsolo
- #### 繁体中文
  Ymaomi
- #### 繁体中文（香港）
  Ymaomi

## 路径点
- #### 保存路径
  专用服务器：

  `<config-path>/server_waypoint/waypoints/`

  单人游戏世界：

  `<minecraft-root>/saves/<world-name>/server_waypoint/waypoints/`
- #### 文件格式
  所有路径点均保存在 JSON 文件中。每个 JSON 文件包含一个维度的所有路径点，文件名为该维度转换后的完整注册名。
  例如，主世界的所有路径点存储在 `minecraft$overworld.json` 中。

## 服务端配置
Fabric、Quilt：

`<minecraft-root>/config/server_waypoint/config.json`

NeoForge、Forge：

`<minecraft-root>/defaultconfigs/server_waypoint/config.json`

Paper、Purpur：

`<server-root>/plugins/ServerWaypoint/config.json`

部分对 `config.json` 的更改将在服务器重启后生效。

- ### 默认每页数量 Default Page Limit
  设置 `/wp list` 命令未指定 `limit` 时每页显示的路径点数量。有效范围为 `1-100`，默认值为 `10`。使用 `/wp reload` 后此设置即可生效。

  ```json5
  {
    "defaultPageLimit": 10
  }
  ```
- ### 默认导航方式 Default Navigation Methods
  设置新会话在使用 `/wp navigate <dimension> <list> <waypoint>` 且未指定 `using` 时启用的一种或多种导航方式。该值必须是非空数组，其中可填写 `compass`、`map`、`bossbar`、`actionbar` 或 `text_display`。默认值为 `actionbar`。

  ```json5
  {
    "defaultNavigationMethods": [
      "actionbar"
    ]
  }
  ```
- ### 命令权限 Command Permission
  修改执行命令所需的[原版权限等级](https://minecraft.wiki/w/Permission_level)。
  
  这将被 [LuckPerms](https://modrinth.com/plugin/luckperms) 设置的权限覆盖。

  上传默认需要等级 2。具有破坏性的 `force local delete` 需要等级 4，也可通过 `server_waypoint.command.upload.delete` 单独授予；普通上传使用 `server_waypoint.command.upload`。

  远程浏览使用 `server_waypoint.command.remote.list`（`remoteList`，等级 0）。来源服务器上的远程传送授权同时需要 `server_waypoint.command.tp` 和 `server_waypoint.command.remote.tp`（`remoteTp`，等级 2），无需另行取得远程浏览权限。玩家抵达目的地时，会重新检查其本地传送权限。Paper 对未设置的权限节点使用原版命令来源等级；明确授予或拒绝的权限优先。Fabric 权限 API 支持节点覆盖；当前 Forge/NeoForge 适配器使用原版等级。集成边界参见[远程授权](docs/features/cross-server/specs/cross-server-authorization.md)。
  
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
      // /wp upload xaero
      "upload": 2,
      // /wp upload xaero force local delete
      "uploadDelete": 4,
      // /wp remote servers 和 /wp remote list
      "remoteList": 0,
      // /wp remote tp 的来源服务器授权
      "remoteTp": 2
    }
  }
  ```
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

远程目录同步和远程图形界面需要匹配的客户端与后端版本。远程快照与本地路径点文件分开保存。参见[客户端同步](docs/features/cross-server/specs/cross-server-client-sync.md)。

### 跨服务器管理

请先阅读[跨服务器传送配置](#跨服务器传送配置)；更多部署细节参见[管理员指南](docs/features/cross-server/cross-server-admin.md)。[发布说明](docs/features/cross-server/cross-server-release-notes.md)和[发布验证](docs/features/cross-server/validation/cross-server-release-readiness.md)提供更多信息。
