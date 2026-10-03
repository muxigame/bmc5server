package audit;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import noppes.npcs.CustomEntities;
import noppes.npcs.CustomNpcs;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobFollower;

/** Destructive to its test terrain: isolated disposable instance ONLY. Uses unmodified NPC AI. */
@Mod("follower_audit")
public class FollowerAudit {
    MinecraftServer server;
    ServerLevel level;
    int tick;
    boolean done;
    final List<Case> cases=new ArrayList<>();
    final List<Map<String,Object>> results=new ArrayList<>();
    final List<String> trajectory=new ArrayList<>(List.of("tick,case,x,y,z,targetX,targetZ,targetFound,ownerPresent,noAI,pathDone"));
    static class Case {
        String name;
        EntityNPCInterface leader, follower;
        Vec3 initial, previous;
        double maxStep;
        int firstMove=-1;
        Case(String name,EntityNPCInterface leader,EntityNPCInterface follower) {
            this.name=name; this.leader=leader; this.follower=follower;
            initial=previous=follower.position();
        }
    }
    public FollowerAudit() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::post);
    }
    void started(ServerStartedEvent event) { server=event.getServer(); level=server.overworld(); }
    EntityNPCInterface spawn(String name,double x,double z,boolean noAI) {
        var npc=CustomEntities.entityCustomNpc.create(level);
        npc.display.setName(name); npc.setPos(x,64,z); npc.setNoAi(noAI);
        npc.setInvulnerable(true); npc.setPersistenceRequired();
        npc.ais.setMovingType(0); npc.ais.movementType=0; npc.ais.animationType=0;
        npc.ais.setWalkingSpeed(5); npc.advanced.setRole(0);
        npc.updateAI=true; level.addFreshEntity(npc); return npc;
    }
    void setup() {
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
        CustomNpcs.FreezeNPCs=false;
        String[] names={"normal_standing","role_conflict","no_ai","sitting","wrong_name","zero_speed"};
        for(int i=0;i<names.length;i++) {
            int x=8+i*48;
            for(int cx=(x-4)>>4;cx<=(x+24)>>4;cx++)for(int cz=0;cz<=1;cz++)level.setChunkForced(cx,cz,true);
            for(int bx=x-4;bx<=x+24;bx++)for(int bz=4;bz<=12;bz++) {
                level.setBlockAndUpdate(new BlockPos(bx,63,bz),Blocks.STONE.defaultBlockState());
                for(int y=64;y<=67;y++)level.setBlockAndUpdate(new BlockPos(bx,y,bz),Blocks.AIR.defaultBlockState());
            }
            var leader=spawn("AuditLeader_"+i,x+10.5,8.5,true);
            var follower=spawn("AuditFollower_"+i,x+0.5,8.5,i==2);
            follower.advanced.setJob(5); ((JobFollower)follower.job).name=leader.display.getName();
            if(i==1)follower.advanced.setRole(2);
            if(i==3)follower.ais.animationType=1;
            if(i==4)((JobFollower)follower.job).name="MissingLeader";
            if(i==5)follower.ais.setWalkingSpeed(0);
            cases.add(new Case(names[i],leader,follower));
        }
    }
    void measure() {
        for(var c:cases) {
            Vec3 p=c.follower.position();
            double step=p.distanceTo(c.previous); c.maxStep=Math.max(c.maxStep,step);
            if(c.firstMove<0&&p.distanceTo(c.initial)>0.2)c.firstMove=tick;
            c.previous=p;
            if(tick%10==0)trajectory.add(String.format(Locale.ROOT,"%d,%s,%.5f,%.5f,%.5f,%.5f,%.5f,%s,%s,%s,%s",tick,c.name,p.x,p.y,p.z,c.leader.getX(),c.leader.getZ(),((JobFollower)c.follower.job).following==c.leader,c.follower.getOwner()!=null,c.follower.isNoAi(),c.follower.getNavigation().isDone()));
        }
    }
    void record(String phase) throws Exception {
        for(var c:cases) {
            double travelled=c.follower.position().distanceTo(c.initial);
            double distance=c.follower.distanceTo(c.leader);
            boolean expectedMoves=phase.equals("recovered")||c.name.equals("normal_standing");
            boolean ok=expectedMoves?travelled>3&&distance<4.5&&c.maxStep<1:travelled<0.5;
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("case",c.name); row.put("phase",phase); row.put("status",ok?"PASS":"FAIL");
            row.put("expectedWalk",expectedMoves); row.put("travelled",travelled); row.put("distanceToLeader",distance);
            row.put("maximumStepPerTick",c.maxStep); row.put("firstMovementTick",c.firstMove);
            row.put("npcAgeTicks",c.follower.tickCount); row.put("jobFoundTarget",((JobFollower)c.follower.job).following==c.leader);
            row.put("resolvedOwner",c.follower.getOwner()==null?"none":c.follower.getOwner().getName().getString());
            row.put("noAI",c.follower.isNoAi()); row.put("walkingSpeed",c.follower.ais.getWalkingSpeed());
            row.put("pathDone",c.follower.getNavigation().isDone()); row.put("position",c.follower.position().toString());
            results.add(row); System.out.println("FOLLOWER_AUDIT "+new GsonBuilder().create().toJson(row));
        }
        Files.writeString(Path.of("follower-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));
        Files.write(Path.of("follower-trajectory.csv"),trajectory);
    }
    void recover() {
        for(var c:cases) {
            c.follower.setNoAi(false); c.follower.advanced.setRole(0); c.follower.ais.animationType=0;
            c.follower.ais.setWalkingSpeed(5); ((JobFollower)c.follower.job).name=c.leader.display.getName();
            c.follower.updateAI=true;
            // Only move the leader; never move the follower under test.
            c.leader.setPos(c.leader.getX()+6,64,c.leader.getZ());
            c.initial=c.previous=c.follower.position(); c.maxStep=0; c.firstMove=-1;
        }
    }
    void post(ServerTickEvent.Post event) {
        if(server==null||done)return;
        try {
            tick++;
            if(tick==20)setup();
            if(tick>20)measure();
            if(tick==280) { record("initial"); recover(); }
            if(tick==560) { record("recovered"); done=true; server.halt(false); }
        } catch(Throwable error) {
            error.printStackTrace(); done=true;
            try { Files.writeString(Path.of("follower-error.txt"),error.toString()); } catch(Exception ignored) {}
            server.halt(false);
        }
    }
}
