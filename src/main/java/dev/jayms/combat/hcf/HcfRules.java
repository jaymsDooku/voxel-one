package dev.jayms.combat.hcf;

/** Initial bounded kit-map tuning. Simulation timers are 20 Hz; persistent timers use milliseconds. */
public record HcfRules(int warmupTicks,int combatTagTicks,int spawnTicks,int rogueCooldownTicks,
                       int markTicks,int auraTicks,int captureTicks,long deathbanMillis,
                       long dtrFreezeMillis,long dtrRegenMillis,int restockTicks) {
    public HcfRules {
        for(int v:new int[]{warmupTicks,combatTagTicks,spawnTicks,rogueCooldownTicks,markTicks,auraTicks,captureTicks,restockTicks})
            if(v<1 || v>72000) throw new IllegalArgumentException("HCF tick tuning");
        for(long v:new long[]{deathbanMillis,dtrFreezeMillis,dtrRegenMillis})
            if(v<1 || v>86_400_000L) throw new IllegalArgumentException("HCF persistent tuning");
    }
    public static HcfRules initial() {return new HcfRules(100,300,100,200,120,100,200,60_000,120_000,60_000,200);}
}
