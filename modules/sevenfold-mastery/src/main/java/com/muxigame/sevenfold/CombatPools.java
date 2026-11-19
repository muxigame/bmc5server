package com.muxigame.sevenfold;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
/** Independent reserves; the completed constellation determines the displayed pool. */
public record CombatPools(int resilience,int stamina,int might,int focus,int mana,int scale) {
    public static final String KEY="SevenfoldCombatPools";
    public static final int CAP=20;
    public CombatPools(int r,int s,int m,int f,int mana){this(r,s,m,f,mana,1);}
    public CombatPools {scale=scale==5?5:1;resilience=Math.clamp(resilience,0,CAP*scale);stamina=Math.clamp(stamina,0,CAP*scale);might=Math.clamp(might,0,CAP*scale);focus=Math.clamp(focus,0,CAP);mana=Math.clamp(mana,0,CAP*scale);}
    public static CombatPools full(){return new CombatPools(20,20,20,0,20);}
    /** Null is neutral mana, never an elemental profession or a saved mastery selection. */
    public static Mastery displayedResource(StarTreeState tree){return switch(tree.profession()){
        case BERSERKER->Mastery.MIGHT;case WARDEN->Mastery.STAMINA;
        case KNIGHT->Mastery.RESILIENCE;case BRAWLER->Mastery.FOCUS;default->null;
    };}
    public int value(Mastery m){return m==null?mana:switch(m){case RESILIENCE->resilience;case STAMINA->stamina;case MIGHT->might;case FOCUS->focus;default->mana;};}
    public CombatPools with(Mastery m,int value){return switch(m){case RESILIENCE->new CombatPools(value,stamina,might,focus,mana,scale);case STAMINA->new CombatPools(resilience,value,might,focus,mana,scale);case MIGHT->new CombatPools(resilience,stamina,value,focus,mana,scale);case FOCUS->new CombatPools(resilience,stamina,might,value,mana,scale);default->new CombatPools(resilience,stamina,might,focus,value,scale);};}
    public CombatPools clampMana(int cap){return new CombatPools(resilience,stamina,might,focus,Math.min(mana,cap),scale);}
    public int cap(Mastery m){return m==Mastery.FOCUS?20:20*scale;}
    public CombatPools scaleTo(int next){next=next==5?5:1;return new CombatPools((resilience*next+scale-1)/scale,(stamina*next+scale-1)/scale,(might*next+scale-1)/scale,focus,(mana*next+scale-1)/scale,next);}
    public static CombatPools of(Player p){
        var data=p.getPersistentData();if(data.contains(KEY,10))return load(data.getCompound(KEY));
        var old=MasteryState.of(p);var pools=full();
        if(old.mastery()!=null&&old.mastery()!=Mastery.FOCUS)pools=pools.with(old.mastery(),old.resource());
        return pools;
    }
    public static CombatPools load(CompoundTag t){return new CombatPools(t.getInt("resilience"),t.getInt("stamina"),t.getInt("might"),t.getInt("focus"),t.getInt("mana"),t.getInt("scale"));}
    public CompoundTag save(){var t=new CompoundTag();t.putInt("schema",2);t.putInt("scale",scale);t.putInt("resilience",resilience);t.putInt("stamina",stamina);t.putInt("might",might);t.putInt("focus",focus);t.putInt("mana",mana);return t;}
    public void store(Player p){p.getPersistentData().put(KEY,save());}
}
