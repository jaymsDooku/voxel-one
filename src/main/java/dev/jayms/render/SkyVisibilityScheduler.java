package dev.jayms.render;

import dev.jayms.*;
import dev.jayms.net.Protocol;
import dev.jayms.net.model.ModelLibrary;
import java.util.*;
import java.util.concurrent.*;

/** Cached 16x16 sky tiles. Immutable preparation shares the bounded terrain CPU lane. */
public final class SkyVisibilityScheduler implements AutoCloseable {
    private static final int BATCH=8;
    private record Ticket(World owner,ChunkPos column,List<ChunkPos> positions,List<Chunk> chunks,long[] revisions,long edits,long models,long geometry,Object terrainGrid,boolean completeTerrain) {
        boolean valid(World world) {
            if(world!=owner||world.isClosed()||world.terrain().stressGrid()!=terrainGrid||world.models().revision()!=models||world.skyEditRevision(column)!=edits||world.skyMembers(column).size()!=positions.size()||!world.skyColumns().contains(column))return false;
            for(int i=0;i<positions.size();i++)if(world.getLoadedChunks().get(positions.get(i))!=chunks.get(i)||chunks.get(i).geometryVersion()!=revisions[i])return false;
            return true;
        }
        boolean displayValid(World world){
            if(!completeTerrain)return valid(world);
            return world==owner&&!world.isClosed()&&world.terrain().stressGrid()==terrainGrid&&world.models().revision()==models&&world.skyEditRevision(column)==edits&&world.geometryRevision(column.chunkX(),column.chunkZ())==geometry;
        }
    }
    public static final class Prepared {
        public final WorldSkyVisibility data;
        public final long createdNanos=System.nanoTime();
        private final List<Ticket> tickets;
        private final Map<ChunkPos,Cached> localParts;
        private record Tile(ChunkPos column,WorldSkyVisibility data) {}
        public boolean belongsTo(World world){return !tickets.isEmpty()&&tickets.get(0).owner()==world;}
        public boolean localReady(World world,float x,float z){
            int cx=Math.floorDiv((int)Math.floor(x),16),cz=Math.floorDiv((int)Math.floor(z),16);
            for(int a=-3;a<3;a++)for(int b=-3;b<3;b++){
                var p=new ChunkPos(cx+a,0,cz+b);
                if(!world.skyColumns().contains(p)||!world.skyColumnReady(p))continue;
                var part=localParts.get(p);if(part==null||!part.valid(world))return false;
            }return true;
        }
        public Object localKey(float x,float z){
            int cx=Math.floorDiv((int)Math.floor(x),16)*16,cz=Math.floorDiv((int)Math.floor(z),16)*16;
            var result=new ArrayList<Tile>();for(int a=-3;a<3;a++)for(int b=-3;b<3;b++){var p=new ChunkPos(cx/16+a,0,cz/16+b);var part=localParts.get(p);if(part!=null)result.add(new Tile(p,part.data));}
            result.sort(Comparator.<Tile>comparingInt(t->t.column.chunkX()).thenComparingInt(t->t.column.chunkZ()));return result;
        }
        private World checked;private long stamp=Long.MIN_VALUE,models=Long.MIN_VALUE;
        private Prepared(WorldSkyVisibility data,List<Cached> parts){this.data=data;this.tickets=parts.stream().map(Cached::ticket).toList();this.localParts=new HashMap<>();for(var c:parts)localParts.put(c.ticket.column,c);}
        public boolean valid(World world){
            if(world.isClosed())return false;
            if(checked==world&&stamp==world.skyStamp()&&models==world.models().revision())return true;
            for(var ticket:tickets)if(!ticket.displayValid(world))return false;
            checked=world;stamp=world.skyStamp();models=world.models().revision();return true;
        }
    }
    /** GI samples sky at y96; below-boundary natural tiles are not dependencies. */
    public static boolean ceilingReady(Prepared prepared,World world,float x,float z){
        int cx=Math.floorDiv((int)Math.floor(x),16),cz=Math.floorDiv((int)Math.floor(z),16);
        for(int a=-3;a<3;a++)for(int b=-3;b<3;b++)if(world.requiresSkyCeiling(cx+a,cz+b)){
            if(prepared==null)return false;var part=prepared.localParts.get(new ChunkPos(cx+a,0,cz+b));if(part==null||!part.valid(world))return false;
        }return true;
    }
    public static Object ceilingKey(Prepared prepared,World world,float x,float z){
        int cx=Math.floorDiv((int)Math.floor(x),16),cz=Math.floorDiv((int)Math.floor(z),16);var result=new ArrayList<Prepared.Tile>();
        for(int a=-3;a<3;a++)for(int b=-3;b<3;b++)if(world.requiresSkyCeiling(cx+a,cz+b)){
            var p=new ChunkPos(cx+a,0,cz+b);var part=prepared==null?null:prepared.localParts.get(p);result.add(new Prepared.Tile(p,part==null?null:part.data));
        }return List.copyOf(result);
    }
    private static final class Cached {
        final Ticket ticket;final WorldSkyVisibility data;
        private World checked;private long stamp=Long.MIN_VALUE,models=Long.MIN_VALUE;
        Cached(Ticket ticket,WorldSkyVisibility data){this.ticket=ticket;this.data=data;}
        WorldSkyVisibility data(){return data;}
        Ticket ticket(){return ticket;}
        boolean valid(World w){
            if(checked==w&&stamp==w.skyStamp()&&models==w.models().revision())return true;
            if(!ticket.valid(w))return false;
            checked=w;stamp=w.skyStamp();models=w.models().revision();return true;
        }
    }
    private static final class Capture {
        final Ticket ticket;final ModelLibrary models;final Iterator<Protocol.Edit> edits;
        final List<WorldSkyVisibility.Geometry> chunks=new ArrayList<>();
        final List<WorldSkyVisibility.EditedCell> cells=new ArrayList<>();
        final Set<String> seen=new HashSet<>();int chunk;
        Capture(World w,ChunkPos column,ModelLibrary models) {
            var positions=new ArrayList<>(w.skyMembers(column));var references=new ArrayList<Chunk>();long[] revisions=new long[positions.size()];
            for(int i=0;i<positions.size();i++){Chunk c=w.getLoadedChunks().get(positions.get(i));references.add(c);revisions[i]=c.geometryVersion();}
            ticket=new Ticket(w,column,positions,references,revisions,w.skyEditRevision(column),w.models().revision(),w.geometryRevision(column.chunkX(),column.chunkZ()),w.terrain().stressGrid(),w.skyTerrainEquivalent(column));
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
    private long generation,packingGeneration,submittedGeneration,bytes,packingInts;
    private int packingLimit;private boolean submittedTruncated;
    private ModelLibrary models;private World modelWorld;private long modelRevision=Long.MIN_VALUE;
    private boolean dirty,closed;
    public long rejected,workerNanos,ownerNanos,published,partialPublications,nearChecks,scanChecks;
    public int cachedColumns(){return cache.size();}
    private final java.util.concurrent.atomic.AtomicLong finishedWorkerNanos=new java.util.concurrent.atomic.AtomicLong();
    public int pending(){return (pending==null?0:1)+(packed==null?0:1)+(capture==null?0:1)+((dirty||packingIterator!=null||!inputs.isEmpty())?1:0);}
    public long cachedBytes(){return bytes;}
    public SkyVisibilityScheduler(){}
    private void remove(ChunkPos p,Cached c){if(cache.remove(p,c)){bytes-=4L*c.data.data.length;generation++;dirty=true;}}
    private boolean current(World world,Cached c){return cache.get(c.ticket.column)==c&&c.valid(world);}
    private static boolean remoteUnloaded(World world,ChunkPos p,float x,float z) {
        return world.skyMembers(p).isEmpty()&&(Math.abs(p.chunkX()-Math.floorDiv((int)Math.floor(x),16))>6||Math.abs(p.chunkZ()-Math.floorDiv((int)Math.floor(z),16))>6);
    }
    public Prepared update(World world,long nanos){return update(world,0,0,nanos);}
    public Prepared update(World world,float x,float z,long nanos) {return update(world,x,z,nanos,false);}
    public Prepared update(World world,float x,float z,long nanos,boolean initial) {
        int batch=initial?BATCH*2:BATCH;
        long start=System.nanoTime(),deadline=start+Math.max(0,nanos);Prepared ready=null;nearChecks=scanChecks=0;workerNanos=finishedWorkerNanos.getAndSet(0);
        if(closed||world.isClosed())return null;
        try {
            if(packed!=null&&packed.isDone()) {
                var result=packed.get();packed=null;
                // New disjoint tiles do not make captured tiles stale. Fence each
                // captured dependency; leave newer additions dirty for the next merge.
                boolean valid=true;
                if(valid)for(var c:packingParts)if(!c.ticket.displayValid(world)){remove(c.ticket.column,c);valid=false;}
                if(valid){published++;if(submittedGeneration!=generation||submittedTruncated){partialPublications++;dirty=true;}lastProduced=result;ready=new Prepared(result,packingParts);}else{rejected++;dirty=true;}
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
            long maintenanceDeadline=Math.min(deadline,start+Math.max(0,nanos)/8);
            long captureDeadline=Math.min(deadline,start+Math.max(0,nanos)*3/4);
            if(cleaning==null||!cleaning.hasNext())cleaning=cache.entrySet().iterator();
            while(cleaning.hasNext()&&System.nanoTime()<maintenanceDeadline) {
                var e=cleaning.next();if(remoteUnloaded(world,e.getKey(),x,z)||!e.getValue().valid(world))remove(e.getKey(),e.getValue());
            }
            if(pending==null) {
                boolean nearExhausted=false;
                if(scan==null||!scan.hasNext())scan=world.skyColumns().iterator();
                while((capture!=null||tickets.size()<batch)&&System.nanoTime()<captureDeadline) {
                    if(capture==null) {
                        ChunkPos p=null;
                        // Reserve all but one batch slot for playable nearby columns; the last
                        // follows the persistent scan so distant/edited columns keep progressing.
                        if(!nearExhausted&&tickets.size()<batch-1) {
                            int cx=Math.floorDiv((int)Math.floor(x),16),cz=Math.floorDiv((int)Math.floor(z),16);
                            near:for(int ring=0;ring<=3;ring++)for(int a=-ring;a<=ring;a++)for(int b=-ring;b<=ring;b++) {
                                if(Math.max(Math.abs(a),Math.abs(b))!=ring||a==3||b==3)continue;nearChecks++;var candidate=new ChunkPos(cx+a,0,cz+b);
                                boolean queued=false;for(var t:tickets)if(t.column.equals(candidate)){queued=true;break;}
                                var previous=cache.get(candidate);
                                if(!queued&&world.skyColumns().contains(candidate)&&world.skyColumnReady(candidate)&&(previous==null||!previous.valid(world))){p=candidate;break near;}
                            }
                        }
                        if(p==null){nearExhausted=true;if(!scan.hasNext())break;p=scan.next();scanChecks++;}
                        if(remoteUnloaded(world,p,x,z))continue;
                        boolean queued=false;for(var t:tickets)if(t.column.equals(p)){queued=true;break;}if(queued)continue;
                        var old=cache.get(p);
                        if(old!=null&&old.valid(world))continue;
                        // Partial terrain columns keep distant-parent coverage and do not need sky tiles yet.
                        if(!world.skyColumnReady(p))continue;
                        if(modelWorld!=world||modelRevision!=world.models().revision()){models=world.models().copy();modelRevision=world.models().revision();modelWorld=world;}
                        capture=new Capture(world,p,models);
                    }
                    if(!capture.ticket.valid(world)){capture=null;rejected++;continue;}
                    if(!capture.pump(world,captureDeadline))break;
                    tickets.add(capture.ticket);inputs.add(capture.input());capture=null;
                }
                if(!inputs.isEmpty()) {
                    var work=List.copyOf(inputs);var timings=finishedWorkerNanos;
                    pending=world.prepareTerrainTask(()->{long then=System.nanoTime();var result=new ArrayList<WorldSkyVisibility>();for(var input:work)result.add(WorldSkyVisibility.build(input));timings.addAndGet(System.nanoTime()-then);return result;});
                }
            }
            if(packed==null&&dirty&&System.nanoTime()<deadline) {
                if(packingIterator==null){packingGeneration=generation;packingParts=new ArrayList<>();packingIterator=cache.values().iterator();packingLimit=cache.size();packingInts=0;}
                boolean truncated=false;
                while(packingIterator.hasNext()&&packingParts.size()<packingLimit&&System.nanoTime()<deadline){
                    var part=packingIterator.next();if(packingInts+part.data.data.length>WorldSkyVisibility.MAX_INTS){truncated=true;break;}
                    packingParts.add(part);packingInts+=part.data.data.length;
                }
                if(truncated||packingParts.size()>=packingLimit||!packingIterator.hasNext()) {
                    truncated|=packingIterator.hasNext();submittedTruncated=truncated;
                    var work=packingParts.stream().map(Cached::data).toList();var timings=finishedWorkerNanos;var previous=lastProduced;submittedGeneration=packingGeneration;dirty=false;packingIterator=null;
                    packed=world.prepareTerrainTask(()->{long then=System.nanoTime();var result=WorldSkyVisibility.merge(work);if(previous!=null&&Arrays.equals(previous.data,result.data))result=previous;timings.addAndGet(System.nanoTime()-then);return result;});
                }
            }
            return ready;
        }catch(InterruptedException e){Thread.currentThread().interrupt();return null;}
        catch(RejectedExecutionException e){dirty=true;return ready;}
        catch(ExecutionException e){throw new IllegalStateException("Sky visibility preparation failed",e.getCause());}
        finally{ownerNanos=System.nanoTime()-start;}
    }
    @Override public void close(){closed=true;if(pending!=null)pending.cancel(true);if(packed!=null)packed.cancel(true);pending=null;packed=null;capture=null;cache.clear();tickets.clear();inputs.clear();packingParts=null;packingIterator=null;lastProduced=null;models=null;modelWorld=null;scan=null;cleaning=null;bytes=0;dirty=false;}
}
