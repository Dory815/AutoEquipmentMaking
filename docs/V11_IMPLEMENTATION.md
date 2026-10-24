# v1.1 候选版本运行说明

源码候选版本 `1.1.0-rc.6`，Minecraft 26.2，Java 25，Fabric Loader 0.19.5，Fabric API 0.158.0+26.2。仅客户端模组，不增加服务端依赖。

最新 RC.6 修订 `rc6-edgefix-20261024` 已安装到 PCL 独立版本，配置保持不变。随后授权的动态报价、暂存满后 good、Servux 接收与缓存校验单人套件通过；远程网络握手未验证，未连接正式服。见 [本次修复与验证](V11_RC6_EDGE_FIXES.md)。上午旧 RC.6 通过独立单人交易边界和全书生产验证，交易菜单点击减少 83.0%；该旧制品已归档，见 [交易优化](V11_TRADE_SPEED.md)。下方 RC.5 与更早记录保留对应历史范围。

RC.4 全书交易测试复现背包接近满时的 2 件、1 件零碎采购，人工中止并归档，未计为完整通过。RC.5 修正为先暂存已购物品，再集中领取下一批资金。全书交易、少量缺货两个独立单人场景均完整交付 14 件、1 盒，消耗 319 级。PCL 的 `26.2-自动打装备` 同步为 RC.5。见 [本轮单人验证](V11_RC4_SINGLEPLAYER_VALIDATION.md)。

## 使用

用构建产物 `client/build/libs/auto-equipment-making-client-1.1.0-rc.6.jar` 替换同一实例中的旧 AEM jar；不能同时保留两个 AEM 版本。原有 Servux 读取所需的 MiniHUD 0.40.4、MaLiLib 0.29.3 及 RDPMouse 保留。原料与村民共用配置的平台边界。仍通过 `/aem bind`、`/aem mark corner1`、`corner2`、`craft`、`smith`、`anvil`、`output` 配置工坊。启动周围 11×11×3 内的空淡蓝色潜影盒决定批量套数。

开发回归使用独立单人存档。用户在单人验证后，于 2026-10-23 另行授权使用独立 formal-client / bot_lighte 进入 201 正式服测试 v1.1。本次正式验证的结果单独归档，不沿用 v1.0 结果。

`workshop.json` 自动迁移到 schemaVersion 2。旧场地坐标、原料边界和保留槽不变，首次迁移默认启用交易。以后可设置 `trading.enabled=false`。当前交易配置仅使用 `enabled`、`maxEmeraldsPerTrade`、`maxEmeraldsPerKit`；两个金额上限默认 null（不限）。旧配置中 villagerArea、emeraldSource、bookSource、restockTimeoutSeconds、vanillaNonTradeable 为早期规划字段，本实现不读取。实际村民区域与取料来源统一采用工坊边界。

## 行为

- 钻石制作前汇总整套已知缺口，提前领取本批全部绿宝石和普通书，再集中买齐。缺 3 本各 10 颗的书时预算 30 颗；没有散装货币时分解 4 块。背包不足时分批采购、暂存到合格来源的空槽；全部补齐后才开始制作。运行期间新增的外部缺口仍保留原有恢复规则。
- 缺少单附魔书、弓、盾牌时，通过 Servux 读取边界内村民的 `Offers`。只有没有报价数据或请求无响应时，才尝试打开该村民菜单。每完成一名村民就重置扫描进度计时；实际购买仍重新打开菜单核对商品、价格与库存。弓和盾牌必须满耐久、未附魔、未改名、无铁砧惩罚。没有报价时通知缺货。
- 库存足够的书不额外买替代品。两种特殊书保留高等级优先的合成策略，实际报价可以提供低等级书。普通附魔仍只接受目标等级书。
- 普通书、绿宝石、绿宝石块只从未染色潜影盒和普通箱领取。初始玩家物品不充当交易资金。绿宝石不足时按缺口批量领取绿宝石块，在一次真实工作台菜单内逐块分解，并合并绿宝石堆叠。
- 货币缺货等待真实玩家 `good`。村民售罄则每 20 秒自动复查；第二次仍售罄提醒一次，第三次及以后静默检查；恢复时提醒一次。
- 取物点击开始前，原料盒消失、颜色/类型改变、实际库存不符或打开超时会自动重新扫描，按背包中的实际缺口重新取料。物资在其他合格容器中时无需发送 `good`。已发送的取物点击仍遵守结果确认，不能以重新扫描代替对账。同一坐标重新扫描后仍打不开时提醒检查场地并等待 `good`。
- 四种制作原料一次取齐。工作台一次制作全部钻石装备，锻造台一次升级全部下界合金装备。全部 14 件基础装备先存产物盒，再按背包容量分批取出、配书、附魔并一起放回。
- 饥饿值 ≤14 时，在关闭菜单后的步骤边界吃一份安全食物。支持主背包、快捷栏、副手。支持暂存已占用的任务快捷栏物品。没有安全食物时只提醒一次，不停工。
- 食物限定正营养且无食用效果、无返回容器的食品，金胡萝卜和面包可用。不会自动吃紫颂果、有负面效果的食品或留下碗/瓶子的食品。初始食物允许消费，其余初始物品仍受保护。

## 控制

| 输入 | 行为 |
|---|---|
| F8 / `/aem start` | 开始；F8 在活动任务中切换暂停/继续 |
| F9 / `/aem pause` | 暂停并释放移动、挖掘、进食 |
| 任意玩家 `pause` | 暂停当前队列，保留菜单和光标 |
| 任意玩家 `continue` / `/aem resume` | 通过恢复门禁后继续；不能绕过缺货或不确定操作 |
| 任意玩家 `good` | 确认补货并复查；不解除手动暂停 |

聊天指令匹配独立正文，忽略大小写与首尾空白。无签名服务器支持在线玩家的标准 `<name> command` 格式。自定义前缀/昵称插件格式尚未适配。

## 验证命令

所有命令使用项目 JDK 25、项目 `.gradle-user-home`，并离线读取已缓存依赖。

```powershell
./gradlew.bat --offline --no-daemon --max-workers=2 build
./gradlew.bat --offline --no-daemon --max-workers=2 -PmcInstance=D:/MC/AutoEquipmentMaking/.test-instance -PaemClientTest=v11-production :client:runClientGameTest
./gradlew.bat --offline --no-daemon --max-workers=2 -PmcInstance=D:/MC/AutoEquipmentMaking/.test-instance -PaemClientTest=v11-trading :client:runClientGameTest
./gradlew.bat --offline --no-daemon --max-workers=2 -PmcInstance=D:/MC/AutoEquipmentMaking/.test-instance -PaemClientTest=v11-food :client:runClientGameTest
./gradlew.bat --offline --no-daemon --max-workers=2 -PmcInstance=D:/MC/AutoEquipmentMaking/.test-instance -PaemClientTest=complex :client:runClientGameTest
./gradlew.bat --offline --no-daemon --max-workers=2 -PmcInstance=D:/MC/AutoEquipmentMaking/.test-instance -PaemClientTest=book-upgrade :client:runClientGameTest
```

## 验证范围

本次另行授权的 201 正式批次完成 2 盒 / 28 件，消耗 688 级，7 笔耐久 III 交易，7 块绿宝石分解，并验证移动原料盒后的自动重新扫描。完整证据与未覆盖路径见 [正式服验证](V11_FORMAL_VALIDATION.md)。客户端仍保留断线、外部库存操作、费用不符及结果未确认时停止并人工对账的规则。没有自动执行进程重启后的队列恢复。

## 单人验证记录

| 用例 | 已验证结果 | 证据 |
|---|---|---|
| 完整交易生产 | 14 件、1 盒；319 级；实际购买弓、盾牌、力量 V；2 块绿宝石；分为 5/6/3 件三批 | [控制台](evidence/client-v11-production.txt)、[任务日志](evidence/v11-production-task.jsonl) |
| 复杂地形与杂物 | 28 件、2 盒；638 级；一格高差、抬高书箱、背包杂物、排除彩盒/木桶；自动从原料箱取出并补放 1 个铁砧 | [控制台](evidence/client-v11-complex.txt)、[任务摘要](evidence/v11-complex-summary.json) |
| 低等级特殊书 | 14 件、1 盒；375 级；灵魂疾行 I×4，迅捷潜行 II+I+I；最多 9 本书的靴子批次 | [控制台](evidence/client-v11-book-upgrade.txt)、[任务摘要](evidence/v11-book-upgrade-summary.json) |
| 进食与盒顶交互 | 14 时吃一份；15 时不吃；进食中暂停；全背包/快捷栏拥挤时互换还原；副手；从盒顶中央/边缘/角落离开完整盒盖范围后开盒 | [控制台](evidence/client-v11-food.txt)、[任务摘要](evidence/v11-food-summary.json) |
| 交易边界与高价报价 | 绿宝石/普通书缺货的 good 门禁；pause/continue；第二次售罄提醒、第三次静默、恢复自动提醒；两次 64 绿宝石报价；自定义灵魂疾行 II×2；共 15 块分解为 135 颗，消费 132 颗，任务余量 3 颗；私人 7 颗绿宝石、3 本书保持原状 | [控制台](evidence/client-v11-trading.txt)、[任务摘要](evidence/v11-trading-summary.json) |
| RC.3 完整生产回归 | 14 件 / 1 盒，319 级，真实购买弓、盾牌和力量 V；无界面发现报价 | [控制台](evidence/client-v11-rc3-production.txt)、[任务摘要](evidence/v11-rc3-production-summary.json) |
| RC.3 原料位置变化 | 整盒移动、部分取料后移动、原盒内容转移均自动恢复；每次恰好取 40，私人 7 不变，彩盒 64 不变；持续打不开时等待 good，修复后恢复 | [控制台](evidence/client-v11-rc3-source-relocation.txt)、[任务摘要](evidence/v11-rc3-source-relocation-summary.json) |
| 构建与规划 | 构建通过；纯 Java 测试 9/9；jar 不包含游戏测试类；包含配套 engine 和进食 Mixin | [构建日志](evidence/build-v11-final.txt)、[完整检查](evidence/v11-release-checks.json) |

复杂地形早期运行暴露了盒盖与玩家身体相交的问题。现在导航要求玩家完整身体离开目标方块的平面范围，再打开菜单；三种起始位置的定向测试和两盒回归均通过。另一轮中预放的五个铁砧随机耗尽，机器人正确进入 `good` 等待。补足测试原料箱备用铁砧后，两盒回归通过，并实际验证了自动补放。以上失败控制台保留为历史证据，不计为通过记录。

最终交易测试记录的相邻售罄复查间隔为 20,667ms 和 20,278ms。高价交易在一次工作台菜单中连续分解所需绿宝石块，产物合并到任务堆叠，未挪用个人资金。远程桌面期间日志出现 OpenAL 音频设备丢失；游戏内交易、收支及控制断言全部通过。

候选 jar 大小为 148,405 字节。SHA256：`4ed3c8c3678facc1f90837bbe74ef0c4d61c49e7306d79f24029a92ed831cf2f`。本轮未安装到正式客户端。


