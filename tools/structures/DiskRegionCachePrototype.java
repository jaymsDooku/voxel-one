import dev.jayms.net.structure.StructurePackage;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Private, single-worker read cache. Not a live world, importer or admission path.
 * The index and its decoded file must remain owned and unchanged until close.
 * Limits charge encoded region bytes, not total Java heap or retained object size. */
public final class DiskRegionCachePrototype implements AutoCloseable {
    private record Page(Map<Integer,StructurePackage.Cell> cells,long bytes) {}
    private final DiskPackageIndexPrototype index;
    private final Thread reader=Thread.currentThread();
    private final Map<Integer,Integer> sections=new HashMap<>();
    private final LinkedHashMap<Integer,Page> pages=new LinkedHashMap<>(16,0.75f,true);
    private final int maxPages;
    private final long maxBytes;
    private long bytes,loads;
    private boolean closed;

    /** Does not own/close index. One page is at most 4096 cells and maxBytes encoded bytes. */
    public DiskRegionCachePrototype(DiskPackageIndexPrototype index,int maxPages,long maxBytes) {
        this.index=Objects.requireNonNull(index);
        if(maxPages<1||maxPages>128||maxBytes<1||maxBytes>8L*1024*1024)
            throw new IllegalArgumentException("Diagnostic cache budget");
        this.maxPages=maxPages;this.maxBytes=maxBytes;
        for(int i=0;i<index.regions.size();i++)
            if(sections.put(index.regions.get(i).section(),i)!=null)
                throw new IllegalArgumentException("Duplicate region index");
    }
    private void check(BooleanSupplier cancel)throws IOException {
        if(Thread.currentThread()!=reader)throw new IllegalStateException("Original cache reader required");
        if(closed)throw new IOException("Region cache closed");
        DiskPackageIndexPrototype.check(Objects.requireNonNull(cancel));
    }
    /** Missing source cells stay absent. Callers choose their terrain/overlap policy.
     * Returned geometry is immutable and survives eviction; retaining results is caller memory. */
    public Optional<StructurePackage.Cell> cell(int x,int y,int z,BooleanSupplier cancel)throws IOException {
        check(cancel);
        if(x<0||y<0||z<0||x>=index.bounds.x()||y>=index.bounds.y()||z>=index.bounds.z())
            throw new IllegalArgumentException("Cell outside package bounds");
        int section=((y/16)*64+z/16)*64+x/16;
        Integer number=sections.get(section);if(number==null)return Optional.empty();
        var page=pages.get(section);
        if(page==null) {
            var region=index.regions.get(number);
            if(region.cells()<1||region.cells()>4096||region.bytes()>maxBytes)
                throw new IOException("Diagnostic region cache budget");
            // Construct and verify fully before cache mutation or exposing any cell.
            var cells=new HashMap<Integer,StructurePackage.Cell>();
            try{index.visitRegion(number,c->{
                DiskPackageIndexPrototype.check(cancel);
                try {
                    var frozen=c.model()<0?new StructurePackage.Cell(c.position(),c.geometry().packedCellCopy(),-1):c;
                    int key=key(c.position().x(),c.position().y(),c.position().z());
                    if(cells.put(key,frozen)!=null)throw new IllegalStateException("Duplicate cached cell");
                }catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
            },cancel);}catch(java.io.UncheckedIOException failure){throw failure.getCause();}
            check(cancel);
            page=new Page(Map.copyOf(cells),region.bytes());
            while(pages.size()>=maxPages||page.bytes()>maxBytes-bytes) {
                var oldest=pages.entrySet().iterator();var victim=oldest.next();
                bytes-=victim.getValue().bytes();oldest.remove();
            }
            pages.put(section,page);bytes+=page.bytes();loads++;
        }
        check(cancel);return Optional.ofNullable(page.cells().get(key(x,y,z)));
    }
    private static int key(int x,int y,int z){return (y&15)*256+(z&15)*16+(x&15);}
    public int residentPages(){return pages.size();}
    public long residentEncodedBytes(){return bytes;}
    public long successfulLoads(){return loads;}
    @Override public void close() {
        if(Thread.currentThread()!=reader)throw new IllegalStateException("Original cache reader required");
        pages.clear();bytes=0;closed=true;
    }
}
