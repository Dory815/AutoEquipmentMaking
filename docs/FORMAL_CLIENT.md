# 正式服独立测试客户端

授权记录：v1.1 开发使用独立单人存档。2026-10-23 用户随后明确授权在 201 正式服测试 v1.1，并沿用 lyerskin / bot_lighte 登录。本次例外只操作独立 formal-client，不操作原 PCL/Dory 客户端。常规回归仍使用单人存档。

配置日期：2026-10-23。用户明确要求独立客户端，并确认由用户手动登录后进入 201（101.gpa.ac.cn）。启动器位于 `D:/MC/AutoEquipmentMaking/runtime/formal-client/AEM Test Launcher.exe`。该目录中的配置、模组、日志和登录设置独立于原 PCL 实例。

游戏版本为 `26.2-Fabric-AEM-Formal`，Minecraft 26.2 / Fabric Loader 0.19.5 / Java 25。游戏目录为 `runtime/formal-client/.minecraft/versions/26.2-Fabric-AEM-Formal`。从原实例只读取并复制启动器程序、游戏包、公共资源和必要依赖。未复制账号凭据、存档或正式工坊的机器人配置。

## 模组与按键

独立实例当前安装 Auto Equipment Making 1.1.0-rc.4，客户端保持关闭。RC.4 的集中采购仅完成编译，按用户要求不启动测试。此前正式批次使用 RC.2 进程，并在暂停对账后应用 RC.3 取料逻辑，完成两盒后正常退出；RC.2、RC.3 安装包均有备份。依赖保留 Fabric API 0.158.0+26.2、MiniHUD 0.40.4、MaLiLib 0.29.3 和 RDPMouse 1.0.0。MiniHUD 的 entityDataSync 启用。Servux 0.11.2 使用协议 1，必须配合 MiniHUD 0.40.4 / MaLiLib 0.29.3。MiniHUD 0.40.7 的协议 2 不适用；仅降级 MiniHUD 而保留 MaLiLib 0.29.6 会触发其 breaks 声明。

[RDPMouse 官方页面](https://modrinth.com/mod/rdpmouse) 标明支持 Minecraft 26.2 Fabric，用于 RDP 视角控制。文件从 Modrinth 官方 CDN 下载，版本 ID 为 `r3BNXtv6`，SHA-512 与官方 API 一致。未采用仅支持旧版 Minecraft 的修复包。

RDPMouse 默认 F8 与机器人冲突，因此此实例将 RDPMouse 改为 F7。游戏启动后按 F7 启用；RDPMouse 默认关闭且不保存开关状态。光标到达窗口边缘时，可以按住左 Alt 释放光标、移回中央再松开。F8 仍控制机器人，F9 急停。

## 验证与交接

验证 Windows 所需的 88 个库文件 SHA-1、游戏包 SHA-1、资源索引 SHA-1 及 RDPMouse SHA-512 通过。独立启动器进程路径和窗口响应检查通过。详细清单和模组 SHA-256 位于实例根目录的 `setup-manifest.json`。

用户完成首次手动登录，随后授权自主使用 lyerskin 的 bot_lighte 账号。独立实例可直接启动并进入 201。远程 Servux 0.11.2 握手通过，工坊坐标已配置。RDPMouse 运行开关与小幅鼠标输入核验通过；远程桌面操作手感仍由用户确认。

该实例位于已忽略的 `runtime/`，后续生成的登录设置不会进入项目版本控制。

201 正式批次完成两盒、28 件装备，等级 1500→812（消耗 688 级）。成品位于末地 `(1066,57,23)` 交付箱前两格。期间完成耐久 III 补货、铁砧补货和导航修复；磁盘版本更新为 RC.7。详细证据见 [铁砧补位与正式服验证](ANVIL_SUPPLY_VALIDATION.md)。

v1.1 后续正式批次另行完成两盒、28 件，812→124（688 级）；新增成品位于同一交付箱第 1、3 格，原历史成品位于第 2 格。7 笔耐久 III 交易消费 66 颗绿宝石，7 块分解；原料盒移动后自动扫描恢复。见 [v1.1 正式服验证](V11_FORMAL_VALIDATION.md)。这份结果不覆盖 RC.4 新增的集中采购。

