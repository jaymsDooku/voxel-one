package dev.jayms.net.city;

import java.io.*;
import java.util.*;

/** Distant residents use homogeneous cohort/company groups; nearby residents use a bounded pool. */
public final class RegionalPopulation {
    public static final int MAX_POPULATION = 10_000_000, MAX_GROUPS = 4096, MAX_AGENTS = 64;
    public static final int AGENT_ID_BASE = 1_000_000_000;
    public record Group(int id, int district, int cohort, int company, int count,
            float x, float y, float z, int housed, int employed, double hunger,
            double savings, double food, double treasury, double wage, double price) {
        public Group {
            if (id < 1 || id > 1_000_000 || district < 1 || district > 1024 || cohort < 0 || cohort > 2 || company < 1 || company > 1_000_000
                    || count < 0 || count > MAX_POPULATION || housed < 0 || housed > count
                    || employed < 0 || employed > count || !Float.isFinite(x) || !Float.isFinite(y)
                    || !Float.isFinite(z) || Math.abs(x) > 1_000_000 || Math.abs(z) > 1_000_000
                    || y < -31 || y > 90 || !finite(hunger) || hunger > 100 || !finite(savings)
                    || !finite(food) || !finite(treasury) || !finite(wage) || !finite(price)
                    || savings > 1e15 || food > 1e12 || treasury > 1e15 || wage > 1000
                    || price < .01 || price > 1000 || count == 0 && (housed != 0 || employed != 0 || savings != 0))
                throw new IllegalArgumentException("Invalid regional population group");
        }
        Group residents(int n, int homes, int jobs, double need, double money) {
            return new Group(id,district,cohort,company,n,x,y,z,homes,jobs,need,money,food,treasury,wage,price);
        }
        Group economy(double need, double money, double stock, double cash) {
            return new Group(id,district,cohort,company,count,x,y,z,housed,employed,need,money,stock,cash,wage,price);
        }
    }
    public record Agent(int id, int group, boolean housed, boolean employed, double hunger,
            double savings, float x, float z, double phase) {
        public Agent {
            if (id < AGENT_ID_BASE || id >= AGENT_ID_BASE+MAX_AGENTS || group < 1
                    || !finite(hunger) || hunger > 100 || !finite(savings) || savings > 1e15
                    || !Float.isFinite(x) || !Float.isFinite(z) || Math.abs(x)>1_000_010
                    || Math.abs(z)>1_000_010 || !finite(phase) || phase >= 48)
                throw new IllegalArgumentException("Invalid regional agent");
        }
    }
    public record State(double elapsed, int focus, List<Group> groups, List<Agent> agents) {
        public State {
            groups=List.copyOf(groups); agents=List.copyOf(agents);
            if (!finite(elapsed) || focus < 0 || groups.size()>MAX_GROUPS || agents.size()>MAX_AGENTS)
                throw new IllegalArgumentException("Invalid regional population state");
            var byId=new HashMap<Integer,Group>(); var ids=new HashSet<Integer>();
            long population=agents.size();
            for (var g:groups) {
                if(byId.put(g.id(),g)!=null) throw new IllegalArgumentException("Duplicate regional group");
                population+=g.count();
            }
            if(population>MAX_POPULATION) throw new IllegalArgumentException("Regional population limit reached");
            if(focus!=0 && groups.stream().noneMatch(g->g.district()==focus))
                throw new IllegalArgumentException("Missing focused district");
            for(var a:agents) {
                var g=byId.get(a.group());
                if(!ids.add(a.id()) || g==null || g.district()!=focus)
                    throw new IllegalArgumentException("Invalid regional agent reference");
            }
        }
        public static State empty() { return new State(0,0,List.of(),List.of()); }
        public int population() { return groups.stream().mapToInt(Group::count).sum()+agents.size(); }
    }
    private static boolean finite(double n) { return Double.isFinite(n) && n>=0; }
    private final LinkedHashMap<Integer,Group> groups=new LinkedHashMap<>();
    private final ArrayList<Agent> agents=new ArrayList<>();
    private double elapsed;
    private int focus;
    public RegionalPopulation(State state) {
        elapsed=state.elapsed(); focus=state.focus();
        state.groups().forEach(g->groups.put(g.id(),g)); agents.addAll(state.agents());
    }
    public State state() { return new State(elapsed,focus,new ArrayList<>(groups.values()),agents); }
    public int population() { return groups.values().stream().mapToInt(Group::count).sum()+agents.size(); }
    public void add(Group group) {
        if(groups.containsKey(group.id()) || groups.size()>=MAX_GROUPS
                || (long)population()+group.count()>MAX_POPULATION)
            throw new IllegalArgumentException("Regional population limit or duplicate group");
        groups.put(group.id(),group);
    }
    /** Immigrants and their businesses bring initial capital and four meals each. */
    public int settle(int count, float y) {
        if(count<3 || (long)population()+count>MAX_POPULATION || groups.size()+3>MAX_GROUPS)
            throw new IllegalArgumentException("Regional population limit reached");
        int district=groups.values().stream().mapToInt(Group::district).max().orElse(0)+1;
        int first=groups.keySet().stream().mapToInt(Integer::intValue).max().orElse(0)+1;
        if(district>1024) throw new IllegalArgumentException("District limit reached");
        float x=8+(district%32)*512, z=24+(district/32+1)*512;
        var arrivals=new ArrayList<Group>();
        for(int c=0;c<3;c++) {
            int n=count/3+(c<count%3?1:0);
            arrivals.add(new Group(first+c,district,c,first+c,n,x,y,z,n,n*3/4,90,
                    n*(12.0+c*24),n*4.0,n*100.0,12+c*6,3));
        }
        arrivals.forEach(this::add);
        return district;
    }
    /** Collapse the old pool before splitting the new one. Counts, money and needs are conserved. */
    public void focus(int district) {
        if(district==focus) return;
        if(district!=0 && groups.values().stream().noneMatch(g->g.district()==district))
            throw new IllegalArgumentException("District is no longer available");
        for(var a:agents) {
            var g=groups.get(a.group()); int n=g.count()+1;
            groups.put(g.id(),g.residents(n,g.housed()+(a.housed()?1:0),g.employed()+(a.employed()?1:0),
                    (g.hunger()*g.count()+a.hunger())/n,g.savings()+a.savings()));
        }
        agents.clear(); focus=district;
        var selected=groups.values().stream().filter(g->g.district()==district).map(Group::id).toList();
        boolean progress=true;
        while(agents.size()<MAX_AGENTS && progress) {
            progress=false;
            for(int key:selected) {
                if(agents.size()==MAX_AGENTS) break;
                var g=groups.get(key); if(g.count()==0) continue;
                progress=true; double money=g.savings()/g.count();
                long draw=Integer.toUnsignedLong(Integer.rotateLeft(key*0x9e3779b9+agents.size()*0x85ebca6b,13));
                int sampleIndex=(int)(draw%g.count());
                boolean home=sampleIndex<g.housed(), job=sampleIndex<g.employed();
                int i=agents.size(); double phase=i*.75%48;
                float x=g.x()-8+(float)(phase<16?phase:phase<24?16:phase<40?40-phase:0);
                float z=g.z()-4+(float)(phase<16?0:phase<24?phase-16:phase<40?8:48-phase);
                agents.add(new Agent(AGENT_ID_BASE+i,key,home,job,g.hunger(),money,x,z,phase));
                groups.put(key,g.residents(g.count()-1,g.housed()-(home?1:0),g.employed()-(job?1:0),
                        g.hunger(),g.count()==1?0:g.savings()-money));
            }
        }
    }
    public void focusNear(float x, float z) {
        int nearest=0; double best=96*96;
        for(var g:groups.values()) {
            double d=(g.x()-x)*(double)(g.x()-x)+(g.z()-z)*(double)(g.z()-z);
            if(d<best || d==best && g.district()<nearest) { best=d; nearest=g.district(); }
        }
        focus(nearest);
    }
    public void advance(double seconds, double daySeconds) {
        if(!Double.isFinite(seconds) || seconds<=0 || seconds>2 || !Double.isFinite(daySeconds) || daySeconds<60)
            throw new IllegalArgumentException("Invalid regional timestep");
        elapsed+=seconds; double days=seconds/daySeconds;
        for(var old:new ArrayList<>(groups.values())) {
            double paid=Math.min(old.treasury(),old.employed()*old.wage()*days);
            double money=old.savings()+paid, cash=old.treasury()-paid;
            double hunger=Math.max(0,old.hunger()-72*days), meals=0;
            if(old.count()>0 && hunger<70) meals=Math.min(old.count()*(100-hunger)/30,
                    Math.min(old.food()+paid/old.price(),money/old.price()));
            double bill=meals*old.price(); money-=bill; cash+=bill;
            groups.put(old.id(),old.economy(old.count()==0?hunger:Math.min(100,hunger+30*meals/old.count()),
                    Math.max(0,money),Math.min(1e12,Math.max(0,old.food()+paid/old.price()-meals)),Math.max(0,cash)));
        }
        for(int i=0;i<agents.size();i++) {
            var a=agents.get(i); var g=groups.get(a.group());
            double paid=a.employed()?Math.min(g.treasury(),g.wage()*days):0;
            double money=a.savings()+paid, cash=g.treasury()-paid;
            double hunger=Math.max(0,a.hunger()-72*days), food=Math.min(1e12,g.food()+paid/g.price());
            if(hunger<70 && food>=1 && money>=g.price()) {
                food-=1; money-=g.price(); cash+=g.price(); hunger=Math.min(100,hunger+30);
            }
            groups.put(g.id(),g.economy(g.hunger(),g.savings(),food,Math.max(0,cash)));
            double phase=(a.phase()+seconds*1.5)%48;
            // A bounded district street circuit: home -> workplace -> shop -> home.
            float x=g.x()-8+(float)(phase<16?phase:phase<24?16:phase<40?40-phase:0);
            float z=g.z()-4+(float)(phase<16?0:phase<24?phase-16:phase<40?8:48-phase);
            agents.set(i,new Agent(a.id(),a.group(),a.housed(),a.employed(),hunger,Math.max(0,money),x,z,phase));
        }
    }
    public static List<CityFrame.Citizen> citizens(State state) {
        var groups=new HashMap<Integer,Group>(); state.groups().forEach(g->groups.put(g.id(),g));
        var result=new ArrayList<CityFrame.Citizen>();
        for(var a:state.agents()) {
            var g=groups.get(a.group());
            result.add(new CityFrame.Citizen(a.id(),"District "+g.district()+" resident "+(a.id()-AGENT_ID_BASE+1),
                    g.cohort(),a.x(),g.y(),a.z(),a.phase()<16?90:a.phase()<24?0:a.phase()<40?270:180,(float)a.phase(),
                    (float)a.hunger(),(float)a.savings(),0,0,0,
                    "Going through district streets (regional agent)"));
        }
        return List.copyOf(result);
    }
    public static void write(DataOutput out, State state) throws IOException {
        out.writeDouble(state.elapsed());out.writeInt(state.focus());out.writeInt(state.groups().size());
        for(var g:state.groups()) {
            out.writeInt(g.id());out.writeInt(g.district());out.writeByte(g.cohort());out.writeInt(g.company());out.writeInt(g.count());
            out.writeFloat(g.x());out.writeFloat(g.y());out.writeFloat(g.z());out.writeInt(g.housed());out.writeInt(g.employed());
            out.writeDouble(g.hunger());out.writeDouble(g.savings());out.writeDouble(g.food());out.writeDouble(g.treasury());
            out.writeDouble(g.wage());out.writeDouble(g.price());
        }
        out.writeInt(state.agents().size());
        for(var a:state.agents()) {
            out.writeInt(a.id());out.writeInt(a.group());out.writeBoolean(a.housed());out.writeBoolean(a.employed());
            out.writeDouble(a.hunger());out.writeDouble(a.savings());out.writeFloat(a.x());out.writeFloat(a.z());out.writeDouble(a.phase());
        }
    }
    public static State read(DataInput in) throws IOException {
        try {
            double elapsed=in.readDouble();int focus=in.readInt(),n=in.readInt();
            if(n<0 || n>MAX_GROUPS) throw new IOException("Invalid regional group count");
            var groups=new ArrayList<Group>(n);
            for(int i=0;i<n;i++) groups.add(new Group(in.readInt(),in.readInt(),in.readUnsignedByte(),in.readInt(),in.readInt(),
                    in.readFloat(),in.readFloat(),in.readFloat(),in.readInt(),in.readInt(),in.readDouble(),in.readDouble(),
                    in.readDouble(),in.readDouble(),in.readDouble(),in.readDouble()));
            n=in.readInt();if(n<0 || n>MAX_AGENTS) throw new IOException("Invalid regional agent count");
            var agents=new ArrayList<Agent>(n);
            for(int i=0;i<n;i++) agents.add(new Agent(in.readInt(),in.readInt(),in.readBoolean(),in.readBoolean(),
                    in.readDouble(),in.readDouble(),in.readFloat(),in.readFloat(),in.readDouble()));
            return new State(elapsed,focus,groups,agents);
        } catch(IllegalArgumentException e) { throw new IOException("Invalid regional population",e); }
    }
}
