# PCL 自动打装备客户端

2026-10-25 当前安装：`1.3.1`，SHA-256 `c392e94ab5ce998ab4f9026a10d9ca4825f101e62fe39c2f14874ad35e1a49d0`。安装时8个配置文件及155个受保护文件哈希不变，模组保持5个。随后用户明确授权操作指定PCL版本测试201，完成同任务2盒/28件；暂停与补经验后继续原队列。任务结束，客户端保持开启。用户确认实际测试没有问题。详见[1.3.1发布说明](release-notes/1.3.1.md)。下方安装状态为历史。

2026-10-25 最新安装：按用户要求同步 `1.3.0` / `bulk-tools-20261025`，由用户自行测试，未启动客户端。安装包 SHA-256 为 `120f208c1f4dd4687e242f458e2fe750846f2503139ab1bbc8e33173b2c97ba7`，与 1.3 验收包一致。配置目录内 8 个文件、全部 153 个受保护文件哈希不变，模组仍为 5 个。旧 `1.1.0-alpha.2` 包与完整配置备份至 `runtime/pcl-equipment-client/backups/v11-alpha2-before-v13-bulk-tools-20261025`，配置备份逐文件校验通过。没有更改工坊范围或新增设置值；新增设置由 1.3 首次运行按默认值读取。未连接正式服、推送或发布。证据见 `docs/evidence/v13-bulk-tools-20261025/pcl-update.json`；安装脚本为 `tools/install_v13_pcl.ps1`。以下安装状态为历史。

2026-10-25 最新安装：按用户要求同步 `1.1.0-alpha.2` / `trapdoor-navigation-20261025`，由用户自行启动测试。安装包 SHA-256 为 `54dc2f07716e818e6d035a79b123bc117c262fafe5caa151294cb75e8b5df309`，与连续三套验收的开发包一致。配置目录内 8 个文件、全部 153 个受保护文件哈希不变，模组仍为 5 个。旧 1.0.4 与完整配置备份至 `runtime/pcl-equipment-client/backups/v104-before-v11-alpha2-navigation-20261025`。没有启动客户端或连接正式服。证据见 `docs/evidence/v11-hall-navigation-20261025/pcl-update.json`。保留的工坊上界仍为 Y=62；用户测试 Y=63 高处原料盒时，须自行把工坊范围扩展至覆盖该位置。以下 1.0.4 状态均为历史。

2026-10-25 寻路开发：项目源码为 `1.1.0-alpha.2`，新寻路与自动疾跑只在项目独立单人副本中测试。本 PCL 模组仍为 `1.0.4`，没有安装本次开发包。当前开发与验证记录见 [201 活板门寻路](V11_TRAPDOOR_NAVIGATION.md)。下文为各次历史交接。

2026-10-25 基准归档交接：用户整理完成并退出保存后，`201交易所-待整理-20261025` 复制归档到项目 `runtime/test-baselines/201-trading-hall-user-arranged-20261025/world`。58 个文件逐一核对，源存档不变，基准 ZIP SHA-256 为 `559d400704c4deaa7f6442916eaaaffdaecdaf6093297f4f0ab7edcd0aa9c09c`。用户移动的刁钻原料盒完整保留，本次未测试其寻路。v1.1 半成品功能按用户例外使用旧简单单人场地测试，不在 PCL 内运行测试；本 PCL 模组继续为 1.0.4。见 [基准说明](201_TEST_WORLD.md)。

2026-10-25 整理存档交接：独立版本 `26.2-自动打装备` 的单人列表新增 `201交易所-待整理-20261025`。用户授权只读复制 201 末地交易所，随后要求先自行整理物品，再保存基准。导入单人核验通过：94 名村民、549 项报价、208 个方块实体及 128×128 全高度区域；创造模式、允许命令和末地出生点供用户编辑。6 个配置和 92 个原有受保护文件不变，AEM jar 仍为下面的 1.0.4。没有启动 PCL；本次独立取样客户端正常退出。最终基准尚未建立，不得覆盖用户整理结果。见 [存档交付记录](201_TEST_WORLD.md)。

2026-10-24 当前交接：用户要求今晚停止排查并恢复 Alpha 寻路。当前安装 AEM `1.0.4` / `alpha-navigation-rollback-20261024`，保留设置、缓存修复和取消重开。SHA-256 为 `7c5869568eb7c0ebc3201e86ed5b07416868f55105079fab0e1ed8a2388fe624`。编译和静态检查通过；本次未运行游戏测试，未启动客户端。配置目录内 6 个文件和 156 个受保护文件哈希不变，模组仍为 5 个。旧包与完整配置备份在 `runtime/pcl-equipment-client/backups/v103-before-v104-alpha-navigation-20261024`。见 [回退记录](V1_ALPHA_NAVIGATION_ROLLBACK.md)。以下 1.0.3 及更早记录均为历史。

2026-10-24 最新交接：当前安装 AEM `1.0.3` / `cancel-restart-fix-20261024`。修正薄地面与边缘站立误判；`Ctrl+F9` 或 `/aem cancel` 取消旧任务，再按 `F8` 重新扫描开工。25 项导航场景和取消重开后的完整生产通过（14 件、1 盒、319 级）。SHA-256 为 `b53f1e5e2c772786ac0b3bb3714451508297b3fe7f460017ec18ba8b703fa9b5`。配置目录内 6 个文件和 153 个受保护文件哈希不变，模组仍为 5 个。备份在 `runtime/pcl-equipment-client/backups/v102-before-v103-restartfix-20261024`。未启动客户端。见 [本次修复](V1_CANCEL_RESTART.md)。以下 1.0.2 及更早记录为历史。

2026-10-24 最新交接：当前安装 AEM `1.0.2` / `trapdoor-cornerfix-20261024`，修正竖直打开的活板门旁偏移转弯后反复重算。22 项导航和完整制作回归（14 件、1 盒、319 级）通过。jar SHA-256 为 `c162872734cc25d50b61baae53003e706667a92231848cbd23a5aa1f2ddd35d9`。6 个配置目录内文件和 151 个受保护文件哈希不变，模组仍为 5 个。旧包和完整配置备份在 `runtime/pcl-equipment-client/backups/v101-before-v102-trapdoorfix-20261024`。没有启动客户端。见 [活板门旁的寻路恢复](V1_TRAPDOOR_RECOVERY.md)。以下 1.0.1 及更早记录为历史。

2026-10-24 最新修复交接：当前安装 AEM `1.0.1` / `servux-cachefix-20261024`，修正 Servux 缓存未命中误报 UUID。安装包 SHA-256 为 `62dd6a31099449c7137f4f02ada20c82ecb976ebb22278836cf07246348cfa06`。两个定向单人套件及构建、包校验通过；本次未重跑完整生产。用户配置保持原样：配置目录内 6 个文件、全部 149 个受保护文件哈希不变，模组仍为 5 个。旧包和配置备份在 `runtime/pcl-equipment-client/backups/v100-before-v101-cachefix-20261024`。没有启动客户端。见 [本次修复与同步](V1_SERVUX_CACHE_MISS.md)。以下 1.0 和 RC 记录为历史。

2026-10-24 正式版交接：用户授权测试后同步正式版 1.0。当前安装 `auto-equipment-making-client-1.0.0.jar`，SHA-256 为 `df53234249c4377a09c9ef8a42c8f25f2c65cc54275f9467eeec0654dd2d3ca0`。同一构建通过 17 项导航场景和完整制作回归（14 件、1 盒、319 级）。3 个配置文件及全部 81 个受保护文件哈希不变，模组仍为 5 个。未启动 PCL，未连接正式服。旧 RC.6 备份在 `runtime/pcl-equipment-client/backups/rc6-before-v10-navigation-20261024`。见 [寻路实现与验证](V1_NAVIGATION.md) 和 `docs/evidence/navigation-v1/pcl-update.json`。

以下 RC.4–RC.6 记录为历史。2026-10-24 RC.6 交接：RC.6 修订 `rc6-edgefix-20261024` 保留在本版本，工坊及另外两个配置文件哈希保持不变。后续授权的定向测试在项目独立单人客户端通过，涵盖动态报价、暂存满后 good 和 Servux 接收/缓存校验；本 PCL 客户端未启动，未连接正式服。测试构建与本版本 jar 哈希一致。见 [本次修复与验证](V11_RC6_EDGE_FIXES.md)。

用户于 2026-10-24 明确要求在原 PCL 目录中新建独立版本 `26.2-自动打装备`。本次授权仅配置这个新版本，不覆盖原版本，不启动客户端或游戏测试。

## 位置与版本

- PCL：`D:/MC/PCL 正式版 2.10.6/Plain Craft Launcher 2.exe`。
- 游戏目录：`D:/MC/PCL 正式版 2.10.6/.minecraft/versions/26.2-自动打装备`。
- Minecraft 26.2、Fabric Loader 0.19.5、Java 25。
- AEM 1.1.0-alpha.2（半成品、碰撞形状寻路与自动疾跑）、Fabric API 0.158.0+26.2。
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
| Ctrl+F9 | 取消旧任务；下次开始重新扫描 |
| 聊天 `pause` / `continue` | 暂停 / 继续 |
| 聊天 `good` | 补货完成后继续 |

## 安装与验证

打包脚本：`.tools/package_pcl_client.py`。脚本先在 `runtime/pcl-equipment-client` 创建安装包，再以 `--install` 独占创建目标版本。目标目录已存在时拒绝安装。

初装 RC.4 的静态检查包括游戏包 SHA-1、Windows x64 所需库及资源内容校验、全部安装文件 SHA-256、模组元数据，以及原版本 JSON 和 PCL 版本配置不变。记录见 `docs/evidence/pcl-equipment-client.json`。

随后用户授权独立单人测试。RC.4 复现零碎采购，RC.5 修正后，全书交易和少量缺货两个场景均通过完整交付。新版本中的 AEM jar 同步为 RC.5，SHA-256 为 `671266e0caf3411b199a7a62cc926f322682d22547d63f2a95dbeb1e950e9e16`，不含游戏测试类。旧 RC.4、原版本用途说明和设置备份位于 `runtime/pcl-equipment-client/backups/rc4-before-rc5`。

以上历史测试使用项目独立单人运行目录，不启动这个 PCL 版本或连接正式服务器。RC.5 安装记录为 `docs/evidence/pcl-equipment-client-rc5-update.json`。

历史 RC.6 安装由 `.tools/v11/install_pcl_rc6.ps1` 完成，仅替换 AEM jar 并更新版本用途说明。三个配置文件全部保持不变，模组仍为五个；旧 RC.5、Setup.ini 和用途说明备份至 `runtime/pcl-equipment-client/backups/rc5-before-rc6`。本次构建只编译和静态检查，未启动客户端或测试。当前结果见 `docs/evidence/pcl-equipment-client-rc6-update.json` 和 [边界修复记录](V11_RC6_EDGE_FIXES.md)。
