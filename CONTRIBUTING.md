# 协作流程

1. 按 README 安装并启动开发副本；不要使用正式服存档测试破坏性改动。
2. 从 main 建功能分支，修改配置/KubeJS/任务/枪械配方等文本；保持改动聚焦。
3. 运行 `python tools/server.py verify` 和隔离 `smoke`；涉及客户端 UI、枪械或登录功能时，另做匹配客户端实测。
4. 检查 `git diff --cached`，不要提交密钥、玩家 UUID/聊天记录、世界、缓存或二进制运行库。
5. 提交 PR，说明目的、测试结果、客户端兼容性及是否需要重启。部署和重启必须由管理员另行批准。

## 核心代码

`modules/muxi-game-core` 是源项目提交 `4173faa` 的源码快照（1.8.2），不是 Git 子模块。
它保留自己的 README、测试和许可证。构建器还需要相匹配的**客户端**编译依赖；仅安装本仓库服务端运行库不代表足以编译客户端 HUD。
使用 `python modules/muxi-game-core/build.py --help` 查看 `--server`、`--client-game`、`--pack-mods`、`--java-home` 和 `--test` 参数。
不要直接运行快照中的生产部署脚本；其中历史路径/部署流程仅作为源码历史参考。
源码变更后须重新构建并走资源发布流程，否则服务器仍加载 Release 中锁定的原版 JAR。

## 二进制更新（维护者）

不能把新 JAR 直接提交 Git，也不要替换已发布基线的同名 ZIP。
在独立目录准备完整资产集，维持 `runtime-lock.json` 的相对路径；每个文件计算 SHA-256/size。
资源 ZIP 必须仅包含 lock.files 中的文件，不能有额外目录条目、重复路径或外部绝对路径。
Simple Nicknames 等原作者直链资源放在 lock.external，不打入 ZIP。
创建**新的 Release tag**，上传资源 ZIP，更新 lock 的版本、URL、size 和 SHA-256，再从全新 checkout 跑 setup/verify/smoke。
新的 core 源码快照、JAR 与客户端版本必须对应；PR 中写清迁移和回滚方法。
此基线是一次明确的导出，不会自动同步正在运行的正式服目录。
