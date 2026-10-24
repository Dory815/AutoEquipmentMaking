# v1.1 正式服验证

日期：2026-10-23。用户在单人回归后明确授权本次进入 201，并沿用 lyerskin / bot_lighte。仅使用项目内独立 `runtime/formal-client` 实例。原 PCL/Dory 客户端不在本次操作范围。

## 环境与材料

- Minecraft 26.2 / Java 25 / Fabric Loader 0.19.5。
- Servux 0.11.2、MiniHUD 0.40.4、MaLiLib 0.29.3，远程握手通过。
- RDPMouse 1.0.0 开启，切换键 F7；机器人 F8，急停 F9。
- 开工位置 `(1077,57,21)`。11×11×3 范围内有两个空淡蓝盒：`(1082,57,22)`、`(1082,57,23)`；更远的另外两个盒不计入批次。
- 原料扫描得到 64 个容器、1094 个非空物品栈。存在普通书、绿宝石和绿宝石块。耐久 III 单书库存为 10 本，少于一套所需的 14 本，预计触发正常补购。
- RC.1 任务实际开工快照为 812 级、快捷栏槽 0 有 30 块熟猪排。开工前更早的诊断快照不代表本任务的初始背包。
- 原交付箱中有一个历史成品盒。该盒不计入本次交付。开工前箱内状态见 [容器快照](evidence/formal-v11-before-boxes.json)。

## RC.1：发现扫描超时

任务 `dd1972c5-4d99-49fe-85d2-551f71029a12`。逐个打开菜单的村民发现步骤在第 40/72 名处触发 2400 tick 整体超时，任务停止并要求核对。没有开始装备制作，没有购买交易，没有消耗生产材料；等级保持 812。两次低饥饿进食使熟猪排 30→28。

正式聊天的 `pause` 停止队列，`continue` 恢复原队列。这一检查没有发送 `good`。

证据：[任务日志](evidence/formal-v11-rc1-task.jsonl)、[摘要](evidence/formal-v11-rc1-summary.json)、[停止快照](evidence/formal-v11-rc1-timeout-state.json)、[暂停](evidence/formal-v11-chat-paused.json)、[继续](evidence/formal-v11-chat-resumed.json)。

## Servux 无界面读取

对第 71 号候选村民发送实体 NBT 请求时，生产扫描仅到第 40 号。Servux 返回 `Offers.Recipes` 中 9 条报价，包含商品、材料、价格倍率、需求和使用上限。读取前后均为 `InventoryMenu`。诊断工具只调用 Servux 请求和缓存读取，没有村民交互或菜单操作。

这是在 201 / bot_lighte 上的实际读取结果，不是仅根据文档推断。证据：[Servux 回复](evidence/formal-v11-remote-villager.json)。服务端实现见 [EntitiesDataProvider](https://github.com/sakura-ryoko/servux/blob/26.2/src/main/java/fi/dy/masa/servux/dataproviders/EntitiesDataProvider.java)。NBT 报价仅供发现和规划；购买时仍须重新核对真实交易菜单中的价格与库存。

## RC.2 修复与单人回归

- 通过 Servux 优先读取报价，核对返回 UUID，并等待服务端回复；数据缺失时回退菜单读取。
- 每完成一名村民就更新扫描进度计时，避免持续扫描被整体超时中断。
- 实际交易的菜单核对、选择填料还原、付款和产物确认保持原有事务规则。

独立单人完整生产通过：1 盒、14 件、319 级（1500→1181），实际购买弓、盾牌和力量 V，共 3 笔；两块绿宝石经工作台分解。新增检查确认 `observed=1 opened=0`，报价发现没有打开交易菜单。套件结束后立即归档。

证据：[控制台](evidence/client-v11-rc2-production.txt)、[任务日志](evidence/v11-rc2-production-task.jsonl)、[任务摘要](evidence/v11-rc2-production-summary.json)。

RC.2 生产 jar SHA-256：`ec2acdfb6e3aa5fd02e672cbdaada8145cc5174d1c85d4fb60e29826e14711d1`。

## RC.2 正式批次

任务 `670f7602-2e1d-408e-99ce-670ce8ceac41`。开工 812 级，保护槽 0 的 28 份熟猪排。72 名村民的 559 条报价由 Servux 在约 7.4 秒内读取，`observed=72 opened=0`。首套计划为 344 级。

首套前 11 件完成后，用户确认移动了数个附魔书盒，原 `(1070,60,19)` 原料盒消失。旧取料流程停止，3 件尚未附魔的基础装备保留在背包，11 件成品保留在淡蓝盒。另有一次库存输入被保护规则拒绝；完整菜单、背包、光标与等级对账一致后继续，没有重发消耗事务。

## RC.3：移动原料盒自动恢复

新增 `SourceFetch`：取物点击开始前发现原料容器消失、类型改变、库存不符或打开超时，就关闭空光标菜单、重新扫描，并按实际背包缺口取料。仍有合格物资时不要求 `good`。同一坐标重扫后仍打不开时，通知检查场地并等待 `good`。一旦取物事务开始，继续使用既有确认规则，未确认点击不能重发。

单人三种移动场景均验证任务钻石恰好 40、私人钻石保持 7、彩盒钻石保持 64。完整生产回归交付 14 件 / 1 盒、319 级、3 笔交易。额外阻挡箱场景通过：同一箱子两次打不开后等待 `good`，移除阻挡并发送 `good` 后，恢复取到恰好 40 颗任务钻石。

证据：[移动场景](evidence/client-v11-rc3-source-relocation.txt)、[移动任务日志](evidence/v11-rc3-source-relocation-task.jsonl)、[完整生产](evidence/client-v11-rc3-production.txt)、[生产任务日志](evidence/v11-rc3-production-task.jsonl)。RC.3 jar SHA-256：`30fbd213523c52cc88f80cca610c1b7a07302684751fa5cff13f75213ff184e6`。

正式任务保留 RC.2 进程，在完整现场对账后更新为 RC.3 的取料方法，并把两项尚未取物的原料打开步骤替换为新流程。旧类的其他方法和所有物品事务保留。Dory 在服务器聊天发送的 `pause` 保持有效，补丁没有自行解除手动暂停。收到后续 `continue` 后，机器人自动扫描 61 个容器，找到新位置的书并继续工作。

首盒 14 件完成回收并交付至 `(1066,57,23)` 的槽 0，等级 812→468，与 344 级计划一致。原槽 1 的历史成品不计入本次产量。证据：[暂停现场](evidence/formal-v11-partial-output.json)、[补丁对账](evidence/formal-v11-source-patch.json)、[首盒内容](evidence/formal-v11-first-delivered.json)。

## 整批验收结果

已完成并直接读取交付箱核验：2 盒 / 28 件，每盒 14 件，附魔、耐久、数量及盒内无杂物均合格。新增成品位于末地 `(1066,57,23)` 的槽 0、2（界面第 1、3 格）；原槽 1 的历史成品及其余原有物品与开工前逐项一致。

等级 812→124，实际铁砧费用之和 688，与两次 344 级计划一致。共实际购买 7 本耐久 III，单价依次为 10、10、10、10、9、9、8，共 66 颗绿宝石；报价变化后仍按当前菜单付款。7 块绿宝石通过工作台分解，最终任务余量为 7 颗绿宝石。初始 28 份熟猪排保持原状。任务结束时队列为空、机器人停止、无未确认操作。

原料盒移动后自动重新扫描在本次正式任务实际发生 1 次，恢复后没有重复制作或重复取料。首盒之后的第二盒完整使用新的取料流程。全部日志在任务结束后立即归档。

证据：[完整任务日志](evidence/formal-v11-task.jsonl)、[费用与交易摘要](evidence/formal-v11-summary.json)、[交付箱全部内容](evidence/formal-v11-final-output.json)、[验收检查](evidence/formal-v11-delivery-checks.json)、[结束状态](evidence/formal-v11-final-state.json)。

本轮正式服没有触发村民售罄冷却、弓/盾牌补购、货币缺货或经验缺货。这些路径的验证依据仍为独立单人测试，不能记为本轮正式服覆盖。正式任务采用 RC.2 进程加 RC.3 取料补丁；完整 RC.3 的启动验证与批次生产结果分别记录。
