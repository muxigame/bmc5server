package com.muxigame.buildingarchive;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Reads only selected files; never extracts ZIP paths onto disk. */
final class ArchiveTransferFiles {
    record Entry(String name,byte[] bytes){}
    private static byte[] bounded(InputStream in) throws IOException {var b=in.readNBytes(ArchiveTransfer.MAX_FILE+1);if(b.length==0||b.length>ArchiveTransfer.MAX_FILE)throw new IOException("建筑文件为空或超过8 MB。");return b;}
    static List<Entry> read(Path selected,String newName) throws IOException {
        var result=new ArrayList<Entry>();
        if(selected.toString().toLowerCase(Locale.ROOT).endsWith(".zip")){
            if(Files.size(selected)>32L*1024*1024)throw new IOException("作品ZIP最多32 MB。");int total=0;
            try(var zip=new ZipFile(selected.toFile())){var entries=zip.entries();while(entries.hasMoreElements()){
                var e=entries.nextElement();if(e.isDirectory()||!e.getName().toLowerCase(Locale.ROOT).endsWith(".nbt"))continue;
                if(e.getName().contains("/")||e.getName().contains("\\"))throw new IOException("建筑文件需位于ZIP顶层，请使用收集建筑作品生成的ZIP。");
                if(result.size()>=64)throw new IOException("一次最多导入64份建筑。");byte[] bytes;try(var in=zip.getInputStream(e)){bytes=bounded(in);}total+=bytes.length;if(total>32*1024*1024)throw new IOException("建筑总量超过32 MB。");result.add(new Entry(e.getName().replaceFirst("(?i)\\.nbt$",""),bytes));
            }}
            if(result.isEmpty())throw new IOException("ZIP里没有NBT建筑文件。");
            String zipName=selected.getFileName().toString().replaceFirst("(?i)\\.zip$","");
            if(result.size()==1&&!newName.equals(zipName))result.set(0,new Entry(newName,result.getFirst().bytes));
        }else{try(var in=Files.newInputStream(selected)){result.add(new Entry(newName,bounded(in)));}}
        var names=new HashSet<String>();
        for(var e:result){if(!e.name.matches("[\\p{L}\\p{N}_-]{1,64}"))throw new IOException("建筑名称只能使用中文、字母、数字、下划线和短横线。");if(!names.add(e.name))throw new IOException("ZIP内有重复的建筑名称。");}
        return result;
    }
}
