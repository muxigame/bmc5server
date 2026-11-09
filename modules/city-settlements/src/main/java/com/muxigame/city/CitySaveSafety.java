package com.muxigame.city;
import java.nio.file.Files;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** SavedData's fallback constructor must never replace an existing unreadable registry. */
public final class CitySaveSafety {
 static <T> T newData(MinecraftServer server,String id,Supplier<T> empty){var file=server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(id+".dat");if(Files.exists(file))throw new IllegalStateException("现有城市存档无法读取，已停止以保护原数据："+id+"；请检查启动日志，禁止创建空表覆盖。");return empty.get();}
}
