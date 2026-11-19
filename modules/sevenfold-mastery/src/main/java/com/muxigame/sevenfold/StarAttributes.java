package com.muxigame.sevenfold;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
public final class StarAttributes {
    private static final ResourceLocation HEALTH=ResourceLocation.fromNamespaceAndPath(SevenfoldMastery.ID,"star_health");
    private static final ResourceLocation ATTACK=ResourceLocation.fromNamespaceAndPath(SevenfoldMastery.ID,"star_attack");
    public static void apply(Player p){
        float absorption=p.getAbsorptionAmount(),absorptionCap=p.getMaxAbsorption();
        var s=StarTreeState.of(p);float before=p.getMaxHealth(),fraction=before>0?p.getHealth()/before:1;set(p.getAttribute(Attributes.MAX_HEALTH),HEALTH,s.healthBonus());
        set(p.getAttribute(Attributes.ATTACK_DAMAGE),ATTACK,s.attackBonus());
        RpgNumbers.effects(p,false);
        if(absorptionCap>0&&absorptionCap!=p.getMaxAbsorption())p.setAbsorptionAmount(Math.min(p.getMaxAbsorption(),absorption/absorptionCap*p.getMaxAbsorption()));
        if(before!=p.getMaxHealth())p.setHealth(Math.min(p.getMaxHealth(),p.getMaxHealth()*fraction));
        CombatPools.of(p).scaleTo(s.active()?5:1).store(p);
        RpgFoodData.install(p).changeScale();
    }
    private static void set(AttributeInstance a,ResourceLocation id,double amount){
        if(a==null)return;
        var old=a.getModifier(id);if(old!=null&&old.amount()==amount)return;
        a.removeModifier(id);
        if(amount>0)a.addTransientModifier(new AttributeModifier(id,amount,AttributeModifier.Operation.ADD_VALUE));
    }
}
