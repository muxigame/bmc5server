package com.muxigame.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded loaded-world sampling. Remembered forest is usable by the data simulation offline. */
public final class HunterForest {
    public static final class Cell {public String dimension="";public int x,z,canopy,logs;public long checked,stand;public boolean usable;}
    static int cursor;static final Map<UUID,Integer> scans=new HashMap<>();
    static final LinkedHashSet<String> dirty=new LinkedHashSet<>();
    static void changed(net.neoforged.neoforge.event.level.BlockEvent event){
        if(!(event.getLevel() instanceof ServerLevel l)||!(event.getState().is(BlockTags.LOGS)||event.getState().is(BlockTags.LEAVES)))return;
        var d=RuralSettlement.get(l.getServer());String key=key(l.dimension().location().toString(),event.getPos().getX()>>4,event.getPos().getZ()>>4);var cell=d.state.forest.get(key);
        if(cell!=null){cell.usable=false;cell.checked=-6000;if(dirty.size()<256)dirty.add(key);d.setDirty();}
    }
    static void broken(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event){changed(event);}
    static void placed(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event){changed(event);}
    static String key(String dim,int x,int z){return dim+":"+x+":"+z;}
    static double horizontal(BlockPos a,BlockPos b){double x=(double)a.getX()-b.getX(),z=(double)a.getZ()-b.getZ();return x*x+z*z;}
    static boolean dense(int canopy,int logs){return canopy>=4&&logs>=1;}
    static BlockPos findStand(ServerLevel l,BlockPos canopy){
        if(!l.getBlockState(canopy).is(BlockTags.LEAVES))return null;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            var column=canopy.offset(dx,0,dz);if(!l.hasChunkAt(column))continue;
            for(int y=canopy.getY()-2;y>=Math.max(l.getMinBuildHeight()+1,canopy.getY()-16);y--){
                var stand=new BlockPos(column.getX(),y,column.getZ());
                if(!l.getBlockState(stand).isAir()||!l.getBlockState(stand.above()).isAir()||l.getBlockState(stand.below()).is(BlockTags.LOGS)||!l.getFluidState(stand.below()).isEmpty())continue;
                if(!l.getBlockState(stand.below()).getCollisionShape(l,stand.below()).isEmpty())return stand;
            }        }return null;
    }
    static Cell inspect(ServerLevel l,int cx,int cz){
        BlockPos base=new BlockPos(cx*16,0,cz*16);if(!l.hasChunkAt(base))return null;
        var cell=new Cell();cell.dimension=l.dimension().location().toString();cell.x=cx;cell.z=cz;cell.checked=l.getGameTime();
        for(int x=2;x<16;x+=4)for(int z=2;z<16;z+=4){
            int top=l.getHeight(Heightmap.Types.WORLD_SURFACE,cx*16+x,cz*16+z)-1;var p=new BlockPos(cx*16+x,top,cz*16+z);
            if(l.getBlockState(p).is(BlockTags.LEAVES))cell.canopy++;
            // Nearby trunk samples distinguish woodland from a roof of decorative leaves.
            for(int dy=0;dy<16&&top-dy>=l.getMinBuildHeight();dy++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(l.getBlockState(p.offset(dx,-dy,dz)).is(BlockTags.LOGS)){cell.logs++;break;}
            var stand=findStand(l,p);if(stand!=null)cell.stand=stand.asLong();
        }
        cell.usable=dense(cell.canopy,cell.logs)&&cell.stand!=0;return cell;
    }
    static RuralSettlement.Site site(RuralSettlement d,UUID person){return d.state.sites.values().stream().filter(s->s.present&&person.equals(s.worker)&&s.kind.equals("hunter")).findFirst().orElse(null);}
    static boolean separated(BlockPos p,Collection<BlockPos> others,double gap){return others.stream().allMatch(q->horizontal(p,q)>=gap*gap);}
    static List<BlockPos> reservations(MinecraftServer server,RuralSettlement d,RuralRules.Person self,String dim){
        var list=new ArrayList<BlockPos>();for(var other:d.state.people.values())if(other!=self&&!other.dead&&other.role==RuralRules.Role.HUNTER&&other.huntingSpot!=null){var family=d.state.families.get(other.family);var city=family==null?null:CityData.get(server).cities.get(family.city);if(city!=null&&city.dimension.equals(dim))list.add(BlockPos.of(other.huntingSpot));}return list;
    }
    static boolean allocate(MinecraftServer s,RuralSettlement d,CityData.City city,RuralRules.Person p){
        var site=site(d,p.id);if(site==null){p.huntingSpot=null;p.workStatus="缺少猎户工作台";return false;}var origin=BlockPos.of(site.pos);var others=reservations(s,d,p,city.dimension);
        if(p.huntingSpot!=null){var pos=BlockPos.of(p.huntingSpot);var cell=d.state.forest.get(key(city.dimension,pos.getX()>>4,pos.getZ()>>4));if(cell!=null&&cell.usable&&horizontal(pos,origin)<=p.huntRadius*p.huntRadius&&separated(pos,others,80))return true;p.huntingSpot=null;}
        var candidate=d.state.forest.values().stream().filter(c->c.usable&&city.dimension.equals(c.dimension)&&horizontal(origin,BlockPos.of(c.stand))<=p.huntRadius*p.huntRadius&&separated(BlockPos.of(c.stand),others,80)).min(Comparator.comparingDouble(c->horizontal(origin,BlockPos.of(c.stand))));
        if(candidate.isPresent()){p.huntingSpot=candidate.get().stand;return true;}p.workStatus="等待：范围内林地不足或与其他猎户间距不足64格";return false;
    }
    // CustomNPCs' own return goal otherwise competes with longer woodland patrols.
    static void reconcileHome(Mob mob,boolean controlled){
        String key="city_hunter_saved_return";var tag=mob.getPersistentData();
        try{var ai=mob.getClass().getField("ais").get(mob);var field=ai.getClass().getField("returnToStart");
            if(controlled){if(!tag.contains(key))tag.putBoolean(key,field.getBoolean(ai));field.setBoolean(ai,false);}
            else if(tag.contains(key)){field.setBoolean(ai,tag.getBoolean(key));tag.remove(key);}
        }catch(ReflectiveOperationException ignored){} // Other entity providers do not have this goal.
    }
    public static void tick(MinecraftServer server){
        if(server.getTickCount()%20!=0)return;var d=RuralSettlement.get(server);
        for(var person:d.state.people.values()){var family=d.state.families.get(person.family);var city=family==null?null:CityData.get(server).cities.get(family.city);if(city==null)continue;var entity=CityEconomy.level(server,city).getEntity(person.id);if(entity instanceof Mob mob)reconcileHome(mob,!person.dead&&person.role==RuralRules.Role.HUNTER&&d.state.enabled.contains(city.id)&&site(d,person.id)!=null);}
        var hunters=d.state.people.values().stream().filter(p->!p.dead&&p.role==RuralRules.Role.HUNTER&&d.state.enabled.contains(d.state.families.get(p.family).city)).toList();hunters=hunters.stream().filter(p->!CityData.get(server).require(d.state.families.get(p.family).city).works.virtual).toList();if(hunters.isEmpty())return;
        // At most two cells per second for the whole server, not per hunter.
        for(int k=0;k<2;k++){
            if(!dirty.isEmpty()){String key=dirty.iterator().next();dirty.remove(key);var previous=d.state.forest.get(key);if(previous!=null){var level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(previous.dimension)));if(level!=null){var next=inspect(level,previous.x,previous.z);if(next!=null)d.state.forest.put(key,next);}}continue;}
            var hunter=hunters.get(Math.floorMod(cursor++,hunters.size()));var site=site(d,hunter.id);if(site==null)continue;var city=CityData.get(server).cities.get(site.city);if(city==null)continue;var level=CityEconomy.level(server,city);var origin=BlockPos.of(site.pos);int radius=(hunter.huntRadius+15)/16,width=radius*2+1,index=Math.floorMod(scans.merge(hunter.id,1,Integer::sum)-1,width*width);int x=(origin.getX()>>4)+index%width-radius,z=(origin.getZ()>>4)+index/width-radius;String key=key(city.dimension,x,z);var cell=d.state.forest.get(key);if(cell!=null&&level.getGameTime()-cell.checked<6000)continue;if(d.state.forest.size()>=16384&&!d.state.forest.containsKey(key))d.state.forest.remove(d.state.forest.keySet().iterator().next());var found=inspect(level,x,z);if(found!=null){d.state.forest.put(key,found);d.setDirty();}}
        for(var hunter:hunters){var home=d.state.families.get(hunter.family);var city=CityData.get(server).cities.get(home.city);if(city==null)continue;var level=CityEconomy.level(server,city);if(!(level.getEntity(hunter.id) instanceof Mob mob)||!mob.isAlive()||mob.getTarget()!=null||mob.isPassenger())continue;
            boolean working=WorkHours.active(server,city,hunter.id);var site=site(d,hunter.id);if(site==null)continue;
            if(!working){if(server.getTickCount()%200==Math.floorMod(hunter.id.hashCode(),10)*20&&level.hasChunkAt(BlockPos.of(site.pos)))mob.getNavigation().moveTo(BlockPos.of(site.pos).getX()+.5,BlockPos.of(site.pos).getY()+1,BlockPos.of(site.pos).getZ()+.5,.65);continue;}
            if(hunter.huntingSpot==null){mob.getNavigation().stop();continue;}
            var otherPositions=new ArrayList<BlockPos>();for(var other:hunters)if(other!=hunter&&other.huntingSpot!=null&&level.getEntity(other.id) instanceof Mob otherMob)otherPositions.add(otherMob.blockPosition());
            // Do not push/teleport actors if player actions put them inside the clearance.
            var velocity=mob.getDeltaMovement();var next=BlockPos.containing(mob.getX()+velocity.x*4,mob.getY(),mob.getZ()+velocity.z*4);
            boolean approaching=otherPositions.stream().anyMatch(q->horizontal(next,q)<4096&&horizontal(next,q)<horizontal(mob.blockPosition(),q));if(approaching){mob.getNavigation().stop();mob.setDeltaMovement(0,velocity.y,0);continue;}
            if(server.getTickCount()%200!=Math.floorMod(hunter.id.hashCode(),10)*20)continue;
            var anchor=BlockPos.of(hunter.huntingSpot);int direction=(int)(level.getGameTime()/200+Math.floorMod(hunter.id.hashCode(),4))%4;var target=anchor.offset(direction==0?5:direction==2?-5:0,0,direction==1?5:direction==3?-5:0);
            if(!level.hasChunkAt(target)||!separated(target,reservations(server,d,hunter,city.dimension),72))continue;
            target=new BlockPos(target.getX(),level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,target.getX(),target.getZ()),target.getZ());if(Math.abs(target.getY()-anchor.getY())>3||!level.getBlockState(target).isAir()||!level.getBlockState(target.above()).isAir())continue;
            mob.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,.65);
        }
    }
}

