# 配置与清单契约

本目录随客户端模组打包。首次运行时，配置读取器将示例写到独立游戏目录的 config/auto-equipment-making/workshop.json。不会修改服务端配置。

## 文件

| 文件 | 含义 |
|---|---|
| kits/newcomer-equipment-v1.json | v1.0 的 14 件装备，逐件列出完整附魔 |
| kits/newcomer-supplies-v1_2.json | 在装备盒上增加六种补给；extends 按 kit id 引用 |
| kits/material-budget-v1.json | 从零生产的基础物资与 55 本书预算 |
| examples/workshop.example.json | 场地、库存、协议、控制、交易与物流配置示例 |

装备清单是规范化目标，不是要直接赋给物品的 NBT。所有物品必须通过游戏交互取得和制作。

## 字段规则

- 所有根对象必须有正整数 schemaVersion，当前为 1。
- 物品和附魔使用带命名空间的 ID；不接受翻译名作为键。
- 装备 count 固定为 1；槽位为 0–26 的唯一整数。
- enchantments 是完整目标集合，不是附加集合；等级必须在运行注册表范围内。
- extends 必须引用唯一已有 kit id，不允许循环引用或重复槽位。
- 完整礼包的槽位 0–13 来自装备清单，槽位 14–19 来自补给清单。
- 末影珍珠数量为 16，其他堆叠数量须结合物品组件最大堆叠验证。
- boxColorPolicy=light_blue 表示产物盒必须为淡蓝色。placedPackaging 表示启动前已放置的空盒，不计入原料取物预算。
- budget.books 的一条记录表示单附魔书；enchantment + level 唯一。
- 坐标对象约定为 `{ "x": 0, "y": 64, "z": 0 }`；这些只是结构例子，不代表现场坐标。
- 边界对象约定为 `{ "min": {"x":0,"y":64,"z":0}, "max": {"x":10,"y":70,"z":10} }`，两端包含。
- workstations 中每项为方块坐标。站位使用坐标中心，并由导航验证。
- protectedSlots 使用玩家背包命名空间，不是当前菜单槽位 ID。装备栏和副手始终保护。
- minimumFreeSlots 是预检下限，执行时还须计算当前动作的实际空间需求。

## 启动条件

示例未填写必需坐标，不能直接启动生产。`configured` 是规划阶段的标记；当前启动检查以实际绑定和坐标为准。必需坐标使用 null，不填假的服务器地址或实际坐标。

v1.0 必须填入 serverKey、platformBounds、工作站、交付箱。启动附近 11×11×3 的空淡蓝色盒决定套数。旧 packing.placement 与 standPosition 不控制生产盒位置。还必须完成 Servux 和铁砧基准验证。

v1.1 启用 trading 时，必须配置村民区域、绿宝石和书的来源、价格上限、单盒预算。null 预算不代表无限预算。

v1.2 启用 logistics 时，必须配置命令、仓库维度、到达区域、仓库扫描边界、打包点、交付箱。命令模板限定配置的固定命令；不得从容器物品名称拼接任意命令。

v1.0 不自动放置打包盒；packing.mayBreakTaskBoxOnly=true 只允许回收日志绑定的任务盒。导航本身不挖、不搭桥。

规则名未知时保持 null。blockedShulkerOpeningConfirmedByUser=true 记录用户事实，不代表模组已经完成服务器运行验证。

## 一致性

PRD、装备清单与预算必须一起修改。增加附魔或物品后重新核算书数、材料数、槽位容量和最优规划支持域。本轮静态检查结果见 `docs/VALIDATION.md`。
