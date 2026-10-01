package audit;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import noppes.npcs.*;
import noppes.npcs.api.wrapper.WrapperEntityData;
import noppes.npcs.controllers.*;
import noppes.npcs.controllers.data.*;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.*;
import noppes.npcs.packets.server.SPacketDialogCategorySave;
import noppes.npcs.roles.RoleTrader;

@Mod("npc_audit")
public class NpcAudit {
    MinecraftServer server;
    ServerLevel level;
    int tick=0, stage=0, stageTicks=0;
    long tickStart;
    boolean finished=false;
    final List<EntityNPCInterface> npcs=new ArrayList<>();
    final List<Zombie> targets=new ArrayList<>();
    final List<Double> samples=new ArrayList<>();
    final List<Map<String,Object>> results=new ArrayList<>();
    final String[] stages={"baseline_0", "no_ai_50", "idle_ai_50", "idle_ai_150", "moving_ai_150", "combat_ai_30", "recovery_0"};
    final Path report=Path.of("audit-results.json");
    final List<Integer> retainedIds=new ArrayList<>();
    FakePlayer observer;
    int npcDamageEvents=0;double npcDamageTotal=0;final Set<Integer> npcAttackers=new HashSet<>();
    public NpcAudit() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::pre);
        NeoForge.EVENT_BUS.addListener(this::post);
        NeoForge.EVENT_BUS.addListener(this::damage);
    }
    void damage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e){if(e.getSource().getEntity() instanceof EntityNPCInterface n && npcs.contains(n) && targets.contains(e.getEntity()) && e.getNewDamage()>0){npcDamageEvents++;npcDamageTotal+=e.getNewDamage();npcAttackers.add(n.getId());}}
    void record(String name, String status, Object... data) {
        Map<String,Object> r=new LinkedHashMap<>();r.put("test",name);r.put("status",status);
        for(int i=0;i<data.length;i+=2)r.put(data[i].toString(),data[i+1]);
        results.add(r);System.out.println("NPC_AUDIT "+new GsonBuilder().create().toJson(r));save();
    }
    void save(){try{Files.writeString(report,new GsonBuilder().setPrettyPrinting().create().toJson(results));}catch(Exception e){e.printStackTrace();}}
    interface Checked {void run() throws Exception;}
    void test(String name, Checked body){try{body.run();}catch(Throwable e){record(name,"HARNESS_ERROR","error",e.toString());e.printStackTrace();}}
    void started(ServerStartedEvent e){server=e.getServer();level=server.overworld();System.out.println("NPC_AUDIT READY");}
    void pre(ServerTickEvent.Pre e){tickStart=System.nanoTime();}
    @SuppressWarnings("unchecked") Map<Integer,Object> cache(Class<?> type)throws Exception{Field f=type.getDeclaredField("dataMap");f.setAccessible(true);return (Map<Integer,Object>)f.get(null);}
    EntityNPCInterface npc(double x,double z, boolean noAI){
        EntityNPCInterface n=CustomEntities.entityCustomNpc.create(level);n.setPos(x,-59,z);n.setNoAi(noAI);n.setInvulnerable(true);n.setPersistenceRequired();level.addFreshEntity(n);return n;
    }
    FakePlayer player(){FakePlayer p=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("0e0a02f1-3d6e-4ae9-99b3-0ad538e9eed1"),"NpcAuditPlayer"));p.setPos(8,-59,8);return p;}
    void runFunctionalTests(){
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
        for(int x=0;x<4;x++)for(int z=0;z<4;z++)level.setChunkForced(x,z,true);
        test("command_and_editor_permission",()->{
            FakePlayer p=player();p.setGameMode(GameType.SURVIVAL);
            boolean cmd=server.getCommands().getDispatcher().getRoot().getChild("noppes").canUse(p.createCommandSourceStack().withPermission(0));
            boolean edit=CustomNpcsPermissions.hasPermission(p,CustomNpcsPermissions.GLOBAL_DIALOG);
            boolean script=CustomNpcsPermissions.hasPermission(p,CustomNpcsPermissions.TOOL_SCRIPTER);
            p.getInventory().setItem(p.getInventory().selected,new ItemStack(CustomItems.wand));
            CompoundTag data=new CompoundTag();data.putInt("Slot",910000);data.putString("Title","AuditNonOpPacket");
            PacketServerBasic.handle(new SPacketDialogCategorySave(data),server,p);
            boolean written=DialogController.instance.categories.values().stream().anyMatch(c->c.title.equals("AuditNonOpPacket"));
            record("command_and_editor_permission",written||cmd||edit||script?"FAIL":"PASS","playerOp",p.hasPermissions(2),"commandAllowed",cmd,"dialogPermission",edit,"scriptPermission",script,"heldTool","wand","nonOpPacketCreatedCategory",written,"opsOnly",CustomNpcs.OpsOnly);
        });
        test("mark_cache_retention",()->{
            Map<Integer,Object> map=cache(MarkData.class);int before=map.size();List<Integer> ids=new ArrayList<>();
            for(int i=0;i<200;i++){Cow c=EntityType.COW.create(level);c.setPos(8,-59,8);level.addFreshEntity(c);ids.add(c.getId());MarkData.get(c);c.discard();}
            Field ef=MarkData.class.getDeclaredField("entity");ef.setAccessible(true);int retained=0;
            for(int id:ids){Object md=map.get(id);if(md!=null&&((Entity)ef.get(md)).isRemoved())retained++;}
            record("mark_cache_retention",retained>0?"FAIL":"PASS","mapClass",map.getClass().getName(),"createdAndRemoved",200,"retainedRemovedEntities",retained,"entriesBefore",before,"entriesAfter",map.size());
        });
        test("wrapper_cache_identity",()->{
            Cow c=EntityType.COW.create(level);c.setPos(8,-59,8);Object a=WrapperEntityData.get(c),b=WrapperEntityData.get(c);
            record("wrapper_cache_identity",a==b?"PASS":"FAIL","sameWrapperOnRepeatedLookup",a==b,"cacheType",cache(WrapperEntityData.class).getClass().getName());c.discard();
        });
        test("cross_dimension_chunk_tickets",()->{
            ServerLevel other=server.getLevel(Level.NETHER);ChunkController cc=new ChunkController();
            int x=10,z=10;long key=ChunkPos.asLong(x,z);UUID a=UUID.randomUUID(),b=UUID.randomUUID();
            cc.load(level,a,x,z);cc.load(other,b,x,z);
            boolean first=level.getForcedChunks().contains(key),second=other.getForcedChunks().contains(key);
            cc.unload(level,a,x,z);cc.unload(other,b,x,z);
            boolean stranded=level.getForcedChunks().contains(key);
            record("cross_dimension_chunk_tickets",first&&second&&!stranded?"PASS":"FAIL","overworldForced",first,"netherForced",second,"overworldStillForcedAfterUnload",stranded);
            level.setChunkForced(x,z,false);other.setChunkForced(x,z,false);cc.clear();
        });
        test("duplicate_chunk_tickets",()->{
            ChunkController cc=ChunkController.instance;UUID a=UUID.randomUUID();int x=9,z=9;
            cc.load(level,a,x,z);cc.load(level,a,x,z);cc.unload(level,a,x,z);
            boolean left=level.getForcedChunks().contains(ChunkPos.asLong(x,z));
            record("duplicate_chunk_tickets",left?"FAIL":"PASS","forcedAfterDoubleLoadSingleUnload",left);
            cc.unload(level,a,x,z);level.setChunkForced(x,z,false);cc.clear();
        });
        test("script_engine_availability",()->{
            Map<String,String> langs=ScriptController.Instance.languages;Map<String,Object> evals=new LinkedHashMap<>();
            for(var ent:ScriptController.Instance.factories.entrySet()){try{evals.put(ent.getKey(),String.valueOf(ent.getValue().getScriptEngine().eval(ent.getKey().toLowerCase().contains("lua")?"return 1+1":"1+1")));}catch(Throwable e){evals.put(ent.getKey(),e.toString());}}
            record("script_engine_availability",CustomNpcs.EnableScripting&&!evals.isEmpty()&&evals.values().stream().allMatch(v->v.equals("2"))?"PASS":"FAIL","enabled",CustomNpcs.EnableScripting,"languages",langs,"basicEvaluation",evals);
        });
        test("dialog_roundtrip_and_fault",()->{
            DialogCategory cat=new DialogCategory();cat.id=920000;cat.title="AuditPersistence";DialogController.instance.saveCategory(level.registryAccess(),cat);
            Dialog dia=new Dialog(cat);dia.id=920001;dia.title="AuditDialog";dia.text="旅人，欢迎来到测试城镇。";dia.sound="";
            DialogController.instance.saveDialog(level.registryAccess(),cat,dia);
            Dialog copy=new Dialog(cat);copy.readNBT(level.registryAccess(),dia.save(level.registryAccess(),new CompoundTag()));
            record("dialog_nbt_roundtrip",dia.text.equals(copy.text)&&dia.title.equals(copy.title)?"PASS":"FAIL","title",copy.title,"text",copy.text);
            Path dir=CustomNpcs.getLevelSaveDirectory().toPath().resolve("dialogs/AuditPersistence");
            Dialog bad=new Dialog(cat);bad.id=920002;bad.title="FaultInjected";bad.text="must not report successful persistence";bad.sound="";
            Path blocked=dir.resolve("920002.json");Files.createDirectories(blocked);Files.writeString(blocked.resolve("sentinel"),"audit obstruction");
            boolean returned=false;try{DialogController.instance.saveDialog(level.registryAccess(),cat,bad);returned=true;}catch(Exception ignored){}
            boolean persisted=Files.isRegularFile(blocked),registered=DialogController.instance.dialogs.containsKey(920002);
            record("dialog_save_fault",returned&&registered&&!persisted?"FAIL":"PASS","saveReturnedNormally",returned,"inMemoryRegistered",registered,"destinationIsRegularFile",persisted,"pendingFileExists",Files.exists(dir.resolve("920002.json_new")));
            Files.delete(blocked.resolve("sentinel"));Files.delete(blocked);DialogController.instance.dialogs.remove(920002);cat.dialogs.remove(920002);
        });
        test("npc_nbt_roundtrip",()->{
            EntityNPCInterface a=npc(8,8,true);a.display.setName("审计 NPC");a.setHealth(7);CompoundTag tag=new CompoundTag();a.save(tag);
            EntityNPCInterface b=CustomEntities.entityCustomNpc.create(level);b.load(tag);
            record("npc_nbt_roundtrip",a.display.getName().equals(b.display.getName())&&b.getHealth()==7?"PASS":"FAIL","name",b.display.getName(),"health",b.getHealth());a.discard();b.discard();
        });
        test("trader_transactions",()->{
            EntityNPCInterface n=npc(8,8,true);RoleTrader role=new RoleTrader(n);n.role=role;
            role.inventorySold.setItem(0,new ItemStack(Items.DIAMOND));role.inventoryCurrency.setItem(0,new ItemStack(Items.EMERALD,10));
            FakePlayer p=player();p.getInventory().clearContent();ContainerNPCTrader menu=new ContainerNPCTrader(123,p.getInventory(),n.getId());p.containerMenu=menu;
            boolean noMoney=!menu.canBuy(role.inventoryCurrency.getItem(0),ItemStack.EMPTY,p);
            p.getInventory().setItem(0,new ItemStack(Items.EMERALD,9));boolean shortMoney=!menu.canBuy(role.inventoryCurrency.getItem(0),ItemStack.EMPTY,p);
            p.getInventory().setItem(0,new ItemStack(Items.EMERALD,10));menu.clicked(0,0,ClickType.PICKUP,p);
            boolean charged=p.getInventory().countItem(Items.EMERALD)==0&&menu.getCarried().is(Items.DIAMOND)&&menu.getCarried().getCount()==1;
            p.getInventory().setItem(0,new ItemStack(Items.EMERALD,10));menu.setCarried(new ItemStack(Items.DIAMOND,64));menu.clicked(0,0,ClickType.PICKUP,p);
            boolean fullSafe=p.getInventory().countItem(Items.EMERALD)==10&&menu.getCarried().getCount()==64;
            ItemStack renamed=new ItemStack(Items.EMERALD,10);renamed.set(DataComponents.CUSTOM_NAME,Component.literal("Different component"));p.getInventory().setItem(0,renamed);
            boolean distinguish=!menu.canBuy(new ItemStack(Items.EMERALD,10),ItemStack.EMPTY,p);
            record("trader_transactions",noMoney&&shortMoney&&charged&&fullSafe&&distinguish?"PASS":"FAIL","noMoneyRejected",noMoney,"insufficientRejected",shortMoney,"correctDebitAndDelivery",charged,"fullCursorNoCharge",fullSafe,"customComponentDistinguished",distinguish);
            menu.setCarried(ItemStack.EMPTY);p.getInventory().clearContent();p.containerMenu=p.inventoryMenu;n.discard();
        });
        test("combat_damage",()->{
            EntityNPCInterface n=npc(8,8,false);Zombie z=EntityType.ZOMBIE.create(level);z.setPos(9,-59,8);z.setNoAi(true);level.addFreshEntity(z);float before=z.getHealth();boolean result=n.doHurtTarget(z);
            record("combat_damage",result&&z.getHealth()<before?"PASS":"FAIL","attackReturned",result,"healthBefore",before,"healthAfter",z.getHealth());n.discard();z.discard();
        });
        record("functional_complete","INFO","note","Measurements use full modpack, no connected real players; controlled forced chunks; 8G heap and 4 CPUs.");
    }
    void clearEntities(){for(var n:npcs)n.discard();npcs.clear();for(var t:targets)t.discard();targets.clear();}
    void phase2(){
        test("dialog_inmemory_after_save",()->{Dialog d=DialogController.instance.dialogs.get(920001);record("dialog_inmemory_after_save",d!=null&&d.text.equals("旅人，欢迎来到测试城镇。")?"PASS":"FAIL","loaded",d!=null);});
        test("script_engines_correct_syntax",()->{
            for(var ent:ScriptController.Instance.factories.entrySet()){
                String code=ent.getKey().toLowerCase().contains("lua")?"return 1+1":"1+1";
                Object result=ent.getValue().getScriptEngine().eval(code);
                record("script_engines_correct_syntax","2".equals(String.valueOf(result))?"PASS":"FAIL","engine",ent.getKey(),"result",String.valueOf(result));
            }
        });
        test("bounded_slow_script",()->{
            EntityNPCInterface n=CustomEntities.entityCustomNpc.create(level);n.script.setLanguage("ECMAScript");n.script.setEnabled(true);
            ScriptContainer s=new ScriptContainer(n.script);s.script="function warmup(e){} function tick(e){java.lang.Thread.sleep(60);}";s.run("warmup",null);
            List<Double> times=new ArrayList<>();for(int i=0;i<3;i++){long start=System.nanoTime();s.run("tick",null);times.add((System.nanoTime()-start)/1e6);}
            record("bounded_slow_script",s.errored?"HARNESS_ERROR":"RISK_CONFIRMED","serverThread",server.isSameThread(),"requestedDelayMs",60,"observedMs",times,"scriptErrored",s.errored,"console",s.console);n.discard();
        });
        test("nonliving_persistent_nbt",()->{
            net.minecraft.world.entity.item.ItemEntity item=new net.minecraft.world.entity.item.ItemEntity(level,8,-59,8,new ItemStack(Items.STONE));
            CompoundTag tag=new CompoundTag();item.save(tag);tag.put("CNPC_persistantData",new CompoundTag());
            Throwable problem=null;try{item.load(tag);}catch(Throwable e){problem=e;}
            record("nonliving_persistent_nbt",problem==null?"PASS":"FAIL","error",problem==null?"":problem.toString());item.discard();
        });
        test("missing_dimension_validation",()->{
            var packet=new noppes.npcs.packets.server.SPacketDimensionTeleport(net.minecraft.resources.ResourceLocation.parse("npc_audit:absent"));packet.player=player();
            var method=packet.getClass().getDeclaredMethod("handle");method.setAccessible(true);Throwable problem=null;
            try{method.invoke(packet);}catch(java.lang.reflect.InvocationTargetException e){problem=e.getCause();}
            record("missing_dimension_validation",problem==null?"PASS":"FAIL","directHandlerInvocation",true,"error",problem==null?"":problem.toString());
        });
        test("quest_state_boundaries",()->{
            FakePlayer p=player();QuestCategory cat=new QuestCategory();cat.id=930000;cat.title="AuditQuests";Quest q=new Quest(cat);q.id=930001;q.title="审计任务";q.setType(5);q.repeat=noppes.npcs.constants.EnumQuestRepeat.NONE;QuestController.instance.quests.put(q.id,q);
            PlayerQuestData data=PlayerData.get(p).questData;data.activeQuests.clear();data.finishedQuests.clear();
            boolean fresh=PlayerQuestController.canQuestBeAccepted(p,q.id);PlayerQuestController.addActiveQuest(q,p);
            boolean active=PlayerQuestController.isQuestActive(p,q.id)&&!PlayerQuestController.canQuestBeAccepted(p,q.id);PlayerQuestController.setQuestFinished(q,p);
            boolean finished=PlayerQuestController.isQuestFinished(p,q.id)&&!PlayerQuestController.isQuestActive(p,q.id)&&!PlayerQuestController.canQuestBeAccepted(p,q.id);
            q.repeat=noppes.npcs.constants.EnumQuestRepeat.MCDAILY;data.finishedQuests.put(q.id,level.getGameTime()-23999);boolean before=!PlayerQuestController.canQuestBeAccepted(p,q.id);data.finishedQuests.put(q.id,level.getGameTime()-24000);boolean after=PlayerQuestController.canQuestBeAccepted(p,q.id);
            CompoundTag tag=new CompoundTag();data.saveNBTData(tag);PlayerQuestData copy=new PlayerQuestData();copy.loadNBTData(tag);boolean roundtrip=data.finishedQuests.equals(copy.finishedQuests);
            record("quest_state_boundaries",fresh&&active&&finished&&before&&after&&roundtrip?"PASS":"FAIL","freshAccepted",fresh,"activeDuplicateRejected",active,"finishedNonrepeatRejected",finished,"daily23999Rejected",before,"daily24000Accepted",after,"finishedNbtRoundtrip",roundtrip);
            data.activeQuests.clear();data.finishedQuests.clear();QuestController.instance.quests.remove(q.id);
        });
        test("delayed_cache_setup",()->{for(int i=0;i<200;i++){Cow c=EntityType.COW.create(level);c.setPos(8,-59,8);level.addFreshEntity(c);MarkData.get(c);retainedIds.add(c.getId());c.discard();}});
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
        for(int x=0;x<4;x++)for(int z=0;z<4;z++)level.setChunkForced(x,z,true);
        if(Files.exists(Path.of("ab.flag"))) return;
        observer=player();observer.setPos(24,-59,24);observer.setInvulnerable(true);level.addNewPlayer(observer);
        record("performance_observer","INFO","worldPlayerCount",level.players().size(),"note","Nearby FakePlayer in ServerLevel; no real client/network/render load; original optimization configuration unchanged.");
    }
    void phase3(){
        test("chunk_loader_job_reset",()->{
            ChunkController cc=new ChunkController();EntityNPCInterface n=CustomEntities.entityCustomNpc.create(level);n.setPos(200,-59,200);
            var job=new noppes.npcs.roles.JobChunkLoader(n);CompoundTag tag=new CompoundTag();tag.putLong("ChunkPlayerLastSeen",System.currentTimeMillis());job.load(tag);
            for(int i=0;i<20;i++)job.aiShouldExecute();int before=cc.size();job.reset();int remaining=0;
            for(int x=12;x<=13;x++)for(int z=12;z<=13;z++)if(level.getForcedChunks().contains(ChunkPos.asLong(x,z)))remaining++;
            record("chunk_loader_job_reset",remaining==0?"PASS":"FAIL","loadedChunkRecords",before,"forcedChunksAfterJobReset",remaining);
            for(int x=12;x<=13;x++)for(int z=12;z<=13;z++)level.setChunkForced(x,z,false);cc.clear();n.discard();
        });
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
        level.setDayTime(18000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
        for(int x=0;x<4;x++)for(int z=0;z<4;z++)level.setChunkForced(x,z,true);
        record("performance_method","INFO","worldPlayerCount",level.players().size(),"note","Isolated config whitelists CustomNPCs and zombies in Does It Tick. No fake observer; functional-test FakePlayer is not added to world. Event Pre-to-Post duration, not client or network benchmark.");
    }
    void beginStage(){
        clearEntities();samples.clear();stageTicks=0;
        npcDamageEvents=0;npcDamageTotal=0;npcAttackers.clear();
        int count=switch(stage){case 1,2->50;case 3,4->150;case 5->30;default->0;};
        for(int i=0;i<count;i++){
            EntityNPCInterface n=npc(4+(i%15)*3,4+(i/15)*3,stage==1);n.display.setName("Audit_"+i);npcs.add(n);
            if(stage==4){n.ais.setMovingType(1);n.updateAI=true;}
            if(stage==5){Zombie z=EntityType.ZOMBIE.create(level);z.setPos(n.getX()+2,-59,n.getZ());z.setNoAi(true);z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);z.setHealth(10000);z.setPersistenceRequired();level.addFreshEntity(z);targets.add(z);n.setTarget(z);}
        }
        record("stage_start","INFO","scenario",stages[stage],"npcs",count);
    }
    void endStage(){
        List<Double> sorted=new ArrayList<>(samples);Collections.sort(sorted);double avg=samples.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        boolean ticking=npcs.isEmpty()||npcs.getFirst().tickCount>=300;
        record("performance",ticking?"MEASURED":"INVALID_NOT_TICKING","scenario",stages[stage],"samples",samples.size(),"meanMs",avg,"p95Ms",sorted.get((int)(sorted.size()*.95)),"maxMs",sorted.getLast(),"firstNpcTickCount",npcs.isEmpty()?0:npcs.getFirst().tickCount,"livingNpcCount",npcs.stream().filter(n->!n.isRemoved()).count(),"firstNpcPosition",npcs.isEmpty()?"":npcs.getFirst().position().toString());
        if(stage==5)record("combat_workload_validation",npcDamageEvents>0?"PASS":"NOT_CONFIRMED","damageEvents",npcDamageEvents,"npcsWithSuccessfulAttack",npcAttackers.size(),"damageTotal",npcDamageTotal,"targetHealthTotal",targets.stream().mapToDouble(z->z.getHealth()).sum());
        if(++stage>=stages.length){finished=true;clearEntities();for(int x=0;x<4;x++)for(int z=0;z<4;z++)level.setChunkForced(x,z,false);record("audit_complete","DONE");server.halt(false);}else beginStage();
    }
    void post(ServerTickEvent.Post event){
        if(server==null||finished)return;
        try{
            tick++;
            if(tick==60){
                if(Files.exists(Path.of("ab.flag"))){runFunctionalTests();phase2();record("ab_complete","DONE");finished=true;server.halt(false);return;}
                if(Files.exists(Path.of("phase4.flag"))){phase3();stage=5;beginStage();return;}
                if(Files.exists(Path.of("phase3.flag"))){phase3();beginStage();return;}
                if(Files.exists(Path.of("phase2.flag"))){phase2();beginStage();return;}
                runFunctionalTests();beginStage();return;
            }
            if(tick<=60)return;
            if(tick==260&&!retainedIds.isEmpty())test("mark_cache_after_200_ticks",()->{Map<Integer,Object> map=cache(MarkData.class);Field ef=MarkData.class.getDeclaredField("entity");ef.setAccessible(true);int retained=0;for(int id:retainedIds){Object d=map.get(id);if(d!=null&&((Entity)ef.get(d)).isRemoved())retained++;}record("mark_cache_after_200_ticks",retained==0?"PASS":"FAIL","removedEntities",200,"stillStronglyReferenced",retained);});
            stageTicks++;
            if(stage==4&&stageTicks%40==0)for(var n:npcs)n.getNavigation().moveTo(4+((stageTicks/40)%2)*40,-59,24,1);
            if(stage==5&&stageTicks%40==0)for(int i=0;i<npcs.size();i++)npcs.get(i).setTarget(targets.get(i));
            if(stageTicks>100)samples.add((System.nanoTime()-tickStart)/1e6);
            if(stageTicks>=400)endStage();
        }catch(Throwable e){record("harness_fatal","HARNESS_ERROR","error",e.toString());e.printStackTrace();finished=true;server.halt(false);}
    }
}
