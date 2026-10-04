# CustomNPCs 合并包与 dev 融合

版本：`1.21.1.20251230-bmc-stock.4`，Minecraft 1.21.1 / NeoForge 21.1.250。

包含龙息之力、NPC 魔杖问号帮助页（32 个可搜索主题）、简体中文、13 种杂项材料、商人库存限制和 NPC 弹射物同步修复。道具 ID 为 `customnpcs:dragon_power`，在管理员用品分类；按住使用键喷吐，松开停止。完整参数与最新隔离检查摘要见同目录 `customnpcs-integration-results.json`。

## 安装与远端基线

本次以最新 `origin/dev` 为基线，保留远端新增的 MCEF 重建锁定、脚本和配置。原资源包 URL、文件清单、逐文件 SHA-256、既有 MCEF `localBuilds` 条目均不变。增加独立的 CustomNPCs 本地构建条目，不把自制 JAR 冒充旧资源包。

NPC 成品已经发布到 [GitHub Release](https://github.com/muxigame/bmc5server/releases/tag/customnpcs-stock4-20261004)，setup 会在本地没有匹配成品时自动下载并校验。MCEF 目前仍需远端锁定构建：

```powershell
python tools/server.py setup --mcef-jar <MCEF构建.jar>
python tools/server.py verify
```

NPC 不显式传参时从同级 `customnpcs-source/dist/CustomNPCs-1.21.1.20251230-bmc-stock.4.jar` 读取。本地默认产物不匹配时改用公开 Release；显式 --npc-jar 指定的产物不匹配则中止，不能随意改 lock 跳过校验。MCEF 获取和构建遵循 [原远端说明](mcef-rebuild.md)。两个构建参数互相独立。

NPC SHA-256：`fe9f1ff4bb4c072f5df88e381904a63f0637c874b29e9e8a4c35a958f104b1f5`。

客户端和服务器必须使用同一版本；NPC 成品已经包含汉化与龙息功能，不应再同时安装旧 NPC JAR。源码恢复工程和二进制不进入 Git 历史。NPC JAR、光影、署名与校验表已发布到新的独立 Release；旧 Release 未改写。本地源码已提交 `3ec2f94`，它不是这两个远端仓库内可检出的提交。

## 升级与回滚

安装器跳过原 ZIP 的旧 NPC，只在原版或已知 stock.2、stock.3、zh.1 校验一致时备份并移除，安装到 stock.4 文件名。旧文件备份于仓库 `.runtime/rebuilt-originals/mods/`。未知修改、未知 CustomNPCs JAR 和变化过的备份均会阻止安装。运行 verify 还会报告旧版重复安装。

回滚前停止自己的实例，备份当前包，恢复原版文件，同时撤回 CustomNPCs 的 localBuilds 条目；保留远端 MCEF 条目。需要回退至另一魔改版时必须同时恢复对应锁定值。不要对运行中的正式服执行替换。

## 验证边界

本次客户端 30 项、服务器 39 项 Python 自动检查通过，包含真实 setup 入口同时安装 MCEF/NPC、原 ZIP 不变、旧版备份、重跑、错误散列、路径越界、重复模组和未知修改保护。

NPC 成品与此前通过真实隔离服务端 59 项、客户端 27 项、商人库存 35 项及按住/松手音频检查的成品逐字节一致；帮助页与 zh.1 相同。这些是既有运行证据，本次没有重新宣称做过同版 MCEF 加 NPC 的整包联机验收。

本机完整 verify 尚未通过：两端 MCEF 仍为旧二进制；客户端另有原清单锁定的 `mods/mcef-cache/LOG` 与运行后内容不一致。保留这些远端要求，没有降低校验。本机缺失的远端武器平衡 startup 脚本已补齐。完整 smoke/联机验收须先取得匹配 MCEF 构建，不能将当前环境当作已通过的发布验收。

本次提交不含服务器本机配置差异、世界、玩家信息、账号、日志和运行库，不部署或重启正式服。

## 附件下载补齐（2026-10-04）

公开下载四个附件后均匹配原大小与 SHA-256。安装器先验证本地成品，必要时下载到 .runtime/rebuild-downloads，下载中断或散列错误不会缓存为成功产物；显式传错 JAR 不会静默回退。客户端会下载配套光影到 shaderpacks，但不改变当前光影选择。两端均安装独立音效署名到 licenses/CustomNPCs-Dragon-Power.txt。
