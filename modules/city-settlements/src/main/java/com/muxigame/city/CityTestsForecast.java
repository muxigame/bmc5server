package com.muxigame.city;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
@GameTestHolder(CityMod.ID) @PrefixGameTestTemplate(false)
public class CityTestsForecast {
 @GameTest(template="empty") public static void sustainedSurplusForecastUsesExistingPriceClock(GameTestHelper h){
  int fresh=TradeRules.plan(1000,200,5,1,1,192,1000,0);
  int rich=TradeRules.plan(1000,200,5,1,1,192,1000,4L*TradeRules.DAY);
  h.assertTrue(fresh>0&&rich==0,"A trade that only appears profitable when accumulated discount is omitted must be rejected");
  h.assertTrue(TradeRules.quote(5,1000,200,0,4L*TradeRules.DAY)==1,"Forecast and actual market share the same accumulated price factor");
  h.assertTrue(TradeRules.plan(300,200,5,1,1,192,1000,4L*TradeRules.DAY)>0,"Dropping below surplus threshold still uses ordinary price recovery");h.succeed();
 }
}
