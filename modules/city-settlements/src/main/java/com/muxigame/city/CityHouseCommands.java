package com.muxigame.city;
import java.util.*;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
public final class CityHouseCommands {
 static void register(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("cityhouse").requires(s->s.hasPermission(2)||s.getEntity() instanceof net.minecraft.server.level.ServerPlayer p&&p.isCreative())
  .then(Commands.literal("open").then(Commands.argument("city",StringArgumentType.word()).then(Commands.argument("house",StringArgumentType.word()).executes(ctx->{var p=ctx.getSource().getPlayerOrException();CityHousing.snapshot(p,CityData.get(p.server).require(UUID.fromString(StringArgumentType.getString(ctx,"city"))),UUID.fromString(StringArgumentType.getString(ctx,"house")),0,"");return 1;}))))
  .then(Commands.literal("supply").then(Commands.argument("city",StringArgumentType.word()).then(Commands.argument("on",BoolArgumentType.bool()).executes(ctx->{var data=CityData.get(ctx.getSource().getServer());var c=data.require(UUID.fromString(StringArgumentType.getString(ctx,"city")));CityHousing.seedCatalog(c.housing);c.housing.testSupply=BoolArgumentType.getBool(ctx,"on");data.setDirty();ctx.getSource().sendSuccess(()->Component.literal("建材测试虚空供货="+c.housing.testSupply),false);return 1;}))))
  .then(Commands.literal("fixture").executes(ctx->{try{var p=ctx.getSource().getPlayerOrException();var c=RuralTools.city(p.serverLevel(),p.blockPosition());CityEconomy.require(c!=null,"请站在验证村内");var old=c.buildings.values().stream().filter(b->b.name().equals("房屋登记验证")).findFirst();if(old.isPresent()){CityHousing.snapshot(p,c,old.get().id(),0,"已有验证房屋，不重复生成");return 1;}
   var min=c.bounds.max().offset(-4,-4,-6);var bounds=CityBounds.between(min,min.offset(4,4,4));for(int n=0;n<bounds.volume();n++)CityEconomy.require(p.serverLevel().hasChunkAt(bounds.at(n))&&p.serverLevel().getBlockState(bounds.at(n)).isAir(),"验证房屋位置已有内容，不覆盖");var owner=c.residents.stream().filter(id->{var person=RuralSettlement.get(p.server).state.people.get(id);return person!=null&&!person.dead;}).sorted().findFirst().orElseThrow(()->new IllegalArgumentException("需要一名在世居民作为私人房屋所有者"));var data=CityData.get(p.server);CityRegistry.building(data,c,"房屋登记验证","住宅",bounds);var b=c.buildings.values().stream().filter(v->v.name().equals("房屋登记验证")).findFirst().orElseThrow();for(int x=0;x<5;x++)for(int y=0;y<5;y++)for(int z=0;z<5;z++){if(y==0||y==4)p.serverLevel().setBlock(min.offset(x,y,z),Blocks.STONE_BRICKS.defaultBlockState(),3);else if(x==0||x==4||z==0||z==4){if(!(x==2&&z==0&&y<=2))p.serverLevel().setBlock(min.offset(x,y,z),(y==2&&(x==0||x==4)&&z==2?Blocks.GLASS:Blocks.OAK_PLANKS).defaultBlockState(),3);}}var h=CityHousing.house(c,b.id());h.personal=true;h.owner=owner;h.beds=3;h.workerLimit=2;h.configured=true;CityHousing.capture(p.server,c,b.id());data.setDirty();ctx.getSource().sendSuccess(()->Component.literal("验证房屋已建："+b.id()+"；"+min.toShortString()),false);CityHousing.snapshot(p,c,b.id(),0,"验证房屋已登记，私人产权，3住户/2工人；没有创建居民或注入资金");return 1;
  }catch(Exception ex){ctx.getSource().sendFailure(Component.literal(ex.getMessage()));return 0;}})));
 }
}
