package com.muxigame.city;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Explicit temporary production-material supply for opted-in data-model towns only. */
public final class CityMaterialCatalog {
 static final Set<String> BASE=Set.of("minecraft:oak_planks","minecraft:glass","minecraft:stone_bricks","minecraft:cobblestone","minecraft:stone","minecraft:dirt","minecraft:grass_block","minecraft:sand","minecraft:gravel");
 static final Map<String,Integer> EQUIPMENT=Map.of("minecraft:crafting_table",80,"city_settlements:seed_sifter",120,"city_settlements:rural_stove",160,"city_settlements:accounting_desk",120,"city_settlements:hunter_table",120,"city_settlements:merchant_guild_desk",160,"city_settlements:baking_oven",320,"city_settlements:consumer_workbench",160,"city_settlements:npc_storage",160);
 static boolean material(String id){return BASE.contains(id)||EQUIPMENT.containsKey(id);}
 static Set<String> auditedItems(){var result=new TreeSet<String>(BASE);result.addAll(EQUIPMENT.keySet());return result;}
 static void ensure(MinecraftServer s,CityData.City city){if(!CityCurrency.model(s,city.id))return;var t=CityTrade.get(s);var vendors=t.state.businesses.values().stream().filter(b->b.city.equals(city.id)&&(b.kind.equals("supplier")||b.kind.equals("head"))).sorted(Comparator.comparing(b->b.id)).toList();int index=0;for(String id:new TreeSet<>(EQUIPMENT.keySet())){if(city.housing.market.containsKey(id))continue;var m=new CityHousing.Material();m.priceCents=EQUIPMENT.get(id);m.supply=1;m.cap=8;m.stock=0;if(!vendors.isEmpty())m.supplier=vendors.get(index++%vendors.size()).id;city.housing.market.put(id,m);}CityData.get(s).setDirty();}
}
