# 公开源码核查与恢复边界

核查日期：2026-09-29。

## 结论

没有找到可确认对应 **Goodbird 的 NeoForge 1.21.1.20251230** 的公开完整源码工程。已核查：

- [官方发布页](https://www.curseforge.com/minecraft/mc-mods/customnpcs-unofficial/files/7411561)：存在目标版本发布文件，未提供对应源码附件。
- [Modrinth 项目 API](https://api.modrinth.com/v2/project/customnpcs-unofficial)：`source_url` 与 `issues_url` 均为空。
- [维护者公开仓库 API](https://api.github.com/users/Goodbird-git/repos?per_page=100)：返回 26 个公开仓库，未出现匹配的 NeoForge 工程。
- CustomNPCPlus1.16.5、CustomNPC-Plus-Contribution、CustomNPC-Plus-API-Contribution 的公开分支名，以及 GitHub 对 CustomNPCs 1.21.1 的仓库搜索；没有确认到目标完整工程。

这不是“已经证明作者闭源”。私有仓库、未列出的托管位置或作者单独提供源码，均不在本次证据范围内。因此按用户指定的备用路线进行反编译准备。

原始 JSON 响应、查询地址和时间保存在 `provenance/public-source-check`。名称相似的 BetaZavr 分支、1.16.5 工程、API 文档、第三方兼容补丁都不能直接当作这个版本的原始源码。

## 恢复方法

主参考使用 [Vineflower 1.12.0](https://github.com/Vineflower/vineflower/releases/tag/1.12.0)，工具 JAR SHA256：`1dfcfe974395734fa467ce620661c7623d05ba83670de0529b1fbd63ff548b9d`。使用 Java 21、4 个反编译线程、4 空格缩进、原有局部变量信息与现有整合包依赖辅助类型恢复。

辅助参考为上轮审计使用的 CFR 0.152。两者解释不同的地方，应使用 JDK `javap -c -p -s` 检查原始字节码，不能凭代码看起来更漂亮就决定哪一份正确。

恢复出的源文件可阅读、检索、定位与局部维护，但不保留原作者注释或开发历史。`provenance/working-baseline.json` 记录刚恢复的工作树，便于后续精确识别改动。

## 权属记录

保留 JAR 原作者与资源信息：Noppes、Goodbird，以及包内原有贡献者记录。JAR 元数据标记 `CC BY-NC`；Modrinth 标记 `LicenseRef-CC-BY-NC-3.0`，链接为 https://creativecommons.org/licenses/by-nc/3.0/ 。恢复过程不为原模组授予新的许可。

服务端远端仓库保存的是本项目自编的恢复工具和说明。完整恢复源码、游戏依赖、原始 JAR 和资源留在本地独立工作区；未自动发布为新的模组发行版。


## 历史源码补充核查（2026-09-29）

扩大检索后，已下载 10 个相关仓库及一份 1.16.5 历史源码存档，覆盖 1.6.4、1.7.10、1.12.2、1.16.5、1.20.1 的完整模组源码路线，以及 API、覆盖修改和独立补丁工程。BetaZavr 的 1.20.1 分支确有完整模组工程，README 的旧说明已经过时。Goodbird 的 1.16.5 仓库则是少量覆盖修改类加预编译依赖，不能混称为完整工程。

详见服务端 `docs/customnpcs-historical-sources.md` 与固定提交清单 `docs/customnpcs-historical-sources.lock.json`。本地历史源码目录为 `D:\CPN\customnpcs-historical-sources`。后续以历史工程提供类型、构建和设计参考，以当前 JAR 及反编译基线核对行为；未找到精确版本源码不等于历史源码不可用。各来源的许可与可信度单独记录。当前未构建旧工程或更换运行模组。
