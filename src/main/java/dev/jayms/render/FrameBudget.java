package dev.jayms.render;

import static org.lwjgl.opengl.GL33.*;

/** Nonblocking GPU timing with hysteresis. Changes are at most 0.05 every 30 samples. */
public final class FrameBudget implements AutoCloseable {
    private final int[] queries={glGenQueries(),glGenQueries(),glGenQueries()};
    private final boolean[] pending=new boolean[3],atmospherePending=new boolean[3];
    private final int[][] atmosphereQueries=new int[3][4];
    private int atmosphereSlot=-1;
    private float atmosphereAverage,tableAverage,tableLast,atmosphereLast;
    private long atmosphereSamples;
    public FrameBudget(){for(int[] row:atmosphereQueries)for(int i=0;i<4;i++)row[i]=glGenQueries();}
    private int frame,active=-1;
    private float average=16.67f;
    public void begin(RenderSettings settings){
        int slot=frame%3;
        if(atmospherePending[slot]&&glGetQueryObjecti(atmosphereQueries[slot][3],GL_QUERY_RESULT_AVAILABLE)!=0){
            long a=glGetQueryObjectui64(atmosphereQueries[slot][0],GL_QUERY_RESULT),b=glGetQueryObjectui64(atmosphereQueries[slot][1],GL_QUERY_RESULT),c=glGetQueryObjectui64(atmosphereQueries[slot][2],GL_QUERY_RESULT),d=glGetQueryObjectui64(atmosphereQueries[slot][3],GL_QUERY_RESULT);
            tableLast=(b-a)/1_000_000f;atmosphereLast=((b-a)+(d-c))/1_000_000f;atmosphereSamples++;
            tableAverage=tableAverage*.9f+(b-a)/1_000_000f*.1f;
            atmosphereAverage=atmosphereAverage*.9f+((b-a)+(d-c))/1_000_000f*.1f;
            atmospherePending[slot]=false;
        }
        atmosphereSlot=atmospherePending[slot]?-1:slot;
        if(pending[slot]&&glGetQueryObjecti(queries[slot],GL_QUERY_RESULT_AVAILABLE)!=0){
            float milliseconds=glGetQueryObjectui64(queries[slot],GL_QUERY_RESULT)/1_000_000f;
            average=average*.9f+milliseconds*.1f;pending[slot]=false;
            if(settings.dynamicResolution&&frame%30==0){
                float step=average>settings.targetFrameMillis*1.12f?-.05f:average<settings.targetFrameMillis*.8f?.05f:0;
                settings.renderScale=Math.max(.5f,Math.min(1,settings.renderScale+step));
            }
        }
        if(!pending[slot]){glBeginQuery(GL_TIME_ELAPSED,queries[slot]);active=slot;}
    }
    /** Timestamp markers may coexist with the frame elapsed query; no nested elapsed queries. */
    public void atmosphereMark(int marker){if(atmosphereSlot>=0){glQueryCounter(atmosphereQueries[atmosphereSlot][marker],GL_TIMESTAMP);if(marker==3)atmospherePending[atmosphereSlot]=true;}}
    public float atmosphereLast(){return atmosphereLast;}
    public float tableLast(){return tableLast;}
    public long atmosphereSamples(){return atmosphereSamples;}
    public float atmosphereMilliseconds(){return atmosphereAverage;}
    public float tableMilliseconds(){return tableAverage;}
    public void end(){if(active>=0){glEndQuery(GL_TIME_ELAPSED);pending[active]=true;active=-1;}frame++;}
    public float milliseconds(){return average;}
    @Override public void close(){if(active>=0)glEndQuery(GL_TIME_ELAPSED);for(int q:queries)glDeleteQueries(q);for(int[] row:atmosphereQueries)for(int q:row)glDeleteQueries(q);}
}
