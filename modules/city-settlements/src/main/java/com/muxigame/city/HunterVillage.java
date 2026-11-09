package com.muxigame.city;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Mob;
import net.minecraft.network.chat.Component;

/** Explicit admin fixture. All touched positions are checked before any mutation. */
public final class HunterVillage {
    static void resetPreflight(ServerPlayer player){
        var d=RuralSettlement.get(player.server);var city=RuralTools.city(player.serverLevel(),player.blockPosition());CityEconomy.require(city!=null&&city.name.equals("猎户农村夜间观察"),"仅限本轮观察村，不修改其他居民");
        try{var dir=RuralMonitor.folder(player.server);java.nio.file.Files.createDirectories(dir);java.nio.file.Files.writeString(dir.resolve("preflight-before-reset.json"),CityEconomy.JSON.toJson(RuralMonitor.snapshot(player.server)));}catch(java.io.IOException e){throw new IllegalArgumentException("无法保留预检记录",e);}
        for(var f:d.state.families.values())if(f.city.equals(city.id)){
            for(var stock:d.stocks(f.id)){stock.wheat=stock.bread=0;RuralMeat.clear(stock);}boolean merchant=false;
            for(UUID id:f.members){var p=d.state.people.get(id);merchant|=p.role==RuralRules.Role.MERCHANT;p.dead=false;p.nourishment=p.maximum();p.workingTicks=p.totalWorkingTicks=p.huntingTicks=p.cropTicks=p.hunts=p.emptyHunts=p.meatProduced=0;p.productionCredit=p.cookingCredit=0;p.pendingWheat=0;p.huntingSpot=null;p.workStatus="预检后重新开始";CityEconomy.get(player.server).state.accounts.put(CityEconomy.person(id),50L);
                var work=d.state.sites.values().stream().filter(s->id.equals(s.worker)).findFirst().orElse(null);if(work!=null&&player.serverLevel().getEntity(id)==null){var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("customnpcs:customnpc"));var entity=type.create(player.serverLevel());if(entity instanceof Mob mob){mob.setUUID(id);mob.setPos(BlockPos.of(work.pos).getX()+.5,BlockPos.of(work.pos).getY(),BlockPos.of(work.pos).getZ()+.5);mob.setNoAi(p.role!=RuralRules.Role.HUNTER);player.serverLevel().addFreshEntity(mob);}}if(work!=null&&player.serverLevel().getEntity(id) instanceof Mob mob){var pos=BlockPos.of(work.pos).south(3);try{mob.getClass().getMethod("reset").invoke(mob);}catch(ReflectiveOperationException ignored){}mob.getNavigation().stop();mob.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);mob.setHealth(mob.getMaxHealth());}
            }RuralRules.put(d.stocks(f.id),false,40);RuralRules.put(d.stocks(f.id),true,merchant?20:8);
        }
        d.state.forest.entrySet().removeIf(e->e.getValue().dimension.equals(city.dimension)&&HunterForest.horizontal(BlockPos.of(e.getValue().stand),city.bounds.min())<256*256);
        var origin=city.bounds.min();for(int patch:new int[]{0,96})for(int x=(origin.getX()+patch)>>4;x<=(origin.getX()+patch+15)>>4;x++)for(int z=(origin.getZ()+48)>>4;z<=(origin.getZ()+63)>>4;z++){var cell=HunterForest.inspect(player.serverLevel(),x,z);if(cell!=null)d.state.forest.put(HunterForest.key(city.dimension,x,z),cell);}
        d.state.audits.put(city.id,new RuralMarket.Audit());d.state.simulationTicks=0;d.state.simulationDay=1000;d.state.minutes=0;d.state.clockStarted=true;d.state.priceRemainders.clear();CityEconomy.get(player.server).setDirty();d.log("预检场地修正后手动重置初始库存/账户；非周期补给");RuralMonitor.start(player.server,city.id);d.setDirty();player.sendSystemMessage(Component.literal("观察基线重置完成，600银币、6个家庭，旧预检记录已保存"));
    }
    static void create(ServerPlayer player){
        var level=player.serverLevel();var server=player.server;BlockPos origin=player.blockPosition().offset(3,0,3);var bounds=CityBounds.between(origin,origin.offset(63,7,31));
        var cities=CityData.get(server);CityEconomy.require(cities.cities().stream().noneMatch(c->c.name.equals("猎户农村夜间观察")),"验证村已存在，不重复生成");cities.checkBounds(null,level.dimension().location().toString(),bounds);
        var touched=new HashSet<BlockPos>();for(int x=0;x<112;x++)for(int z=0;z<64;z++)touched.add(origin.offset(x,0,z));for(int x=0;x<64;x++)for(int z=0;z<32;z++)for(int y=0;y<8;y++)touched.add(origin.offset(x,y,z));
        for(int patch:new int[]{0,96})for(int x=0;x<16;x++)for(int z=48;z<64;z++)for(int y=0;y<8;y++)touched.add(origin.offset(patch+x,y,z));
        for(int x=5;x<=104;x++)for(int z=27;z<=29;z++)touched.add(origin.offset(x,0,z));for(int x:new int[]{5,104})for(int z=28;z<56;z++)for(int w=-1;w<=1;w++)touched.add(origin.offset(x+w,0,z));
        for(var pos:touched)CityEconomy.require(level.hasChunkAt(pos)&&level.getBlockState(pos).isAir(),"验证区域必须全部已加载且为空；请在空中选址，不覆盖已有建筑");
        var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse("customnpcs:customnpc"));CityEconomy.require(type!=null,"需要CustomNPCs");
        var city=cities.create(player.getUUID(),"猎户农村夜间观察",level.dimension().location().toString(),bounds);var d=RuralSettlement.get(server);var plan=CityMaintenance.get(server).state.cities.computeIfAbsent(city.id,k->new CityMaintenance.Plan());
        for(var pos:touched)if(pos.getY()==origin.getY())level.setBlock(pos,Blocks.GRASS_BLOCK.defaultBlockState(),3);
        for(int patch:new int[]{0,96})for(int x:new int[]{3,11})for(int z:new int[]{51,59}){
            for(int y=1;y<=5;y++)level.setBlock(origin.offset(patch+x,y,z),Blocks.OAK_LOG.defaultBlockState(),3);
            for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)for(int y=5;y<=6;y++)if(dx!=0||dz!=0||y==6)level.setBlock(origin.offset(patch+x+dx,y,z+dz),Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT,true),3);
        }
        for(int familyIndex=0;familyIndex<6;familyIndex++){
            RuralRules.Role role=familyIndex<3?RuralRules.Role.FARMER:familyIndex<5?RuralRules.Role.HUNTER:RuralRules.Role.MERCHANT;
            var people=new ArrayList<RuralRules.Person>();int baseX=2+familyIndex*10;
            for(int member=0;member<2;member++){
                var entity=type.create(level);CityEconomy.require(entity instanceof Mob,"NPC创建失败");entity.setPos(origin.getX()+baseX+member*3+.5,origin.getY()+1,origin.getZ()+6.5);entity.setCustomName(Component.literal((member==0?role.toString():"农妇")+"·观察"+familyIndex));((Mob)entity).setNoAi(member!=0||role!=RuralRules.Role.HUNTER);level.addFreshEntity(entity);city.residents.add(entity.getUUID());var person=d.enroll(server,city,entity.getUUID(),entity.getName().getString());person.role=member==0?role:RuralRules.Role.COOK;person.trainedHours=10;people.add(person);
                var pos=origin.offset(baseX+member*3,1,3);var block=member==1?RuralBlocks.STOVE.get():role==RuralRules.Role.FARMER?RuralBlocks.SIFTER.get():role==RuralRules.Role.HUNTER?RuralBlocks.HUNTER.get():RuralBlocks.DESK.get();level.setBlock(pos,block.defaultBlockState(),3);RuralTools.register(level,pos,(RuralBlocks.Link)level.getBlockEntity(pos));d.state.sites.get(RuralSettlement.key(city.id,pos.asLong())).worker=person.id;
            }
            var husband=people.get(0);var wife=people.get(1);var family=d.state.families.get(husband.family);UUID old=wife.family;RuralRules.admit(family,wife,d.state.people);d.state.families.remove(old);husband.spouse=wife.id;wife.spouse=husband.id;husband.marriage=wife.marriage=RuralRules.Marriage.FACTUAL;
            for(int i=0;i<(role==RuralRules.Role.MERCHANT?15:6);i++){var pos=origin.offset(baseX+i%6,1,9+i/6*2);level.setBlock(pos,RuralBlocks.CHEST.get().defaultBlockState(),3);var link=(RuralBlocks.Link)level.getBlockEntity(pos);link.offset=origin.offset(baseX+(i%2)*3,1,3).subtract(pos);link.setChanged();RuralTools.register(level,pos,link);}
            var stock=d.stocks(family.id);RuralRules.put(stock,false,role==RuralRules.Role.MERCHANT?40:40);RuralRules.put(stock,true,role==RuralRules.Role.MERCHANT?30:12);
            if(role==RuralRules.Role.FARMER)for(int x=0;x<5;x++)for(int z=0;z<4;z++){var pos=origin.offset(baseX+x,1,17+z);level.setBlock(pos,Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE,7),3);level.setBlock(pos.above(),Blocks.WHEAT.defaultBlockState(),3);plan.baseline.put(pos.above().asLong(),CityMaintenance.encode(Blocks.WHEAT.defaultBlockState()));}
        }
        plan.healthyPlots=60;d.enable(server,city);CityMaintenance.get(server).setDirty();cities.setDirty();
        // Explicit fixture setup records visible tree samples; ordinary runtime discovery is budgeted.
        for(int patch:new int[]{0,96})for(int x=(origin.getX()+patch)>>4;x<=(origin.getX()+patch+15)>>4;x++)for(int z=(origin.getZ()+48)>>4;z<=(origin.getZ()+63)>>4;z++){var cell=HunterForest.inspect(level,x,z);if(cell!=null)d.state.forest.put(HunterForest.key(city.dimension,x,z),cell);}
        d.log("管理员创建观察村：3农户、2猎户、1头人，6对夫妻，每成人初始50银币；仅此一次种子库存");RuralMonitor.start(server,city.id);d.setDirty();player.sendSystemMessage(Component.literal("观察村已创建，12名成人、60麦田、两个林区；每5分钟记录，持续12运行小时。范围原点："+origin.toShortString()));
    }
}

