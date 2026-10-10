package dev.jayms.ui;

import dev.jayms.render.*;
import java.util.*;
import java.util.function.Supplier;
import static dev.jayms.render.GraphicsProfile.*;
import static org.lwjgl.glfw.GLFW.*;

/** Draft-only, responsive keyboard/mouse settings. Apply is the only renderer allocation path. */
public final class GraphicsMenu {
    public boolean open;private float density=1;
    public void density(float scale){density=Float.isFinite(scale)?Math.max(1,Math.min(3,scale)):1;}
    private final GraphicsController controller;
    private final PerformanceRecorder recorder;
    private final Runnable toggleRecording;
    private final Supplier<String[]> diagnostics;
    private GraphicsProfile draft;
    private Tab tab=Tab.DISPLAY;
    private int selected,offset,performanceOffset;private boolean helpOpen;
    private String message="";
    private final float[] history=new float[120];private int historyIndex;
    public GraphicsMenu(GraphicsController controller,PerformanceRecorder recorder,Runnable toggleRecording,Supplier<String[]> diagnostics){this.controller=controller;this.recorder=recorder;this.toggleRecording=toggleRecording;this.diagnostics=diagnostics;}
    public GraphicsProfile draft(){return draft;}
    public void show(){open=true;draft=controller.transaction.requested();message=controller.startupWarning;selected=offset=0;}
    public void frame(float millis){history[historyIndex++%history.length]=millis;}
    private List<Key> rows(){return Arrays.stream(Key.values()).filter(k->k.tab==tab).toList();}
    public void scroll(double delta){if(controller.transaction.pending())return;if(tab==Tab.PERFORMANCE){performanceOffset=Math.max(0,Math.min(diagnostics.get().length-1,performanceOffset-(int)Math.signum(delta)));return;}selected=Math.max(-1,Math.min(rows().size()-1,selected-(int)Math.signum(delta)));}
    public void key(int key,int action){if(action!=GLFW_PRESS&&action!=GLFW_REPEAT)return;
        if(helpOpen){if(key==GLFW_KEY_F1||key==GLFW_KEY_ESCAPE)helpOpen=false;return;}
        if(key==GLFW_KEY_F1){helpOpen=true;return;}
        if(controller.transaction.pending()){if(key==GLFW_KEY_ENTER||key==GLFW_KEY_K)controller.transaction.confirm();else if(key==GLFW_KEY_ESCAPE||key==GLFW_KEY_R)controller.transaction.revert();return;}
        if(key==GLFW_KEY_ESCAPE||key==GLFW_KEY_C){cancel();return;}
        if(key==GLFW_KEY_A){apply();return;}if(key==GLFW_KEY_R){reset();return;}
        if(key==GLFW_KEY_F9){toggleRecording.run();return;}
        if(key==GLFW_KEY_TAB){tab=Tab.values()[Math.floorMod(tab.ordinal()+1,Tab.values().length)];selected=offset=0;return;}
        if(tab==Tab.PERFORMANCE&&(key==GLFW_KEY_UP||key==GLFW_KEY_DOWN)){performanceOffset=Math.max(0,Math.min(diagnostics.get().length-1,performanceOffset+(key==GLFW_KEY_DOWN?1:-1)));return;}
        if(key==GLFW_KEY_UP)selected=Math.max(-1,selected-1);else if(key==GLFW_KEY_DOWN)selected=Math.min(rows().size()-1,selected+1);
        else if(key==GLFW_KEY_LEFT)change(-1);else if(key==GLFW_KEY_RIGHT||key==GLFW_KEY_ENTER)change(1);
    }
    public void apply(){if(controller.transaction.apply(draft,System.nanoTime()))draft=controller.transaction.requested();message=controller.transaction.message();}
    public void cancel(){draft=controller.transaction.requested();open=false;}
    public void reset(){draft=preset(Preset.BALANCED);message="Defaults are a draft. Apply to save graphics only.";}
    private boolean enabled(Key k){return switch(k){case EXPOSURE->!draft.on(Key.AUTO_EXPOSURE);case ANISOTROPY->controller.capabilities.maxAnisotropy()>1;default->true;};}
    private List<String> options(Key k){if(k!=Key.RESOLUTION)return k.values;var options=new LinkedHashSet<>(List.of("640x480@60","1280x720@60","1920x1080@60"));options.addAll(controller.capabilities.videoModes());options.add(draft.get(k));return List.copyOf(options);}
    private void change(int direction){
        try{if(selected<0){var options=List.of(Preset.LOW,Preset.BALANCED,Preset.HIGH,Preset.ULTRA);int index=options.indexOf(draft.preset());draft=preset(options.get(Math.floorMod(Math.max(0,index)+direction,options.size())));message="Preset quality table is provisional; no FPS guarantee.";return;}
            Key k=rows().get(selected);if(!enabled(k)){message=k==Key.EXPOSURE?"Turn auto exposure off to use manual brightness.":"This device does not support anisotropy.";return;}
            var options=options(k);int index=options.indexOf(draft.get(k));draft=draft.with(k,options.get(Math.floorMod(index+direction,options.size())));message=k.help();var effective=draft.effective(controller.capabilities);if(!effective.reasons().isEmpty())message=String.join(" ",effective.reasons());
        }catch(IllegalArgumentException e){message=e.getMessage();}
    }
    private float left(int w){return Math.max(8,(w-Math.min(840,w-16))/2f);}
    private float panelWidth(int w){return Math.min(840,w-16);}
    private float top(int h){return 8;}
    public void click(int button,float x,float y,int width,int height){x/=density;y/=density;width=Math.round(width/density);height=Math.round(height/density);if(button!=GLFW_MOUSE_BUTTON_LEFT)return;if(helpOpen){helpOpen=false;return;}
        float l=left(width),pw=panelWidth(width),t=top(height);
        if(controller.transaction.pending()){if(y>=height-48&&y<=height-14){if(x<l+pw/2)controller.transaction.confirm();else controller.transaction.revert();}return;}
        if(x<l||x>l+pw)return;
        if(y>=t+29&&y<t+53){selected=-1;change(x<l+pw/2?-1:1);return;}
        if(y>=t+56&&y<t+80){tab=Tab.values()[Math.min(4,(int)((x-l)/(pw/5)))];selected=offset=0;return;}
        int visible=Math.max(1,(height-192)/24);
        if(y>=t+84&&y<t+84+visible*24){int row=(int)((y-t-84)/24)+offset;if(row<rows().size()){selected=row;change(x<l+pw/2?-1:1);}return;}
        if(y>=height-48&&y<=height-14){int action=Math.min(2,(int)((x-l)/(pw/3)));if(action==0)apply();else if(action==1)cancel();else reset();}
        if(tab==Tab.PERFORMANCE&&y>=height-82&&y<height-55)toggleRecording.run();
    }
    private static void fit(Overlay ui,String text,float x,float y,float scale,float max){String s=text;while(s.length()>1&&ui.textWidth(s,scale)>max)s=s.substring(0,s.length()-1);if(s.length()<text.length()&&s.length()>3)s=s.substring(0,s.length()-3)+"...";ui.text(s,x,y,scale);}
    public void render(Overlay ui,int width,int height){if(!open)return;ui.coordinateScale(density);try{renderScaled(ui,Math.round(width/density),Math.round(height/density));}finally{ui.coordinateScale(1);}}
    private void renderScaled(Overlay ui,int width,int height){
        float l=left(width),pw=panelWidth(width),t=top(height);float scale=width<500?1.2f:1.55f;
        ui.rectangle(0,0,width,height,.01f,.02f,.05f,.8f);ui.rectangle(l,t,pw,height-16,.025f,.06f,.11f,.98f);
        ui.text("GRAPHICS",l+12,t+8,2);if(selected<0)ui.rectangle(l+5,t+29,pw-10,25,.07f,.27f,.36f,.95f);fit(ui,"Preset: "+draft.preset()+" | "+draft.cost(),l+12,t+33,scale,pw-24);
        if(helpOpen){
            String text=String.join(" ",draft.effective(controller.capabilities).reasons())+" "+(tab==Tab.WORLD?"Model detail has no separate quality path. Terrain distances affect rendering only. ":tab==Tab.LIGHTING?"Separate volumetric clouds are unavailable; Clouds controls the sky shader. ":"")+(selected<0?draft.cost()+". Presets update every graphics setting. Manual changes select Custom. Tables are provisional until reference desktop measurements.":rows().get(Math.min(selected,rows().size()-1)).help());
            float y=t+86;String line="";for(String word:text.split(" ")){String next=line+word+" ";if(ui.textWidth(next,scale)>pw-28){ui.text(line,l+12,y,scale);y+=22;line=word+" ";}else line=next;}ui.text(line,l+12,y,scale);fit(ui,"F1 / Esc closes help",l+12,height-40,scale,pw-24);return;
        }
        if(controller.transaction.pending()){
            ui.text("KEEP CHANGES?",l+12,t+80,2);fit(ui,"Revert in "+controller.transaction.remaining(System.nanoTime())+" seconds. Focus loss also reverts.",l+12,t+115,scale,pw-24);
            button(ui,l,height-48,pw/2-8,"Keep (Enter)",scale);button(ui,l+pw/2,height-48,pw/2-8,"Revert (Esc)",scale);return;
        }
        String[] tabs={"Display","World","Light","Image","Perf"};for(int i=0;i<5;i++){ui.rectangle(l+i*pw/5,t+56,pw/5-2,24,tab.ordinal()==i?.1f:.04f,.2f,.3f,1);fit(ui,tabs[i],l+i*pw/5+7,t+62,scale,pw/5-12);}
        int visible=Math.max(1,(height-192)/24);var rows=rows();offset=Math.max(0,Math.min(Math.max(0,selected-visible+1),Math.max(0,rows.size()-visible)));
        for(int i=offset;i<Math.min(rows.size(),offset+visible);i++){Key k=rows.get(i);float y=t+84+(i-offset)*24;boolean active=enabled(k);if(i==selected)ui.rectangle(l+5,y,pw-10,23,.07f,.27f,.36f,.95f);
            fit(ui,k.label+(active?"":" (unavailable)"),l+12,y+5,scale,pw*.64f-18);fit(ui,draft.get(k),l+pw*.65f,y+5,scale,pw*.35f-12);}
        String help=selected<0?"Left / right selects a preset; manual edits select Custom.":rows.get(Math.min(selected,rows.size()-1)).help();
        int footer=height-(tab==Tab.PERFORMANCE?134:104);fit(ui,help,l+12,footer,1.1f,pw-24);fit(ui,"Tab tabs | arrows | F1 help | A Apply | C Cancel | R Reset",l+12,footer+15,1.1f,pw-24);
        String status=message.isEmpty()?controller.transaction.message():message;fit(ui,status,l+12,footer+30,1.1f,pw-24);
        if(!controller.overrides.isEmpty())fit(ui,"Launch overrides: "+String.join(", ",controller.overrides),l+12,t+height-145,1,pw-24);
        if(tab==Tab.PERFORMANCE){
            int y=(int)t+84+24;String[] lines=diagnostics.get();for(int i=performanceOffset;i<lines.length;i++){if(y>height-(height>420?250:146))break;fit(ui,lines[i],l+12,y,scale,pw-24);y+=19;}
            if(height>420){float graphY=height-190,graphH=42;ui.rectangle(l+12,graphY,pw-24,graphH,.02f,.03f,.06f,1);float step=(pw-24)/history.length;for(int i=0;i<history.length;i++){float v=history[(historyIndex+i)%history.length];float bar=Math.min(graphH,Math.max(0,v)*graphH/50);ui.rectangle(l+12+i*step,graphY+graphH-bar,Math.max(1,step-1),bar,.1f,.8f,.7f,1);}fit(ui,"Frame ms: graph range 0 - 50",l+12,graphY-16,1.1f,pw-24);}
            button(ui,l+12,height-82,pw-24,recorder.active()?"Stop performance recording (F9)":"Record performance (F9)",scale);
        }
        button(ui,l+5,height-48,pw/3-9,"Apply (A)",scale);button(ui,l+pw/3+3,height-48,pw/3-9,"Cancel (C)",scale);button(ui,l+pw*2/3+1,height-48,pw/3-9,"Defaults (R)",scale);
    }
    private static void button(Overlay ui,float x,float y,float w,String text,float scale){ui.rectangle(x,y,w,31,.06f,.24f,.32f,1);fit(ui,text,x+7,y+9,scale,w-14);}
}
