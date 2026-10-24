# 特殊附魔低等级书验收

版本：`1.0.0-rc.6`。日期：2026-10-23。

## 需求与选书策略

仅灵魂疾行、迅捷潜行支持低等级原料。成品仍为 III。优先选 III；没有 III 时选 II+II；只有一本 II 时选 II+I+I；没有 II 时选 I×4。同级优先较低铁砧惩罚。按该选书策略确定原料后，对书与书、书与装备的全部合法有序合并树求最低等级费用。最优性不跨越用户指定的等级优先级，也不跨多盒分配。

库存足量计算为 `III + floor((II + floor(I/2))/2)`，不是直接相加附魔等级。I+II、I×3 都不足以得到 III。其他附魔的低等级书和所有多附魔书不计入合格库存。

## 规划器与执行器改动

- 子集搜索状态增加每种附魔的实际等级。相同子集、不同等级分别保留，不能互相删除。
- 合并费用使用右侧每种附魔的合并后等级。同等级升一级，不同等级取高值，按最高等级封顶；每步费用仍必须小于 40。
- 靴子最多使用 9 本原料书。升级步骤和装备附魔共同优化，不固定先合成 III 级书。
- 相同附魔、等级和惩罚的重复书按总数量取料，每个叶节点绑定独立背包槽。成品检查按真正的等级合并规则校验。

## 已通过的测试

`./gradlew.bat --offline --no-daemon --max-workers=2 build`：7 项 JUnit 测试通过。包括原有 14 件装备权重、随机惩罚、40 级与超时边界，以及新增重复附魔的独立全配对穷举、高等级优先、不足量和最大 9 本书规划。

`./gradlew.bat --offline --no-daemon --max-workers=2 -PmcInstance=D:/MC/AutoEquipmentMaking/.test-instance -PaemClientTest=book-upgrade :client:runClientGameTest`：完整单人生存制作通过。测试只提供原料和工作站，不直接生成最终装备。灵魂疾行仅有 I×4；迅捷潜行仅有 II+I+I。实际菜单制作、下界合金升级、附魔、挖盒、拾取和交付全部执行。

结果：交付 1 个淡蓝色潜影盒，14 件正确装备；消耗 60 本书，60 次铁砧结果确认；计划费用 375 级，实际扣除 375 级；初始 1500 级，最终 1125 级。服务器侧校验全部装备附魔、耐久、数量及盒内余料。证据见 [测试汇总](evidence/book-upgrade-validation.json)、[完整动作日志](evidence/book-upgrade-task.jsonl)、[客户端测试输出](evidence/client-book-upgrade.txt)。

制品 SHA-256：`fdc1be426ff43731af63ba3e4725b8704759703382d5e3cd7ac54bc5d48b6c65`。

`./gradlew.bat --offline --no-daemon --max-workers=2 -PmcInstance=D:/MC/AutoEquipmentMaking/.test-instance -PaemClientTest=upgrade-shortage :client:runClientGameTest`：低等级书缺货与补货复查通过。两种特殊书仅有 I+II 时，中文提示包含各级库存和可合成 III 数量 0。补齐 I 后仍等待；`goodbye` 不恢复；独立的 `good` 触发重新扫描，计划选择 II+I+I。该单人测试通过集成服务器广播在线测试玩家格式的消息，覆盖 GAME 类型聊天兼容分支；正式服 Dory 的实际 `good` 确认另有任务日志。证据见 [测试输出](evidence/client-upgrade-shortage.txt)、[动作日志](evidence/upgrade-shortage-task.jsonl)。

[制品检查](evidence/artifact-upgrade-checks.json)确认正式实例 jar 与构建产物完全一致，发布 jar 不含客户端测试类，内嵌 engine 版本为 RC.6。

## 远程读取与正式实例

正式实例固定 MiniHUD 0.40.4 / MaLiLib 0.29.3，对接 Servux 0.11.2 协议 1。库存读取等待服务器 NBT 回包清除 pending 标志，再解码完整槽位；空盒、尾部空槽和双箱两半分别处理。防止 MiniHUD 本地空占位缓存造成虚假缺货。[远程读取回归](evidence/client-remote-inventory-regression.txt)通过，覆盖占位未确认、真实空盒、稀疏 27 槽、双箱双回包及损坏数据拒绝。

更新前对 bot_lighte 的真实扫描记录只读核对：灵魂疾行 II×12、I×9；迅捷潜行 II×11、I×7；水下速掘 I×12。记录中没有两种特殊附魔的 III 级书。正式两盒测试选择每种特殊附魔 II×2/盒，保留 I 级库存。

正式服第一盒通过完整生产与交付：14 次装箱、57 次铁砧结果确认、1 次交付。等级 1500→1156，计划与实际均为 344 级。成品在末地 `(1066,57,23)` 普通交付箱第 1 格（槽号 0）。通过 Servux 重新读取真实箱内 NBT，另用独立脚本逐槽对照装备清单：14 件、全部目标附魔、满耐久、无额外物品。交付箱原有 13 个未染色盒物品保持原槽位与数量。

成品之后的权威书库存为灵魂疾行 II×10、I×9；迅捷潜行 II×9、I×7；水下速掘 I×11。确认各使用两本 II，I 级库存未消耗。证据见 [交付箱及书盒 NBT](evidence/formal-output-kit1.json)、[独立核验汇总](evidence/formal-rc6-validation.json)、[正式任务日志](evidence/formal-rc6-task.jsonl)。

当时第二盒处于 `WAITING_MATERIAL_OR_XP`：耐久 III 缺 2 本。中文消息正常发到服务器聊天。Dory 发送 `good` 后机器人重新扫描；库存仍不足时再次等待。第一盒成果保留，不重新制作。当时第二盒尚未完成，因此该阶段不计为两盒完整交付。用户补货后任务继续，后续铁砧恢复与高处导航修复见 [RC.7 验证](ANVIL_SUPPLY_VALIDATION.md)。最终两盒全部交付：28 件，688 级；两种特殊书各消耗 II×4，I 级库存保持不变。

单人测试不能替代正式服网络与 Carpet 组合验收。正式实例只使用用户指定的独立目录和 bot_lighte 账号。
