package com.muxigame.city;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsListing {
 @GameTest(template="empty") public static void profitableBelowStandardCanListButCostFloorAndOwnershipRemain(GameTestHelper h){try(var f=new CityTestsScale.Fixture(h)){
  f.r.state.simulationTicks=f.r.state.simulationDay=6000;f.city.works.clock=f.city.works.day=6000;
  var merchant=f.t.state.businesses.values().stream().filter(b->b.city.equals(f.city.id)&&b.kind.equals("procurement")).findFirst().orElseThrow();
  for(var seller:f.t.state.businesses.values())if(seller.city.equals(f.city.id))seller.listed[0].take(seller.listed[0].count);
  var farm=f.t.state.businesses.values().stream().filter(b->b.city.equals(f.city.id)&&b.kind.equals("urban_farmer")).findFirst().orElseThrow();farm.listed[0].add(300,0);
  merchant.stock[0].add(24,4800);long cash=f.t.money(f.s),grain=f.t.grain(f.s);
  int quote=f.t.quote(f.s,f.city.id,0,6000);h.assertTrue(quote<f.t.base(f.city.id,0)&&quote>merchant.stock[0].unit(),"Real market quote is profitable but below the reference price");
  f.t.list(f.s,merchant,6000);
  h.assertTrue(merchant.listed[0].count==f.t.state.config.grainStep&&merchant.stock[0].count==24-f.t.state.config.grainStep,"Profitable stock can start a bounded listing batch below standard");
  h.assertTrue(merchant.listed[0].cents+merchant.stock[0].cents==4800&&f.t.money(f.s)==cash&&f.t.grain(f.s)==grain,"Listing preserves stock cost and quantity and creates no sales cash");
  int listed=merchant.listed[0].count,remaining=merchant.stock[0].count;
  merchant.stock[0].cents=(long)remaining*f.t.quote(f.s,f.city.id,0,6000)*100;
  f.t.list(f.s,merchant,6000);h.assertTrue(merchant.listed[0].count==listed&&merchant.stock[0].count==remaining&&!merchant.listing[0],"At-cost quotes stop further listing, including an existing batch");
  merchant.stock[0].cents+=remaining*100L;f.t.list(f.s,merchant,6000);
  h.assertTrue(merchant.listed[0].count==listed&&merchant.stock[0].count==remaining&&!merchant.listing[0]&&f.t.money(f.s)==cash,"Below-cost quotes cannot start a new batch or create revenue");
 }h.succeed();}
}
