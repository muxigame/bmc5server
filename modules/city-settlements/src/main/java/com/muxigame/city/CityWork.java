package com.muxigame.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;

/** Visual work never awards goods or money. Runtime state is bounded by configured jobs. */
public final class CityWork {
    private static final Map<UUID,Motion> motions=new HashMap<>();
    private static CityEconomy.State indexed;
    private record Delivery(ServerLevel level,UUID city,BlockPos from,BlockPos to,long expires,int stage,long next){}
    private static final Map<UUID,Delivery> deliveries=new LinkedHashMap<>();
    static void delivery(ServerLevel level,CityData.City city,UUID npc,BlockPos from,BlockPos to){if(level.getEntity(npc)!=null&&deliveries.size()<64)deliveries.putIfAbsent(npc,new Delivery(level,city.id,from,to,level.getGameTime()+1200,0,0));}
    private static boolean deliveries(long now){
        var it=deliveries.entrySet().iterator();while(it.hasNext()){var entry=it.next();var d=entry.getValue();if(now>d.expires){it.remove();continue;}if(now<d.next)continue;var npc=d.level.getEntity(entry.getKey());if(!(npc instanceof Mob mob)||!mob.isAlive()){it.remove();continue;}if(mob.getTarget()!=null||mob.isPassenger()){it.remove();continue;}var pos=d.stage==0?d.from:d.to;if(!d.level.hasChunkAt(pos)){it.remove();continue;}
            if(mob.distanceToSqr(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5)<9){mob.swing(InteractionHand.MAIN_HAND);if(d.stage==1)it.remove();else entry.setValue(new Delivery(d.level,d.city,d.from,d.to,d.expires,1,now+40));return false;}
            entry.setValue(new Delivery(d.level,d.city,d.from,d.to,d.expires,d.stage,now+200));if(mob.getNavigation().isDone()){paths++;mob.getNavigation().moveTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5,0.65);return true;}return false;
        }return false;
    }
    private static int cursor;
    static long paths,harvests,visits;
    private static final class Motion {long next;int scan;BlockPos target;}
    public static void clear(){motions.clear();deliveries.clear();indexed=null;cursor=0;paths=harvests=visits=0;}
    static void validateSource(CityEconomy.Job j){
        String item,block;
        switch(j.resourceSource){
            case "wheat" -> {item="minecraft:wheat";block="minecraft:wheat";}
            case "rice" -> {item="city_settlements:rice";block="city_settlements:rice_crop";}
            case "forestry" -> {item="minecraft:oak_log";block="minecraft:oak_log";}
            case "quarry" -> {item="minecraft:cobblestone";block="minecraft:stone";}
            default -> throw new IllegalArgumentException("未知周期资源源");
        }
        int quantity=j.outputs.getOrDefault(item,0);
        CityEconomy.require(j.inputs.isEmpty()&&j.outputs.size()==1&&quantity>=1&&quantity<=64&&j.requiredBlocks.getOrDefault(block,0)>=quantity&&j.periodTicks>=1200,"资源源须单一对应产物1–64，设施数量至少等于产量，周期至少1200tick");
    }
    static boolean harvest(ServerLevel l,BlockPos p){
        if(!l.hasChunkAt(p))return false;
        var state=l.getBlockState(p);var rice=CityRice.CROP.get();
        if(!state.is(rice)||!rice.isMaxAge(state))return false;
        l.setBlock(p,rice.getStateForAge(0),3);return true;
    }
    public static void tick(MinecraftServer server){
        var e=CityEconomy.get(server);
        if(indexed!=e.state){clear();indexed=e.state;}
        var jobs=e.state.production;int n=jobs.size();
        long now=server.overworld().getGameTime();boolean pathUsed=deliveries(now);
        // Exactly two inspections; never scan all residents or all farm blocks each tick.
        for(int k=0;k<Math.min(2,n);k++){
            var j=jobs.get(Math.floorMod(cursor++,n));if(RuralSettlement.active(server,j.city)||CityMaintenance.collective(server,j.city)||!j.enabled||!j.workVisual||deliveries.containsKey(j.worker))continue;
            Motion m=motions.computeIfAbsent(j.id,id->new Motion());if(now<m.next)continue;
            m.next=now+200+Math.floorMod(j.worker.hashCode(),80);
            var c=CityData.get(server).cities.get(j.city);if(c==null||!WorkHours.active(server,c,j.worker))continue;
            ServerLevel l=CityEconomy.level(server,c);var entity=l.getEntity(j.worker);
            if(!(entity instanceof Mob mob)||!mob.isAlive()||mob.isNoAi()||mob.getTarget()!=null||mob.isPassenger()||!c.includes(mob.blockPosition()))continue;
            var b=c.buildings.get(j.building);if(b==null)continue;visits++;
            if(m.target!=null&&mob.distanceToSqr(m.target.getX()+0.5,m.target.getY(),m.target.getZ()+0.5)<9){
                mob.getLookControl().setLookAt(m.target.getX()+0.5,m.target.getY(),m.target.getZ()+0.5);
                mob.swing(InteractionHand.MAIN_HAND);
                if(harvest(l,m.target))harvests++;
                m.target=null;continue;
            }
            if(pathUsed||!mob.getNavigation().isDone())continue;
            var bounds=b.bounds();
            // Rice farmers preferentially visit mature rice, bounded search resumes next visit.
            if("rice".equals(j.resourceSource))for(int scan=0;scan<32;scan++){
                int tile=Math.floorMod(m.scan++,bounds.sizeX()*bounds.sizeZ());
                BlockPos p=new BlockPos(bounds.min().getX()+tile%bounds.sizeX(),bounds.min().getY()+1,bounds.min().getZ()+tile/bounds.sizeX());
                if(l.hasChunkAt(p)&&c.includes(p)&&l.getBlockState(p).is(CityRice.CROP.get())&&CityRice.CROP.get().isMaxAge(l.getBlockState(p))){m.target=p;break;}
            }
            if(m.target==null){int seed=mob.getRandom().nextInt(bounds.sizeX()*bounds.sizeZ());m.target=new BlockPos(bounds.min().getX()+seed%bounds.sizeX(),bounds.min().getY()+1,bounds.min().getZ()+seed/bounds.sizeX());}
            if(!l.hasChunkAt(m.target)||!c.includes(m.target)){m.target=null;continue;}
            // No forced chunks; no retry storm when a workplace is blocked.
            pathUsed=true;paths++;mob.getNavigation().moveTo(m.target.getX()+0.5,m.target.getY(),m.target.getZ()+0.5,0.65);
        }
    }
}


