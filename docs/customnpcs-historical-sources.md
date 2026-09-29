# CustomNPCs 历史源码检索与本地目录

核查日期：2026-09-29。此前仅确认“没有找到精确匹配 Goodbird NeoForge 1.21.1.20251230 的完整工程”，检索范围过窄。历史公开完整源码确实存在，应该作为后续维护的重要材料，而不能仅依赖反编译。公开可读与拥有宽松开源许可是两件事。

本地根目录：`D:\CPN\customnpcs-historical-sources`。`repos/` 保存 10 个仓库，`snapshots/` 保存独立可读快照，`archives/` 保留下载原件，`source-lock.json` 锁定提交与分支。服务端仓库只提交本报告和来源清单，不再分发第三方源码。

## 已下载与核查

| 来源 | MC 版本 | 类型 | Java 文件 | 与现用包同路径类 |
|---|---|---|---:|---:|
| [BetaZavr--CustomNPCs-Unofficial](https://github.com/BetaZavr/CustomNPCs-Unofficial.git) | 1.20.1 / 1.12.2 | 完整模组工程 | 1576 | 836 |
| [CannibalVox--CustomNPCs](https://github.com/CannibalVox/CustomNPCs.git) | 1.7.10 | 历史模组工程 | 708 | 294 |
| [gaetsiffert--customnpcs-extended-compat-1.21.1](https://github.com/gaetsiffert/customnpcs-extended-compat-1.21.1.git) | 1.21.1 NeoForge | 独立兼容补丁 | 80 | 0 |
| [Goodbird-git--CustomNPCPlus1.16.5](https://github.com/Goodbird-git/CustomNPCPlus1.16.5.git) | 1.16.5 | 覆盖修改工程 + sources JAR | 21 | 20 |
| [KAMKEEL--CustomNPC-Plus](https://github.com/KAMKEEL/CustomNPC-Plus.git) | 1.7.10 | 完整模组工程 | 1821 | 359 |
| [mchhui--CustomNPCsFix](https://github.com/mchhui/CustomNPCsFix.git) | 1.12.2 / 1.7.10 | 独立修复补丁 | 76 | 0 |
| [Noppes--cnpcs-scripting-examples](https://github.com/Noppes/cnpcs-scripting-examples.git) | 1.12.2 / 1.16.5 | 原作者脚本示例 | 0 | 0 |
| [Noppes--CustomNPCsAPI](https://github.com/Noppes/CustomNPCsAPI.git) | 1.8–1.18 多分支 | 原作者 API | 128 | 0 |
| [postyizhan--CustomNPCs-164](https://github.com/postyizhan/CustomNPCs-164.git) | 1.6.4 | 完整模组工程 | 598 | 214 |
| [XiaoBiniaoX--CNPC-plus-Fix](https://github.com/XiaoBiniaoX/CNPC-plus-Fix.git) | 1.20.1 | 独立扩展补丁 | 235 | 0 |

Java 数量为当前检出提交的静态统计，包含工程自带辅助类；同路径数仅用于定位对照，不表示行为一致或可直接移植。

此外已下载 [2022 年公开帖子附带的源码 ZIP](https://pixelmonmod.com/tracker.php?p=2&t=18820)，解压至 `snapshots/Noppes-1.16-public-archive`。含 923 个 Java 文件、资源、API 和 ForgeGradle 4.1 构建脚本，脚本目标是 MC 1.16.5 / Java 8。帖子报告者称源码由 Noppes 提供；该来源尚未获得原作者独立确认，不将它冒称为经过认证的官方发布源码。原件 SHA256：`352ae76d49b88340965a8ed0548a25bdd3dc11f7b3acf0bd91b388bf0e4fd83d`。

BetaZavr 当前检出 `1.20.1` 分支；1.12.2 的 master 快照在 `snapshots/` 中。已核实 1.20.1 的 CustomNpcs 主类、实体、控制器及 Forge 构建文件存在，实际构建配置为 Java 17、Forge 47.3.12、ForgeGradle 6.0.44。README 中源码未公开的旧文字不能替代对实际分支树的检查。

## 各来源的边界

- **BetaZavr--CustomNPCs-Unofficial**：1.20.1 gradle.properties 标记 All Rights Reserved；master README 的 CC BY-NC 标识与新版元数据需区分。README 未公开声明已落后于实际分支。
- **CannibalVox--CustomNPCs**：README 自述依照 CC BY-NC 托管；非原作者仓库，授权来源待核实。
- **gaetsiffert--customnpcs-extended-compat-1.21.1**：MIT 标记；依赖 CurseForge 7411561，与本服目标发布包一致。仍需独立评估与 muxi-game-core 的补丁冲突。
- **Goodbird-git--CustomNPCPlus1.16.5**：master 只有 21 个覆盖修改类，其他分支 22–73 个；构建依赖并拷贝预编译 JAR，不是完整独立源码工程。lib 内另带 1602 个 Java 文件的 deobf-sources.jar，生成来源未证实，不能视作作者原始源码。LICENSE 仅为模板文字。
- **KAMKEEL--CustomNPC-Plus**：自定义限制许可，限制完整再分发；标签 1.21 等是模组版本，不是 Minecraft 版本。
- **mchhui--CustomNPCsFix**：coremod/ASM 修复工程，不是完整模组。
- **Noppes--cnpcs-scripting-examples**：仅脚本和编辑器提示，不是完整模组。
- **Noppes--CustomNPCsAPI**：仅接口，不是完整模组；各分支含版本差异。
- **postyizhan--CustomNPCs-164**：PolyForm Noncommercial 1.0.0；README 声称获得 Noppes 授权，尚未独立核实该授权。
- **XiaoBiniaoX--CNPC-plus-Fix**：MIT 标记仅涉及本工程；依赖 libs 中 CustomNPCs 原包，不能替代完整源码。

## 后续如何使用

1. 1.16.5 历史存档：帮助恢复原有类型、构建方式、访问变换及 NPC 数据结构。Goodbird 的覆盖修改工程另用于研究维护者的局部改动，不能误当完整独立源码。
2. BetaZavr 1.20.1：优先研究较新的 Minecraft API 适配与工程组织；它是另一条维护分支，不能假定与 Goodbird 1.21.1 功能、网络或保存格式相同。
3. KAMKEEL 1.7.10 及其他旧分支：参考功能设计和缺陷修复历史，不整包覆盖现代工程。
4. 1.21.1 独立兼容工程：参考当前平台如何实现补丁、编译与 Mixin；安装前仍需核查与本服已有补丁的重叠。当前只下载，没有安装。
5. 当前已安装 JAR 的反编译基线继续保留，作为实际行为对照。历史源码与当前字节码互相核对后，再按缺陷逐项恢复构建、修复和验证，不改包名、序列化字段或协议来追求表面编译成功。

## 验证与检索范围

核对了所有克隆的 Git 提交、远程分支和标签、工作树状态、Java 文件及构建入口；所有克隆工作树干净。使用非浅克隆保留提交历史，但 `--filter=blob:none` 意味着未检出的历史文件内容可能仍需联网读取。旧 Gradle 工程本轮没有执行构建，不宣称它们已经可编译或可迁入当前服务器。

检索涵盖原作者公开 API/脚本工程、Goodbird 历史工程、BetaZavr 分支、CustomNPC+、早期社区分支及当前平台兼容工程。GitHub 中同名 Spigot 插件、纯脚本库、重复 fork 没有算作完整模组源码。本轮不是全网所有历史版本均已找到的证明。

[BRP 1.16.5 发布页](https://www.curseforge.com/minecraft/mc-mods/custom-npcs-brp) 有源码构建说明，但本轮未确认可下载源码入口，不能算已取得源码。目标 Goodbird 1.21.1.20251230 完整工程仍未找到；1.18/1.19 的发布文件或 API 分支也不能算完整源码。

## 复查固定提交

每个工程的 URL、完整提交号、分支与标签在同目录来源清单中。重取时先 `git clone --filter=blob:none <remote> <directory>`，再 `git -C <directory> checkout --detach <commit>`。研究其他分支前先确认工作树干净。压缩包以锁定 URL 下载并核对 SHA256。
