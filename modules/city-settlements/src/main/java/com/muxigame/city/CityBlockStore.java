package com.muxigame.city;

import java.util.*;
import java.lang.reflect.Type;
import com.google.gson.*;
import net.minecraft.core.BlockPos;

/** Palette-backed 16x16 layers; flat terrain does not allocate a boxed map entry per block. */
public final class CityBlockStore extends AbstractMap<Long,String> {
    private static final class Layer {int uniform;int[] cells;int get(int i){return cells==null?uniform:cells[i];}void set(int i,int id){if(cells==null){if(uniform==id)return;cells=new int[256];Arrays.fill(cells,uniform);}cells[i]=id;}void compact(){if(cells==null)return;int v=cells[0];for(int n:cells)if(n!=v)return;uniform=v;cells=null;}}
    private static final class Section {final TreeMap<Integer,Layer> layers=new TreeMap<>();}
    private final LinkedHashMap<Long,Section> sections=new LinkedHashMap<>();
    private final ArrayList<String> palette=new ArrayList<>();
    private final HashMap<String,Integer> ids=new HashMap<>();
    private long count;
    private long revision;
    public CityBlockStore(){palette.add(null);}
    public CityBlockStore(Map<Long,String> source){this();putAll(source);compact();}
    private static long section(BlockPos p){return new BlockPos(p.getX()>>4,p.getY()>>4,p.getZ()>>4).asLong();}
    private int id(String value){Objects.requireNonNull(value);return ids.computeIfAbsent(value,v->{palette.add(v);return palette.size()-1;});}
    @Override public int size(){return (int)Math.min(Integer.MAX_VALUE,count);}
    public long blockCount(){return count;}
    public long revision(){return revision;}
    @Override public String get(Object key){if(!(key instanceof Long packed))return null;var p=BlockPos.of(packed);var s=sections.get(section(p));if(s==null)return null;var layer=s.layers.get(p.getY()&15);return layer==null?null:palette.get(layer.get(((p.getZ()&15)<<4)|(p.getX()&15)));}
    @Override public boolean containsKey(Object key){return get(key)!=null;}
    @Override public String put(Long key,String value){var p=BlockPos.of(key);var s=sections.computeIfAbsent(section(p),k->new Section());var layer=s.layers.computeIfAbsent(p.getY()&15,k->new Layer());int index=((p.getZ()&15)<<4)|(p.getX()&15),old=layer.get(index),next=id(value);if(old!=next){layer.set(index,next);revision++;if(old==0)count++;}return palette.get(old);}
    @Override public String remove(Object key){if(!(key instanceof Long packed))return null;var p=BlockPos.of(packed);var s=sections.get(section(p));if(s==null)return null;var layer=s.layers.get(p.getY()&15);if(layer==null)return null;int index=((p.getZ()&15)<<4)|(p.getX()&15),old=layer.get(index);if(old!=0){layer.set(index,0);count--;revision++;}return palette.get(old);}
    @Override public void clear(){sections.clear();ids.clear();palette.clear();palette.add(null);count=0;revision++;}
    public void compact(){for(var s:sections.values())for(var l:s.layers.values())l.compact();}
    public int storedSections(){return sections.size();}
    static Collection<String> states(Map<Long,String> values){return values instanceof CityBlockStore b?b.states():new HashSet<>(values.values());}
    public Collection<String> states(){var used=new HashSet<Integer>();for(var s:sections.values())for(var layer:s.layers.values()){if(layer.cells==null){if(layer.uniform!=0)used.add(layer.uniform);}else for(int id:layer.cells)if(id!=0)used.add(id);}return used.stream().map(palette::get).toList();}
    public boolean inside(CityBounds bounds){
        for(var entry:sections.entrySet()){var base=BlockPos.of(entry.getKey());var min=new BlockPos(base.getX()<<4,base.getY()<<4,base.getZ()<<4);if(bounds.contains(min)&&bounds.contains(min.offset(15,15,15)))continue;
            for(var e:entry.getValue().layers.entrySet())for(int i=0;i<256;i++)if(e.getValue().get(i)!=0&&!bounds.contains(min.offset(i&15,e.getKey(),i>>4)))return false;
        }return true;
    }
    static boolean inside(Map<Long,String> values,CityBounds bounds){return values instanceof CityBlockStore b?b.inside(bounds):values.keySet().stream().allMatch(p->bounds.contains(BlockPos.of(p)));}
    public void removeWithin(CityBounds bounds){
        for(var entry:sections.entrySet()){var base=BlockPos.of(entry.getKey());int x=base.getX()<<4,y=base.getY()<<4,z=base.getZ()<<4;
            if(x>bounds.max().getX()||x+15<bounds.min().getX()||y>bounds.max().getY()||y+15<bounds.min().getY()||z>bounds.max().getZ()||z+15<bounds.min().getZ())continue;
            for(var layer:entry.getValue().layers.entrySet()){int height=y+layer.getKey();if(height<bounds.min().getY()||height>bounds.max().getY())continue;for(int i=0;i<256;i++)if(layer.getValue().get(i)!=0){var p=new BlockPos(x+(i&15),height,z+(i>>4));if(bounds.contains(p))remove(p.asLong());}}
        }
    }
    public void retainWithin(CityBounds bounds){
        var iterator=sections.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();var base=BlockPos.of(entry.getKey());int x=base.getX()<<4,y=base.getY()<<4,z=base.getZ()<<4;
            if(bounds.contains(new BlockPos(x,y,z))&&bounds.contains(new BlockPos(x+15,y+15,z+15)))continue;
            if(x>bounds.max().getX()||x+15<bounds.min().getX()||y>bounds.max().getY()||y+15<bounds.min().getY()||z>bounds.max().getZ()||z+15<bounds.min().getZ()){
                for(var layer:entry.getValue().layers.values()){if(layer.cells==null){if(layer.uniform!=0)count-=256;}else for(int id:layer.cells)if(id!=0)count--;}
                iterator.remove();revision++;continue;
            }
            for(var layer:entry.getValue().layers.entrySet())for(int i=0;i<256;i++)if(layer.getValue().get(i)!=0){var p=new BlockPos(x+(i&15),y+layer.getKey(),z+(i>>4));if(!bounds.contains(p))remove(p.asLong());}
        }
    }
    @Override public Set<Entry<Long,String>> entrySet(){return new AbstractSet<>(){
        @Override public int size(){return CityBlockStore.this.size();}
        @Override public Iterator<Entry<Long,String>> iterator(){return new Iterator<>(){
            final Iterator<Entry<Long,Section>> chunks=sections.entrySet().iterator();Iterator<Entry<Integer,Layer>> layers=Collections.emptyIterator();BlockPos base;Layer layer;int y,index;Long ready,last;
            private void advance(){while(ready==null){if(layer!=null){while(index<256){int i=index++;if(layer.get(i)!=0){ready=new BlockPos((base.getX()<<4)+(i&15),(base.getY()<<4)+y,(base.getZ()<<4)+(i>>4)).asLong();return;}}layer=null;}if(layers.hasNext()){var e=layers.next();y=e.getKey();layer=e.getValue();index=0;}else if(chunks.hasNext()){var e=chunks.next();base=BlockPos.of(e.getKey());layers=e.getValue().layers.entrySet().iterator();}else return;}}
            @Override public boolean hasNext(){advance();return ready!=null;}
            @Override public Entry<Long,String> next(){advance();if(ready==null)throw new NoSuchElementException();last=ready;ready=null;return new SimpleImmutableEntry<>(last,get(last));}
            @Override public void remove(){if(last==null)throw new IllegalStateException();CityBlockStore.this.remove(last);last=null;}
        };}
    };}
    /** Reads original plain coordinate maps as well as the compact format. */
    public static final class Adapter implements JsonSerializer<Map<Long,String>>,JsonDeserializer<Map<Long,String>> {
        @Override public JsonElement serialize(Map<Long,String> map,Type type,JsonSerializationContext context){
            CityBlockStore store=map instanceof CityBlockStore b?b:new CityBlockStore(map);store.compact();var root=new JsonObject();root.addProperty("format","layers-v1");var used=new HashSet<Integer>();for(var section:store.sections.values())for(var layer:section.layers.values()){if(layer.cells==null){if(layer.uniform!=0)used.add(layer.uniform);}else for(int id:layer.cells)if(id!=0)used.add(id);}var active=used.stream().map(store.palette::get).sorted().toList();var canonical=new HashMap<String,Integer>();var palette=new JsonArray();for(String state:active){canonical.put(state,canonical.size()+1);palette.add(state);}root.add("palette",palette);var chunks=new JsonArray();
            for(var entry:new TreeMap<>(store.sections).entrySet()){var row=new JsonObject();row.addProperty("pos",Long.toString(entry.getKey()));var layers=new JsonArray();for(var item:entry.getValue().layers.entrySet()){var layer=item.getValue();var record=new JsonArray();record.add(item.getKey());if(layer.cells==null){if(layer.uniform==0)continue;record.add(canonical.get(store.palette.get(layer.uniform)));}else{var runs=new JsonArray();for(int i=0;i<256;){int id=layer.cells[i],start=i++;while(i<256&&layer.cells[i]==id)i++;if(id==0)continue;var run=new JsonArray();run.add(start);run.add(i-start);run.add(canonical.get(store.palette.get(id)));runs.add(run);}if(runs.isEmpty())continue;record.add(runs);}layers.add(record);}if(layers.isEmpty())continue;row.add("layers",layers);chunks.add(row);}root.add("sections",chunks);return root;
        }
        @Override public Map<Long,String> deserialize(JsonElement json,Type type,JsonDeserializationContext context)throws JsonParseException{
            var out=new CityBlockStore();var root=json.getAsJsonObject();
            if(!root.has("format")){for(var e:root.entrySet()){long key=Long.parseLong(e.getKey());if(!e.getValue().isJsonPrimitive()||!e.getValue().getAsJsonPrimitive().isString())throw new JsonParseException("Invalid legacy block state");out.put(key,e.getValue().getAsString());}out.compact();return out;}
            if(!root.get("format").getAsString().equals("layers-v1"))throw new JsonParseException("Unsupported block store format");
            var states=root.getAsJsonArray("palette");var palette=new ArrayList<String>();palette.add(null);for(var v:states){String value=v.getAsString();if(value.length()>1024)throw new JsonParseException("Block state too long");palette.add(value);}
            var seen=new HashSet<Long>();for(var raw:root.getAsJsonArray("sections")){var row=raw.getAsJsonObject();long key=Long.parseLong(row.get("pos").getAsString());if(!seen.add(key))throw new JsonParseException("Duplicate section");var base=BlockPos.of(key);if(Math.abs((long)base.getX())>1875000L||Math.abs((long)base.getZ())>1875000L||base.getY() < -128||base.getY()>127)throw new JsonParseException("Section coordinate outside representable world");var ys=new HashSet<Integer>();for(var layerRaw:row.getAsJsonArray("layers")){var layer=layerRaw.getAsJsonArray();if(layer.size()!=2)throw new JsonParseException("Invalid layer");int y=layer.get(0).getAsBigDecimal().intValueExact();if(y<0||y>15||!ys.add(y))throw new JsonParseException("Invalid layer coordinate");var runs=new JsonArray();if(layer.get(1).isJsonPrimitive()){var run=new JsonArray();run.add(0);run.add(256);run.add(layer.get(1));runs.add(run);}else runs=layer.get(1).getAsJsonArray();int previousEnd=0;for(var runRaw:runs){var run=runRaw.getAsJsonArray();if(run.size()!=3)throw new JsonParseException("Invalid layer run");int start=run.get(0).getAsBigDecimal().intValueExact(),length=run.get(1).getAsBigDecimal().intValueExact(),id=run.get(2).getAsBigDecimal().intValueExact();if(start<previousEnd||length<1||start<0||start>255||length>256-start||id<1||id>=palette.size())throw new JsonParseException("Invalid palette run");previousEnd=start+length;for(int i=start;i<previousEnd;i++)out.put(new BlockPos((base.getX()<<4)+(i&15),(base.getY()<<4)+y,(base.getZ()<<4)+(i>>4)).asLong(),palette.get(id));}}}out.compact();return out;
        }
    }
}
