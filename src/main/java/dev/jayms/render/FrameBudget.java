package dev.jayms.render;

import static org.lwjgl.opengl.GL33.*;

/** Nonblocking GPU timing with hysteresis. Changes are at most 0.05 every 30 frames with an available result. */
public final class FrameBudget implements AutoCloseable {
    private final int[] queries={glGenQueries(),glGenQueries(),glGenQueries()};
    private final boolean[] pending=new boolean[3],atmospherePending=new boolean[3];
    private final int[][] atmosphereQueries=new int[3][4];
    private int atmosphereSlot=-1;
    private float atmosphereAverage,tableAverage,tableLast,atmosphereLast;
    private long atmosphereSamples;
    public FrameBudget(){for(int[] row:atmosphereQueries)for(int i=0;i<4;i++)row[i]=glGenQueries();}
    private int frame,active=-1;
    private boolean frameOpen;
    private long samples;
    private final GpuTimestampRing shadows=stage(),probes=stage(),terrain=stage(),water=stage(),post=stage();
    private static GpuTimestampRing stage() {
        return new GpuTimestampRing(new GpuTimestampRing.Queries() {
            public int create(){return glGenQueries();}
            public void stamp(int q){glQueryCounter(q,GL_TIMESTAMP);}
            public boolean available(int q){return glGetQueryObjecti(q,GL_QUERY_RESULT_AVAILABLE)!=0;}
            public long result(int q){return glGetQueryObjectui64(q,GL_QUERY_RESULT);}
            public void delete(int q){glDeleteQueries(q);}
        });
    }
    public void shadowsBegin(){shadows.begin();}
    public void shadowsEnd(){shadows.end();}
    public void probesBegin(){probes.begin();}
    public void probesEnd(){probes.end();}
    public float shadowsMilliseconds(){return shadows.milliseconds();}
    public float probesMilliseconds(){return probes.milliseconds();}
    public void terrainBegin(){terrain.begin();}public void terrainEnd(){terrain.end();}
    public void waterBegin(){water.begin();}public void waterEnd(){water.end();}
    public void postBegin(){post.begin();}public void postEnd(){post.end();}
    public float terrainMilliseconds(){return terrain.milliseconds();}
    public float waterMilliseconds(){return water.milliseconds();}
    public float postMilliseconds(){return post.milliseconds();}
    public long samples(){return samples;}
    private float average=16.67f;
    public void begin(RenderSettings settings){
        if(frameOpen)return;
        frameOpen=true;
        int slot=frame%3;
        if(atmospherePending[slot]&&atmosphereAvailable(slot)){
            long a=glGetQueryObjectui64(atmosphereQueries[slot][0],GL_QUERY_RESULT),b=glGetQueryObjectui64(atmosphereQueries[slot][1],GL_QUERY_RESULT),c=glGetQueryObjectui64(atmosphereQueries[slot][2],GL_QUERY_RESULT),d=glGetQueryObjectui64(atmosphereQueries[slot][3],GL_QUERY_RESULT);
            tableLast=(b-a)/1_000_000f;atmosphereLast=((b-a)+(d-c))/1_000_000f;atmosphereSamples++;
            tableAverage=tableAverage*.9f+(b-a)/1_000_000f*.1f;
            atmosphereAverage=atmosphereAverage*.9f+((b-a)+(d-c))/1_000_000f*.1f;
            atmospherePending[slot]=false;
        }
        atmosphereSlot=atmospherePending[slot]?-1:slot;
        if(pending[slot]&&glGetQueryObjecti(queries[slot],GL_QUERY_RESULT_AVAILABLE)!=0){
            float milliseconds=glGetQueryObjectui64(queries[slot],GL_QUERY_RESULT)/1_000_000f;
            average=samples++==0?milliseconds:average*.9f+milliseconds*.1f;pending[slot]=false;
            if(settings.dynamicResolution&&frame%30==0){
                float step=average>settings.targetFrameMillis*1.12f?-.05f:average<settings.targetFrameMillis*.8f?.05f:0;
                settings.renderScale=Math.max(settings.minRenderScale,Math.min(settings.maxRenderScale,settings.renderScale+step));
            }
        }
        if(!pending[slot]){glBeginQuery(GL_TIME_ELAPSED,queries[slot]);active=slot;}
    }
    private boolean atmosphereAvailable(int slot){for(int q:atmosphereQueries[slot])if(glGetQueryObjecti(q,GL_QUERY_RESULT_AVAILABLE)==0)return false;return true;}
    /** Timestamp markers may coexist with the frame elapsed query; no nested elapsed queries. */
    public void atmosphereMark(int marker){if(atmosphereSlot>=0){glQueryCounter(atmosphereQueries[atmosphereSlot][marker],GL_TIMESTAMP);if(marker==3)atmospherePending[atmosphereSlot]=true;}}
    public float atmosphereLast(){return atmosphereLast;}
    public float tableLast(){return tableLast;}
    public long atmosphereSamples(){return atmosphereSamples;}
    public float atmosphereMilliseconds(){return atmosphereAverage;}
    public float tableMilliseconds(){return tableAverage;}
    public void end(){if(!frameOpen)return;frameOpen=false;if(active>=0){glEndQuery(GL_TIME_ELAPSED);pending[active]=true;active=-1;}frame++;}
    public float milliseconds(){return samples==0?Float.NaN:average;}
    @Override public void close(){if(active>=0)glEndQuery(GL_TIME_ELAPSED);shadows.close();probes.close();terrain.close();water.close();post.close();for(int q:queries)glDeleteQueries(q);for(int[] row:atmosphereQueries)for(int q:row)glDeleteQueries(q);}
}
