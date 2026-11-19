package com.muxigame.sevenfold;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.*;
import net.minecraft.world.level.GameRules;
import com.muxigame.sevenfold.mixin.PlayerFoodAccess;

/** Food and mana remain separate. Same timers and thresholds expressed on the new scale. */
public final class RpgFoodData extends FoodData {
    private final Player owner;private int food=20,last=20,timer,storedScale=1;private float saturation=5,exhaustion;
    public RpgFoodData(Player owner){this.owner=owner;}
    public int scale(){return StarTreeState.of(owner).active()?5:1;}
    public int maximum(){return 20*scale();}
    public static RpgFoodData install(Player p){
        if(p.getFoodData() instanceof RpgFoodData r&&r.owner==p)return r;
        var data=new CompoundTag();p.getFoodData().addAdditionalSaveData(data);var next=new RpgFoodData(p);next.readAdditionalSaveData(data);
        ((PlayerFoodAccess)p).sevenfold$food(next);return next;
    }
    public void changeScale(){int next=scale();if(storedScale!=next){food=Math.clamp((int)Math.ceil(food*(double)next/storedScale),0,20*next);saturation=Math.min(food,saturation*next/storedScale);last=food;storedScale=next;}}
    private void add(int nutrition,float sat){food=Math.clamp(food+nutrition,0,maximum());saturation=Math.clamp(saturation+sat,0,food);}
    @Override public void eat(int n,float modifier){add(n*scale(),n*modifier*2*scale());}
    @Override public void eat(FoodProperties f){add(f.nutrition()*scale(),f.saturation()*scale());}
    @Override public void tick(Player p){
        changeScale();int s=scale();last=food;Difficulty d=p.level().getDifficulty();
        if(exhaustion>4){exhaustion-=4;int loss=s==5?4:1;if(saturation>0)saturation=Math.max(0,saturation-loss);else if(d!=Difficulty.PEACEFUL)food=Math.max(0,food-loss);}
        boolean regen=p.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        if(regen&&saturation>0&&p.isHurt()&&food>=20*s){if(++timer>=10){float f=Math.min(saturation/s,6);RpgNumbers.scaledHeal(p,f/6*s);addExhaustion(f);timer=0;}}
        else if(regen&&food>=18*s&&p.isHurt()){if(++timer>=80){RpgNumbers.scaledHeal(p,s);addExhaustion(6);timer=0;}}
        else if(food<=0){if(++timer>=80){if(p.getHealth()>10*s||d==Difficulty.HARD||p.getHealth()>s&&d==Difficulty.NORMAL)RpgNumbers.scaledDamage(p,p.damageSources().starve(),s);timer=0;}}
        else timer=0;
    }
    @Override public void readAdditionalSaveData(CompoundTag t){if(t.contains("foodLevel",99)){food=Math.clamp(t.getInt("foodLevel"),0,100);last=food;timer=Math.clamp(t.getInt("foodTickTimer"),0,79);saturation=Math.clamp(t.getFloat("foodSaturationLevel"),0,food);exhaustion=Math.clamp(t.getFloat("foodExhaustionLevel"),0,40);storedScale=t.getInt("SevenfoldFoodScale")==5?5:1;}}
    @Override public void addAdditionalSaveData(CompoundTag t){t.putInt("foodLevel",food);t.putInt("foodTickTimer",timer);t.putFloat("foodSaturationLevel",saturation);t.putFloat("foodExhaustionLevel",exhaustion);t.putInt("SevenfoldFoodScale",storedScale);}
    @Override public int getFoodLevel(){return food;}@Override public int getLastFoodLevel(){return last;}
    @Override public boolean needsFood(){return food<maximum();}
    @Override public void addExhaustion(float f){exhaustion=Math.clamp(exhaustion+f,0,40);}
    @Override public float getExhaustionLevel(){return exhaustion;}@Override public float getSaturationLevel(){return saturation;}
    @Override public void setFoodLevel(int f){food=Math.clamp(f,0,owner.level().isClientSide?100:maximum());}
    @Override public void setSaturation(float f){saturation=Math.clamp(f,0,food);}@Override public void setExhaustion(float f){exhaustion=Math.clamp(f,0,40);}
}
