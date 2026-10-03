# 兽人战士客户端克隆验证（2026-09-30）

本次交付内容是客户端克隆模板，没有修改 CustomNPCs 的业务代码或正式服世界。模板和完整台词维护在 bmc5client 的 `pack/customnpcs/clones/1/兽人战士_灰牙氏族_v1.json`、`docs/orc-warrior.md`，后续版本再通过复制魔杖导入服务端。

## 复现方法

`tools/npc-rebuild/OrcWarriorAudit.java` 是本项目编写的内容生成器与集成测试器，只用于隔离测试。它会铺设测试场地、生成实体、输出文件并关闭测试服，**不要放进正式服或玩家世界**。

```powershell
python tools/npc_stock_audit.py --suite orc --workspace D:\CPN\customnpcs-source --java-home D:\CPN\dependencies\jdk-21.0.12.1+1 --output D:\CPN\work\npc-orc-test\audit
```

使用现有恢复构建的类路径编译测试器；运行时安装未修改的 CustomNPCs 原 JAR（SHA-256 `6c28d87b215fc1191488194188ec8a39dd908ae7d2d887b7c9d7d463be0a162c`），不安装库存候选补丁。

测试环境为 Minecraft 1.21.1 / NeoForge 21.1.250 / Java 21，339 个顶层模组 JAR（含测试器），6 GB 堆，4 个处理器，正常难度。隔离目录 `D:\CPN\work\npc-orc-test\server`，仅监听 `127.0.0.1:25589`。沿用已授权的本地测试 EULA；不包含玩家存档或账号认证配置。

### 无玩家测试环境的必要设置

在隔离目录 `config/does_it_tick-common.toml` 的 `EntitiesWhitelist` 中添加 `minecraft:cow`。仅豁免测试靶，不修改用户客户端或正式服配置。

初始测试中牛作为固定靶只掉血一次。对照记录表明：启用和禁用 AI 的两只牛实体年龄都为 0，受伤后的无敌倒计时都停在 20；兽人仍锁定目标。整合包原有 Does It Tick 配置会暂停远离玩家的牛，而 `customnpcs` 在模组白名单里仍正常运行。因此无人测试场中的靶一直处于无敌时间，并非兽人停止攻击。允许牛正常 tick 后重新验证。

## 验证内容与边界

- 用原模组 `ClientCloneController` 把模板写入临时客户端目录，再用同一控制器列出、读取。
- 验证带类型 NBT JSON 往返不变，24 句中文原文完整；去除 UUID、出生坐标、原巡逻起点。
- 用复制魔杖实际调用的 `SPacketToolMobSpawner.spawnClone` 连续召唤两只，验证独立 UUID、皮肤路径、模型尺寸、石斧、生命、攻击配置和 AI。
- 通过原阵营逻辑和 `NPCAttackSelector` 检查可见生存玩家可被选为目标、创造玩家被排除。FakePlayer 不注册进全服玩家列表。
- 用生存的无护甲牛进行实际伤害测试：生命提高为 100，移动速度为 0，击退抗性为 1；一次模拟袭击触发原生反击。通过自然服务器 tick 驱动寻路和攻击，测试器不调用攻击 AI 的 tick，不逐帧重设目标，不手动扣靶血来制造结果。
- 记录每次实际掉血和对应 tick；检查重复伤害和冷却。第二只兽人正常死亡后应移除。
- 皮肤与 3 个声音事件下的 6 个 OGG 文件均在指定客户端原 JAR 中存在。

结果文件：`customnpcs-orc-results.json`。本测试不是图形客户端验收：未逐句触发画面气泡、试听音效，也未用真实玩家账号检验全部地形及装备组合。战斗目标选择和近战伤害分别验证，NPC 对真实玩家的最终伤害仍可能受其他模组事件与护甲影响。

最终 **16 项检查全部通过**。8 次实际命中分别发生于测试 tick 173、203、233、263、293、323、353、383，每次掉血 5 点，间隔均为 30 tick。兽人起终点相距 7.55 格，最大单 tick 位移 0.483 格（含测试起始受击的击退），未发现传送。对照牛无敌计时恢复至 0，活动靶正常更新 380 tick。测试服务端正常退出，退出码 0。

迁移前应检查正式服阵营 ID 2 的含义及玩家声望；模板不会覆盖全服阵营定义。这里只交付普通敌人模板，不安排正式服刷新点。
