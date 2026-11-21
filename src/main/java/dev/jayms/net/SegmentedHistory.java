package dev.jayms.net;

import java.util.*;

/** Ordered ordinary edits plus compact bulk-cell segments. Owner-thread only.
 * Snapshot roots share immutable segments; ordinary cell indexes use COW tokens. */
final class SegmentedHistory extends AbstractMap<String,Protocol.Edit> {
    private final PersistentOrderedMap<Object> ordered;
    private final PersistentOrderedMap<Cell> cells;
    private Object owner=new Object();
    private int entries;
    private long revision;
    private static final class Cell {
        final Object owner;
        String single,bulk;
        LinkedHashSet<String> many;
        int bulkSize;
        Cell(Object owner,Cell source){this.owner=owner;if(source!=null){single=source.single;bulk=source.bulk;bulkSize=source.bulkSize;if(source.many!=null)many=new LinkedHashSet<>(source.many);}}
        int size(){return bulkSize+(many==null?(single==null?0:1):many.size());}
        void add(String key){if(many!=null)many.add(key);else if(single==null)single=key;else if(!single.equals(key)){many=new LinkedHashSet<>();many.add(single);many.add(key);single=null;}}
        void remove(String key){if(many==null){if(key.equals(single))single=null;}else{many.remove(key);if(many.size()==1){single=many.iterator().next();many=null;}}}
        List<String> keys(){return many==null?(single==null?List.of():List.of(single)):List.copyOf(many);}
    }
    /** Immutable bounded pages. An overwrite copies at most one 32-row page
     * and its directory, rather than every previous override in the cell. */
    private static final class Overrides {
        static final Overrides EMPTY=new Overrides(null);
        final Protocol.Edit[][] pages;
        Overrides(Protocol.Edit[][] pages){this.pages=pages;}
        Protocol.Edit get(int slot){return pages==null||pages[slot>>>5]==null?null:pages[slot>>>5][slot&31];}
        Overrides put(int slot,Protocol.Edit edit,int capacity) {
            var next=pages==null?new Protocol.Edit[(capacity+31)>>>5][]:pages.clone();int page=slot>>>5;
            var values=next[page]==null?new Protocol.Edit[Math.min(32,capacity-(page<<5))]:next[page].clone();
            values[slot&31]=edit;next[page]=values;return new Overrides(next);
        }
        Overrides remove(BitSet slots) {
            if(pages==null)return this;
            Protocol.Edit[][] next=null;var copied=new BitSet();
            for(int slot=slots.nextSetBit(0);slot>=0;slot=slots.nextSetBit(slot+1)) {
                int page=slot>>>5;if(pages[page]==null||pages[page][slot&31]==null)continue;
                if(next==null)next=pages.clone();
                if(!copied.get(page)){next[page]=next[page].clone();copied.set(page);}
                next[page][slot&31]=null;
            }
            if(next==null)return this;
            for(int page=copied.nextSetBit(0);page>=0;page=copied.nextSetBit(page+1)) {
                boolean empty=true;for(var value:next[page])if(value!=null){empty=false;break;}
                if(empty)next[page]=null;
            }
            for(var page:next)if(page!=null)return new Overrides(next);
            return EMPTY;
        }
    }
    private static final class Segment {
        final CellReplayRows rows;
        final long[] removed;
        final Overrides overrides;
        final int size;
        Segment(CellReplayRows rows){this(rows,null,Overrides.EMPTY,rows.size);}
        Segment(CellReplayRows rows,long[] removed,Overrides overrides,int size){this.rows=rows;this.removed=removed;this.overrides=overrides;this.size=size;}
        boolean active(int slot){return removed==null||(removed[slot>>>6]&(1L<<(slot&63)))==0;}
        Protocol.Edit edit(int slot){var replacement=overrides.get(slot);return replacement==null?rows.edit(slot):replacement;}
        Segment put(int slot,Protocol.Edit edit){return new Segment(rows,removed,overrides.put(slot,edit,rows.size),size);}
        Segment remove(BitSet slots){
            var mask=removed==null?new long[(rows.size+63)/64]:removed.clone();
            var next=overrides.remove(slots);
            for(int i=slots.nextSetBit(0);i>=0;i=slots.nextSetBit(i+1))mask[i>>>6]|=1L<<(i&63);
            return new Segment(rows,mask,next,size-slots.cardinality());
        }
    }
    SegmentedHistory(){ordered=new PersistentOrderedMap<>();cells=new PersistentOrderedMap<>();}
    SegmentedHistory(SegmentedHistory source){ordered=new PersistentOrderedMap<>(source.ordered);cells=new PersistentOrderedMap<>(source.cells);entries=source.entries;source.owner=new Object();}
    long revision(){return revision;}
    int cellCount(){return cells.size();}
    int cellSize(String key){var cell=cells.get(key);return cell==null?0:cell.size();}
    Iterator<WorldVoxels.CellPosition> positions(){
        var iterator=cells.values().iterator();
        return new Iterator<>(){
            public boolean hasNext(){return iterator.hasNext();}
            public WorldVoxels.CellPosition next(){var cell=iterator.next();if(cell.bulk!=null){var rows=((Segment)ordered.get(cell.bulk)).rows;return new WorldVoxels.CellPosition(rows.x,rows.y,rows.z);}String key=cell.many==null?cell.single:cell.many.iterator().next();var edit=(Protocol.Edit)ordered.get(key);return new WorldVoxels.CellPosition(edit.x(),edit.y(),edit.z());}
        };
    }
    /** Stable owner/snapshot inputs only. Shared COW cell identities prove that
     * all values are unchanged; other cells still require exact keyed equality.
     * Scans cell indexes, not every unchanged compact row. */
    Set<WorldVoxels.CellPosition> changedPositions(SegmentedHistory before) {
        var changed=new LinkedHashSet<WorldVoxels.CellPosition>();
        for(var entry:cells.entrySet()) {
            var cell=entry.getValue();var old=before.cells.get(entry.getKey());
            if(cell!=old&&!sameCellValues(cell,old,before))changed.add(position(cell));
        }
        for(var entry:before.cells.entrySet())
            if(!cells.containsKey(entry.getKey()))changed.add(before.position(entry.getValue()));
        return Collections.unmodifiableSet(changed);
    }
    private WorldVoxels.CellPosition position(Cell cell) {
        if(cell.bulk!=null){var rows=bulk(cell).rows;return new WorldVoxels.CellPosition(rows.x,rows.y,rows.z);}
        var edit=(Protocol.Edit)ordered.get(cell.many==null?cell.single:cell.many.iterator().next());
        return new WorldVoxels.CellPosition(edit.x(),edit.y(),edit.z());
    }
    private boolean sameCellValues(Cell cell,Cell old,SegmentedHistory before) {
        if(old==null||cell.size()!=old.size())return false;
        for(var key:cell.keys())if(!ordered.get(key).equals(before.get(key)))return false;
        var segment=bulk(cell);
        if(segment!=null&&segment!=before.bulk(old))
            for(int slot=0;slot<segment.rows.size;slot++)if(segment.active(slot)) {
                var edit=segment.edit(slot);if(!edit.equals(before.get(segment.rows.key(slot))))return false;
            }
        return true;
    }
    private Cell writable(String key){var cell=cells.get(key);if(cell==null||cell.owner!=owner){cell=new Cell(owner,cell);cells.put(key,cell);}return cell;}
    private static String cellKey(String key){int slash=key.indexOf('/');return slash<0?key:key.substring(0,slash);}
    private Segment bulk(Cell cell){return cell==null||cell.bulk==null?null:(Segment)ordered.get(cell.bulk);}
    @Override public int size(){return entries;}
    @Override public Protocol.Edit get(Object object){
        if(!(object instanceof String key))return null;
        var direct=ordered.get(key);if(direct instanceof Protocol.Edit edit)return edit;
        var segment=bulk(cells.get(cellKey(key)));if(segment==null)return null;
        int slot=segment.rows.find(key);return slot<0||!segment.active(slot)?null:segment.edit(slot);
    }
    @Override public boolean containsKey(Object key){return get(key)!=null;}
    @Override public Protocol.Edit put(String key,Protocol.Edit edit){
        if(!key.equals(edit.key()))throw new IllegalArgumentException("Edit key mismatch");
        var direct=ordered.get(key);
        if(direct instanceof Protocol.Edit old){writable(edit.cellKey());ordered.put(key,edit);revision++;return old;}
        String position=edit.depth()==0?key:edit.cellKey();var current=cells.get(position);var segment=bulk(current);
        int slot=segment==null?-1:segment.rows.find(key);
        if(slot>=0&&segment.active(slot)){var old=segment.edit(slot);writable(position);ordered.put(current.bulk,segment.put(slot,edit));revision++;return old;}
        if(entries==Integer.MAX_VALUE)throw new IllegalStateException("History map capacity exhausted");
        writable(position).add(key);ordered.put(key,edit);entries++;revision++;return null;
    }
    @Override public Protocol.Edit remove(Object object){
        if(!(object instanceof String key))return null;
        var direct=ordered.get(key);String position=direct instanceof Protocol.Edit edit?edit.cellKey():cellKey(key);
        var current=cells.get(position);if(current==null)return null;
        if(direct instanceof Protocol.Edit edit){ordered.remove(key);var cell=writable(position);cell.remove(key);if(cell.size()==0)cells.remove(position);entries--;revision++;return edit;}
        var segment=bulk(current);if(segment==null)return null;int slot=segment.rows.find(key);
        if(slot<0||!segment.active(slot))return null;
        var old=segment.edit(slot);var slots=new BitSet();slots.set(slot);removeBulk(position,segment,slots);return old;
    }
    private void removeBulk(String position,Segment segment,BitSet slots){
        if(slots.isEmpty())return;
        var cell=writable(position);var next=segment.remove(slots);
        if(next.size==0){ordered.remove(cell.bulk);cell.bulk=null;cell.bulkSize=0;}else{ordered.put(cell.bulk,next);cell.bulkSize=next.size;}
        if(cell.size()==0)cells.remove(position);entries-=slots.cardinality();revision+=slots.cardinality();
    }
    void remember(Protocol.Edit edit){
        String position=edit.cellKey();var current=cells.get(position);
        if(current!=null){
            for(var key:current.keys())if(WorldVoxels.covered((Protocol.Edit)ordered.get(key),edit))remove(key);
            var segment=bulk(cells.get(position));
            if(segment!=null){var removed=new BitSet();for(int i=0;i<segment.rows.size;i++)if(segment.active(i)&&WorldVoxels.covered(segment.edit(i),edit))removed.set(i);removeBulk(position,segment,removed);}
        }
        put(edit.key(),edit);
    }
    void replaceCell(CellReplayRows rows){
        var old=cells.get(rows.cell);int prior=old==null?0:old.size();
        long nextSize=(long)entries-prior+rows.size;if(nextSize>Integer.MAX_VALUE)throw new IllegalStateException("History map capacity exhausted");
        if(old!=null){for(var key:old.keys())ordered.remove(key);if(old.bulk!=null)ordered.remove(old.bulk);}
        var cell=new Cell(owner,null);
        if(rows.size==1){cell.single=rows.cell;ordered.put(rows.cell,rows.edit(0));}
        else{cell.bulk="\u0000"+rows.cell;cell.bulkSize=rows.size;ordered.put(cell.bulk,new Segment(rows));}
        cells.put(rows.cell,cell);entries=(int)nextSize;revision+=rows.rawCount;
    }
    /** Ordinary replay's initial clear removes every previous row in this cell. */
    void replayCell(CellReplayRows rows){
        int prior=cellSize(rows.cell);replaceCell(rows);revision+=prior;
    }
    @Override public void clear(){if(entries!=0)revision++;ordered.clear();cells.clear();entries=0;}
    @Override public Set<Entry<String,Protocol.Edit>> entrySet(){
        return Collections.unmodifiableSet(new AbstractSet<>(){
            public int size(){return entries;}
            public Iterator<Entry<String,Protocol.Edit>> iterator(){
                return new Iterator<>(){
                    final long expected=revision;
                    final Iterator<Entry<String,Object>> segments=ordered.entrySet().iterator();
                    Entry<String,Object> current;int slot;
                    private void check(){if(revision!=expected)throw new ConcurrentModificationException();}
                    public boolean hasNext(){check();while(true){if(current!=null){if(current.getValue() instanceof Protocol.Edit)return true;var s=(Segment)current.getValue();while(slot<s.rows.size&&!s.active(slot))slot++;if(slot<s.rows.size)return true;}if(!segments.hasNext())return false;current=segments.next();slot=0;}}
                    public Entry<String,Protocol.Edit> next(){if(!hasNext())throw new NoSuchElementException();if(current.getValue() instanceof Protocol.Edit edit){var result=Map.entry(current.getKey(),edit);current=null;return result;}var s=(Segment)current.getValue();int i=slot++;return Map.entry(s.rows.key(i),s.edit(i));}
                };
            }
        });
    }
}
