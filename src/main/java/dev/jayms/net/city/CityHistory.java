package dev.jayms.net.city;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Bounded simulation-owned history. Unknown intervals remain gaps, never zeroes. */
public final class CityHistory {
    public static final int VERSION=1, MAX_SERIES=576, MAX_POINTS=1000;
    public static final long MAX_BYTES=64L*1024*1024;
    public enum Kind { STOCK, COUNTER, SIGNED_COUNTER, RATIO, ESTIMATE }
    public record Metric(String id,String name,String unit,String domain,Kind kind,
                         String scope,String definition,int precision,String source,String aggregation) {
        public Metric(String id,String name,String unit,String domain,Kind kind,String scope,String definition,int precision) {
            this(id,name,unit,domain,kind,scope,definition,precision,
                    id.startsWith("finance.")?"CityEconomy measured public accounts":id.startsWith("resource.")?"CityMaterials inventory, production and validated trades":id.startsWith("company.")||id.startsWith("business.")||id.startsWith("workplace.")?"CityBusinesses accounts and measured local staff":id.startsWith("development.")?"CityFrame zones, plots, roads and buildings":"CityMetrics and RegionalPopulation represented cohorts",
                    flow(kind)?"Sum of event-time flows or validated cumulative deltas":kind==Kind.RATIO?"Denominator-weighted time mean with extrema":kind==Kind.ESTIMATE?"Weighted estimated summary; median summaries remain estimates":"Held stock value, time-weighted mean with extrema");
        }
        public Metric {
            if(id.length()>100 || name.length()>160 || unit.length()>48 || domain.length()>48
                    || scope.length()>160 || definition.length()>512 || source.length()>200 || aggregation.length()>200 || precision<0 || precision>6)
                throw new IllegalArgumentException("Invalid metric metadata");
        }
        public boolean compatible(Metric other) {
            return unit.equals(other.unit) && flow(kind)==flow(other.kind);
        }
    }
    public record Epoch(int id,double start,double daySeconds,double startHour,boolean cycle) {
        public String label(double elapsed) {
            return cycle ? CityTime.at(new GameConfig(true,true,daySeconds,startHour),elapsed).label()
                    : String.format(Locale.ROOT,"Elapsed %.1fs | frozen calendar",elapsed);
        }
    }
    public record Point(double start,double end,double value,double min,double max,double coverage,
                        int epoch,boolean partial,boolean gap,double coverageEnd,double numerator,double denominator) {}
    public record Series(Metric metric,List<Point> points) {
        public Series { points=List.copyOf(points); }
    }
    public record Reply(UUID city,long revision,long request,List<Epoch> epochs,List<Metric> catalog,List<Series> series,
                        double availableStart,double latest,String note) {
        public Reply { epochs=List.copyOf(epochs);catalog=List.copyOf(catalog);series=List.copyOf(series); }
    }
    private static final class Bucket {
        double start,end,sum,weight,averageWeight,lastCapture,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,last;
        int epoch; boolean gap;
        Bucket(double start,double end,int epoch) {this.start=start;this.end=end;this.epoch=epoch;this.lastCapture=start;}
        void add(double value,double duration,Kind kind) {
            if(!Double.isFinite(value)) {gap=true;return;}
            sum+=flow(kind)?value:value*duration;weight+=duration;averageWeight+=duration;
            min=Math.min(min,value);max=Math.max(max,value);last=value;
        }
        Point point(Kind kind,double latest) {
            double value=flow(kind)?sum:averageWeight>0?sum/averageWeight:last;
            boolean missing=weight<=0 || gap;
            return new Point(start,end,missing?Double.NaN:value,flow(kind)?value:min,flow(kind)?value:max,weight,epoch,
                    weight<end-start-1e-6 || latest<end,missing,lastCapture,sum,averageWeight);
        }
    }
    private static boolean flow(Kind kind){return kind==Kind.COUNTER || kind==Kind.SIGNED_COUNTER;}
    public boolean events;
    private UUID identity=UUID.randomUUID();
    private long revision;
    private final LinkedHashMap<String,Metric> catalog=new LinkedHashMap<>();
    private final Map<String,Double> metricStarts=new LinkedHashMap<>();
    private final List<Epoch> epochs=new ArrayList<>();
    private final Map<String,List<ArrayDeque<Bucket>>> data=new LinkedHashMap<>();
    private Map<String,Double> previous=Map.of();
    private double last=Double.NaN,latestEvent=Double.NaN,clock=Double.NaN;
    private GameConfig config;
    private String note="Collection begins now; no backfill";
    public synchronized UUID identity(){return identity;}
    public synchronized long revision(){return revision;}
    public synchronized List<Metric> catalog(){return List.copyOf(catalog.values());}
    public synchronized double latest(){return Math.max(Double.isFinite(clock)?clock:0,Math.max(Double.isFinite(last)?last:0,Double.isFinite(latestEvent)?latestEvent:0));}
    public synchronized void clock(double elapsed){if(Double.isFinite(elapsed)&&elapsed>=0)clock=elapsed;}
    public synchronized boolean due(double elapsed,GameConfig settings) {
        return !Objects.equals(config,settings) || !Double.isFinite(last) || elapsed<last
                || elapsed-last>=settings.daySeconds()/288;
    }
    public void observe(double elapsed,GameConfig settings,List<Metric> metrics,Map<String,Double> values) {
        observe(elapsed,settings,metrics,values,null);
    }
    public synchronized void observe(double elapsed,GameConfig settings,List<Metric> metrics,Map<String,Double> values,Set<String> activeEntities) {
        if(!Double.isFinite(elapsed) || elapsed<0)return;
        boolean boundary=!Objects.equals(config,settings) || Double.isFinite(last)&&elapsed<last;
        if(Double.isFinite(last)&&elapsed<last) {
            for(var levels:data.values())for(var rows:levels)rows.removeIf(b->b.end>elapsed);
            for(var id:new ArrayList<>(metricStarts.keySet()))if(metricStarts.get(id)>elapsed){metricStarts.remove(id);catalog.remove(id);data.remove(id);}
            note="Clock rollback: future coverage removed; new epoch begins";
        }
        if(boundary || epochs.isEmpty()) {
            if(epochs.size()==64) {clear();note="Epoch cap reached; earlier coverage discarded";}
            epochs.add(new Epoch(epochs.size(),elapsed,settings.daySeconds(),settings.startHour(),settings.cycle()));
            config=settings;previous=Map.of();last=Double.NaN;latestEvent=Double.NaN;clock=elapsed;
        }
        if(Double.isFinite(last)&&elapsed<=last)return;
        for(var m:metrics) if(catalog.containsKey(m.id()) || catalog.size()<MAX_SERIES) {
            metricStarts.putIfAbsent(m.id(),elapsed);
            catalog.put(m.id(),m);
            data.computeIfAbsent(m.id(),k->List.of(new ArrayDeque<>(),new ArrayDeque<>(),new ArrayDeque<>()));
        }
        var present=new HashSet<String>();metrics.forEach(m->present.add(m.id()));
        for(var id:new ArrayList<>(catalog.keySet()))if(!present.contains(id)&&(id.startsWith("company.")||id.startsWith("workplace."))) {
            var m=catalog.get(id);
            String entity=id.substring(0,id.lastIndexOf('.')+1);
            boolean active=activeEntities!=null&&activeEntities.contains(entity);
            String baseScope=m.scope();int cut=baseScope.indexOf(" | Inactive observed at elapsed");
            if(cut<0)cut=baseScope.indexOf(" | outside current detail cap");
            if(cut>=0)baseScope=baseScope.substring(0,cut);
            String suffix=active?" | outside current detail cap":" | Inactive observed at elapsed "+String.format(Locale.ROOT,"%.1fs",elapsed);
            if(active&&!m.scope().contains("outside current detail cap") || !active&&!m.scope().contains("Inactive observed"))
                catalog.put(id,new Metric(m.id(),m.name(),m.unit(),m.domain(),m.kind(),baseScope+suffix,m.definition(),m.precision(),m.source(),m.aggregation()));
        }
        Epoch epoch=epochs.get(epochs.size()-1);
        double dt=Double.isFinite(last)?elapsed-last:0;
        double[] intervals={settings.daySeconds()/288,settings.daySeconds()/24,settings.daySeconds()};
        int[] limits={289,721,366};
        for(var entry:data.entrySet()) {
            var m=catalog.get(entry.getKey());Double v=values.get(m.id()),old=previous.get(m.id());
            for(int level=0;level<3;level++) {
                double width=intervals[level];var rows=entry.getValue().get(level);
                if(dt>0 && dt<=intervals[0]*1.5 && old!=null) {
                    double cursor=last;
                    while(cursor<elapsed-1e-9) {
                        Bucket b=bucket(rows,cursor,settings,width,epoch.id());
                        double duration=Math.min(elapsed,b.end)-cursor;
                        if(duration<=1e-9)break;
                        if(flow(m.kind())) {
                            if(events) {b.weight+=duration;if(v!=null&&m.kind()==Kind.COUNTER&&v<old)b.gap=true;}
                            else if(v!=null && (v>=old || m.kind()==Kind.SIGNED_COUNTER) && elapsed<=b.end+1e-6)
                                b.add(v-old,duration,m.kind());
                            else b.gap=true;
                        } else if(v!=null) {
                            double factor=(m.kind()==Kind.RATIO||m.id().equals("population.hunger")||m.id().equals("population.averageSavings"))?previous.getOrDefault((m.id().equals("population.hunger")||m.id().equals("population.averageSavings"))?"population.total":m.id().startsWith("workplace.")?m.id().substring(0,m.id().lastIndexOf('.')+1)+"employees":!m.id().equals("population.employment")?m.id().substring(0,m.id().lastIndexOf('.')+1)+"population":"population.total",0.0):1;
                            if(factor<=0)b.gap=true;
                            else {b.sum+=old*duration*factor;b.weight+=duration;b.averageWeight+=duration*factor;b.min=Math.min(b.min,old);b.max=Math.max(b.max,old);}
                        }
                        else b.gap=true;
                        b.lastCapture=Math.max(b.lastCapture,cursor+duration);
                        cursor+=duration;
                    }
                }
                Bucket current=bucket(rows,elapsed,settings,width,epoch.id());
                if(v!=null&&Double.isFinite(v) && !flow(m.kind())) {
                    current.lastCapture=Math.max(current.lastCapture,elapsed);
                    current.last=v;current.min=Math.min(current.min,v);current.max=Math.max(current.max,v);
                }
                if(dt>intervals[0]*1.5 || v==null || !Double.isFinite(v))current.gap=true;
                while(rows.size()>limits[level] || !rows.isEmpty()&&rows.peekFirst().end<elapsed-width*(limits[level]-1))rows.removeFirst();
            }
        }
        var bounded=new LinkedHashMap<String,Double>();
        values.forEach((id,value)->{if(catalog.containsKey(id)&&Double.isFinite(value))bounded.put(id,value);});
        previous=Map.copyOf(bounded);last=elapsed;revision++;
    }
    private static Bucket bucket(ArrayDeque<Bucket> rows,double elapsed,GameConfig settings,double width,int epoch) {
        double offset=settings.cycle()?settings.startHour()/24*settings.daySeconds():0;
        double coordinate=(elapsed+offset)/width;
        double nearest=Math.rint(coordinate);
        if(Math.abs(coordinate-nearest)<1e-9)coordinate=nearest;
        double start=Math.floor(coordinate)*width-offset;
        for(var iterator=rows.descendingIterator();iterator.hasNext();){var existing=iterator.next();if(existing.start==start && existing.epoch==epoch)return existing;if(existing.epoch==epoch&&existing.start<start)break;}
        Bucket b=rows.peekLast();
        if(b==null || b.start!=start || b.epoch!=epoch){
            var later=new ArrayDeque<Bucket>();
            while(!rows.isEmpty()&&rows.peekLast().epoch==epoch&&rows.peekLast().start>start)later.addFirst(rows.removeLast());
            b=new Bucket(start,start+width,epoch);rows.add(b);rows.addAll(later);
        }
        return b;
    }
    /** Exact event-time flows, placed in half-open calendar buckets [start,end). */
    public synchronized void event(String id,double elapsed,double amount,GameConfig settings) {
        if(!events || !Double.isFinite(elapsed) || elapsed<0 || epochs.isEmpty())return;
        var m=catalog.get(id);if(m==null || !flow(m.kind()))return;
        latestEvent=Math.max(Double.isFinite(latestEvent)?latestEvent:0,elapsed);
        boolean valid=Double.isFinite(amount)&&(amount>=0||m.kind()==Kind.SIGNED_COUNTER);
        double[] widths={settings.daySeconds()/288,settings.daySeconds()/24,settings.daySeconds()};
        for(int i=0;i<3;i++) {
            var b=bucket(data.get(id).get(i),elapsed,settings,widths[i],epochs.get(epochs.size()-1).id());
            if(valid){b.sum+=amount;b.lastCapture=Math.max(b.lastCapture,elapsed);}else b.gap=true;
            int cap=new int[]{289,721,366}[i];var rows=data.get(id).get(i);
            while(rows.size()>cap)rows.removeFirst();
        }
        revision++;
    }
    private void clear(){catalog.clear();data.clear();metricStarts.clear();epochs.clear();previous=Map.of();last=Double.NaN;latestEvent=Double.NaN;clock=Double.NaN;}
    public synchronized Reply query(UUID city,long request,List<String> ids,double start,double end,int resolution,int limit) {
        if(city!=null&&!identity.equals(city))throw new IllegalArgumentException("City identity changed");
        if(ids.size()>4||new HashSet<>(ids).size()!=ids.size()||resolution<0||resolution>2
                ||limit<1||limit>MAX_POINTS||!Double.isFinite(start)||!Double.isFinite(end)||start<0||end<start||end>1e12)
            throw new IllegalArgumentException("Invalid bounded history query");
        var result=new ArrayList<Series>();Metric first=null;double available=Double.POSITIVE_INFINITY;
        for(String id:ids) {
            Metric m=catalog.get(id);if(m==null)throw new IllegalArgumentException("Unknown metric");
            if(first!=null&&!first.compatible(m))throw new IllegalArgumentException("Incompatible series units or stock/flow types");first=m;
            var points=new ArrayList<Point>();
            for(var b:data.get(id).get(resolution)) {
                available=Math.min(available,Math.max(metricStarts.getOrDefault(id,0.0),Math.max(epochs.get(b.epoch).start(),Math.max(0,b.start))));
                if(start<end&&b.end>start&&b.start<end) {
                    Point point=b.point(m.kind(),latest());
                    if(events&&flow(m.kind())) {
                        double epochEnd=b.epoch+1<epochs.size()?epochs.get(b.epoch+1).start():latest();
                        double knownEnd=Math.min(b.end,Math.min(latest(),epochEnd)),knownStart=Math.max(b.start,Math.max(metricStarts.get(id),epochs.get(b.epoch).start()));
                        double coverage=Math.max(0,knownEnd-knownStart);boolean missing=b.gap||coverage<=0;
                        point=new Point(b.start,b.end,missing?Double.NaN:b.sum,b.sum,b.sum,coverage,b.epoch,coverage<b.end-b.start-1e-6,missing,Math.max(b.start,knownEnd),b.sum,0);
                    }
                    if(Math.max(b.start,Math.max(metricStarts.get(id),epochs.get(b.epoch).start()))<start || point.coverageEnd()>end)
                        point=new Point(b.start,b.end,Double.NaN,Double.NaN,Double.NaN,0,b.epoch,true,true,Math.min(b.end,Math.max(b.start,end)),0,0);
                    points.add(point);
                }
            }
            points.sort(Comparator.comparingDouble(Point::start).thenComparingInt(Point::epoch));
            if(points.size()>limit)throw new IllegalArgumentException("Choose coarser resolution or narrower range");
            result.add(new Series(m,points));
        }
        return new Reply(identity,revision,request,epochs,catalog(),result,Double.isFinite(available)?available:latest(),latest(),note+" | detail caps: companies "+HistoryCatalog.COMPANY_CAP+", products "+HistoryCatalog.PRODUCT_CAP+", districts "+HistoryCatalog.DISTRICT_CAP+", workplaces "+HistoryCatalog.WORKPLACE_CAP);
    }
    public synchronized void save(Path history,Path world) throws IOException {
        if(history==null)return;
        Path tmp=history.resolveSibling(history.getFileName()+".tmp");
        var checksum=new CRC32();
        try(var out=new DataOutputStream(new CheckedOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)),checksum))) {
            out.writeInt(0x48495331);out.writeInt(VERSION);out.writeUTF(fingerprint(world));
            out.writeLong(identity.getMostSignificantBits());out.writeLong(identity.getLeastSignificantBits());out.writeLong(revision);
            out.writeDouble(last);out.writeDouble(latestEvent);out.writeDouble(clock);out.writeBoolean(events);out.writeInt(epochs.size());
            for(var e:epochs){out.writeInt(e.id());out.writeDouble(e.start());out.writeDouble(e.daySeconds());out.writeDouble(e.startHour());out.writeBoolean(e.cycle());}
            out.writeInt(catalog.size());
            for(var m:catalog.values()) {
                writeMetric(out,m);out.writeDouble(metricStarts.get(m.id()));
                for(var rows:data.get(m.id())) {
                    double priorStart=Double.NEGATIVE_INFINITY;int priorEpoch=-1;
                    for(var b:rows){
                        if((b.epoch==priorEpoch&&b.start<priorStart) || b.weight>b.end-b.start+1 || !Double.isFinite(b.last))
                            throw new IOException("Invalid generated history bucket");
                        priorStart=b.start;priorEpoch=b.epoch;
                    }
                    out.writeInt(rows.size());
                    for(var b:rows){out.writeDouble(b.start);out.writeDouble(b.end);out.writeDouble(b.sum);out.writeDouble(b.weight);out.writeDouble(b.averageWeight);out.writeDouble(b.min);out.writeDouble(b.max);out.writeDouble(b.last);out.writeDouble(b.lastCapture);out.writeInt(b.epoch);out.writeBoolean(b.gap);}
                }
            }
            out.writeInt(previous.size());for(var e:previous.entrySet()){out.writeUTF(e.getKey());out.writeDouble(e.getValue());}
            out.writeLong(checksum.getValue());
        }
        if(Files.size(tmp)>MAX_BYTES){Files.delete(tmp);throw new IOException("History size cap exceeded");}
        try{Files.move(tmp,history,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,history,StandardCopyOption.REPLACE_EXISTING);}
    }
    public static CityHistory load(Path history,Path world,GameConfig settings,double elapsed) {
        CityHistory h=new CityHistory();
        if(history==null||!Files.exists(history))return h;
        try {
            if(Files.size(history)>MAX_BYTES)throw new IOException("Oversized history");
            var checksum=new CRC32();
            try(var in=new DataInputStream(new CheckedInputStream(new BufferedInputStream(Files.newInputStream(history)),checksum))) {
                if(in.readInt()!=0x48495331||in.readInt()!=VERSION||!in.readUTF().equals(fingerprint(world)))throw new IOException("History world/version mismatch");
                h.identity=new UUID(in.readLong(),in.readLong());h.revision=in.readLong();h.last=in.readDouble();h.latestEvent=in.readDouble();h.clock=in.readDouble();h.events=in.readBoolean();
                if(!Double.isFinite(h.last)||h.last<0||h.last>elapsed||!Double.isNaN(h.latestEvent)&&(!Double.isFinite(h.latestEvent)||h.latestEvent<0||h.latestEvent>elapsed)||!Double.isNaN(h.clock)&&(!Double.isFinite(h.clock)||h.clock<0||h.clock>elapsed))throw new IOException("History clock mismatch");
                for(int i=0,n=count(in,64);i<n;i++) {
                    int id=in.readInt();double at=in.readDouble(),day=in.readDouble(),hour=in.readDouble();boolean cycle=in.readBoolean();
                    new GameConfig(true,cycle,day,hour);
                    if(id!=i||!Double.isFinite(at)||at<0)throw new IOException("Invalid epoch");
                    h.epochs.add(new Epoch(id,at,day,hour,cycle));
                }
                if(h.epochs.isEmpty())throw new IOException("Missing epoch");
                for(int i=0,n=count(in,MAX_SERIES);i<n;i++) {
                    Metric m=readMetric(in);double metricStart=in.readDouble();if(!Double.isFinite(metricStart)||metricStart<0||metricStart>elapsed)throw new IOException("Invalid metric coverage start");h.metricStarts.put(m.id(),metricStart);if(h.catalog.put(m.id(),m)!=null)throw new IOException("Duplicate metric");
                    var levels=new ArrayList<ArrayDeque<Bucket>>();int[] caps={289,721,366};
                    for(int level=0;level<3;level++) {
                        var rows=new ArrayDeque<Bucket>();double before=Double.NEGATIVE_INFINITY;
                        for(int j=0,size=count(in,caps[level]);j<size;j++) {
                            double start=in.readDouble(),end=in.readDouble(),sum=in.readDouble(),weight=in.readDouble(),averageWeight=in.readDouble(),min=in.readDouble(),max=in.readDouble(),last=in.readDouble(),lastCapture=in.readDouble();int epoch=in.readInt();boolean gap=in.readBoolean();
                            if(!Double.isFinite(start)||!Double.isFinite(end)||(start<before&&epoch==rows.peekLast().epoch)||end<=start||!Double.isFinite(sum)||!Double.isFinite(weight)||!Double.isFinite(averageWeight)||averageWeight<0||weight<0||weight>end-start+1||!Double.isFinite(last)||!Double.isFinite(lastCapture)||lastCapture<start-1e-6||lastCapture>end+1e-6||(!flow(m.kind())&&weight>0&&(!Double.isFinite(min)||!Double.isFinite(max)||min>max))||epoch<0||epoch>=h.epochs.size())throw new IOException("Invalid history bucket");
                            Bucket b=new Bucket(start,end,epoch);b.sum=sum;b.weight=weight;b.averageWeight=averageWeight;b.min=min;b.max=max;b.last=last;b.lastCapture=lastCapture;b.gap=gap;rows.add(b);before=start;
                        }
                        levels.add(rows);
                    }
                    h.data.put(m.id(),levels);
                }
                var previous=new LinkedHashMap<String,Double>();
                for(int i=0,n=count(in,MAX_SERIES);i<n;i++){String id=in.readUTF();double value=in.readDouble();if(!h.catalog.containsKey(id)||!Double.isFinite(value)||previous.put(id,value)!=null)throw new IOException("Invalid prior sample");}
                long actualChecksum=checksum.getValue();
                if(in.readLong()!=actualChecksum)throw new IOException("History checksum mismatch");
                if(in.read()!=-1)throw new IOException("Trailing history bytes");h.previous=Map.copyOf(previous);
                Epoch e=h.epochs.get(h.epochs.size()-1);h.config=new GameConfig(true,e.cycle(),e.daySeconds(),e.startHour(),settings.atmosphere());
                h.note=String.format(Locale.ROOT,"Restore boundary at elapsed %.3fs; no simulation time recorded while stopped",elapsed);
            }
            return h;
        } catch(IOException|IllegalArgumentException e) { h=new CityHistory();h.note="History rejected ("+(Set.of("Invalid history bucket","Invalid prior sample","History clock mismatch","History world/version mismatch","Invalid epoch","Missing epoch","Duplicate metric","Trailing history bytes","History count cap","Oversized history","History checksum mismatch","Invalid metric coverage start").contains(Objects.toString(e.getMessage(),"invalid data"))?e.getMessage():"invalid data")+"); valid world preserved. Collection restarts now";return h; }
    }
    private static int count(DataInput in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IOException("History count cap");return n;}
    private static String fingerprint(Path world)throws IOException {
        try {return HexFormat.of().formatHex(digest(world));}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static byte[] digest(Path world)throws IOException,NoSuchAlgorithmException {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(world)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}
        return digest.digest();
    }
    public static void writeMetric(DataOutput out,Metric m)throws IOException {
        out.writeUTF(m.id());out.writeUTF(m.name());out.writeUTF(m.unit());out.writeUTF(m.domain());out.writeByte(m.kind().ordinal());out.writeUTF(m.scope());out.writeUTF(m.definition());out.writeByte(m.precision());out.writeUTF(m.source());out.writeUTF(m.aggregation());
    }
    public static Metric readMetric(DataInput in)throws IOException {
        try{return new Metric(in.readUTF(),in.readUTF(),in.readUTF(),in.readUTF(),Kind.values()[in.readUnsignedByte()],in.readUTF(),in.readUTF(),in.readUnsignedByte(),in.readUTF(),in.readUTF());}
        catch(IllegalArgumentException|ArrayIndexOutOfBoundsException e){throw new IOException("Invalid metric",e);}
    }
}
