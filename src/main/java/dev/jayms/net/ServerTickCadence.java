package dev.jayms.net;

/** Single scheduler-thread deadlines. Skip missed slots; never replay a backlog. */
final class ServerTickCadence {
    static final long PERIOD=50_000_000L,MIN_GAP=1_000_000L;
    private long tick,items,broadcast;
    ServerTickCadence(long now){tick=now+PERIOD;items=now+100_000_000L;broadcast=now+200_000_000L;}
    long delayAfterTick(long now) {
        tick+=PERIOD;long delay=tick-now;
        if(delay<MIN_GAP){tick+=((MIN_GAP-delay+PERIOD-1)/PERIOD)*PERIOD;delay=tick-now;}
        return delay;
    }
    boolean itemsDue(long now){if(now-items<0)return false;items=next(items,now,100_000_000L);return true;}
    boolean broadcastDue(long now){if(now-broadcast<0)return false;broadcast=next(broadcast,now,200_000_000L);return true;}
    private static long next(long due,long now,long period){return due+((now-due)/period+1)*period;}
}
