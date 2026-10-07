# 服务器路径点 Server Waypoint

[中文](README_zh.md) [English](README.md)

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
[![crowdin](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/translate/crowdin_vector.svg)](https://crowdin.com/project/server-waypoint)

在服务器上管理路径点，并自动同步到玩家的客户端。兼容 Xaero 小地图 (Xaero's Minimap)、Xaero 世界地图 (Xaero's World Map) 和 VoxelMap，大部分功能无需在客户端安装本模组。

4.0.0 的更新内容见[更新日志（英文）](CHANGELOG.md)。

## 主要功能
- 从服务端自动同步路径点，包括同步到 Xaero 小地图和 VoxelMap（支持的版本见下文）。
- 自定义路径点渲染，标记可显示缩写、物品图标或 VoxelMap 图标。
- 允许玩家通过图形界面（需要安装客户端）和可点击的聊天命令（只需安装服务器）管理路径点。
- 服务端导航：用指南针、地图、Boss 栏、动作栏或悬浮文字引导玩家前往路径点，无需在客户端安装本模组。
- 从 Xaero 小地图或 VoxelMap 上传路径点到服务器。
- 通过 Velocity 代理浏览其他服务器的路径点并跨服务器传送（默认关闭）。
- 命令自动补全和游戏内帮助。
- `/wp <选项>` 命令支持自定义权限。兼容 [LuckPerms](https://modrinth.com/plugin/luckperms)。
- 支持从 Xaero 小地图的聊天分享消息中便捷添加路径点，无需在客户端安装本模组。
- 服务端翻译：消息按每位玩家的语言显示。

## 依赖项
必需：
  - [Fabric API](https://modrinth.com/mod/fabric-api)（Fabric）
  
可选：
  - [LuckPerms](https://modrinth.com/plugin/luckperms)
  - [Xaero's Minimap](https://modrinth.com/mod/xaeros-minimap)
  - [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map)：在其右键菜单中加入本模组的选项
  - [VoxelMap](https://modrinth.com/mod/voxelmap-updated)（Fabric；Forge 1.21.11、26.1-26.1.2 和 26.2；NeoForge 1.21.2-1.21.4、1.21.11、26.1-26.1.2、26.2 和 26.3）
  - [Mod Menu](https://modrinth.com/mod/modmenu)（Fabric）：从模组列表打开客户端设置
  - [Velocity](https://papermc.io/software/velocity)：仅[跨服务器传送](#跨服务器传送配置)需要

## 快捷键
- 按下 `右 Shift` 或使用 `/wp_gui` 打开路径点管理界面。
- 在路径点管理界面按下 `T` 可传送至鼠标悬停的路径点（需要`/wp tp`命令权限）。
- 在路径点管理界面按下 `C` 可打开客户端设置。也可以通过 Mod Menu（Fabric）或模组列表（NeoForge 和 Forge）中的配置按钮打开。
- 添加和编辑路径点界面也可以设置关键词、描述和图标。
- 在 Xaero 世界地图中右键点击地图，可在该位置向服务器添加路径点；右键点击路径点，可将其添加到服务器，或编辑服务器上的对应路径点。

## 命令
运行 `/wp` 打开菜单：大多数功能点一下即可使用，每个界面都会以链接给出下一步操作。控制台、RCON 和命令方块会收到相同信息的纯文本版本，其中写明标识符和坐标。
- `/wp add` 打开当前维度的列表选择，在你所在的位置添加路径点。同一列表中的标识符不能重复。
  - `/wp add <维度> <列表标识符>` 添加一个路径点列表。
  - `/wp add <x y z> <列表标识符> <名称>` 在当前维度添加一个路径点，缩写自动生成，颜色随机，朝向与你当前的朝向相同。在前面加上维度（`/wp add <维度> <列表标识符> <x y z> <名称>`）可添加到其他维度。完整格式还可依次填写缩写、颜色、偏航角、可见范围、关键词、描述和 `icon <namespace:path>`。
- `/wp download [<维度> [<列表标识符> [<路径点标识符>]]]` 将路径点发送到你的地图模组（需客户端安装本模组）。
- `/wp details list <维度> <列表标识符>` 和 `/wp details waypoint <维度> <列表标识符> <路径点标识符>` 显示全部属性，并提供编辑按钮。
- `/wp edit list ...` 和 `/wp edit waypoint ...` 每次设置一个属性，或清除一个可选属性。不填值时，`set color` 打开颜色选择，`set yaw` 打开朝向选择；`set color random` 随机选择颜色。完整命令格式请运行 `/wp help edit`。
  - `/wp edit waypoint <维度> <列表> <路径点> set icon minecraft:diamond` 选用物品图标；使用 `clear icon` 恢复首字母标记。`voxelmap:star` 等 `voxelmap:` ID 可选用 VoxelMap 内置图片。`/wp add` 也接受在可选关键词和描述之后添加 `icon <namespace:path>`。图标 ID 不加引号；图标参数会补全服务器物品 ID 和 VoxelMap 内置图片 ID；`/wp details waypoint` 的图标行提供编辑和清除按钮。
- `/wp help [<主题>]` 说明各个命令，提供可点击的用法和示例。
- `/wp list` 显示当前维度：能在一页内显示时，列出各列表及其前几个路径点，否则每个列表占一行。可使用 `all`、维度，或维度加列表标识符更改范围。
  - 选项顺序为 `search <查询内容>`、`sort <default|name|distance|color>`（非默认排序可加 `order <ascending|descending>`）、`limit <1-100>`、`view <lists|tree|flat>`、`page <页码>`。包含空格的值，以及与选项名称相同的列表名称，需要加引号。
  - 每页只显示完整的列表：树状视图和每列表一行的视图每页最多显示每页数量加 5 行；平铺视图和单个列表每页显示每页数量的行数。
  - `/wp list dimensions [page <页码>]` 列出所有维度及其路径点数量。
- `/wp navigate` 显示导航面板。`/wp navigate <维度> <列表> <路径点> [<方式>|default|all]` 开始导航，`/wp navigate use|disable <方式>` 开启或关闭某种方式，`/wp navigate disable` 停止导航，`/wp navigate config text_display` 调整悬浮文字。
- `/wp remote` 显示远程服务器及其状态；`/wp remote page <页码>` 翻页。
- `/wp remote list [<服务器> [<维度> [<列表>]]]` 浏览缓存中的远程路径点，选项与 `/wp list` 相同。标识符必须精确匹配；包含空格或与选项名称相同的名称需要加引号。跨服务器距离不可用，因此按距离排序时会显示提示。
  - 结果仅供浏览，通过普通服务端聊天显示。彩色圆点表示每个服务器的状态：可用、已过期、无法连接或无权访问。`/wp remote details <服务器> <维度> <列表> <路径点>` 显示单个路径点。运行 `/wp help remote` 查看帮助。
  - 启用跨服务器配置后开始同步目录。参见[Velocity 运行时配置](docs/features/cross-server/specs/cross-server-velocity-runtime.md)和[远程目录查询](docs/features/cross-server/specs/cross-server-catalog-queries.md)。
- `/wp remote tp <服务器> <维度> <列表> <路径点>` 使用缓存中的精确标识符请求跨服务器传送（含空格的名称须加引号）。过期或不存在的目标会在准备阶段前被拒绝；目的地准备完成且重新检查权限通过前，玩家仍留在来源服务器，到达后由目的地服务器确认。参见[远程传送发起流程](docs/features/cross-server/specs/cross-server-source-teleport.md)。
  - Velocity 与专用后端的运行时集成已实现，默认禁用。命令补全仅使用本地缓存。维度标识符也须加引号，例如 `"minecraft:overworld"`。参见[配置与验证](docs/features/cross-server/specs/cross-server-velocity-runtime.md)。
- `/wp reload` 重载 `config.json` 和 `<config-path>/server_waypoint/lang/` 目录下的翻译文件。`defaultPageLimit`、`defaultNavigationMethods`、`CommandPermission`、`addWaypointFromChatSharing` 和 `compressChunkedMessages` 立即生效；`serverId`、`cross-server.json` 以及开启 `sendXaerosWorldId` 需要重启服务器才能生效。路径点文件不会重新加载，请在服务器停止时编辑。
- `/wp remove` 按标识符删除路径点，并给出临时且仅可使用一次的恢复链接。
  - `/wp remove <维度> <列表标识符>` 删除一个空的路径点列表。
- `/wp restore <令牌>` 恢复 10 分钟内删除的路径点。每个令牌只能使用一次。
- `/wp tp <维度> <列表标识符> <路径点标识符>` 将你传送至指定路径点。
- `/wp upload` 在客户端安装了本模组时显示上传面板。`/wp upload <xaero|voxelmap>` 从执行玩家客户端上所选的地图模组导入路径点。冲突、强制覆盖和删除行为详见[从客户端地图模组上传](#从客户端地图模组上传)。

## 跨服务器传送配置

跨服务器传送默认关闭，需要一台供玩家进入的 Velocity 服务器，以及至少两台 Paper、Fabric、Forge 或 NeoForge 游戏服务器（例如 `survival` 和 `creative`）。以下将游戏服务器称为“后端”。先根据机器的摆放方式选择一种连接方式：

- **所有服务器都在同一台电脑上：**使用下面的 `PLAINTEXT` 配置。它设置简单，但服务器之间的连接不加密。
- **服务器分布在不同电脑上，或需要加密连接：**使用 `NOISE_KK` 配置。它需要多一步交换公钥。

Velocity 和每台后端必须安装相匹配的 Server Waypoint 版本。Velocity 插件为 `server_waypoint-<版本>-velocity.jar`，可在 [Modrinth](https://modrinth.com/plugin/server_waypoint/versions?l=velocity) 或 [GitHub Releases](https://github.com/2676959/server_waypoint/releases) 下载。Velocity 需要 Java 25；后端使用对应版本要求的 Java。玩家使用远程命令无需安装客户端模组；使用远程图形界面则需要匹配的客户端模组。单人游戏不能使用跨服务器传送。

### 开始前

1. 在 Velocity 中注册 `survival` 和 `creative` 等后端服务器。先启动 Velocity 和每台后端一次，让 Server Waypoint 自动生成 `cross-server.json`，然后停止它们。
2. 找到各自的配置文件：
   - Velocity：`<velocity-root>/plugins/server_waypoint/cross-server.json`
   - Paper：`<paper-root>/plugins/ServerWaypoint/cross-server.json`
   - Fabric：`<fabric-root>/config/server_waypoint/cross-server.json`
   - Forge 或 NeoForge：`<forge-or-neoforge-root>/defaultconfigs/server_waypoint/cross-server.json`
3. 按 Velocity 和后端服务器的说明设置玩家转发。这样同一位玩家在各服务器上的身份编号（UUID）才会相同。使用 `NOISE_KK` 时，玩家转发还需要启用身份验证。

### 方式一：同一台电脑（PLAINTEXT）

只有 Velocity 和所有后端都运行在**同一台电脑**上时，才能使用此方式。请将它们之间的地址写为 `127.0.0.1`，不要写 `localhost`、`0.0.0.0` 或其他 IP 地址。

1. 将 Velocity 的 `cross-server.json` 改为：

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

   `velocityServer` 必须与 Velocity 中注册的服务器名称一致。`survival` 和 `creative` 是各后端的唯一 ID；设置后不要随意更改。
2. 将 `survival` 后端的 `cross-server.json` 改为：

   ```json
   {
       "enabled": true,
       "transportMode": "PLAINTEXT",
       "serverId": "survival",
       "coordinator": "127.0.0.1:25580",
       "catalogExport": "PUBLIC"
   }
   ```

   在 `creative` 后端使用相同内容，只将 `serverId` 改为 `creative`。`PUBLIC` 表示把该服务器的路径点列表提供给有权限浏览的玩家。
3. 确认配置文件与上面的示例一致，保存后启动 Velocity 和两台后端。

### 方式二：不同电脑或需要加密（NOISE_KK）

此方式会加密服务器之间的连接，并用公钥确认连接的是自己配置的服务器。每台服务器会生成自己的**公钥**和**私钥**：公钥是一串可以发给其他服务器的文字；私钥保存在 `credentials/static.key`，不能分享或复制到别的服务器。

不同电脑上的后端需要能连接到 Velocity。下面的 `127.0.0.1:25580` 只适用于同一台电脑；不同电脑时，请把示例中的 `127.0.0.1` 换成后端可以访问的 Velocity 内网 IP，并在防火墙中只允许后端连接 `25580`。这个端口与玩家连接 Velocity 的端口、后端的 Minecraft 端口不同。

1. 将 Velocity 的 `cross-server.json` 改为以下内容。**首次启动先不要添加公钥：**

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

2. 将 `survival` 后端的 `cross-server.json` 改为以下内容。`creative` 使用相同内容，只将 `serverId` 改为 `creative`。**首次启动先不要添加公钥：**

   ```json
   {
       "enabled": true,
       "transportMode": "NOISE_KK",
       "serverId": "survival",
       "coordinator": "127.0.0.1:25580",
       "catalogExport": "PUBLIC"
   }
   ```

3. 启动 Velocity 和两台后端，再停止它们。此时日志提示缺少公钥是正常的。每台服务器都会在自己的 `cross-server.json` 旁生成 `cross-server-public-key.txt`。
4. 打开这三份公钥文件，复制其中**完整的一行文字**，不要复制 `credentials/static.key`。通过可信的管理渠道核对并交换公钥，然后修改配置：
   - 在 **`survival` 和 `creative` 两台后端**的 `cross-server.json` 中添加 `"coordinatorPublicKey": "<Velocity 的公钥>"`。
   - 在 **Velocity** 配置的 `backends.survival` 中添加 `"publicKey": "<survival 的公钥>"`；在 `backends.creative` 中添加 `"publicKey": "<creative 的公钥>"`。

   例如，`survival` 的后端配置在填入真实公钥后应是：

   ```json
   {
       "enabled": true,
       "transportMode": "NOISE_KK",
       "serverId": "survival",
       "coordinator": "127.0.0.1:25580",
       "catalogExport": "PUBLIC",
       "coordinatorPublicKey": "<Velocity 的完整公钥>"
   }
   ```

   Velocity 的配置在填入两台后端的真实公钥后应是：

   ```json
   {
       "enabled": true,
       "transportMode": "NOISE_KK",
       "listen": "127.0.0.1:25580",
       "backends": {
           "survival": {
               "enabled": true,
               "velocityServer": "survival",
               "publicKey": "<survival 的完整公钥>"
           },
           "creative": {
               "enabled": true,
               "velocityServer": "creative",
               "publicKey": "<creative 的完整公钥>"
           }
       }
   }
   ```

   把尖括号中的示例文字换成对应公钥文件里的真实内容。无需运行配对命令。
5. 保存配置后，先启动 Velocity，再启动两台后端。不同电脑时，请再次确认 Velocity 的 `listen` 地址以及后端的 `coordinator` 地址均已改为可互相访问的地址。

### 检查是否成功

通过 Velocity 加入服务器，运行 `/wp remote` 和 `/wp remote list survival`。找一个已分享的路径点，执行类似 `/wp remote tp creative "minecraft:overworld" "Public list" "Home"` 的命令。检查玩家到达后的坐标和反馈，再从 `creative` 测试返回 `survival`。仅切换到另一台服务器不代表已到达路径点。

远程浏览默认对所有玩家开放。远程传送默认需要等级 2 的来源服务器权限，并在目的地再次检查本地传送权限。具体权限、公钥更换和故障排查参见[管理员指南](docs/features/cross-server/cross-server-admin.md)。

### 跨服务器管理

- 在 Velocity 控制台运行 `/serverwaypoint status`，可查看协调器是否在运行、使用的连接方式，以及哪些后端在线。玩家需要 `server_waypoint.command.cross_server.status` 权限才能使用。Velocity 本身无法授予权限，请通过 LuckPerms 等权限插件授予。
- 配置好 `cross-server.json` 后，在后端控制台运行 `/sw-cross-server-keygen`，即可生成该后端的 `NOISE_KK` 公钥和私钥，无需先启动再停止一次。此命令需要等级 4，且不会覆盖已有的密钥。
- 在后端的 `cross-server.json` 中设置 `"serverIconItem": "minecraft:diamond"` 并重启该后端，可更改玩家在服务器选择器中看到的图标。默认值为 `minecraft:beacon`。

跨服务器功能的[发布说明](docs/features/cross-server/cross-server-release-notes.md)和[发布验证](docs/features/cross-server/validation/cross-server-release-readiness.md)提供更多信息。

## 从客户端地图模组上传

上传由服务器发起，但读取的是执行命令玩家客户端中的地图模组数据。必填的 `<source>` 为 `xaero` 或 `voxelmap`；客户端必须已安装并正确加载 Server Waypoint 和所选地图模组。服务器只接受命令所选维度以及可选列表/路径点范围内的数据。

在单人游戏（集成服务器模式）中，主机也使用相同的 `/wp upload xaero` 和 `/wp upload voxelmap` 命令。路径点在 Minecraft 客户端线程中收集，并直接传给服务端线程验证和应用，无需上传请求数据包或分块上传传输。此方式也适用于开放到局域网的世界主机；加入的其他玩家仍从各自客户端通过网络上传。权限、范围、冲突策略和删除规则均相同。

Xaero 只导入普通、已启用且非临时的路径点，并同步名称、缩写、坐标、颜色、yaw 和本地/全局可见性。VoxelMap 会跳过已禁用和坐标高亮路径点；服务器同步的名称会还原为原始列表和路径点名称，其他本地路径点会放入固定的 `VoxelMap` 列表，坐标会从维度缩放中还原，缩写和 yaw 使用空值/零值且可见性为本地。更新已有路径点时会保留服务器专有的显示名称、关键词和描述。

VoxelMap 的内置路径点图片会导入为 `voxelmap:` 图标 ID。Xaero 上传会保留现有图标；VoxelMap 上传提供可识别的图片时会替换现有图标。同步至 VoxelMap 时，物品图标及不可用的 VoxelMap 图片会显示为 VoxelMap 默认路径点图片，但 Server Waypoint 仍保留原图标 ID。路径点图标也会在本地及跨服务器路径点数据中传输，因此客户端、后端和 Velocity 插件必须使用相同版本。

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
此模组发送的消息和命令反馈将根据玩家客户端的语言设置自动翻译。此功能完全在服务器端运行；玩家无需在客户端安装此模组即可看到翻译后的消息。目前，该模组支持英语、简体中文、繁体中文、繁体中文（香港）、西班牙语和希伯来语翻译。如果您有兴趣，可以在 [Crowdin](https://crowdin.com/project/server-waypoint) 上添加翻译，帮助我们完善翻译。

- ### 添加翻译
  将语言文件放置在目录 `<config-path>/server_waypoint/lang/` 下。模组将在服务器启动时加载它们，如果服务器已运行，请使用 `/wp reload`。
  
- ### 创建语言文件
  请遵循 [`en_us.json`](./common/src/main/resources/lang/en_us.json) 或 [`zh_cn.json`](./common/src/main/resources/lang/zh_cn.json) 中的格式。

  使用[有效的语言代码](https://minecraft.wiki/w/Language#Languages)命名语言文件。

  4.0.0 更改了大部分翻译键，为 3.x 制作的语言文件需要参照当前的 `en_us.json` 重新制作。
  
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

Paper、Folia、Purpur：

`<server-root>/plugins/ServerWaypoint/config.json`

`/wp reload` 会立即应用 `defaultPageLimit`、`defaultNavigationMethods`、`CommandPermission`、`addWaypointFromChatSharing` 和 `compressChunkedMessages` 的更改；更改 `serverId`、开启 `sendXaerosWorldId` 以及修改 `cross-server.json` 需要重启服务器才能生效。

- ### 默认每页数量 Default Page Limit
  设置 `/wp list` 和 `/wp remote list` 未指定 `limit` 时的每页数量 `L`：平铺视图和单个列表每页显示 `L` 行，树状视图、每列表一行的视图以及维度列表和服务器列表每页最多 `L + 5` 行。有效范围为 `1-100`，默认值为 `10`。使用 `/wp reload` 后此设置即可生效。

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
      // /wp remote 和 /wp remote list
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

    示例：
    ```json5
     {
       "Features": {
         "sendXaerosWorldId": true
       }
     }
     ```
  - #### compressChunkedMessages
    默认值：`true`

    压缩服务器与安装了本模组的客户端之间传输的路径点数据。服务器在 `/wp reload` 后立即应用更改；已连接的客户端按加入服务器时收到的设置压缩它发送的数据。

    示例：
    ```json5
     {
       "Features": {
         "compressChunkedMessages": true
       }
     }
     ```

## 客户端配置

在路径点管理界面按 `C`，或点击 Mod Menu（Fabric）或模组列表（NeoForge 和 Forge）中的配置按钮，即可打开客户端设置。更改会立即生效，并在关闭界面时保存。将鼠标悬停在某一行上可查看说明和默认值。设置旁的 ↺ 按钮可将其恢复默认，**全部恢复默认…** 会恢复所有设置。

- #### 路径点渲染
  - **在世界中显示路径点**：在世界中绘制路径点标记。默认值：`开`。
  - **在 F1 模式下渲染路径点**：按 F1 隐藏界面时仍显示世界中的路径点；加载画面中始终隐藏路径点。默认值：`关`。
  - **缩放**：标记的大小，范围 `0` 到 `500`（百分比）。默认值：`100%`。
  - **垂直偏移**：将标记上移或下移，最多半格，范围 `-100` 到 `100`（百分比）。默认值：`0%`。
  - **背景不透明度**：标记背景和图标的不透明度，从 `0`（透明）到 `255`（不透明）。默认值：`128`。
  - **局部路径点范围**：可见范围为局部的路径点只在此区块数范围内绘制，范围 `0` 到 `1024`；全局路径点始终绘制。默认值：`12` 区块。
- #### 地图模组
  所有加载器都支持Xaero的小地图。VoxelMap 支持 Fabric、Forge 1.21.11、26.1-26.1.2 和 26.2，以及 NeoForge 1.21.2-1.21.4、1.21.11、26.1-26.1.2、26.2 和 26.3。受支持但未安装的地图模组会显示为未安装。
  - **自动同步**：随服务器上的变化，保持本模组添加到地图模组的路径点为最新。默认值：`开`。
  - **立即同步**：确认后，用服务器当前的路径点替换本模组添加的路径点。进入世界且路径点同步完成后可用。

  本模组会标记它添加的内容：Xaero的小地图中的集合和 VoxelMap 中的路径点名称带有内部 `sw␟` 前缀。同步只会修改这些内容，因此你自己的路径点和列表永远不会被更改，即使与服务器列表同名。你对已同步路径点所做的修改、你放入本模组列表中的路径点，以及服务器上已删除的列表都会丢失。上传时会将这些管理名称映射回服务端的列表和路径点名称。
- #### 外观
  - **颜色主题**：打开主题编辑器。

远程目录同步和远程图形界面需要匹配的客户端与后端版本。远程快照与本地路径点文件分开保存。参见[客户端同步](docs/features/cross-server/specs/cross-server-client-sync.md)和[跨服务器传送配置](#跨服务器传送配置)。
