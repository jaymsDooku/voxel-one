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
        private final boolean residentIndependent;
        private final long logicalEdits,logicalGeometry,logicalMembership;
        private boolean hasPackingFence(World world){
            return logicalMembership>=0&&belongsTo(world)&&tickets.get(0).models==world.models().revision()&&tickets.get(0).terrainGrid==world.terrain().stressGrid();
        }
        private boolean unchangedTerrain(World world){
            if(tickets.isEmpty()||world.isClosed())return false;
            var t=tickets.get(0);return t.owner==world&&logicalEdits==world.editsVersion()&&logicalGeometry==world.skyGeometryRevision()&&t.models==world.models().revision()&&t.terrainGrid==world.terrain().stressGrid()&&(residentIndependent||logicalMembership==world.skyStamp());
        }
        private long validationStamp(World w){return residentIndependent?w.editsVersion():w.skyStamp();}
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
        private World checked;private long stamp=Long.MIN_VALUE,models=Long.MIN_VALUE,geometry=Long.MIN_VALUE;private Object grid;private long changesChecked;
        private Prepared(WorldSkyVisibility data,List<Cached> parts,boolean fenced,long edits,long geometry,long membership){
            this.data=data;
            this.residentIndependent=parts.stream().allMatch(c->c.ticket.completeTerrain&&c.ticket.owner==parts.get(0).ticket.owner&&c.ticket.models==parts.get(0).ticket.models&&c.ticket.terrainGrid==parts.get(0).ticket.terrainGrid);
            this.tickets=parts.stream().map(Cached::ticket).toList();
            logicalEdits=fenced?edits:Long.MIN_VALUE;logicalGeometry=fenced?geometry:Long.MIN_VALUE;logicalMembership=fenced?membership:Long.MIN_VALUE;
            this.localParts=new HashMap<>();for(var c:parts)localParts.put(c.ticket.column,c);
        }
        public boolean valid(World world){
        int trace=dev.jayms.OwnerTrace.begin("sky.validate");int checks=0;try{
            if(world.isClosed()||checked!=world||models!=world.models().revision()||grid!=world.terrain().stressGrid())return false;
            if(stamp==validationStamp(world)&&geometry==world.skyGeometryRevision())return true;
            long latest=world.skyStamp();
            if(latest-changesChecked>64||latest<changesChecked)return false;
            for(long change=changesChecked+1;change<=latest;change++){
                checks++;
                var column=world.skyChange(change);if(column==null)return false;
                var part=localParts.get(column);if(part!=null&&!part.ticket.displayValid(world))return false;
            }
            stamp=validationStamp(world);geometry=world.skyGeometryRevision();changesChecked=latest;return true;

        }finally{dev.jayms.OwnerTrace.end(trace,checks);}
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
        final Ticket ticket;final WorldSkyVisibility data;final long installedGeneration;
        private World checked;private long stamp=Long.MIN_VALUE,models=Long.MIN_VALUE,geometry=Long.MIN_VALUE;private Object grid;
        Cached(Ticket ticket,WorldSkyVisibility data,long generation){this.ticket=ticket;this.data=data;installedGeneration=generation;}
        WorldSkyVisibility data(){return data;}
        Ticket ticket(){return ticket;}
        boolean valid(World w){
            if(checked==w&&stamp==(ticket.completeTerrain?w.editsVersion():w.skyStamp())&&models==w.models().revision()&&geometry==w.skyGeometryRevision()&&grid==w.terrain().stressGrid())return true;
            if(!ticket.displayValid(w))return false;
            checked=w;stamp=ticket.completeTerrain?w.editsVersion():w.skyStamp();models=w.models().revision();geometry=w.skyGeometryRevision();grid=w.terrain().stressGrid();return true;
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
        int trace=dev.jayms.OwnerTrace.begin("sky.capture");try{
            while(chunk<ticket.positions.size()&&System.nanoTime()<deadline) {
                chunks.add(new WorldSkyVisibility.Geometry(ticket.positions.get(chunk),ticket.chunks.get(chunk).snapshot()));chunk++;
            }
            while(chunk==ticket.positions.size()&&edits.hasNext()&&System.nanoTime()<deadline) {
                var e=edits.next();if(!world.isLoaded(e.x(),e.y(),e.z())&&seen.add(e.cellKey()))cells.add(new WorldSkyVisibility.EditedCell(e.x(),e.y(),e.z(),world.cell(e.x(),e.y(),e.z()).copy().freeze()));
            }
            return chunk==ticket.positions.size()&&!edits.hasNext();

        }finally{dev.jayms.OwnerTrace.end(trace,chunk);}
    }
        WorldSkyVisibility.Input input(){return new WorldSkyVisibility.Input(List.copyOf(chunks),List.copyOf(cells),models);}
    }
    private final Map<ChunkPos,Cached> cache=new ConcurrentHashMap<>();
    /** A generation-bounded view freezes membership without an owner-thread cache copy. */
    private final ConcurrentSkipListMap<Long,Cached> cacheOrder=new ConcurrentSkipListMap<>();
    private Iterator<ChunkPos> scan;
    private Iterator<Map.Entry<ChunkPos,Cached>> cleaning;
    private Capture capture;
    private final List<Ticket> tickets=new ArrayList<>();
    private final List<WorldSkyVisibility.Input> inputs=new ArrayList<>();
    private Future<List<WorldSkyVisibility>> pending;
    private Future<Prepared> packed;
    private Prepared completed;private int validated;
    private long validationStamp,validationModels;private Object validationGrid;
    private WorldSkyVisibility lastProduced;
    private List<Cached> packingParts;
    private Iterator<Cached> packingIterator;
    private long generation,packingGeneration,submittedGeneration,bytes,packingInts;
    private int packingLimit;private boolean submittedTruncated;
    private World packingWorld;private long packingEdits,packingGeometry,packingModels,packingMembership;private Object packingGrid;private boolean packingCurrent;
    private ModelLibrary models;private World modelWorld;private long modelRevision=Long.MIN_VALUE;
    private boolean dirty,closed;
    public int packingChecks;
    public long rejected,workerNanos,ownerNanos,published,partialPublications,nearChecks,scanChecks;
    public int cachedColumns(){return cache.size();}
    private final java.util.concurrent.atomic.AtomicLong finishedWorkerNanos=new java.util.concurrent.atomic.AtomicLong();
    public int pending(){return (pending==null?0:1)+(packed==null&&completed==null?0:1)+(capture==null?0:1)+((dirty||packingIterator!=null||!inputs.isEmpty())?1:0);}
    public long cachedBytes(){return bytes;}
    public SkyVisibilityScheduler(){}
    private void remove(ChunkPos p,Cached c){if(cache.remove(p,c)){cacheOrder.remove(c.installedGeneration,c);bytes-=4L*c.data.data.length;generation++;dirty=true;}}
    private boolean current(World world,Cached c){return cache.get(c.ticket.column)==c&&c.valid(world);}
    private static boolean remoteUnloaded(World world,ChunkPos p,float x,float z) {
        return world.skyMembers(p).isEmpty()&&(Math.abs(p.chunkX()-Math.floorDiv((int)Math.floor(x),16))>6||Math.abs(p.chunkZ()-Math.floorDiv((int)Math.floor(z),16))>6);
    }
    public Prepared update(World world,long nanos){return update(world,0,0,nanos);}
    public Prepared update(World world,float x,float z,long nanos) {return update(world,x,z,nanos,false);}
    public Prepared update(World world,float x,float z,long nanos,boolean initial) {
        int trace=dev.jayms.OwnerTrace.begin("sky.update");try{
        int batch=initial?BATCH*2:BATCH;
        long start=System.nanoTime(),deadline=start+Math.max(0,nanos);Prepared ready=null;nearChecks=scanChecks=0;packingChecks=0;workerNanos=finishedWorkerNanos.getAndSet(0);
        if(closed||world.isClosed())return null;
        try {
            if(packed!=null&&packed.isDone()) {
                completed=packed.get();packed=null;boolean fenced=completed.hasPackingFence(world);validated=fenced?packingParts.size():0;
                validationStamp=fenced?completed.logicalMembership:world.skyStamp();
                validationModels=world.models().revision();validationGrid=world.terrain().stressGrid();
            }
            if(completed!=null) {
                int publicationTrace=OwnerTrace.begin("sky.publish");int checks=0,changes=0;
                try{
                    boolean valid=completed.belongsTo(world)&&validationModels==world.models().revision()&&validationGrid==world.terrain().stressGrid();
                    if(completed.unchangedTerrain(world)){validated=packingParts.size();validationStamp=world.skyStamp();}
                    while(valid&&validationStamp<world.skyStamp()&&changes<64&&System.nanoTime()<deadline){
                        var column=world.skyChange(++validationStamp);changes++;
                        if(column==null){valid=false;break;}
                        var part=completed.localParts.get(column);
                        if(part!=null&&!part.ticket.displayValid(world)){remove(column,part);valid=false;}
                    }
                    while(valid&&validationStamp==world.skyStamp()&&validated<packingParts.size()&&checks<8&&System.nanoTime()<deadline){
                        var c=packingParts.get(validated++);checks++;
                        if(!c.ticket.displayValid(world)){remove(c.ticket.column,c);valid=false;break;}
                    }
                    if(!valid){rejected++;dirty=true;completed=null;packingParts=null;}
                    else if(validated==packingParts.size()&&validationStamp==world.skyStamp()){
                        published++;if(submittedGeneration!=generation||submittedTruncated){partialPublications++;dirty=true;}
                        lastProduced=completed.data;ready=completed;ready.checked=world;ready.stamp=ready.validationStamp(world);ready.models=validationModels;ready.geometry=world.skyGeometryRevision();ready.grid=validationGrid;ready.changesChecked=world.skyStamp();
                        completed=null;packingParts=null;
                    }
                }finally{OwnerTrace.end(publicationTrace,checks+changes);}
            }
            if(pending!=null&&pending.isDone()) {
                var results=pending.get();pending=null;
                for(int i=0;i<results.size();i++) {
                    var ticket=tickets.get(i);if(!ticket.valid(world)){rejected++;continue;}
                    var next=new Cached(ticket,results.get(i),generation+1);var old=cache.put(ticket.column,next);
                    if(old!=null)cacheOrder.remove(old.installedGeneration,old);cacheOrder.put(next.installedGeneration,next);
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
            if(packingIterator!=null)pack(world,Math.min(deadline,start+Math.max(0,nanos)*3/4));
            if(pending==null) {
                boolean nearExhausted=false;
                if(scan==null||!scan.hasNext())scan=world.skyColumns().iterator();
                while((capture!=null||tickets.size()<batch)&&System.nanoTime()<captureDeadline) {
                    if(capture==null) {
                        ChunkPos p=null;
                        // Include the loaded forward edge around the predicted focus, not just GI.
                        // Reserve all but one batch slot for nearby columns; the last
                        // follows the persistent scan so distant/edited columns keep progressing.
                        if(!nearExhausted&&tickets.size()<batch-1) {
                            int cx=Math.floorDiv((int)Math.floor(x),16),cz=Math.floorDiv((int)Math.floor(z),16);
                            near:for(int ring=0;ring<=4;ring++)for(int a=-ring;a<=ring;a++)for(int b=-ring;b<=ring;b++) {
                                if(Math.max(Math.abs(a),Math.abs(b))!=ring)continue;nearChecks++;var candidate=new ChunkPos(cx+a,0,cz+b);
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
            pack(world,deadline);
            return ready;
        }catch(InterruptedException e){Thread.currentThread().interrupt();return null;}
        catch(RejectedExecutionException e){dirty=true;return ready;}
        catch(ExecutionException e){throw new IllegalStateException("Sky visibility preparation failed",e.getCause());}
        finally{ownerNanos=System.nanoTime()-start;}

        }finally{dev.jayms.OwnerTrace.end(trace,0);}
    }
    /** A continued atlas pack gets first service; at most256 tile checks per owner frame. */
    private void pack(World world,long deadline){
        if(packed!=null||completed!=null||!dirty||System.nanoTime()>=deadline)return;
        int trace=OwnerTrace.begin("sky.pack"),before=packingChecks;
        try {
            if(packingIterator==null){packingGeneration=generation;packingParts=new ArrayList<>();packingIterator=cacheOrder.headMap(packingGeneration,true).values().iterator();packingLimit=cache.size();packingInts=0;packingWorld=world;packingEdits=world.editsVersion();packingGeometry=world.skyGeometryRevision();packingModels=world.models().revision();packingGrid=world.terrain().stressGrid();packingMembership=world.skyStamp();packingCurrent=true;}
            packingCurrent&=packingWorld==world&&packingEdits==world.editsVersion()&&packingGeometry==world.skyGeometryRevision()&&packingModels==world.models().revision()&&packingGrid==world.terrain().stressGrid();
            boolean truncated=false;
            while(packingIterator.hasNext()&&packingParts.size()<packingLimit&&packingChecks<256&&System.nanoTime()<deadline){
                packingChecks++;var part=packingIterator.next();if(part.installedGeneration>packingGeneration)continue;if(!part.valid(world)){remove(part.ticket.column,part);continue;}if(packingInts+part.data.data.length>WorldSkyVisibility.MAX_INTS){truncated=true;break;}
                packingParts.add(part);packingInts+=part.data.data.length;
            }
            if(truncated||packingParts.size()>=packingLimit||!packingIterator.hasNext()) {
                truncated|=packingIterator.hasNext();submittedTruncated=truncated;
                var parts=packingParts;boolean fenced=packingCurrent;long editsFence=packingEdits,geometryFence=packingGeometry,membershipFence=packingMembership;var timings=finishedWorkerNanos;var previous=lastProduced;submittedGeneration=packingGeneration;dirty=false;packingIterator=null;
                packed=world.prepareTerrainTask(()->{long then=System.nanoTime();var result=WorldSkyVisibility.merge(parts.stream().map(Cached::data).toList());if(previous!=null&&Arrays.equals(previous.data,result.data))result=previous;var prepared=new Prepared(result,parts,fenced,editsFence,geometryFence,membershipFence);timings.addAndGet(System.nanoTime()-then);return prepared;});
            }
        }finally{OwnerTrace.end(trace,packingChecks-before);}
    }
    @Override public void close(){closed=true;if(pending!=null)pending.cancel(true);if(packed!=null)packed.cancel(true);pending=null;packed=null;completed=null;capture=null;cache.clear();cacheOrder.clear();tickets.clear();inputs.clear();packingParts=null;packingIterator=null;lastProduced=null;models=null;modelWorld=null;scan=null;cleaning=null;bytes=0;dirty=false;}
}
