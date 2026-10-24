# Fabric 客户端适配

后续包根为 `io.github.autoequipmentmaking.client`。当前入口为 AutoEquipmentClient；生产状态机为 Robot。实现和运行边界见 docs/V1_IMPLEMENTATION.md。

| 包 | 后续职责 |
|---|---|
| bootstrap | 客户端入口与生命周期 |
| control | 开关、急停、本地命令、进度显示 |
| servux | 0.11.2 协议探针、请求复用、响应转换 |
| world | 区域扫描、站位、路径搜索 |
| interaction | 槽位操作、配方、锻造、铁砧、潜影盒 |
| persistence | 配置、任务日志、重连对账 |
| notification | 聊天消息限频、缺货去重 |
| trading | v1.1 村民报价与交易 |
| logistics | v1.2 home 与仓库补给 |

优先使用 Fabric API。确实需要 Mixin 时，单独记录目标类、用途、版本约束与替代方案。

现有 MiniHUD、Tweakeroo、Item Scroller、Inventory Profiles Next 与本模组共享客户端环境。Servux 通道注册和槽位写操作必须验证冲突。不得把 Servux 服务端 jar 声明为客户端运行依赖。
