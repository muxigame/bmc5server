package com.muxigame.city;

import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

@GameTestHolder("city_settlements") @PrefixGameTestTemplate(false)
public final class CityTestsAccountCommand {
 @GameTest(template="empty") public static void prefilledRealAccountIdsParseWithoutAdjustingFunds(GameTestHelper h){
  var server=h.getLevel().getServer();var dispatcher=new CommandDispatcher<CommandSourceStack>();CityEconomyAccess.register(dispatcher);
  var source=server.createCommandSourceStack();var economy=CityEconomy.get(server);String before=CityEconomy.JSON.toJson(economy.state);
  for(String id:java.util.List.of("person:4f8943d8-56a8-3d24-b81f-21e7cd099b07","trade:1d35f5bc-015b-4392-b1ef-d5018fd21d57","treasury:6333e2e0-7d47-4747-bd09-e96c464e5c91","simple")){
   String command=CityEconomyAccess.accountCommand(id,275).substring(1);var parsed=dispatcher.parse(command,source);
   h.assertTrue(!parsed.getReader().canRead()&&parsed.getExceptions().isEmpty()&&parsed.getContext().getCommand()!=null,"The generated account command must parse completely, including colon-prefixed real account IDs");
   var context=parsed.getContext().build(command);h.assertTrue(id.equals(com.mojang.brigadier.arguments.StringArgumentType.getString(context,"id")),"Quoting must preserve the exact account, not create an alias");
  }
  h.assertTrue(before.equals(CityEconomy.JSON.toJson(economy.state)),"Parsing never executes account adjustment or mutates balances/audit");h.succeed();
 }
}
