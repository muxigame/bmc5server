# muxigame BMC5 服务端协作基线

当前正式服内容的**脱敏开发副本**：Minecraft **1.21.1** / NeoForge **21.1.250** / muxi-game-core **1.9.1**。
不包含正式服世界、玩家数据、账号接口密钥、RCON 密码、隧道配置、日志或备份。
已核对正式服目录中的 1.9.1 核心校验值与源码提交 `7e3863e`；早期 1.8.2 导出保留在 `baseline-2026.09.28` 标签。

## 拉取与启动

准备 Git、Python **3.10+**、JDK **21+**（建议 JDK 21），设置 `JAVA_HOME` 或把 Java 加入 PATH。
需要访问 GitHub Releases、NeoForge/Minecraft 官方下载服务和 Modrinth；首次安装下载约 1.57 GB 的资源包及运行库。
磁盘建议至少预留 8 GB；完整模组服内存建议 8 GB 以上，实际需求随玩家和世界增长。

```sh
git clone https://github.com/muxigame/bmc5server.git
cd bmc5server
python tools/server.py setup --accept-eula
python tools/server.py start --memory 6G
```

Windows 若没有 `python` 命令，可改用 `py -3.12`（需安装 Python 3.12），避免默认选择旧版 Python。`--accept-eula` 表示你已阅读并同意 [Minecraft EULA](https://aka.ms/MinecraftEULA)；不同意时不要传入该参数或启动服务。
Java 不在 PATH 时，在 setup、start 或 smoke 命令后加 `--java "你的 JDK/bin/java.exe"`。

安装器会校验 SHA-256、解包锁定资源、从原作者地址下载 Simple Nicknames，再安装 NeoForge。
重跑 setup 不覆盖已有配置；遇到已修改的二进制会中止，防止丢失本地改动。
下载中断后重跑即可（当前整包重新下载，不支持断点续传）。

默认仅监听 **127.0.0.1:25585**，生成新的 `world`，RCON 关闭。
本地开发关闭正版验证及 muxigame 账号/昵称服务接入，避免依赖正式服私钥；这不代表正式服设置发生变化。
连接时需要匹配的 BMC5 客户端及同版 core；本仓库不是纯净客户端可直接加入的原版服务器。

## 文件分工

| 内容 | 位置 |
| --- | --- |
| 可协作编辑的模组配置、任务、脚本、枪械数据 | `config/`、`defaultconfigs/`、`configureddefaults/`、`kubejs/`、`tacz/` 等 |
| 当前 1.9.1 核心源码快照 | `modules/muxi-game-core/`，来源提交 `7e3863e` |
| 模组、模型、贴图、声音等二进制 | GitHub Release 的 `server-assets.zip`，不进入 Git 历史 |
| 精确版本、下载地址、逐文件校验 | `runtime-lock.json` |
| 本机私密设置的无密钥示例 | `config-examples/` |
| 导出范围 | `export-policy.json`、`.gitignore` |

1.9.1 复用原始大资源包，新核心 JAR 从新版 Release 单独下载并校验。安装器跳过资源包中的旧核心；升级已有副本时，仅在旧核心校验值匹配时将其移到 `.runtime/retired/`，不删除或覆盖自定义 JAR。先 `git pull`，再重跑 setup 后启动。不要在正在运行的实例上升级。

## 本机验证

```sh
python tools/server.py verify
python tools/server.py smoke --memory 4G --cpus 2 --timeout 1200
```

`verify` 校验所有锁定二进制，不限制正常的文本配置修改。
`smoke` 要求回环地址且关闭 RCON，先检查端口空闲，启动本目录的服务，等到 `Done` 且端口可连接后发送 `stop`。
结果在忽略的 `.runtime/smoke-result.json` 和 `.runtime/smoke.log`；它会生成本机测试世界，不访问正式服控制接口。
首次生成世界可能较慢，完整模组在低内存机器上也可能启动失败；增加可用内存或延长 timeout 后重试。
Windows/Linux 使用 NeoForge 对应平台参数文件；平台实测范围以发布说明为准。

## 配置与协作

修改配置后提交分支并发 PR，详见 [CONTRIBUTING.md](CONTRIBUTING.md)。
`server.properties`、`config/muxi-game-core.json` 和女仆 AI 凭据配置由 setup 从无密钥示例生成，均忽略提交。
网页配置管理模组使用新实例自行生成的密码，不继承正式服密码。
正式服登录/昵称接口需管理员单独提供授权配置；不要把密钥写入示例或 PR。

此开发启动器禁止将离线模式直接绑定公网。若需要开放公网，先使用 `online-mode=true` 并评估防火墙/白名单；采用自定义登录网关时，须单独审查认证链路并使用专门的生产启动方案。
不要把此仓库直接覆盖到正在运行的正式服目录，也不要用本地开发配置替换生产认证配置。

## NPC 源码维护准备

当前版本的公开源码核查、独立反编译工作区、完整性校验和编译诊断，见 [CustomNPCs 源码恢复指南](docs/customnpcs-source-recovery.md)。恢复工具不安装或替换游戏模组。

## 资源与许可

第三方模组/模型版权属于各作者，见 [THIRD_PARTY.md](THIRD_PARTY.md)。
Minecraft/NeoForge 运行库通过安装器获取，不上传世界与 Minecraft 游戏二进制。
Simple Nicknames 保留原作者下载链路，不在资源 ZIP 中二次分发。


## MCEF source rebuild

Before setup, build the pinned MCEF source or provide `--mcef-jar`. See [build, install and rollback](docs/mcef-rebuild.md).

## CustomNPCs 合并构建

龙息、中文界面、魔杖帮助及既有 NPC 修复通过独立 localBuilds 条目锁定。setup 同时需要对应 MCEF 与 NPC 构建，支持 `--npc-jar`，详见 [安装、回滚与验证边界](docs/customnpcs-integration.md)。
