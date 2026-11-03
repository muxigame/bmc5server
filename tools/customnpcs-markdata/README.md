# MarkData patch

Apply MarkData.patch to the recovered source at f5589d88. Copy src/noppes/npcs into the corresponding src/main/java/noppes/npcs source tree. The checked-in build_overlay.py adds EntityMarkDataMixin to the BASE JAR mixin list, preserving all other entries. This is a small patch to the pinned binary, not a standalone distribution of third-party sources.

Run with JDK21 and the existing mapped NeoForge/Minecraft/CustomNPCs compile classpath:

    python tools/customnpcs-markdata/build_overlay.py --base BASE.jar --source-root RECOVERED_SOURCE --classpath-file CLASSPATH.txt --output FIXED.jar

CLASSPATH.txt contains a platform-separated javac classpath, not credentials. The base JAR is SHA256-pinned and verified before compilation. Only MarkData and the two new cache-interface/mixin sources compile; all unrelated binary and asset entries must remain byte-identical. The native MarkAudit mod exercises actual Entity save/load and garbage collection; it must run in an isolated dev server (test code intentionally requests GC and shuts down its server).
