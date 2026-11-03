# CustomNPCs motion.118 MarkData 内存泄漏修复

基于最新 dev `7878922` 锁定的 motion.118 JAR（f98ff432…）增量修复，不升级到本地134开发分支。

旧静态 `dataMap<Integer, MarkData>` 通过 MarkData.entity 永久持有所有存读/追踪/渲染过的实体。世界生成候选即使没有入世界、也没有remove事件，仍被留住。核心1.9.1把表换成ConcurrentHashMap只能解决并发，无法释放引用。

新实现把缓存挂到 LivingEntity 对象，缓存随实体一起回收；不依赖死亡/卸载清理事件，也不以可复用网络ID作为对象身份。get在同一实体上同步初始化。为兼容已发布core的Shadow/<clinit>注入保留原静态字段，但不存入任何记录。NBT键cnpcmarkdata、marks/type/color/availability、脚本API与网络协议保持。

## 验证

Windows Java21 / NeoForge21.1.250 / core1.9.1，复用原隔离测试世界与只读依赖，未使用线上世界。旧118对照：10000个未入世界但经过save/load的僵尸、苦力怕、骷髅、蝙蝠、猫，GC后10000存活/静态表10000。修复版：0存活/静态表0。还验证标记类型/颜色NBT读回、128次并发get保持同一实例、相同数字ID的不同实体隔离、已移除实体的既有API和NPC标记。core注入实际生效，静态字段类型仍ConcurrentHashMap。

完整结果见customnpcs-markdata-validation.json；不将此针对性测试当作整包全玩法或008线上长跑验收。没有访问/修改/重启008。

## 安装与回退

管理员正常停服并备份世界后，用新JAR替换旧118，不能同时装两份。客户端建议同步同一JAR，避免混用；无网络协议/存档格式迁移。不修改其他模组、配置、NPC设计模板或YSM资源。通过setup安装时，锁定的旧118自动进入现有rebuilt-originals备份机制，手工改过且校验不匹配的JAR仍会拒绝覆盖。

必须重启JVM才能释放旧静态表已攥住的对象，仅替换磁盘文件不能清空正在运行的堆。更新后需继续观察GC、存活实体和内存曲线。回退时还原旧118及原runtime-lock即可，但泄漏也会恢复。

源码修复本地提交 c2d5079a283caffc9f2181c267f5af3f2ba8e213；仓库仅提交小范围补丁/新增缓存mixin/回归工具，不上传完整第三方反编译树。tools/customnpcs-markdata包含构建说明。
