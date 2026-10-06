package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/** Complete life journeys, with real road and building collision geometry and zero prior study. */
public class PopulationJourneyTest {
    public static final class Ground implements CitySimulation.Ground {
        public final int grade;
        public final Map<List<Integer>,Protocol.Edit> edits = new LinkedHashMap<>();
        public Ground(int grade) { this.grade=grade; }
        public int type(int x,int y,int z) { var e=edits.get(List.of(x,y,z)); return e==null ? (y<=grade?Blocks.DIRT:0) : e.type(); }
        public boolean occupied(int x,int y,int z,int w,int d) { return false; }
        public void apply(List<Protocol.Edit> batch) { for(var e:batch) edits.put(List.of(e.x(),e.y(),e.z()),e); }
    }
    public static CitySimulation fixture(CitySimulation.Ground ground, Terrain terrain, boolean roadsPresent) {
        int grade=terrain.column(8,24).height();
        var config=new GameConfig(true,true,60,8);
        var buildings=new ArrayList<CityFrame.Building>();
        buildings.add(new CityFrame.Building(100,1,0,8,grade+1,26,8,0));
        buildings.add(new CityFrame.Building(101,1,0,56,grade+1,26,8,0));
        buildings.add(new CityFrame.Building(102,0,SpecialBuildings.type(1,1),20,grade+1,26,8,0));
        buildings.add(new CityFrame.Building(103,0,SpecialBuildings.type(2,1),32,grade+1,26,8,0));
        buildings.add(new CityFrame.Building(104,0,SpecialBuildings.type(5,1),44,grade+1,26,8,0));
        buildings.add(new CityFrame.Building(105,0,SpecialBuildings.type(3,1),64,grade+1,26,8,0));
        buildings.add(new CityFrame.Building(106,1,1,56,grade+1,14,4,1000));
        buildings.add(new CityFrame.Building(107,2,2,80,grade+1,26,4,0));
        buildings.add(new CityFrame.Building(108,0,SpecialBuildings.EXCHANGE,92,grade+1,26,4,0));
        buildings.add(new CityFrame.Building(109,0,SpecialBuildings.type(1,1),68,grade+1,14,8,0));
        buildings.add(new CityFrame.Building(110,0,SpecialBuildings.type(2,1),72,grade+1,26,8,0));
        var roads=new ArrayList<CityFrame.Road>();
        if(roadsPresent) for(int x=8;x<100;x++) for(int z=23;z<=25;z++) roads.add(new CityFrame.Road(x,z,grade));
        var people=new ArrayList<CityFrame.Citizen>();
        for(int id=1;id<=3;id++) {
            int home=id==1?100:101;
            var b=buildings.get(home-100);
            people.add(new CityFrame.Citizen(id,id<3?"Learner "+id:"Shop worker "+id,0,
                b.x()+2.5f,grade+2.01f,b.z()+1.5f,0,0,100,1000000,home,id<3?0:106,0,"Ready",
                id<3?5:30,CitizenLife.Gender.MALE,id<3?CitizenLife.Education.NONE:CitizenLife.Education.SECONDARY,
                0,0,0,0,0,-10));
        }
        var catalog=ProductionCatalog.toolEra();
        var city=new CitySimulation(config,ground,terrain,new CityFrame(config,0,roads,List.of(),buildings,people,List.of(),
                new CityEconomy(new Ecs(),null,catalog).state()));
        var shop=city.economy.companies().stream().filter(c->c.kind==CityEconomy.SHOP).findFirst().orElseThrow();
        var factory=city.economy.companies().stream().filter(c->c.kind==CityMaterials.TOOLS).findFirst().orElseThrow();
        city.economy.properties.removeIf(p->p.building()==106||p.building()==107);
        city.economy.properties.add(new CityEconomy.Property(106,0,shop.id,shop.id,100,0));
        city.economy.properties.add(new CityEconomy.Property(107,0,factory.id,factory.id,100,0));
        for(var firm:city.economy.companies()) firm.cash=firm.id==shop.id || firm.id==factory.id ? 1000000 : 0;
        city.economy.budget=1000000;
        city.economy.resources.add(0,shop.id,CityMaterials.FOOD,100000*CityMaterials.UNIT);
        for(var b:buildings) {
            var batch=SpecialBuildings.special(b.type())?StructureBlueprint.special(b.type(),b.x(),b.y(),b.z()):
                    b.type()==2?StructureBlueprint.generate(2,CityMaterials.TOOLS,b.x(),b.y(),b.z()):StructureBlueprint.generate(b.type(),b.x(),b.y(),b.z());
            ground.apply(batch);
        }
        return city;
    }
    public static final class Journey {
        public final Set<String> stages=new LinkedHashSet<>(), activities=new LinkedHashSet<>();
        public final Map<Integer,Double> maxStudy=new LinkedHashMap<>();
        public final Set<Integer> schools=new LinkedHashSet<>();
        public void observe(CitySimulation city) {
            for(int id=1;id<=2;id++) {
                var l=city.life(id); var t=city.ecs.get(id,CitySimulation.Travel.class);
                stages.add(id+":"+l.education); activities.add(id+":"+t.activity);
                if(l.school!=0) { schools.add(l.school); maxStudy.merge(l.school,l.study,Math::max); }
            }
        }
        public void verify(CitySimulation city) {
            assertEquals(CitizenLife.Education.TECHNICAL,city.life(1).education,stages.toString()+maxStudy);
            assertEquals(CitizenLife.Education.UNIVERSITY,city.life(2).education,stages.toString()+maxStudy);
            for(int id=1;id<=2;id++) {
                assertTrue(stages.contains(id+":PRIMARY")); assertTrue(stages.contains(id+":SECONDARY"));
                String prefix=id+":Going to "; assertTrue(activities.stream().anyMatch(s->s.startsWith(prefix)));
            }
            assertTrue(activities.stream().anyMatch(s->s.startsWith("1:Working:")),activities.toString());
            assertTrue(activities.contains("2:Exchange analyst (graduate)"),activities.toString());
            assertEquals(107,city.ecs.get(1,CitySimulation.Household.class).job);
            assertEquals(108,city.ecs.get(2,CitySimulation.Household.class).job);
            assertTrue(activities.stream().anyMatch(s->s.endsWith("Eating at shop")));
            assertTrue(schools.containsAll(List.of(102,103,104,105)));
        }
    }
    @Test public void housedChildrenCommuteGraduateFromZeroStudyAndTakeQualifiedJobs() {
        var terrain=new Terrain(Terrain.DEFAULT_SEED);var ground=new Ground(terrain.column(8,24).height());
        var city=fixture(ground,terrain,true); var journey=new Journey();
        for(int n=0;n<125000;n++) { city.advance(.1); journey.observe(city); }
        journey.verify(city);
    }
    @Test public void disconnectedSchoolDoesNotGrantStudyOrQualification() {
        var terrain=new Terrain(Terrain.DEFAULT_SEED);var ground=new Ground(terrain.column(8,24).height());
        var city=fixture(ground,terrain,false);
        for(int n=0;n<1200;n++) city.advance(.1);
        assertEquals(0,city.life(1).study);assertEquals(CitizenLife.Education.NONE,city.life(1).education);
    }
}
