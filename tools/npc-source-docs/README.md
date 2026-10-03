# CustomNPCs 本地源码恢复工作区

目标：为后续修复与迁移保存一份可读、可追溯、可重复生成的代码基线。

**这是从已安装 JAR 恢复的代码，不是作者原始源码工程。目前不能作为可直接替换游戏模组的发行版。** 原始直接 javac 诊断保存在 `reports/compile-check.json`；恢复后的完整构建结果以 `reports/rebuild-latest.json` 为准，原作者注释、Git 历史和构建脚本无法从 JAR 原样恢复。

当前版本：`CustomNPCs-Unofficial-NeoForge-1.21.1.20251230.jar`，Minecraft 1.21.1 / NeoForge 21.1.250 / Java 21。SHA256：`6c28d87b215fc1191488194188ec8a39dd908ae7d2d887b7c9d7d463be0a162c`。

## 从哪里开始

1. 阅读 `docs/SOURCE-STATUS.md`：公开源码核查与恢复边界。
2. 阅读 `docs/MODULE-MAP.md`：对话、任务、战斗、脚本、保存、网络和客户端入口。
3. 阅读 `docs/MIGRATION.md`：如何修改、验证与将来迁入官方源码。
4. 查看 `reports/coverage.json` 和 `reports/compile-check.json`：实际覆盖与编译阻碍。
5. 工作代码在 `src/main/java`，资源在 `src/main/resources`。`reference/vineflower` 是不修改的参考；疑难方法可对照 `reference/cfr` 及原始字节码。

## 目录约定

| 目录 | 用途 |
|---|---|
| original | 精确版本 JAR，本地保留并校验；不修改 |
| reference/vineflower | 依赖辅助反编译结果，完整性清单固定；不修改 |
| reference/cfr | 第二种反编译器的只读对照；不作为可编译来源 |
| src/main/java | 可编辑工作副本，保持原包名和公开 API 名称 |
| src/main/resources | 恢复的模组描述、Mixin 配置、语言、模型、纹理、音效等 |
| provenance | 原包及条目散列、依赖散列、元数据、公开来源证据 |
| dependencies | 依赖的内容寻址快照，避免原整合包更新导致基线漂移 |
| reports | 代码索引、覆盖、完整性和编译诊断 |
| patches | 后续修复的说明与补丁；每项对应独立测试 |
| build | 编译检查产物，不是可部署模组 |

## 可重复执行

工具在服务端仓库 `tools/npc_source.py`，工作区默认与服务端仓库平级，例如 `D:\CPN\customnpcs-source`。

```powershell
python D:\CPN\bmc5server\tools\npc_source.py prepare --research --java-home D:\CPN\dependencies\jdk-21.0.12.1+1
python D:\CPN\bmc5server\tools\npc_source.py verify --java-home D:\CPN\dependencies\jdk-21.0.12.1+1
python D:\CPN\bmc5server\tools\npc_source.py index --java-home D:\CPN\dependencies\jdk-21.0.12.1+1
python D:\CPN\bmc5server\tools\npc_source.py compare --java-home D:\CPN\dependencies\jdk-21.0.12.1+1
python D:\CPN\bmc5server\tools\npc_source.py compile-check --java-home D:\CPN\dependencies\jdk-21.0.12.1+1
```

工具拒绝错误的原包散列；工作区必须与运行目录分开；再次 prepare 不覆盖已编辑的源码。依赖与反编译器都记录散列。compile-check 禁用注解处理器，不打包 JAR、不启动游戏、不部署。

compare 首次生成 CFR 对照；已有对照时拒绝覆盖。局部维护可用 `compile-check --source noppes/npcs/controllers/ChunkController.java`，多文件重复指定 `--source`。局部检查将其余类视为原始 JAR 的依赖，不能代替全工程编译；报告分别写入 `focused-compile-check.json` 与 `compile-check.json`。

第一次联网只读取公开元数据和下载指定反编译器；后续完整性检查和编译检查可离线执行。依赖来自本机已安装客户端/服务端，并已在本工作区内快照。跨机器迁移需准备同一依赖快照，或在相同版本整合包旁重新 prepare。

## 维护纪律

- 不修改 original、reference 与其散列清单来掩盖差异。
- 不把反编译成功误报为编译成功，也不把编译成功误报为存档兼容。
- 恢复构建所需的语法修整与游戏行为修复分开提交。
- 文件编码 UTF-8、4 空格缩进，保留反编译器的不确定性标记。
- 修复动态访问权限、Mixin 或泛型问题前，先对照原始字节码；不要为了“编译通过”删除方法或返回空值。
- 当前只是准备迁移，不替换正式服或客户端里的 JAR。


## 完整重打包进展

已用 `tools/npc_rebuild.py` 完成全量编译、固定打包和短时服务端 A/B 验证。见本机 `docs/REBUILD-VALIDATION.md` 或服务端仓库 `docs/customnpcs-rebuild-validation.md`。尚未证明全部方法的严格等价，候选包仅用于隔离验证。
