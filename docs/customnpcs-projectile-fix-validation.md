# 弹射物修复与本地替换（2026-10-02）

版本 `1.21.1.20251230-bmc-stock.2`，协议仍为 `bmc-stock-1`。基于库存魔改版，保留其功能和数据格式。SHA-256：`dc798773d40363a7c8b87f2e9c4b8db37fa3461a9976268e123653964aa0583a`。

## 修复

`ClientPlayNetHandlerMixin` 保留原版 `handleAddEntity` 生成并初始化的实体，仅补充既有 owner 设置。避免替代对象丢失相对坐标基准，并移除重复角度换算。与 `bmc-stock.1` 的压缩包逐项比较，仅此 Mixin class 与 `META-INF/MANIFEST.MF` 不同；库存 class 全部相同。完整编译 1038 个源码文件，生成 1234 个 class，无原类丢失。

## 隔离运行验证

目录 `D:\CPN\work\npc-projectile-fix-20261002`，独立平坦世界，回环地址 `127.0.0.1:25586`。测试使用两端相同候选包、完整整合包和原有光影设置，未使用主本地服世界。

- 真实客户端连接通过。运行中的、已应用 Mixin 的生成包处理后，实体位置和相对坐标基准均为 `(4,102,5)`；随后零相对位移包仍保持该位置。原包同一测试会归零。
- 包内朝向 yaw=90、量化后 pitch=29.53125 正确保留；不再重复乘 360/256。
- 实际发射 3D 原木、2D 雪球和箭，捕获 41 个弹射物、201 条客户端样本：物品正确、坐标均在测试高度90以上、未隐形，渲染器 crash/crash2 均为 false。
- 原木模型画面已人工检查工具返回图像：`screenshots/confirmed-flight-2.png` 可见 NPC 前方飞行的两个原木模型。雪球与箭本次依赖运行轨迹和渲染状态验证，不将其表述为逐帧视觉验收。
- 原有库存服务端回归 35/35 通过，详见测试目录 `server/stock-results.json`。

测试辅助类仅装在隔离服，未加入日常 mods。测试服和测试客户端已退出。原有库存 OP 编辑器的全面交互验收仍待完成；本次也不代表长期稳定性测试。

初次测试脚本的编码、测试辅助模组包名冲突及截图探针空物品问题已修正，最终数据来自修正后的成功运行；详细记录见 `projectile-fix-results.json` 的 testSetupNotes。一次已完成保存的测试 JVM 因模组线程不退出而单独结束，不涉及主世界。

## 用户授权的本地部署

在关闭主本地服与本次客户端后，用户要求替换魔改包。已将以下两处旧原版包移入备份目录，两处 mods 均只剩一份同哈希的新 CustomNPCs 包：

- `D:\CPN\BMC5-workspace\bmc5server\mods\CustomNPCs-1.21.1.20251230-bmc-stock.2.jar`
- `D:\CPN\BMC5-workspace\bmc5client\game\mods\CustomNPCs-1.21.1.20251230-bmc-stock.2.jar`

构建成品另保留在本工作区 `dist/CustomNPCs-1.21.1.20251230-bmc-stock.2.jar`。日常实例按要求保持关闭；游戏重启时才加载新包。

备份：`D:\CPN\BMC5-workspace\recovery-20261002\before-stock2-deployment`，包含两端原包、创造世界、服务端 customnpcs 数据、server.properties 与 ops.json。此前旧 world 未加载、未修改。测试轨迹、源码与截图保留在上述隔离目录。

客户端和服务端原有 `runtime-lock.json` 描述上游整合包基线，未通过改基线清单掩盖本地覆盖。因此原有 verify 会报告旧 NPC 包缺失；直接执行 setup 可能重新放回旧包，造成双包冲突。维护本次覆盖应查看两端 `.runtime/local-npc-override.json`，保持同一新包且每端仅一份。未发布第三方完整源码、JAR 或执行远程推送。

回滚需在两端关闭时把新包移出 mods，再将对应 client-original.jar/server-original.jar 恢复为原文件名。旧包不支持新增库存功能；若已在新版中修改世界或库存，需要结合部署前备份决定恢复范围，不直接覆盖新的玩家数据。
