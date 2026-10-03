# MCEF 源码重编译版

当前锁定 `2.1.6-1.21.1-muxi.1`，源码仓库为 [muxigame/mcef](https://github.com/muxigame/mcef)，提交 `eb8f20f4d83dcb59a1c8a5f824bdd758d4058025`。同一产物用于 131 本地客户端和服务端；专用服务端 MCEF 入口为空操作。

SHA-256：`fee3f495aa5c6566a252cdb2a6b0f153105624c125144d1cd257dbad044bdd1d`（215390 字节）。原版本 SHA-256：`0c7696216fa5cfee659d687475873c847a9a17cc8ce3a56119a69c946eeb8772`。

先取得同级 mcef 源码并初始化锁定的 java-cef 子模块，使用 JDK 21：

```powershell
cd ..\mcef
git submodule update --init --recursive
.\gradlew.bat :neoforge:jar --no-daemon --console=plain
cd ..\bmc5server
python tools/server.py setup --mcef-jar ..\mcef\neoforge\build\libs\mcef-neoforge-2.1.6-1.21.1-muxi.1.jar
```

不传 `--mcef-jar` 时也会从这个同级构建目录取得。安装前先核对精确 size/SHA，未构建或版本不同会明确报错。普通编译不要求发布凭据。NeoGradle 游戏缓存阶段需要网络，不能保证全链离线构建。

`runtime-lock.json.files` 和原 ZIP 的 hash 不变；`localBuilds` 仅覆盖安装后的 MCEF 校验，安装工具跳过 ZIP 中的旧 JAR，保留原安装文件名，通过 mod/manifest 版本识别新产物。没有把自产物伪装成旧发布文件下载 URL，也没有改写历史 Release。

仅替换匹配已知原 SHA 的 JAR。原件保存于 `.runtime/rebuilt-originals/mods/[嵌入式框架]mcef-neoforge-2.1.6-1.21.1.jar`，未知本地修改和变化过的备份均拒绝覆盖。回滚时先停止自己的实例，确认当前新 SHA，再恢复该原件；同时撤回 `localBuilds` 条目，否则 verify 会正确报告旧版不匹配。不要向正在运行的生产实例直接安装。

源码重编译得到 185 类，公开/受保护接口与 335 个 JNI 声明匹配。独立真实客户端/专用服务端验收 209/209 通过，真实 GL/CEF paint、四方向键/Enter、退出资源基线通过；两端正常退出码 0。窗口焦点与原生键盘回调由 QA 辅助，没有物理 OS 键鼠或全包业务重验。原生 CEF 库沿用原提交缓存。

本次 131 仅安装 MCEF 对应文件，没有重新下载/验证全资源包或迁移配置。008 生产环境未替换、未重启。完整证据在 mcef 仓库 `docs/rebuild-20261003.md` 与 `docs/rebuild-evidence-20261003.json`。

迁移检查：`python -B -m unittest discover -s tools -p test_local_rebuild.py -v`，8 项通过，包含真实安装入口处理不可变原 ZIP 的用例。
