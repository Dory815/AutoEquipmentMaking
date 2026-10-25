**本模组完全是vibe出来的，质量稀碎，目前只有26.2Fabric15.1的版本。如果需要其他版本的可以提issue，可以移植**

**本模组借鉴了yiyihehe大佬的一部分代码，欢迎到qq群713134137交流，github仓库https://github.com/qk-yiyihehe/quickcraft**

# 新人装备自动制作

Minecraft Java Edition 26.2 的 Fabric 客户端模组。机器人读取工坊内的未染色潜影盒与普通箱子，集中采购缺货的附魔书、弓和盾牌，制作下界合金装备，并把成品装入指定颜色的潜影盒。

## 版本

| 发布名称 | Git 标签与模组版本 | 对应历史构建 | 内容 |
| --- | --- | --- | --- |
| Alpha 1.0 | `v1.0.0-alpha.1.0` | 原 v1.0 RC.7 | 制作、附魔、装箱。 |
| Alpha 1.2 | `v1.0.0-alpha.1.2` | 原 v1.1 RC.5 | 加入村民交易；集中采购；进食与聊天控制。 |
| Alpha 1.3 | `v1.0.0-alpha.1.3` | 原 v1.1 RC.6 边界修复版 | 加快交易；修正动态价格、暂存与 Servux 校验。 |
| 正式版 1.0 | `v1.0.0` | 当前源码 | MaLiLib 设置界面；交付箱绑定；可选装备、附魔、交易和铁砧补位；原版寻路、跳跃提升与自动疾跑。 |

原计划的 v1.2 已取消。历史 Alpha 标签保存发布清单；历史源码没有完整 Git 快照。每个历史 Release 的 jar 由对应归档构建只替换 `fabric.mod.json` 内的版本字段得到。具体哈希和验证范围见 [版本记录](docs/RELEASES.md)。

## 正式版 1.0 使用

依赖 Minecraft 26.2、Fabric Loader 0.19.5、Fabric API 0.158.0+26.2、Java 25 和 MaLiLib 0.29.3。MaLiLib 0.29.3 是当前客户端与 MiniHUD 0.40.4 已验证可共存的最低版本；更早版本未验证。服务端按既有配置使用 Servux 0.11.2，无须安装本模组。

按 `F6` 打开设置。默认快捷键：`F8` 开始、`F10` 暂停、`F12` 恢复、`F9` 急停、`B` 绑定交付箱。开始时，模组提示指向一个工坊范围内的箱子并按 `B`。任意玩家发送 `skip` 时，模组选用已绑定的有效箱子；找不到时选最近的有效箱子。旧任务继续使用 `good` 补货确认，以及聊天中的 `pause` 和 `continue`。

设置界面支持手动套数或自动识别。手动套数范围为 1–10；自动识别范围的长度和宽度均为 3–51，高度为 1–21，默认扫描 `21×21×5`。本模组字体倍率可设为 0.1–3.0，默认 1.0，不改变 Minecraft 或其他模组的文字。高倍率时，文字可能超出界面。产物盒方块 ID 列表默认只有 `minecraft:light_blue_shulker_box`，只接受潜影盒。可关闭迅捷潜行、灵魂疾行、鞘翅、盾牌、村民交易和备用铁砧取出。运行中的任务使用启动时的设置快照；新设置从下次开始生效。工坊坐标保存在 `config/auto-equipment-making/workshop.json`，界面设置保存在同目录的 `settings.json`。工坊标记命令和故障恢复见 [运行说明](docs/V11_IMPLEMENTATION.md)。

自动行走使用原版通用 A* 寻路，连续通过平地路径点，并自动疾跑。导航检查碰撞形状、地面支撑、危险方块和目标视线。拥有跳跃提升 II 且净空足够时，可以直接跳上两格台阶；效果消失后重新规划。停止或暂停时退出疾跑，饥饿限制仍按原版规则执行。具体实现与独立单人测试见 [寻路优化](docs/V1_NAVIGATION.md)。

## 构建与验证

使用 JDK 25、Gradle Wrapper 9.6.1 和项目的 `.gradle-user-home`。将 `JAVA_HOME` 指向 JDK 25，将 `GRADLE_USER_HOME` 指向项目 `.gradle-user-home`，执行 `./gradlew.bat --no-daemon build`，成品位于 `client/build/libs/auto-equipment-making-client-1.0.0.jar`。`client/src/gametest` 中的游戏测试不进入成品 jar。

当前验证记录见 [正式版 1.0](docs/RELEASES.md)。历史记录见 [Alpha 1.2 单人验证](docs/V11_RC4_SINGLEPLAYER_VALIDATION.md)、[Alpha 1.3 交易优化](docs/V11_TRADE_SPEED.md)和 [Alpha 1.3 边界修复](docs/V11_RC6_EDGE_FIXES.md)。产品范围见 [PRD](docs/PRD.md)，安全执行约束见 [技术标准](docs/TECHNICAL_SPEC.md)。
