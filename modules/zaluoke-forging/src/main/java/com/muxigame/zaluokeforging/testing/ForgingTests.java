package com.muxigame.zaluokeforging.testing;

import com.muxigame.zaluokeforging.ModContent;
import com.muxigame.zaluokeforging.block.WorkshopEntity;
import com.muxigame.zaluokeforging.block.WorkshopBlock;
import com.muxigame.zaluokeforging.forging.Bellows;
import com.muxigame.zaluokeforging.forging.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("zaluoke_forging")
@PrefixGameTestTemplate(false)
public final class ForgingTests {
    private static WorkshopEntity machine(GameTestHelper h,int x,int y,int z,WorkshopBlock block) {
        BlockPos p=new BlockPos(x,y,z); h.setBlock(p,block.defaultBlockState()); return h.getBlockEntity(p);
    }
    private static void ticks(WorkshopEntity machine,int count) { for(int i=0;i<count;i++) machine.serverTick(); }
    private static void use(WorkshopEntity machine,Player player,ItemStack held) { player.setItemInHand(InteractionHand.MAIN_HAND,held); if(machine.kind().equals("blacksmith_anvil") && held.is(ModContent.HAMMER.get())) strike(machine,player,Workpiece.get(machine.work(),"Target"));
        else machine.interact(player,InteractionHand.MAIN_HAND); }
    // Legacy chain tests arrange a fresh visible window; cadence has independent real-time tests below.
    private static void strike(WorkshopEntity be,Player player,int zone) {
        be.targetStart=be.getLevel().getGameTime()-1; be.targetConsumed=false;
        strikeRaw(be,player,zone);
    }
    private static void strikeRaw(WorkshopEntity be,Player player,int zone) {
        var point=com.muxigame.zaluokeforging.forging.ForgeTarget.center(be.work(),be.getBlockPos(),be.getBlockState().getValue(WorkshopBlock.FACING),zone);
        be.hammer(player,new net.minecraft.world.phys.BlockHitResult(point,Direction.UP,be.getBlockPos(),false));
    }
    private static int dropped(GameTestHelper h,WorkshopEntity be,Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(be.getBlockPos()).inflate(3)).stream()
                .filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum();
    }

    @GameTest(template="empty") public static void completeSwordChain(GameTestHelper h) {
        var heater=machine(h,3,1,3,ModContent.HEARTH.get()); heater.fire=640000;
        var refiner=machine(h,3,2,3,ModContent.REFINER.get());
        refiner.setItem(0,new ItemStack(Items.RAW_IRON,10));
        ticks(refiner,1334);
        h.assertTrue(refiner.liquid==10 && refiner.getItem(0).isEmpty(),"Ten raw iron must become ten liquid units in 1334 ticks");
        var stand=machine(h,3,1,2,ModContent.MOLD_STAND.get());
        Player player=h.makeMockPlayer(GameType.SURVIVAL);
        use(stand,player,new ItemStack(ModContent.LONGSWORD_MOLD.get()));
        ticks(refiner,200);
        h.assertTrue(stand.work().is(ModContent.FILLED_MOLD.get()) && refiner.liquid==0,"Mold fills in ten seconds");
        ItemStack filled=stand.work().copy(); use(stand,player,ItemStack.EMPTY);
        h.assertTrue(stand.work().isEmpty() && player.getInventory().countItem(ModContent.FILLED_MOLD.get())==1,"Taking mold must transfer exactly once");
        var mold=machine(h,1,1,1,ModContent.PLACED_MOLD.get()); mold.work(filled);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModContent.HAMMER.get()));
        for(int i=0;i<3;i++) { mold.lastHammer=-100; mold.interact(player,InteractionHand.MAIN_HAND); }
        h.assertTrue(h.getLevel().getBlockState(mold.getBlockPos()).isAir(),"Three tool strikes must release mold");
        h.assertTrue(dropped(h,mold,ModContent.SWORD_BLANK.get())==1 && dropped(h,mold,ModContent.LONGSWORD_MOLD.get())==1,"Opening mold must produce a blank and reusable mold");
        var hearth=machine(h,5,1,5,ModContent.HEARTH.get()); hearth.fire=640000;
        var anvil=machine(h,6,1,5,ModContent.ANVIL.get());
        ItemStack blank=new ItemStack(ModContent.SWORD_BLANK.get());
        for(int round=0;round<2;round++) {
            use(hearth,player,blank); ticks(hearth,(int)Math.ceil(400-Workpiece.thermal(hearth.work())));
            h.assertTrue(Workpiece.heat(hearth.work())==2,"Billet should be white at 20 seconds");
            blank=hearth.work().copy(); use((WorkshopEntity)h.getLevel().getBlockEntity(hearth.getBlockPos().above()),player,ItemStack.EMPTY);
            use(anvil,player,blank);
            int strikes=round==1?2:10;
            for(int i=0;i<strikes;i++) { anvil.lastHammer=-100; use(anvil,player,new ItemStack(ModContent.HAMMER.get())); }
            blank=anvil.work().copy(); use(anvil,player,ItemStack.EMPTY);
        }
        h.assertTrue(Workpiece.get(blank,"Hits")==12 && Workpiece.heat(blank)==2,"12 hot strikes must produce a hot ready blank");
        var barrel=machine(h,5,1,6,ModContent.BARREL.get()); use(barrel,player,new ItemStack(Items.WATER_BUCKET));
        use(barrel,player,blank);
        h.assertTrue(barrel.water==2 && player.getInventory().countItem(ModContent.FORGED_SWORD.get())==1,"Quenching consumes one water level and yields exactly one sword");
        var sword=new ItemStack(ModContent.FORGED_SWORD.get());
        h.assertTrue(sword.getMaxDamage()==Tiers.IRON.getUses()+30,"Sword durability must be 280");
        final double[] damage={1},speed={4};
        sword.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND,(attribute,modifier)->{
            if(attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) damage[0]+=modifier.amount();
            if(attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED)) speed[0]+=modifier.amount();
        });
        h.assertTrue(damage[0]==8 && Math.abs(speed[0]-1.6)<.001,"Sword total damage 8, attack speed 1.6");
        h.succeed();
    }
    @GameTest(template="empty") public static void coolingClogAndManualRecovery(GameTestHelper h) {
        var r=machine(h,3,2,3,ModContent.REFINER.get()); r.liquid=3; r.setItem(1,new ItemStack(ModContent.SLAG.get(),63));
        ticks(r,1200);
        h.assertTrue(r.liquid==0 && r.slag()==64 && dropped(h,r,ModContent.SLAG.get())==8,"Cold iron converts 1:3; clogged overflow drops outside");
        var heat=machine(h,3,1,3,ModContent.HEARTH.get()); heat.fire=640000;
        r.setItem(0,new ItemStack(Items.RAW_IRON)); r.liquid=2;
        var stand=machine(h,3,1,2,ModContent.MOLD_STAND.get()); stand.work(new ItemStack(ModContent.LONGSWORD_MOLD.get()));
        ticks(r,400);
        h.assertTrue(r.liquid==2 && Workpiece.get(stand.work(),"Iron")==0 && r.getItem(0).getCount()==1,"Clogging blocks refinement AND output");
        Player p=h.makeMockPlayer(GameType.SURVIVAL); use(r,p,new ItemStack(Items.IRON_SHOVEL));
        h.assertTrue(r.slag()==54 && p.getInventory().countItem(ModContent.SLAG.get())==10,"Shovel removes exactly ten slag");
        stand.work(ItemStack.EMPTY); r.setItem(1,ItemStack.EMPTY); r.setItem(0,new ItemStack(ModContent.SLAG.get(),16)); r.liquid=0;r.progress=0;
        ticks(r,200);
        h.assertTrue(r.liquid==1 && r.getItem(0).isEmpty(),"Manually inserted sixteen slag recover one iron unit");
        h.succeed();
    }
    @GameTest(template="empty") public static void fullMoldOverflowAndRemoval(GameTestHelper h) {
        var heat=machine(h,3,1,3,ModContent.HEARTH.get()); heat.fire=640000;
        var r=machine(h,3,2,3,ModContent.REFINER.get()); r.liquid=15;
        var stand=machine(h,3,1,2,ModContent.MOLD_STAND.get()); stand.work(new ItemStack(ModContent.LONGSWORD_MOLD.get()));
        ticks(r,200); ticks(r,119);
        h.assertTrue(r.liquid==5 && dropped(h,r,ModContent.SLAG.get())==0,"Overflow has 5 seconds grace then one second cadence");
        ticks(r,1);
        h.assertTrue(r.liquid==4 && dropped(h,r,ModContent.SLAG.get())==1,"Overflow consumes stored iron at 1:1");
        stand.work(ItemStack.EMPTY); ticks(r,200);
        h.assertTrue(r.liquid==4,"Removing mold immediately stops overflow");
        h.succeed();
    }
    @GameTest(template="empty") public static void precisionSpeedAndCapacity(GameTestHelper h) {
        var heat=machine(h,3,1,3,ModContent.HEARTH.get()); heat.fire=640000;
        var r=machine(h,3,2,3,ModContent.REFINER.get()); r.setItem(0,new ItemStack(Items.RAW_IRON,64));
        r.setItem(1,new ItemStack(ModContent.SLAG.get(),8)); ticks(r,100);
        h.assertTrue(r.liquid==0 && r.progress==135,"Eight slag reduce speed by ten percent");
        ticks(r,49); h.assertTrue(r.liquid==1,"Slowed batch completes after 149 ticks");
        r.progress=0;r.setItem(1,ItemStack.EMPTY);Bellows.boost(h.getLevel(),heat.getBlockPos());ticks(r,84);
        h.assertTrue(r.liquid==2,"Bellows increase refinery speed to 1.6x");
        Bellows.boost(h.getLevel(),heat.getBlockPos());r.progress=0;ticks(r,84);
        h.assertTrue(r.liquid==3,"Repeated bellows refresh, do not stack");
        r.liquid=192;int input=r.getItem(0).getCount();ticks(r,1000);
        h.assertTrue(r.liquid==192 && r.getItem(0).getCount()==input,"Capacity is three stacks without consuming extra raw iron");
        h.succeed();
    }
    @GameTest(template="empty") public static void heatingOverheatAndObstruction(GameTestHelper h) {
        var f=machine(h,3,1,3,ModContent.HEARTH.get());f.setItem(0,new ItemStack(Items.CHARCOAL));f.work(new ItemStack(ModContent.SWORD_BLANK.get()));
        ticks(f,200);h.assertTrue(Workpiece.heat(f.work())==1,"Orange at ten seconds");
        ticks(f,200);h.assertTrue(Workpiece.heat(f.work())==2,"White at twenty seconds");
        ticks(f,259);h.assertTrue(!f.work().isEmpty(),"Not destroyed before ten accumulated overheating damage");
        ticks(f,1);h.assertTrue(f.work().isEmpty() && dropped(h,f,ModContent.SLAG.get())==24,"Overheat destruction yields 24 slag");
        h.assertTrue(f.fire==2540,"One charcoal supplies 3200 normal burn ticks");
        f.work(new ItemStack(ModContent.SWORD_BLANK.get())); h.setBlock(new BlockPos(3,2,3),ModContent.REFINER.get());ticks(f,400);
        h.assertTrue(Workpiece.heat(f.work())==0,"Occupied hearth top prevents billet heating");
        h.setBlock(new BlockPos(3,2,3),Blocks.AIR); f.heating=0;Bellows.boost(h.getLevel(),f.getBlockPos());ticks(f,250);
        h.assertTrue(Workpiece.heat(f.work())==2,"Boosted heating reaches white after 250 ticks");
        h.succeed();
    }
    @GameTest(template="empty") public static void coldStrikesAndSavedWork(GameTestHelper h) {
        var a=machine(h,3,1,3,ModContent.ANVIL.get());var p=h.makeMockPlayer(GameType.SURVIVAL);
        a.work(Workpiece.temperature(new ItemStack(ModContent.SWORD_BLANK.get()),2)); Workpiece.target(a.work(),h.getLevel().random);
        for(int i=0;i<5;i++) { a.lastHammer=-100;strike(a,p,Workpiece.get(a.work(),"Target")); }
        h.assertTrue(Workpiece.heat(a.work())==2 && Workpiece.get(a.work(),"Hits")==5,"Strikes must no longer consume temperature");
        var tag=a.saveWithFullMetadata(h.getLevel().registryAccess());
        var loaded=new WorkshopEntity(a.getBlockPos(),a.getBlockState());loaded.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(Workpiece.get(loaded.work(),"Hits")==5 && Workpiece.heat(loaded.work())==2,"Item process data survives block entity save/load");
        a.work(Workpiece.temperature(a.work(),0));
        for(int i=0;i<4;i++) { a.lastHammer=-100;strike(a,p,Workpiece.get(a.work(),"Target")); }
        h.assertTrue(Workpiece.get(a.work(),"Hits")==5 && Workpiece.damage(a.work())==4,"Cold strikes never count toward completion");
        for(int i=0;i<6;i++) { a.lastHammer=-100;strike(a,p,Workpiece.get(a.work(),"Target")); }
        h.assertTrue(a.work().isEmpty() && dropped(h,a,ModContent.SLAG.get())==24,"Tenth invalid strike destroys the blank");h.succeed();
    }
    @GameTest(template="empty") public static void waterAndColdQuench(GameTestHelper h) {
        var b=machine(h,3,1,3,ModContent.BARREL.get());var p=h.makeMockPlayer(GameType.SURVIVAL);
        var blank=Workpiece.put(new ItemStack(ModContent.READY_BLANK.get()),"Hits",12);
        use(b,p,new ItemStack(Items.WATER_BUCKET)); use(b,p,blank);
        h.assertTrue(b.water==3 && !p.getMainHandItem().isEmpty(),"Cold completed blank cannot quench");
        use(b,p,Workpiece.temperature(blank,1));
        h.assertTrue(b.water==2,"Hot completed blank consumes one bottle level");
        use(b,p,new ItemStack(Items.GLASS_BOTTLE));h.assertTrue(b.water==1,"Bottle can retrieve one water level");
        var potion=net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,net.minecraft.world.item.alchemy.Potions.WATER);
        use(b,p,potion);h.assertTrue(b.water==2,"Water bottle refills one level");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=150) public static void vanillaBellowsFuelAndExpiry(GameTestHelper h) {
        var pos=new BlockPos(3,1,3);h.setBlock(pos,Blocks.FURNACE);
        AbstractFurnaceBlockEntity f=h.getBlockEntity(pos);
        Bellows.boost(h.getLevel(),f.getBlockPos());
        h.runAtTickTime(5,()->{
            h.assertTrue(f.litTime==0,"Bellows cannot create fuel");f.setItem(1,new ItemStack(Items.COAL));Bellows.boost(h.getLevel(),f.getBlockPos());
        });
        h.runAtTickTime(15,()->{
            h.assertTrue(f.litTime>0 && f.getItem(1).isEmpty(),"Bellows ignites a fueled empty furnace");
        });
        h.runAtTickTime(125,()->{
            h.assertTrue(!Bellows.active(h.getLevel(),f.getBlockPos()),"Boost expires in six seconds");
            h.assertTrue(f.litTime>=1418 && f.litTime<=1422,"120 ticks uses 180 fuel ticks at 1.5x");h.succeed();
        });
    }

    @GameTest(template="empty") public static void actualPlacementPreservesData(GameTestHelper h) {
        var p=h.makeMockPlayer(GameType.SURVIVAL);
        var floor=new BlockPos(2,1,2);h.setBlock(floor,Blocks.STONE);
        ItemStack blank=Workpiece.put(Workpiece.temperature(new ItemStack(ModContent.SWORD_BLANK.get()),1),"Hits",14);
        p.setItemInHand(InteractionHand.MAIN_HAND,blank);
        blank.useOn(new net.minecraft.world.item.context.UseOnContext(p,InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(floor)),Direction.UP,h.absolutePos(floor),false)));
        var placed=(WorkshopEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(2,2,2)));
        h.assertTrue(placed!=null && Workpiece.get(placed.work(),"Hits")==14 && Workpiece.heat(placed.work())==1,"BlockItem placement must carry all workpiece data");
        h.assertTrue(blank.isEmpty(),"Placed workpiece consumes one item");
        ItemStack picked=placed.getBlockState().getBlock().getCloneItemStack(h.getLevel(),placed.getBlockPos(),placed.getBlockState());
        h.assertTrue(Workpiece.get(picked,"Hits")==14,"Pick-block keeps work history");
        use(placed,p,ItemStack.EMPTY);
        h.assertTrue(h.getLevel().getBlockState(placed.getBlockPos()).isAir(),"Retrieving placed blank removes display block");
        h.assertTrue(p.getInventory().countItem(ModContent.READY_BLANK.get())==1,"Legacy 14-hit billet becomes ready under the 12-hit requirement without duplication");h.succeed();
    }
    @GameTest(template="empty") public static void partialMoldAndMachinePersistence(GameTestHelper h) {
        var heat=machine(h,3,1,3,ModContent.HEARTH.get());heat.fire=640000;
        var r=machine(h,3,2,3,ModContent.REFINER.get());r.liquid=3;r.progress=73.6;r.setItem(1,new ItemStack(ModContent.SLAG.get(),8));
        var stand=machine(h,3,1,2,ModContent.MOLD_STAND.get());stand.work(new ItemStack(ModContent.LONGSWORD_MOLD.get()));ticks(r,60);
        h.assertTrue(Workpiece.get(stand.work(),"Iron")==3,"Partial mold fills available liquid only");
        ItemStack partial=stand.work().copy();stand.work(ItemStack.EMPTY);stand.work(partial);
        r.liquid=7;ticks(r,140);
        h.assertTrue(stand.work().is(ModContent.FILLED_MOLD.get()),"Replaced partial mold retains previous fill");
        r.liquid=187;r.progress=73.6;r.cooling=91;
        var tag=r.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored=new WorkshopEntity(r.getBlockPos(),r.getBlockState());restored.loadWithComponents(tag,h.getLevel().registryAccess());
        h.assertTrue(restored.liquid==187 && restored.slag()==8 && restored.cooling==91 && restored.progress==73.6,"Liquid, slag and both progress bars survive save/load");h.succeed();
    }
    @GameTest(template="empty") public static void menuLocksAndInputRules(GameTestHelper h) {
        var r=machine(h,3,1,3,ModContent.REFINER.get());var p=h.makeMockPlayer(GameType.SURVIVAL);
        var menu=new com.muxigame.zaluokeforging.menu.WorkshopMenu(1,p.getInventory(),r,r.data);
        h.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.IRON_ORE)) && !menu.getSlot(0).mayPlace(new ItemStack(Items.COAL)),"Refiner accepts raw iron, not ore blocks or fuel");
        h.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(Items.RAW_IRON)) && menu.getSlot(0).mayPlace(new ItemStack(ModContent.SLAG.get())),"Refiner accepts raw iron and recycled slag");
        h.assertTrue(!menu.getSlot(1).mayPickup(p) && !menu.getSlot(1).mayPlace(new ItemStack(ModContent.SLAG.get())),"Internal slag slot is locked to manual clicks");
        h.assertTrue(!r.canTakeItemThroughFace(1,new ItemStack(ModContent.SLAG.get()),Direction.DOWN),"Hoppers cannot bypass shovel cleaning");
        var f=machine(h,5,1,3,ModContent.HEARTH.get());f.fire=640000;f.setItem(0,new ItemStack(Items.COAL,2));ticks(f,1);
        h.assertTrue(f.getItem(0).getCount()==2 && f.fire==639999,"Full charcoal capacity does not consume queued fuel");
        h.assertTrue(ModContent.BRIGHT_BLANK.get().getDescriptionId().equals("item.zaluoke_forging.bright_sword_blank"),"Shared block heat items retain their own names");h.succeed();
    }
    @GameTest(template="empty") public static void reheatingKeepsDamageAndQuenchProgress(GameTestHelper h) {
        var f=machine(h,3,1,3,ModContent.HEARTH.get());f.fire=640000;var p=h.makeMockPlayer(GameType.SURVIVAL);
        var blank=Workpiece.put(Workpiece.put(new ItemStack(ModContent.SWORD_BLANK.get()),"Hits",12),"ColdHits",3);
        blank=Workpiece.temperature(blank,0);use(f,p,blank);ticks(f,200);
        h.assertTrue(Workpiece.get(f.work(),"Hits")==12 && Workpiece.get(f.work(),"ColdHits")==3 && Workpiece.heat(f.work())==1,"Reheat preserves finished forging and prior damage");
        var b=machine(h,5,1,3,ModContent.BARREL.get());b.water=1;use(b,p,f.work().copy());
        h.assertTrue(b.water==0 && p.getInventory().countItem(ModContent.FORGED_SWORD.get())==1,"Reheated finished blank is quenchable");
        var a=machine(h,6,1,3,ModContent.ANVIL.get());
        var piece=Workpiece.put(Workpiece.temperature(new ItemStack(ModContent.SWORD_BLANK.get()),2),"HeatHits",4);
        use(a,p,piece);strike(a,p,Workpiece.get(a.work(),"Target"));
        h.assertTrue(Workpiece.heat(a.work())==2,"Legacy five-strike counter no longer changes heat");
        h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=30) public static void blastFurnaceAndAutoFacing(GameTestHelper h) {
        var pos=new BlockPos(3,1,3);h.setBlock(pos,Blocks.BLAST_FURNACE);var p=h.makeMockPlayer(GameType.SURVIVAL);
        AbstractFurnaceBlockEntity f=h.getBlockEntity(pos);f.setItem(1,new ItemStack(Items.COAL));
        var context=new net.minecraft.world.item.context.BlockPlaceContext(new net.minecraft.world.item.context.UseOnContext(p,InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(pos)),Direction.EAST,h.absolutePos(pos),false)));
        var state=ModContent.BELLOWS.get().getStateForPlacement(context);
        h.assertTrue(state.getValue(WorkshopBlock.FACING)==Direction.WEST,"Side-attached bellows automatically point into the furnace");
        Bellows.boost(h.getLevel(),f.getBlockPos());
        h.runAtTickTime(10,()->{
            h.assertTrue(f.litTime>780 && f.litTime<800 && f.getItem(1).isEmpty(),"Blast furnace uses its vanilla half-duration fuel and starts empty");h.succeed();
        });
    }
    @GameTest(template="empty") public static void coveredCoolingAndOverflow(GameTestHelper h) {
        var r=machine(h,3,1,3,ModContent.REFINER.get());r.liquid=2;
        ticks(r,399);h.assertTrue(r.liquid==2,"No solidification before 20 seconds");
        ticks(r,1);h.assertTrue(r.liquid==1 && r.slag()==3,"One liquid produces three slag at 20 seconds");
        h.setBlock(new BlockPos(3,2,3),Blocks.STONE);
        ticks(r,470);h.assertTrue(r.liquid==1,"Cover reduces cooling speed by a further 15 percent");
        ticks(r,1);h.assertTrue(r.liquid==0 && r.slag()==6,"Covered cooling completes at tick 471");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=75) public static void bellowsInteractionCooldown(GameTestHelper h) {
        var heat=machine(h,3,1,3,ModContent.HEARTH.get());heat.fire=10000;
        var b=machine(h,3,1,4,ModContent.BELLOWS.get());var p=h.makeMockPlayer(GameType.SURVIVAL);
        use(b,p,ItemStack.EMPTY);long first=heat.boostUntil;long start=b.bellowsStart;
        h.runAtTickTime(30,()->{use(b,p,ItemStack.EMPTY);h.assertTrue(heat.boostUntil==first && b.bellowsStart==start,"Repeated clicks do not restart stroke or refresh boost during cooldown");});
        h.runAtTickTime(61,()->{use(b,p,ItemStack.EMPTY);h.assertTrue(heat.boostUntil>first && b.bellowsStart>start,"Next stroke allowed after three seconds");h.succeed();});
    }
    @GameTest(template="empty") public static void hearthSeparateTargetAndMenu(GameTestHelper h) {
        var f=machine(h,3,1,3,ModContent.HEARTH.get());f.fire=10000;var p=h.makeMockPlayer(GameType.SURVIVAL);
        use(f,p,new ItemStack(ModContent.SWORD_BLANK.get()));
        var top=(WorkshopEntity)h.getLevel().getBlockEntity(f.getBlockPos().above());
        h.assertTrue(top!=null && top.kind().equals("placed_blank"),"Billet is a separately targetable block above hearth");
        use(f,p,ItemStack.EMPTY);h.assertTrue(!top.work().isEmpty(),"Opening hearth must not retrieve billet");
        var menu=new com.muxigame.zaluokeforging.menu.WorkshopMenu(1,p.getInventory(),f,f.data);
        h.assertTrue(!menu.getSlot(1).isActive() && !menu.getSlot(2).isActive(),"Hearth only shows fuel slot");
        ticks(f,200);use(top,p,ItemStack.EMPTY);
        h.assertTrue(f.work().isEmpty() && h.getLevel().isEmptyBlock(f.getBlockPos().above()),"Top interaction retrieves heated billet without duplication");h.succeed();
    }

    @GameTest(template="empty") public static void hammerOnlyMoldOpening(GameTestHelper h) {
        var m=machine(h,3,1,3,ModContent.PLACED_MOLD.get());m.work(new ItemStack(ModContent.FILLED_MOLD.get()));
        var p=h.makeMockPlayer(GameType.SURVIVAL);
        for(var item:new Item[]{Items.IRON_AXE,Items.IRON_PICKAXE,Items.IRON_SWORD}) {m.lastHammer=-100;use(m,p,new ItemStack(item));}
        h.assertTrue(m.cracks==0,"Other tools cannot open filled mold");
        for(int i=0;i<3;i++) {m.lastHammer=-100;use(m,p,new ItemStack(ModContent.HAMMER.get()));}
        h.assertTrue(h.getLevel().isEmptyBlock(m.getBlockPos()) && dropped(h,m,ModContent.SWORD_BLANK.get())==1 && dropped(h,m,ModContent.LONGSWORD_MOLD.get())==1,"Three hammer interactions produce exactly one blank and mold");h.succeed();
    }
    @GameTest(template="empty") public static void targetGeometryAndDamage(GameTestHelper h) {
        var a=machine(h,3,1,3,ModContent.ANVIL.get());var p=h.makeMockPlayer(GameType.SURVIVAL);
        use(a,p,Workpiece.temperature(new ItemStack(ModContent.SWORD_BLANK.get()),2));
        for(var facing:Direction.Plane.HORIZONTAL) for(int z=0;z<4;z++) {
            var point=com.muxigame.zaluokeforging.forging.ForgeTarget.center(a.getBlockPos(),facing,z);
            h.assertTrue(com.muxigame.zaluokeforging.forging.ForgeTarget.zone(a.getBlockPos(),facing,point,Direction.UP)==z,"Rotated visual and hit zones must match");
            h.getLevel().setBlock(a.getBlockPos(),a.getBlockState().setValue(WorkshopBlock.FACING,facing),3);
            var ray=h.getLevel().clip(new net.minecraft.world.level.ClipContext(point.add(0,2,0),point.add(0,-2,0),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            h.assertTrue(ray.getBlockPos().equals(a.getBlockPos()) && com.muxigame.zaluokeforging.forging.ForgeTarget.zone(a.getBlockPos(),facing,ray.getLocation(),ray.getDirection())==z,"Actual block raycast must hit the displayed blade zone");
            h.assertTrue(com.muxigame.zaluokeforging.forging.ForgeTarget.zone(a.getBlockPos(),facing,point,Direction.NORTH)==-1,"Side face is not a blade hit");
        }
        int target=Workpiece.get(a.work(),"Target");strike(a,p,(target+1)%4);
        h.assertTrue(Workpiece.get(a.work(),"Hits")==0 && Workpiece.damage(a.work())==1,"Wrong region damages without progress");
        a.lastHammer=-100;strike(a,p,target);
        h.assertTrue(Workpiece.get(a.work(),"Hits")==1 && a.targetConsumed && Workpiece.thermal(a.work())==400,"Correct region consumes its window without strike cooling");
        var saved=a.saveWithFullMetadata(h.getLevel().registryAccess());var restored=new WorkshopEntity(a.getBlockPos(),a.getBlockState());restored.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(Workpiece.damage(restored.work())==1 && Workpiece.get(restored.work(),"Target")==Workpiece.get(a.work(),"Target"),"Damage and target persist");h.succeed();
    }
    @GameTest(template="empty") public static void elapsedCoolingAndGradient(GameTestHelper h) {
        var item=Workpiece.thermal(new ItemStack(ModContent.SWORD_BLANK.get()),400,1000);
        h.assertTrue(Workpiece.thermalAt(item,1200)==300 && Workpiece.thermalAt(item,1800)==0,"Cooling is half normal heating speed, including time in storage");
        h.assertTrue(Workpiece.color(item,1000)!=Workpiece.color(item,1100) && Workpiece.color(item,1100)!=Workpiece.color(item,1200),"Colors change continuously between thermal stages");
        var moved=Workpiece.settle(item,1200);h.assertTrue(Workpiece.thermalAt(moved,1400)==200,"Transfers must not reset cooling clock");h.succeed();
    }
    @GameTest(template="empty") public static void overheatGraceAndCombinedDamage(GameTestHelper h) {
        var f=machine(h,3,1,3,ModContent.HEARTH.get());f.fire=10000;var p=h.makeMockPlayer(GameType.SURVIVAL);
        var item=Workpiece.put(Workpiece.temperature(new ItemStack(ModContent.SWORD_BLANK.get()),2),"Damage",8);use(f,p,item);
        ticks(f,60);h.assertTrue(Workpiece.damage(f.work())==8,"White-hot billet has three seconds grace");
        ticks(f,20);h.assertTrue(Workpiece.damage(f.work())==9,"One further overheated second adds one hidden damage");
        ticks(f,20);h.assertTrue(f.work().isEmpty() && dropped(h,f,ModContent.SLAG.get())==24,"Combined damage reaches ten and destroys exactly once");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=60) public static void realTimeAnvilCooling(GameTestHelper h) {
        var a=machine(h,3,1,3,ModContent.ANVIL.get());a.work(Workpiece.thermal(new ItemStack(ModContent.SWORD_BLANK.get()),400,h.getLevel().getGameTime()));
        long start=h.getLevel().getGameTime();
        h.runAtTickTime(40,()->{
            double expected=400-(h.getLevel().getGameTime()-start)*.5;
            h.assertTrue(Math.abs(Workpiece.thermalAt(a.work(),h.getLevel().getGameTime())-expected)<.01,"Real server ticks cool a placed anvil billet at half heating rate");h.succeed();
        });
    }

    @GameTest(template="empty") public static void filledMoldRetrievalWithOccupiedHands(GameTestHelper h) {
        var stand=machine(h,3,1,3,ModContent.MOLD_STAND.get());
        for (Item held : new Item[]{ModContent.LONGSWORD_MOLD.get(), ModContent.HAMMER.get(), Items.IRON_PICKAXE}) {
            var p=h.makeMockPlayer(GameType.SURVIVAL);
            stand.work(Workpiece.put(new ItemStack(ModContent.FILLED_MOLD.get()),"Iron",10));
            use(stand,p,new ItemStack(held));
            h.assertTrue(stand.work().isEmpty() && p.getInventory().countItem(ModContent.FILLED_MOLD.get())==1,
                    "Filled mold must be retrievable while holding a mold or tool");
            h.assertTrue(p.getMainHandItem().is(held) && p.getMainHandItem().getCount()==1,"Retrieval must preserve held item");
        }
        var p=h.makeMockPlayer(GameType.SURVIVAL);
        p.setPos(stand.getBlockPos().getX()+.5,stand.getBlockPos().getY(),stand.getBlockPos().getZ()+.5);
        for(int slot=0;slot<36;slot++) p.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        stand.work(new ItemStack(ModContent.FILLED_MOLD.get()));
        stand.interact(p,InteractionHand.MAIN_HAND);
        stand.interact(p,InteractionHand.MAIN_HAND);
        h.assertTrue(stand.work().isEmpty() && dropped(h,stand,ModContent.FILLED_MOLD.get())==1,"Full inventory drops the retrieved mold exactly once");
        h.succeed();
    }
    @GameTest(template="empty") public static void hearthFurnaceLighting(GameTestHelper h) {
        var f=machine(h,3,1,3,ModContent.HEARTH.get());
        ticks(f,1);h.assertTrue(f.getBlockState().getLightEmission()==0,"Empty hearth is dark");
        f.setItem(0,new ItemStack(Items.COAL));ticks(f,1);
        int furnaceLight=Blocks.FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT,true).getLightEmission();
        h.assertTrue(f.getBlockState().getLightEmission()==furnaceLight && furnaceLight==13,"Burning hearth matches vanilla furnace light even without a billet");
        f.fire=1;ticks(f,1);h.assertTrue(!f.getBlockState().getValue(WorkshopBlock.LIT) && f.getBlockState().getLightEmission()==0,"Exhausted hearth extinguishes light");
        f.setItem(0,new ItemStack(Items.CHARCOAL));ticks(f,1);
        h.assertTrue(f.getBlockState().getValue(WorkshopBlock.LIT),"Refueling relights hearth");h.succeed();
    }
    @GameTest(template="empty") public static void hearthRefinerJoin(GameTestHelper h) {
        var f=machine(h,3,1,3,ModContent.HEARTH.get());
        var r=machine(h,3,2,3,ModContent.REFINER.get());
        for(var facing:Direction.Plane.HORIZONTAL) {
            var state=f.getBlockState().setValue(WorkshopBlock.FACING,facing);
            h.assertTrue(state.getShape(h.getLevel(),f.getBlockPos()).max(Direction.Axis.Y)==1,
                    "Hearth walls must reach the bottom of the refiner in all orientations");
        }
        f.setItem(0,new ItemStack(Items.COAL));ticks(f,1);
        r.setItem(0,new ItemStack(Items.RAW_IRON));ticks(r,134);
        h.assertTrue(r.liquid==1,"Joined hearth heats refiner without a billet");h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=100) public static void timedHighlightWindows(GameTestHelper h) {
        var a=machine(h,3,1,3,ModContent.ANVIL.get());var p=h.makeMockPlayer(GameType.SURVIVAL);
        use(a,p,Workpiece.temperature(new ItemStack(ModContent.SWORD_BLANK.get()),2));
        int first=Workpiece.get(a.work(),"Target");
        strikeRaw(a,p,first);
        h.assertTrue(Workpiece.get(a.work(),"Hits")==0 && Workpiece.damage(a.work())==1,"A target must become visible before it accepts a strike");
        h.runAtTickTime(8,()->{
            h.assertTrue(a.targetActive(h.getLevel().getGameTime()),"Target active during first second");
            a.lastHammer=-100;strikeRaw(a,p,first);
            h.assertTrue(Workpiece.get(a.work(),"Hits")==1 && a.targetConsumed,"One correct hit consumes the window");
            a.lastHammer=-100;strikeRaw(a,p,first);
            h.assertTrue(Workpiece.get(a.work(),"Hits")==1 && Workpiece.damage(a.work())==2,"Repeated strikes cannot farm one highlight");
        });
        h.runAtTickTime(28,()->{
            h.assertTrue(!a.targetActive(h.getLevel().getGameTime()),"Second second has no target");
            a.lastHammer=-100;strikeRaw(a,p,first);
            h.assertTrue(Workpiece.get(a.work(),"Hits")==1 && Workpiece.damage(a.work())==3,"Hits during rest are incorrect");
        });
        h.runAtTickTime(48,()->{
            h.assertTrue(a.targetActive(h.getLevel().getGameTime()) && Workpiece.get(a.work(),"Target")!=first,"Next cycle chooses one different region");
            a.lastHammer=-100;strikeRaw(a,p,Workpiece.get(a.work(),"Target"));
            h.assertTrue(Workpiece.get(a.work(),"Hits")==2,"New window accepts next hit");
            var data=a.saveWithFullMetadata(h.getLevel().registryAccess());var restored=new WorkshopEntity(a.getBlockPos(),a.getBlockState());restored.loadWithComponents(data,h.getLevel().registryAccess());
            h.assertTrue(restored.targetStart==a.targetStart && restored.targetConsumed,"Reload cannot replay a consumed target");h.succeed();
        });
    }
    @GameTest(template="empty") public static void completionAtTwelveAndSharedDamage(GameTestHelper h) {
        var a=machine(h,3,1,3,ModContent.ANVIL.get());var p=h.makeMockPlayer(GameType.SURVIVAL);
        var blank=Workpiece.put(Workpiece.temperature(new ItemStack(ModContent.SWORD_BLANK.get()),2),"Hits",11);
        Workpiece.put(blank,"Damage",8);use(a,p,blank);
        strike(a,p,Workpiece.get(a.work(),"Target"));
        h.assertTrue(a.work().is(ModContent.READY_BLANK.get()) && Workpiece.get(a.work(),"Hits")==12 && Workpiece.damage(a.work())==8,"Twelfth correct strike completes without damage");
        long completed=a.finishedAt;a.lastHammer=-100;strikeRaw(a,p,Workpiece.get(a.work(),"Target"));
        h.assertTrue(a.finishedAt==completed && Workpiece.get(a.work(),"Hits")==12,"Completion cue fires once");
        var f=machine(h,5,1,3,ModContent.HEARTH.get());f.fire=10000;
        var item=a.work().copy();a.work(ItemStack.EMPTY);use(f,p,item);ticks(f,80);
        h.assertTrue(Workpiece.damage(f.work())==9,"Post-forging reheating still accumulates overheating damage");
        ticks(f,20);h.assertTrue(f.work().isEmpty() && dropped(h,f,ModContent.SLAG.get())==24,"Previously accumulated hammer and furnace damage share the ten-point limit");h.succeed();
    }
    @GameTest(template="empty") public static void shapedBladeHitRegions(GameTestHelper h) {
        var item=Workpiece.put(new ItemStack(ModContent.SWORD_BLANK.get()),"Hits",11);
        for(var facing:Direction.Plane.HORIZONTAL) for(int zone=0;zone<4;zone++) {
            var point=com.muxigame.zaluokeforging.forging.ForgeTarget.center(BlockPos.ZERO,facing,zone);
            h.assertTrue(com.muxigame.zaluokeforging.forging.ForgeTarget.zone(item,BlockPos.ZERO,facing,point,Direction.UP)==zone,"All shaped blade region centers remain strikeable");
        }
        double tip=com.muxigame.zaluokeforging.forging.ForgeTarget.START+3.95*com.muxigame.zaluokeforging.forging.ForgeTarget.STEP;
        h.assertTrue(com.muxigame.zaluokeforging.forging.ForgeTarget.halfWidth(item,tip)<com.muxigame.zaluokeforging.forging.ForgeTarget.halfWidth(new ItemStack(ModContent.SWORD_BLANK.get()),tip),"Tip narrows as forging progresses");h.succeed();
    }

    @GameTest(template="empty") public static void smallSlagBatchesAndPersistence(GameTestHelper h) {
        var heat=machine(h,3,1,3,ModContent.HEARTH.get());heat.fire=10000;
        var r=machine(h,3,2,3,ModContent.REFINER.get());var p=h.makeMockPlayer(GameType.SURVIVAL);
        var menu=new com.muxigame.zaluokeforging.menu.WorkshopMenu(1,p.getInventory(),r,r.data);
        p.getInventory().setItem(9,new ItemStack(ModContent.SLAG.get(),3));menu.quickMoveStack(p,3);ticks(r,30);
        h.assertTrue(r.getItem(0).isEmpty() && r.reclaimedSlag==3 && r.liquid==0,"Small slag batches inserted through GUI are processed and retained");
        var saved=r.saveWithFullMetadata(h.getLevel().registryAccess());var loaded=new WorkshopEntity(r.getBlockPos(),r.getBlockState());loaded.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(loaded.reclaimedSlag==3,"Partial slag recovery persists across reload");
        r.setItem(0,new ItemStack(ModContent.SLAG.get(),13));ticks(r,120);
        h.assertTrue(r.reclaimedSlag==0 && r.liquid==1,"Sixteen recovered slag yield exactly one molten iron");
        r.setItem(0,new ItemStack(ModContent.SLAG.get(),3));ticks(r,30);r.dropContents();
        h.assertTrue(dropped(h,r,ModContent.SLAG.get())==4,"Breaking returns partial recovery plus stored liquid without duplication");h.succeed();
    }
    @GameTest(template="empty") public static void refinerPlacementOnHearth(GameTestHelper h) {
        var f=machine(h,3,1,3,ModContent.HEARTH.get());var p=h.makeMockPlayer(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ModContent.REFINER.get().asItem()));
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(f.getBlockPos()).add(0,.5,0),Direction.UP,f.getBlockPos(),false);
        h.useBlock(new BlockPos(3,1,3),p,hit);
        h.assertTrue(h.getLevel().getBlockState(f.getBlockPos().above()).is(ModContent.REFINER.get()),"Normal right-click places refiner atop hearth without sneaking or opening menu");h.succeed();
    }

    @GameTest(template="empty") public static void completeBattleaxeChain(GameTestHelper h) {
        var heat=machine(h,3,1,3,ModContent.HEARTH.get());heat.fire=100000;
        var r=machine(h,3,2,3,ModContent.REFINER.get());r.liquid=7;
        var stand=machine(h,3,1,2,ModContent.MOLD_STAND.get());var p=h.makeMockPlayer(GameType.SURVIVAL);
        use(stand,p,new ItemStack(ModContent.AXE_MOLD.get()));ticks(r,120);
        h.assertTrue(stand.work().is(ModContent.FILLED_AXE_MOLD.get()) && r.liquid==1 && Workpiece.get(stand.work(),"Iron")==6,"Battleaxe mold consumes exactly six iron units");
        var filled=stand.work().copy();use(stand,p,ItemStack.EMPTY);
        var mold=machine(h,1,1,1,ModContent.PLACED_MOLD.get());mold.work(filled);
        for(int i=0;i<3;i++){mold.lastHammer=-100;use(mold,p,new ItemStack(ModContent.HAMMER.get()));}
        h.assertTrue(dropped(h,mold,ModContent.AXE_BLANK.get())==1 && dropped(h,mold,ModContent.AXE_MOLD.get())==1,"Opening axe mold returns axe blank and reusable axe mold");
        var f=machine(h,5,1,5,ModContent.HEARTH.get());f.fire=100000;
        use(f,p,new ItemStack(ModContent.AXE_BLANK.get()));ticks(f,400);
        h.assertTrue(f.work().is(ModContent.DAZZLING_AXE.get()),"Axe heat stages preserve family");
        p.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(ModContent.TONGS.get()));
        var placed=(WorkshopEntity)h.getLevel().getBlockEntity(f.getBlockPos().above());use(placed,p,ItemStack.EMPTY);
        ItemStack blank=p.getInventory().items.stream().filter(Workpiece::blank).findFirst().orElseThrow().copy();
        h.assertTrue(Workpiece.clamped(blank) && p.getOffhandItem().isEmpty(),"Offhand tongs attach and are consumed exactly once");
        p.getInventory().clearContent();
        var a=machine(h,6,1,5,ModContent.ANVIL.get());use(a,p,blank);
        for(int i=0;i<10;i++){a.lastHammer=-100;strike(a,p,Workpiece.get(a.work(),"Target"));}
        h.assertTrue(a.work().is(ModContent.READY_AXE.get()) && Workpiece.get(a.work(),"Hits")==10,"Ten correct edge hits finish battleaxe");
        var ready=a.work().copy();a.work(ItemStack.EMPTY);
        var barrel=machine(h,6,1,7,ModContent.BARREL.get());barrel.water=3;use(barrel,p,ready);
        h.assertTrue(barrel.water==2 && p.getInventory().countItem(ModContent.FORGED_AXE.get())==1 && p.getInventory().countItem(ModContent.TONGS.get())==1,"Quench creates battleaxe and returns tongs once");
        var axe=new ItemStack(ModContent.FORGED_AXE.get());double[] values={1,4};
        axe.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND,(attribute,modifier)->{if(attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))values[0]+=modifier.amount();if(attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED))values[1]+=modifier.amount();});
        h.assertTrue(axe.getMaxDamage()==270 && values[0]==12 && Math.abs(values[1]-.9)<.001,"Battleaxe has 270 durability, 23 total damage, iron-axe attack speed");
        h.assertTrue(axe.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState())==new ItemStack(Items.IRON_AXE).getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()),"Mining efficiency matches iron axe");h.succeed();
    }
    @GameTest(template="empty") public static void axeRegionsAndEightDamage(GameTestHelper h) {
        var a=machine(h,3,1,3,ModContent.ANVIL.get());var p=h.makeMockPlayer(GameType.SURVIVAL);
        use(a,p,Workpiece.temperature(new ItemStack(ModContent.AXE_BLANK.get()),2));Workpiece.put(a.work(),"Tongs",1);
        for(var facing:Direction.Plane.HORIZONTAL) for(int zone=0;zone<2;zone++) {
            h.getLevel().setBlock(a.getBlockPos(),a.getBlockState().setValue(WorkshopBlock.FACING,facing),3);
            var point=com.muxigame.zaluokeforging.forging.ForgeTarget.center(a.work(),a.getBlockPos(),facing,zone);
            var ray=h.getLevel().clip(new net.minecraft.world.level.ClipContext(point.add(0,2,0),point.add(0,-2,0),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            h.assertTrue(com.muxigame.zaluokeforging.forging.ForgeTarget.zone(a.work(),a.getBlockPos(),facing,ray.getLocation(),ray.getDirection())==zone,"Rotated edge model and actual raycast agree");
            var middle=net.minecraft.world.phys.Vec3.atCenterOf(a.getBlockPos()).add(0,com.muxigame.zaluokeforging.forging.ForgeTarget.TOP-.5,0);
            h.assertTrue(com.muxigame.zaluokeforging.forging.ForgeTarget.zone(a.work(),a.getBlockPos(),facing,middle,Direction.UP)==-1,"Axe eye is never a valid edge target");
        }
        for(int i=0;i<7;i++){a.lastHammer=-100;strike(a,p,(Workpiece.get(a.work(),"Target")+1)%2);}
        h.assertTrue(!a.work().isEmpty() && Workpiece.damage(a.work())==7,"Axe survives seven errors");
        a.lastHammer=-100;strike(a,p,(Workpiece.get(a.work(),"Target")+1)%2);
        h.assertTrue(a.work().isEmpty() && dropped(h,a,ModContent.SLAG.get())==24 && dropped(h,a,ModContent.TONGS.get())==1,"Eighth error scraps axe and returns tongs once");h.succeed();
    }
    @GameTest(template="empty") public static void tongsTemperatureAndOverheating(GameTestHelper h) {
        for(var item:new Item[]{ModContent.SWORD_BLANK.get(),ModContent.AXE_BLANK.get()}) {
            var blank=Workpiece.thermal(new ItemStack(item),400,100);
            h.assertTrue(Workpiece.burnDamage(blank,100)==4 && Workpiece.burnDamage(blank,300)==2 && Workpiece.burnDamage(blank,900)==0,"Bare hot workpieces burn until cooled");
            Workpiece.put(blank,"Tongs",1);Workpiece.put(blank,"Hits",3);Workpiece.put(blank,"Damage",2);
            var cooled=Workpiece.settle(blank,300);
            h.assertTrue(Workpiece.burnDamage(cooled,300)==0 && Workpiece.clamped(cooled) && Workpiece.get(cooled,"Hits")==3 && Workpiece.damage(cooled)==2,"Clamped temperature changes preserve safety and history");
        }
        var f=machine(h,3,1,3,ModContent.HEARTH.get());f.fire=10000;var p=h.makeMockPlayer(GameType.SURVIVAL);
        var axe=Workpiece.put(Workpiece.temperature(new ItemStack(ModContent.AXE_BLANK.get()),2),"Damage",7);Workpiece.put(axe,"Tongs",1);use(f,p,axe);ticks(f,80);
        h.assertTrue(f.work().isEmpty() && dropped(h,f,ModContent.TONGS.get())==1,"Overheating uses axe damage limit and returns attached tongs");h.succeed();
    }
    @GameTest(template="empty") public static void axePartialMoldPersists(GameTestHelper h) {
        var mold=Workpiece.filled(new ItemStack(ModContent.AXE_MOLD.get()),5);
        h.assertTrue(mold.is(ModContent.AXE_MOLD.get()) && Workpiece.get(mold,"Iron")==5,"Five units do not complete axe mold");
        var full=Workpiece.filled(mold,6);
        h.assertTrue(full.is(ModContent.FILLED_AXE_MOLD.get()) && Workpiece.capacity(full)==6 && Workpiece.capacity(new ItemStack(ModContent.LONGSWORD_MOLD.get()))==10,"Mold capacities are independent");
        var placed=machine(h,3,1,3,ModContent.PLACED_MOLD.get());placed.work(full);
        var saved=placed.saveWithFullMetadata(h.getLevel().registryAccess());var restored=new WorkshopEntity(placed.getBlockPos(),placed.getBlockState());restored.loadWithComponents(saved,h.getLevel().registryAccess());
        h.assertTrue(Workpiece.axe(restored.work()) && Workpiece.get(restored.work(),"Iron")==6,"Full axe mold survives block save/load");h.succeed();
    }

}
