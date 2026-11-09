package com.muxigame.city;

import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;

/** Explicit JSON exchange; import validates everything before touching saved state. */
public final class CityBlueprints {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    static JsonArray pos(BlockPos p){JsonArray a=new JsonArray();a.add(p.getX());a.add(p.getY());a.add(p.getZ());return a;}
    static BlockPos pos(JsonArray a){if(a.size()!=3)throw new IllegalArgumentException("坐标需要三个整数。");int[] n=new int[3];for(int i=0;i<3;i++)n[i]=a.get(i).getAsBigDecimal().intValueExact();return new BlockPos(n[0],n[1],n[2]);}
    public static JsonObject export(CityData.City c){JsonObject j=new JsonObject();j.addProperty("schema",3);j.addProperty("id",c.id.toString());j.addProperty("owner",c.owner.toString());j.addProperty("dimension",c.dimension);j.addProperty("revision",c.revision);j.addProperty("name",c.name);j.add("min",pos(c.bounds.min()));j.add("max",pos(c.bounds.max()));JsonArray excluded=new JsonArray();c.excluded.stream().sorted().forEach(v->excluded.add(pos(BlockPos.of(v))));j.add("excluded",excluded);if(c.areas!=null){JsonArray regions=new JsonArray();for(var b:c.areas){JsonObject row=new JsonObject();row.add("min",pos(b.min()));row.add("max",pos(b.max()));regions.add(row);}j.add("areas",regions);}JsonArray residents=new JsonArray();c.residents.stream().sorted().forEach(v->residents.add(v.toString()));j.add("residents",residents);j.add("registry",CityRegistry.export(c));return j;}
    public static void apply(CityData d,CityData.City c,ServerLevel level,JsonObject j,long revision){
        try{
            if((j.get("schema").getAsInt()!=1&&j.get("schema").getAsInt()!=2&&j.get("schema").getAsInt()!=3)||!j.get("id").getAsString().equals(c.id.toString())||!j.get("owner").getAsString().equals(c.owner.toString())||!j.get("dimension").getAsString().equals(c.dimension))throw new IllegalArgumentException("版本、ID、所有者和维度必须与目标聚落一致。");
            if(c.revision!=revision||j.get("revision").getAsLong()!=revision)throw new IllegalArgumentException("数据版本已变化，请重新导出。");
            String name=CityData.validName(j.get("name").getAsString());CityBounds b=new CityBounds(pos(j.getAsJsonArray("min")),pos(j.getAsJsonArray("max")));b.validateWorld(level);d.checkBounds(c.id,c.dimension,b);
            Set<Long> mask=new HashSet<>();JsonArray excluded=j.getAsJsonArray("excluded");if(excluded.size()>b.volumeLong())throw new IllegalArgumentException("排除数据过多。");for(JsonElement e:excluded){BlockPos p=pos(e.getAsJsonArray());if(!b.contains(p)||!mask.add(p.asLong()))throw new IllegalArgumentException("排除坐标越界或重复。");}
            Set<UUID> residents=new HashSet<>();if(j.getAsJsonArray("residents").size()>4096)throw new IllegalArgumentException("居民数据过多。");for(JsonElement e:j.getAsJsonArray("residents")){UUID id=UUID.fromString(e.getAsString());if(!residents.add(id)||d.cities().stream().anyMatch(other->other!=c&&other.residents.contains(id)))throw new IllegalArgumentException("居民重复或属于其他聚落。");}
            CityData.City candidate=new CityData.City(c.id,c.owner,name,c.dimension,b);
            candidate.excluded.addAll(mask);if(j.has("areas")){JsonArray rows=j.getAsJsonArray("areas");if(rows.size()>CityAreas.MAX_PARTS)throw CityAreas.complex();var regions=new ArrayList<CityBounds>();for(var raw:rows){var row=raw.getAsJsonObject();regions.add(new CityBounds(pos(row.getAsJsonArray("min")),pos(row.getAsJsonArray("max"))));}candidate.areas=CityAreas.validate(b,regions);}
            CityRegistry.read(candidate,j.get("schema").getAsInt()>=2?j.getAsJsonObject("registry"):CityRegistry.export(c));
            c.housing=candidate.housing;c.works=candidate.works;c.buildings.clear();c.buildings.putAll(candidate.buildings);c.warehouses.clear();c.warehouses.putAll(candidate.warehouses);
            c.name=name;c.bounds=b;c.areas=candidate.areas;c.excluded.clear();c.excluded.addAll(mask);c.residents.clear();c.residents.addAll(residents);d.changed(c);CityService.invalidate(c);
        }catch(RuntimeException e){throw new IllegalArgumentException("导入拒绝："+e.getMessage(),e);}
    }
    private static Path file(CommandSourceStack s,String name)throws Exception{if(!name.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("文件名只允许字母、数字、下划线和连字符。");Path dir=s.getServer().getWorldPath(LevelResource.ROOT).resolve("city_settlements/blueprints");Files.createDirectories(dir);return dir.resolve(name+".json");}
    private interface Action {String run()throws Exception;}
    private static int run(CommandSourceStack s,Action a){try{String message=a.run();s.sendSuccess(()->Component.literal(message),false);return 1;}catch(Exception e){s.sendFailure(Component.literal("[城市数据] "+e.getMessage()));return 0;}}
    private static ServerLevel level(CommandSourceStack s,CityData.City c){ServerLevel l=s.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(c.dimension)));if(l==null)throw new IllegalArgumentException("维度未启用。");return l;}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        var root=Commands.literal("citydata").requires(s->s.hasPermission(2));
        root.then(Commands.literal("list").executes(ctx->run(ctx.getSource(),()->{StringBuilder out=new StringBuilder();for(var c:CityData.get(ctx.getSource().getServer()).cities())out.append(c.id).append(" ").append(c.name).append(" revision=").append(c.revision).append(" included=").append(CityAreas.volume(c)).append("\n");return out.toString();})));
        for(String op:List.of("export","apply")){
            var path=Commands.argument("file",StringArgumentType.word());
            if(op.equals("export"))path.executes(ctx->run(ctx.getSource(),()->{var c=CityData.get(ctx.getSource().getServer()).require(UUID.fromString(StringArgumentType.getString(ctx,"id")));Path f=file(ctx.getSource(),StringArgumentType.getString(ctx,"file"));Files.writeString(f,JSON.toJson(export(c)));return "已导出 "+f;}));
            else path.then(Commands.argument("revision",LongArgumentType.longArg(0)).executes(ctx->run(ctx.getSource(),()->{var d=CityData.get(ctx.getSource().getServer());var c=d.require(UUID.fromString(StringArgumentType.getString(ctx,"id")));Path f=file(ctx.getSource(),StringArgumentType.getString(ctx,"file"));if(Files.size(f)>16*1024*1024)throw new IllegalArgumentException("文件超过16MB。");JsonObject j=JsonParser.parseString(Files.readString(f)).getAsJsonObject();Files.writeString(f.resolveSibling(f.getFileName()+".before-"+c.revision+"-"+System.currentTimeMillis()+".json"),JSON.toJson(export(c)));apply(d,c,level(ctx.getSource(),c),j,LongArgumentType.getLong(ctx,"revision"));return "已应用数据，revision="+c.revision;})));
            root.then(Commands.literal(op).then(Commands.argument("id",StringArgumentType.word()).then(path)));
        }
        for(String op:List.of("include","exclude"))root.then(Commands.literal(op).then(Commands.argument("id",StringArgumentType.word()).then(Commands.argument("from",BlockPosArgument.blockPos()).then(Commands.argument("to",BlockPosArgument.blockPos()).then(Commands.argument("revision",LongArgumentType.longArg(0)).executes(ctx->run(ctx.getSource(),()->{var d=CityData.get(ctx.getSource().getServer());var c=d.require(UUID.fromString(StringArgumentType.getString(ctx,"id")));if(c.revision!=LongArgumentType.getLong(ctx,"revision"))throw new IllegalArgumentException("版本冲突。");long n=CityService.area(d,c,level(ctx.getSource(),c),new UUID(0,0),CityBounds.between(BlockPosArgument.getBlockPos(ctx,"from"),BlockPosArgument.getBlockPos(ctx,"to")),op.equals("include"));return "已调整 "+n+" 格，revision="+c.revision;})))))));
        root.then(Commands.literal("create").then(Commands.argument("owner",StringArgumentType.word()).then(Commands.argument("name",StringArgumentType.string()).then(Commands.argument("from",BlockPosArgument.blockPos()).then(Commands.argument("to",BlockPosArgument.blockPos()).executes(ctx->run(ctx.getSource(),()->{CityBounds b=CityBounds.between(BlockPosArgument.getBlockPos(ctx,"from"),BlockPosArgument.getBlockPos(ctx,"to"));b.validateWorld(ctx.getSource().getLevel());var c=CityData.get(ctx.getSource().getServer()).create(UUID.fromString(StringArgumentType.getString(ctx,"owner")),StringArgumentType.getString(ctx,"name"),ctx.getSource().getLevel().dimension().location().toString(),b);CityService.scan(c);return c.id.toString();})))))));
        dispatcher.register(root);
    }
}
