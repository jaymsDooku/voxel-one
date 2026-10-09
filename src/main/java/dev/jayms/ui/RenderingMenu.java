package dev.jayms.ui;

import dev.jayms.render.*;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

/** Live quality controls. Uses the same coordinates for drawing and hit testing. */
public final class RenderingMenu {
    private record Option(String field, String label, float min, float max, float step) {}
    private static final Option[] OPTIONS = {
        new Option("renderScale", "Render scale (lower is faster)", .5f, 1, .05f),
        new Option("dynamicResolution", "Dynamic resolution",0,0,0),
        new Option("targetFrameMillis", "Frame budget (ms)",8.33f,50,8.33f),
        new Option("atmosphereQuality", "Atmosphere quality",0,0,0),
        new Option("shadows", "World shadows",0,0,0),
        new Option("particles", "Light particles",0,0,0),
        new Option("taa", "Temporal antialiasing",0,0,0),
        new Option("ao", "Ambient occlusion",0,0,0),
        new Option("contactShadows", "Contact shadows",0,0,0),
        new Option("reflections", "Screen reflections",0,0,0),
        new Option("screenGi", "Screen global illumination",0,0,0),
        new Option("volumetrics", "Volumetric light",0,0,0),
        new Option("bloom", "Bloom",0,0,0),
        new Option("clouds", "Clouds",0,0,0),
        new Option("autoExposure", "Auto exposure",0,0,0),
        new Option("exposure", "Exposure",.1f,4,.1f),
        new Option("saturation", "Saturation",0,2,.1f),
        new Option("contrast", "Contrast",.5f,2,.1f),
        new Option("fogDensity", "Fog density",0,.02f,.001f),
        new Option("cloudCoverage", "Cloud coverage",0,1,.05f),
        new Option("overviewHaze", "Overview haze",0,1,.1f)
    };
    public boolean open;
    private final RenderPipeline pipeline;
    private final Path file;
    private int selected, offset, visible=10;
    private String message="Changes apply now and save automatically.";
    public RenderingMenu(RenderPipeline pipeline) {
        this.pipeline=pipeline;
        file=Path.of(System.getProperty("user.home"),".voxel-one","rendering.properties");
        load();
    }
    public void show(){open=true;}
    public void scroll(double direction){select(selected-(int)Math.signum(direction));}
    private void select(int value){selected=Math.max(0,Math.min(OPTIONS.length-1,value));offset=Math.max(0,Math.min(offset,selected));if(selected>=offset+visible)offset=selected-visible+1;}
    public void key(int key,int action){
        if(action!=GLFW_PRESS&&action!=GLFW_REPEAT)return;
        if(key==GLFW_KEY_ESCAPE)open=false;
        else if(key==GLFW_KEY_UP)select(selected-1);
        else if(key==GLFW_KEY_DOWN)select(selected+1);
        else if(key==GLFW_KEY_LEFT)change(-1);
        else if(key==GLFW_KEY_RIGHT||key==GLFW_KEY_ENTER)change(1);
        else if(key==GLFW_KEY_1)preset(true);
        else if(key==GLFW_KEY_2)preset(false);
    }
    private Object value(Option o)throws ReflectiveOperationException{return RenderSettings.class.getField(o.field).get(pipeline.settings);}
    private void assign(Option o,Object value)throws ReflectiveOperationException{RenderSettings.class.getField(o.field).set(pipeline.settings,value);}
    private void change(int direction){
        try {
            Option o=OPTIONS[selected];Object v=value(o);
            if(v instanceof Boolean b)assign(o,!b);
            else if(v instanceof PlanetAtmosphere.Quality q){var all=PlanetAtmosphere.Quality.values();assign(o,all[Math.floorMod(q.ordinal()+direction,all.length)]);}
            else assign(o,Math.max(o.min,Math.min(o.max,(Float)v+direction*o.step)));
            save();
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private void preset(boolean low){
        RenderSettings defaults=new RenderSettings();
        try {for(Option o:OPTIONS)assign(o,RenderSettings.class.getField(o.field).get(defaults));}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        if(low){var s=pipeline.settings;s.renderScale=.5f;s.atmosphereQuality=PlanetAtmosphere.Quality.LOW;s.shadows=s.particles=s.ao=s.contactShadows=s.reflections=s.screenGi=s.volumetrics=s.bloom=s.clouds=false;}
        save();
    }
    private void save(){
        pipeline.resetHistory();
        try {Properties p=new Properties();for(Option o:OPTIONS)p.setProperty(o.field,value(o).toString());Files.createDirectories(file.getParent());Path temporary=file.resolveSibling("rendering.properties.tmp");try(var out=Files.newOutputStream(temporary)){p.store(out,"Voxel rendering quality");}Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);message="Saved. Changes are live.";}
        catch(Exception e){message="Changes applied; could not save settings.";}
    }
    private void load(){
        if(!Files.isRegularFile(file))return;
        try{Properties p=new Properties();try(var in=Files.newInputStream(file)){p.load(in);}
            for(Option o:OPTIONS){String raw=p.getProperty(o.field);if(raw==null)continue;
                try{Object old=value(o);Object v;
                    if(old instanceof Boolean){if(!raw.equals("true")&&!raw.equals("false"))continue;v=Boolean.parseBoolean(raw);}
                    else if(old instanceof PlanetAtmosphere.Quality)v=PlanetAtmosphere.Quality.valueOf(raw);
                    else{float n=Float.parseFloat(raw);if(!Float.isFinite(n))continue;v=Math.max(o.min,Math.min(o.max,n));}assign(o,v);
                }catch(IllegalArgumentException ignored){}
            }
        }catch(Exception e){message="Could not load saved settings; using defaults.";}
    }
    private float left(int w){return Math.max(8,(w-Math.min(650,w-16))/2f);}
    private int rows(int h){return Math.max(1,Math.min(OPTIONS.length,(h-160)/27));}
    public void click(int button,float x,float y,int w,int h){
        if(button!=GLFW_MOUSE_BUTTON_LEFT)return;
        float l=left(w),width=Math.min(650,w-16);
        if(x<l||x>l+width)return;
        if(y>=48&&y<78){if(x<l+width/3)preset(true);else if(x<l+2*width/3)preset(false);else open=false;return;}
        int row=(int)((y-105)/27);if(y>=105&&row>=0&&row<rows(h)&&offset+row<OPTIONS.length){selected=offset+row;change(x<l+width*.75f?-1:1);}
    }
    public void render(Overlay ui,int w,int h){
        visible=rows(h);select(selected);float l=left(w),width=Math.min(650,w-16);
        ui.rectangle(0,0,w,h,.01f,.02f,.06f,.92f);
        ui.text("RENDERING SETTINGS",l+8,15,2.2f);
        String[] buttons={"1: Low cost","2: Defaults","Back (Esc)"};
        for(int i=0;i<3;i++){ui.rectangle(l+i*width/3,48,width/3-4,30,.06f,.27f,.35f,1);ui.text(buttons[i],l+i*width/3+5,57,1.5f);}
        ui.text("Scroll / Up / Down | Left / Right to change",l+8,85,1.3f);
        for(int i=offset;i<Math.min(OPTIONS.length,offset+visible);i++){
            float y=105+(i-offset)*27;if(i==selected)ui.rectangle(l,y,width,25,.07f,.27f,.36f,1);
            ui.text(OPTIONS[i].label,l+8,y+6,Math.min(1.4f,width/440f));
            try{Object v=value(OPTIONS[i]);String text=v instanceof Boolean b?(b?"On":"Off"):v instanceof Float f?String.format(Locale.ROOT,"%.3f",f):v.toString();ui.text("< "+text+" >",l+width*.72f,y+6,1.4f);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        ui.text((selected+1)+" / "+OPTIONS.length+"   "+message,l+8,h-30,1.2f);
    }
}
