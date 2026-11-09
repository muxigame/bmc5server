package com.muxigame.buildingarchive;

import java.text.Normalizer;
import java.util.*;

/** Cached text only: no registry, translations, item stacks or model work during search. */
final class TerrainSearchIndex<T> {
    record Entry<T>(String id,String label,String folded,T value) {}
    private final NavigableSet<Entry<T>> all=new TreeSet<>(Comparator.<Entry<T>,String>comparing(Entry::label).thenComparing(Entry::id));
    private final NavigableSet<Entry<T>> targets=new TreeSet<>(all.comparator());
    private boolean finished;
    static String fold(String value){return Normalizer.normalize(value.strip(),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);}
    void add(String id,String label,T value,boolean target){
        if(finished)throw new IllegalStateException("Index already complete");
        var entry=new Entry<T>(id,label,fold(label)+"\0"+fold(id),value);
        all.add(entry);if(target)targets.add(entry);
    }
    void finish(){finished=true;}
    boolean ready(){return finished;}
    int size(){return all.size();}
    Query<T> query(String text,boolean target){if(!finished)throw new IllegalStateException("Index still building");var source=target?targets:all;return new Query<>(source.iterator(),source.size(),fold(text));}
    static final class Query<T> {
        private final Iterator<Entry<T>> source;private final int total;private final String text;
        private final List<Entry<T>> found=new ArrayList<>();private boolean done,cancelled;private int scanned;
        Query(Iterator<Entry<T>> source,int total,String text){this.source=source;this.total=total;this.text=text;}
        int step(int maximum,long deadline){
            if(maximum<0)throw new IllegalArgumentException("Negative search budget");
            int visited=0;
            while(!done&&!cancelled&&visited<maximum&&source.hasNext()&&System.nanoTime()<deadline){
                var entry=source.next();visited++;scanned++;if(entry.folded().contains(text))found.add(entry);
            }
            if(!cancelled&&!source.hasNext())done=true;
            return visited;
        }
        void cancel(){cancelled=true;}
        boolean done(){return done&&!cancelled;}
        int scanned(){return scanned;}
        int total(){return total;}
        List<Entry<T>> results(){return done()?Collections.unmodifiableList(found):List.of();}
    }
}
