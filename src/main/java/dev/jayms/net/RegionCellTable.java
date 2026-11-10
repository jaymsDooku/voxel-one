package dev.jayms.net;

import java.util.*;
import java.util.function.Predicate;

/** Owner-private local-cell table. Values require an immutable/COW protocol.
 * Copies detach only arrays, never values. Keys are the 4096 cells of one region. */
final class RegionCellTable<V> extends AbstractMap<Integer,V> {
    private static final int CELLS=4096,DENSE_AT=1024;
    private short[] keys;
    private Object[] values;
    private int size;
    private long version;
    private boolean dense;
    RegionCellTable(){keys=new short[4];values=new Object[4];}
    RegionCellTable(RegionCellTable<V> source) {
        keys=source.keys==null?null:source.keys.clone();values=source.values.clone();
        size=source.size;dense=source.dense;
    }
    private static void valid(int key){if(key<0||key>=CELLS)throw new IllegalArgumentException("Local region cell key");}
    private static int slot(int key,short[] keys,Object[] values) {
        int mask=values.length-1,index=(key*0x9e3779b9)&mask;
        while(values[index]!=null&&Short.toUnsignedInt(keys[index])!=key)index=(index+1)&mask;
        return index;
    }
    @SuppressWarnings("unchecked") V get(int key) {
        if(key<0||key>=CELLS)return null;
        return (V)values[dense?key:slot(key,keys,values)];
    }
    @Override public V get(Object key){return key instanceof Integer i?get(i.intValue()):null;}
    @Override public boolean containsKey(Object key){return get(key)!=null;}
    @Override public int size(){return size;}
    @Override public V put(Integer key,V value){return put(Objects.requireNonNull(key).intValue(),value);}
    @SuppressWarnings("unchecked") V put(int key,V value) {
        valid(key);Objects.requireNonNull(value);
        int index=dense?key:slot(key,keys,values);V old=(V)values[index];
        if(old==null&&!dense&&size==values.length/2) {
            grow();index=dense?key:slot(key,keys,values);
        }
        if(!dense)keys[index]=(short)key;
        values[index]=value;if(old==null)size++;version++;return old;
    }
    private void grow() {
        boolean nextDense=size>=DENSE_AT;
        var nextValues=new Object[nextDense?CELLS:values.length*2];
        short[] nextKeys=nextDense?null:new short[nextValues.length];
        for(int i=0;i<values.length;i++)if(values[i]!=null) {
            int key=Short.toUnsignedInt(keys[i]);
            int index=nextDense?key:slot(key,nextKeys,nextValues);
            if(!nextDense)nextKeys[index]=(short)key;
            nextValues[index]=values[i];
        }
        keys=nextKeys;values=nextValues;dense=nextDense;
    }
    /** Avoid entry/key allocation for region occupancy checks. */
    @SuppressWarnings("unchecked") boolean anyMatch(Predicate<? super V> predicate) {
        for(var value:values)if(value!=null&&predicate.test((V)value))return true;
        return false;
    }
    @Override public Set<Entry<Integer,V>> entrySet() {
        return Collections.unmodifiableSet(new AbstractSet<>() {
            public int size(){return RegionCellTable.this.size;}
            public Iterator<Entry<Integer,V>> iterator() {
                return new Iterator<>() {
                    final long expected=version;int index;
                    private void check(){if(version!=expected)throw new ConcurrentModificationException("Region cell table changed");}
                    private void seek(){while(index<values.length&&values[index]==null)index++;}
                    public boolean hasNext(){check();seek();return index<values.length;}
                    @SuppressWarnings("unchecked") public Entry<Integer,V> next() {
                        check();seek();if(index==values.length)throw new NoSuchElementException();
                        int i=index++;return Map.entry(dense?i:Short.toUnsignedInt(keys[i]),(V)values[i]);
                    }
                };
            }
        });
    }
}
