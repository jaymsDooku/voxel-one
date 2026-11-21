package dev.jayms.net;

import dev.jayms.ChunkPos;
import java.util.*;
import java.util.function.ToIntFunction;

/** Immutable chunk directory. Values require their own immutable/COW protocol.
 * Root retention shares nodes; callers bound retained roots and authorize publication. */
public final class ChunkDirectory<V> implements Iterable<Map.Entry<ChunkPos,V>> {
    private interface Node<V> {}
    private record Leaf<V>(int hash,ChunkPos key,V value) implements Node<V> {}
    private record Branch<V>(int bitmap,Node<V>[] children) implements Node<V> {}
    private record Collision<V>(int hash,CollisionTree<V> entries) implements Node<V> {}
    private final Node<V> root;
    private final int size,budget;
    private static final ToIntFunction<ChunkPos> DEFAULT_HASH=ChunkDirectory::hash;
    private final ToIntFunction<ChunkPos> hash;
    private final boolean defaultHash;
    public ChunkDirectory(int budget){this(budget,DEFAULT_HASH);}
    /** Trusted deterministic hash policy. Never accept one from package input. */
    public ChunkDirectory(int budget,ToIntFunction<ChunkPos> hash) {
        if(budget<1)throw new IllegalArgumentException("Directory entry budget");
        this.hash=Objects.requireNonNull(hash);defaultHash=hash==DEFAULT_HASH;this.budget=budget;root=null;size=0;
    }
    private ChunkDirectory(Node<V> root,int size,ChunkDirectory<V> source){this.root=root;this.size=size;budget=source.budget;hash=source.hash;defaultHash=source.defaultHash;}
    private static int hash(ChunkPos key){return hash(key.chunkX(),key.chunkY(),key.chunkZ());}
    private static int hash(int x,int y,int z) {
        long h=Long.rotateLeft(0x9e3779b185ebca87L^Integer.toUnsignedLong(x),27)*0xc2b2ae3d27d4eb4fL;
        h=Long.rotateLeft(h^Integer.toUnsignedLong(y),31)*0x165667b19e3779f9L;
        h=Long.rotateLeft(h^Integer.toUnsignedLong(z),33)*0x9e3779b185ebca87L;
        h^=h>>>33;h*=0xff51afd7ed558ccdL;h^=h>>>33;return (int)(h^(h>>>32));
    }
    private static int bit(int hash,int shift){if(shift>30)throw new AssertionError("Trie depth");return 1<<((hash>>>shift)&31);}
    private static int index(int bitmap,int bit){return Integer.bitCount(bitmap&(bit-1));}
    @SuppressWarnings("unchecked") private static <V> Node<V>[] array(int size){return (Node<V>[])new Node<?>[size];}
    public int size(){return size;}
    public V get(ChunkPos key) {
        Objects.requireNonNull(key);return find(hash.applyAsInt(key),key.chunkX(),key.chunkY(),key.chunkZ());
    }
    /** Coordinate lookup avoids a temporary key for ordinary voxel queries. */
    public V get(int x,int y,int z){return find(defaultHash?hash(x,y,z):hash.applyAsInt(new ChunkPos(x,y,z)),x,y,z);}
    private V find(int h,int x,int y,int z) {
        int shift=0;var n=root;
        while(n!=null) {
            if(n instanceof Leaf<V> l)return l.hash==h&&l.key.chunkX()==x&&l.key.chunkY()==y&&l.key.chunkZ()==z?l.value:null;
            if(n instanceof Collision<V> c)return c.hash==h?c.entries.get(new ChunkPos(x,y,z)):null;
            var b=(Branch<V>)n;int bit=bit(h,shift);if((b.bitmap&bit)==0)return null;n=b.children[index(b.bitmap,bit)];shift+=5;
        }
        return null;
    }
    public ChunkDirectory<V> with(ChunkPos key,V value) {
        Objects.requireNonNull(key);Objects.requireNonNull(value);V old=get(key);
        if(old==value)return this;
        if(old==null&&size==budget)throw new IllegalArgumentException("Directory entry budget exceeded");
        return new ChunkDirectory<>(put(root,hash.applyAsInt(key),key,value,0),size+(old==null?1:0),this);
    }
    private Node<V> put(Node<V> n,int h,ChunkPos key,V value,int shift) {
        if(n==null)return new Leaf<>(h,key,value);
        if(n instanceof Leaf<V> l) {
            if(l.hash==h) {
                if(l.key.equals(key))return l.value==value?l:new Leaf<>(h,key,value);
                var entries=new CollisionTree<V>(budget).with(l.key,l.value).with(key,value);return new Collision<>(h,entries);
            }
            return join(l,l.hash,new Leaf<>(h,key,value),h,shift);
        }
        if(n instanceof Collision<V> c) {
            if(c.hash==h)return new Collision<>(h,c.entries.with(key,value));
            return join(c,c.hash,new Leaf<>(h,key,value),h,shift);
        }
        var b=(Branch<V>)n;int bit=bit(h,shift),i=index(b.bitmap,bit);
        if((b.bitmap&bit)==0) {
            var next=ChunkDirectory.<V>array(b.children.length+1);System.arraycopy(b.children,0,next,0,i);next[i]=new Leaf<>(h,key,value);System.arraycopy(b.children,i,next,i+1,b.children.length-i);return new Branch<>(b.bitmap|bit,next);
        }
        var next=b.children.clone();next[i]=put(next[i],h,key,value,shift+5);return new Branch<>(b.bitmap,next);
    }
    private Node<V> join(Node<V> a,int ah,Node<V> b,int bh,int shift) {
        int ab=bit(ah,shift),bb=bit(bh,shift);
        if(ab==bb){var next=ChunkDirectory.<V>array(1);next[0]=join(a,ah,b,bh,shift+5);return new Branch<>(ab,next);}
        var next=ChunkDirectory.<V>array(2);int first=Integer.compareUnsigned(ab,bb)<0?0:1;next[first]=a;next[1-first]=b;return new Branch<>(ab|bb,next);
    }
    public ChunkDirectory<V> without(ChunkPos key) {
        Objects.requireNonNull(key);if(get(key)==null)return this;
        return new ChunkDirectory<>(remove(root,hash.applyAsInt(key),key,0),size-1,this);
    }
    private Node<V> remove(Node<V> n,int h,ChunkPos key,int shift) {
        if(n instanceof Leaf<V> l)return l.hash==h&&l.key.equals(key)?null:l;
        if(n instanceof Collision<V> c) {
            if(c.hash!=h)return c;var entries=c.entries.without(key);
            if(entries.size()==1){var e=entries.iterator().next();return new Leaf<>(h,e.getKey(),e.getValue());}
            return new Collision<>(h,entries);
        }
        var b=(Branch<V>)n;int bit=bit(h,shift),i=index(b.bitmap,bit);if((b.bitmap&bit)==0)return b;
        var child=remove(b.children[i],h,key,shift+5);Node<V>[] next;int bitmap=b.bitmap;
        if(child==null){next=array(b.children.length-1);System.arraycopy(b.children,0,next,0,i);System.arraycopy(b.children,i+1,next,i,next.length-i);bitmap&=~bit;}
        else{next=b.children.clone();next[i]=child;}
        if(next.length==0)return null;
        // A remaining branch still consumes this level's hash bits; do not lift it.
        if(next.length==1&&!(next[0] instanceof Branch<?>))return next[0];
        return new Branch<>(bitmap,next);
    }
    public ChunkDirectory<V> cleared(){return size==0?this:new ChunkDirectory<>(null,0,this);}
    @Override public Iterator<Map.Entry<ChunkPos,V>> iterator() {
        return new Iterator<>() {
            private final ArrayDeque<Node<V>> pending=new ArrayDeque<>();
            private Iterator<Map.Entry<ChunkPos,V>> collision=Collections.emptyIterator();
            {if(root!=null)pending.push(root);}
            public boolean hasNext(){return collision.hasNext()||!pending.isEmpty();}
            public Map.Entry<ChunkPos,V> next() {
                if(collision.hasNext())return collision.next();
                while(!pending.isEmpty()) {
                    var n=pending.pop();if(n instanceof Leaf<V> l)return Map.entry(l.key,l.value);
                    if(n instanceof Collision<V> c){collision=c.entries.iterator();return collision.next();}
                    var b=(Branch<V>)n;for(int i=b.children.length-1;i>=0;i--)pending.push(b.children[i]);
                }
                throw new NoSuchElementException();
            }
        };
    }
    /** Diagnostic only. A hash path has at most seven branches; collision AVL work
     * is additional, bounded by the entry budget. No wall-time/memory claim. */
    public int trieDepth(){return depth(root);}
    /** Diagnostic only; count entries routed through full-hash collision trees. */
    public int collisionEntries(){return collisionEntries(root);}
    private static int collisionEntries(Node<?> n) {
        if(n instanceof Collision<?> c)return c.entries.size();
        if(!(n instanceof Branch<?> b))return 0;
        int count=0;for(var child:b.children)count+=collisionEntries(child);return count;
    }
    private static int depth(Node<?> n){if(!(n instanceof Branch<?> b))return 0;int max=0;for(var child:b.children)max=Math.max(max,depth(child));return max+1;}
    /** Diagnostic only: verify structure and every leaf's route against actual lookups. */
    public void validate() {
        validate(root,0);int count=0;for(var e:this){if(e.getValue()!=get(e.getKey()))throw new AssertionError("Trie route");count++;}if(count!=size)throw new AssertionError("Trie size");
    }
    private void validate(Node<V> n,int shift) {
        if(n==null)return;
        if(n instanceof Leaf<V> l){if(l.hash!=hash.applyAsInt(l.key))throw new AssertionError("Leaf hash");return;}
        if(n instanceof Collision<V> c){if(c.entries.size()<2)throw new AssertionError("Collision count");c.entries.validate();for(var e:c.entries)if(hash.applyAsInt(e.getKey())!=c.hash)throw new AssertionError("Collision hash");return;}
        var b=(Branch<V>)n;
        if(shift>30||b.children.length==0||Integer.bitCount(b.bitmap)!=b.children.length||b.children.length==1&&!(b.children[0] instanceof Branch<?>))throw new AssertionError("Trie branch");
        for(var child:b.children){if(child==null)throw new AssertionError("Null child");validate(child,shift+5);}
    }

    // Full-hash collisions retain logarithmic coordinate lookup rather than a list.
    private static final class CollisionTree<V> implements Iterable<Map.Entry<ChunkPos,V>> {
        private static final int MAX_ENTRIES=Integer.MAX_VALUE;
        private record Node<V>(ChunkPos key,V value,Node<V> left,Node<V> right,int height,int size) {}
        private final Node<V> root;
        private final int budget;
        public CollisionTree(int budget) {
            if(budget<1||budget>MAX_ENTRIES)throw new IllegalArgumentException("Directory entry budget");
            this.budget=budget;root=null;
        }
        private CollisionTree(Node<V> root,int budget){this.root=root;this.budget=budget;}
        private static int height(Node<?> n){return n==null?0:n.height;}
        private static int size(Node<?> n){return n==null?0:n.size;}
        private static int compare(ChunkPos a,ChunkPos b) {
            int c=Integer.compare(a.chunkX(),b.chunkX());if(c!=0)return c;
            c=Integer.compare(a.chunkY(),b.chunkY());return c!=0?c:Integer.compare(a.chunkZ(),b.chunkZ());
        }
        private static <V> Node<V> node(ChunkPos k,V v,Node<V> l,Node<V> r){return new Node<>(k,v,l,r,1+Math.max(height(l),height(r)),1+size(l)+size(r));}
        private static <V> Node<V> left(Node<V> n){var r=n.right;return node(r.key,r.value,node(n.key,n.value,n.left,r.left),r.right);}
        private static <V> Node<V> right(Node<V> n){var l=n.left;return node(l.key,l.value,l.left,node(n.key,n.value,l.right,n.right));}
        private static <V> Node<V> balance(Node<V> n) {
            if(height(n.left)-height(n.right)>1) {
                if(height(n.left.left)<height(n.left.right))n=node(n.key,n.value,left(n.left),n.right);
                return right(n);
            }
            if(height(n.right)-height(n.left)>1) {
                if(height(n.right.right)<height(n.right.left))n=node(n.key,n.value,n.left,right(n.right));
                return left(n);
            }
            return n;
        }
        public int size(){return size(root);}
        public int height(){return height(root);}
        public V get(ChunkPos key) {
            Objects.requireNonNull(key);
            for(var n=root;n!=null;) {int c=compare(key,n.key);if(c==0)return n.value;n=c<0?n.left:n.right;}
            return null;
        }
        /** Returns a new root; never mutates this root or its values. Capacity is checked
         * before allocating an insertion path. Replacing an existing key at capacity works. */
        public CollisionTree<V> with(ChunkPos key,V value) {
            Objects.requireNonNull(key);Objects.requireNonNull(value);
            if(size()==budget&&get(key)==null)throw new IllegalArgumentException("Directory entry budget exceeded");
            var next=put(root,key,value);return next==root?this:new CollisionTree<>(next,budget);
        }
        private static <V> Node<V> put(Node<V> n,ChunkPos key,V value) {
            if(n==null)return node(key,value,null,null);
            int c=compare(key,n.key);if(c==0)return n.value==value?n:node(n.key,value,n.left,n.right);
            if(c<0){var l=put(n.left,key,value);return l==n.left?n:balance(node(n.key,n.value,l,n.right));}
            var r=put(n.right,key,value);return r==n.right?n:balance(node(n.key,n.value,n.left,r));
        }
        public CollisionTree<V> without(ChunkPos key) {
            Objects.requireNonNull(key);var next=remove(root,key);return next==root?this:new CollisionTree<>(next,budget);
        }
        private static <V> Node<V> remove(Node<V> n,ChunkPos key) {
            if(n==null)return null;int c=compare(key,n.key);
            if(c<0){var l=remove(n.left,key);return l==n.left?n:balance(node(n.key,n.value,l,n.right));}
            if(c>0){var r=remove(n.right,key);return r==n.right?n:balance(node(n.key,n.value,n.left,r));}
            if(n.left==null)return n.right;if(n.right==null)return n.left;
            var successor=n.right;while(successor.left!=null)successor=successor.left;
            return balance(node(successor.key,successor.value,n.left,remove(n.right,successor.key)));
        }
        public CollisionTree<V> cleared(){return root==null?this:new CollisionTree<>(null,budget);}
        /** Stable sorted traversal of this root; at most height nodes retained by iterator. */
        @Override public Iterator<Map.Entry<ChunkPos,V>> iterator() {
            return new Iterator<>() {
                final ArrayDeque<Node<V>> stack=new ArrayDeque<>();
                {push(root);}
                private void push(Node<V> n){while(n!=null){stack.push(n);n=n.left;}}
                public boolean hasNext(){return !stack.isEmpty();}
                public Map.Entry<ChunkPos,V> next(){if(stack.isEmpty())throw new NoSuchElementException();var n=stack.pop();push(n.right);return Map.entry(n.key,n.value);}
            };
        }
        /** Exhaustive private diagnostic, not an owner-path operation. */
        public void validate(){validate(root,null,null);}
        private static void validate(Node<?> n,ChunkPos low,ChunkPos high) {
            if(n==null)return;
            if(low!=null&&compare(n.key,low)<=0||high!=null&&compare(n.key,high)>=0||Math.abs(height(n.left)-height(n.right))>1
                    ||n.height!=1+Math.max(height(n.left),height(n.right))||n.size!=1+size(n.left)+size(n.right))throw new AssertionError("Directory invariant");
            validate(n.left,low,n.key);validate(n.right,n.key,high);
        }
    }
}
