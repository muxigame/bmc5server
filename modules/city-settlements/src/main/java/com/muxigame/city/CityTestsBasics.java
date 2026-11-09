package com.muxigame.city;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public final class CityTestsBasics {
 @GameTest(template="empty") public static void hourAndReload(GameTestHelper h){
  var p=new CityLiving.Profile();p.foodPerHour=2;int eaten=0;
  for(int i=0;i<29;i++)eaten+=CityBasics.due(p);
  h.assertTrue(eaten==0,"No premature meal");
  p=CityEconomy.JSON.fromJson(CityEconomy.JSON.toJson(p),CityLiving.Profile.class);
  eaten+=CityBasics.due(p);h.assertTrue(eaten==1,"Reload preserves half-hour meal");
  for(int i=30;i<60;i++)eaten+=CityBasics.due(p);
  h.assertTrue(eaten==2&&p.foodCredit==0,"Exactly two portions per 72000 ticks");
  p.foodPerHour=64;eaten=0;for(int i=0;i<60;i++)eaten+=CityBasics.due(p);
  h.assertTrue(eaten==64&&p.foodCredit==0,"High rate conserves fractional progress");h.succeed();
 }
 @GameTest(template="empty") public static void realRationAndShortage(GameTestHelper h){try(var f=new CityTestsSupply.Fixture(h)){
  var p=f.e.state.living.get(f.people.getFirst());p.basicMode=true;p.basicWarehouse=f.food;p.periodTicks=1200;p.foodPerHour=2;p.minNeed=20;p.maxNeed=40;p.baseComfort=30;
  f.stock(f.food,CityTestsSupply.FOOD,2);long money=f.money();int need=p.need;
  for(int i=1;i<=60;i++)CityLiving.cycle(f.server,f.e,p,i*1200L);
  h.assertTrue(f.count(f.food,CityTestsSupply.FOOD)==0&&p.lastEssentials&&p.contribution==0&&p.need==need,"Two real meals, fixed needs, acceptable treatment");
  for(int i=61;i<=90;i++)CityLiving.cycle(f.server,f.e,p,i*1200L);
  h.assertTrue(!p.lastEssentials&&p.contribution==-1&&f.money()==money,"Shortage does not mint food or debit money");
  f.stock(f.food,CityTestsSupply.FOOD,1);p.baseComfort=40;for(int i=91;i<=120;i++)CityLiving.cycle(f.server,f.e,p,i*1200L);
  h.assertTrue(p.lastEssentials&&p.contribution==1&&!f.l.hasChunkAt(f.pos),"Recovery consumes real food without loading city chunks");
  CityEconomy.validate(f.e.state);CityEconomy.references(f.e.state,f.data);
 }h.succeed();}
 @GameTest(template="empty") public static void formAndEditSafety(GameTestHelper h){try(var f=new CityTestsSupply.Fixture(h)){
  var id=f.people.getFirst();var old=f.e.state.living.get(id);old.foodCredit=2400;var base=CityLivingEditor.draft(f.e,f.c,id);
  var fields=com.google.gson.JsonParser.parseString("{\"enabled\":true,\"foodPerHour\":2,\"min\":20,\"max\":40,\"treatment\":30,\"warehouse\":\""+f.food+"\",\"item\":\""+CityTestsSupply.FOOD+"\"}").getAsJsonObject();
  var edited=CityBasics.edit(base,fields);var next=CityEconomy.decode(CityLivingEditor.candidate(f.e,f.c,id,edited,100));
  h.assertTrue(next.living.get(id).foodCredit==2400&&next.accounts.equals(f.e.state.accounts)&&!next.living.get(f.people.get(1)).basicMode,"Save preserves progress, money and other citizens");
  fields.addProperty("min",41);CityTestsSimulation.reject(h,()->CityBasics.edit(base,fields));fields.addProperty("min",20);fields.addProperty("item","minecraft:stone");CityTestsSimulation.reject(h,()->CityBasics.edit(base,fields));
  h.assertTrue(CityBasics.contribution(19,20,40)==-1&&CityBasics.contribution(20,20,40)==0&&CityBasics.contribution(40,20,40)==1,"Treatment interval boundaries");
 }h.succeed();}
}
