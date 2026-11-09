package com.muxigame.city;
import java.util.*;
import net.minecraft.world.item.*;

/** Meat is valuable household stock; nutrition and luxury-consumption effects are intentionally separate. */
public final class RuralMeat {
    static final String[] NAMES={"猪肉","鸡肉","羊肉","牛肉","兔肉"};
    static final int[] BUY={16,14,18,20,16},SELL={20,18,23,25,21},WEIGHT={35,25,20,15,5};
    static final Item[] RAW={Items.PORKCHOP,Items.CHICKEN,Items.MUTTON,Items.BEEF,Items.RABBIT};
    static final Item[] COOKED={Items.COOKED_PORKCHOP,Items.COOKED_CHICKEN,Items.COOKED_MUTTON,Items.COOKED_BEEF,Items.COOKED_RABBIT};
    static int type(int roll){int sum=0;for(int i=0;i<5;i++){sum+=WEIGHT[i];if(roll<sum)return i;}throw new IllegalArgumentException("roll");}
    static int count(List<RuralRules.Stock> stocks,boolean cooked,int type){return stocks.stream().mapToInt(s->(cooked?s.cooked:s.raw)[type]).sum();}
    static int total(List<RuralRules.Stock> stocks){return stocks.stream().mapToInt(s->Arrays.stream(s.raw).sum()+Arrays.stream(s.cooked).sum()).sum();}
    static int reserve(RuralRules.Family f,Map<UUID,RuralRules.Person> people){return 2*(int)RuralRules.members(f,people).stream().filter(p->p.age!=RuralRules.Age.INFANT).count();}
    static int put(List<RuralRules.Stock> stocks,boolean cooked,int type,int n){return FoodMoves.add(stocks,type+(cooked?7:2),n,0);}
    static int take(List<RuralRules.Stock> stocks,boolean cooked,int type,int n){return FoodMoves.remove(stocks,type+(cooked?7:2),n).count();}
    static int cook(List<RuralRules.Stock> stocks,int budget,int target){int already=stocks.stream().mapToInt(s->Arrays.stream(s.cooked).sum()).sum(),done=0;for(int i=0;i<5;i++){int n=Math.min(budget-done,Math.min(count(stocks,false,i),Math.max(0,target-already-done)));take(stocks,false,i,n);put(stocks,true,i,n);done+=n;}return done;}
    static int trade(RuralRules.Family buyer,UUID seller,List<RuralRules.Stock> source,List<RuralRules.Stock> target,int type,int wanted,int price,Map<UUID,Long> money){
        if(buyer.members.contains(seller)||wanted<=0)return 0;int n=(int)Math.min(Math.min(wanted,Math.min(count(source,false,type),RuralRules.capacity(target))),RuralRules.balance(buyer,money)/price);if(n==0||!RuralRules.pay(buyer,seller,(long)n*price,money))return 0;FoodMoves.move(source,target,type+2,n);return n;
    }
    static int transferItem(RuralRules.Stock stock,ItemStack held){for(int i=0;i<5;i++)for(int j=0;j<2;j++)if(held.is((j==0?RAW:COOKED)[i])){int n=put(List.of(stock),j==1,i,held.getCount());held.shrink(n);return n;}return -1;}
    static ItemStack withdraw(RuralRules.Stock stock){for(int i=0;i<5;i++)for(int j=0;j<2;j++)if((j==0?stock.raw:stock.cooked)[i]>0){take(List.of(stock),j==1,i,1);return new ItemStack((j==0?RAW:COOKED)[i]);}return ItemStack.EMPTY;}
    static void clear(RuralRules.Stock stock){Arrays.fill(stock.raw,0);Arrays.fill(stock.cooked,0);}
}
