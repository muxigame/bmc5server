// ============================================================================
// 冰火传说 (Ice and Fire 2.0) 武器数值平衡
// ----------------------------------------------------------------------------
// 这是 startup 事件：客户端和服务端必须放同一份，改完要重启，/reload 不生效。
//
// 口径：
//   显示伤害 = 属性修饰符 + 1（玩家自身基础攻击力）
//   攻速     = 4.0 + 攻速修饰符
//   DPS      = 显示伤害 × 攻速
//
// 基准（全整合包实测，已剔除创造物品 pasterdream:creative_sword、一击即碎的
//   twilightforest:glass_sword、以及 modulargolems 的傀儡零件）：
//   cataclysm:the_incinerator      显示 14  攻速 1.4  DPS 19.6  <- 生存武器天花板
//   pasterdream:shadow_sword       显示 12  攻速 1.6  DPS 19.2
//   mca:scythe                     显示 11  攻速 1.6  DPS 17.6
//   —— 原版参照 ——
//   下界合金剑  显示 8   攻速 1.6  DPS 12.8
//   下界合金斧  显示 10  攻速 1.0  DPS 10.0
//   三叉戟      显示 9   攻速 1.1  DPS 9.9
//
// 设计原则：冰火是屠龙后的终局内容。整合包实测的生存武器天花板是
//   cataclysm:the_incinerator（DPS 19.6）。龙钢定在略高于它的位置，只压伤害、不动攻速。
//
// 注意：巨魔武器（显示 17 / 攻速 0.5 / DPS 8.5）看着数字吓人，实际 DPS 只有下界合金剑
//   的 0.66 倍，属于"慢重武器"设计，本脚本刻意不动它。
//
// 龙钢 tier 的伤害和耐久另由 config/iceandfire/iaf-common.json 控制，
//   已同步改为 armors.dragonSteelBaseDamage=9.0（原 25.0）、
//   dragonSteelBaseDurability=2800（原 8000，下界合金是 2031）。
// ============================================================================

const CAP_DPS = 23      // DPS 上限（龙钢剑 14 显示伤害 × 攻速 1.6 = 22.4，上限需高于它）
const CAP_SHOWN = 18    // 显示伤害上限（放宽到 18，避免误伤巨魔武器这类慢重武器）

// 显式目标，写的是"显示伤害"
const TARGET_SHOWN = {}
;['fire', 'ice', 'lightning'].forEach(el => {
  TARGET_SHOWN['iceandfire:dragonsteel_' + el + '_sword']   = 14    // 原 28  DPS 44.8 -> 22.4
  TARGET_SHOWN['iceandfire:dragonsteel_' + el + '_axe']     = 16    // 原 30  DPS 30.0 -> 16.0
  TARGET_SHOWN['iceandfire:dragonsteel_' + el + '_pickaxe'] = 7     // 原 26  DPS 31.2 -> 8.4
  TARGET_SHOWN['iceandfire:dragonsteel_' + el + '_shovel']  = 7.5   // 原 26.5 DPS 26.5 -> 7.5
  TARGET_SHOWN['iceandfire:dragonsteel_' + el + '_hoe']     = 2     // 原 21  DPS 84.0 -> 8.0
})
TARGET_SHOWN['iceandfire:dread_knight_sword']          = 9    // 原 17   DPS 27.2 -> 14.4
TARGET_SHOWN['iceandfire:dragonbone_sword_fire']       = 8    // 原 9.5  DPS 15.2 -> 12.8
TARGET_SHOWN['iceandfire:dragonbone_sword_ice']        = 8
TARGET_SHOWN['iceandfire:dragonbone_sword_lightning']  = 8

// ── 工具函数：Rhino 下记录式访问器有时是方法有时是属性，统一处理 ──
function val(obj, name) {
  let v = obj[name]
  return (typeof v === 'function') ? obj[name]() : v
}

// 直接读属性组件。不能用 item.attackDamage —— KubeJS 7 的那个 getter
// 会把攻击力和攻速加在一起返回（下界合金剑读出来是 4.6 = 7 + (-2.4)）。
function readStats(m) {
  let dmg = null, spd = 0
  let list = val(val(m, 'attributeModifiers'), 'modifiers')
  for (let i = 0; i < list.size(); i++) {
    let e = list.get(i)
    let attr = String(val(e, 'attribute'))
    let amt = val(val(e, 'modifier'), 'amount')
    if (attr.indexOf('attack_damage') >= 0) dmg = amt
    else if (attr.indexOf('attack_speed') >= 0) spd = amt
  }
  if (dmg === null) return null
  return { shown: dmg + 1, rate: 4.0 + spd }
}

function idOf(m) {
  let it = val(m, 'item')
  let names = ['id', 'descriptionId']
  for (let i = 0; i < names.length; i++) {
    try {
      let v = val(it, names[i])
      if (v !== null && v !== undefined && String(v) !== 'undefined') return String(v)
    } catch (e) {}
  }
  try { return String(it) } catch (e) { return '?' }
}

function r1(x) { return Math.round(x * 10) / 10 }

ItemEvents.modification(event => {
  let done = 0, capped = 0

  // ── 1) 按表钉死 ──
  for (let id in TARGET_SHOWN) {
    let want = TARGET_SHOWN[id]
    try {
      event.modify(id, m => {
        let s = readStats(m)
        m.attackDamage = want - 1
        if (s) {
          console.info('[IAF平衡] ' + id + '  显示 ' + s.shown + ' -> ' + want +
                       '  (DPS ' + r1(s.shown * s.rate) + ' -> ' + r1(want * s.rate) + ')')
        } else {
          console.info('[IAF平衡] ' + id + '  显示 -> ' + want + '（原值读取失败）')
        }
        done++
      })
    } catch (err) {
      console.error('[IAF平衡] 处理失败 ' + id + ' :: ' + err)
    }
  }

  // ── 2) 兜底：扫全部冰火物品，DPS 或显示伤害超标的压到上限 ──
  // 显式表里的已经达标，不会被二次处理。
  try {
    event.modify('@iceandfire', m => {
      let s = readStats(m)
      if (!s || s.rate <= 0) return
      let dps = s.shown * s.rate
      if (dps <= CAP_DPS && s.shown <= CAP_SHOWN) return
      let byDps = CAP_DPS / s.rate
      let want = Math.min(byDps, CAP_SHOWN, s.shown)
      want = Math.round(want * 2) / 2   // 取到 0.5
      if (want >= s.shown) return
      m.attackDamage = want - 1
      capped++
      console.warn('[IAF平衡] 兜底压制 ' + idOf(m) + '  显示 ' + s.shown + ' -> ' + want +
                   '  攻速 ' + r1(s.rate) + '  (DPS ' + r1(dps) + ' -> ' + r1(want * s.rate) + ')')
    })
  } catch (err) {
    console.error('[IAF平衡] 兜底扫描失败，只有显式表生效 :: ' + err)
  }

  console.info('[IAF平衡] 完成：显式 ' + done + ' 件，兜底 ' + capped + ' 件')
})
