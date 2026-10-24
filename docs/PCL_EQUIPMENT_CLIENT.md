# PCL 自动打装备客户端

2026-10-24 正式版交接：用户授权测试后同步正式版 1.0。当前安装 `auto-equipment-making-client-1.0.0.jar`，SHA-256 为 `df53234249c4377a09c9ef8a42c8f25f2c65cc54275f9467eeec0654dd2d3ca0`。同一构建通过 17 项导航场景和完整制作回归（14 件、1 盒、319 级）。3 个配置文件及全部 81 个受保护文件哈希不变，模组仍为 5 个。未启动 PCL，未连接正式服。旧 RC.6 备份在 `runtime/pcl-equipment-client/backups/rc6-before-v10-navigation-20261024`。见 [寻路实现与验证](V1_NAVIGATION.md) 和 `docs/evidence/navigation-v1/pcl-update.json`。

以下 RC.4–RC.6 记录为历史。2026-10-24 RC.6 交接：RC.6 修订 `rc6-edgefix-20261024` 保留在本版本，工坊及另外两个配置文件哈希保持不变。后续授权的定向测试在项目独立单人客户端通过，涵盖动态报价、暂存满后 good 和 Servux 接收/缓存校验；本 PCL 客户端未启动，未连接正式服。测试构建与本版本 jar 哈希一致。见 [本次修复与验证](V11_RC6_EDGE_FIXES.md)。

用户于 2026-10-24 明确要求在原 PCL 目录中新建独立版本 `26.2-自动打装备`。本次授权仅配置这个新版本，不覆盖原版本，不启动客户端或游戏测试。

## 位置与版本

- PCL：`D:/MC/PCL 正式版 2.10.6/Plain Craft Launcher 2.exe`。
- 游戏目录：`D:/MC/PCL 正式版 2.10.6/.minecraft/versions/26.2-自动打装备`。
- Minecraft 26.2、Fabric Loader 0.19.5、Java 25。
- AEM 1.0.0（正式版）、Fabric API 0.158.0+26.2。
- Servux 客户端依赖：MiniHUD 0.40.4、MaLiLib 0.29.3；兼容服务器 Servux 0.11.2。
- 远程桌面鼠标：RDPMouse 1.0.0。

版本的 `PCL/Setup.ini` 设置 `VersionArgumentIndieV2:True`。游戏配置、模组、存档及后续日志使用本版本目录。公共 libraries 和 assets 由 PCL 共享，仅补充缺失文件，已有文件必须校验一致，绝不覆盖。

使用公开的 lyerskin 验证地址 `https://auth.lylighte.cc/skinapi`。不复制账号凭据，不更改启动器当前账号。账号由用户在 PCL 中选择。服务器列表提供 `201`，未配置自动进服。

## 工坊与控制

工坊配置来自独立 formal-client 的 `workshop.json`，绑定 `101.gpa.ac.cn` 的末地工坊。范围 `(1054,56,8)` 至 `(1095,62,36)`；工作台 `(1072,57,24)`，锻造台 `(1071,57,24)`，交付箱 `(1066,57,23)`。

原料从未染色潜影盒和普通箱子获取。正式版默认扫描自身周围 21×21×5 内的空淡蓝色潜影盒；可以在设置中调整扫描范围、产物盒列表或手动套数。机器人不会因启动客户端而自动开始任务。

| 控制 | 用途 |
| --- | --- |
| F7 | 启用或关闭远程桌面鼠标修复，每次启动后手动启用 |
| 左 Alt | 释放远程鼠标，移回中央后松开 |
| F6 | 设置界面 |
| F8 | 开始任务 |
| F10 / F12 | 暂停 / 恢复 |
| B | 绑定指向的交付箱 |
| 聊天 `skip` | 自动选择有效交付箱 |
| F9 | 急停 |
| 聊天 `pause` / `continue` | 暂停 / 继续 |
| 聊天 `good` | 补货完成后继续 |

## 安装与验证

打包脚本：`.tools/package_pcl_client.py`。脚本先在 `runtime/pcl-equipment-client` 创建安装包，再以 `--install` 独占创建目标版本。目标目录已存在时拒绝安装。

初装 RC.4 的静态检查包括游戏包 SHA-1、Windows x64 所需库及资源内容校验、全部安装文件 SHA-256、模组元数据，以及原版本 JSON 和 PCL 版本配置不变。记录见 `docs/evidence/pcl-equipment-client.json`。

随后用户授权独立单人测试。RC.4 复现零碎采购，RC.5 修正后，全书交易和少量缺货两个场景均通过完整交付。新版本中的 AEM jar 同步为 RC.5，SHA-256 为 `671266e0caf3411b199a7a62cc926f322682d22547d63f2a95dbeb1e950e9e16`，不含游戏测试类。旧 RC.4、原版本用途说明和设置备份位于 `runtime/pcl-equipment-client/backups/rc4-before-rc5`。

以上历史测试使用项目独立单人运行目录，不启动这个 PCL 版本或连接正式服务器。RC.5 安装记录为 `docs/evidence/pcl-equipment-client-rc5-update.json`。

历史 RC.6 安装由 `.tools/v11/install_pcl_rc6.ps1` 完成，仅替换 AEM jar 并更新版本用途说明。三个配置文件全部保持不变，模组仍为五个；旧 RC.5、Setup.ini 和用途说明备份至 `runtime/pcl-equipment-client/backups/rc5-before-rc6`。本次构建只编译和静态检查，未启动客户端或测试。当前结果见 `docs/evidence/pcl-equipment-client-rc6-update.json` 和 [边界修复记录](V11_RC6_EDGE_FIXES.md)。
