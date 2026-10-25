# 正式版 1.0：Servux 缓存未命中误报 UUID

日期：2026-10-24。

后续的 1.0.2 包含此缓存修复，当前 PCL 同步状态见 [活板门转弯修复](V1_TRAPDOOR_RECOVERY.md)。下文保留 1.0.1 当次构建与安装证据。

## 故障证据

用户 PCL 独立版本的任务记录在 `SCANNING TRADES` 阶段停止，错误为 `Servux 村民回复缺少有效 UUID`。发生在扫描村民期间的暂停和恢复之后；该任务尚未购买物品或制作装备。当前 MiniHUD 配置的 `entityDataSyncCacheTimeout` 为 2.75 秒，`entityDataSyncBackup` 为 false。

原始记录只读归档在本地 `docs/evidence/v1-uuid-investigation/task.jsonl`。没有启动、控制或连接正式服务器，也没有修改 PCL 的文件。

## 原因

MaLiLib 0.29.3 的 `EntityDataCache.getEntityNbtFromCache` 在缓存未命中时返回一个空的 `CompoundTag`，而不是 null。原 `RemoteOffers.read` 只检查 null，随后对空数据检查 UUID，导致缺少 UUID 的报错。缓存过期会移除条目，但先前回复已经清除等待标记。因此，恢复后会进入该错误分支。

通过本地依赖字节码确认缓存行为，并在独立单人存档中使用实际 MiniHUD/MaLiLib 接收器复现相同报错。用户现场没有原始网络回复快照，不能声称直接捕获了该次缓存删除；暂停时间、配置和报错与已复现路径一致。

同一问题也影响 `RemoteInventory.read`：缓存丢失会被误判为真实的空箱或空的双箱一半。容器扫描和产物盒计数因此也必须区分“没有回复缓存”与“服务端确认空库存”。

## 修复

- `RemoteOffers` 和 `RemoteInventory` 先读取可返回 null 的 `get*DataEntryFromCache`，确认实际缓存条目存在，再读取 NBT。
- `ServuxObserver` 在缓存缺失且没有请求等待回复时重新请求数据。等待回复期间不重复请求；双箱同时重新确认两半。
- 保留已有 UUID、实体类型、容器坐标和菜单校验。存在真实缓存条目但缺少 UUID、UUID 不符或报价格式错误时，仍停止任务。
- 在开始任务的接口检查中加入缓存条目 API 校验。

## 验证

- `before-fix`：独立单人测试失败，实际输出 `java.lang.IllegalStateException: Servux 村民回复缺少有效 UUID`，证明缓存未命中可以触发现场相同错误。
- `after-fix`：`servux-protocol` 独立单人套件通过并正常退出。新增覆盖初始缓存缺失、实际缓存到期删除，以及真实回复缺少 UUID。保留 UUID 不匹配、报价格式、握手和连接隔离校验。
- `inventory-after-fix`：`remote-inventory` 独立单人套件通过并正常退出。覆盖空箱、稀疏槽位、双箱、缓存丢失、过期后只重新请求一次，以及双箱缺失一半时同时刷新两半。

## 编译与 PCL 同步

随后用户明确要求编译打包并同步 PCL，保留配置。修复版版本号为 `1.0.1`，修订为 `servux-cachefix-20261024`。构建 `build :client:compileGametestJava` 通过；分发包的 66 个客户端 class、内嵌引擎的 7 个 class 与编译输出一致，不含游戏测试。修复源码哈希与两个通过的单人套件一致；9 项引擎测试通过。仅修改版本元数据后打包，没有重跑完整生产测试。

安装包 `client/build/libs/auto-equipment-making-client-1.0.1.jar` 的 SHA-256 为 `62dd6a31099449c7137f4f02ada20c82ecb976ebb22278836cf07246348cfa06`。同步到 PCL 独立版本 `26.2-自动打装备`，6 个配置目录内文件及全部 149 个受保护文件哈希不变，模组仍为 5 个。只替换 AEM jar 并更新用途说明，没有启动客户端。

旧 1.0 jar、用途说明和完整配置备份在 `runtime/pcl-equipment-client/backups/v100-before-v101-cachefix-20261024`。构建、包校验和安装证据分别为本地 `final-build.txt`、`package-checks.json`、`pcl-update.json`，均位于 `docs/evidence/v1-uuid-investigation/`。

GitHub 正式版 1.0 Release 保持原包；本次没有推送或发布新 Release，没有验证真实远程网络恢复。
