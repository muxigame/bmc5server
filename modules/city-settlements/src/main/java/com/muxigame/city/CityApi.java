package com.muxigame.city;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Optional CustomNPCs bridge: read on the Minecraft server thread; no hard dependency. */
public final class CityApi {
    private CityApi(){}
    public record Settlement(UUID id,String name,String dimension,CityBounds bounds,Set<UUID> residents){}
    private static Settlement view(CityData.City c){return new Settlement(c.id,c.name,c.dimension,c.bounds,c.residents());}
    public static Optional<Settlement> at(ServerLevel level,BlockPos pos){
        return CityData.get(level.getServer()).cities().stream().filter(c->c.dimension.equals(level.dimension().location().toString())&&c.includes(pos)).findFirst().map(CityApi::view);
    }
    public static Optional<Settlement> home(MinecraftServer server,UUID npc){return CityData.get(server).cities().stream().filter(c->c.residents.contains(npc)).findFirst().map(CityApi::view);}
    public static Optional<CityService.Statistics> statistics(MinecraftServer server,UUID city){CityData.get(server).require(city);return Optional.ofNullable(CityService.statistics(city));}
    public static long balance(MinecraftServer server,String account){return CityEconomy.get(server).balance(account);}
    /** Server-thread trusted integration only; callers must enforce their policy/contract authority. */
    public static boolean settle(MinecraftServer server,String transaction,String from,String to,long amount,CityEconomy.Purpose purpose){
        return CityEconomy.get(server).settle(transaction,from,to,amount,purpose,server.overworld().getGameTime());
    }
}
