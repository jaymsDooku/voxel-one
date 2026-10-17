package dev.jayms.net;

import java.util.Arrays;
import java.util.Objects;

/** Fixed-capacity primitive-key LRU. Callers retain their existing synchronization. */
final class LongLruCache<V> {
    private final long[] keys;
    private final Object[] values;
    private final int[] table,previous,next;
    private final int mask;
    private int size,head=-1,tail=-1;
    LongLruCache(int capacity) {
        if(capacity<1||capacity>1_000_000)throw new IllegalArgumentException("Cache capacity");
        int slots=1;while(slots<capacity*2)slots<<=1;
        keys=new long[capacity];values=new Object[capacity];previous=new int[capacity];next=new int[capacity];table=new int[slots];mask=slots-1;
    }
    private int bucket(long key){key=(key^(key>>>33))*0xff51afd7ed558ccdl;key=(key^(key>>>33))*0xc4ceb9fe1a85ec53l;return (int)(key^(key>>>33))&mask;}
    private int find(long key){int slot=bucket(key);while(table[slot]!=0&&keys[table[slot]-1]!=key)slot=(slot+1)&mask;return slot;}
    private void unlink(int i){if(previous[i]<0)head=next[i];else next[previous[i]]=next[i];if(next[i]<0)tail=previous[i];else previous[next[i]]=previous[i];}
    private void first(int i){previous[i]=-1;next[i]=head;if(head>=0)previous[head]=i;else tail=i;head=i;}
    @SuppressWarnings("unchecked") V get(long key){int entry=table[find(key)]-1;if(entry<0)return null;if(entry!=head){unlink(entry);first(entry);}return (V)values[entry];}
    void put(long key,V value){
        Objects.requireNonNull(value);int slot=find(key),entry=table[slot]-1;
        if(entry>=0){values[entry]=value;if(entry!=head){unlink(entry);first(entry);}return;}
        if(size<values.length)entry=size++;
        else {
            entry=tail;int hole=find(keys[entry]);unlink(entry);
            for(int scan=(hole+1)&mask;table[scan]!=0;scan=(scan+1)&mask){
                int home=bucket(keys[table[scan]-1]);
                if(((scan-home)&mask)>=((scan-hole)&mask)){table[hole]=table[scan];hole=scan;}
            }table[hole]=0;slot=find(key);
        }
        keys[entry]=key;values[entry]=value;table[slot]=entry+1;first(entry);
    }
    int size(){return size;}
    void clear(){Arrays.fill(table,0);Arrays.fill(values,null);size=0;head=tail=-1;}
}
