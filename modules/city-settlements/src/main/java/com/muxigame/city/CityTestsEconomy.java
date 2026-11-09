package com.muxigame.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID)
@PrefixGameTestTemplate(false)
public final class CityTestsEconomy {
    @GameTest(template="empty") public static void longJournalAndLegacyEmptyLoad(GameTestHelper h){
        var e=money();String text="中文流水".repeat(200);for(int i=0;i<256;i++)e.transfer("buyer","business",0,CityEconomy.Purpose.TRADE,text,i);
        var tag=e.save(new CompoundTag(),h.getLevel().registryAccess());h.assertTrue(tag.getByteArray("JournalUtf8").length>65535,"Regression covers actual NBT string size limit");
        var restored=CityEconomy.load(tag,h.getLevel().registryAccess());h.assertTrue(restored.journal.size()==256&&restored.journal.getLast().detail().equals(text)&&restored.balance("buyer")==100,"Long UTF8 journal and state preserved");
        var legacy=new CompoundTag();legacy.putString("State",CityEconomy.JSON.toJson(e.state));legacy.putString("Journal","");legacy.putLong("Sequence",258);
        restored=CityEconomy.load(legacy,h.getLevel().registryAccess());h.assertTrue(restored.balance("business")==100&&restored.journal.isEmpty(),"Empty legacy journal cannot discard accounts");h.succeed();
    }
    @GameTest(template="empty") public static void ledgerBindingAndPagination(GameTestHelper h){
        var p=h.makeMockServerPlayerInLevel();var pos=h.absolutePos(new BlockPos(1,2,1));p.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
        var cities=CityData.get(h.getLevel().getServer());var c=cities.create(p.getUUID(),"账本测试",h.getLevel().dimension().location().toString(),CityBounds.between(pos,pos.offset(1,1,1)));
        var book=new ItemStack(CityMod.LEDGER.get());p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,book);CityLedgerItem.open(p,net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(c.id.equals(CityLedgerItem.binding(book))&&book.has(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT),"Right-click binds and prepares vanilla book content");
        p.setPos(pos.getX()+10,pos.getY(),pos.getZ());CityLedgerItem.open(p,net.minecraft.world.InteractionHand.MAIN_HAND);h.assertTrue(c.id.equals(CityLedgerItem.binding(book)),"Outside city retains existing binding");
        var blank=new ItemStack(CityMod.LEDGER.get());p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,blank);CityLedgerItem.open(p,net.minecraft.world.InteractionHand.MAIN_HAND);h.assertTrue(CityLedgerItem.binding(blank)==null,"Unbound book cannot bind outside city");
        var pages=CityLedgerItem.paginate(List.of(List.of("汉字".repeat(200))));h.assertTrue(pages.size()>1&&pages.stream().allMatch(v->v.raw().getString().split("\n").length<=12),"Book text fits multiple bounded pages");cities.cities.remove(c.id);cities.setDirty();h.succeed();
    }
    @GameTest(template="empty") public static void harvestRealCropsAtomically(GameTestHelper h){
        var d=new CityData();var p=h.absolutePos(new BlockPos(1,2,1));
        var c=d.create(UUID.randomUUID(),"农场","minecraft:overworld",CityBounds.between(p.below(),p.offset(2,1,2)));
        h.getLevel().setBlock(p,Blocks.BARREL.defaultBlockState(),3);
        h.getLevel().setBlock(p.east().below(),Blocks.FARMLAND.defaultBlockState(),3);
        h.getLevel().setBlock(p.east(),((net.minecraft.world.level.block.CropBlock)Blocks.WHEAT).getStateForAge(7),3);
        CityRegistry.warehouse(d,c,h.getLevel(),"粮仓",p);CityRegistry.building(d,c,"农场","农田",c.bounds);
        var e=money();var j=new CityEconomy.Job();j.id=UUID.randomUUID();j.city=c.id;j.building=c.buildings.keySet().iterator().next();j.warehouse=c.warehouses.keySet().iterator().next();j.worker=UUID.randomUUID();j.employer="business";j.harvestWheat=1;j.outputs.put("minecraft:wheat",1);j.requiredBlocks.put("minecraft:farmland",1);j.wage=3;
        e.adjust(CityEconomy.person(j.worker),0,0,"test");e.state.warehouseOwners.put(j.warehouse,"business");e.state.production.add(j);CityEconomy.validate(e.state);
        var box=CityEconomy.warehouse(h.getLevel(),c,j.warehouse);
        for(int i=0;i<box.getContainerSize();i++)box.setItem(i,new ItemStack(Items.STONE,64));
        rejects(h,()->e.productionAt(h.getLevel(),c,j));
        h.assertTrue(((net.minecraft.world.level.block.CropBlock)Blocks.WHEAT).isMaxAge(h.getLevel().getBlockState(p.east()))&&e.balance("business")==100,"Full storage must not harvest or pay");
        box.clearContent();e.productionAt(h.getLevel(),c,j);
        h.assertTrue(box.getItem(0).is(Items.WHEAT)&&box.getItem(0).getCount()==1&&((net.minecraft.world.level.block.CropBlock)Blocks.WHEAT).getAge(h.getLevel().getBlockState(p.east()))==0&&e.balance("business")==97,"Real mature crop becomes wheat and is replanted with wage");
        rejects(h,()->e.productionAt(h.getLevel(),c,j));h.assertTrue(e.balance("business")==97&&box.getItem(0).getCount()==1,"Immature crop cannot be harvested twice");
        var restored=CityEconomy.decode(e.export());h.assertTrue(restored.production.getFirst().harvestWheat==1,"Harvest settings persist");
        j.outputs.put("minecraft:diamond",1);rejects(h,()->CityEconomy.validate(e.state));h.succeed();
    }
    private static void rejects(GameTestHelper h,Runnable action){boolean rejected=false;try{action.run();}catch(IllegalArgumentException|ArithmeticException ex){rejected=true;}h.assertTrue(rejected,"Operation must be rejected");}
    private static CityEconomy money(){var e=new CityEconomy();e.adjust("buyer",100,0,"test");e.adjust("business",100,0,"test");return e;}
    @GameTest(template="empty") public static void moneyConservation(GameTestHelper h){
        var e=money();e.transfer("buyer","business",17,CityEconomy.Purpose.POLICY,"policy-test",1);
        h.assertTrue(e.balance("buyer")==83&&e.balance("business")==117,"Policy transfer conserves money");
        rejects(h,()->e.transfer("buyer","business",84,CityEconomy.Purpose.DEBT_INTEREST,"debt-test",2));
        rejects(h,()->e.transfer("buyer","business",-1,CityEconomy.Purpose.TRANSFER,"negative",2));
        e.transfer("buyer","buyer",5,CityEconomy.Purpose.TRANSFER,"self",3);
        h.assertTrue(e.balance("buyer")+e.balance("business")==200,"No mutation on failure or self transfer");
        var restored=CityEconomy.load(e.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.balance("buyer")==83&&restored.journal.size()==4,"Accounts and audit survive reload");h.succeed();
    }
    @GameTest(template="empty") public static void realTradeAndFullInventory(GameTestHelper h){
        var e=money();var stock=new SimpleContainer(new ItemStack(Items.BREAD,3));var bag=new SimpleContainer(1);
        e.exchange(stock,bag,1,"buyer","business","minecraft:bread",true,7,1,"offer");
        h.assertTrue(stock.getItem(0).getCount()==2&&bag.getItem(0).getCount()==1&&e.balance("buyer")==93&&e.balance("business")==107,"Buy transfers stock and funds");
        e.exchange(stock,bag,1,"buyer","business","minecraft:bread",false,5,2,"offer");
        h.assertTrue(stock.getItem(0).getCount()==3&&bag.isEmpty()&&e.balance("buyer")==98,"Sell returns stock and pays player");
        bag.setItem(0,new ItemStack(Items.STONE,64));long rev=e.state.revision;
        rejects(h,()->e.exchange(stock,bag,1,"buyer","business","minecraft:bread",true,7,3,"offer"));
        h.assertTrue(stock.getItem(0).getCount()==3&&e.state.revision==rev&&e.balance("buyer")==98,"Full backpack causes no charge or stock loss");h.succeed();
    }
    @GameTest(template="empty") public static void noSupplyNoMoneyNoTrade(GameTestHelper h){
        var e=money();var stock=new SimpleContainer(1);var bag=new SimpleContainer(1);
        rejects(h,()->e.exchange(stock,bag,1,"buyer","business","minecraft:bread",true,7,1,"offer"));
        stock.setItem(0,new ItemStack(Items.BREAD,1));rejects(h,()->e.exchange(stock,bag,1,"buyer","business","minecraft:bread",true,101,1,"offer"));
        h.assertTrue(e.balance("buyer")==100&&stock.getItem(0).getCount()==1&&bag.isEmpty(),"No credit and no free goods");
        stock.getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("private"));
        rejects(h,()->e.exchange(stock,bag,1,"buyer","business","minecraft:bread",true,7,1,"offer"));
        h.assertTrue(stock.getItem(0).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME),"Special components preserved");h.succeed();
    }
    @GameTest(template="empty") public static void consumptionIsReal(GameTestHelper h){
        var e=money();var stock=new SimpleContainer(new ItemStack(Items.BREAD,4));
        e.consumeAt(stock,"buyer","business","minecraft:bread",2,3,1);
        h.assertTrue(stock.getItem(0).getCount()==2&&e.balance("buyer")==94&&e.balance("business")==106,"Food removed and purchase paid");
        rejects(h,()->e.consumeAt(stock,"buyer","business","minecraft:bread",3,3,2));
        rejects(h,()->e.consumeAt(stock,"buyer","business","minecraft:stone",1,3,2));
        h.assertTrue(stock.getItem(0).getCount()==2&&e.balance("buyer")==94,"Failed consumption leaves inventory and money intact");h.succeed();
    }
    @GameTest(template="empty") public static void productionAndDestroyedFacility(GameTestHelper h){
        var d=new CityData();BlockPos pos=h.absolutePos(new BlockPos(1,2,1));var c=d.create(UUID.randomUUID(),"工坊","minecraft:overworld",CityBounds.between(pos,pos.offset(2,1,2)));
        h.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);h.getLevel().setBlock(pos.east(),Blocks.FURNACE.defaultBlockState(),3);
        CityRegistry.warehouse(d,c,h.getLevel(),"原料",pos);CityRegistry.building(d,c,"面包坊","工坊",c.bounds);
        var e=money();var job=new CityEconomy.Job();job.id=UUID.randomUUID();job.city=c.id;job.building=c.buildings.keySet().iterator().next();job.warehouse=c.warehouses.keySet().iterator().next();job.worker=UUID.randomUUID();job.employer="business";job.inputs.put("minecraft:wheat",3);job.outputs.put("minecraft:bread",1);job.requiredBlocks.put("minecraft:furnace",1);job.wage=4;
        e.adjust(CityEconomy.person(job.worker),0,0,"test");e.state.warehouseOwners.put(job.warehouse,"business");
        var box=CityEconomy.warehouse(h.getLevel(),c,job.warehouse);box.setItem(0,new ItemStack(Items.WHEAT,6));
        e.productionAt(h.getLevel(),c,job);
        h.assertTrue(box.getItem(0).getCount()==3&&box.getItem(1).is(Items.BREAD)&&e.balance(CityEconomy.person(job.worker))==4,"Production consumes materials, creates output, pays wages");
        box.setItem(0,new ItemStack(Items.WHEAT,6));for(int i=1;i<box.getContainerSize();i++)box.setItem(i,new ItemStack(Items.STONE,64));
        rejects(h,()->e.productionAt(h.getLevel(),c,job));h.assertTrue(box.getItem(0).getCount()==6&&e.balance(CityEconomy.person(job.worker))==4,"Full output storage consumes neither ingredients nor wages");
        h.getLevel().setBlock(pos.east(),Blocks.AIR.defaultBlockState(),3);long rev=e.state.revision;
        rejects(h,()->e.productionAt(h.getLevel(),c,job));h.assertTrue(box.getItem(0).getCount()==6&&e.state.revision==rev,"Destroyed required facility stops production without charging");
        h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);rejects(h,()->CityEconomy.warehouse(h.getLevel(),c,job.warehouse));h.succeed();
    }
    @GameTest(template="empty") public static void invalidImportIsAtomic(GameTestHelper h){
        var e=money();var original=e.export();var bad=original.deepCopy();bad.getAsJsonObject("accounts").addProperty("buyer",-1);
        rejects(h,()->e.apply(bad,e.state.revision,new CityData(),1));h.assertTrue(e.export().equals(original),"Negative balance rejects whole import");
        var fractional=original.deepCopy();fractional.getAsJsonObject("accounts").addProperty("buyer",1.5);
        rejects(h,()->e.apply(fractional,e.state.revision,new CityData(),1));
        rejects(h,()->e.apply(original,e.state.revision+1,new CityData(),1));
        var valid=original.deepCopy();valid.getAsJsonObject("accounts").addProperty("buyer",123);e.apply(valid,e.state.revision,new CityData(),2);
        h.assertTrue(e.balance("buyer")==123&&e.journal.getLast().kind().equals("ADMIN_IMPORT"),"Explicit admin adjustment audited");h.succeed();
    }
    @GameTest(template="empty") public static void idempotentDebtPayment(GameTestHelper h){
        var e=money();h.assertTrue(e.settle("bond:coupon:1","buyer","business",5,CityEconomy.Purpose.DEBT_INTEREST,1),"First payment");
        h.assertTrue(!e.settle("bond:coupon:1","buyer","business",5,CityEconomy.Purpose.DEBT_INTEREST,2),"Duplicate payment ignored");
        var restored=CityEconomy.load(e.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(!restored.settle("bond:coupon:1","buyer","business",5,CityEconomy.Purpose.DEBT_INTEREST,3)&&restored.balance("buyer")==95,"Restart cannot replay coupon");
        rejects(h,()->restored.settle("bond:coupon:1","buyer","business",6,CityEconomy.Purpose.DEBT_INTEREST,4));h.succeed();
    }
    @GameTest(template="empty") public static void stockImportConflictAndCommit(GameTestHelper h){
        var server=h.getLevel().getServer();var cities=CityData.get(server);var econ=CityEconomy.get(server);var old=econ.state;
        BlockPos pos=h.absolutePos(new BlockPos(1,2,1));var c=cities.create(UUID.randomUUID(),"库存导入测试",h.getLevel().dimension().location().toString(),CityBounds.between(pos,pos));h.getLevel().setBlock(pos,Blocks.CHEST.defaultBlockState(),3);
        CityRegistry.warehouse(cities,c,h.getLevel(),"库存",pos);UUID wid=c.warehouses.keySet().iterator().next();var box=CityEconomy.warehouse(h.getLevel(),c,wid);box.setItem(0,new ItemStack(Items.WHEAT,3));
        var json=econ.export();json.getAsJsonObject("accounts").addProperty("test-stock",42);
        var edits=new com.google.gson.JsonArray();var edit=new com.google.gson.JsonObject();edit.addProperty("city",c.id.toString());edit.addProperty("warehouse",wid.toString());var expected=new com.google.gson.JsonObject();expected.addProperty("minecraft:wheat",2);var desired=new com.google.gson.JsonObject();desired.addProperty("minecraft:bread",4);edit.add("expected",expected);edit.add("desired",desired);edits.add(edit);json.add("stockEdits",edits);
        long revision=econ.state.revision;
        rejects(h,()->CityEconomyAccess.apply(server.createCommandSourceStack(),json,revision));
        h.assertTrue(box.getItem(0).is(Items.WHEAT)&&box.getItem(0).getCount()==3&&econ.balance("test-stock")==0,"Stale physical inventory rejects accounts and goods together");
        expected.addProperty("minecraft:wheat",3);CityEconomyAccess.apply(server.createCommandSourceStack(),json,revision);
        h.assertTrue(box.getItem(0).is(Items.BREAD)&&box.getItem(0).getCount()==4&&econ.balance("test-stock")==42,"Validated JSON changes actual goods and accounts");
        econ.state=old;econ.setDirty();cities.cities.remove(c.id);cities.setDirty();h.succeed();
    }
}
