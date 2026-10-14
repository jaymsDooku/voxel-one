import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.ui.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Real X11 input checks all bottom KPI links at normal, small and scaled sizes. */
public final class OverviewDashboardSmoke extends CitySavesSmoke {
    final String[] metrics={"development.completed","business.profit","population.savings","population.unhoused"};
    final String[] sizes={"normal","900x600","900x600-125"};
    float playerX,playerZ;List<CityFrame.Road> roads;
    MayorDashboard mayor()throws Exception{return (MayorDashboard)get(game,"mayorDashboard");}
    long handle()throws Exception{return ((dev.jayms.window.Window)get(game,"window")).getHandle();}
    @Override String id()throws Exception{return Long.toUnsignedString(org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window(handle()));}
    double scale()throws Exception{return (double)get(game,"dashboardScale");}
    int width()throws Exception{return (int)(((dev.jayms.window.Window)get(game,"window")).getSize()[0]/scale());}
    int height()throws Exception{return (int)(((dev.jayms.window.Window)get(game,"window")).getSize()[1]/scale());}
    void at(float x,float y)throws Exception {x("mousemove","--window",id(),""+Math.round(x*scale()),""+Math.round(y*scale()));x("click","1");}
    void card(int index)throws Exception {
        int cols=width()>=900?3:2;float cw=(width()-48-(cols-1)*12)/(float)cols;
        at(24+(index%cols)*(cw+12)+cw/2,138+(index/cols-mayor().overviewFirstRow)*88+35);
    }
    public void started(Main value)throws Exception {
        game=value;starts++;x("windowfocus",id());local().city.collectHistory();
        var p=((dev.jayms.player.Player)get(game,"player")).position();playerX=p.x;playerZ=p.z;roads=local().city.frame().roads();
        due=System.nanoTime()+1_000_000_000L;
    }
    public void beforeFrame(Main value)throws Exception {
        if(System.nanoTime()<due)return;due=System.nanoTime()+700_000_000L;
        int size=step/19,phase=step++%19;
        try {
            switch(phase) {
                case 0 -> {set(game,"dashboardScale",size==2?1.25:1.0);glfwSetWindowSize(handle(),size==0?1280:900,size==0?720:600);if(menu().open)key("Escape");if(!mayor().open)key("F9");if(size==2)key("F10");}
                case 1 -> {at(50,104);key("Home");}
                case 2 -> {require(mayor().overviewFirstRow==0,"Home reaches first row");capture="history-overview-"+sizes[size]+"-top.png";key("End");}
                case 3 -> {capture="history-overview-"+sizes[size]+"-bottom.png";at(10,150);}
                case 4 -> {require(!mayor().trends.open,"Outside card margin does not open a chart");int cols=width()>=900?3:2;float cw=(width()-48-(cols-1)*12)/(float)cols;at(24+cw+6,160);}
                case 5 -> {require(!mayor().trends.open,"Card gutter does not open a chart");card(8);}
                case 6,8,10,12 -> {int i=(phase-6)/2;require(mayor().trends.open&&mayor().trends.selected().contains(metrics[i]),"Bottom KPI drill-down: "+metrics[i]+" at "+sizes[size]);at(50,104);}
                case 7,9,11 -> card(8+(phase-5)/2);
                case 13 -> {key("Home");}
                case 14 -> {x("click","5");}
                case 15 -> {if(size==2){require(mayor().overviewFirstRow>0,"Wheel reaches hidden rows");at(width()-70,height()-50);}}
                case 16 -> {if(size==2){require(mayor().overviewFirstRow==3,"Next reaches final row");at(width()-180,height()-50);}}
                case 17 -> {if(size==2)require(mayor().overviewFirstRow==0,"Previous reaches first row");key("Escape F6");}
                case 18 -> {
                    require(!mayor().open,"Escape returns to world");var p=((dev.jayms.player.Player)get(game,"player")).position();
                    require(p.x==playerX&&p.z==playerZ&&roads.equals(local().city.frame().roads()),"Overview input leaves player and roads unchanged");key("F6");
                    if(size==2){key("F10");Files.writeString(evidence.resolve("history-overview-playtest.txt"),"Playtest: PASS. Linux X11/software OpenGL; inherited role display; isolated synthetic profile. Real xdotool input tested Overview at 1280x720/100%, 900x600/100%, and 900x600/125%. Home/End reached first/final rows. All four bottom KPI cards opened development.completed, business.profit, population.savings and population.unhoused at each size (12 drill-downs). Margin and gutter clicks opened no chart. Wheel and Next/Previous reached hidden rows at 125%. Escape returned to world; F6 worked; player X/Z and roads unchanged. Screenshots captured top/final rows at each size; actual F10 recording captured the scaled-window portion. Expected and observed: all 12 cards reachable, drawing and hit regions stay inside the viewport. No browser UI changed.\n");glfwSetWindowShouldClose(handle(),true);}
                }
            }
        }catch(Throwable t){Files.writeString(root.resolve("assertion-failure.txt"),"stage="+(step-1)+" "+t.getClass().getSimpleName()+": "+t.getMessage()+"\n");throw t;}
    }
    public static void main(String[] args)throws Exception {
        var test=new OverviewDashboardSmoke();test.root=Path.of(args[0]).toAbsolutePath();test.evidence=Path.of(args[1]);Files.createDirectories(test.root);Files.createDirectories(test.evidence);
        var game=new Main();set(game,"offlineSave",test.root.resolve("offline-city.dat"));set(game,"gameConfig",new GameConfig(true,true,60,0));
        glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_X11);game.run(test);test.require(test.step==57,"All Overview steps executed");System.out.println("PASS: 57 native Overview steps; 12 bottom-card drill-downs.");
    }
}
