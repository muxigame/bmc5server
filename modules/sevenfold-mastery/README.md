# 星之树精简版候选更新（默认关闭）

基于截至2026-11-18已拉取的最新仓库内容候选：客户端a7525c4、服务端0fe85e8（1.4.101 + Archive0.6.3）。这是未发布的内容候选，不代表正式服或官方启动器最新稳定版。

## 内容

只保留启行星、战意座、狡诈座、理智座。三主座可在总览独立激活/熄灭，放大后内部留空：没有十星、数值、升级、职业或后续枝丫；服务端拒绝伪造的内部星辰请求。启行入口原有80额外生命/法力与饱食上限、战斗槽、安全物品迁移和五倍尺度规则保留。当前视觉、声音、半速碎裂、平滑交叠、无长按读条转圈保持。

精简版与完整版同mod ID，不能同时装；必须两端使用同一精简版。精简进度使用SevenfoldStarTreeLite，保留原SevenfoldStarTree数据，不自动导入满星。已激活完整版换版会撤销其旧属性/职业，测试须使用备份世界；回退不会赠送生命或物品。

## 管理者决定启用

此提交不修改runtime-lock.json或任何自动setup路径。ZIP的optional-mods不会被游戏加载；enabled:false只是候选状态，改为true本身不会自动安装。不要将其误认为已经启用。

审核后，在停服、退出客户端并备份世界/玩家数据后：核验bundle-manifest.json全部SHA256；把optional-mods中的两份JAR同步复制到客户端/服务端mods。先将既有zaluoke_forging旧JAR移入不扫描的回退目录，不可共存；不要删除未知或修改过的文件。确认不存在另一份sevenfold_mastery。正式纳入setup时，由维护者新建/更新对应localBuilds锁定条目与准确旧版本替换规则，并完成双端测试。

依赖：本候选配套zaluoke_forging0.9.9-dev，替换当前内容候选0.6.3-dev；二者不会默认替换。最新候选使用YSM3/CustomNPCs兼容包，新武备与该NPC组合尚未实机验收，必须先验证加载、战斗/格挡/NPC、进度存取和物品迁移，再决定启用。原1.4.101候选已有的登录地图素材阻断和城市/YSM验收欠项仍存在，见docs/city-core-candidate.md；本更新不绕过认证、不修改这些模块。

回退：停服退出两端，把本包两份JAR移出mods，再按原runtime-lock恢复原武备；按需要恢复备份世界，不在运行中覆盖。精简包只作为待审内容，不部署、不重启正式服。

## 验证与重建

完整版53项GameTest通过；精简模式2项GameTest验证四节点、空内部、请求拒绝、无隐藏职业、存档隔离/往返。生产JAR排除测试与预览夹具。没有在最新完整1.4.101/YSM3候选上新增真实入服/玩法验收。

源码位于配套服务端modules/sevenfold-mastery和ZIP的sources；武备源码快照在sources/forging-source.zip。Java21下从附属源码目录运行 gradlew.bat build liteJar -PforgingCandidate=绝对路径/zaluoke_forging-0.9.9-dev.jar；测试须从已有开发环境提供极小GameTest模板。生成完整版与带lite标记的独立JAR，不覆盖已登记联合测试环境。两个JAR只能选择一个。
