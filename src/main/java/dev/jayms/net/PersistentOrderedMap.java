package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.util.*;

/** Owner-mutated insertion-ordered map with immutable hash and order roots. Values must be
 * immutable or protected separately by copy-on-write ownership. Copies share roots;
 * edits copy bounded hash paths and logarithmic order/collision paths. This container
 * defines no file or wire format and is not a persistence transaction. */
final class PersistentOrderedMap<V> extends AbstractMap<String,V> {
    // Coordinate is only a trusted in-process adapter for the string hash. Actual
    // key equality and full hash collisions remain in the immutable bucket tree.
    private ChunkDirectory<Node<V>> keys;
    private Node<V> order;
    private int entries;
    private long nextOrdinal,version;
    PersistentOrderedMap() {keys=new ChunkDirectory<>(Integer.MAX_VALUE);}
    PersistentOrderedMap(PersistentOrderedMap<V> source) {
        keys=source.keys;order=source.order;entries=source.entries;nextOrdinal=source.nextOrdinal;
    }
    private static final class Item<V> implements Entry<String,V> {
        final String key;final V value;final long ordinal;
        Item(String key,V value,long ordinal){this.key=key;this.value=value;this.ordinal=ordinal;}
        public String getKey(){return key;}
        public V getValue(){return value;}
        public V setValue(V value){throw new UnsupportedOperationException();}
        @Override public boolean equals(Object other){return other instanceof Entry<?,?> e&&Objects.equals(key,e.getKey())&&Objects.equals(value,e.getValue());}
        @Override public int hashCode(){return Objects.hashCode(key)^Objects.hashCode(value);}
        @Override public String toString(){return key+"="+value;}
    }
    private static final class Node<V> {
        final Item<V> item;final Node<V> left,right;final int height,size;
        Node(Item<V> item,Node<V> left,Node<V> right) {
            this.item=item;this.left=left;this.right=right;
            height=1+Math.max(height(left),height(right));size=1+size(left)+size(right);
        }
    }
    private static int height(Node<?> n){return n==null?0:n.height;}
    private static int size(Node<?> n){return n==null?0:n.size;}
    private static <V> Node<V> left(Node<V> n) {
        var r=n.right;return new Node<>(r.item,new Node<>(n.item,n.left,r.left),r.right);
    }
    private static <V> Node<V> right(Node<V> n) {
        var l=n.left;return new Node<>(l.item,l.left,new Node<>(n.item,l.right,n.right));
    }
    private static <V> Node<V> balance(Node<V> n) {
        int delta=height(n.left)-height(n.right);
        if(delta>1){if(height(n.left.left)<height(n.left.right))n=new Node<>(n.item,left(n.left),n.right);return right(n);}
        if(delta< -1){if(height(n.right.right)<height(n.right.left))n=new Node<>(n.item,n.left,right(n.right));return left(n);}
        return n;
    }
    private static int compare(Item<?> a,Item<?> b,boolean ordered){return ordered?Long.compare(a.ordinal,b.ordinal):a.key.compareTo(b.key);}
    private static <V> Node<V> put(Node<V> n,Item<V> item,boolean ordered) {
        if(n==null)return new Node<>(item,null,null);
        int c=compare(item,n.item,ordered);
        if(c==0)return new Node<>(item,n.left,n.right);
        return balance(c<0?new Node<>(n.item,put(n.left,item,ordered),n.right):new Node<>(n.item,n.left,put(n.right,item,ordered)));
    }
    private static <V> Node<V> remove(Node<V> n,Item<V> item,boolean ordered) {
        if(n==null)return null;
        int c=compare(item,n.item,ordered);
        if(c<0)return balance(new Node<>(n.item,remove(n.left,item,ordered),n.right));
        if(c>0)return balance(new Node<>(n.item,n.left,remove(n.right,item,ordered)));
        if(n.left==null)return n.right;if(n.right==null)return n.left;
        var successor=n.right;while(successor.left!=null)successor=successor.left;
        return balance(new Node<>(successor.item,n.left,remove(n.right,successor.item,ordered)));
    }
    private Item<V> find(Object key) {
        if(!(key instanceof String s))return null;
        var n=keys.get(s.hashCode(),0,0);while(n!=null){int c=s.compareTo(n.item.key);if(c==0)return n.item;n=c<0?n.left:n.right;}return null;
    }
    @Override public V get(Object key){var found=find(key);return found==null?null:found.value;}
    @Override public boolean containsKey(Object key){return find(key)!=null;}
    @Override public int size(){return entries;}
    @Override public V put(String key,V value) {
        Objects.requireNonNull(key);Objects.requireNonNull(value);var old=find(key);
        if(old==null&&entries==Integer.MAX_VALUE)throw new IllegalStateException("History map capacity exhausted");
        if(old==null&&nextOrdinal==Long.MAX_VALUE)throw new IllegalStateException("History insertion ordinal exhausted");
        var item=new Item<>(old==null?key:old.key,value,old==null?nextOrdinal:old.ordinal);
        // Prepare both roots before changing either visible field.
        int hash=key.hashCode();var nextKeys=keys.with(new ChunkPos(hash,0,0),put(keys.get(hash,0,0),item,false));
        var nextOrder=put(order,item,true);
        keys=nextKeys;order=nextOrder;if(old==null){nextOrdinal++;entries++;}version++;
        return old==null?null:old.value;
    }
    @Override public V remove(Object key) {
        var old=find(key);if(old==null)return null;
        int hash=old.key.hashCode();var bucket=remove(keys.get(hash,0,0),old,false);var position=new ChunkPos(hash,0,0);
        var nextKeys=bucket==null?keys.without(position):keys.with(position,bucket);var nextOrder=remove(order,old,true);
        keys=nextKeys;order=nextOrder;entries--;version++;return old.value;
    }
    @Override public void clear(){if(entries!=0)version++;keys=keys.cleared();order=null;entries=0;nextOrdinal=0;}
    @Override public Set<Entry<String,V>> entrySet() {
        return Collections.unmodifiableSet(new AbstractSet<>() {
            public int size(){return PersistentOrderedMap.this.size();}
            public boolean contains(Object value){if(!(value instanceof Entry<?,?> e))return false;var found=find(e.getKey());return found!=null&&Objects.equals(found.value,e.getValue());}
            public Iterator<Entry<String,V>> iterator() {
                return new Iterator<>() {
                    final long expected=version;final ArrayDeque<Node<V>> stack=new ArrayDeque<>();
                    {push(order);}
                    private void check(){if(expected!=version)throw new ConcurrentModificationException("History map changed during iteration");}
                    private void push(Node<V> n){while(n!=null){stack.push(n);n=n.left;}}
                    public boolean hasNext(){check();return !stack.isEmpty();}
                    public Entry<String,V> next(){check();if(stack.isEmpty())throw new NoSuchElementException();var n=stack.pop();push(n.right);return n.item;}
                };
            }
        });
    }
    /** Test/diagnostic invariant walk; never called by a live query or commit. */
    void validate() {
        keys.validate();long indexed=0;
        for(var bucket:keys){validate(bucket.getValue(),null,null,false);indexed+=size(bucket.getValue());}
        validate(order,null,null,true);
        if(indexed!=entries||entries!=size(order))throw new IllegalStateException("History index size mismatch");
        for(var e:entrySet()){var item=(Item<V>)e;if(find(item.key)!=item)throw new IllegalStateException("History index identity mismatch");}
    }
    private static <V> void validate(Node<V> n,Item<V> lower,Item<V> upper,boolean ordered) {
        if(n==null)return;
        if(lower!=null&&compare(lower,n.item,ordered)>=0||upper!=null&&compare(n.item,upper,ordered)>=0
                ||n.height!=1+Math.max(height(n.left),height(n.right))||n.size!=1+size(n.left)+size(n.right)
                ||Math.abs(height(n.left)-height(n.right))>1)throw new IllegalStateException("Invalid history index tree");
        validate(n.left,lower,n.item,ordered);validate(n.right,n.item,upper,ordered);
    }
}
