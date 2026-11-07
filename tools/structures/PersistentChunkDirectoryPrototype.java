import dev.jayms.ChunkPos;
import java.util.*;

/** Private immutable AVL directory experiment, not installed in WorldVoxels.
 * Values must be immutable or separately copy-on-write. A root is a stable snapshot;
 * callers must bound retained roots and publish them under their authority lock. */
public final class PersistentChunkDirectoryPrototype<V> implements Iterable<Map.Entry<ChunkPos,V>> {
    private static final int MAX_ENTRIES=1_048_576;
    private record Node<V>(ChunkPos key,V value,Node<V> left,Node<V> right,int height,int size) {}
    private final Node<V> root;
    private final int budget;
    public PersistentChunkDirectoryPrototype(int budget) {
        if(budget<1||budget>MAX_ENTRIES)throw new IllegalArgumentException("Directory entry budget");
        this.budget=budget;root=null;
    }
    private PersistentChunkDirectoryPrototype(Node<V> root,int budget){this.root=root;this.budget=budget;}
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
    public PersistentChunkDirectoryPrototype<V> with(ChunkPos key,V value) {
        Objects.requireNonNull(key);Objects.requireNonNull(value);
        if(size()==budget&&get(key)==null)throw new IllegalArgumentException("Directory entry budget exceeded");
        var next=put(root,key,value);return next==root?this:new PersistentChunkDirectoryPrototype<>(next,budget);
    }
    private static <V> Node<V> put(Node<V> n,ChunkPos key,V value) {
        if(n==null)return node(key,value,null,null);
        int c=compare(key,n.key);if(c==0)return n.value==value?n:node(n.key,value,n.left,n.right);
        if(c<0){var l=put(n.left,key,value);return l==n.left?n:balance(node(n.key,n.value,l,n.right));}
        var r=put(n.right,key,value);return r==n.right?n:balance(node(n.key,n.value,n.left,r));
    }
    public PersistentChunkDirectoryPrototype<V> without(ChunkPos key) {
        Objects.requireNonNull(key);var next=remove(root,key);return next==root?this:new PersistentChunkDirectoryPrototype<>(next,budget);
    }
    private static <V> Node<V> remove(Node<V> n,ChunkPos key) {
        if(n==null)return null;int c=compare(key,n.key);
        if(c<0){var l=remove(n.left,key);return l==n.left?n:balance(node(n.key,n.value,l,n.right));}
        if(c>0){var r=remove(n.right,key);return r==n.right?n:balance(node(n.key,n.value,n.left,r));}
        if(n.left==null)return n.right;if(n.right==null)return n.left;
        var successor=n.right;while(successor.left!=null)successor=successor.left;
        return balance(node(successor.key,successor.value,n.left,remove(n.right,successor.key)));
    }
    public PersistentChunkDirectoryPrototype<V> cleared(){return root==null?this:new PersistentChunkDirectoryPrototype<>(null,budget);}
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
