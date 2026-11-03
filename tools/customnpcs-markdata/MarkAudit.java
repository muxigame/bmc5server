package audit.mark;
import java.nio.file.*;
import java.util.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.world.entity.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.MarkData;
@Mod("mark_audit") public class MarkAudit {
 final List<String> checks=new ArrayList<>(); final Map<String,Object> stats=new LinkedHashMap<>();
 void check(boolean b,String s){if(!b)throw new AssertionError(s);checks.add(s);}
 public MarkAudit(){NeoForge.EVENT_BUS.addListener(this::start);}
 static List<WeakReference<LivingEntity>> batch(ServerLevel l){
  List<WeakReference<LivingEntity>> refs=new ArrayList<>();
  EntityType<?>[] types={EntityType.ZOMBIE,EntityType.CREEPER,EntityType.SKELETON,EntityType.BAT,EntityType.CAT};
  for(int i=0;i<10000;i++){
   LivingEntity entity=(LivingEntity)types[i%types.length].create(l);
   CompoundTag nbt=new CompoundTag();entity.saveWithoutId(nbt);
   if(i%3==0)entity.load(nbt);
   MarkData.get(entity); // includes tracking/render/script callers as well as save mixins
   refs.add(new WeakReference<>(entity));
  }
  return refs;
 }
 void functional(ServerLevel l)throws Exception{
  LivingEntity e=EntityType.ZOMBIE.create(l);MarkData a=MarkData.get(e);
  check(a==MarkData.get(e),"same object reuses same mark cache");
  a.addMark(2,0x123456);a.save();var nbt=new CompoundTag();e.saveWithoutId(nbt);
  LivingEntity copy=EntityType.ZOMBIE.create(l);copy.load(nbt);MarkData restored=MarkData.get(copy);
  check(restored.marks.size()==1&&restored.marks.getFirst().color==0x123456,"mark type color NBT round trip");
  check(restored.marks.getFirst().type==2,"mark type preserved");
  var pool=Executors.newFixedThreadPool(8);try{
   List<Callable<MarkData>> jobs=new ArrayList<>();for(int i=0;i<128;i++)jobs.add(()->MarkData.get(e));
   for(var result:pool.invokeAll(jobs))check(result.get()==a,"parallel get shares entity cache");
  }finally{pool.shutdownNow();}
  LivingEntity twin=EntityType.SKELETON.create(l);twin.setId(e.getId());
  check(MarkData.get(twin)!=a&&MarkData.get(twin).marks.isEmpty(),"same numeric ID different object has isolated marks");
  e.discard();check(MarkData.get(e)==a,"discard retains entity-owned data for existing callers");
  var npc=(LivingEntity)noppes.npcs.CustomEntities.entityCustomNpc.create(l);check(MarkData.get(npc)==MarkData.get(npc),"NPC mark API remains functional");
 }
 void start(ServerStartedEvent event){var server=event.getServer();try{
  var refs=batch(server.overworld());int alive=10000;
  for(int i=0;i<12;i++){System.gc();Thread.sleep(100);alive=(int)refs.stream().filter(x->x.get()!=null).count();if(alive==0)break;}
  stats.put("unspawnedCandidates",10000);stats.put("aliveAfterGC",alive);
  var field=MarkData.class.getDeclaredField("dataMap");field.setAccessible(true);Map<?,?> global=(Map<?,?>)field.get(null);stats.put("compatMapClass",global.getClass().getName());stats.put("compatMapSize",global.size());
  check(alive==0,"all unspawned saved/read candidates collectible");check(global.isEmpty(),"legacy static compatibility map remains empty");
  functional(server.overworld());stats.put("passed",true);
 }catch(Throwable ex){ex.printStackTrace();stats.put("passed",false);stats.put("error",ex.toString());}finally{
  stats.put("checks",checks);try{Files.writeString(Path.of("result.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(stats));}catch(Exception ignored){}server.halt(false);
 }}
}
