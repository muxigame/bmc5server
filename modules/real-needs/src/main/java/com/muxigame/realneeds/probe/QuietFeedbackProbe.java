package com.muxigame.realneeds.probe;

import com.muxigame.realneeds.NeedsClient;
import com.muxigame.realneeds.NeedsNetwork;
import com.muxigame.realneeds.RealNeeds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in native-client regression on an untracked surrogate, never the real player. */
@EventBusSubscriber(modid=RealNeeds.ID,value=Dist.CLIENT)
public final class QuietFeedbackProbe {
    private static boolean done;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        String target=System.getProperty("realneeds.quietFeedbackProbe","");var mc=Minecraft.getInstance();
        if(done||target.isEmpty()||mc.player==null||mc.level==null||mc.getConnection()==null)return;
        done=true;var result=new com.google.gson.JsonObject();var checks=new com.google.gson.JsonArray();
        var camera=mc.getCameraEntity();float realHealth=mc.player.getHealth();
        try{
            var p=new LocalPlayer(mc,mc.level,mc.getConnection(),new StatsCounter(),new ClientRecipeBook(),false,false);
            p.setId(-190052);p.hurtTo(20);p.hurtTime=0;p.hurtDuration=10;p.walkAnimation.setSpeed(.2F);
            var packet=new NeedsNetwork.QuietHealth(p.getId(),18);
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),mc.level.registryAccess());
            NeedsNetwork.QuietHealth.CODEC.encode(buffer,packet);var decoded=NeedsNetwork.QuietHealth.CODEC.decode(buffer);buffer.release();
            check(decoded.equals(packet),"Quiet-health wire codec round trip");checks.add("wire_codec");
            NeedsClient.applyQuietHealth(p,decoded);p.hurtTo(18);
            check(p.getHealth()==18&&p.hurtTime==0&&p.hurtDuration==10,"Quiet update plus vanilla duplicate must not animate");
            check(Math.abs(p.walkAnimation.speed()-.2F)<1e-6,"Quiet update preserves walking animation");checks.add("quiet_then_vanilla_health_no_animation");
            var bob=net.minecraft.client.renderer.GameRenderer.class.getDeclaredMethod("bobHurt",PoseStack.class,float.class);bob.setAccessible(true);
            mc.setCameraEntity(p);var quietPose=new PoseStack();bob.invoke(mc.gameRenderer,quietPose,.5F);
            check(quietPose.last().pose().equals(new org.joml.Matrix4f()),"Quiet damage must produce identity hurt-camera transform");checks.add("quiet_camera_identity");
            var attacker=new Zombie(EntityType.ZOMBIE,mc.level);var attack=p.damageSources().mobAttack(attacker);
            p.handleDamageEvent(attack);check(p.hurtTime==10,"Real attack damage event animates");
            var attackPose=new PoseStack();bob.invoke(mc.gameRenderer,attackPose,.5F);
            if(mc.options.damageTiltStrength().get()>0)check(!attackPose.last().pose().equals(new org.joml.Matrix4f()),"Real attack retains camera tilt");checks.add("real_attack_animation_and_camera");
            NeedsClient.applyQuietHealth(p,new NeedsNetwork.QuietHealth(p.getId(),17));p.hurtTo(17);
            check(p.hurtTime==10&&p.getHealth()==17,"Quiet damage in same tick must not erase real attack feedback");checks.add("real_attack_then_quiet_preserves_feedback");
            p.hurtTime=0;NeedsClient.applyQuietHealth(p,new NeedsNetwork.QuietHealth(p.getId(),16));p.handleDamageEvent(attack);p.hurtTo(14);
            check(p.hurtTime==10&&p.getHealth()==14,"Real attack after quiet damage still animates");checks.add("quiet_then_real_attack_preserves_feedback");
            NeedsClient.applyQuietHealth(p,new NeedsNetwork.QuietHealth(p.getId()+1,1));
            check(p.getHealth()==14,"Old entity health packet ignored after respawn");checks.add("stale_entity_ignored");
            NeedsClient.applyQuietHealth(p,new NeedsNetwork.QuietHealth(p.getId(),Float.NaN));
            check(p.getHealth()==14,"Non-finite health ignored");checks.add("invalid_health_ignored");
            check(mc.player.getHealth()==realHealth,"Real player's health never changed");checks.add("real_player_untouched");
            result.addProperty("passed",true);
        }catch(Throwable error){result.addProperty("passed",false);result.addProperty("error",error.toString());}
        finally{mc.setCameraEntity(camera);}
        result.add("checks",checks);result.addProperty("real_health_unchanged",mc.player.getHealth()==realHealth);
        result.addProperty("camera_restored",mc.getCameraEntity()==camera);result.addProperty("version","0.5.2");
        try{var file=java.nio.file.Path.of(target);java.nio.file.Files.createDirectories(file.getParent());java.nio.file.Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}
        catch(java.io.IOException error){throw new IllegalStateException(error);}
    }
}
