package com.muxigame.city;

import java.util.*;
import net.minecraft.core.BlockPos;

/** Disjoint rectangular regions. Work depends on edit complexity, never empty land volume. */
final class CityAreas {
    private CityAreas() {}
    static final int MAX_PARTS = 4096;
    private record Cached(Object regions,Index index) {}
    private static final Map<CityData.City,Cached> CACHE=new WeakHashMap<>();
    /** A bounding-volume tree, independent of the number of blocks inside a region. */
    static final class Index {
        final CityBounds bounds;final Index left,right;final List<CityBounds> leaf;
        Index(List<CityBounds> regions){
            if(regions.isEmpty()){bounds=null;left=right=null;leaf=List.of();return;}
            int x=Integer.MAX_VALUE,y=x,z=x,a=Integer.MIN_VALUE,b=a,c=a;
            for(var r:regions){x=Math.min(x,r.min().getX());y=Math.min(y,r.min().getY());z=Math.min(z,r.min().getZ());a=Math.max(a,r.max().getX());b=Math.max(b,r.max().getY());c=Math.max(c,r.max().getZ());}
            bounds=new CityBounds(new BlockPos(x,y,z),new BlockPos(a,b,c));
            if(regions.size()<=8){left=right=null;leaf=List.copyOf(regions);return;}
            int axis=(long)a-x>=(long)b-y&&((long)a-x>=(long)c-z)?0:(long)b-y>=(long)c-z?1:2;
            var sorted=new ArrayList<>(regions);sorted.sort(Comparator.comparingLong(r->axis==0?(long)r.min().getX()+r.max().getX():axis==1?(long)r.min().getY()+r.max().getY():(long)r.min().getZ()+r.max().getZ()));int mid=sorted.size()/2;
            left=new Index(sorted.subList(0,mid));right=new Index(sorted.subList(mid,sorted.size()));leaf=null;
        }
        boolean contains(BlockPos p){if(bounds==null||!bounds.contains(p))return false;if(leaf!=null)return leaf.stream().anyMatch(b->b.contains(p));return left.contains(p)||right.contains(p);}
        boolean intersects(CityBounds b){if(bounds==null||!bounds.intersects(b))return false;if(leaf!=null)return leaf.stream().anyMatch(r->r.intersects(b));return left.intersects(b)||right.intersects(b);}
    }
    private static Index index(CityData.City c){var cached=CACHE.get(c);if(cached==null||cached.regions!=c.areas){cached=new Cached(c.areas,new Index(c.areas));CACHE.put(c,cached);}return cached.index;}
    static List<CityBounds> regions(CityData.City c) { return c.areas == null ? List.of(c.bounds) : c.areas; }
    static boolean contains(CityData.City c, BlockPos p) {
        return c.bounds.contains(p) && !c.excluded.contains(p.asLong()) && (c.areas == null || index(c).contains(p));
    }
    static CityBounds intersection(CityBounds a, CityBounds b) {
        if (!a.intersects(b)) return null;
        return new CityBounds(new BlockPos(Math.max(a.min().getX(), b.min().getX()), Math.max(a.min().getY(), b.min().getY()), Math.max(a.min().getZ(), b.min().getZ())),
                new BlockPos(Math.min(a.max().getX(), b.max().getX()), Math.min(a.max().getY(), b.max().getY()), Math.min(a.max().getZ(), b.max().getZ())));
    }
    static void subtract(CityBounds from, CityBounds cut, List<CityBounds> out) {
        CityBounds i = intersection(from, cut);
        if (i == null) { out.add(from); return; }
        int x0=from.min().getX(), y0=from.min().getY(), z0=from.min().getZ(), x1=from.max().getX(), y1=from.max().getY(), z1=from.max().getZ();
        int a=i.min().getX(), b=i.min().getY(), c=i.min().getZ(), d=i.max().getX(), e=i.max().getY(), f=i.max().getZ();
        if (x0<a) box(out,x0,y0,z0,a-1,y1,z1);
        if (d<x1) box(out,d+1,y0,z0,x1,y1,z1);
        if (y0<b) box(out,a,y0,z0,d,b-1,z1);
        if (e<y1) box(out,a,e+1,z0,d,y1,z1);
        if (z0<c) box(out,a,b,z0,d,e,c-1);
        if (f<z1) box(out,a,b,f+1,d,e,z1);
    }
    private static void box(List<CityBounds> out,int x,int y,int z,int a,int b,int c) {out.add(new CityBounds(new BlockPos(x,y,z),new BlockPos(a,b,c)));}
    static List<CityBounds> edit(List<CityBounds> old, CityBounds area, boolean include) {
        var next = new ArrayList<CityBounds>();
        for (CityBounds b : old) { subtract(b, area, next); if (next.size()>MAX_PARTS) throw complex(); }
        if (include) next.add(area);
        if (next.size()>MAX_PARTS) throw complex();
        return List.copyOf(next);
    }
    static IllegalArgumentException complex() {return new IllegalArgumentException("区域形状过于零碎，请合并相邻选区；城市边长不受此项限制。");}
    static long volume(CityData.City c) {
        long n=0; for (CityBounds b:regions(c)) n=Math.addExact(n,b.volumeLong());
        for (long p:c.excluded) if (c.areas==null||index(c).contains(BlockPos.of(p))) n--;
        return n;
    }
    static long volumeIn(CityData.City c,CityBounds area) {
        long n=0;for (CityBounds b:regions(c)) {var i=intersection(b,area);if(i!=null)n=Math.addExact(n,i.volumeLong());}
        for(long p:c.excluded)if(area.contains(BlockPos.of(p))&&(c.areas==null||index(c).contains(BlockPos.of(p))))n--;
        return n;
    }
    static boolean covers(CityData.City c,CityBounds b) {return volumeIn(c,b)==b.volumeLong();}
    static List<CityBounds> validate(CityBounds bounds,List<CityBounds> areas) {
        if(areas==null)return null;
        if(areas.size()>MAX_PARTS)throw complex();
        for(int n=0;n<areas.size();n++){var b=areas.get(n);if(!bounds.contains(b.min())||!bounds.contains(b.max()))throw new IllegalArgumentException("区域记录超出城市边界。");for(int k=0;k<n;k++)if(b.intersects(areas.get(k)))throw new IllegalArgumentException("区域记录重复重叠。");}
        return List.copyOf(areas);
    }
}
