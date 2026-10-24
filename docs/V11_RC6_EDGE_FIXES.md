# RC.6 边界修复与 PCL 更新

2026-10-24，用户恢复任务，要求处理动态报价、暂存满后的 good 恢复、远程 Servux 三个路径，并更新 PCL `26.2-自动打装备`。首次修改与安装只做编译及静态检查。随后用户授权独立单人验证；下面记录同一生产构建的运行结果，全程未连接正式服务器。

## 已实现

- **动态报价**：选择报价前、准备付款后、发送结果点击前核对实际商品与两项价格。提交前变化时，将付款退回任务槽并重新打开菜单核价，重新计算本批剩余资金。价格上涨导致背包不足时取消旧批次，重新计算采购批次；计算包含尚未买到的商品槽位。超过预算时关闭菜单并等待 good。预算预检恢复后重新扫描原料与报价。已经发送结果点击后发生数量不一致，仍停止对账，不重发消耗操作。
- **暂存满**：只使用普通箱子、未染色潜影盒的空槽。没有空槽时清除本步骤的旧扫描、导航和打开菜单状态，等待 good。good 后重新扫描整个配置范围，包括新增或移动的原料盒。已确认的存放不重做；移动未确认或光标非空时不得重新排队。每次确认存放后更新步骤计时。
- **Servux**：沿用 MiniHUD 既有接收器，增加可选 Mixin 观察握手，不注册第二个协议通道。要求当前连接的 Servux 0.11.2 / 协议 1 握手；不接受另一连接留下的状态。开始前检查实际使用的缓存、请求跟踪和请求接口。观察绑定当前连接与客户端世界；更换后停止读取旧状态。容器回复校验已有的坐标、类型和 Items 格式，双箱校验相邻两半关系。村民回复必须匹配实体 UUID；非法 Offers 拒绝读取，尚未初始化 Offers 时回退到打开实际菜单。

## 验证状态

首次编译和静态检查通过，执行 `:client:compileGametestJava :client:jar`。检查对象包括安装目标中实际 MiniHUD 0.40.4、MaLiLib 0.29.3 的方法签名和协议常量。随后运行下面两个独立单人套件，均通过。分发包内的 57 个客户端 class 与测试实际使用的编译输出一致，不含游戏测试类。

新增用例的单人运行结果：

| 套件 | 已通过的行为 |
| --- | --- |
| `v11-edges` | 付款完成、取结果之前涨价和降价；涨价超过预算后 good；准确扣款且不超买；满原料箱清出一个空槽后再次等待；新增未染色潜影盒后第二次 good；私人货币与容器杂物保持原状 |
| `servux-protocol` | 握手版本与连接隔离；实际 MiniHUD/MaLiLib NBT 接收接口；等待回复期间不使用占位缓存；UUID 不匹配；未初始化和畸形 Offers；空盒、坐标与类型不匹配；客户端世界更换 |

`servux-protocol` 使用实际 MiniHUD/MaLiLib NBT 接收接口。版本与连接隔离由握手校验函数断言，游戏运行时加载相关 Mixin 未报错。它不建立远程连接，也不测试远程元数据包经过 Mixin 的完整流程；远程网络握手仍未验证。

`v11-edges` 完成 6 笔购买。逐笔菜单扣款为 5、5、5、3、3、1 颗绿宝石，合计 22 颗和 6 本普通书。出现 3 次提交前重新核价、1 次预算等待、2 次暂存等待和 3 次 good 确认。全部 6 本成品书存入原料容器：腾出原箱一格后只存一件，再等待；新增未染色潜影盒后存入其余五件。容器中其他 26 格泥土保持原状。玩家初始 7 颗绿宝石、3 本普通书保持不变。continue 不能绕过 good。最终队列 COMPLETED，没有 RECOVERY_REQUIRED。

首两次交易用例失败属于测试代码：第一次交付位置错误配置成潜影盒，启动校验拒绝；第二次发送 continue 后未等待异步聊天到达就断言结束，误判暂停。分别修正为普通交付箱、等待聊天确认后，第三次通过。失败日志全部保留。本轮没有修改生产代码，没有重新安装 PCL。

运行证据：[审计报告](evidence/v11-rc6-edgefix-runtime-checks.json)、[Servux 单人控制台](evidence/v11-rc6-edgefix-servux-attempt1/console.txt)、[交易与暂存控制台](evidence/v11-rc6-edgefix-procurement-attempt3/console.txt)、[逐步任务日志](evidence/v11-rc6-edgefix-procurement-attempt3/task.jsonl)。每套结束后归档，测试客户端正常退出。没有重跑完整 14 件生产，此次是三个修复路径的定向验证。

证据：[编译日志](evidence/v11-rc6-edgefix-build.txt)、[静态检查](evidence/v11-rc6-edgefix-static-checks.json)、[依赖接口签名](evidence/v11-rc6-edgefix-api-signatures.txt)。

## 安装结果

PCL `26.2-自动打装备` 已从 RC.5 更新为 RC.6。版本号保持 `1.1.0-rc.6`，附加构建标记 `rc6-edgefix-20261024`，用于区分上午已完成游戏测试的 RC.6。

- 文件：`client/build/libs/auto-equipment-making-client-1.1.0-rc.6.jar`。
- 大小：186,519 字节。
- SHA-256：`7ab97452aec57a1f40ec697f3d78e97d5cfa6998a6bb91636e34d8cba26b1765`。
- 目标：`D:/MC/PCL 正式版 2.10.6/.minecraft/versions/26.2-自动打装备/mods`。
- 仅有一个 AEM jar；模组总数仍为 5。其他四个模组未改变。
- 三个配置文件哈希全部不变；连同其他模组和原版本元数据，共核对 75 个受保护文件。
- `workshop.json` 更新前后 SHA-256 均为 `9db93b444afde0ea6082fc1102d2b98a864945819522d07489281552a6f3ddb7`。
- 旧 RC.5、旧版本说明和 Setup.ini 保存在 `runtime/pcl-equipment-client/backups/rc5-before-rc6`。
- 原 PCL/Dory 版本、formal-client 和账号配置未写入。没有启动游戏。

安装证据：[PCL 更新记录](evidence/pcl-equipment-client-rc6-update.json)。该记录保留安装当时的静态验证范围。后续单人测试确认 PCL 中 jar 与测试构建 SHA-256 一致；75 个受保护文件的哈希仍不变，运行结果见上面的审计报告。

上午已完成游戏测试的旧 RC.6 已归档到 `docs/evidence/artifacts/rc6-tested-20261024/`，SHA-256 为 `10666e24b242924e470326c86dd4a67ecd42de65347e22aae76249eec4796b33`。旧测试报告保留原样；其通过状态不能证明本次新构建已通过运行验证。
