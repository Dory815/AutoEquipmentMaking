# 开发环境与事实来源

核查日期：2026-10-23，Asia/Shanghai。此记录区分本地读取、用户提供、公开资料和未验证的服务器行为。

## 1 本地环境

游戏实例：`D:/MC/PCL 正式版 2.10.6/.minecraft/versions/26.2-Fabric 0.19.3-201与迎新服`。

| 证据 | 核查结果 | 范围 |
|---|---|---|
| `26.2-Fabric 0.19.3.json` | Minecraft 的 Java majorVersion=25；Fabric Loader 依赖为 0.19.5 | 配置文件读取 |
| `logs/latest.log` 第 1 行 | `Loading Minecraft 26.2 with Fabric Loader 0.19.5` | 本地最近启动记录 |
| `26.2-Fabric 0.19.3-201与迎新服.jar!/version.json` | id=26.2，java_version=25，protocol_version=776 | 游戏包内容 |
| `mods/fabric-api-0.158.0+26.2.jar!/fabric.mod.json` | Fabric API 0.158.0+26.2；依赖 Java >=25 | 模组元数据 |
| `mods/servux-fabric-26.2-0.11.2.jar!/fabric.mod.json` | Servux 0.11.2；environment=server | 本地文件，不能单独证明远端安装 |
| Microsoft JDK | 25.0.2+10-LTS | 执行 `java -version` |

本机 JDK 路径：`C:/Users/Lenovo/AppData/Local/Programs/Microsoft/jdk-25.0.2.10-hotspot`。这是本地验证路径，不写入共享构建配置。

PATH 中的 `java.exe` 指向旧 Oracle Java 8 启动器。构建必须显式选择 JDK 25。Gradle 使用项目内 `.gradle-user-home`，不把缓存写到游戏实例。

本机已有 MiniHUD 0.40.7、MaLiLib 0.29.6、Tweakeroo 0.29.5、Item Scroller 0.32.1、Inventory Profiles Next 2.3.4、Litematica 0.28.8。共存测试须覆盖这些客户端模组。

## 2 用户提供的服务端清单

以下为用户在本轮提供的 `server/mods` 文件名。文件名用于兼容性规划；没有访问远程服务器、读取其配置或检查远端 jar 内容。完整原始清单保存于 [server-mods-user-supplied.txt](evidence/server-mods-user-supplied.txt)。

| 关键项目 | 启用文件 | 注意事项 |
|---|---|---|
| Servux | servux-fabric-26.2-0.11.2.jar | 0.11.3.jar.disabled 不生效 |
| Fabric API | fabric-api-0.158.0+26.2.jar | 与客户端相同 |
| Carpet | fabric-carpet-26.2+v260616.jar | 本项目不因其存在就假设已有自动制装逻辑 |
| Carpet 附加包 | AMS、ORG、TIS、Gugle、REMS、ROF | 具体规则值未提供；费用模型必须现场对照 |
| 代理兼容 | FabricProxy-Lite-2.12.0.jar | 验证 Servux 数据包可穿过实际连接路径 |
| 交易相关 | TheExchange-1.0.2-fabric-26.2.jar | 不根据名称猜测其功能；核查实际报价与交互影响 |
| 命令相关 | gugle-carpet-addition、tpc | home 提供者尚未确认，不根据文件名绑定命令 |

用户确认：可以修改 Servux 配置；不能新增服务端模组按当前规划约束处理；潜影盒受方块阻挡仍能打开。规则的确切名字和服务器实际交互尚未验证。

## 3 Minecraft 26.2 本地规则证据

读取游戏 jar 中以下原始数据，而不是套用旧版教程：

- `data/minecraft/enchantment/*.json`：目标附魔最高等级和 `anvil_cost`。
- `data/minecraft/tags/enchantment/tradeable.json` 与 `non_treasure.json`：可交易附魔标签不包括灵魂疾行和迅捷潜行。
- `data/minecraft/recipe/diamond_*.json`：基础装备的材料数量。
- `data/minecraft/recipe/netherite_helmet_smithing.json`：模板、钻石装备与下界合金锭的锻造配方。

本机另有 Minecraft 26.2 源码目录 `D:/MC/McSrc`。本轮查阅：

- `net/minecraft/world/inventory/AnvilMenu.java`：左右惩罚累加、附魔书费用、40 级限制、输出惩罚及扣等级行为。
- `net/minecraft/world/item/Items.java`：末影珍珠 `stacksTo(16)`。

这些证据描述原版基线，不证明当前远端服务器的 Carpet 附加包没有改写规则。M0 必须比对真实菜单费用。

## 4 公开一手来源

| 来源 | 支持范围 |
|---|---|
| [Fabric 26.2 开发说明](https://www.fabricmc.net/2026/06/15/262.html) | 26.2 使用 Loom 1.17 系列；发布时建议 Gradle 9.5.1。本项目选 9.6.1，结果另验 |
| [Fabric 26.1 迁移说明](https://fabricmc.net/2026/03/14/261.html) | JDK 25、未混淆名称、新 Loom 插件、implementation 和 jar 的迁移背景 |
| [Fabric Maven](https://maven.fabricmc.net/net/fabricmc/fabric-loom/) | Loom 制品来源 |
| [Servux 0.11.4 功能说明](https://github.com/sakura-ryoko/servux/blob/26.2-0.11.4/FEATURES.md) | 数据提供者和权限配置设计；不代表 0.11.2 运行验证 |
| [Servux 0.11.4 EntitiesDataProvider](https://github.com/sakura-ryoko/servux/blob/26.2-0.11.4/src/main/java/fi/dy/masa/servux/dataproviders/EntitiesDataProvider.java) | 注册/权限检查、按坐标查询方块实体的参考实现 |
| [Carpet 26.2 EntityPlayerMPFake](https://github.com/gnembon/fabric-carpet/blob/v26.2/src/main/java/carpet/patches/EntityPlayerMPFake.java) | 假人服务器玩家实体的行为和生命周期 |
| [Gradle 9.6.1 发行校验和](https://services.gradle.org/distributions/gradle-9.6.1-bin.zip.sha256) | Wrapper 下载完整性 |
| [Gradle 9.6.1 Wrapper 校验和](https://services.gradle.org/distributions/gradle-9.6.1-wrapper.jar.sha256) | Wrapper jar 来源校验 |

Minecraft Wiki 的 [铁砧机制](https://minecraft.wiki/w/Anvil_mechanics) 与 [交易](https://minecraft.wiki/w/Trading) 页面访问被站点 robots 限制。本轮采用本地 26.2 原始数据与源码核查，未将搜索摘要作为规则的唯一证据。

公开 Servux 0.11.2 标签未取得，因此本轮不写死该版本的通道名、包编号或可调用 API。0.11.2 握手实测是明确的实现前置条件。

## 5 不包含的运行验证

本轮不登录目标服务器、不执行交易、不操作游戏库存、不修改 Carpet/Servux 配置、不测试 home。本轮具体检查结果见 [VALIDATION](VALIDATION.md)。
