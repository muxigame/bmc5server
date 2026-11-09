package com.muxigame.city;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.google.gson.*;
/** Retired registry IDs remain readable solely for migration; no new rice enters gameplay. */
public final class CityWheatMigration {
 static final Map<String,String> ITEMS=Map.of("city_settlements:rice_seeds","minecraft:wheat_seeds","city_settlements:rice","minecraft:wheat","city_settlements:cooked_rice","minecraft:bread","city_settlements:rice_crop","minecraft:wheat");
 static String replace(String text){return text.replace("city_settlements:rice_seeds","minecraft:wheat_seeds").replace("city_settlements:rice_crop","minecraft:wheat").replace("city_settlements:cooked_rice","minecraft:bread").replace("city_settlements:rice","minecraft:wheat");}
 static JsonElement convert(JsonElement value){if(value.isJsonObject()){var out=new JsonObject();for(var entry:value.getAsJsonObject().entrySet()){String key=replace(entry.getKey());JsonElement next=entry.getKey().equals("resourceSource")&&entry.getValue().getAsString().equals("rice")?new JsonPrimitive("wheat"):convert(entry.getValue());if(out.has(key)&&next.isJsonPrimitive()&&next.getAsJsonPrimitive().isNumber())next=new JsonPrimitive(out.get(key).getAsLong()+next.getAsLong());out.add(key,next);}return out;}if(value.isJsonArray()){var out=new JsonArray();for(var entry:value.getAsJsonArray())out.add(convert(entry));return out;}if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString())return new JsonPrimitive(replace(value.getAsString()));return value.deepCopy();}
 static ItemStack convert(ItemStack stack){if(stack.isEmpty())return stack;String to=ITEMS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());return to==null?stack:stack.transmuteCopy(BuiltInRegistries.ITEM.get(ResourceLocation.parse(to)));}
 static void inventory(Container box){boolean changed=false;for(int i=0;i<box.getContainerSize();i++){var old=box.getItem(i);var next=convert(old);if(next!=old){box.setItem(i,next);changed=true;}}if(changed)box.setChanged();}
 public static void start(net.neoforged.neoforge.event.server.ServerStartedEvent event){var server=event.getServer();var e=CityEconomy.get(server);if(!e.state.extensions.has("wheatMigration0814")){var old=e.export();var next=convert(old).getAsJsonObject();next.getAsJsonObject("extensions").addProperty("wheatMigration0814",true);try{var dir=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("city_settlements/economy/backups");java.nio.file.Files.createDirectories(dir);var backup=dir.resolve("before-wheat0814.json");if(!java.nio.file.Files.exists(backup))java.nio.file.Files.writeString(backup,CityEconomy.JSON.toJson(old));e.apply(next,e.state.revision,CityData.get(server),server.overworld().getGameTime());}catch(Exception ex){throw new IllegalStateException("小麦迁移失败，旧数据保持，请查看备份",ex);}}}
 private static int cityIndex,scan,playerIndex;private static final Map<CityData.City,CityScanCursor> scans=new WeakHashMap<>();
 public static void clear(){cityIndex=scan=playerIndex=0;scans.clear();}
 public static void tick(MinecraftServer server){var cities=new ArrayList<>(CityData.get(server).cities());if(!cities.isEmpty()){var c=cities.get(Math.floorMod(cityIndex,cities.size()));var l=CityEconomy.level(server,c);var cursor=scans.get(c);if(cursor==null||cursor.done()){cursor=new CityScanCursor(CityLoadedChunks.scanBoxes(l,c.bounds));scans.put(c,cursor);}cursor.step(l,256,pos->{var state=l.getBlockState(pos);if(state.is(CityRice.CROP.get()))l.setBlock(pos,Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,state.getValue(BlockStateProperties.AGE_7)),2);if(l.getBlockEntity(pos) instanceof Container box)inventory(box);},air->{});cityIndex++;}
  if(server.getTickCount()%20==0&&!server.getPlayerList().getPlayers().isEmpty()){var players=server.getPlayerList().getPlayers();inventory(players.get(Math.floorMod(playerIndex++,players.size())).getInventory());}}
}
