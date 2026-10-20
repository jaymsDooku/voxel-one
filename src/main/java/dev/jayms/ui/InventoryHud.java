package dev.jayms.ui;

import dev.jayms.net.*;
import dev.jayms.net.model.*;

import java.util.function.BiConsumer;
import java.util.*;
import static org.lwjgl.glfw.GLFW.*;

public final class InventoryHud {
    public boolean open;
    public int selected;
    private boolean crafting;
    private int firstRecipe, selectedRecipe=96;
    private String search="", category="All", expandedGroup="", sourceMessage="", feedback="";
    private boolean searchFocus, craftableOnly, compactDetail, pending;
    private int quantity=1, focus=-1, searchCursor;
    private float density=1;
    private String dropKey="Q", hoverName="", hoverDescription="";
    private final Deque<Integer> history=new ArrayDeque<>();
    private record Hit(float x,float y,float w,float h,String action,int value,boolean enabled) {}
    private final List<Hit> hits=new ArrayList<>();
    public void density(float value) { density=Float.isFinite(value)?Math.max(1,Math.min(3,value)):1; }
    public void pending(boolean value) { pending=value; }
    public void feedback(String value) { feedback=value==null?"":value; }
    public int selectedRecipe() { return selectedRecipe; }
    public int quantity() { return quantity; }
    public boolean bookOpen() { return crafting; }
    public String search() { return search; }
    public boolean searchFocused() { return searchFocus; }
    public void openBook() { crafting=true; source=-1; }
    public void query(String value) { search=value; searchCursor=search.length(); firstRecipe=0; compactDetail=false; }
    public void category(String value) { category=value; firstRecipe=0; compactDetail=false; }
    public void craftableOnly(boolean value) { craftableOnly=value; firstRecipe=0; }
    public List<Crafting.Recipe> filtered(Inventory inv) {
        return Crafting.recipes().stream().filter(r->category.equals("All")||r.category().equals(category))
                .filter(r->Crafting.matches(r,search)).filter(r->!craftableOnly||r.available(inv)).toList();
    }
    public void character(int codepoint) {
        if(open&&crafting&&searchFocus&&codepoint>=32&&codepoint<=126&&search.length()<64) {
            int next=searchCursor+1; query(search.substring(0,searchCursor)+(char)codepoint+search.substring(searchCursor)); searchCursor=next;
        }
    }
    public void selectRecipe(int id) {
        if(Crafting.recipe(id)==null) return;
        selectedRecipe=id; quantity=1; sourceMessage=""; compactDetail=true;
    }
    public void ingredient(int item) {
        var r=Crafting.ingredientRecipe(item);
        if(r==null) { sourceMessage=Items.source(item); return; }
        if(r.id()==selectedRecipe) { sourceMessage="Already viewing this recipe."; return; }
        if(history.contains(r.id())) history.clear();
        if(history.size()==16) history.removeLast();
        history.push(selectedRecipe); search=""; searchCursor=0; category="All"; craftableOnly=false; expandedGroup="";
        selectRecipe(r.id());
    }
    public void back() { if(!history.isEmpty()) selectRecipe(history.pop()); else compactDetail=false; }
    public void key(int key,Inventory inv,BiConsumer<Integer,Integer> swap,BiConsumer<Integer,Integer> craft) {
        if(key==GLFW_KEY_ESCAPE) {
            if(searchFocus) { searchFocus=false; return; }
            if(crafting) { crafting=false; compactDetail=false; return; }
            close(); return;
        }
        if(searchFocus) {
            if(key==GLFW_KEY_BACKSPACE&&searchCursor>0) {
                int next=searchCursor-1; query(search.substring(0,next)+search.substring(searchCursor)); searchCursor=next;
            } else if(key==GLFW_KEY_DELETE&&searchCursor<search.length()) {
                int next=searchCursor; query(search.substring(0,next)+search.substring(next+1)); searchCursor=next;
            } else if(key==GLFW_KEY_LEFT) searchCursor=Math.max(0,searchCursor-1);
            else if(key==GLFW_KEY_RIGHT) searchCursor=Math.min(search.length(),searchCursor+1);
            else if(key==GLFW_KEY_HOME) searchCursor=0;
            else if(key==GLFW_KEY_END) searchCursor=search.length();
            if(key==GLFW_KEY_ENTER) { searchFocus=false; return; }
            if(key!=GLFW_KEY_TAB&&key!=GLFW_KEY_DOWN&&key!=GLFW_KEY_UP) return;
            searchFocus=false;
        }
        if(key==GLFW_KEY_TAB||key==GLFW_KEY_RIGHT||key==GLFW_KEY_DOWN) focus=hits.isEmpty()?-1:Math.floorMod(focus+1,hits.size());
        else if(key==GLFW_KEY_LEFT||key==GLFW_KEY_UP) focus=hits.isEmpty()?-1:Math.floorMod(focus-1,hits.size());
        else if(key==GLFW_KEY_ENTER&&focus>=0&&focus<hits.size()) activate(hits.get(focus),inv,swap,craft);
        else if(key==GLFW_KEY_PAGE_DOWN) firstRecipe+=4;
        else if(key==GLFW_KEY_PAGE_UP) firstRecipe=Math.max(0,firstRecipe-4);
    }
    private int source = -1;
    private static final int ICON_SIZE = 16;
    private final java.util.Map<ModelLibrary.Entry, int[]> icons = new java.util.HashMap<>();
    private ModelLibrary iconLibrary;
    private long iconRevision=-1;
    public void prepareIcons(ModelLibrary models) {
        if(iconLibrary==models && iconRevision==models.revision()) return;
        iconLibrary=models; iconRevision=models.revision(); icons.clear();
        for(var entry:models.entries()) if(Items.valid(entry.id()))icon(entry);
        for(int id=190;id<=198;id++) icon(models.get(id));
    }

    private int[] icon(ModelLibrary.Entry entry) {
        return icons.computeIfAbsent(
                entry,
                e -> {
                    int[] result = new int[ICON_SIZE * ICON_SIZE];
                    int n = e.definition().voxels().size();
                    for (int y = 0; y < ICON_SIZE; y++)
                        for (int x = 0; x < ICON_SIZE; x++) {
                            var hit =
                                    e.definition()
                                            .voxels()
                                            .raycast(
                                                    (x + .5f) / ICON_SIZE,
                                                    (ICON_SIZE - 1 - y + .5f) / ICON_SIZE,
                                                    2,
                                                    0,
                                                    0,
                                                    -1,
                                                    0,
                                                    3);
                            if (hit != null) {
                                float z = 2 - hit.distance() - .00001f;
                                result[x + y * ICON_SIZE] =
                                        e.definition()
                                                .voxels()
                                                .get(
                                                        (int) ((x + .5f) / ICON_SIZE * n),
                                                        (int)
                                                                ((ICON_SIZE - 1 - y + .5f)
                                                                        / ICON_SIZE
                                                                        * n),
                                                        Math.max(
                                                                0, Math.min(n - 1, (int) (z * n))));
                            }
                        }
                    return result;
                });
    }

    public void toggle() {
        open = !open;
        source = -1;
        crafting = false;
        searchFocus=false; compactDetail=false;
    }

    public void close() {
        open = false;
        source = -1;
        searchFocus=false;
    }

    public void scroll(double amount) {
        if(open&&crafting) { firstRecipe=Math.max(0,firstRecipe-(int)Math.signum(amount)*2); return; }
        if(!open) selected=Math.floorMod(selected-(int)Math.signum(amount),9);
    }

    private void slot(
            Overlay ui,
            Inventory inv,
            int i,
            float x,
            float y,
            float size,
            boolean active,
            ModelLibrary models) {
        ui.rectangle(
                x,
                y,
                size - 3,
                size - 3,
                active ? .12f : .035f,
                active ? .43f : .085f,
                active ? .53f : .13f,
                .96f);
        if (active) {
            ui.rectangle(x, y, size - 3, 2, .3f, .95f, 1, 1);
            ui.rectangle(x, y + size - 5, size - 3, 2, .3f, .95f, 1, 1);
        }
        int type = inv.type(i);
        if (type != 0) {
            var model = models.get(type);
            if (model != null) {
                int[] pixels = icon(model);
                for (int iy = 0; iy < ICON_SIZE; iy++)
                    for (int ix = 0; ix < ICON_SIZE; ix++) {
                        int c = pixels[ix + iy * ICON_SIZE];
                        if (c != 0)
                            ui.rectangle(
                                    x + ix * 2 + 8,
                                    y + iy * 2 + 7,
                                    2,
                                    2,
                                    ((c >>> 16) & 255) / 255f,
                                    ((c >>> 8) & 255) / 255f,
                                    (c & 255) / 255f,
                                    1);
                    }
            } else {
                float[] c = Blocks.color(type);
                float iconSide = Math.max(7, 25f / (1 << Blocks.depth(type)));
                float ix = x + 11 + (25 - iconSide) / 2, iy = y + 8 + (25 - iconSide) / 2;
                ui.rectangle(
                        ix + 2,
                        iy + 3,
                        iconSide,
                        iconSide,
                        c[0] * .65f,
                        c[1] * .65f,
                        c[2] * .65f,
                        1);
                ui.rectangle(ix, iy, iconSide, iconSide, c[0], c[1], c[2], 1);
                ui.rectangle(
                        ix,
                        iy,
                        iconSide,
                        Math.min(5, iconSide / 3),
                        Math.min(1, c[0] * 1.2f),
                        Math.min(1, c[1] * 1.2f),
                        Math.min(1, c[2] * 1.2f),
                        1);
            }
            ui.text("" + inv.count(i), x + size - 23, y + size - 19, 1.4f);
        }
    }

    public void render(
            Overlay ui,
            Inventory inv,
            int hp,
            int w,
            int h,
            Controls controls,
            ModelLibrary models,
            float mx,
            float my) {
        render(ui, inv, hp, w, h, controls, models, mx, my, true);
    }

    /** Keep the inventory panel usable when the player HUD is hidden in sky view. */
    public void render(
            Overlay ui, Inventory inv, int hp, int w, int h, Controls controls,
            ModelLibrary models, float mx, float my, boolean showPlayerHud) {
        float scale=Math.min(density,Math.max(1,Math.min(w/640f,h/480f)));
        ui.coordinateScale(scale);
        try { renderScaled(ui,inv,hp,Math.round(w/scale),Math.round(h/scale),controls,models,mx/scale,my/scale,showPlayerHud); }
        finally { ui.coordinateScale(1); }
    }
    private void renderScaled(Overlay ui,Inventory inv,int hp,int w,int h,Controls controls,ModelLibrary models,float mx,float my,boolean showPlayerHud) {
        dropKey=Controls.keyName(controls.code(Controls.Action.DROP_ITEM));
        if (showPlayerHud) {
            float size = 56, left = w / 2f - size * 9 / 2, top = h - 72;
            ui.rectangle(left - 7, top - 36, size * 9 + 11, 30, .02f, .05f, .09f, .85f);
            ui.text("HEALTH", left + 4, top - 28, 1.5f);
            for (int i = 0; i < 20; i++)
                ui.rectangle(
                        left + 90 + i * 17,
                        top - 26,
                        14,
                        13,
                        i < hp ? .9f : .12f,
                        i < hp ? .23f : .16f,
                        i < hp ? .3f : .20f,
                        1);
            ui.text(hp + "/20", left + 437, top - 28, 1.4f);
            for (int i = 0; i < 9; i++) {
                slot(ui, inv, i, left + i * size, top, size, i == selected, models);
                ui.text(
                        Controls.keyName(
                                controls.code(
                                        Controls.Action.values()[
                                                Controls.Action.SLOT_1.ordinal() + i])),
                        left + i * size + 4,
                        top + 3,
                        1.1f);
            }
            ui.text(
                    models.name(inv.type(selected))
                            + " | "
                            + Controls.keyName(controls.code(Controls.Action.INVENTORY))
                            + ": inventory",
                    left,
                    top - 56,
                    1.5f);
        }
        if (!open) return;
        hits.clear(); hoverName=""; hoverDescription="";
        ui.rectangle(0,0,w,h,.01f,.025f,.045f,.72f);
        if(crafting) {
            boolean wide=w>=1240&&h>=600;
            if(wide) renderInventory(ui,inv,h,w,models,mx,my,22,h/2f-175,false);
            float bx=wide?558:12, bw=w-bx-12;
            float by=wide?56:12;
            renderBook(ui,inv,models,bx,by,bw,h-by-12,mx,my);
        } else renderInventory(ui,inv,h,w,models,mx,my,w/2f-268,h/2f-175,true);
        if(!hoverName.isEmpty()) showTooltip(ui,hoverName,hoverDescription,mx,my,w,h);
    }

    private void drawItem(Overlay ui,ModelLibrary models,int type,float x,float y,float side) {
        var model=models.get(type);
        if(model!=null) {
            var pixels=icon(model); float px=side/ICON_SIZE;
            for(int iy=0;iy<ICON_SIZE;iy++) for(int ix=0;ix<ICON_SIZE;ix++) {
                int c=pixels[ix+iy*ICON_SIZE];
                if(c!=0) ui.rectangle(x+ix*px,y+iy*px,px,px,((c>>>16)&255)/255f,((c>>>8)&255)/255f,(c&255)/255f,1);
            }
        } else if(type>0) {
            var c=Blocks.color(type); float small=Math.max(side/4,side/(1<<Blocks.depth(type)));
            ui.rectangle(x+(side-small)/2,y+(side-small)/2,small,small,c[0],c[1],c[2],1);
            ui.rectangle(x+(side-small)/2,y+(side-small)/2,small,small/4,Math.min(1,c[0]*1.2f),Math.min(1,c[1]*1.2f),Math.min(1,c[2]*1.2f),1);
        }
    }
    private void button(Overlay ui,String text,float x,float y,float width,String action,int value,boolean enabled) {
        int i=hits.size(); hits.add(new Hit(x,y,width,27,action,value,enabled));
        ui.rectangle(x,y,width,27,enabled?.075f:.04f,enabled?.23f:.09f,enabled?.30f:.13f,1);
        if(i==focus) { ui.rectangle(x,y,width,2,.4f,1,1,1); ui.rectangle(x,y+25,width,2,.4f,1,1,1); }
        fit(ui,text,x+7,y+8,width-14,1.35f);
    }
    private static void fit(Overlay ui,String text,float x,float y,float width,float scale) {
        String s=text;
        while(s.length()>1&&ui.textWidth(s,scale)>width) s=s.substring(0,s.length()-1);
        if(s.length()<text.length()&&s.length()>3) s=s.substring(0,s.length()-3)+"...";
        ui.text(s,x,y,scale);
    }
    private static void wrap(Overlay ui,String text,float x,float y,float width,int lines) {
        String line=""; int row=0;
        for(String word:text.split(" ")) {
            if(ui.textWidth(line+word,1.3f)>width&&!line.isEmpty()) { ui.text(line,x,y+row*18,1.3f); line=""; if(++row>=lines)return; }
            line+=word+" ";
        }
        if(row<lines) ui.text(line,x,y+row*18,1.3f);
    }
    private void renderInventory(Overlay ui,Inventory inv,int h,int w,ModelLibrary models,float mx,float my,float x,float y,boolean tab) {
        ui.rectangle(x-10,y-65,524,370,.025f,.06f,.11f,.98f);
        ui.text("INVENTORY / 36 SLOTS",x,y-43,1.9f);
        if(tab) button(ui,"Recipe Book",x+365,y-49,139,"book",0,true);
        ui.text("Click a stack, then its destination.",x,y-15,1.4f);
        for(int row=0;row<4;row++) for(int col=0;col<9;col++) {
            int i=row==3?col:9+row*9+col;
            slot(ui,inv,i,x+col*56,y+row*60+(row==3?10:0),56,i==source,models);
            hits.add(new Hit(x+col*56,y+row*60+(row==3?10:0),53,53,"slot",i,true));
            if(hits.size()-1==focus) ui.rectangle(x+col*56,y+row*60+(row==3?10:0),53,2,1,1,.4f,1);
        }
        ui.text("Bottom row: hotbar. "+dropKey+" drops the held item in play.",x,y+265,1.3f);
        ui.text("Tab: focus | Enter: use | Escape: close",x,y+285,1.3f);
        for(var hit:hits) if(hit.action().equals("slot")&&inside(hit,mx,my)&&inv.count(hit.value())>0) tooltip(ui,models.name(inv.type(hit.value())),Items.get(inv.type(hit.value())).description(),mx,my,w,h);
    }
    private void renderBook(Overlay ui,Inventory inv,ModelLibrary models,float x,float y,float width,float height,float mx,float my) {
        ui.rectangle(x,y,width,height,.025f,.06f,.105f,.99f);
        ui.rectangle(x,y,width,3,.15f,.85f,1,1);
        ui.text("RECIPE BOOK",x+12,y+14,2);
        button(ui,"Inventory",x+width-124,y+9,112,"inventory",0,true);
        button(ui,"Search: "+(searchFocus?search.substring(0,searchCursor)+"_"+search.substring(searchCursor):search),x+12,y+43,width-24,"search",0,true);
        if(searchFocus) ui.rectangle(x+12,y+68,width-24,2,.4f,1,1,1);
        String[] categories={"All","Tools","Materials","Building","Decorations","Lighting"};
        float cw=(width-28)/3;
        for(int i=0;i<6;i++) button(ui,(category.equals(categories[i])?"> ":"")+categories[i],x+12+(i%3)*(cw+2),y+77+(i/3)*29,cw,"category",i,true);
        button(ui,(craftableOnly?"[x] ":"[ ] ")+"Craftable now",x+12,y+140,180,"filter",0,true);
        button(ui,"Back",x+200,y+140,78,"back",0,true);
        if(!expandedGroup.isEmpty()) button(ui,"Fold cuts",x+282,y+140,100,"fold",0,true);
        boolean split=width>=700&&height>=520;
        if(!split) button(ui,compactDetail?"Browse":"Details",x+width-112,y+140,100,"detail",0,true);
        List<Crafting.Recipe> all=filtered(inv);
        float top=y+178, bottom=y+height-42;
        if(split||!compactDetail) {
            float gw=split?(width-36)*.49f:width-24;
            List<Object> tiles=new ArrayList<>(); Set<String> groups=new HashSet<>();
            for(var r:all) {
                if(!r.group().isEmpty()&&search.isBlank()&&!r.group().equals(expandedGroup)) {
                    if(groups.add(r.group())) tiles.add(r.group());
                } else tiles.add(r);
            }
            int cols=gw>=400?3:2, rows=Math.max(1,(int)((bottom-top-24)/88)), page=cols*rows;
            firstRecipe=Math.min(firstRecipe,Math.max(0,tiles.size()-1));
            for(int i=firstRecipe;i<Math.min(tiles.size(),firstRecipe+page);i++) {
                int at=i-firstRecipe; float tx=x+12+(at%cols)*(gw/cols), ty=top+(at/cols)*88, tw=gw/cols-5;
                Object t=tiles.get(i);
                if(t instanceof String group) {
                    button(ui,group+" cuts",tx,ty+20,tw,"group",Arrays.asList("Grass","Dirt","Stone","Sand","Snow","Wood","Leaves","Planks","Bricks","Glass","LED light").indexOf(group),true);
                    fit(ui,"Open cut / join recipes",tx+4,ty+55,tw-8,1.3f);
                    continue;
                }
                var r=(Crafting.Recipe)t; boolean ready=r.available(inv);
                ui.rectangle(tx,ty,tw,83,r.id()==selectedRecipe?.1f:.04f,r.id()==selectedRecipe?.27f:.11f,.19f,1);
                drawItem(ui,models,r.output(),tx+7,ty+6,32);
                wrap(ui,models.name(r.output()),tx+45,ty+7,tw-51,2);
                fit(ui,"x"+r.count()+" "+(ready?"Ready":"Missing"),tx+7,ty+43,tw-14,1.35f);
                fit(ui,r.name(),tx+7,ty+64,tw-14,1.2f);
                hits.add(new Hit(tx,ty,tw,83,"recipe",r.id(),true));
                if(hits.size()-1==focus) ui.rectangle(tx,ty,tw,2,1,1,.4f,1);
                if(inside(hits.get(hits.size()-1),mx,my)) tooltip(ui,models.name(r.output()),Items.get(r.output()).description(),mx,my,(int)(x+width+12),(int)(y+height+12));
            }
            if(tiles.isEmpty()) wrap(ui,"No recipes match. Clear search or Craftable now.",x+18,top+24,gw-12,3);
            fit(ui,"Scroll / Page Up / Down | "+tiles.size()+" entries",x+12,bottom-10,gw,1.3f);
        }
        if(split||compactDetail) {
            float dx=split?x+width*.52f:x+12, dw=split?width*.48f-12:width-24;
            var r=Crafting.recipe(selectedRecipe);
            if(r==null||all.stream().noneMatch(t->t.id()==selectedRecipe)) {
                wrap(ui,"Selected recipe left this filter. Select another, or clear the filter.",dx,top+20,dw,4);
            } else renderDetails(ui,inv,models,r,dx,top,dw,bottom-top);
        }
        String footer=!split&&!sourceMessage.isEmpty()?sourceMessage:feedback.isEmpty()?"Tab: focus | Enter: use | Escape: search, book, inventory":feedback;
        fit(ui,footer,x+12,y+height-25,width-24,1.3f);
    }
    private void renderDetails(Overlay ui,Inventory inv,ModelLibrary models,Crafting.Recipe r,float x,float y,float width,float height) {
        boolean shortPane=height<340;
        drawItem(ui,models,r.output(),x,y,shortPane?40:64);
        float px=shortPane?48:72;
        fit(ui,models.name(r.output()),x+px,y+4,width-px,1.6f);
        fit(ui,"Output: "+(r.count()*quantity)+" | "+r.variant(),x+px,y+26,width-px,1.3f);
        if(!shortPane) wrap(ui,Items.get(r.output()).description(),x+px,y+46,width-px,2);
        float iy=y+(shortPane?49:82);
        for(var e:r.costs(quantity).entrySet()) {
            button(ui,models.name(e.getKey())+" "+inv.countType(e.getKey())+" / "+e.getValue(),x+26,iy,width-26-(shortPane?80:0),"ingredient",e.getKey(),true);
            drawItem(ui,models,e.getKey(),x,iy+1,23); iy+=29;
        }
        if(!sourceMessage.isEmpty()&&!shortPane) { wrap(ui,sourceMessage,x,iy,width,2); iy+=36; }
        if(!r.diagram().isEmpty()) {
            float cell=shortPane?20:30;
            float diagramX=shortPane?x+width-68:x, diagramY=shortPane?y+54:iy;
            int head=r.output()>=195?Blocks.STONE:Blocks.PLANKS;
            for(int row=0;row<3;row++) for(int col=0;col<3;col++) {
                ui.rectangle(diagramX+col*cell,diagramY+row*cell,cell-2,cell-2,.07f,.16f,.23f,1);
                char c=r.diagram().get(row).charAt(col);
                if(c!=' ') drawItem(ui,models,c=='H'?head:Items.STICKS,diagramX+col*cell+2,diagramY+row*cell+2,cell-6);
            }
            if(!shortPane) fit(ui,"Diagram only",x+cell*3+8,iy+10,width-cell*3-8,1.3f);
        } else if(shortPane) {
            int i=0;for(int item:r.inputs().keySet()) drawItem(ui,models,item,x+width-72+(i++)*20,y+54,18);
            ui.text("v",x+width-47,y+77,1.3f);
            drawItem(ui,models,r.output(),x+width-56,y+94,27);
        } else fit(ui,"Ingredients -> "+r.count()+" "+Items.name(r.output()),x,iy+8,width,1.3f);
        float action=y+height-59;
        String missing=r.missing(inv,quantity);
        wrap(ui,pending?"Waiting for authority...":missing.isEmpty()?"Costs above; craft from inventory.":"Missing: "+missing,x,action-39,width,2);
        button(ui,"-",x,action,30,"less",0,quantity>1&&!pending);
        button(ui,""+quantity,x+34,action,42,"quantity",0,false);
        button(ui,"+",x+80,action,30,"more",0,quantity<Crafting.MAX_BATCH&&!pending);
        button(ui,"Max "+r.maximum(inv),x+116,action,Math.max(85,width-116),"max",0,r.maximum(inv)>0&&!pending);
        button(ui,"Craft "+quantity+" batch"+(quantity==1?"":"es"),x,action+31,width,"craft",r.id(),missing.isEmpty()&&!pending);
    }
    private void tooltip(Overlay ui,String name,String description,float mx,float my,int w,int h) {
        hoverName=name; hoverDescription=description;
    }
    private void showTooltip(Overlay ui,String name,String description,float mx,float my,int w,int h) {
        float width=Math.min(340,w-12), height=74;
        float x=Math.max(6,Math.min(w-width-6,mx+12)),y=Math.max(6,Math.min(h-height-6,my+16));
        ui.rectangle(x,y,width,height,.025f,.045f,.085f,.99f);
        fit(ui,name,x+8,y+8,width-16,1.5f); wrap(ui,description,x+8,y+29,width-16,2);
    }
    private static boolean inside(Hit r,float x,float y) { return x>=r.x()&&x<r.x()+r.w()&&y>=r.y()&&y<r.y()+r.h(); }
    private void activate(Hit hit,Inventory inv,BiConsumer<Integer,Integer> swap,BiConsumer<Integer,Integer> craft) {
        if(!hit.enabled()) return;
        if(!hit.action().equals("search")) searchFocus=false;
        switch(hit.action()) {
            case "book" -> openBook();
            case "inventory" -> { crafting=false; compactDetail=false; }
            case "search" -> searchFocus=true;
            case "category" -> category(new String[]{"All","Tools","Materials","Building","Decorations","Lighting"}[hit.value()]);
            case "filter" -> craftableOnly(!craftableOnly);
            case "back" -> back();
            case "fold" -> { expandedGroup=""; firstRecipe=0; }
            case "detail" -> compactDetail=!compactDetail;
            case "group" -> { expandedGroup=new String[]{"Grass","Dirt","Stone","Sand","Snow","Wood","Leaves","Planks","Bricks","Glass","LED light"}[hit.value()]; firstRecipe=0; }
            case "recipe" -> selectRecipe(hit.value());
            case "ingredient" -> ingredient(hit.value());
            case "less" -> quantity=Math.max(1,quantity-1);
            case "more" -> quantity=Math.min(Crafting.MAX_BATCH,quantity+1);
            case "max" -> quantity=Math.max(1,Crafting.recipe(selectedRecipe).maximum(inv));
            case "craft" -> { if(!pending && Crafting.recipe(selectedRecipe).missing(inv,quantity).isEmpty()) craft.accept(selectedRecipe,quantity); }
            case "slot" -> { int i=hit.value(); if(source<0) { if(inv.count(i)>0) source=i; } else { if(source!=i)swap.accept(source,i); source=-1; } }
            default -> {}
        }
    }
    public void click(float mx,float my,int w,int h,Inventory inv,BiConsumer<Integer,Integer> swap) { click(mx,my,w,h,inv,swap,id->{}); }
    public void click(float mx,float my,int w,int h,Inventory inv,BiConsumer<Integer,Integer> swap,java.util.function.IntConsumer craft) {
        clickBatch(mx,my,w,h,inv,swap,(id,n)->craft.accept(id));
    }
    public void clickBatch(float mx,float my,int w,int h,Inventory inv,BiConsumer<Integer,Integer> swap,BiConsumer<Integer,Integer> craft) {
        if(!open)return;
        float scale=Math.min(density,Math.max(1,Math.min(w/640f,h/480f)));
        mx/=scale; my/=scale; w=Math.round(w/scale); h=Math.round(h/scale);
        for(int i=hits.size()-1;i>=0;i--) if(inside(hits.get(i),mx,my)) { focus=i; activate(hits.get(i),inv,swap,craft); return; }
        // Inventory-only hit testing works before the first render (also used by headless clients).
        if(!crafting) {
            float x=w/2f-268,y=h/2f-175;
            if(mx>=x+365&&mx<x+504&&my>=y-49&&my<y-22) { openBook(); return; }
            int i=slotAt(mx,my,w,h);
            if(i>=0)activate(new Hit(0,0,0,0,"slot",i,true),inv,swap,craft); else source=-1;
        }
    }
    public String hoveredName(Inventory inv,ModelLibrary models,float mx,float my,int w,int h) {
        if(!open||crafting)return ""; int i=slotAt(mx,my,w,h);
        return i<0||inv.count(i)==0?"":models.name(inv.type(i));
    }
    public static int slotAt(float mx,float my,int w,int h) {
        float x=w/2f-268,y=h/2f-175;
        for(int row=0;row<4;row++) for(int col=0;col<9;col++) {
            float sx=x+col*56,sy=y+row*60+(row==3?10:0);
            if(mx>=sx&&mx<sx+53&&my>=sy&&my<sy+53)return row==3?col:9+row*9+col;
        }
        return -1;
    }
}
