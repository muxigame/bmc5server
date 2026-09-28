// ============================================================================
// Stalwart Dungeons 锤子 —— 跨 mod 锭替代配方
// ----------------------------------------------------------------------------
// 用途：
//   Stalwart Dungeons 的 8 把锤子原本只认原版材料（木板 / 圆石 / 铁锭 / 金锭 /
//   钻石 / 钨锭），外加下界锤和锻造台合金锤两条特殊线。本脚本给其中 6 把补上
//   "用别的 mod 的锭也能做"的等价配方，让走暮色 / 灾变 / 冰火 / 永恒星光 /
//   以太 / 帕斯特之梦这些线的玩家不用回头刷原版矿也能拿到同档锤子。
//
// 为什么用 KubeJS 而不是改 mod：
//   1. 锤子是 Stalwart 的物品，配方挂在整合包这一侧，Stalwart 重新构建 / 更新
//      不会丢；
//   2. 能按 mod 装没装做条件判断，删 mod 不会留下报错配方；
//   3. 移植出来的 mod 本体保持干净，不引入对其它 mod 的硬依赖。
//
// 这是 server 事件：改完 /reload 即可生效，不用重启。
//
// ----------------------------------------------------------------------------
// 配方模板（和 mod 自带的铁锤 / 金锤 / 钻石锤 / 钨锤配方完全一致，只换那 4 个锭）
//
//        M S M          M = 替换材料（锭），共 4 个
//        M T M          S = minecraft:smooth_stone（平滑石头）
//        _ T _          T = minecraft:stick（木棍）×2
//
// ----------------------------------------------------------------------------
// 锤子数值一览（口径同 iaf_weapon_balance.js：显示伤害 = 修饰符 + 1，攻速 = 4.0 + 修饰符）
//
//   锤子        显示伤害  攻速   DPS    耐久   挖掘等级   备注
//   木锤          7      0.9    6.3     60     石
//   石锤          8      0.9    7.2    131     石
//   铁锤          9      1.0    9.0    250     铁
//   金锤          7      0.9    6.3     32     金        挖速 12.0，快而脆
//   钻石锤       10      1.2   12.0   1561     钻石
//   钨锤         12      1.2   14.4    750     钻石
//   下界锤       14      0.9   12.6   2250     石        命中点燃 12 秒，全 mod 最高伤害
//   合金锤       11      1.2   13.2   2032     合金      防火
//
//   —— 全部低于整合包生存武器 DPS 天花板 19.6（cataclysm:the_incinerator），
//      所以本脚本只是"换条路拿到同一把锤子"，不抬任何数值上限。
//
// ----------------------------------------------------------------------------
// 材料对应关系与理由（第一版，按各 mod 自己的档次排，后续平衡直接改下面那张表）
//
//   铁锤   <- 各 mod 的"第一块自有金属"：矿石直接烧炼、能合成整套工具，档次在铁附近
//   金锤   <- "快而脆"型材料：金锤本身就是 32 耐久 + 挖速 12 的定位
//   钻石锤 <- 各 mod 中段、可直接合成全套工具的材料
//   钨锤   <- 比钻石强、还没到顶的一档（钨锤 12 伤害 / 750 耐久就是这个位置）
//   下界锤 <- 火焰主题 + 后期档，对上下界锤的点燃效果和最高伤害
//   合金锤 <- 各 mod 的顶级材料。合金锤原本必须"钻石锤 + 锻造台 + 下界合金"，
//            能绕过这条线的只能是同样要打完 boss 才拿得到的东西，不构成捷径
//
//   木锤 / 石锤 故意不给：它们的原配方是木板和圆石，任何"锭"都比原料贵，
//   加了也没人会用。要补的话照下表加一行即可。
// ============================================================================

// 锤子 -> 可替代的锭列表。加 / 删材料只改这张表，其余代码不用动。
const HAMMER_MATERIALS = {
  'stalwart_dungeons:iron_hammer': [
    'twilightforest:ironwood_ingot',               // 暮色：铁木，暮色第一块自有金属
    'iceandfire:silver_ingot',                     // 冰火：白银，矿石烧炼，成套工具在铁附近
    'eternal_starlight:deepsilver_ingot',          // 永恒星光：深银，该 mod 的基础金属
    'pasterdream:wind_iron_ingot'                  // 帕斯特之梦：风铁，由铁锭合成
  ],
  'stalwart_dungeons:golden_hammer': [
    'twilightforest:steeleaf_ingot',               // 暮色：钢叶，131 耐久 + 高挖速，"快而脆"正对金锤
    'pasterdream:moltengold_ingot'                 // 帕斯特之梦：熔金，金主题，能合成整套工具
  ],
  'stalwart_dungeons:diamond_hammer': [
    'twilightforest:knightmetal_ingot',            // 暮色：骑士金属，暮色中后段成套工具材料
    'cataclysm:black_steel_ingot',                 // 灾变：黑钢，灾变唯一能直接合成整套工具的金属
    'eternal_starlight:amaramber_ingot'            // 永恒星光：琥珀金，深银之上的一档
  ],
  'stalwart_dungeons:tungsten_hammer': [
    'cataclysm:ancient_metal_ingot',               // 灾变：远古金属，黑钢线的上游材料
    'pasterdream:titanium_ingot',                  // 帕斯特之梦：钛，高硬度向
    'eternal_starlight:thermal_springstone_ingot'  // 永恒星光：温泉石，中段成套工具材料
  ],
  'stalwart_dungeons:nether_hammer': [
    'twilightforest:fiery_ingot',                  // 暮色：烈焰锭，暮色后期火属性材料
    'cataclysm:ignitium_ingot',                    // 灾变：燃焰石，Ignis 掉落，火属性顶级
    'iceandfire:dragonsteel_fire_ingot'            // 冰火：火龙钢，屠火龙后产物
  ],
  'stalwart_dungeons:netherite_hammer': [
    'advancednetherite:netherite_diamond_ingot',   // 进阶合金：钻石合金，本身就要先有下界合金
    'eternal_starlight:unrealium_ingot',           // 永恒星光：虚幻矿，该 mod 的终局材料
    'cataclysm:witherite_ingot',                   // 灾变：凋零石，boss 掉落升级材料
    'iceandfire:dragonsteel_ice_ingot',            // 冰火：冰龙钢
    'iceandfire:dragonsteel_lightning_ingot',      // 冰火：雷龙钢
    'aether_treasure_reforging:valkyrum_ingot',    // 以太：女武神锭，以太终局
    'deep_aether:stratus_ingot'                    // 深层以太：层云锭，深层以太终局
  ]
}

// 模板里不变的两样东西
const FILLER_STONE = 'minecraft:smooth_stone'
const FILLER_STICK = 'minecraft:stick'
const PATTERN = ['MSM', 'MTM', ' T ']

// mod 没装 / 物品改名时安全跳过。
// 优先用 Item.exists（权威：物品注册表里到底有没有这个 id），
// 万一 KubeJS 版本不认这个调用，退回到只判断命名空间对应的 mod 在不在。
function itemExists(id) {
  try {
    return Item.exists(id)
  } catch (err) {
    try {
      return Platform.isLoaded(String(id).split(':')[0])
    } catch (err2) {
      return false
    }
  }
}

// 'twilightforest:ironwood_ingot' -> 'twilightforest_ironwood_ingot'，用于拼配方 id
function idSafe(s) {
  return String(s).replace(/[^a-z0-9_]/g, '_')
}

ServerEvents.recipes(event => {
  // Stalwart Dungeons 整个没装就直接收工，别刷 6 条警告
  if (!itemExists('stalwart_dungeons:iron_hammer')) {
    console.info('[锤子配方] 未检测到 Stalwart Dungeons，跳过跨 mod 锤子配方')
    return
  }

  let added = 0
  let missingMods = {}

  for (let hammer in HAMMER_MATERIALS) {
    if (!itemExists(hammer)) {
      console.warn('[锤子配方] 找不到锤子 ' + hammer + '，跳过它的全部替代配方')
      continue
    }

    let hammerShort = hammer.split(':')[1]

    HAMMER_MATERIALS[hammer].forEach(ingot => {
      if (!itemExists(ingot)) {
        missingMods[ingot.split(':')[0]] = true
        return
      }
      try {
        event.shaped(hammer, PATTERN, {
          M: ingot,
          S: FILLER_STONE,
          T: FILLER_STICK
        }).id('kubejs:stalwart_hammers/' + hammerShort + '_from_' + idSafe(ingot))
        added++
      } catch (err) {
        console.error('[锤子配方] 添加失败 ' + hammer + ' <- ' + ingot + ' :: ' + err)
      }
    })
  }

  let missing = Object.keys(missingMods)
  console.info('[锤子配方] 已添加 ' + added + ' 条跨 mod 锤子配方' +
    (missing.length > 0 ? '；以下 mod 未装，其材料已跳过：' + missing.join(', ') : ''))
})
