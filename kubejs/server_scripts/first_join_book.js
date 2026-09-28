// 首次进服只给一本任务书。
//
// 实测一个新玩家的背包里就这 4 样，别的什么都没有：
//   touhou_little_maid:smart_slab_init                     智能女仆板
//   patchouli:guide_book → touhou_little_maid:...gensokyo  幻想乡之书
//   pasterdream:dreamnotes_0                               梦境笔记
//   patchouli:guide_book → pasterdream:doremys_guidebook   多莉米的指南书
//
// 车万女仆那两样已在 touhou_little_maid-common.toml 关掉（GiveSoulSpell /
// GivePatchouliBook），从源头不发；这里仍然清一遍，配置万一被改回去也兜得住。
// 帕斯特之梦没有任何配置开关 —— 它的 giveGuideBookIfNeeded 只看玩家 NBT 上的
// pasterdream.guide_book_given，所以只能等它发完再收走。
//
// —— 这个脚本会动玩家背包，所以三条自我约束 ——
//
// 1) 标记存在 NeoForge 实体持久数据（存档里的 NeoForgeData）的 PlayerPersisted 子标签里。
//    NeoForge 重生时只复制这个子标签；服务端老玩家的标记也是预先写在这里的。
//    注意脚本里的 player.persistentData 是 KubeJS 自己的另一份数据（存档键 KubeJSPersistentData），
//    NeoForge 那份要用 getForgePersistentData() 取 —— 两边读写不一致，老玩家就会被当成新人再处理一遍。
// 2) 导览书按组件精确匹配。patchouli:guide_book 是几十个模组共用的手册物品，
//    直接按物品 id 清会把玩家攒的所有手册一锅端。
// 3) 每条 clear 都带数量上限 1。就算前两条同时失效，最多也只动一件。
//
// 延迟 3 秒：模组同样在 PlayerLoggedIn 里发东西，谁先谁后不确定，立刻清理会扑空。
//
// KubeJS 2101.7.2 里几个会踩的坑（都实测过）：
// - 原版 getUUID() 在脚本里被改名成 getUuid()，写 getUUID() 会直接找不到方法。
// - server.getPlayer('名字') 会先把字符串当 UUID 解析，不是 32/36 位就抛异常，走不到按名字找人；
//   这个异常出在 Rhino 参数转换阶段，try/catch 接不住。所以找人一律用 UUID 对象 + 原版 PlayerList。
// - 命令里不加引号的名字只认字母、数字和 _ - . +，中文昵称会让 give/clear 解析失败，所以命令也按 UUID 指人。
//   但 UUID 在原版命令里算"实体选择器"，give/clear 只收玩家，直接写 give <UUID> 会被拒
//   （Only players may be affected），静默模式下连报错都看不到。所以写成
//   execute as <UUID> run give @s ... —— @s 能过"只限玩家"的检查。

const PERSISTED_KEY = 'PlayerPersisted' // 只有这个子标签能活过死亡
const FIRST_JOIN_FLAG = 'muxiFirstJoinBook'
const QUEST_BOOK = 'ftbquests:book'

// 每条都限 1 件；导览书按 patchouli:book 组件精确到具体那一本
const REMOVE_ON_FIRST_JOIN = [
  'patchouli:guide_book[patchouli:book="pasterdream:doremys_guidebook"]',
  'patchouli:guide_book[patchouli:book="touhou_little_maid:memorizable_gensokyo"]',
  'pasterdream:dreamnotes_0',
  'touhou_little_maid:smart_slab_init',
]

// 取 NeoForgeData 下的 PlayerPersisted 子标签。不存在时 getCompound 返回的是一个游离的新标签，
// 必须显式写回去，否则后面的 putBoolean 落在空气上。
function persistedTag(player) {
  var root = player.getForgePersistentData()
  var tag = root.getCompound(PERSISTED_KEY)
  root.put(PERSISTED_KEY, tag)
  return tag
}

PlayerEvents.loggedIn((event) => {
  var player = event.player
  if (persistedTag(player).getBoolean(FIRST_JOIN_FLAG)) return

  var server = event.server
  var name = player.username // 只用于日志
  var uuid = player.getUuid()
  var target = String(uuid)

  server.scheduleInTicks(60, () => {
    // 函数里的变量一律在函数顶层用 var 声明：Rhino 对 try 块里的 const/let 会报
    // "redeclaration of var"（实测），整个回调直接失败。只有脚本顶层的常量用 const。
    var online = null
    var flagTag = null
    try {
      // 人可能在这 3 秒里退了。标记没落下，下次登录再来一遍。
      // 也可能是掉线又重连 —— 那样 online 是新的实体对象，上面捕获的 player
      // 已经被移除，往它身上写标记等于写进空气。下面一律用 online。
      online = server.getPlayerList().getPlayer(uuid)
      if (!online) return

      // 重连会让两个回调排队，这里再查一次，保证只发一本
      flagTag = persistedTag(online)
      if (flagTag.getBoolean(FIRST_JOIN_FLAG)) return

      server.runCommandSilent(`execute as ${target} run give @s ${QUEST_BOOK}`)
      flagTag.putBoolean(FIRST_JOIN_FLAG, true)

      REMOVE_ON_FIRST_JOIN.forEach((predicate) => {
        try {
          server.runCommandSilent(`execute as ${target} run clear @s ${predicate} 1`)
        } catch (err) {
          console.error(`[muxi] 清理失败 ${predicate}（${name}）：${err}`)
        }
      })

      console.info(`[muxi] 首次进服：已发任务书并清掉模组入门书 -> ${name}`)
    } catch (err) {
      // 宁可这次不做，也不要在玩家登录流程里抛异常
      console.error(`[muxi] 首次进服整理失败（${name}）：${err}`)
    }
  })
})
