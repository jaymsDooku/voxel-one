package dev.jayms.net.city;

import java.util.*;
import static dev.jayms.net.city.CityHistory.Kind.*;

/** Explicit measured catalog. No reflected names and no population-sized regional expansion. */
public final class HistoryCatalog {
    public static final int COMPANY_CAP=Math.max(1,Math.min(32,Integer.getInteger("voxel.history.companies",32))),
            PRODUCT_CAP=Math.max(1,Math.min(32,Integer.getInteger("voxel.history.products",32))),
            WORKPLACE_CAP=Math.max(1,Math.min(4,Integer.getInteger("voxel.history.workplaces",4))),
            DISTRICT_CAP=Math.max(1,Math.min(16,Integer.getInteger("voxel.history.districts",16)));
    public record Snapshot(List<CityHistory.Metric> metrics,Map<String,Double> values,Set<String> activeEntities) {
        public Snapshot {metrics=List.copyOf(metrics);values=Map.copyOf(values);activeEntities=Set.copyOf(activeEntities);}
    }
    private final List<CityHistory.Metric> metrics=new ArrayList<>();
    private final Map<String,Double> values=new LinkedHashMap<>();
    private void add(String id,String name,String unit,String domain,CityHistory.Kind kind,String scope,String definition,double value) {
        metrics.add(new CityHistory.Metric(id,name,unit,domain,kind,scope,definition,unit.equals("people")?0:unit.startsWith("product #")?6:2));
        if(Double.isFinite(value))values.put(id,value);
    }
    public static Snapshot from(CityFrame city) {
        var c=new HistoryCatalog();var m=CityMetrics.from(city);var e=city.economy();
        String scope="City: local measured residents plus regional cohort estimates";
        c.add("population.total","Residents","people","Population",STOCK,scope,"Measured local residents plus represented regional groups and agents",m.population());
        c.add("population.local","Local residents","people","Population",STOCK,"Local detailed residents","Local resident entities",city.citizens().size());
        c.add("population.regional","Regional residents","people","Population",ESTIMATE,"Regional estimates","Represented regional groups and agents",m.population()-city.citizens().size());
        c.add("population.housed","Housed residents","people","Population",STOCK,scope,"Residents with measured or estimated housing",m.housed());
        c.add("population.unhoused","Unhoused residents","people","Population",STOCK,scope,"Residents minus housed residents",m.population()-m.housed());
        c.add("population.beds","Local housing capacity","people","Population",STOCK,"Local measured housing","Completed local residential and farmhouse capacity; regional vacancy/capacity is not measured",city.buildings().stream().filter(b->b.type()==0||b.type()==3).mapToInt(CityFrame.Building::capacity).sum());
        c.add("population.employed","Employed residents","people","Population",STOCK,scope,"Residents with a measured or represented employer",m.employed());
        c.add("population.employment","All-resident employment","%","Population",RATIO,scope,"100 * employed residents / all residents; includes children; not eligible-worker employment",m.population()==0?Double.NaN:m.employmentPercent());
        c.add("population.hungry","Hungry residents","people","Population",ESTIMATE,scope,"Residents with hunger reserve below 35; regional cohort threshold is estimated",m.hungry());
        c.add("population.hunger","Mean hunger reserve","%","Population",ESTIMATE,scope,"Population-weighted hunger reserve; higher means better fed",m.population()==0?Double.NaN:m.averageHunger());
        c.add("population.savings","Household savings","$","Population",ESTIMATE,scope,"Sum of local savings plus regional represented savings",m.totalSavings());
        c.add("population.averageSavings","Mean household savings","$","Population",ESTIMATE,scope,"Total savings / represented residents",m.population()==0?Double.NaN:m.averageSavings());
        c.add("population.medianSavings","Estimated median savings","$","Population",ESTIMATE,scope,"Estimated weighted median of cohort means and local savings; interval summaries average estimates, never claim an exact interval median",m.population()==0?Double.NaN:m.medianSavings());
        for(var g:m.groups()) {
            String id="cohort."+g.cohort()+".",name=CitySimulation.COHORTS[g.cohort()];
            c.add(id+"population",name+" residents","people","Population",STOCK,scope,"Represented cohort residents",g.population());
            c.add(id+"employed",name+" employed","people","Population",STOCK,scope,"Represented employed cohort residents",g.employed());
            c.add(id+"employment",name+" all-resident employment","%","Population",RATIO,scope,"100 * cohort employed / cohort residents",g.population()==0?Double.NaN:100.0*g.employed()/g.population());
        }
        var districts=new TreeMap<Integer,double[]>();var regionalGroups=new HashMap<Integer,RegionalPopulation.Group>();
        for(var g:city.population().groups()) {
            regionalGroups.put(g.id(),g);var row=districts.computeIfAbsent(g.district(),k->new double[3]);
            row[0]+=g.count();row[1]+=g.employed();row[2]+=g.housed();
        }
        for(var agent:city.population().agents()) {
            var group=regionalGroups.get(agent.group());if(group==null)continue;
            var row=districts.computeIfAbsent(group.district(),k->new double[3]);row[0]++;if(agent.employed())row[1]++;if(agent.housed())row[2]++;
        }
        for(var entry:districts.entrySet().stream().limit(DISTRICT_CAP).toList()) {
            int id=entry.getKey();var row=entry.getValue();String prefix="district."+id+".",district="Regional district #"+id+" (cohort estimates)";
            c.add(prefix+"population",district+" residents","people","Population",ESTIMATE,district,"Represented group residents plus detailed regional agents, counted once",row[0]);
            c.add(prefix+"employed",district+" employed","people","Population",ESTIMATE,district,"Represented employed residents",row[1]);
            c.add(prefix+"employment",district+" employment","%","Population",RATIO,district,"100 * represented employed / all represented district residents",row[0]>0?100*row[1]/row[0]:Double.NaN);
        }
        c.add("finance.treasury","Treasury balance","$","City finances",STOCK,"City government","Current public cash balance; not profit",e.budget());
        c.add("finance.spending","Road spending","$","City finances",COUNTER,"City government","Period actual public road spending from ledger events; private buildings and free zoning excluded",e.roadSpending());
        c.add("finance.land","Land income","$","City finances",COUNTER,"City government","Period actual public land receipts from ledger events",e.landRevenue());
        c.add("finance.net","Land/road net flow","$","City finances",SIGNED_COUNTER,"City government land and roads","Land receipts minus actual road outlays; excludes initial budget and administrative grants",e.landRevenue()-e.roadSpending());
        Map<Integer,double[]> accounts=new HashMap<>();
        for(var a:e.businesses()) {
            var row=accounts.computeIfAbsent(a.company(),k->new double[6]);var t=a.total();
            row[0]+=t.revenue();row[1]+=t.wages();row[2]+=t.supplies();row[3]+=t.rent();row[4]+=t.expenses();row[5]+=t.profit();
        }
        var employees=new HashMap<Integer,Integer>();
        for(var resident:city.citizens()){int employer=CityMetrics.employer(city,resident);if(employer>0)employees.merge(employer,1,Integer::sum);}
        c.add("business.employees","Local company workforce","people","Businesses",STOCK,"Local detailed company staff","Assigned local residents, including construction crews; excludes regional estimated workers and public exchange staff",employees.values().stream().mapToInt(Integer::intValue).sum());
        double[] total=new double[6];for(var row:accounts.values())for(int i=0;i<6;i++)total[i]+=row[i];
        c.business("business.","All local company accounts",total);
        c.add("business.cash","Operating cash","$","Businesses",STOCK,"All companies","Sum of operating company cash, excluding developer balances; distinct from sales",e.firms().stream().filter(f->f.kind()!=CityEconomy.DEVELOPER).mapToDouble(CityEconomy.Firm::cash).sum());
        c.add("business.companies","Companies","companies","Businesses",STOCK,"City","Active company records",e.firms().size());
        for(var f:e.firms().stream().sorted(Comparator.comparingInt(CityEconomy.Firm::id)).limit(COMPANY_CAP).toList()) {
            c.business("company."+f.id()+".",f.name()+" #"+f.id(),accounts.getOrDefault(f.id(),new double[6]));
            c.add("company."+f.id()+".employees",f.name()+" employees","people","Businesses",STOCK,"Local detailed company staff","Assigned local residents, including construction crews",employees.getOrDefault(f.id(),0));
            c.add("company."+f.id()+".cash",f.name()+" cash","$","Businesses",STOCK,"Company #"+f.id(),"Company cash balance",f.cash());
        }
        for(var account:e.businesses().stream().sorted(Comparator.comparingInt(CityBusinesses.Record::building)).limit(WORKPLACE_CAP).toList()) {
            String prefix="workplace."+account.building()+"."+account.company()+".",location="Workplace #"+account.building()+" / company #"+account.company();
            var totals=account.total();c.business(prefix,location,new double[]{totals.revenue(),totals.wages(),totals.supplies(),totals.rent(),totals.expenses(),totals.profit()});
            var staff=city.citizens().stream().filter(worker->worker.job()==account.building()).sorted(Comparator.comparingInt(CityFrame.Citizen::id)).toList();
            long working=staff.stream().filter(worker->worker.activity().equals("Working in shop")||worker.activity().equals("Working in mine")||worker.activity().startsWith("Working: ")).count();
            c.add(prefix+"employees",location+" employees","people","Businesses",STOCK,"Local assigned detailed residents","Residents assigned to this workplace; no regional workers materialized",staff.size());
            var building=city.buildings().stream().filter(b->b.id()==account.building()).findFirst().orElse(null);
            if(building!=null) {
                int present=0;var time=city.config().time(city.elapsed());
                for(int i=0;i<staff.size();i++) {
                    var worker=staff.get(i);boolean shift=building.type()==1?CityBusinesses.shopShift(time.hour(),i):time.period()==CityTime.Period.WORKDAY;
                    if(shift&&worker.x()>building.x()&&worker.x()<building.x()+6&&worker.z()>building.z()&&worker.z()<building.z()+7)present++;
                }
                c.add(prefix+"onSite",location+" on site","people","Businesses",STOCK,"Local shift staff","Assigned staff in the same workplace bounds and shift used by the snapshot dashboard",present);
            }
            c.add(prefix+"working",location+" working","people","Businesses",STOCK,"Local measured work activity","Assigned residents with a measured working activity",working);
            c.add(prefix+"utilization",location+" working share","%","Businesses",RATIO,"Local assigned detailed staff","100 * currently working / assigned staff; not physical capacity utilization",staff.isEmpty()?Double.NaN:100.0*working/staff.size());
        }
        var stocks=new TreeMap<Integer,Double>();
        for(var s:e.resources().stocks())stocks.merge(s.material(),s.units()/(double)CityMaterials.UNIT,Double::sum);
        var products=new LinkedHashSet<Integer>();
        products.add(dev.jayms.net.Blocks.WOOD);products.add(dev.jayms.net.Blocks.STONE);
        products.add(CityMaterials.PICKAXE);products.add(CityMaterials.AXE);
        products.addAll(e.resources().catalog().food());
        e.resources().catalog().products().forEach(p->products.add(p.id()));
        e.resources().catalog().recipes().forEach(recipe->{products.add(recipe.output());products.addAll(recipe.inputs().keySet());});
        products.addAll(stocks.keySet());
        for(int product:products.stream().filter(e.resources().catalog()::valid).limit(PRODUCT_CAP).toList()) {
            String name=e.resources().catalog().name(product),unit="product #"+product+" units";
            c.add("resource."+product+".stock",name+" stock",unit,"Resources/production",STOCK,"Local owned inventory; regional estimates excluded","Inventory summed once by product; transfers are not production",stocks.getOrDefault(product,0.0));
            c.add("resource."+product+".production",name+" produced",unit,"Resources/production",COUNTER,"Local measured product output","Event-time harvested and recipe output; excludes transfers and initial seed inventory",0);
            c.add("resource."+product+".sales",name+" paid sales",unit,"Resources/production",COUNTER,"Local measured paid product trades","Product units in validated paid trades and purchased local meals, counted once per transaction",0);
            c.add("resource."+product+".consumption",name+" production inputs",unit,"Resources/production",COUNTER,"Local measured recipe and meal use","Event-time recipe inputs, operating power and consumed local household meals; excludes transfers",0);
        }
        c.add("development.roads","Road coverage","cells","Development",STOCK,"Local city","Measured explicit road cells or the implicit stress-grid road counter; not traffic volume",city.stressGrid()==null?city.roads().size():city.stressGrid().roadCells());
        c.add("development.completed","Completed buildings","buildings","Development",STOCK,"Local city","Existing completed building records",city.buildings().size());
        c.add("development.planned","Unfinished plots","plots","Development",STOCK,"Local city","Planned or active plots without completed buildings",e.plots().stream().filter(p->p.building()==0).count());
        c.add("development.vacant","Local vacant housing","people","Development",STOCK,"Local measured housing","Measured local beds minus locally housed residents; excludes unknown regional vacancy",m.vacantBeds());
        if(city.stressGrid()!=null) {
            int[] plots={400_000,200_000,200_000,200_000};
            for(int type=0;type<4;type++)c.add("development.zone."+type,"Zone type "+type+" area","m2","Development",STOCK,"Implicit stress grid","Exact rank-band plot count * plot area; no polygon materialization",(double)plots[type]*StressGrid.PLOT*StressGrid.PLOT);
        } else {
            var areas=new TreeMap<Integer,Double>();for(int type=0;type<4;type++)areas.put(type,0.0);
            for(var zone:city.zones()){var vertices=zone.polygon().vertices();double twice=0;for(int i=0;i<vertices.size();i++){var a=vertices.get(i);var b=vertices.get((i+1)%vertices.size());twice+=a.x()*b.z()-b.x()*a.z();}areas.merge(zone.type(),Math.abs(twice)/2,Double::sum);}
            for(var entry:areas.entrySet().stream().limit(16).toList())c.add("development.zone."+entry.getKey(),"Zone type "+entry.getKey()+" area","m2","Development",STOCK,"Local city","Measured polygon area by zone type; zoning is free",entry.getValue());
        }
        double available=e.plots().stream().filter(p->p.developer()==0&&p.building()==0).count();
        if(city.stressGrid()!=null){var acquired=new HashSet<Integer>();e.plots().stream().filter(p->p.developer()!=0&&p.zone()>0).forEach(p->acquired.add(p.zone()));city.buildings().stream().filter(b->b.zone()>0).forEach(b->acquired.add(b.zone()));available=Math.max(0,StressGrid.COUNT-acquired.size());}
        c.add("development.available","Available plots","plots","Development",STOCK,"Local city","Explicit unacquired plots or implicit grid plots minus distinct acquired/completed sites",available);
        c.add("development.active","Active construction","plots","Development",STOCK,"Local city","Acquired unfinished plots with measured construction progress",e.plots().stream().filter(p->p.developer()!=0&&p.building()==0&&p.work()>0).count());
        var activeEntities=new HashSet<String>();
        e.firms().forEach(f->activeEntities.add("company."+f.id()+"."));
        e.businesses().forEach(a->activeEntities.add("workplace."+a.building()+"."+a.company()+"."));
        return new Snapshot(c.metrics,c.values,activeEntities);
    }
    private void business(String prefix,String scope,double[] row) {
        String[] ids={"revenue","wages","inputs","rent","costs","profit"};
        String[] names={"Revenue","Wages","Input purchases","Rent","Operating costs","Operating profit"};
        for(int i=0;i<ids.length;i++) add(prefix+ids[i],scope+" / "+names[i],"$","Businesses",i==5?SIGNED_COUNTER:COUNTER,scope,
                i==5?"Period operating profit: sales minus wages, input purchases and rent, from signed ledger events":"Period flow from cumulative measured account events: "+names[i],row[i]);
    }
}
