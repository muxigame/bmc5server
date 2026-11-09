package com.muxigame.city;

import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Finite material wholesale from the paired city's shared market pool. No transport entities. */
public final class CityMaterialImports {
 static final int WHOLESALE_PERCENT=55,ITEM_BATCH=16,ITEM_TYPES_PER_BATCH=4;
 static int cents(int local){return Math.max(1,(int)(((long)local*WHOLESALE_PERCENT+50)/100));}
 static void step(MinecraftServer s,long now){
  var t=CityTrade.get(s);
  for(var town:t.state.towns.values())if(town.model&&!town.urban&&RuralSettlement.active(s,town.city)){
   var village=CityData.get(s).require(town.city);var dest=village.housing;
   if(!dest.importedMaterials){dest.importedMaterials=true;CityData.get(s).setDirty();}
   long segment=Math.floorDiv(now,3000);if(dest.materialImportSegment==segment)continue;dest.materialImportSegment=segment;CityData.get(s).setDirty();
   var head=t.liveHead(s,town.city);if(head==null)continue;
   var sourceTown=t.state.towns.values().stream().filter(v->v.model&&v.urban&&RuralSettlement.active(s,v.city)&&town.city.equals(CityRoute.village(t,v.city))).sorted(Comparator.comparing(v->v.city)).findFirst().orElse(null);if(sourceTown==null)continue;
   var city=CityData.get(s).require(sourceTown.city);
   var sellers=t.state.businesses.values().stream().filter(b->b.city.equals(city.id)&&b.kind.equals("supplier")&&!b.account.equals(head.account)&&t.usable(s,b)&&WorkHours.active(s,city,b.worker)).sorted(Comparator.comparing(b->b.id)).toList();if(sellers.isEmpty())continue;
   var seller=sellers.get((int)Math.floorMod(dest.materialImportCursor,sellers.size()));var e=CityEconomy.get(s);int types=0,units=0;
   for(var entry:dest.market.entrySet()){
    if(types>=ITEM_TYPES_PER_BATCH)break;String item=entry.getKey();if(!CityMaterialCatalog.material(item))continue;var target=entry.getValue();var source=city.housing.market.get(item);if(source==null||source.stock<=0||target.stock>=target.cap)continue;
    String key="material-import|"+seller.account+"|"+item;if(!dest.tails.containsKey(key)&&dest.tails.size()>=8192)continue;
    int tail=dest.tails.getOrDefault(key,0),unit=cents(target.priceCents);long spare=Math.max(0,e.balance(head.account)-CityTrade.headReserve(s,town.city));if(spare<=0)continue;
    int n=(int)Math.min(Math.min(ITEM_BATCH,Math.min(source.stock,target.cap-target.stock)),(spare*100+99-tail)/unit);if(n<=0)continue;
    long total=(long)n*unit+tail,paid=total/100;if(e.balance(seller.account)>CityEconomy.LIMIT-paid||!CityTrade.pay(s,head.account,seller.account,paid,"MATERIAL_WHOLESALE"))continue;
    source.stock-=n;source.sold+=n;target.stock+=n;dest.tails.put(key,(int)(total%100));dest.imported.merge(item,(long)n,Long::sum);city.housing.exported.merge(item,(long)n,Long::sum);seller.materialUnits+=n;seller.materialSales+=paid;t.profit(s,seller,(int)paid,0);units+=n;types++;
    e.log(now,"MATERIAL_SHIPMENT",city.id.toString(),village.id.toString(),0,item+" x"+n+"; wholesaleCents="+unit+"; paid="+paid+"; seller="+seller.id);
   }
   if(units>0){dest.materialImportCursor++;t.setDirty();CityData.get(s).setDirty();}
  }
 }
}



