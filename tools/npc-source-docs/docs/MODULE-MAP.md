# 功能与代码入口

下列路径均相对于 `src/main/java/noppes/npcs`。先从业务入口阅读，再进入调用的数据层，可避免在上千个文件中盲目搜索。完整文件与行数清单见 `reports/source-index.json`。

| 功能 | 入口 | 维护重点 |
|---|---|---|
| 模组启动、配置 | CustomNpcs.java / CustomNpcsPermissions.java | 服务器启动和停机、实际权限值 |
| NPC 核心、战斗 | entity/EntityNPCInterface.java | tick/aiStep、伤害、死亡、任务标记与存档 |
| NPC 行为参数 | entity/data | 外观、属性、AI、脚本、定时器、场景 |
| 寻路与索敌 | ai / ai/target / ai/selector | 目标筛选、攻击条件、寻路频率 |
| 对话 | controllers/DialogController.java / controllers/data/Dialog.java | 分类、引用、读写与玩家分支 |
| 任务 | controllers/QuestController.java / controllers/PlayerQuestController.java / quests | 接取、完成、重复周期、目标类型 |
| 玩家数据 | controllers/data/PlayerData.java / PlayerQuestData.java | 落盘、重新登录、任务与奖励一致性 |
| 商店 | containers/ContainerNPCTrader.java / roles/RoleTrader.java | 扣款、发货、物品组件、满背包 |
| 脚本 | controllers/ScriptController.java / ScriptContainer.java / entity/data/DataScript.java | 脚本引擎、同步执行、错误隔离 |
| 脚本 API | api / api/wrapper | 对外 API 签名、实体包装与缓存 |
| 区块加载 | controllers/ChunkController.java / roles/JobChunkLoader.java | 维度、登记去重、全部释放 |
| 网络与权限 | packets/Packets.java / packets/PacketServerBasic.java / packets/server | 注册、发送范围、服务端验证 |
| 客户端交互 | client/gui / shared/client/gui | 对话、编辑器、任务列表与控件 |
| 模型和动画 | client/model / client/renderer / client/layer | 模型、纹理、骨骼与渲染兼容 |
| 注入兼容层 | mixin；资源中的 customnpcs.mixins.json | 原版类注入、类型约束、线程与权限 |

## 上轮审计已验证的优先入口

| 问题 | 文件/方法 | 后续必须保留的验证 |
|---|---|---|
| 删除实体后缓存残留 | controllers/data/MarkData.java：get、dataMap、entity | 删除后释放，保存重读，C2ME 并发兼容 |
| 包装器缓存不复用 | api/wrapper/WrapperEntityData.java：get/getData | 同实体复用，不新增强引用泄漏 |
| 区块记录跨维度冲突、退出残留 | ChunkController.load/unload；JobChunkLoader.reset | 两维度同坐标、重复登记、加载 4 个释放 4 个 |
| 保存失败未明确返回 | DialogController.saveDialog | I/O 故障保留旧文件、返回错误、重启读取 |
| 非生物实体类型转换异常 | mixin/EntityPersistentData.java：read/save | 生物、掉落物、投射物输入 |
| 目标维度不存在 | packets/server/SPacketDimensionTeleport.java：handle | 正常维度和缺失维度 |

本服已在 muxi-game-core 的 MarkDataConcurrentMapMixin 中修复并发容器。原模组源码中的 HashMap 与运行时 ConcurrentHashMap 并不矛盾：补丁在运行时生效。迁移时需要明确保留或整合该补丁，避免重复应用。
