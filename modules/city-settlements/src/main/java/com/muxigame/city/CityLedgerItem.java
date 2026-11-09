package com.muxigame.city;

import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** City-bound, read-only financial snapshot using Minecraft's own book viewer. */
public final class CityLedgerItem extends Item {
    public CityLedgerItem(){super(new Properties().stacksTo(1));}
    @Override public InteractionResultHolder<ItemStack> use(Level level,net.minecraft.world.entity.player.Player player,InteractionHand hand){
        if(player instanceof ServerPlayer p)open(p,hand);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    @Override public InteractionResult useOn(UseOnContext context){if(context.getPlayer() instanceof ServerPlayer p)open(p,context.getHand());return InteractionResult.sidedSuccess(context.getLevel().isClientSide);}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,net.minecraft.world.item.TooltipFlag flag){
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        lines.add(Component.literal(tag.contains("CityId")?"已绑定："+tag.getString("CityName"):"在城市内右键绑定"));
        lines.add(Component.literal("右键刷新阅读；潜行右键换绑"));
    }
    static UUID binding(ItemStack stack){var t=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();return t.contains("CityId")?UUID.fromString(t.getString("CityId")):null;}
    public static void open(ServerPlayer p,InteractionHand hand){
        ItemStack stack=p.getItemInHand(hand);
        try{
            CityEconomy.require(stack.getItem() instanceof CityLedgerItem,"请手持城市账本");
            UUID id=binding(stack);var cities=CityData.get(p.server);
            var c=(id==null||p.isShiftKeyDown())?cities.cities().stream().filter(v->v.dimension.equals(p.level().dimension().location().toString())&&v.includes(p.blockPosition())).findFirst().orElseThrow(()->new IllegalArgumentException("请站在实际城市区域内右键绑定")):cities.require(id);
            CityEconomy.require(p.hasPermissions(2)||p.getUUID().equals(c.owner),"仅城市所有者或管理员可查账");
            var data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();data.putString("CityId",c.id.toString());data.putString("CityName",c.name);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            stack.set(DataComponents.WRITTEN_BOOK_CONTENT,new WrittenBookContent(Filterable.passThrough("城市账本"),c.name,0,pages(p,c),true));
            p.containerMenu.broadcastChanges();p.connection.send(new ClientboundOpenBookPacket(hand));
        }catch(IllegalArgumentException ex){stack.remove(DataComponents.WRITTEN_BOOK_CONTENT);p.sendSystemMessage(Component.literal("[城市账本] "+ex.getMessage()));}
    }
    static List<Filterable<Component>> pages(ServerPlayer p,CityData.City c){
        var e=CityEconomy.get(p.server);List<List<String>> sections=new ArrayList<>();Set<String> business=new TreeSet<>();
        for(UUID w:c.warehouses.keySet()){String a=e.state.warehouseOwners.get(w);if(a!=null)business.add(a);}
        long firms=business.stream().mapToLong(e::balance).sum(),people=c.residents.stream().mapToLong(n->e.balance(CityEconomy.person(n))).sum();
        Set<String> accounts=new TreeSet<>(business);accounts.add(CityEconomy.treasury(c.id));c.residents.forEach(n->accounts.add(CityEconomy.person(n)));
        sections.add(List.of("城市账本",c.name,"人口："+c.residents.size(),"建筑："+c.buildings.size(),"仓库："+c.warehouses.size(),"数据版本："+e.state.revision,"服务器刻："+p.server.overworld().getGameTime(),"右键重新读取最新值","潜行右键在城内换绑"));
        sections.add(List.of("资金总览（整币）","财政："+e.balance(CityEconomy.treasury(c.id)),"经营账户："+firms,"居民个人："+people,"关联账户去重合计：",Long.toString(accounts.stream().mapToLong(e::balance).sum()),"经营资金不是财政收入","共用经营账户只算一次","跨城共用账户不可再相加"));
        int index=0;for(String a:business)sections.add(List.of("经营账户 "+(++index),a,"余额："+e.balance(a)));
        sections.add(List.of("社会与稳定度","稳定指数："+CityLiving.stability(e,c),"暴乱风险："+CitySociety.risk(CitySecurity.riskIndex(e,c)),"财富结构压力："+CitySociety.pressure(e,c),"日常欲求不满有上限","高风险需外部压力","此处为风险，非暴乱AI",CitySecurity.summary(e,c)));
        for(var v:e.state.living.values())if(v.city.equals(c.id)&&c.residents.contains(v.npc)){var entity=CityEconomy.level(p.server,c).getEntity(v.npc);List<String> lines=new ArrayList<>(List.of("居民生活",entity==null?v.npc.toString().substring(0,8):entity.getName().getString(),v.enabled?"复杂消费启用":"复杂消费停用",v.distributor?"身份：分配者":"身份：普通居民","舒适 / 需求："+v.actual+" / "+v.need,"稳定贡献："+v.contribution,"上期收入："+v.lastIncome,"上期支出："+v.lastCost));for(var n:v.basket)lines.add(n.category+" x"+n.quantity+"："+n.status);sections.add(lines);}
        for(var w:c.warehouses.values()){
            List<String> lines=new ArrayList<>();lines.add("实物库存 · "+w.name());lines.add("位置："+w.pos().toShortString());
            try{var inv=e.inventory(p.server,c.id,w.id());if(inv.isEmpty())lines.add("空仓");inv.forEach((id,n)->lines.add(goods(id)+"："+n));}catch(IllegalArgumentException ex){lines.add("数量未知（不计作0）");lines.add(ex.getMessage());}sections.add(lines);
        }
        for(var j:e.state.production)if(j.city.equals(c.id)){
            var b=c.buildings.get(j.building);List<String> lines=new ArrayList<>(List.of("生产 · "+(b==null?"已移除建筑":b.name()),j.enabled?"启用":"停用",j.status,"每期工资："+j.wage,"周期秒："+j.periodTicks/20,"距离检查秒："+Math.max(0,j.nextTick-p.server.overworld().getGameTime())/20));
            if(j.harvestWheat>0)lines.add("收割成熟小麦："+j.harvestWheat);j.inputs.forEach((id,n)->lines.add("消耗 "+goods(id)+" "+n));j.outputs.forEach((id,n)->lines.add("产出 "+goods(id)+" "+n));sections.add(lines);
        }
        for(UUID n:c.residents){var entity=CityEconomy.level(p.server,c).getEntity(n);List<String> lines=new ArrayList<>(List.of("居民账户",entity==null?"未加载 "+n.toString().substring(0,8):entity.getName().getString(),"余额："+e.balance(CityEconomy.person(n))));for(var d:e.state.consumption)if(d.city.equals(c.id)&&d.npc.equals(n)){lines.add("口粮每期："+d.quantity);lines.add(d.enabled?d.status:"口粮停用");}sections.add(lines);}
        var entries=e.journal.stream().filter(v->accounts.contains(v.from())||accounts.contains(v.to())).toList();
        sections.add(List.of("近期流水说明","仅保留全世界最近256条","以下列出本城最近12条","并非历史累计收入","无记录不代表从未交易","本书为打开时的快照"));
        for(var v:entries.subList(Math.max(0,entries.size()-12),entries.size()))sections.add(List.of("流水 #"+v.sequence(),"时间刻："+v.tick(),kind(v.kind()),"金额："+v.amount(),"付款："+label(v.from(),c,business),"收款："+label(v.to(),c,business)));
        return paginate(sections);
    }
    private static String kind(String k){return switch(k){case "PRODUCTION"->"生产工资";case "CONSUMPTION"->"购买并消耗口粮";case "TRADE"->"商品交易";case "ADMIN_IMPORT","ADMIN_ADJUST"->"管理员调整";default->k;};}
    private static String label(String a,CityData.City c,Set<String> firms){if(a.equals(CityEconomy.treasury(c.id)))return "城市财政";if(firms.contains(a))return "经营账户";if(a.startsWith("person:"))return "个人 "+a.substring(7,Math.min(15,a.length()));return a.isBlank()?"未记录":a;}
    private static String goods(String s){return switch(s){case "minecraft:wheat"->"小麦";case "minecraft:bread"->"面包";case "minecraft:oak_log"->"橡木原木";case "minecraft:oak_planks"->"橡木木板";case "minecraft:raw_iron"->"粗铁";case "minecraft:coal"->"煤";case "minecraft:iron_ingot"->"铁锭";default->s;};}
    static List<Filterable<Component>> paginate(List<List<String>> sections){
        List<Filterable<Component>> pages=new ArrayList<>();
        for(var section:sections){List<String> lines=new ArrayList<>();for(String s:section){StringBuilder row=new StringBuilder();int width=0;for(int cp:s.codePoints().toArray()){int w=cp<128?6:9;if(width+w>108){lines.add(row.toString());row.setLength(0);width=0;}row.appendCodePoint(cp);width+=w;}lines.add(row.toString());}
            for(int start=0;start<lines.size();start+=12){if(pages.size()>=99){pages.add(Filterable.passThrough(Component.literal("内容较多，后续已省略。\n请使用经济页或JSON导出查看全部数据。")));return pages;}pages.add(Filterable.passThrough(Component.literal(String.join("\n",lines.subList(start,Math.min(start+12,lines.size()))))));}}
        return pages;
    }
}
