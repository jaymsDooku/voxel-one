package dev.jayms.render;

import dev.jayms.*;
import dev.jayms.net.Protocol;
import dev.jayms.net.model.ModelLibrary;
import java.util.*;
import java.util.concurrent.*;

/** Cached 16x16 sky tiles. Immutable preparation shares the bounded terrain CPU lane. */
public final class SkyVisibilityScheduler implements AutoCloseable {
    private static final int BATCH=4;
    private record Ticket(World owner,ChunkPos column,List<ChunkPos> positions,List<Chunk> chunks,long[] revisions,long edits,long models) {
        boolean valid(World world) {
            if(world!=owner||world.models().revision()!=models||world.skyEditRevision(column)!=edits||world.skyMembers(column).size()!=positions.size()||!world.skyColumns().contains(column))return false;
            for(int i=0;i<positions.size();i++)if(world.getLoadedChunks().get(positions.get(i))!=chunks.get(i)||chunks.get(i).geometryVersion()!=revisions[i])return false;
            return true;
        }
    }
    public static final class Prepared {
        public final WorldSkyVisibility data;
        private final List<Ticket> tickets;
        private final List<Tile> tiles;
        private record Tile(ChunkPos column,WorldSkyVisibility data) {}
        public Object localKey(float x,float z){
            int cx=Math.floorDiv((int)Math.floor(x),16)*16,cz=Math.floorDiv((int)Math.floor(z),16)*16;
            var result=new ArrayList<Tile>();for(var tile:tiles){int tx=tile.column.chunkX()*16,tz=tile.column.chunkZ()*16;if(tx+16>cx-48&&tx<cx+48&&tz+16>cz-48&&tz<cz+48)result.add(tile);}
            result.sort(Comparator.<Tile>comparingInt(t->t.column.chunkX()).thenComparingInt(t->t.column.chunkZ()));return result;
        }
        private World checked;private long stamp=Long.MIN_VALUE,models=Long.MIN_VALUE;
        private Prepared(WorldSkyVisibility data,List<Cached> parts){this.data=data;this.tickets=parts.stream().map(Cached::ticket).toList();this.tiles=parts.stream().map(c->new Tile(c.ticket.column,c.data)).toList();}
        public boolean valid(World world){
            if(checked==world&&stamp==world.skyStamp()&&models==world.models().revision())return true;
            for(var ticket:tickets)if(!ticket.valid(world))return false;
            checked=world;stamp=world.skyStamp();models=world.models().revision();return true;
        }
    }
    private record Cached(Ticket ticket,WorldSkyVisibility data) {}
    private static final class Capture {
        final Ticket ticket;final ModelLibrary models;final Iterator<Protocol.Edit> edits;
        final List<WorldSkyVisibility.Geometry> chunks=new ArrayList<>();
        final List<WorldSkyVisibility.EditedCell> cells=new ArrayList<>();
        final Set<String> seen=new HashSet<>();int chunk;
        Capture(World w,ChunkPos column,ModelLibrary models) {
            var positions=new ArrayList<>(w.skyMembers(column));var references=new ArrayList<Chunk>();long[] revisions=new long[positions.size()];
            for(int i=0;i<positions.size();i++){Chunk c=w.getLoadedChunks().get(positions.get(i));references.add(c);revisions[i]=c.geometryVersion();}
            ticket=new Ticket(w,column,positions,references,revisions,w.skyEditRevision(column),w.models().revision());
            this.models=models;edits=w.skyEdits(column);
        }
        boolean pump(World world,long deadline) {
            while(chunk<ticket.positions.size()&&System.nanoTime()<deadline) {
                chunks.add(new WorldSkyVisibility.Geometry(ticket.positions.get(chunk),ticket.chunks.get(chunk).snapshot()));chunk++;
            }
            while(chunk==ticket.positions.size()&&edits.hasNext()&&System.nanoTime()<deadline) {
                var e=edits.next();if(!world.isLoaded(e.x(),e.y(),e.z())&&seen.add(e.cellKey()))cells.add(new WorldSkyVisibility.EditedCell(e.x(),e.y(),e.z(),world.cell(e.x(),e.y(),e.z()).copy().freeze()));
            }
            return chunk==ticket.positions.size()&&!edits.hasNext();
        }
        WorldSkyVisibility.Input input(){return new WorldSkyVisibility.Input(List.copyOf(chunks),List.copyOf(cells),models);}
    }
    private final Map<ChunkPos,Cached> cache=new ConcurrentHashMap<>();
    private Iterator<ChunkPos> scan;
    private Iterator<Map.Entry<ChunkPos,Cached>> cleaning;
    private Capture capture;
    private final List<Ticket> tickets=new ArrayList<>();
    private final List<WorldSkyVisibility.Input> inputs=new ArrayList<>();
    private Future<List<WorldSkyVisibility>> pending;
    private Future<WorldSkyVisibility> packed;
    private WorldSkyVisibility lastProduced;
    private List<Cached> packingParts;
    private Iterator<Cached> packingIterator;
    private long generation,packingGeneration,submittedGeneration,bytes;
    private ModelLibrary models;private long modelRevision=Long.MIN_VALUE;
    private boolean dirty,closed;
    public long rejected,workerNanos,ownerNanos;
    private final java.util.concurrent.atomic.AtomicLong finishedWorkerNanos=new java.util.concurrent.atomic.AtomicLong();
    public int pending(){return (pending==null?0:1)+(packed==null?0:1)+(capture==null?0:1);}
    public long cachedBytes(){return bytes;}
    public SkyVisibilityScheduler(){}
    private void remove(ChunkPos p,Cached c){if(cache.remove(p,c)){bytes-=4L*c.data.data.length;generation++;dirty=true;}}
    private boolean current(World world,Cached c){return cache.get(c.ticket.column)==c&&c.ticket.valid(world);}
    private static boolean remoteUnloaded(World world,ChunkPos p,float x,float z) {
        return world.skyMembers(p).isEmpty()&&(Math.abs(p.chunkX()-Math.floorDiv((int)Math.floor(x),16))>6||Math.abs(p.chunkZ()-Math.floorDiv((int)Math.floor(z),16))>6);
    }
    public Prepared update(World world,long nanos){return update(world,0,0,nanos);}
    public Prepared update(World world,float x,float z,long nanos) {
        long start=System.nanoTime(),deadline=start+Math.max(0,nanos);Prepared ready=null;workerNanos=finishedWorkerNanos.getAndSet(0);
        if(closed)return null;
        try {
            if(packed!=null&&packed.isDone()) {
                var result=packed.get();packed=null;
                boolean valid=submittedGeneration==generation;
                if(valid)for(var c:packingParts)if(!current(world,c)){remove(c.ticket.column,c);valid=false;}
                if(valid){lastProduced=result;ready=new Prepared(result,packingParts);}else{rejected++;dirty=true;}
                packingParts=null;
            }
            if(pending!=null&&pending.isDone()) {
                var results=pending.get();pending=null;
                for(int i=0;i<results.size();i++) {
                    var ticket=tickets.get(i);if(!ticket.valid(world)){rejected++;continue;}
                    var next=new Cached(ticket,results.get(i));var old=cache.put(ticket.column,next);
                    bytes+=4L*next.data.data.length-(old==null?0:4L*old.data.data.length);generation++;dirty=true;
                }
                tickets.clear();inputs.clear();
                if(bytes>4L*WorldSkyVisibility.MAX_INTS)throw new IllegalStateException("Loaded sky visibility exceeds bounded buffer capacity");
            }
            if(cleaning==null||!cleaning.hasNext())cleaning=cache.entrySet().iterator();
            while(cleaning.hasNext()&&System.nanoTime()<deadline) {
                var e=cleaning.next();if(remoteUnloaded(world,e.getKey(),x,z)||!e.getValue().ticket.valid(world))remove(e.getKey(),e.getValue());
            }
            if(pending==null) {
                if(scan==null||!scan.hasNext())scan=world.skyColumns().iterator();
                while(tickets.size()<BATCH&&System.nanoTime()<deadline) {
                    if(capture==null) {
                        ChunkPos p=null;
                        // Reserve three batch slots for playable nearby columns; the fourth
                        // follows the persistent scan so distant/edited columns keep progressing.
                        if(tickets.size()<BATCH-1) {
                            int cx=Math.floorDiv((int)Math.floor(x),16),cz=Math.floorDiv((int)Math.floor(z),16);
                            near:for(int ring=0;ring<=1;ring++)for(int a=-ring;a<=ring;a++)for(int b=-ring;b<=ring;b++) {
                                if(Math.max(Math.abs(a),Math.abs(b))!=ring)continue;var candidate=new ChunkPos(cx+a,0,cz+b);
                                boolean queued=false;for(var t:tickets)if(t.column.equals(candidate)){queued=true;break;}
                                var previous=cache.get(candidate);
                                if(!queued&&world.skyColumns().contains(candidate)&&world.skyColumnReady(candidate)&&(previous==null||!previous.ticket.valid(world))){p=candidate;break near;}
                            }
                        }
                        if(p==null){if(!scan.hasNext())break;p=scan.next();}
                        if(remoteUnloaded(world,p,x,z))continue;
                        boolean queued=false;for(var t:tickets)if(t.column.equals(p)){queued=true;break;}if(queued)continue;
                        var old=cache.get(p);
                        if(old!=null&&old.ticket.valid(world))continue;
                        // Partial terrain columns keep distant-parent coverage and do not need sky tiles yet.
                        if(!world.skyColumnReady(p))continue;
                        if(modelRevision!=world.models().revision()){models=world.models().copy();modelRevision=world.models().revision();}
                        capture=new Capture(world,p,models);
                    }
                    if(!capture.ticket.valid(world)){capture=null;rejected++;continue;}
                    if(!capture.pump(world,deadline))break;
                    tickets.add(capture.ticket);inputs.add(capture.input());capture=null;
                }
                if(!inputs.isEmpty()&&(tickets.size()==BATCH||capture==null)) {
                    var work=List.copyOf(inputs);var timings=finishedWorkerNanos;
                    pending=world.prepareTerrainTask(()->{long then=System.nanoTime();var result=new ArrayList<WorldSkyVisibility>();for(var input:work)result.add(WorldSkyVisibility.build(input));timings.addAndGet(System.nanoTime()-then);return result;});
                }
            }
            if(packed==null&&dirty&&System.nanoTime()<deadline) {
                if(packingIterator==null||packingGeneration!=generation){packingGeneration=generation;packingParts=new ArrayList<>();packingIterator=cache.values().iterator();}
                while(packingIterator.hasNext()&&System.nanoTime()<deadline)packingParts.add(packingIterator.next());
                if(!packingIterator.hasNext()) {
                    var work=packingParts.stream().map(Cached::data).toList();var timings=finishedWorkerNanos;var previous=lastProduced;submittedGeneration=generation;dirty=false;packingIterator=null;
                    packed=world.prepareTerrainTask(()->{long then=System.nanoTime();var result=WorldSkyVisibility.merge(work);if(previous!=null&&Arrays.equals(previous.data,result.data))result=previous;timings.addAndGet(System.nanoTime()-then);return result;});
                }
            }
            return ready;
        }catch(InterruptedException e){Thread.currentThread().interrupt();return null;}
        catch(RejectedExecutionException e){dirty=true;return ready;}
        catch(ExecutionException e){throw new IllegalStateException("Sky visibility preparation failed",e.getCause());}
        finally{ownerNanos=System.nanoTime()-start;}
    }
    @Override public void close(){closed=true;if(pending!=null)pending.cancel(true);if(packed!=null)packed.cancel(true);pending=null;packed=null;capture=null;cache.clear();tickets.clear();inputs.clear();packingParts=null;packingIterator=null;lastProduced=null;models=null;scan=null;cleaning=null;bytes=0;}
}
