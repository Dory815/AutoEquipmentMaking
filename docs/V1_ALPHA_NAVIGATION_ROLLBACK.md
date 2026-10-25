# 1.0.4：回退 Alpha 寻路

2026-10-24，用户报告再次卡死，要求恢复 Alpha 寻路，明天再排查。此次不继续诊断原因，不启动游戏测试、PCL 或正式服连接。

## 改动

恢复 Alpha 1.3 的广度优先搜索、逐点行走、单格上下寻路、旧站立判断及掉落盒拾取坐标。移除新寻路的自动疾跑、跳跃提升 II 两格跳跃优化和自动跳跃拦截。普通游戏操作仍按 Minecraft 设置执行。

只回退寻路相关逻辑。保留正式版设置界面、Servux 缓存缺失与过期修复、`Ctrl+F9` / `/aem cancel` 取消旧任务后重新扫描开工。取消任务仍保留现场物资，新任务仍保护初始背包。

新实现和测试暂存在 `client/src/experimentalNavigation/`，不参与编译或分发。回退前 Robot 快照和本次检查在 `docs/evidence/v1-alpha-navigation-rollback/`。先前失败与通过的运行证据保持原样，不能用于证明本回退包通过游戏验证。

## 验证

- 已验证：Java 25 离线 `build :client:compileGametestJava` 通过。9 项引擎测试沿用 Gradle `UP-TO-DATE` 结果；本次未重跑。
- 已验证：恢复的 `walkable` 和 `Navigator` 源码块与优化前备份完全一致。使用 `javap -p -c` 比较 `Robot$Navigator`，除常量池编号外，与 Alpha 1.3 原包字节码完全一致。
- Alpha 1.3 原包 SHA-256：`7ab97452aec57a1f40ec697f3d78e97d5cfa6998a6bb91636e34d8cba26b1765`。
- 已验证：分发包 61 个客户端 class 和内嵌 7 个引擎 class 与编译输出逐字节一致。包内没有游戏测试、新寻路类或旧导航 mixin。
- 未验证：回退包的游戏运行、完整生产和用户现场恢复。用户决定今晚停止故障排查。

构建为 `1.0.4` / `alpha-navigation-rollback-20261024`。jar SHA-256：`7c5869568eb7c0ebc3201e86ed5b07416868f55105079fab0e1ed8a2388fe624`。检查见 `docs/evidence/v1-alpha-navigation-rollback/package-checks.json`。

## PCL 同步

独立版本 `26.2-自动打装备` 安装 `auto-equipment-making-client-1.0.4.jar`。配置目录内 6 个文件和全部 156 个受保护文件哈希不变，模组仍为 5 个。没有启动或关闭客户端，没有连接正式服，没有改写原 PCL 其他版本。

旧 1.0.3 包、完整配置和用途说明备份至 `runtime/pcl-equipment-client/backups/v103-before-v104-alpha-navigation-20261024`。安装证据见 `docs/evidence/v1-alpha-navigation-rollback/pcl-update.json`。本次没有推送 GitHub 或创建新 Release。
