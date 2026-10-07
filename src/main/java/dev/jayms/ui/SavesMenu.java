package dev.jayms.ui;

import static org.lwjgl.glfw.GLFW.*;
import dev.jayms.net.CitySaves;
import java.nio.file.Path;
import java.util.List;

/** Native game overlay; all disk and simulation work runs on the render thread. */
public final class SavesMenu {
    public interface Actions {
        void save() throws Exception;
        void load(Path path) throws Exception;
        void create(String name, boolean copy) throws Exception;
        default String developStressGrid() throws Exception { return "Load Stress Test Grid first."; }
    }
    private final CitySaves store;
    private final Actions actions;
    private final Path current;
    private List<CitySaves.Entry> entries = List.of();
    private int selected;
    private String name = "", message = "Loading another city saves the current city first.";
    public boolean open;
    public SavesMenu(CitySaves store, Path current, Actions actions) {
        this.store = store; this.current = current; this.actions = actions;
    }
    public void show() {
        open = true;
        try { entries = store.list(); selected = Math.max(0, Math.min(selected, Math.max(0, entries.size()-1))); }
        catch (Exception e) { message = "Could not list saves. Check folder permissions."; }
    }
    public void character(int character) {
        if (open && character >= 32 && character < 127 && name.length() < 40) name += (char)character;
    }
    public void key(int key, int action) {
        if (action != GLFW_PRESS && action != GLFW_REPEAT) return;
        if (key == GLFW_KEY_ESCAPE) open = false;
        else if (key == GLFW_KEY_BACKSPACE && !name.isEmpty()) name = name.substring(0, name.length()-1);
        else if (key == GLFW_KEY_UP) selected = Math.max(0, selected-1);
        else if (key == GLFW_KEY_DOWN) selected = Math.max(0, Math.min(entries.size()-1, selected+1));
    }
    public void scroll(double direction) { selected = Math.max(0, Math.min(entries.size()-1, selected-(int)Math.signum(direction))); }
    private int offset() { return Math.max(0, selected-7); }
    public void click(int button, float x, float y, int width, int height) {
        if (button != GLFW_MOUSE_BUTTON_LEFT) return;
        float left = width/2f-300, top = height/2f-290;
        if (x < left+16 || x > left+584) return;
        if (y >= top+100 && y < top+340) {
            int row = (int)((y-top-100)/30)+offset();
            if (row < entries.size()) selected = row;
            return;
        }
        try {
            if (y >= top+354 && y < top+394) {
                if (x < left+300) { actions.save(); message = "Current simulation saved."; show(); }
                else if (!entries.isEmpty()) actions.load(entries.get(selected).world());
            } else if (y >= top+458 && y < top+498) {
                actions.create(name, x < left+300); name = ""; show();
            } else if (y >= top+530 && y < top+570) {
                if (x < left+300 && current.getParent().getFileName().toString().equals(dev.jayms.net.city.StressGrid.NAME))
                    message = actions.developStressGrid();
                else open = false;
            }
        } catch (Exception e) {
            message = e instanceof java.io.IOException ? e.getMessage() : "Save operation failed. Current city kept.";
        }
    }
    public void render(Overlay ui, int width, int height) {
        float l = width/2f-300, t = height/2f-290;
        ui.rectangle(0,0,width,height,.01f,.02f,.06f,.7f);
        ui.rectangle(l,t,600,580,.025f,.06f,.11f,.98f);
        ui.text("CITY SAVES",l+24,t+22,3);
        ui.text("Current: "+store.name(current),l+24,t+60,1.6f);
        ui.text("Select a save | Scroll or arrows for more",l+24,t+82,1.3f);
        for (int i=offset(); i<Math.min(entries.size(),offset()+8); i++) {
            float y=t+100+(i-offset())*30;
            if (i==selected) ui.rectangle(l+16,y,568,28,.07f,.27f,.36f,1);
            ui.text(entries.get(i).name(),l+26,y+7,1.7f);
        }
        if (entries.isEmpty()) ui.text("No saved cities yet. Save your current city below.",l+24,t+110,1.5f);
        button(ui,l+16,t+354,276,"Save current"); button(ui,l+308,t+354,276,"Load selected");
        ui.text("Name for copy / new city (type here):",l+24,t+410,1.5f);
        ui.rectangle(l+16,t+432,568,24,.05f,.16f,.22f,1);
        ui.text(name+"_",l+24,t+437,1.5f);
        button(ui,l+16,t+458,276,"Save a copy"); button(ui,l+308,t+458,276,"New city");
        ui.text(message,l+24,t+508,1.15f);
        if (current.getParent().getFileName().toString().equals(dev.jayms.net.city.StressGrid.NAME)) {
            button(ui,l+16,t+530,276,"Develop stress save");
            button(ui,l+308,t+530,276,"Back / Esc");
        } else button(ui,l+16,t+530,568,"Back to controls / Esc");
    }
    private void button(Overlay ui,float x,float y,float w,String text) {
        ui.rectangle(x,y,w,40,.06f,.27f,.35f,1); ui.text(text,x+16,y+13,1.7f);
    }
}
