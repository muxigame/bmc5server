# CustomNPCs 源码恢复与后续维护准备

2026-09-29 核查没有找到可确认匹配 Goodbird NeoForge 1.21.1.20251230 的公开完整源码工程，因此按本服维护者要求建立反编译工作区。此结论是“未找到匹配公开工程”，不是证明作者闭源。核查方式与来源见 [源码状态说明](../tools/npc-source-docs/docs/SOURCE-STATUS.md)。

本机工作区：`D:\CPN\customnpcs-source`。它与服务端、客户端运行目录分开。恢复工具和文档在本仓库；第三方完整恢复代码、游戏依赖、原始 JAR 和资源不加入本仓库。

## 已准备

- 精确版本原包与 SHA256、所有条目的散列、模组元数据及原许可标记。
- Vineflower 1.12.0 主参考和 CFR 0.152 对照，主参考不可变，工作副本单独编辑。
- 1,036 个 Java 源文件、97,353 行主参考源码；1,036 个顶层类均有对应文件。
- 本机整合包依赖的内容寻址快照，避免中文文件名被 Windows Java 参数文件错误解析，也避免依赖随整合包更新漂移。
- 功能模块导航、已验证缺陷入口、完整文件索引、编译诊断与迁移步骤。
- 重复准备不覆盖工作源码；完整性校验能区分原始参考被改动与工作副本的正常编辑。

## 验证结果与限制

- 原包与参考完整性通过；主反编译器未报告方法恢复失败；生成日志无缺失依赖警告。
- 全工程 javac 检查尚未通过：本轮诊断 322 条。主要涉及原构建的访问权限变换未恢复（158 条直接访问错误）、方法重载歧义（118 条）及泛型/Mixin 编译关系。诊断不是运行时 BUG 清单。
- 对 ChunkController、MarkData、ScriptContainer 三个重要类，以原始 JAR 为其余类的依赖，局部编译通过。这证明可以开展针对性维护，不代表完整模组可重新发行。
- 恢复工具的路径穿越拒绝、错误原包拒绝、参考篡改检测与工作改动保留检查通过。
- 重新 prepare 保留已锁定依赖，不随本机整合包变化重新绑定；总计 13 项工具测试通过，4,245 个原运行资源校验通过。隔离 smoke 达到 Done、回环端口可连接、最终退出码 0；停机期间有短暂剩余线程告警，随后正常退出。
- 没有打包或部署替换模组，没有改变游戏行为；本次也没有宣称恢复了原作者注释和历史。

## 使用

需要 Python 3.11+、JDK 21、安装完整的同版本服务端与客户端：

```powershell
python tools/npc_source.py prepare --research --java-home D:\CPN\dependencies\jdk-21.0.12.1+1
python tools/npc_source.py compare --java-home D:\CPN\dependencies\jdk-21.0.12.1+1
python tools/npc_source.py verify --java-home D:\CPN\dependencies\jdk-21.0.12.1+1
python tools/npc_source.py compile-check --java-home D:\CPN\dependencies\jdk-21.0.12.1+1
python tools/npc_source.py compile-check --java-home D:\CPN\dependencies\jdk-21.0.12.1+1 --source noppes/npcs/controllers/ChunkController.java --source noppes/npcs/controllers/data/MarkData.java --source noppes/npcs/controllers/ScriptContainer.java
python -m unittest discover -s tools -p test_npc_source.py -v
```

可用 `--server`、`--client`、`--workspace` 指定路径。工具只接受锁定散列的原包，不会悄悄对新版本继续运行。prepare 不删除已有工作；不完整输出也会停止并提示检查。

完整指南：[工作区说明](../tools/npc-source-docs/README.md)、[模块导航](../tools/npc-source-docs/docs/MODULE-MAP.md)、[迁移路线](../tools/npc-source-docs/docs/MIGRATION.md)。


## 历史源码补充核查（2026-09-29）

扩大检索后，已下载 10 个相关仓库及一份 1.16.5 历史源码存档，覆盖 1.6.4、1.7.10、1.12.2、1.16.5、1.20.1 的完整模组源码路线，以及 API、覆盖修改和独立补丁工程。BetaZavr 的 1.20.1 分支确有完整模组工程，README 的旧说明已经过时。Goodbird 的 1.16.5 仓库则是少量覆盖修改类加预编译依赖，不能混称为完整工程。

详见服务端 `docs/customnpcs-historical-sources.md` 与固定提交清单 `docs/customnpcs-historical-sources.lock.json`。本地历史源码目录为 `D:\CPN\customnpcs-historical-sources`。后续以历史工程提供类型、构建和设计参考，以当前 JAR 及反编译基线核对行为；未找到精确版本源码不等于历史源码不可用。各来源的许可与可信度单独记录。当前未构建旧工程或更换运行模组。


后续已完成全量重打包和短时 A/B 检查，旧的 322 条诊断是恢复前状态。当前进展及等价性边界见 [重打包验证](customnpcs-rebuild-validation.md)。
