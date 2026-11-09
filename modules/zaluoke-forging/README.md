# 精炼武备

联合测试入口：`D:/CPN/BMC5-workspace/npc-forging-test`，与 CustomNPCs（含龙息之力）共享唯一干净实例；先读该目录 HANDOFF.md 与 manifest.json。

独立的原创 Minecraft 模组，目标是构建完整而拟真的锻造工艺，丰富玩家从原料到成品的锻造体验。技术 ID 为 `zaluoke_forging`。

当前版本 **0.6.1-dev** 已实现精钢半手剑与精钢战斧的完整加工链：粗铁熔炼 → 模具浇注 → 开模 → 加热 → 锻打 → 热态淬火。包含风箱、积渣堵塞、过热与冷敲报废、库存界面、动态显示和存档同步。完整操作方法与数值见 [锻造链说明](docs/FORGING_CHAIN.md)。

## 在游戏内使用

将 `build/libs/zaluoke_forging-0.6.1-dev.jar` 放入 Minecraft 1.21.1 / NeoForge 21.1.250 实例的 `mods` 目录，或用下方的 `runClient` 启动独立开发客户端。在创造模式的“精炼武备”分类获取测试工具与设备。本版无合成配方。

先搭建“热源在下、精炼炉在上、模具座在热源正前方、风箱在热源侧面”的组合。炉体与模具座需要按浇口朝向对齐。炉子普通右键开界面，放入剑胚／模具使用手持右键，空手取回；锻造锤右键锻打，铲子右键清渣。潜行可绕过界面放置相邻方块。

## 环境

- Minecraft 1.21.1 / NeoForge 21.1.250，与当前 BMC5 环境对齐。
- Java 21，Gradle Wrapper 9.2.1，ModDevGradle 2.0.148。
- 不依赖 CustomNPCs、MCEF 或 muxi-game-core。

## 开始开发

在本目录运行：

```powershell
.\dev.ps1 build
.\dev.ps1 runClient
.\dev.ps1 runServer
.\dev.ps1 runData
```

脚本优先使用 `JAVA_HOME`，否则使用同工作区 `dependencies/jdk-21.0.12.1+1`。其他电脑可以传入 `-JavaHome '你的 JDK 21 路径'`；也可直接运行 `gradlew.bat build`。

IntelliJ IDEA 中打开本目录，以 Gradle 项目导入，Gradle JVM 选择 Java 21。首次同步需要联网下载开发依赖。

成品在 `build/libs/`，其中 `-sources.jar` 是源码包。客户端运行目录为 `run/client`，服务端为 `run/server`，数据生成为 `run/data`。首次启动开发服务端如提示 EULA，请阅读提示后自行决定是否接受。

## 代码与资源

- `src/main/java/com/muxigame/zaluokeforging/ZaluokeForging.java`：公共模组入口。
- `src/main/java/com/muxigame/zaluokeforging/client/ClientEvents.java`：仅客户端加载的入口。
- `src/main/resources/assets/zaluoke_forging/lang/`：中英文语言文件。
- `src/main/templates/META-INF/neoforge.mods.toml`：构建时展开版本和名称的元数据。
- `src/generated/resources/`：数据生成输出，出现后可随源码提交。
- `gradle.properties`：版本与模组身份参数。

正式名称、包名与资源命名空间已统一。注册入口为 `ModContent.java`；原生 JSON 模型、掉落表、语言文件和匹配的碰撞形状由 `tools/generate_forging_resources.py` 维护。模型引用原版纹理，不需要额外资源包；运行此脚本会重建对应资源，修改外观时应同步修改脚本。

## 验证与发布

`build` 执行编译与打包；`runGameTestServer` 运行 33 项实际服务端世界测试。资源引用验证使用 `tools/validate_workshop_assets.py`。最新验收记录见 `docs/forging-chain-check.json`；旧版检查文件保留为历史记录。

构建不会自动安装到现有整合包或发布到远端，当前仅使用本地 Git。

工程依据 [NeoForge 官方 MDK](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle) 整理，模板提交见 `template-origin.json`，模板许可见 `TEMPLATE_LICENSE.txt`；Gradle Wrapper 保留原始版权头。新业务代码暂按 All Rights Reserved，正式发布许可待确定。


2026-10-11 锻造0.6.3：精钢半手剑增加中央剑脊、双侧削薄面与亮色薄刃口；握柄、剑长、显示尺寸及攻击范围保持。CustomNPCs motion.48 保持原散列。Gradle构建及隔离实机左右手/NPC/挥砍检查通过，报告：D:/CPN/BMC5-workspace/npc-forging-test/reports/blade63-validation.json。
