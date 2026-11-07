package dev.jayms;

/** Startup scheduling only, not an elapsed-time guarantee for one edit or draw. */
final class StartupReplayBudget {
    static final int RECORDS=128;
    static final long WORK_NANOS=2_000_000L,DRAW_NANOS=16_666_667L;
    private int records;
    private long workStart,lastFrameEnd;
    private boolean drew;
    StartupReplayBudget(long now){workStart=now;}
    boolean afterEdit(long now){return ++records>=RECORDS||now-workStart>=WORK_NANOS;}
    boolean drawDue(long now){return !drew||now-lastFrameEnd>=DRAW_NANOS;}
    void frameFinished(long now){drew=true;lastFrameEnd=now;}
    void resumed(long now){records=0;workStart=now;}
}
