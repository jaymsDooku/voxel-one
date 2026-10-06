package dev.jayms.ui;

import dev.jayms.net.city.*;
import java.util.*;
import java.util.function.*;

/** Population-scale controls never expand a region into one UI row per resident. */
public final class RegionalDashboard {
    public int firstRow;
    public void scroll(double amount) { firstRow=Math.max(0,firstRow-(int)Math.signum(amount)*3); }
    public static List<Integer> districts(CityFrame city) {
        return city.population().groups().stream().map(RegionalPopulation.Group::district).distinct().sorted().toList();
    }
    public void click(float x,float y,int w,int h,CityFrame city,Consumer<CityCommand> submit,IntConsumer select) {
        if(y>=166 && y<=198) {
            int[] sizes={1000,100_000,1_000_000};
            for(int i=0;i<3;i++) if(x>=24+i*190 && x<204+i*190)
                submit.accept(new CityCommand(CityCommand.SETTLE_DISTRICT,sizes[i],List.of()));
            if(x>=610 && x<850 && !city.population().agents().isEmpty())
                select.accept(city.population().agents().get(0).id());
            return;
        }
        if(y>=276 && y<h-60 && x>=24 && x<w-24) {
            var ids=districts(city);int index=firstRow+(int)((y-276)/30);
            if(index<ids.size()) submit.accept(new CityCommand(CityCommand.FOCUS_DISTRICT,ids.get(index),List.of()));
        }
    }
    public void render(Overlay ui,int w,int h,CityFrame city) {
        var state=city.population();
        ui.text("Regional residents: "+state.population()+" | Local residents: "+city.citizens().size()
                +" | Nearby agents: "+state.agents().size()+" / "+RegionalPopulation.MAX_AGENTS,24,140,1.3f);
        String[] buttons={"Settle 1,000","Settle 100,000","Settle 1,000,000","Inspect nearby resident"};
        for(int i=0;i<4;i++) {
            int x=i==3?610:24+i*190, width=i==3?240:180;
            ui.rectangle(x,166,width,32,.1f,.2f,.25f,1);ui.text(buttons[i],x+8,176,1.2f);
        }
        ui.text("Immigrants and regional firms bring savings, capital and four meals per resident.",24,211,1.15f);
        ui.text("Click a district to activate nearby residents. Inspect one to visit. Wheel: scroll districts.",24,233,1.15f);
        ui.text("District       Population       Housed       Employed       Hunger       Household savings",24,255,1.2f);
        var ids=districts(city);int rows=Math.max(1,(h-336)/30);
        firstRow=Math.max(0,Math.min(firstRow,Math.max(0,ids.size()-rows)));
        var groups=new HashMap<Integer,RegionalPopulation.Group>();state.groups().forEach(g->groups.put(g.id(),g));
        // One pass to collect district totals; draw only the visible page.
        var totals=new HashMap<Integer,double[]>();
        for(var g:state.groups()) {
            var t=totals.computeIfAbsent(g.district(),k->new double[6]);
            t[0]+=g.count();t[1]+=g.housed();t[2]+=g.employed();t[3]+=g.hunger()*g.count();t[4]+=g.savings();
        }
        for(var a:state.agents()) {
            var g=groups.get(a.group());var t=totals.get(g.district());
            t[0]++;t[1]+=a.housed()?1:0;t[2]+=a.employed()?1:0;t[3]+=a.hunger();t[4]+=a.savings();
        }
        for(int row=0;row<rows && firstRow+row<ids.size();row++) {
            int id=ids.get(firstRow+row),y=276+row*30;var t=totals.get(id);
            ui.rectangle(24,y,w-48,28,id==state.focus()?.12f:.055f,.12f,.16f,1);
            ui.text(String.format(Locale.ROOT,"#%-12d %,10.0f       %,8.0f       %,8.0f       %5.1f       $%,.0f",
                    id,t[0],t[1],t[2],t[0]==0?0:t[3]/t[0],t[4]),32,y+8,1.15f);
        }
        ui.text("Distant needs and savings are cohort/company averages. Nearby agents keep individual needs and wallets.",24,h-60,1.05f);
    }
}
