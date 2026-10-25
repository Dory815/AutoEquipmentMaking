# 暂存的新寻路实现

用户于 2026-10-24 要求恢复 Alpha 寻路，明天再排查故障。

这里保存 1.0.3 的新寻路代码、自动跳跃控制 mixin 和 25 项导航测试。此目录不是 Gradle source set，不参与编译或打包。对应 mixin 和测试入口均已移除，`navigation` 测试选项暂时停用。

当前生效的寻路是 `Robot.Navigator` 内恢复的 Alpha 1.3 广度优先搜索。取消旧任务、设置界面和缓存修复继续保留。恢复新实现之前，必须重新验证；历史测试通过不代表用户现场卡死已解决。

见 `docs/V1_ALPHA_NAVIGATION_ROLLBACK.md`。
