package com.muxigame.sevenfold;
import net.minecraft.nbt.CompoundTag;
/** Server tick clock: no wall-clock or offline catch-up. */
public final class ResourceClock {
    private long combatUntil,hurtAt=Long.MIN_VALUE/2,contactAt=Long.MIN_VALUE/2,actionAt=Long.MIN_VALUE/2,focusAt;
    private int resilienceTicks,staminaTicks,mightTicks,focusTicks;
    public ResourceClock(long now){focusAt=now;}
    public void combat(long now){combatUntil=now+200;}
    public void action(long now){actionAt=now;}
    public void hurt(long now){combat(now);hurtAt=contactAt=now;resilienceTicks=staminaTicks=0;}
    public void hit(long now){combat(now);contactAt=now;}
    public void focus(long now){focusAt=now;focusTicks=0;}
    public CompoundTag save(long now){
        var t=new CompoundTag();t.putInt("combat",(int)Math.clamp(combatUntil-now,0,200));
        t.putInt("hurtAge",(int)Math.clamp(now-hurtAt,0,101));t.putInt("contactAge",(int)Math.clamp(now-contactAt,0,61));
        t.putInt("actionAge",(int)Math.clamp(now-actionAt,0,21));t.putInt("focusAge",(int)Math.clamp(now-focusAt,0,201));
        t.putInt("r",resilienceTicks);t.putInt("s",staminaTicks);t.putInt("m",mightTicks);t.putInt("f",focusTicks);return t;
    }
    public static ResourceClock load(CompoundTag t,long now){
        var c=new ResourceClock(now);if(t.isEmpty())return c;
        c.combatUntil=now+Math.clamp(t.getInt("combat"),0,200);c.hurtAt=now-Math.clamp(t.getInt("hurtAge"),0,101);
        c.contactAt=now-Math.clamp(t.getInt("contactAge"),0,61);c.actionAt=now-Math.clamp(t.getInt("actionAge"),0,21);c.focusAt=now-Math.clamp(t.getInt("focusAge"),0,201);
        c.resilienceTicks=Math.clamp(t.getInt("r"),0,9);c.staminaTicks=Math.clamp(t.getInt("s"),0,19);c.mightTicks=Math.clamp(t.getInt("m"),0,19);c.focusTicks=Math.clamp(t.getInt("f"),0,9);return c;
    }
    public CombatPools tick(CombatPools p,long now,boolean busy,boolean stunned){
        if(busy)action(now);
        int r=p.resilience(),s=p.stamina(),m=p.might(),f=p.focus();boolean combat=now<combatUntil;
        if(now-hurtAt>60){if(++resilienceTicks>=(combat?10:5)){r+=p.scale();resilienceTicks=0;}}else resilienceTicks=0;
        if(now-hurtAt>100){if(++staminaTicks>=(now-actionAt>=20?10:20)){s+=p.scale();staminaTicks=0;}}else staminaTicks=0;
        if(++mightTicks>=(now-contactAt<60?10:combat?20:10)){m+=p.scale();mightTicks=0;}
        if(stunned){f=0;focusTicks=0;}
        else if(now-focusAt>200){if(++focusTicks>=10){f--;focusTicks=0;}}
        else focusTicks=0;
        return new CombatPools(r,s,m,f,p.mana(),p.scale());
    }
}
