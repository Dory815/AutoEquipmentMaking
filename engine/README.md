# 规划内核

本模块保持纯 Java，不依赖 Minecraft、Fabric、Servux 或 Carpet。

后续包根为 `io.github.autoequipmentmaking.engine`。当前包含 AnvilPlanner 精确规划器及独立穷举对照测试。

| 包 | 后续职责 |
|---|---|
| model | 装备规格、物品快照、库存位置、版本标识 |
| inventory | 库存索引、候选书分配、数量预留 |
| planner | 配方依赖、铁砧合法性、精确费用搜索 |
| workflow | 单盒状态机、动作计划、故障分类 |
| port | 读取世界、执行交互、记录日志的接口契约 |

接口的数据字段与不变量见 `docs/TECHNICAL_SPEC.md`。Minecraft 物品必须先转换为不可变快照，再进入本模块。不得传入可变 `ItemStack` 或世界对象。
