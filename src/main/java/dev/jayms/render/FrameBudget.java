package dev.jayms.render;

import static org.lwjgl.opengl.GL33.*;

/** Nonblocking GPU timing with hysteresis. Changes are at most 0.05 every 30 samples. */
public final class FrameBudget implements AutoCloseable {
    private final int[] queries={glGenQueries(),glGenQueries(),glGenQueries()};
    private final boolean[] pending=new boolean[3];
    private int frame,active=-1;
    private float average=16.67f;
    public void begin(RenderSettings settings){
        int slot=frame%3;
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
    public void end(){if(active>=0){glEndQuery(GL_TIME_ELAPSED);pending[active]=true;active=-1;}frame++;}
    public float milliseconds(){return average;}
    @Override public void close(){if(active>=0)glEndQuery(GL_TIME_ELAPSED);for(int q:queries)glDeleteQueries(q);}
}
