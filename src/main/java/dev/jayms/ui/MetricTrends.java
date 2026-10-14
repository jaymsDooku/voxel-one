package dev.jayms.ui;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Shared bounded native history chart. Every chart input stays inside the mayor overlay. */
public final class MetricTrends {
    public boolean open, live=true;
    private boolean legacyActive;
    private final SessionMetricTrends legacy=new SessionMetricTrends();
    public CityHistory.Reply reply;
    public String error="",search="";
    private static final String[] DOMAINS={"Population","Businesses","City finances","Resources/production","Development"};
    private final LinkedHashSet<String> selected=new LinkedHashSet<>(List.of("population.total"));
    private final Set<String> hidden=new HashSet<>();
    private List<CityHistory.Metric> choices=List.of();
    private int domain,range=1,resolution=1,focus=-1,selectorOffset,resolutionOverride=-1;
    private boolean selector,editing,customEditing;
    private String customText="";
    private int axisMode;
    private double start,end,day=1200;
    private boolean cycling=true,stale;
    private long request,lastQuery;
    private boolean dirty=true;
    private static final String[] KPIS={"population.total","population.housed","population.employed","population.hunger","population.averageSavings","population.hungry","finance.treasury","business.revenue","development.completed","business.profit","population.savings","population.unhoused"};
    private final Map<String,CityHistory.Series> overview=new HashMap<>();
    private int overviewIndex;
    private long overviewClock;
    private CityHistory.Reply overviewReply;
    private double overviewStart,overviewEnd,overviewSpan;
    private String overviewSelection="";
    private long overviewRequest=-1,pendingHistoryRequest=-1;
    public double overviewRangeStart(){return overviewStart;}
    public double overviewRangeEnd(){return overviewEnd;}
    public void overviewClick(int index) {
        if(index>=0&&index<KPIS.length){select(KPIS[index]);open=true;}
    }
    private void updateOverview(CityFrame city,CityHistory local,MultiplayerClient remote) {
        long now=System.nanoTime();
        String selection=(live?"Live:"+range:"Fixed:"+start+":"+end)+":"+city.config();
        if(!selection.equals(overviewSelection)) {
            overviewSelection=selection;overview.clear();overviewIndex=0;overviewClock=0;
            overviewRequest=-1;request++;
        }
        overviewSpan=live?day*new int[]{1,7,30,90,365}[range]:Math.max(0,end-start);
        int overviewResolution=live&&range==4?2:overviewSpan<=15*day?1:2;
        if(live) {
            double width=overviewResolution==1?day/24:day;
            double offset=city.config().cycle()?city.config().startHour()/24*day:0;
            overviewEnd=Math.max(0,Math.floor((city.elapsed()+offset)/width+1e-9)*width-offset);
            overviewStart=range==4?Math.max(0,start):Math.max(0,overviewEnd-overviewSpan);
            if(range==4)overviewSpan=Math.max(0,overviewEnd-overviewStart);
        } else {overviewStart=start;overviewEnd=end;}
        if(remote!=null&&overviewRequest==request&&remote.historyReply!=null&&remote.historyReply.request()==overviewRequest) {
            overviewReply=remote.historyReply;cityId=overviewReply.city();
            for(var s:overviewReply.series())overview.put(s.metric().id(),s);
        }
        if(remote!=null&&pendingHistoryRequest>=0&&remote.historyResponseRequest!=pendingHistoryRequest)return;
        if(remote!=null&&now-lastQuery<400_000_000L)return;
        if(now-overviewClock<400_000_000L)return;
        // Include the preceding equivalent period; never follow latest in a fixed view.
        double begin=Math.max(0,overviewStart-overviewSpan);
        String id=KPIS[overviewIndex++%KPIS.length];
        if(local!=null) {
            if(!local.identity().equals(cityId)){overview.clear();cityId=local.identity();reply=null;dirty=true;}
            overviewReply=local.query(cityId,++request,List.of(id),begin,Math.min(1e12,overviewEnd),overviewResolution,1000);
            overview.put(id,overviewReply.series().get(0));
        } else if(remote!=null) {
            List<String> ids=overviewReply==null?List.of():List.of(id);
            if(!remote.historyQuery(new HistoryWire.Request(cityId,++request,ids,begin,Math.min(1e12,overviewEnd),overviewResolution,1000)))error=remote.historyError;
            else pendingHistoryRequest=request;
            overviewRequest=request;lastQuery=now;
        }
        overviewClock=now;
    }
    public void sparkline(Overlay ui,int index,float x,float y,float width) {
        if(index>=KPIS.length)return;
        var s=overview.get(KPIS[index]);
        if(s==null){label(ui,error.isEmpty()?"History loading":error,x,y+75,width);return;}
        var visible=s.points().stream().filter(p->!p.gap()&&p.start()>=overviewStart&&p.end()<=overviewEnd).toList();
        double high=visible.stream().mapToDouble(CityHistory.Point::value).max().orElse(1);
        double low=visible.stream().mapToDouble(CityHistory.Point::value).min().orElse(0);
        float sx=x+width-75,sy=y+20,px=0,py=0;CityHistory.Point previous=null;
        double last=overviewEnd,begin=overviewStart;
        for(var p:s.points()) {
            if(p.gap()||p.start()<begin||p.end()>last){previous=null;continue;}
            float ax=sx+(float)((p.start()-begin)/Math.max(1,last-begin))*70,ay=sy+24-(float)((p.value()-low)/Math.max(1,high-low))*24;
            if(previous!=null&&p.start()<=previous.end()+1e-6)ui.line(px,py,ax,ay,1,.3f,.9f,.75f,1);
            px=ax;py=ay;previous=p;
        }
        label(ui,overviewComparison(index),x,y+75,width);
    }
    /** Same completed-period summary used by the rendered Overview card. */
    public String overviewComparison(int index) {
        var s=index>=0&&index<KPIS.length?overview.get(KPIS[index]):null;
        if(s==null)return error.isEmpty()?"History loading":error;
        double last=overviewEnd,span=overviewSpan,begin=overviewStart;
        var current=s.points().stream().filter(p->p.start()>=begin&&p.end()<=last&&!p.gap()&&!p.partial()).toList();
        var prior=s.points().stream().filter(p->p.start()>=begin-span&&p.end()<=begin&&!p.gap()&&!p.partial()).toList();
        String period=!live?"Fixed "+java.math.BigDecimal.valueOf(begin).stripTrailingZeros().toPlainString()+"-"+java.math.BigDecimal.valueOf(last).stripTrailingZeros().toPlainString()+"s":cycling?new String[]{"1d","7d","30d","90d","available"}[range]:String.format(Locale.ROOT,"%.0fs",span);
        if(live&&begin>=span&&!s.points().isEmpty()&&s.points().stream().mapToDouble(CityHistory.Point::end).max().orElse(0)<last-1e-6)
            return period+" comparison: updating";
        String comparison=period+" comparison: insufficient coverage";
        if(span>0&&current.stream().mapToDouble(CityHistory.Point::coverage).sum()>=span-1e-6&&prior.stream().mapToDouble(CityHistory.Point::coverage).sum()>=span-1e-6) {
            boolean flow=s.metric().kind()==CityHistory.Kind.COUNTER||s.metric().kind()==CityHistory.Kind.SIGNED_COUNTER;
            double a=flow?current.stream().mapToDouble(CityHistory.Point::value).sum():current.stream().mapToDouble(CityHistory.Point::numerator).sum()/Math.max(1e-12,current.stream().mapToDouble(CityHistory.Point::denominator).sum());
            double b=flow?prior.stream().mapToDouble(CityHistory.Point::value).sum():prior.stream().mapToDouble(CityHistory.Point::numerator).sum()/Math.max(1e-12,prior.stream().mapToDouble(CityHistory.Point::denominator).sum());
            comparison=String.format(Locale.ROOT,"%s completed %s %+.2f %s",period,flow?"total":"mean",a-b,s.metric().unit());
        }
        return comparison;
    }
    private UUID cityId;
    private Object provider;
    private float mouseX=-1,mouseY=-1,left,right,top,bottom;
    public void selectDomain(int value) {
        legacy.selectDomain(Math.min(3,value));
        domain=Math.max(0,Math.min(4,value));selector=true;editing=true;search="";selectorOffset=0;
    }
    public Set<String> selected(){return Set.copyOf(selected);}
    public double rangeStart(){return start;}
    public double rangeEnd(){return end;}
    public int resolution(){return resolution;}
    public void select(String id){selected.clear();selected.add(id);hidden.clear();dirty=true;selector=false;editing=false;focus=-1;}
    public void hover(float x,float y){mouseX=x;mouseY=y;}
    public void update(CityFrame city,CityHistory local,MultiplayerClient remote) {
        Object nextProvider=local!=null?local:remote;
        if(provider!=nextProvider) {
            provider=nextProvider;cityId=null;reply=null;overviewReply=null;overview.clear();overviewSelection="";overviewRequest=-1;pendingHistoryRequest=-1;
            selected.clear();selected.add("population.total");hidden.clear();domain=0;live=true;dirty=true;error="";lastQuery=0;
        }
        stale=remote!=null&&!remote.connected();
        if(stale)return;
        legacyActive=remote!=null&&remote.serverProtocol<30;
        if(legacyActive){error="Legacy server: snapshot and client-session Trends only";return;}
        day=city.config().daySeconds();cycling=city.config().cycle();
        if(!open){updateOverview(city,local,remote);return;}
        if(local!=null&&!local.identity().equals(cityId)) {
            cityId=local.identity();reply=null;selected.clear();selected.add("population.total");hidden.clear();live=true;dirty=true;
        }
        if(remote!=null&&remote.historyResponseRequest==request) {
            error=remote.historyError;
            if(remote.historyReply!=null){reply=remote.historyReply;cityId=reply.city();if(live&&range==4)start=reply.availableStart();if(reply.series().isEmpty())dirty=true;}
        }
        if(reply!=null)choices=reply.catalog().stream().filter(m->m.domain().equals(DOMAINS[domain]))
                .filter(m->(m.name()+" "+m.id()+" "+m.scope()).toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))).toList();
        long now=System.nanoTime();
        if(remote!=null&&pendingHistoryRequest>=0&&remote.historyResponseRequest!=pendingHistoryRequest)return;
        if(remote!=null&&now-lastQuery<400_000_000L)return;
        if(!dirty&&(!live || now-lastQuery<1_000_000_000L))return;
        if(live) {end=city.elapsed();start=range==4?(reply==null?0:reply.availableStart()):Math.max(0,end-day*new int[]{1,7,30,90,365}[range]);}
        resolution=resolutionOverride>=0?resolutionOverride:live&&range==4?2:end-start<=day?0:end-start<=30*day?1:2;
        var ids=reply==null?List.<String>of():new ArrayList<>(selected);
        var query=new HistoryWire.Request(cityId,++request,ids,live&&range==4?0:start,Math.min(1e12,Math.max(start,end)+(live?Math.max(1,day/288):0)),resolution,1000);
        try {
            if(local!=null) {
                reply=local.query(query.city(),query.request(),query.ids(),query.start(),query.end(),query.resolution(),query.limit());
                cityId=reply.city();if(live&&range==4)start=reply.availableStart();if(ids.isEmpty()){dirty=true;return;}
            } else if(remote!=null) {
                if(!remote.historyQuery(query)){error=remote.historyError;lastQuery=now;dirty=false;return;}
                pendingHistoryRequest=request;
            }
            error="";
        }catch(IllegalArgumentException failure){error=failure.getMessage();}
        lastQuery=now;dirty=false;
    }
    public void key(int key,int action) {
        if(action!=GLFW_PRESS&&action!=GLFW_REPEAT)return;
        if(customEditing) {
            if(key==GLFW_KEY_BACKSPACE&&!customText.isEmpty())customText=customText.substring(0,customText.length()-1);
            if(key==GLFW_KEY_ESCAPE)customEditing=false;
            if(key==GLFW_KEY_ENTER) {
                try {var fields=customText.split(",");double a=Double.parseDouble(fields[0].trim()),b=Double.parseDouble(fields[1].trim());
                    if(fields.length!=2||!Double.isFinite(a)||!Double.isFinite(b)||a<0||b<=a||b>1e12)throw new IllegalArgumentException();
                    start=a;end=b;live=false;dirty=true;customEditing=false;error="";
                }catch(RuntimeException invalid){error="Enter start,end elapsed simulation seconds; end must exceed start";}
            }
            return;
        }
        if(editing) {
            if(key==GLFW_KEY_BACKSPACE&&!search.isEmpty()){search=search.substring(0,search.length()-1);selectorOffset=0;}
            if(key==GLFW_KEY_ESCAPE){selector=false;editing=false;}
            if(key==GLFW_KEY_ENTER&&!choices.isEmpty())choose(choices.get(Math.min(selectorOffset,choices.size()-1)));
            if(key==GLFW_KEY_DOWN)selectorOffset=Math.min(Math.max(0,choices.size()-1),selectorOffset+1);
            if(key==GLFW_KEY_UP)selectorOffset=Math.max(0,selectorOffset-1);
            return;
        }
        if(key==GLFW_KEY_D){resolutionOverride=2;dirty=true;}
        if(key==GLFW_KEY_H){resolutionOverride=1;dirty=true;}
        if(key==GLFW_KEY_M){resolutionOverride=0;dirty=true;}
        if(key==GLFW_KEY_C){customEditing=true;customText=String.format(Locale.ROOT,"%.0f,%.0f",start,end);}
        if(key==GLFW_KEY_B)axisMode=(axisMode+1)%3;
        if(key==GLFW_KEY_LEFT){live=false;focus=Math.max(0,focus-1);}
        if(key==GLFW_KEY_RIGHT){live=false;focus++;}
        if(key==GLFW_KEY_HOME){live=true;dirty=true;focus=-1;}
        if(key==GLFW_KEY_EQUAL)zoom(.5);
        if(key==GLFW_KEY_MINUS)zoom(2);
        if(key==GLFW_KEY_PAGE_UP)pan(-.5);
        if(key==GLFW_KEY_PAGE_DOWN)pan(.5);
        if(key==GLFW_KEY_0)hidden.clear();
    }
    public void character(int c){if(customEditing&&c>=32&&c<=126&&customText.length()<64){customText+=(char)c;return;}if(editing&&c>=32&&c<=126&&search.length()<48){search+=(char)c;selectorOffset=0;}}
    public void scroll(double amount){if(selector){selectorOffset=Math.max(0,Math.min(Math.max(0,choices.size()-1),selectorOffset-(int)Math.signum(amount)));}else zoom(amount>0?.8:1.25);}
    private void zoom(double factor){live=false;double middle=(start+end)/2,span=Math.min(1e12,Math.max(day/288,(end-start)*factor));start=Math.max(0,Math.min(1e12-span,middle-span/2));end=start+span;dirty=true;}
    private void pan(double direction){live=false;double shift=Math.max(-start,Math.min(1e12-end,(end-start)*direction));start+=shift;end+=shift;dirty=true;}
    private void choose(CityHistory.Metric m) {
        var first=reply==null?null:reply.catalog().stream().filter(v->selected.contains(v.id())).findFirst().orElse(null);
        if(selected.contains(m.id())){if(selected.size()>1)selected.remove(m.id());}
        else if(first!=null&&first.compatible(m)){if(selected.size()>=4){error="Compare at most four compatible series";return;}selected.add(m.id());}
        else {selected.clear();selected.add(m.id());}
        selector=false;editing=false;dirty=true;focus=-1;
    }
    public void click(float x,float y,int w) {
        if(legacyActive){legacy.click(x,y,w);return;}
        if(x<24||x>w-24)return;
        if(y>=138&&y<=168){selectDomain((int)((x-24)/((w-48)/5f)));return;}
        if(y>=178&&y<=206) {
            int i=(int)((x-24)/((w-48)/8f));
            if(i<5){range=i;live=true;}else if(i==5)live=!live;else if(i==6){live=true;focus=-1;}else{selector=!selector;editing=selector;}
            dirty=true;return;
        }
        if(selector&&y>=246&&y<396) {
            int row=(int)((y-246)/30)+selectorOffset;if(row<choices.size())choose(choices.get(row));return;
        }
        if(y>=218&&y<=240&&reply!=null) {
            int i=(int)((x-24)/((w-48)/4f));if(i<reply.series().size()){String id=reply.series().get(i).metric().id();if(!hidden.add(id))hidden.remove(id);}return;
        }
        if(x>=left&&x<=right&&y>=top&&y<=bottom){live=false;focus=nearest(x);}
    }
    private int nearest(float x) {
        if(reply==null||reply.series().isEmpty())return -1;
        var points=reply.series().get(0).points();int chosen=-1;double distance=Double.POSITIVE_INFINITY;
        double at=start+(x-left)/Math.max(1,right-left)*(end-start);
        for(int i=0;i<points.size();i++){double d=Math.abs((points.get(i).start()+points.get(i).end())/2-at);if(d<distance){distance=d;chosen=i;}}
        return chosen;
    }
    private static void label(Overlay ui,String s,float x,float y,float width) {
        while(!s.isEmpty()&&ui.textWidth(s,1.1f)>width)s=s.substring(0,s.length()-1);
        ui.text(s,x,y,1.1f);
    }
    private static final float[][] COLORS={{.3f,.9f,.75f},{1,.72f,.3f},{.5f,.7f,1},{1,.5f,.75f}};
    public void render(Overlay ui,int w,int h,CityFrame city,MetricHistory ignored) {
        if(legacyActive){legacy.render(ui,w,h,city,ignored);return;}
        for(int i=0;i<5;i++){float x=24+i*(w-48)/5f;ui.rectangle(x,138,(w-48)/5f-4,30,.055f,.085f,.115f,1);label(ui,DOMAINS[i],x+5,148,(w-48)/5f-10);if(domain==i)ui.rectangle(x,166,(w-48)/5f-4,2,.3f,.9f,.75f,1);}
        String[] ranges=cycling?new String[]{"Last day","7 days","30 days","90 days","Available",live?"Live":"Fixed","Reset range","Find / compare"}:new String[]{String.format(Locale.ROOT,"%.0fs",day),String.format(Locale.ROOT,"%.0fs",7*day),String.format(Locale.ROOT,"%.0fs",30*day),String.format(Locale.ROOT,"%.0fs",90*day),"Available",live?"Live":"Fixed","Reset range","Find / compare"};
        for(int i=0;i<8;i++){float x=24+i*(w-48)/8f;ui.rectangle(x,178,(w-48)/8f-4,28,.055f,.085f,.115f,1);label(ui,ranges[i],x+4,188,(w-48)/8f-8);}
        if(reply==null){label(ui,error.isEmpty()?"Loading authoritative history...":error,36,260,w-72);return;}
        if(!error.isEmpty()){label(ui,error,36,260,w-72);if(!selector&&!customEditing)return;}
        left=105;right=w-30;top=305;bottom=Math.max(top+40,h-130);
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        boolean percent=!reply.series().isEmpty()&&reply.series().get(0).metric().unit().equals("%");
        for(var s:reply.series())if(!hidden.contains(s.metric().id()))for(var p:s.points())if(!p.gap()&&Double.isFinite(p.value())){min=Math.min(min,p.min());max=Math.max(max,p.max());}
        if(percent&&axisMode==0){min=0;max=100;}else if(axisMode==1){min=Math.min(0,min);max=Math.max(1,max);}else if(!Double.isFinite(min)||!Double.isFinite(max)){min=0;max=1;}else if(min==max){double pad=Math.max(1,Math.abs(min)*.05);min-=pad;max+=pad;}
        for(int i=0;i<=4;i++){float y=top+(bottom-top)*i/4;ui.rectangle(left,y,right-left,1,.12f,.18f,.22f,1);label(ui,String.format(Locale.ROOT,"%.2f",max-(max-min)*i/4),24,y-4,76);}
        for(int i=0;i<=4;i++){float x=left+(right-left)*i/4;ui.rectangle(x,top,1,bottom-top,.1f,.15f,.2f,1);String tick=timeLabel(start+(end-start)*i/4);float tickWidth=Math.min(ui.textWidth(tick,1.1f),(right-left)/4);float tickX=Math.max(left,Math.min(right-tickWidth,x-tickWidth/2));label(ui,tick,tickX,bottom+8,right-tickX);}
        int count=0;
        for(var s:reply.series()) {
            float[] color=COLORS[count];float lx=24+count*(w-48)/4f;
            ui.rectangle(lx,219,10,10,color[0],color[1],color[2],1);label(ui,(hidden.contains(s.metric().id())?"[off] ":"")+new String[]{"[solid] ","[dash] ","[dash2] ","[dash3] "}[count]+s.metric().name(),lx+14,220,(w-48)/4f-20);
            float px=0,py=0;CityHistory.Point before=null;int index=0;
            if(!hidden.contains(s.metric().id()))for(var p:s.points()) {
                if(p.gap()||!Double.isFinite(p.value())){before=null;continue;}
                float x=left+(float)(((Math.max(p.start(),p.coverageEnd()-p.coverage())+p.coverageEnd())/2-start)/Math.max(1,end-start))*(right-left);
                float y=bottom-(float)((p.value()-min)/Math.max(1e-9,max-min))*(bottom-top);
                y=Math.max(top,Math.min(bottom,y));
                if(x<left||x>right){before=null;continue;}
                if(before!=null&&before.epoch()==p.epoch()&&p.start()<=before.end()+1e-6) {
                    if(count==0)ui.line(px,py,x,y,2,color[0],color[1],color[2],1);
                    else {int steps=Math.max(1,(int)(Math.hypot(x-px,y-py)/6));for(int j=0;j<steps;j++)if(j%(count+1)==0){float a=j/(float)steps,b=Math.min(1,(j+.7f)/steps);ui.line(px+(x-px)*a,py+(y-py)*a,px+(x-px)*b,py+(y-py)*b,2,color[0],color[1],color[2],1);}}
                }
                if(p.max()>p.min()) {
                    float high=bottom-(float)((p.max()-min)/Math.max(1e-9,max-min))*(bottom-top),low=bottom-(float)((p.min()-min)/Math.max(1e-9,max-min))*(bottom-top);
                    ui.line(x,Math.max(top,Math.min(bottom,high)),x,Math.max(top,Math.min(bottom,low)),1,color[0],color[1],color[2],.3f);
                }
                ui.rectangle(x-2,y-2,4,4,color[0],color[1],color[2],1);px=x;py=y;before=p;index++;
            }
            count++;
        }
        if(reply.series().isEmpty())label(ui,"No measured series selected. Use Find / compare.",left,top+30,right-left);
        else {
            var first=reply.series().get(0);label(ui,first.metric().name()+" ("+first.metric().unit()+") | "+first.metric().scope(),36,249,w-72);
            label(ui,first.metric().definition(),36,270,w-72);
            label(ui,first.metric().aggregation()+" | "+first.metric().source(),36,287,w-72);
            boolean noData=reply.series().stream().allMatch(s->s.points().stream().allMatch(CityHistory.Point::gap));
            if(noData)label(ui,"No covered values in this range. No missing values are replaced with zero.",left,top+30,right-left);
        }
        int inspect=focus>=0?focus:mouseX>=left&&mouseX<=right&&mouseY>=top&&mouseY<=bottom?nearest(mouseX):-1;
        if(inspect>=0&&!reply.series().isEmpty()&&!reply.series().get(0).points().isEmpty()) {
            var points=reply.series().get(0).points();inspect=Math.min(inspect,points.size()-1);focus=focus<0?-1:inspect;var p=points.get(inspect);
            float x=Math.max(left,Math.min(right,left+(float)(((Math.max(p.start(),p.coverageEnd()-p.coverage())+p.coverageEnd())/2-start)/Math.max(1,end-start))*(right-left)));
            ui.rectangle(x,top,1,bottom-top,.8f,.9f,1,1);
            float boxWidth=Math.min(480,right-left),boxX=Math.min(right-boxWidth,Math.max(left,x+8));
            float tipTop=Math.max(138,Math.min(top+8,h-110-(32+reply.series().size()*22)));
            ui.rectangle(boxX,tipTop,boxWidth,32+reply.series().size()*22,.02f,.03f,.05f,1);
            label(ui,timeLabel(p.start(),p.epoch())+" -> "+timeLabel(p.end(),p.epoch())+" | epoch "+p.epoch()+" summary",boxX+8,tipTop+8,boxWidth-16);
            int i=0;for(var series:reply.series()) {
                final double pointStart=p.start();var match=series.points().stream().filter(v->v.start()==pointStart&&v.epoch()==p.epoch()).findFirst().orElse(null);
                String value=match==null||match.gap()?"Missing":Double.toString(match.value())+" "+series.metric().unit()+(match.partial()?" [partial]":"");
                label(ui,value+(match==null||match.gap()?"":" | min/max "+String.format(Locale.ROOT,"%.2f/%.2f",match.min(),match.max())+" | "+String.format(Locale.ROOT,"%.1fs covered",match.coverage()))+" | "+series.metric().name(),boxX+8,tipTop+32+i++*22,boxWidth-16);
            }
        }
        if(!reply.series().isEmpty()) {
            var s=reply.series().get(0);var valid=s.points().stream().filter(p->!p.gap()).toList();
            if(!valid.isEmpty())label(ui,String.format(Locale.ROOT,"Summary: %d covered buckets | last %.2f %s | axis %s | min %.2f / max %.2f",valid.size(),valid.get(valid.size()-1).value(),s.metric().unit(),percent&&axisMode==0?"0-100":axisMode==1?"includes zero":"automatic",min,max),24,h-103,w-48);
        }
        label(ui,String.format(Locale.ROOT,"%s | %s summaries | coverage begins %s | series cap 576",stale?"Cached / disconnected":live?"Live":"Fixed",(cycling?new String[]{"5-minute","hourly","daily"}[resolution]:String.format(Locale.ROOT,"%.1fs elapsed",day/new double[]{288,24,1}[resolution])),timeLabel(reply.availableStart())),24,h-82,w-48);
        label(ui,reply.note(),24,h-60,w-48);
        label(ui,"Arrows: inspect | +/-: zoom | PgUp/Dn: pan | Home: Live | C: custom | B: axis | D/H/M: resolution | legend: toggle",24,h-38,w-48);
        if(customEditing){ui.rectangle(24,214,w-48,70,.03f,.06f,.08f,1);label(ui,"Custom simulation range: start,end elapsed seconds",36,226,w-72);label(ui,customText+"_ | Enter applies",36,252,w-72);}
        if(selector) {
            ui.rectangle(24,214,w-48,184,.03f,.06f,.08f,1);label(ui,"Search: "+search+"_ | Enter selects; compatible series add up to four",36,226,w-72);
            for(int i=0;i<5&&i+selectorOffset<choices.size();i++){var m=choices.get(i+selectorOffset);label(ui,(selected.contains(m.id())?"[x] ":"[ ] ")+m.name()+" | "+m.unit()+" | "+m.scope(),36,256+i*30,w-72);}
            if(choices.isEmpty())label(ui,"No measured entities match this search.",36,256,w-72);
        }
    }
    private String timeLabel(double at,int epochId) {
        var epoch=reply.epochs().get(epochId);
        if(!epoch.cycle())return Double.toString(at)+"s frozen";
        long seconds=Math.round(epoch.startHour()*3600+at*86400/epoch.daySeconds());
        long clockSeconds=Math.floorMod(seconds,86400);
        return String.format(Locale.ROOT,"D%d %02d:%02d:%02d",1+Math.floorDiv(seconds,86400),clockSeconds/3600,clockSeconds/60%60,clockSeconds%60);
    }
    private String timeLabel(double at) {
        if(reply==null||reply.epochs().isEmpty())return String.format(Locale.ROOT,"%.0fs",at);
        var epoch=reply.epochs().get(0);for(var e:reply.epochs())if(e.start()<=at)epoch=e;
        return epoch.cycle()?String.format(Locale.ROOT,"D%d %.1fh",CityTime.at(new GameConfig(true,true,epoch.daySeconds(),epoch.startHour()),at).day(),CityTime.at(new GameConfig(true,true,epoch.daySeconds(),epoch.startHour()),at).hour()):String.format(Locale.ROOT,"%.0fs frozen",at);
    }
}
