package com.muxigame.city;

import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Explicit economic opt-in, data exchange and server-validated merchant interaction. */
public final class CityEconomyAccess {
    public static void interact(PlayerInteractEvent.EntityInteract e){
        if(!(e.getEntity() instanceof ServerPlayer p)||e.getHand()!=InteractionHand.MAIN_HAND)return;
        if(p.getMainHandItem().getItem() instanceof RuralBlocks.Wand)return;String held=BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString();
        if(held.equals("customnpcs:npcwand")||p.getMainHandItem().getItem() instanceof CityMod.PlanningItem)return;
        var econ=CityEconomy.get(p.server);
        // Once configured, even disabled offers suppress the old infinite-stock shop.
        if(econ.state.merchants.stream().noneMatch(o->o.npc.equals(e.getTarget().getUUID())))return;
        e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);
        market(p,e.getTarget().getUUID(),"");
    }
    public static void request(ServerPlayer p,CityNetwork.Request r){
        try{
            if(r.op().equals("econview")){overview(p,UUID.fromString(r.city()),"");return;}
            UUID npc=UUID.fromString(r.city());
            if(r.op().equals("econbuy")||r.op().equals("econsell")){
                var e=CityEconomy.get(p.server);var offer=e.offer(UUID.fromString(r.value()));
                CityEconomy.require(offer.npc.equals(npc),"商人已变化");e.trade(p,offer.id,r.op().equals("econbuy"),r.revision());market(p,npc,"交易成功，货物与资金已交割。");
            }else if(r.op().equals("econmarket"))market(p,npc,"");
        }catch(IllegalArgumentException ex){
            if(r.op().equals("econview"))p.sendSystemMessage(Component.literal(ex.getMessage()));
            else try{market(p,UUID.fromString(r.city()),ex.getMessage());}catch(IllegalArgumentException closed){p.sendSystemMessage(Component.literal(closed.getMessage()));}
        }
    }
    private static void line(ListTag lines,String text){lines.add(StringTag.valueOf(text));}
    private static String name(ServerPlayer p,UUID id){var entity=p.serverLevel().getEntity(id);return entity==null?"未加载居民 "+id.toString().substring(0,8):entity.getName().getString();}
    private static String purpose(String kind){return switch(kind){case "PRODUCTION"->"生产发薪";case "CONSUMPTION"->"购买并消耗口粮";case "TRADE"->"商店交易";case "ADMIN_ADJUST","ADMIN_IMPORT"->"管理员调整";default->kind;};}
    public static void overview(ServerPlayer p,UUID city,String message){
        var c=CityData.get(p.server).require(city);CityEconomy.require(p.hasPermissions(2)||c.owner.equals(p.getUUID()),"经济账目仅城市所有者或管理员可看");
        var e=CityEconomy.get(p.server);CompoundTag t=new CompoundTag();t.putBoolean("Economy",true);t.putString("Target",city.toString());t.putString("Title","城市经济 · "+c.name);t.putString("Message",message);t.putLong("Revision",e.state.revision);
        ListTag lines=new ListTag();line(lines,"记账单位：整币；本版固定报价，无银行、税收或通胀。");
        var simulation=e.state.simulation.get(city);if(simulation!=null)line(lines,"离城模拟："+(simulation.enabled?"开启":"关闭")+" | 后台生产/消费 "+simulation.offlineProductions+"/"+simulation.offlineConsumptions+" | 迁出 "+simulation.migrations);
        var logistics=e.state.logistics.get(city);if(logistics!=null)line(lines,"商人配送："+logistics.status+" | 累计 "+logistics.shipments+"单 "+logistics.moved+"件");
        line(lines,"财政余额："+e.balance(CityEconomy.treasury(city)));
        line(lines,"稳定度："+CityLiving.stability(e,c)+" | 暴乱风险："+CitySociety.risk(CitySecurity.riskIndex(e,c))+" | 财富结构压力："+CitySociety.pressure(e,c));
        line(lines,CitySecurity.summary(e,c));
        for(var v:e.state.living.values())if(v.city.equals(city))line(lines,"居民生活 "+name(p,v.npc)+" | "+(v.enabled?v.status:"停用")+" | "+(v.distributor?"分配者":"普通居民"));
        for(var w:c.warehouses.values()){
            String a=e.state.warehouseOwners.get(w.id());line(lines,"仓库 "+w.name()+" | "+(a==null?"尚未绑定经营账户":"经营余额："+e.balance(a)));
            try{var inv=e.inventory(p.server,city,w.id());if(inv.isEmpty())line(lines,"  空仓");else inv.forEach((id,n)->line(lines,"  "+id+" ×"+n));}catch(IllegalArgumentException ex){line(lines,"  "+ex.getMessage());}
        }
        for(var j:e.state.production)if(j.city.equals(city))line(lines,"生产 · "+(c.buildings.containsKey(j.building)?c.buildings.get(j.building).name():"建筑已解除")+" | "+(j.enabled?"启用":"停用")+" | "+j.status+" | 约"+Math.max(0,(j.nextTick-p.server.overworld().getGameTime())/20)+"秒后检查");
        for(var d:e.state.consumption)if(d.city.equals(city))line(lines,"口粮 · "+name(p,d.npc)+" | "+(d.enabled?"启用":"停用")+" | "+d.status);
        for(UUID npc:c.residents)line(lines,"居民 · "+name(p,npc)+" 余额："+e.balance(CityEconomy.person(npc)));
        Set<String> accounts=new HashSet<>();accounts.add(CityEconomy.treasury(city));c.residents.forEach(id->accounts.add(CityEconomy.person(id)));c.warehouses.keySet().forEach(id->{String owner=e.state.warehouseOwners.get(id);if(owner!=null)accounts.add(owner);});
        line(lines,"最近账目（从旧到新；完整最近256条在JSON导出）：");
        e.journal.stream().filter(v->accounts.contains(v.from())||accounts.contains(v.to())).skip(Math.max(0,e.journal.stream().filter(v->accounts.contains(v.from())||accounts.contains(v.to())).count()-20)).forEach(v->line(lines,"#"+v.sequence()+" "+purpose(v.kind())+" | 金额 "+v.amount()));
        line(lines,"管理员：/cityeconomy export 文件名；修改JSON后apply 文件名 revision");
        line(lines,"仓库商品由真实箱子提供；未加载时暂停，不追补停服期间产量。");
        t.put("Lines",lines);PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(t));
    }
    public static void market(ServerPlayer p,UUID npc,String message){
        var entity=p.serverLevel().getEntity(npc);CityEconomy.require(entity!=null&&entity.isAlive()&&entity.distanceToSqr(p)<=36,"商人未加载、死亡或超出6格，请重新靠近");
        var e=CityEconomy.get(p.server);String personal=CityEconomy.person(p.getUUID());
        if(!e.state.accounts.containsKey(personal))e.adjust(personal,0,p.serverLevel().getGameTime(),"首次开户，初始资金0");
        CompoundTag t=new CompoundTag();t.putBoolean("Economy",true);t.putBoolean("Market",true);t.putString("Target",npc.toString());t.putString("Title","城市商人 · "+entity.getName().getString());t.putString("Message",message);t.putLong("Revision",e.state.revision);t.putLong("Balance",e.balance(personal));
        ListTag offers=new ListTag();for(var o:e.state.merchants)if(o.npc.equals(npc)){
            CompoundTag row=new CompoundTag();row.putString("Id",o.id.toString());row.putString("Item",o.item);row.putLong("Buy",o.buyPrice);row.putLong("Sell",o.sellPrice);row.putBoolean("Enabled",o.enabled);
            try{row.putInt("Stock",e.inventory(p.server,o.city,o.warehouse).getOrDefault(o.item,0));row.putString("Status",o.enabled?"":"停用");}catch(IllegalArgumentException ex){row.putString("Status",ex.getMessage());row.putBoolean("Enabled",false);}
            offers.add(row);
        }t.put("Offers",offers);PacketDistributor.sendToPlayer(p,new CityNetwork.Snapshot(t));
    }
    private interface Action {String run()throws Exception;}
    private static int run(CommandSourceStack s,Action a){try{String m=a.run();s.sendSuccess(()->Component.literal(m),false);return 1;}catch(Exception ex){s.sendFailure(Component.literal("[城市经济] "+ex.getMessage()));return 0;}}
    private static Path file(CommandSourceStack s,String name)throws Exception{
        CityEconomy.require(name.matches("[A-Za-z0-9_-]{1,64}"),"文件名仅字母数字下划线连字符");Path dir=s.getServer().getWorldPath(LevelResource.ROOT).resolve("city_settlements/economy");Files.createDirectories(dir);return dir.resolve(name+".json");
    }
    private static JsonObject export(CommandSourceStack s){
        var e=CityEconomy.get(s.getServer());JsonObject j=e.export();j.add("journalReadOnly",CityEconomy.JSON.toJsonTree(e.journal));
        JsonArray stocks=new JsonArray();
        for(var city:CityData.get(s.getServer()).cities())for(var w:city.warehouses.values())if(e.state.warehouseOwners.containsKey(w.id())){
            try{Map<String,Integer> goods=new TreeMap<>(e.inventory(s.getServer(),city.id,w.id()));goods.keySet().removeIf(k->k.contains(" ["));
                JsonObject edit=new JsonObject();edit.addProperty("city",city.id.toString());edit.addProperty("warehouse",w.id().toString());edit.add("expected",CityEconomy.JSON.toJsonTree(goods));edit.add("desired",CityEconomy.JSON.toJsonTree(goods));stocks.add(edit);
            }catch(IllegalArgumentException unloaded){/* Unloaded goods are not editable until loaded. */}
        }j.add("stockEdits",stocks);return j;
    }
    private static Map<String,Integer> stockMap(JsonObject j){
        CityEconomy.require(j!=null&&j.size()<=128,"库存最多128类");Map<String,Integer> m=new TreeMap<>();
        for(var v:j.entrySet()){CityEconomy.item(v.getKey());int n=v.getValue().getAsBigDecimal().intValueExact();CityEconomy.require(n>=0&&n<=4096,"库存数量须0–4096");if(n>0)m.put(v.getKey(),n);}return m;
    }
    static void apply(CommandSourceStack source,JsonObject json,long revision){
        var e=CityEconomy.get(source.getServer());var cities=CityData.get(source.getServer());
        // Preflight every real slot and expected inventory before changing any accounts.
        List<CityEconomy.Slots> prepared=new ArrayList<>();Set<UUID> seen=new HashSet<>();
        if(json.has("stockEdits")){var edits=json.getAsJsonArray("stockEdits");CityEconomy.require(edits.size()<=128,"一次最多编辑128个仓库");
            for(var raw:edits){var edit=raw.getAsJsonObject();UUID city=UUID.fromString(edit.get("city").getAsString()),id=UUID.fromString(edit.get("warehouse").getAsString());CityEconomy.require(seen.add(id),"库存编辑重复");
                var expected=stockMap(edit.getAsJsonObject("expected"));var desired=stockMap(edit.getAsJsonObject("desired"));var actual=new TreeMap<>(e.inventory(source.getServer(),city,id));actual.keySet().removeIf(k->k.contains(" ["));CityEconomy.require(actual.equals(expected),"真实库存已变化，请重新导出："+id);
                var c=cities.require(city);var slots=new CityEconomy.Slots(CityEconomy.warehouse(CityEconomy.level(source.getServer(),c),c,id));expected.forEach(slots::remove);desired.forEach(slots::add);if(slots.target instanceof CitySimulation.Shadow){
                    CityEconomy.require(json.has("simulation")&&json.getAsJsonObject("simulation").has(city.toString()),"后台库存编辑必须保留simulation配置");var policy=json.getAsJsonObject("simulation").getAsJsonObject(city.toString());CityEconomy.require(policy.has("stocks")&&policy.getAsJsonObject("stocks").has(id.toString()),"后台库存快照缺失");var stock=policy.getAsJsonObject("stocks").getAsJsonObject(id.toString());stock.add("goods",CityEconomy.JSON.toJsonTree(desired));stock.addProperty("pending",true);
                }else prepared.add(slots);
            }
        }
        e.apply(json,revision,cities,source.getServer().overworld().getGameTime());prepared.forEach(CityEconomy.Slots::commit);
    }
    static String accountCommand(String id,long balance){return "/cityeconomy account "+StringArgumentType.escapeIfRequired(id)+" "+balance;}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        var root=Commands.literal("cityeconomy").requires(s->s.hasPermission(2));
        root.then(Commands.literal("perf").executes(ctx->run(ctx.getSource(),()->{
            var e=CityEconomy.get(ctx.getSource().getServer());long now=ctx.getSource().getServer().overworld().getGameTime();
            long jobs=e.state.production.stream().filter(j->j.enabled&&j.nextTick<=now).count(),needs=e.state.living.values().stream().filter(p->p.enabled&&p.nextTick<=now).count();
            return String.format(java.util.Locale.ROOT,"城市经济调度：上tick %.3fms，本次运行峰值 %.3fms；处理生产 %d / 消费 %d；到期等待生产 %d / 复杂消费 %d。仅经济调度，不含NPC AI/渲染、统计扫描或存档。",e.lastEconomyNanos/1e6,e.maxEconomyNanos/1e6,e.lastProductionCount,e.lastConsumptionCount,jobs,needs)+" 工作表现累计：寻路="+CityWork.paths+"，稻株补种="+CityWork.harvests+"，到访="+CityWork.visits;
        })));
        var template=Commands.argument("file",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),()->{
            var s=ctx.getSource();var e=CityEconomy.get(s.getServer());var next=CityEconomy.decode(e.export());
            UUID city=UUID.fromString(StringArgumentType.getString(ctx,"city")),building=UUID.fromString(StringArgumentType.getString(ctx,"building")),warehouse=UUID.fromString(StringArgumentType.getString(ctx,"warehouse")),npc=UUID.fromString(StringArgumentType.getString(ctx,"npc"));
            String business=next.warehouseOwners.getOrDefault(warehouse,"business:"+warehouse);next.accounts.putIfAbsent(business,0L);next.accounts.putIfAbsent(CityEconomy.person(npc),0L);next.accounts.putIfAbsent(CityEconomy.treasury(city),0L);next.warehouseOwners.put(warehouse,business);
            var j=new CityEconomy.Job();j.id=UUID.randomUUID();j.city=city;j.building=building;j.warehouse=warehouse;j.worker=npc;j.employer=business;j.wage=3;j.inputs.put("minecraft:wheat",3);j.outputs.put("minecraft:bread",1);j.requiredBlocks.put("minecraft:furnace",1);next.production.add(j);
            var o=new CityEconomy.Offer();o.id=UUID.randomUUID();o.city=city;o.npc=npc;o.warehouse=warehouse;o.item="minecraft:bread";o.buyPrice=3;o.sellPrice=2;next.merchants.add(o);
            var d=new CityEconomy.Demand();d.id=UUID.randomUUID();d.city=city;d.npc=npc;d.offer=o.id;next.consumption.add(d);
            CityEconomy.validate(next);CityEconomy.references(next,CityData.get(s.getServer()));Path p=file(s,StringArgumentType.getString(ctx,"file"));Files.writeString(p,CityEconomy.JSON.toJson(next));return "已生成未启用的面包坊模板 "+p+"；编辑资金、周期和enabled后apply。";
        }));
        root.then(Commands.literal("template").then(Commands.argument("city",StringArgumentType.word()).then(Commands.argument("building",StringArgumentType.word()).then(Commands.argument("warehouse",StringArgumentType.word()).then(Commands.argument("npc",StringArgumentType.word()).then(template))))));
        root.then(Commands.literal("account").then(Commands.argument("id",StringArgumentType.string()).then(Commands.argument("balance",LongArgumentType.longArg(0,CityEconomy.LIMIT)).executes(ctx->run(ctx.getSource(),()->{var s=ctx.getSource();CityEconomy.get(s.getServer()).adjust(StringArgumentType.getString(ctx,"id"),LongArgumentType.getLong(ctx,"balance"),s.getServer().overworld().getGameTime(),"管理员 "+s.getTextName());return "账户已调整并记入审计日志";})))));
        for(String op:List.of("export","apply")){
            var f=Commands.argument("file",StringArgumentType.word());
            if(op.equals("export"))f.executes(ctx->run(ctx.getSource(),()->{Path p=file(ctx.getSource(),StringArgumentType.getString(ctx,"file"));Files.writeString(p,CityEconomy.JSON.toJson(export(ctx.getSource())));return "已导出 "+p;}));
            else f.then(Commands.argument("revision",LongArgumentType.longArg(0)).executes(ctx->run(ctx.getSource(),()->{var s=ctx.getSource();Path p=file(s,StringArgumentType.getString(ctx,"file"));CityEconomy.require(Files.size(p)<=16*1024*1024,"经济文件超过16MB");JsonObject json=JsonParser.parseString(Files.readString(p)).getAsJsonObject();Files.writeString(p.resolveSibling(p.getFileName()+".before-"+System.currentTimeMillis()+".json"),CityEconomy.JSON.toJson(export(s)));apply(s,json,LongArgumentType.getLong(ctx,"revision"));return "经济数据已原子校验并应用，revision="+CityEconomy.get(s.getServer()).state.revision;})));
            root.then(Commands.literal(op).then(f));
        }
        root.then(Commands.literal("view").then(Commands.argument("city",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),()->{overview(ctx.getSource().getPlayerOrException(),UUID.fromString(StringArgumentType.getString(ctx,"city")),"");return "已打开城市经济";}))));
        root.then(Commands.literal("market").then(Commands.argument("npc",StringArgumentType.word()).executes(ctx->run(ctx.getSource(),()->{market(ctx.getSource().getPlayerOrException(),UUID.fromString(StringArgumentType.getString(ctx,"npc")),"");return "已打开真实库存商店";}))));
        dispatcher.register(root);
    }
}

