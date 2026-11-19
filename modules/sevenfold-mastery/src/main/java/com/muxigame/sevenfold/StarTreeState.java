package com.muxigame.sevenfold;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/** Visibility, deliberate constellation activation and ten-star completion are separate states. */
public record StarTreeState(int[] masks,int activated) {
    public static final String KEY=BuildVariant.LITE?"SevenfoldStarTreeLite":"SevenfoldStarTree";public static final int FULL=1023;
    public static final int CONSTELLATION=-1;
    public StarTreeState {masks=Arrays.copyOf(masks,8);activated&=255;for(int i=0;i<8;i++)masks[i]&=FULL;
        if((activated&1)==0){Arrays.fill(masks,0);activated=0;}else masks[0]=FULL;
        int chosen=-1;for(int i=1;i<=3;i++)if((activated&(1<<i))!=0){if(chosen>=0){for(int j=1;j<8;j++)masks[j]=0;activated&=1;break;}chosen=i;}
        for(int i=1;i<8;i++)if((activated&(1<<i))==0)masks[i]=0;
        if((activated&2)==0||masks[1]!=FULL){for(int i=4;i<8;i++)masks[i]=0;activated&=15;}
        chosen=-1;for(int i=4;i<8;i++)if((activated&(1<<i))!=0){if(chosen>=0){for(int j=4;j<8;j++)masks[j]=0;activated&=15;break;}chosen=i;}
        if(BuildVariant.LITE){for(int i=1;i<8;i++)masks[i]=0;activated&=15;}
    }
    /** Preserve the older public constructor; schema4 loads use explicit activation instead. */
    public StarTreeState(int[] masks){this(masks,inferActivation(masks));}
    private static int inferActivation(int[] masks){int bits=0;for(int i=0;i<Math.min(8,masks.length);i++)if((masks[i]&FULL)!=0)bits|=1<<i;return bits;}
    public StarTreeState(int legacy){this(legacyMasks(legacy,1,WarProfession.NONE));}
    public StarTreeState(int legacy,int rank,WarProfession profession){this(legacyMasks(legacy,rank,profession));}
    private static int[] legacyMasks(int lit,int rank,WarProfession profession){
        int[] a=new int[8];if(lit<0||(lit&1)==0)return a;a[0]=FULL;
        if(Integer.bitCount(lit&14)!=1)return a;
        int index=(lit&2)!=0?1:(lit&8)!=0?2:3;a[index]=(1<<Math.clamp(rank*2,0,10))-1;
        if(index==1&&rank>=5&&profession!=null&&profession!=WarProfession.NONE)a[3+profession.ordinal()]=FULL;return a;
    }
    @Override public int[] masks(){return masks.clone();}
    @Override public boolean equals(Object o){return o instanceof StarTreeState s&&activated==s.activated&&Arrays.equals(masks,s.masks);}
    @Override public int hashCode(){return 31*Arrays.hashCode(masks)+activated;}
    public int mask(StarNode n){return n==null||n==StarNode.RESET?0:masks[n.ordinal()];}
    public int count(StarNode n){return Integer.bitCount(mask(n));}
    public boolean isActivated(StarNode n){return n!=null&&n!=StarNode.RESET&&(activated&(1<<n.ordinal()))!=0;}
    public boolean isLit(StarNode n){return isActivated(n);}
    public boolean isComplete(StarNode n){return isActivated(n)&&mask(n)==FULL;}
    public boolean active(){return isComplete(StarNode.CRYING);}
    public boolean star(StarNode n,int index){return index>=0&&index<10&&(mask(n)&(1<<index))!=0;}
    public boolean visible(StarNode n){return n!=null&&n!=StarNode.RESET&&(n==StarNode.CRYING||active()&&(n.base()||!BuildVariant.LITE&&n.path!=null&&isComplete(n.path)));}
    public StarNode path(){for(var n:StarNode.BASES)if(isActivated(n))return n;return null;}
    public WarProfession profession(){for(var n:StarNode.values())if(n.profession!=WarProfession.NONE&&isComplete(n))return n.profession;return WarProfession.NONE;}
    public int rank(){return path()==null?0:count(path());}
    public int lit(){return (active()?1:0)|(isLit(StarNode.WARRIOR)?2:0)|(isLit(StarNode.MAGE)?4:0)|(isLit(StarNode.ASSASSIN)?8:0);}
    public boolean canToggle(StarNode n,int index){
        if(BuildVariant.LITE&&n!=null&&n!=StarNode.CRYING&&n!=StarNode.RESET&&(!n.base()||index!=CONSTELLATION))return false;
        if(n==null)return false;if(n==StarNode.RESET)return path()!=null&&index==0;
        if(index<CONSTELLATION||index>=10||!visible(n))return false;
        return n==StarNode.CRYING||index==CONSTELLATION||isActivated(n);
    }
    public StarTreeState toggle(StarNode n,int index){
        if(!canToggle(n,index))return this;if(n==StarNode.RESET)return reset();
        var a=masks.clone();int bits=activated;
        if(n==StarNode.CRYING){Arrays.fill(a,0);a[0]=active()?0:FULL;return new StarTreeState(a,active()?0:1);}
        if(index==CONSTELLATION){
            if(isActivated(n)){a[n.ordinal()]=0;bits&=~(1<<n.ordinal());}
            else {
                if(n.base()&&path()!=null&&path()!=n){for(int i=1;i<8;i++)a[i]=0;bits&=1;}
                if(n.profession!=WarProfession.NONE){for(int i=4;i<8;i++)a[i]=0;bits&=15;}
                bits|=1<<n.ordinal();
            }
        }else a[n.ordinal()]^=1<<index;
        return new StarTreeState(a,bits);
    }
    public StarTreeState reset(){int[] a=new int[8];a[0]=masks[0];return new StarTreeState(a,activated&1);}
    public static int healthPerStar(StarNode n){return n==StarNode.WARRIOR?2:0;}
    public double healthBonus(){return active()?80+count(StarNode.WARRIOR)*healthPerStar(StarNode.WARRIOR):0;}
    public double attackBonus(){return 0;}
    public int manaBonus(){return active()?80:0;}
    public static StarTreeState load(CompoundTag t){
        if(t.getInt("schema")>=3){int[] a=new int[8];var stars=t.getCompound("stars");for(var n:StarNode.values())if(n!=StarNode.RESET)a[n.ordinal()]=stars.getInt(n.id);return t.getInt("schema")>=4?new StarTreeState(a,t.getInt("activated")):new StarTreeState(a);}
        return new StarTreeState(0);
    }
    public CompoundTag save(){var t=new CompoundTag();t.putInt("schema",4);t.putInt("activated",activated);var stars=new CompoundTag();for(var n:StarNode.values())if(n!=StarNode.RESET)stars.putInt(n.id,mask(n));t.put("stars",stars);return t;}
    public static StarTreeState of(Player p){return load(p.getPersistentData().getCompound(KEY));}
    public void store(Player p){p.getPersistentData().put(KEY,save());}
}
