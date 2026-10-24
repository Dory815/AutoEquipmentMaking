# v1.0 候选版本验证记录

日期：2026-10-23。下列历史记录对应制品 `1.0.0-rc.2`。当前 RC.6 的特殊书升级、正式服验收及库存读取修复，见 [RC.6 验证记录](LOW_LEVEL_BOOKS_VALIDATION.md)。保留下列原始环境与哈希，不用旧版测试替代新版验收。

## 环境与方法

使用 Minecraft 26.2、Fabric Loader 0.19.5、Fabric API 0.158.0+26.2、JDK 25.0.2。独立测试依赖使用 MiniHUD 0.40.7、MaLiLib 0.29.6 和 Servux 0.11.2 的工作区副本。没有修改正式 PCL 实例，没有连接正式服务器。

实际客户端测试在普通生存模式运行。生产场景只生成原料、附魔书、工作站和空盒；装备必须经过真实制作、锻造、铁砧和容器菜单取得。`PackingGameTest` 单独使用预制装备检查包装，不能计入实际生产套数。

## 已执行记录

| 记录 | 范围 | 结果 |
|---|---|---|
| `evidence/client-runtime-6.txt` | 旧版单盒实际生产和拾取交付 | 通过；14 件，消耗 319 级 |
| `evidence/client-runtime-8.txt` | 旧版杂物干扰、较简单台阶、缺货 good 流程 | 通过；该台阶允许绕行，不代替本轮强制跨越台阶 |
| `evidence/client-runtime-9.txt` | 新颜色规则、两盒批量、全宽一格台阶 | 失败；第一盒交付，第二盒 14 件装箱后在台阶前停住，报告“任务盒拾取未确认，不能重复制作” |
| `evidence/build-batch-fix.txt` | RC.2 完整构建和游戏测试源码编译 | 通过 |
| `evidence/client-runtime-10.txt` | 修复后的高台拾取专项 | 通过；从台阶下方拾取并交付预制装备盒 |
| `evidence/client-runtime-10.txt` | 修复后的两盒实际生产和 good 复查 | 通过；连续交付 2 盒、28 件；消耗 638 级；杂物和 good 等待检查全部通过 |

第 9 轮暴露的原因是：挖盒后直接按前进键，拾取阶段没有使用导航和跳跃。RC.2 改为寻找附近有效成品盒掉落物，导航到掉落物所在方块，必要时跳跃；只有在背包确认完整盒子后才交付。无法确认时保留现场并停止，不重做一套。

## 本轮完整测试场景与断言

- 玩家脚下坐标 `(3,65,7)`，统计范围 X=-2..8、Y=64..66、Z=2..12，包含边界。
- 两个空淡蓝色潜影盒位于 `(0,66,5)` 和 `(6,66,5)`，对应两套装备。
- `(7,65,7)` 的淡蓝色盒装有钻石，不计入空盒。
- `(9,65,7)` 的空淡蓝色盒在范围外，不计入，也不挖取。
- 红色潜影盒及木桶内放置可用原料，不进入来源索引；原料来自未染色潜影盒和普通箱子。
- 全宽台阶最高落差一格；测试必须观察到角色到达 Y>=66。
- 私人背包的泥土、圆石、普通书、钻石和箭保持原槽位、种类与数量。
- 原料箱杂物保持原状；附魔书盒放入泥土和箭干扰查找。
- 交付箱必须正好有两个淡蓝色盒；每盒恰好 14 件，种类、完整附魔、数量及耐久全部正确，额外槽位为空。
- 两套共消耗钻石 80、木棍 24、下界合金锭 22、升级模板 22、附魔书 110、基础鞘翅/弓/盾各 2。经验等级预期 1500→862，即每套 319 级。
- 缺货测试分别检查基础材料、附魔书、经验不足；补货和 `goodbye` 均不得自动继续；`good` 必须触发重新检查，仍缺货时再次等待。

## 最终结果与定位

第 10 轮进程退出码为 0，Gradle 输出 `BUILD SUCCESSFUL in 10m 16s`。生产任务 ID 为 `6bc42cae-ce6b-4c3a-ad34-7e84f00a5c6d`。该任务有 28 次 `PACKED`、2 次 `DELIVERED` 和最终 `COMPLETED`；经验等级从 1500 降到 862。逐 tick 的角色高度断言通过（Y≥66）。

精简数据见 [`evidence/batch-validation.json`](evidence/batch-validation.json)。截图见 `client/build/run/clientGameTest/screenshots/0001_newcomer-equipment-complete.png` 和 `0002_shortage-good-confirmed.png`。最后一项缺货测试在确认第三次 good 后恢复到制作阶段，再主动取消以结束夹具；日志末尾的 `CANCELLED` 属于该专项，不是两盒生产失败。

RC.2 SHA-256：`8956acf1bf5f692934064fbaf32ad1b041d4f0df9162f0439213a88e33d02acf`。包检查确认版本号、淡蓝色清单及内嵌 engine 正确，且不含 GameTest 类。

## 离线检查

规划器 JUnit 测试共 4 项，包含独立全配对穷举对照、14 件装备权重、24 组随机初始惩罚、胸甲算例、40 级限制和规划超时。报告位于 `engine/build/test-results/test/TEST-io.github.autoequipmentmaking.engine.planner.PlannerTest.xml`。

静态清单检查确认 14 件装备、55 本书及逐附魔数量一致。产物配置为 `minecraft:light_blue_shulker_box`；预算中的盒子属于已放置包装，不从原料箱取用。

## 复现命令

```powershell
$env:JAVA_HOME='C:/Users/Lenovo/AppData/Local/Programs/Microsoft/jdk-25.0.2.10-hotspot'
$env:GRADLE_USER_HOME="$PWD/.gradle-user-home"
./gradlew.bat --offline --no-daemon --max-workers=2 build :client:compileGametestJava
./gradlew.bat --offline --no-daemon --max-workers=2 '-PmcInstance=D:/MC/AutoEquipmentMaking/.test-instance' :client:runClientGameTest
```

游戏测试会打开独立客户端，测试入口位于 `client/src/gametest/resources/fabric.mod.json`。工作目录和截图位于 `client/build/run/clientGameTest`。

## 尚未验证

远程 Servux 协议、代理转发、正式服 Carpet 附加包、正式服聊天格式及权限尚未验证。单人测试通过不代表正式服兼容性通过。当前候选版本仍需人工核对掉线或重启时的中间资产，不提供自动重建整个任务队列的恢复器。
