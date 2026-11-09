package com.muxigame.city;

import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Review-model switches and explicitly owned placeholder material supply, never retroactive grants. */
public final class CityModel {
 static void configure(MinecraftServer s,CityData.City city,boolean mint){var t=CityTrade.get(s);var town=t.state.towns.get(city.id);town.model=true;CityMaterialCatalog.ensure(s,city);var p=new CityCurrency.Policy();p.enabled=mint&&town.urban;t.state.currencies.put(city.id,p);
  var vendors=t.state.businesses.values().stream().filter(b->b.city.equals(city.id)&&(b.kind.equals("supplier")||b.kind.equals("head"))).sorted(Comparator.comparing(b->b.id)).toList();int i=0;for(var m:city.housing.market.values())if(!vendors.isEmpty())m.supplier=vendors.get(i++%vendors.size()).id;
  if(!town.urban){city.housing.importedMaterials=true;var head=t.head(city.id);if(head!=null){var person=RuralSettlement.get(s).state.people.get(head.worker);for(int good=2;good<12;good++)RuralMarket.policy(person,good).discount=60;}}
  t.linkStaff();t.setDirty();CityData.get(s).setDirty();
 }
 static CityTrade.Business materialVendor(MinecraftServer s,CityData.City city,String item){if(!CityCurrency.model(s,city.id))return null;var material=city.housing.market.get(item);if(material==null||material.supplier==null)return null;var b=CityTrade.get(s).state.businesses.get(material.supplier);return b!=null&&b.city.equals(city.id)?b:null;}
 static String materialAccount(MinecraftServer s,CityData.City city,String item){var b=materialVendor(s,city,item);return b==null?"market:"+city.id:b.account;}
 static void materialSold(MinecraftServer s,CityData.City city,String item,int quantity,long paid){var b=materialVendor(s,city,item);if(b==null)return;b.materialUnits+=quantity;b.materialSales+=paid;if(paid>0)CityTrade.get(s).profit(s,b,(int)paid,0);CityTrade.get(s).setDirty();}
}
