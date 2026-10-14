package dev.jayms.net.city;

import java.io.*;
import java.util.*;

/** Bounded history wire envelope, independent of CityFrame and city-save versions. */
public final class HistoryWire {
    public record Request(UUID city,long request,List<String> ids,double start,double end,int resolution,int limit) {
        public Request { ids=List.copyOf(ids);if(ids.size()>4 || limit<1 || limit>1000 || resolution<0 || resolution>2
                || !Double.isFinite(start)||!Double.isFinite(end)||start<0||end<start||end>1e12)throw new IllegalArgumentException("Invalid history request"); }
        public void write(DataOutput out)throws IOException {
            out.writeBoolean(city!=null);if(city!=null){out.writeLong(city.getMostSignificantBits());out.writeLong(city.getLeastSignificantBits());}
            out.writeLong(request);out.writeByte(ids.size());for(var id:ids)out.writeUTF(id);
            out.writeDouble(start);out.writeDouble(end);out.writeByte(resolution);out.writeInt(limit);
        }
        public static Request read(DataInput in)throws IOException {
            try {
                UUID city=in.readBoolean()?new UUID(in.readLong(),in.readLong()):null;long request=in.readLong();
                int size=in.readUnsignedByte();if(size>4)throw new IOException("History series cap");
                var ids=new ArrayList<String>();for(int i=0;i<size;i++){String id=in.readUTF();if(id.length()>100)throw new IOException("History ID cap");ids.add(id);}
                return new Request(city,request,ids,in.readDouble(),in.readDouble(),in.readUnsignedByte(),in.readInt());
            }catch(IllegalArgumentException e){throw new IOException("Invalid history request",e);}
        }
    }
    public static void write(DataOutput out,CityHistory.Reply reply)throws IOException {
        out.writeInt(CityHistory.VERSION);out.writeLong(reply.city().getMostSignificantBits());out.writeLong(reply.city().getLeastSignificantBits());
        out.writeLong(reply.revision());out.writeLong(reply.request());out.writeDouble(reply.availableStart());out.writeDouble(reply.latest());out.writeUTF(reply.note());
        out.writeInt(reply.epochs().size());for(var e:reply.epochs()){out.writeInt(e.id());out.writeDouble(e.start());out.writeDouble(e.daySeconds());out.writeDouble(e.startHour());out.writeBoolean(e.cycle());}
        out.writeInt(reply.catalog().size());for(var m:reply.catalog())CityHistory.writeMetric(out,m);
        out.writeByte(reply.series().size());for(var series:reply.series()) {
            out.writeUTF(series.metric().id());out.writeInt(series.points().size());
            for(var p:series.points()){out.writeDouble(p.start());out.writeDouble(p.end());out.writeDouble(p.value());out.writeDouble(p.min());out.writeDouble(p.max());out.writeDouble(p.coverage());out.writeInt(p.epoch());out.writeBoolean(p.partial());out.writeBoolean(p.gap());out.writeDouble(p.coverageEnd());out.writeDouble(p.numerator());out.writeDouble(p.denominator());}
        }
    }
    public static CityHistory.Reply read(DataInput in)throws IOException {
        if(in.readInt()!=CityHistory.VERSION)throw new IOException("History schema mismatch");
        UUID city=new UUID(in.readLong(),in.readLong());long revision=in.readLong(),request=in.readLong();
        double start=in.readDouble(),latest=in.readDouble();String note=in.readUTF();if(note.length()>512||!Double.isFinite(start)||!Double.isFinite(latest))throw new IOException("History envelope bounds");
        var epochs=new ArrayList<CityHistory.Epoch>();
        for(int i=0,n=count(in,64);i<n;i++) {
            int id=in.readInt();double at=in.readDouble(),day=in.readDouble(),hour=in.readDouble();boolean cycle=in.readBoolean();
            try{new GameConfig(true,cycle,day,hour);}catch(IllegalArgumentException e){throw new IOException("Invalid epoch",e);}
            if(id!=i||!Double.isFinite(at)||at<0)throw new IOException("Invalid epoch");epochs.add(new CityHistory.Epoch(id,at,day,hour,cycle));
        }
        var catalog=new LinkedHashMap<String,CityHistory.Metric>();for(int i=0,n=count(in,CityHistory.MAX_SERIES);i<n;i++){var m=CityHistory.readMetric(in);if(catalog.put(m.id(),m)!=null)throw new IOException("Duplicate history metric");}
        int seriesCount=in.readUnsignedByte();if(seriesCount>4)throw new IOException("History series cap");var series=new ArrayList<CityHistory.Series>();
        for(int i=0;i<seriesCount;i++) {
            var metric=catalog.get(in.readUTF());if(metric==null)throw new IOException("Missing history metric");var points=new ArrayList<CityHistory.Point>();
            for(int j=0,n=count(in,1000);j<n;j++) {
                var p=new CityHistory.Point(in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble(),in.readInt(),in.readBoolean(),in.readBoolean(),in.readDouble(),in.readDouble(),in.readDouble());
                if(!Double.isFinite(p.start())||!Double.isFinite(p.end())||p.end()<=p.start()||!Double.isFinite(p.coverage())||p.coverage()<0||!Double.isFinite(p.coverageEnd())||!Double.isFinite(p.numerator())||!Double.isFinite(p.denominator())||p.denominator()<0||p.coverageEnd()<p.start()-1e-6||p.coverageEnd()>p.end()+1e-6||p.epoch()<0||p.epoch()>=epochs.size()||!p.gap()&&(!Double.isFinite(p.value())||!Double.isFinite(p.min())||!Double.isFinite(p.max())||p.min()>p.max()))throw new IOException("Invalid history point");points.add(p);
            }
            series.add(new CityHistory.Series(metric,points));
        }
        return new CityHistory.Reply(city,revision,request,epochs,new ArrayList<>(catalog.values()),series,start,latest,note);
    }
    private static int count(DataInput in,int max)throws IOException{int n=in.readInt();if(n<0||n>max)throw new IOException("History count cap");return n;}
}
