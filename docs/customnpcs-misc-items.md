# 本地 CustomNPCs 杂项物品扩展

2026-10-03，两端本地覆盖版本更新为 `CustomNPCs-1.21.1.20251230-bmc-stock.3.jar`。

新增金币、银币、铜币、金币堆、铜币堆、银币堆、书信、兽齿骨链、卷轴、血石、被割下的耳朵、亡灵粉尘、尖锐的牙齿。全部为普通材料，64 个一组，位于现有 cnpcs 创造页，无特殊效果或右键放置行为；没有加入配方和货币兑换。

SHA256：`4c142d6348b275bd6a13e00b1c3902fac0274834c4f2c5e07119c03bedab4040`。

完整编译通过；隔离服务端 13 项物品的使用/放置/保存读回验证通过，配套客户端 13 项模型/贴图/中文名称/创造页验证通过，实际游戏截图已核对。原商人库存回归 35/35 通过。库存及弹射物修复类与 stock.2 字节相同。

源码和完整证据仅保留本地：`D:\CPN\customnpcs-source\docs\MISC-ITEMS-VALIDATION.md`、`reports\misc-items-results.json`。隔离测试在 `D:\CPN\work\npc-misc-items-20261002`，两端已正常关闭。主实例此次没有启动或保存世界。

两端旧包和覆盖记录备份：`D:\CPN\BMC5-workspace\recovery-20261003\before-stock3-deployment`。以 `.runtime/local-npc-override.json` 记录为准，两端需保持同版本且各只有一个 CustomNPCs JAR。原 `runtime-lock.json` 是上游基线，未修改；不要直接用 setup 恢复旧包造成双包冲突。用过新增物品的存档不应直接降级后保存，以免丢失这些物品。

本仓库只记录维护说明，不提交第三方完整源码或 JAR。
