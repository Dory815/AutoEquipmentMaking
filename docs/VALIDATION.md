# 规划框架阶段验证记录

本文为初始空框架的历史记录。后续功能代码和候选版本不适用本文中的零源码、无入口与制品哈希结论。

日期：2026-10-23。范围：项目规划、配置数据和空构建框架。

## 静态数据检查

已验证：用本地 Minecraft 26.2 游戏 jar 的配方、附魔数据和标签检查配置。

- 14 件装备，其中 11 件下界合金装备。
- 55 本目标等级单附魔书，共 20 种附魔/等级组合。
- 逐件附魔计数与材料预算一致。
- 配方推导结果为 40 颗钻石、12 根木棍。工具配方中的材料标签包含钻石。
- 盾牌含耐久 III 和经验修补 I。弓不含经验修补。
- 所有目标附魔等级均处于原版 26.2 定义的有效范围。
- 完整礼包槽位连续占用 0–19，无重复；末影珍珠配置为 16。
- 原版可交易附魔标签不包含灵魂疾行和迅捷潜行。
- 源码目录中的 Java 文件数为 0。
- 场地示例为 configured=false，保留未知坐标，不会作为可启动配置。

机器可读记录：[scaffold-checks.json](evidence/scaffold-checks.json)。该记录含游戏 jar 的 SHA256 和目标附魔费用数据。

## Wrapper 校验

已验证：本地 Wrapper jar 的 SHA256 与 Gradle 9.6.1 官方值相同。

- Wrapper jar：`497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`。
- 发行 ZIP：`9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14`。
- gradle-wrapper.properties 包含 distributionSha256Sum。

Wrapper 脚本和 jar 取自本机已有项目中的官方 Wrapper。jar 用官方发布校验和重新核验，不复制该项目的功能代码。

## Gradle 构建

已验证：使用 JDK 25.0.2、Gradle Wrapper 9.6.1、Loom 1.17.20 完成两个模块的构建。

执行命令：`./gradlew.bat --no-daemon --max-workers=2 build`。

结果：`BUILD SUCCESSFUL in 36s`，8 个任务执行成功。日志见 [gradle-build.txt](evidence/gradle-build.txt)。

本地验证设置：

- JAVA_HOME 指向 ENVIRONMENT.md 记录的 JDK 25 路径。
- GRADLE_USER_HOME 指向本项目 `.gradle-user-home`。
- GRADLE_RO_DEP_CACHE 指向本机现有 `D:/MC/.gradle-home/caches`，只读复用依赖。
- 当前命令通过本机已有 `127.0.0.1:7897` 代理下载依赖；代理配置没有写入共享构建文件。

首次 Wrapper 下载出现 `timeout (10000ms)`。加入本机代理并将 Wrapper 网络超时设为 60000 ms 后，发行包下载成功。随后部分 Maven 依赖出现 `Remote host terminated the handshake`。降低并发后重试构建成功。没有关闭 TLS 校验，没有更换不可信依赖源。

构建输出包含 Gradle 10 不兼容的弃用提示。当前验证范围固定为 Gradle 9.6.1，未验证 Gradle 10。

## 制品和文档检查

已验证：检查构建 jar 的 ZIP 内容，确认模组版本占位符展开正确。

- 产物：`client/build/libs/auto-equipment-making-client-0.1.0-scaffold.jar`。
- SHA256：`4cc747298ce05ad9a7ecaa39daa4ff0a6fe6ffc0fd5242ded8756e3133930b10`。
- 环境为 client，没有入口类或 Mixin 声明。
- 客户端 jar 内包含 engine jar；两者均没有 Java class 文件。
- 产物只是空框架，不是可以制作装备的模组。

检查记录见 [artifact-checks.json](evidence/artifact-checks.json)。另用 JSON 解析与相对链接检查核对配置和文档，检查通过。

## 未验证的运行行为

本轮没有功能实现，因此未执行：客户端启动、Servux 握手、自动移动、取物、合成、锻造、附魔、交易、传送、装箱、恢复和游戏内验收。

没有连接目标服务器，没有修改 PCL 实例，没有修改服务端配置，没有发送游戏聊天消息。

`test NO-SOURCE` 仅表示没有测试源码，不代表任何业务测试通过。功能用例见 [ACCEPTANCE](ACCEPTANCE.md)。
