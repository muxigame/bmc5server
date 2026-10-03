# CustomNPCs 源码重打包验证

2026-09-29。目标是恢复现用 1.21.1.20251230 的构建能力并检查行为保持，不包含功能 BUG 修复或正式部署。

## 已实现

- 1,036 个源码文件完整编译，1,230 个 class，与原包类名集合一致，没有用原包 class 填补缺失编译产物。
- 类继承/接口/访问标志与字段描述符、访问标志、泛型签名、常量清单一致；全部非 class 资源逐项散列一致。
- 两个全新构建目录产出完全相同的 JAR：SHA256 `1c3a01a44975c9bed76b2a1fb610dd7848517d7190e9384d90cb9dea17bb74fa`。这是重复构建的一致性，不是与原始 JAR 字节相同。
- 原包与候选包分别在独立新世界、同一套服务端模组配置中运行短时测试。20 条记录归一化后相同，均完成测试并退出码 0，无测试器错误。测试中保留了原包已有的缓存、区块票据、保存失败、错误输入问题，不把这些原有 FAIL 当作此次引入的差异。
- A/B 只忽略实际耗时、JVM 类加载器实例编号、空指针异常里的局部变量名称；保留错误类型和错误操作。没有把不同异常统一抹掉。

## 修复范围

构建视图应用 NeoForge 及模组原有 AT/AW 规则，并为受保护的嵌套枚举提供仅编译使用的可见性。所有依赖快照保持不变，变换后的 Minecraft 类仅放在编译目录，绝不进入产物。排除误混入的旧 Guava 20.0 与旧 ASM 主包/树包。

源码恢复包括显式泛型/函数式接口目标类型、Mixin 经 Object 的合法转换、丢失的局部变量作用域和无效的 boolean/int 反编译。字节码比较另外发现并修复了 Runnable/Callable 重载选择、父子类同名 tile 字段、投射物浮点运算结合顺序、模型 double 运算、绘制 translate 重载、StringBuilder 构造及字体常量访问的恢复差异。修改依据原包行为，而非“更合理”的新行为。

工作源码及逐项差异保存在本地 `D:\CPN\customnpcs-source`，原包和两个反编译参考完整性复核通过。第三方源码不推送到服务端仓库；远端保存自编构建/验证工具和报告。

## 能证明什么，不能证明什么

8,284 / 9,323 个原始方法的标准化指令和方法注解完全一致（忽略调试信息、栈帧、最大栈与局部变量容量）。仍有 1,039 个原始方法的指令表示不同，另多出一个编译器生成的 getFocused 转发桥接方法，因此不能声称完成了全部方法的严格等价证明。字段和方法清单也不是完整 Java 反射元数据比较。

剩余差异包含控制流布局、局部变量槽和泛型编译形式；操作/常量集合相同不意味着执行顺序或行为一定相同，不能据此自动盖章。`differences.json` 和每方法原/新文本保留全部未归零差异。短时 A/B 验证涵盖权限、脚本、对话/NPC/任务 NBT、交易、直接伤害及已知边界缺陷，未覆盖全部客户端界面、真实玩家联网、所有脚本输入及长期运行。`dialog_inmemory_after_save` 是同进程检查，不冒称重启存档兼容验证。

结论：**完整可重建、产物可重复、已测服务端行为一致；尚未证明全模组在所有场景与原包等价。** 不自动换正式服或日常客户端的包。

## 构建与复查

```powershell
python tools/npc_rebuild.py --java-home D:\CPN\dependencies\jdk-21.0.12.1+1
python tools/npc_source.py verify --java-home D:\CPN\dependencies\jdk-21.0.12.1+1
python tools/npc-rebuild/compare_ab.py D:\CPN\work\npc-rebuild-ab
```

每次构建使用新目录，先验证原包/依赖散列，禁用注解处理器，全量编译所有源码，不依赖上一次 class。打包顺序、时间戳、压缩与权限固定。`inputs.json` 记录源码、资源、编译器和工具散列。源码工作区必须使用已经完成恢复修正的版本；重新反编译不能自动代替这些修正。

`tools/npc-rebuild/prepare_ab.py` 是本机隔离测试准备脚本，以已有 `D:\CPN\work\npc-audit-server` 为模组/配置来源，拒绝覆盖已有测试目录。测试会修改自己创建的测试世界并自动停服，禁止把 NpcAudit 安装到正常服务器。准备后在 original、rebuilt 两个目录依次运行：

```powershell
& D:\CPN\dependencies\jdk-21.0.12.1+1\bin\java.exe -Xmx8G -XX:ActiveProcessorCount=4 '@libraries/net/neoforged/neoforge/21.1.250/win_args.txt' nogui *> console-ab.log
$LASTEXITCODE | Set-Content exit-code.txt
```

本机产物：`D:\CPN\customnpcs-source\dist\CustomNPCs-Unofficial-NeoForge-1.21.1.20251230-rebuilt.jar`。完整报告、输入散列及差异在旁边；原始日志在各隔离实例内。


## 客户端与运行目录复核

同一重打包在单独客户端目录启动，日志记录 Game took 149.788 seconds to start；进程窗口响应后通过正常关窗退出。此项是启动检查，不包含联网、NPC 编辑器或战斗渲染回归。正式服务端及日常客户端原包 SHA256 均仍为 `6c28d87b215fc1191488194188ec8a39dd908ae7d2d887b7c9d7d463be0a162c`。原包及不可变反编译参考复核通过，恢复工具原有 4 项安全测试通过。
