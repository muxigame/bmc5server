package com.muxigame.city;
import java.util.*;
import com.google.gson.*;
import net.minecraft.server.level.ServerLevel;
/** First stage: one citizen, a designated communal food store, fixed treatment interval. */
public final class CityBasics {
    static JsonObject edit(JsonObject base,JsonObject fields){
        CityEconomy.require(fields.keySet().equals(Set.of("enabled","foodPerHour","min","max","treatment","warehouse","item")),"基础表单字段不完整");
        int food=fields.get("foodPerHour").getAsBigDecimal().intValueExact(),min=fields.get("min").getAsBigDecimal().intValueExact(),max=fields.get("max").getAsBigDecimal().intValueExact(),actual=fields.get("treatment").getAsBigDecimal().intValueExact();
        CityEconomy.require(food>=1&&food<=64&&min>=0&&min<=max&&max<=100&&actual>=0&&actual<=100,"食量须1–64，待遇须0–100且下限不高于上限");
        String item=fields.get("item").getAsString();CityEconomy.require(new net.minecraft.world.item.ItemStack(CityEconomy.item(item)).has(net.minecraft.core.component.DataComponents.FOOD),"口粮必须是可食用物品");UUID warehouse=UUID.fromString(fields.get("warehouse").getAsString());
        var result=base.deepCopy();var p=result.getAsJsonObject("profile");p.addProperty("basicMode",true);p.addProperty("enabled",fields.get("enabled").getAsBoolean());p.addProperty("foodPerHour",food);p.addProperty("basicWarehouse",warehouse.toString());p.addProperty("periodTicks",1200);p.addProperty("baseComfort",actual);p.addProperty("minNeed",min);p.addProperty("maxNeed",max);p.addProperty("need",(min+max)/2);p.addProperty("level",0);p.addProperty("step",0);
        var need=new CityLiving.Need();need.item=item;need.comfort=0;var basket=new JsonArray();basket.add(CityEconomy.JSON.toJsonTree(need));p.add("basket",basket);return result;
    }
    static int due(CityLiving.Profile p){long credit=p.foodCredit+(long)p.foodPerHour*1200;int count=(int)(credit/72000);p.foodCredit=credit%72000;return count;}
    static int contribution(int treatment,int min,int max){return treatment<min?-1:treatment>=max?1:0;}
    static void cycle(CityEconomy e,ServerLevel l,CityData.City c,CityLiving.Profile p,long now){
        int quantity=due(p);var food=p.basket.getFirst();boolean fed=p.lastTick==0||p.lastEssentials;
        if(quantity>0)try{var box=CityEconomy.warehouse(l,c,p.basicWarehouse);var slots=new CityEconomy.Slots(box);slots.remove(food.item,quantity);slots.commit();fed=true;food.status="本次领取口粮"+quantity+"份";}catch(IllegalArgumentException ex){fed=false;food.status="未吃到饭："+ex.getMessage();}
        else food.status="尚未到领取时间（按实际运行小时累计）";
        p.lastTick=now;p.lastEssentials=fed;p.actual=p.baseComfort;p.contribution=fed?contribution(p.actual,p.minNeed,p.maxNeed):-1;p.lastIncome=p.income;p.income=0;p.lastCost=0;p.lastBudget=0;p.lastCovered=true;p.comfortableCycles=0;p.affordableCycles=0;
        p.status=(fed?"吃饭正常":"缺粮")+"；待遇"+p.actual+" / 需求"+p.minNeed+"–"+p.maxNeed+"；"+(p.contribution<0?"不满":p.contribution>0?"满意":"可以接受");
        var simulation=e.state.simulation.get(c.id);if(simulation!=null&&simulation.enabled&&l.getEntity(p.npc)==null)simulation.offlineConsumptions++;
        e.log(now,"BASIC_LIVING",CityEconomy.person(p.npc),"",0,"口粮应领"+quantity+"；"+p.status);
    }
}
