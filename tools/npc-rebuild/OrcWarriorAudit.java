package audit;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import noppes.npcs.CustomEntities;
import noppes.npcs.CustomNpcs;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.ai.selector.NPCAttackSelector;
import noppes.npcs.client.controllers.ClientCloneController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.server.SPacketToolMobSpawner;
import noppes.npcs.util.NBTJsonUtil;

/** Generates authored content through the ORIGINAL mod API. Isolated test instance ONLY. */
@Mod("orc_warrior_audit")
public class OrcWarriorAudit {
    static final String NAME="兽人战士_灰牙氏族_v1";
    static final String SKIN="customnpcs:textures/entity/orcmale/leatherarmoredorc.png";
    // Native line groups: interact, attack, idle, death, kill, NPC interaction.
    static final String[][] LINES={
        {"站住！前面是灰牙的营地。", "收起你的花招。我的斧头可不认人。", "想过这条路？先问问我的斧头！", "我们不欢迎闯入者。离开这里！"},
        {"为了灰牙！", "你的脚步太响了，入侵者！", "别躲！接我这一斧！", "这里就是你的终点！"},
        {"斧刃还利，哨火还亮。", "风里有陌生人的气味……", "营地在身后。我不会退。", "巡完这条路，就该换岗了。"},
        {"我的斧头……还没钝……", "灰牙……守住营地……", "这一战……你赢了……", "山风……带我回家……"},
        {"我警告过你。", "灰牙的路，灰牙来守。", "把斧头擦净。还有下一班岗。", "营地安全了。"},
        {"看紧林子，别让人绕到后面。", "听见了吗？那边有脚步声。", "守住路口。我去看看。", "等换岗了，给我留一口炖汤。"}
    };
    MinecraftServer server;
    ServerLevel level;
    int tick;
    boolean done;
    EntityNPCInterface orc, duplicate;
    Cow target, frozenControl;
    Vec3 initial, previous;
    double maxStep;
    float previousHealth;
    final List<Integer> hitTicks=new ArrayList<>();
    final List<Float> hitDamage=new ArrayList<>();
    final List<Map<String,Object>> checks=new ArrayList<>();
    final List<Map<String,Object>> trace=new ArrayList<>();

    public OrcWarriorAudit() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::post);
    }
    void started(ServerStartedEvent e) {server=e.getServer();level=server.overworld();}
    void check(String name,boolean ok,Object detail) {
        checks.add(Map.of("check",name,"status",ok?"PASS":"FAIL","detail",detail));
        System.out.println("ORC_AUDIT "+name+" "+(ok?"PASS":"FAIL")+" "+detail);
    }
    void configure(EntityNPCInterface n) {
        n.display.setName("兽人战士");n.display.setTitle("灰牙氏族");
        n.display.setSkinTexture(SKIN);n.display.setSize(5);
        n.setNoAi(false);n.setInvulnerable(false);n.setPersistenceRequired();
        n.stats.setMaxHealth(24);n.setHealth(24);n.stats.aggroRange=16;
        n.stats.healthRegen=0;n.stats.combatRegen=0;n.stats.spawnCycle=3;
        n.stats.melee.setStrength(5);n.stats.melee.setDelay(30);n.stats.melee.setRange(2);
        n.ais.setMovingType(1);n.ais.walkingRange=4;n.ais.setWalkingSpeed(5);
        n.ais.canSprint=false;n.ais.canLeap=false;n.ais.returnToStart=true;
        n.advanced.setRole(0);n.advanced.setJob(0);n.setFaction(2);
        n.advanced.setSound(0,"");
        n.advanced.setSound(1,"customnpcs:human.male.evil.attack");
        n.advanced.setSound(2,"customnpcs:human.male.evil.hurt");
        n.advanced.setSound(3,"customnpcs:human.male.evil.death");
        for(int group=0;group<LINES.length;group++)for(int slot=0;slot<LINES[group].length;slot++)
            n.advanced.setLine(group,slot,LINES[group][slot],"");
        n.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_AXE));
        // Armor is painted into the bundled skin; no hidden damage resistance or armor equipment.
        n.inventory.setExp(5,5);
        n.inventory.setDropItem(0,NpcAPI.Instance().getIItemStack(new ItemStack(Items.LEATHER)),35);
        n.inventory.setDropItem(1,NpcAPI.Instance().getIItemStack(new ItemStack(Items.STONE_AXE)),8);
        n.updateAI=true;
    }
    void setup() throws Exception {
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
        CustomNpcs.FreezeNPCs=false;
        for(int cx=0;cx<3;cx++)for(int cz=0;cz<2;cz++)level.setChunkForced(cx,cz,true);
        for(int x=0;x<40;x++)for(int z=0;z<24;z++) {
            level.setBlockAndUpdate(new BlockPos(x,63,z),Blocks.STONE.defaultBlockState());
            for(int y=64;y<69;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        var template=CustomEntities.entityCustomNpc.create(level);configure(template);
        CompoundTag data=new CompoundTag();template.saveAsPassenger(data);
        // Exercise the exact client library controller in a disposable export directory.
        File prior=CustomNpcs.Dir;
        CompoundTag loaded;
        try {
            CustomNpcs.Dir=Path.of("orc-export/customnpcs").toAbsolutePath().toFile();
            Files.createDirectories(CustomNpcs.Dir.toPath());
            var library=new ClientCloneController();library.addClone(data,NAME,1);
            check("client_library_listing",library.getClones(1).contains(NAME),NAME);
            loaded=library.getCloneData(null,NAME,1);
            check("client_library_parse",loaded!=null&&!loaded.isEmpty(),"original ClientCloneController / NBTJsonUtil");
        } finally {CustomNpcs.Dir=prior;}
        check("portable_identity",!loaded.contains("UUID")&&!loaded.contains("Pos")&&!loaded.contains("StartPosNew"),"no saved identity or location");
        check("typed_nbt_roundtrip",loaded.equals(NBTJsonUtil.Convert(NBTJsonUtil.Convert(loaded))),"all NBT types and Chinese text");
        orc=(EntityNPCInterface)SPacketToolMobSpawner.spawnClone(loaded.copy(),8.5,64,8.5,level);
        duplicate=(EntityNPCInterface)SPacketToolMobSpawner.spawnClone(loaded.copy(),30.5,64,8.5,level);
        check("cloner_spawn_twice",orc!=null&&duplicate!=null&&!orc.getUUID().equals(duplicate.getUUID()),"two independent UUIDs");
        check("appearance_and_equipment",orc.display.getSkinTexture().equals(SKIN)&&orc.display.getSize()==5&&orc.getMainHandItem().is(Items.STONE_AXE),SKIN);
        check("combat_settings",orc.getMaxHealth()==24&&orc.getHealth()==24&&orc.stats.melee.getStrength()==5&&orc.stats.melee.getDelay()==30&&!orc.isNoAi(),"24 health / 5 damage / 30 ticks / AI enabled");
        boolean lines=true;
        for(int group=0;group<LINES.length;group++)for(int slot=0;slot<LINES[group].length;slot++)
            lines&=orc.advanced.getLine(group,slot).equals(LINES[group][slot]);
        check("all_dialogue_loaded",lines,"24 original Chinese lines in 6 native groups");
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("84893663-c246-42fd-9531-aaa01e997226"),"OrcAudit"));
        player.setGameMode(GameType.SURVIVAL);
        check("survival_player_hostility",orc.getFaction().isAggressiveToPlayer(player),"fresh world faction 2");
        player.setGameMode(GameType.CREATIVE);
        check("creative_player_exemption",!orc.getFaction().isAggressiveToPlayer(player),"native creative-mode behavior");
        player.setGameMode(GameType.SURVIVAL);player.setPos(14.5,64,8.5);orc.faction=orc.getFaction();
        check("native_player_target_selector",new NPCAttackSelector(orc).apply(player),"visible survival player within aggro range");
        target=EntityType.COW.create(level);target.setPersistenceRequired();
        // Keep the living entity tick active so its post-hit immunity timer expires.
        target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);
        target.setPos(16.5,64,8.5);level.addFreshEntity(target);
        frozenControl=EntityType.COW.create(level);frozenControl.setNoAi(true);
        frozenControl.setPos(34.5,64,16.5);level.addFreshEntity(frozenControl);
        initial=previous=orc.position();previousHealth=target.getHealth();
        duplicate.hurt(level.damageSources().generic(),1000);
        Files.writeString(Path.of("orc-dialogue.json"),new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(LINES));
    }
    void finish() throws Exception {
        check("walks_to_target",orc.position().distanceTo(initial)>3&&maxStep<1,
            Map.of("distanceTravelled",orc.position().distanceTo(initial),"maximumStep",maxStep));
        check("actual_melee_damage",hitDamage.size()>=3&&hitDamage.stream().allMatch(d->Math.abs(d-5)<0.01),hitDamage);
        boolean delay=hitTicks.size()>=3;
        for(int i=1;i<hitTicks.size();i++)delay&=hitTicks.get(i)-hitTicks.get(i-1)>=30;
        check("melee_cooldown",delay,hitTicks);
        check("death_removes_without_respawn",duplicate.isRemoved(),"dead clone removed; SpawnCycle=3");
        check("drops_and_xp",orc.inventory.getExpMin()==5&&orc.inventory.getExpMax()==5&&orc.inventory.dropchance.get(0)==35F&&orc.inventory.dropchance.get(1)==8F,"5 XP; leather 35%; stone axe 8%");
        Files.writeString(Path.of("orc-dummy-control.json"),new GsonBuilder().setPrettyPrinting().create().toJson(
            Map.of("noAIControlInvulnerabilityTicks",frozenControl.invulnerableTime,"noAIControlAgeTicks",frozenControl.tickCount,
                "activeTargetInvulnerabilityTicks",target.invulnerableTime,"activeTargetAgeTicks",target.tickCount)));
        Files.writeString(Path.of("orc-results.json"),new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(checks));
        Files.writeString(Path.of("orc-trajectory.json"),new GsonBuilder().setPrettyPrinting().create().toJson(trace));
        done=true;server.halt(false);
    }
    void post(ServerTickEvent.Post e) {
        if(server==null||done)return;
        try {
            tick++;if(tick==20)setup();
            // Let initial NPC AI setup complete, then trigger native retaliation exactly once.
            if(tick==120) {
                orc.hurt(level.damageSources().mobAttack(target),1);
                frozenControl.hurt(level.damageSources().generic(),1);
            }
            if(tick>20&&tick<=400) {
                maxStep=Math.max(maxStep,orc.position().distanceTo(previous));previous=orc.position();
                float health=target.getHealth();
                if(health<previousHealth) {hitTicks.add(tick);hitDamage.add(previousHealth-health);}
                previousHealth=health;
                if(tick%10==0)trace.add(Map.of("tick",tick,"position",orc.position().toString(),"targetPosition",target.position().toString(),
                    "health",health,"targetAlive",target.isAlive(),"targetRemoved",target.isRemoved(),"hasTarget",orc.getTarget()==target,"updateAI",orc.updateAI,
                    "targetImmunityTicks",target.invulnerableTime,"npcAgeTicks",orc.tickCount));
            }
            if(tick==400)finish();
        } catch(Throwable error) {
            error.printStackTrace();done=true;
            try {Files.writeString(Path.of("orc-error.txt"),error.toString());}catch(Exception ignored){}
            server.halt(false);
        }
    }
}
