# Server Waypoint

[中文](README_zh.md) [English](README.md)

[![License: MIT](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](https://opensource.org/licenses/MIT)
![Modrinth Version](https://img.shields.io/modrinth/v/server_waypoint?style=flat-square&label=Version)
![both](https://img.shields.io/badge/Environment-Server%26Client-4caf50?style=flat-square)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/server_waypoint?style=flat-square&logo=modrinth&logoColor=%2300AF5C&label=Modrinth%20Downloads&color=%2300AF5C)](https://modrinth.com/plugin/server_waypoint)
[![CurseForge Downloads](https://img.shields.io/curseforge/dt/1416929?style=flat-square&logo=curseforge&logoColor=%23F16436&label=CurseForge%20Downloads&color=%23F16436)](https://www.curseforge.com/minecraft/mc-mods/server-waypoint)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/2676959/server_waypoint/total?style=flat-square&logo=github&logoColor=%23181717&label=GitHub%20Downloads&color=%23181717)

![Static Badge](https://img.shields.io/badge/1.20.x-5395FD?style=flat-square)
![Static Badge](https://img.shields.io/badge/1.21--1.21.10-5395FD?style=flat-square)

[![fabric](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/supported/fabric_vector.svg)](https://modrinth.com/plugin/server_waypoint/versions?l=fabric)
[![paper](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/supported/paper_vector.svg)](https://modrinth.com/plugin/server_waypoint/versions?l=paper)

管理路径点并自动将其同步到其他玩家的客户端。兼容 Xaero 小地图 (Xaero's Minimap)。

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
- `/wp edit` 编辑路径点。
- `/wp list` 列出所有路径点。以树状层级显示所有路径点。允许用户点击以传送、编辑或删除路径点。
- `/wp reload` 重载 `config.json` 和 `\config\server_waypoint\lang` 目录下的翻译文件。`sendXaerosWorldId` 特性需要重启服务器才能生效。
- `/wp remove` 按名称删除路径点。显示已删除的路径点，点击该消息可恢复该路径点。
  - `/wp remove <维度> <列表>` 删除一个空的路径点列表。
- `/wp tp` 将执行该命令的玩家传送至指定路径点。

## 翻译
本模组目前内置了英语和简体中文翻译。如果加载了对应的语言文件，命令反馈将根据发送者客户端的语言设置自动翻译。

- ### 添加翻译
  将语言文件放置在目录 `<minecraft_root>\config\server_waypoint\lang\` 下。模组将在服务器启动时加载它们，如果服务器已运行，请使用 `/wp reload`。
  
- ### 创建语言文件
  请遵循 [`en_us.json`](./common/src/main/resources/lang/en_us.json) 或 [`zh_cn.json`](./common/src/main/resources/lang/zh_cn.json) 中的格式。

  使用[有效的语言代码](https://minecraft.wiki/w/Language#Languages)命名语言文件。

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

- ### 命令权限 Command Permission
  修改执行命令所需的[原版权限等级](https://minecraft.wiki/w/Permission_level)。
  
  这将被 [LuckPerms](https://modrinth.com/plugin/luckperms) 设置的权限覆盖。
  
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
      // /wp tp
      "tp": 2,
      // /wp reload
      "reload": 2
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
  
  需要安装Xaero的小地图模组。
- #### 手动同步至Xaero的小地图模组
  默认值：`无`

  手动触发，需要安装Xaero的小地图模组。
  
  此操作将替换Xaero的小地图中所有与服务器列表同名的路径点集合。
  - 保留的内容：
  名称唯一、且在服务器上不存在的集合。
  - 丢失的内容：
  您在与服务器同名的集合中添加的所有路径点。您自行创建的、但恰好与服务器列表重名的集合。