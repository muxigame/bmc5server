package com.muxigame.city;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

/** Per-NPC configuration edits cannot write accounts, runtime history or other residents. */
public final class CityLivingEditor {
    private static final Map<UUID,Long> requests=new HashMap<>();
    private record EditBase(UUID npc,long revision,JsonObject settings){}
    private static final Map<ServerPlayer,EditBase> bases=new WeakHashMap<>();
    /** Runtime payments and scheduling must not invalidate a human's configuration draft. */
    static JsonObject settings(JsonObject draft){
        var out=draft.deepCopy();
        strip(out.getAsJsonObject("profile"),"foodCredit","nextTick","income","lastIncome","lastCost","lastBudget","lastTick","actual","contribution","lastCovered","lastEssentials","comfortableCycles","affordableCycles","status");
        for(var need:out.getAsJsonObject("profile").getAsJsonArray("basket"))strip(need.getAsJsonObject(),"status");
        for(var job:out.getAsJsonArray("jobs"))strip(job.getAsJsonObject(),"nextTick","status");
        strip(out.getAsJsonObject("society"),"nextTick","lastDistributed","status");
        strip(out.getAsJsonObject("security"),"nextTick","lastTick","lastCost","order","crime","activeGuards","absentGuards","unpaidGuards","coverage","status");
        return out;
    }
    private static void strip(JsonObject o,String... names){if(o!=null)for(String name:names)o.remove(name);}
    static JsonElement merge(JsonElement base,JsonElement current,JsonElement edited){
        if(Objects.equals(base,edited))return current==null?JsonNull.INSTANCE:current.deepCopy();
        if(Objects.equals(base,current)||Objects.equals(current,edited))return edited==null?JsonNull.INSTANCE:edited.deepCopy();
        if(base!=null&&current!=null&&edited!=null&&base.isJsonObject()&&current.isJsonObject()&&edited.isJsonObject()){
            var result=new JsonObject();Set<String> keys=new LinkedHashSet<>(base.getAsJsonObject().keySet());keys.addAll(current.getAsJsonObject().keySet());keys.addAll(edited.getAsJsonObject().keySet());
            for(String key:keys){var value=merge(base.getAsJsonObject().get(key),current.getAsJsonObject().get(key),edited.getAsJsonObject().get(key));if(!value.isJsonNull())result.add(key,value);}return result;
        }
        throw new IllegalArgumentException("同一设置已被修改，请刷新后重试（本地草稿保留）");
    }
    static JsonObject draft(CityEconomy e,CityData.City c,UUID npc){
        JsonObject d=new JsonObject();var profile=e.state.living.get(npc);
        if(profile==null){profile=CityLiving.defaults(c.id,npc);var old=e.state.consumption.stream().filter(v->v.npc.equals(npc)&&v.city.equals(c.id)&&v.enabled).findFirst();if(old.isPresent()){var v=old.get();profile.periodTicks=v.periodTicks;var n=profile.basket.getFirst();n.item=e.offer(v.offer).item;n.quantity=v.quantity;}}
        var guard=e.state.security.getOrDefault(c.id,new CitySecurity.Policy());d.add("security",CityEconomy.JSON.toJsonTree(guard));
        d.add("profile",CityEconomy.JSON.toJsonTree(profile));d.add("society",CityEconomy.JSON.toJsonTree(e.state.society.getOrDefault(c.id,new CitySociety.Policy())));
        var jobs=e.state.production.stream().filter(j->j.worker.equals(npc)).toList();d.add("jobs",CityEconomy.JSON.toJsonTree(jobs));return d;
    }
    static JsonObject candidate(CityEconomy e,CityData.City c,UUID npc,JsonObject input,long now){
        JsonObject whole=e.export();var next=CityEconomy.JSON.fromJson(whole,CityEconomy.State.class);
        CityEconomy.require(input.has("profile")&&input.has("jobs"),"编辑数据不完整");
        var p=CityEconomy.JSON.fromJson(input.get("profile"),CityLiving.Profile.class);CityEconomy.require(p!=null&&npc.equals(p.npc)&&c.id.equals(p.city),"NPC或城市已变化");
        var old=next.living.get(npc);
        // Runtime measurements are copied from the server, not trusted from the form.
        p.foodCredit=old==null?0:old.foodCredit;
        p.income=old==null?0:old.income;p.lastIncome=old==null?0:old.lastIncome;p.lastCost=old==null?0:old.lastCost;p.lastBudget=old==null?0:old.lastBudget;p.lastTick=old==null?0:old.lastTick;
        p.actual=old==null?0:old.actual;p.contribution=old==null?0:old.contribution;p.lastCovered=old!=null&&old.lastCovered;p.lastEssentials=old!=null&&old.lastEssentials;
        p.comfortableCycles=0;p.affordableCycles=0;p.status="配置已更新，等待下一期";
        p.nextTick=old==null||old.periodTicks!=p.periodTicks||old.enabled!=p.enabled?now+p.periodTicks:old.nextTick;
        if(old==null||old.enabled!=p.enabled){p.income=0;p.contribution=0;}
        next.accounts.putIfAbsent(CityEconomy.person(npc),0L);next.living.put(npc,p);
        if(p.enabled)next.consumption.stream().filter(d->d.npc.equals(npc)).forEach(d->d.enabled=false);
        var jobs=CityEconomy.JSON.fromJson(input.get("jobs"),CityEconomy.Job[].class);CityEconomy.require(jobs!=null&&jobs.length<=16,"单人编辑最多16份岗位");
        // This editor may create jobs, but may not silently remove existing jobs.
        for(var j:next.production)if(j.worker.equals(npc))CityEconomy.require(Arrays.stream(jobs).anyMatch(v->v!=null&&j.id.equals(v.id)),"岗位缺失，请刷新；停用岗位请关闭启用开关");
        for(var j:jobs){CityEconomy.require(j!=null&&j.id!=null&&j.worker.equals(npc)&&j.city.equals(c.id),"不能编辑其他NPC的岗位");var prior=next.production.stream().filter(v->v.id.equals(j.id)).findFirst().orElse(null);
            CityEconomy.require(prior==null||prior.worker.equals(npc),"岗位ID属于其他NPC");
            j.nextTick=prior==null||prior.periodTicks!=j.periodTicks||prior.enabled!=j.enabled?now+j.periodTicks:prior.nextTick;j.status="配置已更新，等待下一期";
            if(prior!=null)next.production.remove(prior);next.production.add(j);
        }
        // Exact integer decoding rejects fractional JSON values before Gson can truncate them.
        JsonObject strict=input.deepCopy();strict.add("accounts",new JsonObject());validateNumbers(strict);
        if(input.has("society")){var policy=CityEconomy.JSON.fromJson(input.get("society"),CitySociety.Policy.class);CityEconomy.require(policy!=null,"社会配置为空");var previous=next.society.get(c.id);policy.lastDistributed=previous==null?0:previous.lastDistributed;policy.nextTick=previous==null||previous.enabled!=policy.enabled||previous.periodTicks!=policy.periodTicks?now+policy.periodTicks:previous.nextTick;policy.status="配置已保存，等待结算";
            if(policy.enabled&&(previous==null||!previous.enabled))for(String account:CitySociety.businesses(next,c))next.businessNet.put(account,0L);
            next.society.put(c.id,policy);
        }
        if(input.has("security")){var sec=CityEconomy.JSON.fromJson(input.get("security"),CitySecurity.Policy.class);CityEconomy.require(sec!=null,"治安配置为空");var previous=next.security.get(c.id);
            sec.lastTick=previous==null?0:previous.lastTick;sec.lastCost=previous==null?0:previous.lastCost;sec.order=previous==null?sec.baseOrder:previous.order;sec.crime=previous==null?0:previous.crime;sec.activeGuards=previous==null?0:previous.activeGuards;sec.absentGuards=previous==null?0:previous.absentGuards;sec.unpaidGuards=previous==null?0:previous.unpaidGuards;sec.coverage=previous==null?0:previous.coverage;
            sec.nextTick=previous==null||previous.enabled!=sec.enabled||previous.periodTicks!=sec.periodTicks?now+sec.periodTicks:previous.nextTick;sec.status="配置已保存，等待治安结算";next.security.put(c.id,sec);
        }
        CityEconomy.validate(next);return CityEconomy.JSON.toJsonTree(next).getAsJsonObject();
    }
    private static void validateNumbers(JsonElement el){if(el.isJsonObject())el.getAsJsonObject().entrySet().forEach(v->validateNumbers(v.getValue()));else if(el.isJsonArray())el.getAsJsonArray().forEach(CityLivingEditor::validateNumbers);else if(el.isJsonPrimitive()&&el.getAsJsonPrimitive().isNumber())el.getAsBigDecimal().longValueExact();}
    public static void request(ServerPlayer player,CityNetwork.Request r){
        UUID id;CityData.City c;CityEconomy e=CityEconomy.get(player.server);String name;
        try{long now=player.server.overworld().getGameTime();Long last=requests.put(player.getUUID(),now);CityEconomy.require(last==null||now-last>=3,"操作太快，请稍后重试");
            id=UUID.fromString(r.city());var npc=CityNpcBridge.editing(player,id);CityEconomy.require(npc.distanceToSqr(player)<=4096,"请靠近要编辑的NPC");name=npc.getName().getString();c=CityNpcBridge.home(CityData.get(player.server),id);CityEconomy.require(c!=null,"请先在高级→阵营→所属城市绑定NPC");
        }catch(RuntimeException ex){player.sendSystemMessage(Component.literal("[NPC生活] "+ex.getMessage()));return;}
        String message="";boolean failed=false;
        try{if(r.op().equals("npclivingbasicsave")){
            var base=bases.get(player);CityEconomy.require(base!=null&&base.npc.equals(id)&&base.revision==r.revision(),"编辑会话已变化，请刷新后重试");var submitted=JsonParser.parseString(r.value()).getAsJsonObject();
            if(r.op().equals("npclivingbasicsave"))submitted=CityBasics.edit(base.settings,submitted);
            var input=merge(base.settings,settings(draft(e,c,id)),settings(submitted)).getAsJsonObject();var next=candidate(e,c,id,input,player.server.overworld().getGameTime());CityEconomy.references(CityEconomy.decode(next),CityData.get(player.server));
            Path directory=player.server.getWorldPath(LevelResource.ROOT).resolve("city_settlements/economy/backups");Files.createDirectories(directory);if(r.op().equals("npclivingbasicsave")){
                Path first=directory.resolve("basic-"+id+"-original.json");if(!Files.exists(first))Files.writeString(first,CityEconomy.JSON.toJson(draft(e,c,id)),StandardOpenOption.CREATE_NEW);
                Files.writeString(directory.resolve("basic-"+id+"-previous.json"),CityEconomy.JSON.toJson(draft(e,c,id)),StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
            }else Files.writeString(directory.resolve("npc-"+id+"-"+System.currentTimeMillis()+".json"),CityEconomy.JSON.toJson(e.export()),StandardOpenOption.CREATE_NEW);
            e.apply(next,e.state.revision,CityData.get(player.server),player.server.overworld().getGameTime());message=r.op().equals("npclivingbasicsave")?"基础生活已保存；原设置已备份。仅修改此NPC。":"已保存；旧经济JSON已备份。";
        }else CityEconomy.require(r.op().equals("npclivingbasicview"),"未知操作");
        }catch(Exception ex){failed=true;message="未保存："+ex.getMessage();}
        var snapshot=draft(e,c,id);if(!failed)bases.put(player,new EditBase(id,e.state.revision,settings(snapshot)));
        CompoundTag t=new CompoundTag();t.putBoolean("NpcLivingEditor",true);t.putBoolean("BasicLivingEditor",r.op().startsWith("npclivingbasic"));t.putBoolean("Failed",failed);t.putString("NpcId",id.toString());t.putString("Name",name);t.putString("CityName",c.name);t.putLong("Revision",e.state.revision);t.putString("Message",message);t.putString("Draft",CityEconomy.JSON.toJson(snapshot));t.putInt("Stability",CityLiving.stability(e,c));t.putString("SecurityStatus",CitySecurity.summary(e,c));t.putString("Risk",CitySociety.risk(CitySecurity.riskIndex(e,c)));
        JsonObject choices=new JsonObject();choices.add("buildings",CityEconomy.JSON.toJsonTree(c.buildings.values().stream().map(v->Map.of("id",v.id().toString(),"name",v.name())).toList()));
        choices.add("warehouses",CityEconomy.JSON.toJsonTree(c.warehouses.values().stream().filter(v->e.state.warehouseOwners.containsKey(v.id())).map(v->Map.of("id",v.id().toString(),"name",v.name(),"owner",e.state.warehouseOwners.get(v.id()))).toList()));
        t.putString("Choices",choices.toString());PacketDistributor.sendToPlayer(player,new CityNetwork.Snapshot(t));
    }
}
