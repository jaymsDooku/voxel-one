package dev.jayms.ui;

import dev.jayms.*;
import dev.jayms.net.*;
import dev.jayms.net.structure.*;
import static dev.jayms.net.structure.StructurePackage.*;
import static org.lwjgl.glfw.GLFW.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/** Experimental keyboard-driven structure library; workers never mutate the live World. */
public final class StructureMenu implements AutoCloseable {
    public boolean open;
    public volatile String message="Mark two corners, choose an anchor, then capture a named structure.";
    public volatile int progress;
    public volatile boolean busy;
    public volatile StructurePackage selected;
    public Position first,second,anchor,mark=new Position(0,64,0),target=mark;
    public int rotation;
    public volatile String input="";
    private final Path directory;
    private volatile List<Path> saved=List.of();
    private int index;
    private int networkResults;
    private final StructureEngine engine=new StructureEngine();
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"structure-library");t.setDaemon(true);return t;});
    private AtomicBoolean cancellation=new AtomicBoolean();
    private final AtomicReference<Completion> ready=new AtomicReference<>();
    private volatile Boolean conflict;
    private final AtomicBoolean closed=new AtomicBoolean();
    private record Completion(Runnable action,AutoCloseable resource,AtomicBoolean cancel) {}
    private interface Work {Completion run(AtomicBoolean cancel)throws Exception;}
    private final java.util.function.Supplier<Protocol.Pose> player;
    public StructureMenu(Path directory){this(directory, () -> null);}
    public StructureMenu(Path directory, java.util.function.Supplier<Protocol.Pose> player){this.directory=directory;this.player=player;refresh();}
    private void refresh() {
        try {Files.createDirectories(directory);try(var files=Files.list(directory)){saved=files.filter(p->p.getFileName().toString().endsWith(".vstr")).sorted().limit(256).toList();}}
        catch(IOException failure){message="Could not open structure library";}
    }
    public void show(Position mark,Position target){this.mark=mark;this.target=target;open=true;conflict=null;}
    public void character(int value){if(input.length()<4096&&value>=32&&value<127)input+=(char)value;}
    private void start(String label,Work work) {
        if(closed.get())return;
        if(busy){message="Wait for the current operation, or cancel it.";return;}
        var cancel=new AtomicBoolean();cancellation=cancel;busy=true;progress=0;message=label;
        worker.submit(()->{
            try {deliver(work.run(cancel));}
            catch(Exception failure){deliver(new Completion(()->message=cancel.get()?"Cancelled; nothing placed":String.valueOf(failure.getMessage()),null,cancel));}
        });
    }
    private void deliver(Completion completion) {
        ready.set(completion);
        if(closed.get()&&ready.compareAndSet(completion,null))try{if(completion.resource!=null)completion.resource.close();}catch(Exception ignored){}
    }
    public void pump() {
        var completion=ready.getAndSet(null);if(completion==null)return;
        busy=false;
        try {if(!completion.cancel.get())completion.action.run();else message="Cancelled; nothing placed";}
        finally {try{if(completion.resource!=null)completion.resource.close();}catch(Exception ignored){}}
    }
    public void cancel(MultiplayerClient network){cancellation.set(true);if(network!=null&&network.structurePending())network.cancelStructure();message="Cancelling before commit...";}
    public void key(int key,int action,int mods,World world,LocalGame local,MultiplayerClient network) {
        if(action!=GLFW_PRESS&&action!=GLFW_REPEAT)return;
        if(key==GLFW_KEY_ESCAPE){if(busy||network!=null&&network.structurePending())cancel(network);else open=false;return;}
        if(key==GLFW_KEY_BACKSPACE){if(!input.isEmpty())input=input.substring(0,input.length()-1);return;}
        if(key==GLFW_KEY_ENTER){String command=input.trim();input="";execute(command,world,local,network);return;}
        if(!input.isEmpty())return;
        if(key==GLFW_KEY_UP||key==GLFW_KEY_DOWN) {
            if(saved.isEmpty())return;index=Math.floorMod(index+(key==GLFW_KEY_DOWN?1:-1),saved.size());
            Path file=saved.get(index);start("Loading "+file.getFileName(),cancel->{var asset=StructurePackage.load(file,cancel::get,p->progress=p);return new Completion(()->{selected=asset;message="Selected "+asset.name;conflict=null;},null,cancel);});
        }
    }
    public void execute(String command,World world,LocalGame local,MultiplayerClient network) {
        try {
            String[] words=command.split("\\s+");String verb=words[0].toLowerCase(Locale.ROOT);
            if(verb.equals("first")){first=mark;message="First corner "+first;return;}
            if(verb.equals("second")){second=mark;message="Second corner "+second;return;}
            if(verb.equals("anchor")){anchor=mark;message="Capture anchor "+anchor;return;}
            if(verb.equals("at")&&words.length==4){target=new Position(Integer.parseInt(words[1]),Integer.parseInt(words[2]),Integer.parseInt(words[3]));conflict=null;if(selected!=null)execute("preview",world,local,network);return;}
            if(verb.equals("rotate")){rotation=(rotation+1)%4;conflict=null;message="Orientation "+rotation*90+" degrees";if(selected!=null)execute("preview",world,local,network);return;}
            if(verb.equals("cancel")){cancel(network);return;}
            if(verb.equals("capture")) {
                if(first==null||second==null)throw new IOException("Mark first and second corners before capture");
                Position origin=new Position(Math.min(first.x(),second.x()),Math.min(first.y(),second.y()),Math.min(first.z(),second.z()));
                var a=anchor==null?origin:anchor;
                var bounds=new Bounds(Math.abs(first.x()-second.x())+1,Math.abs(first.y()-second.y())+1,Math.abs(first.z()-second.z())+1,a.x()-origin.x(),a.y()-origin.y(),a.z()-origin.z());
                String name=command.substring(verb.length()).trim();var snapshot=world.structureSnapshot();
                start("Capturing physical construction...",cancel->{
                    var asset=StructurePackage.capture(name,bounds,origin,snapshot,cancel::get,p->progress=p);StructurePackage.cancelled(cancel::get);
                    asset.save(directory.resolve(asset.name+".vstr"));
                    return new Completion(()->{selected=asset;refresh();message="Captured "+asset.name;conflict=null;},null,cancel);
                });return;
            }
            if(verb.equals("import")) {
                Path file=Path.of(command.substring(verb.length()).trim());
                start("Validating imported package...",cancel->{var asset=StructurePackage.load(file,cancel::get,p->progress=p);StructurePackage.cancelled(cancel::get);asset.save(directory.resolve(asset.name+".vstr"));return new Completion(()->{selected=asset;refresh();conflict=null;message="Imported "+asset.name;},null,cancel);});return;
            }
            if(verb.equals("undo")) {
                if(network!=null){message=network.undoStructure()?"Undo sent to server":"Wait or update server to use structures";return;}
                engine.undo("offline",local.structureAuthority(world,player));message="Structure placement undone";conflict=null;return;
            }
            var asset=selected;if(asset==null)throw new IOException("Choose a saved structure with Up/Down first");
            if(verb.equals("export")) {
                Path file=directory.resolve("exports").resolve(asset.name+".vstr");start("Exporting self-contained package...",cancel->{asset.save(file);return new Completion(()->message="Exported "+file,null,cancel);});return;
            }
            if(verb.equals("preview")) {
                var snapshot=world.structureSnapshot();var point=target;int orientation=rotation;
                start("Checking destination footprint...",cancel->{
                    var box=StructureEngine.footprint(asset,point,orientation);boolean blocked=false;
                    for(var cell:asset.cells){StructurePackage.cancelled(cancel::get);var p=StructureEngine.rotate(cell.position(),asset.bounds.x(),asset.bounds.z(),orientation);if(snapshot.cell(box.minX()+p.x(),box.minY()+p.y(),box.minZ()+p.z()).occupied()!=0)blocked=true;}
                    final boolean overlap=blocked;return new Completion(()->{
                        if(selected!=asset||!target.equals(point)||rotation!=orientation){conflict=null;execute("preview",world,local,network);return;}
                        conflict=overlap;message=overlap?"Blocking occupied cells; placement rejected":"Footprint clear; server revalidates at commit";
                    },null,cancel);
                });return;
            }
            if(verb.equals("place")) {
                var point=target;int orientation=rotation;
                if(network!=null) {
                    start("Preparing structure upload...",cancel->{byte[] payload=asset.encode();return new Completion(()->message=network.placeStructure(payload,point,orientation)?"Upload queued; wait for server result":"Wait or update server to use structures",null,cancel);});return;
                }
                var snapshot=world.structureSnapshot();start("Staging placement; world unchanged...",cancel->{
                    var prepared=engine.prepare(asset,point,orientation,snapshot,cancel::get,p->progress=p);
                    return new Completion(()->{try{prepared.commit("offline",local.structureAuthority(world,player));message="Placed and saved "+asset.name+". Move the target to place again.";conflict=true;}catch(Exception failure){message=failure.getMessage();}},prepared,cancel);
                });return;
            }
            message="Unknown command; use the commands shown below.";
        }catch(Exception failure){message=String.valueOf(failure.getMessage());}
    }
    public void render(Overlay ui,int width,int height,Matrix4fc projection,Matrix4fc view,MultiplayerClient network) {
        if(!open)return;
        if(network!=null&&networkResults!=network.structureResults){networkResults=network.structureResults;message=network.structureMessage;}
        // Bounds-only world preview. No detailed voxel meshes are built for preview movement.
        if(selected!=null) {
            var box=StructureEngine.footprint(selected,target,rotation);var matrix=new Matrix4f(projection).mul(view);var points=new Vector4f[8];
            for(int i=0;i<8;i++)points[i]=matrix.transform(new Vector4f(box.minX()+((i&1)==0?0:box.width()),box.minY()+((i&2)==0?0:box.height()),box.minZ()+((i&4)==0?0:box.depth()),1));
            for(int i=0;i<8;i++)for(int bit=1;bit<=4;bit*=2)if((i&bit)==0) {
                var a=points[i];var b=points[i|bit];if(a.w>0&&b.w>0)ui.line((a.x/a.w*.5f+.5f)*width,(.5f-a.y/a.w*.5f)*height,(b.x/b.w*.5f+.5f)*width,(.5f-b.y/b.w*.5f)*height,2,Boolean.TRUE.equals(conflict)?1:.2f,Boolean.TRUE.equals(conflict)?.2f:1,.3f,.8f);
            }
            var marker=matrix.transform(new Vector4f(target.x()+.5f,target.y()+.5f,target.z()+.5f,1));
            if(marker.w>0){float x=(marker.x/marker.w*.5f+.5f)*width,y=(.5f-marker.y/marker.w*.5f)*height;ui.line(x-8,y,x+8,y,3,1,.8f,.1f,1);ui.line(x,y-8,x,y+8,3,1,.8f,.1f,1);}
        }
        float scale=Math.min(1,width/1024f);ui.coordinateScale(scale);
        ui.rectangle(12,12,Math.min(width/scale-24,980),326,.03f,.05f,.09f,.94f);
        ui.text("STRUCTURES | Experimental | F12 / Esc: close",28,30,1.5f,1,.8f,.3f,1);
        ui.text("Aim, open F12, type first / second / anchor to mark the cell you aimed at.",28,59,1.1f);
        ui.text("Close, aim elsewhere, reopen to mark another cell. Up/Down: browse saved structures.",28,80,1.1f);
        ui.text("capture NAME | at X Y Z | rotate | preview | place | undo | cancel",28,108,1.2f);
        ui.text("import PATH | export (to library exports folder). Empty source space preserves terrain.",28,133,1.1f);
        ui.text("Selected: "+(selected==null?"none":selected.name+" | "+selected.bounds.x()+" x "+selected.bounds.y()+" x "+selected.bounds.z()),28,160,1.2f);
        ui.text("Target anchor "+target.x()+", "+target.y()+", "+target.z()+" | orientation "+rotation*90+" deg | conflicts: "+(conflict==null?"run preview":conflict?"blocked":"clear"),28,184,1.1f);
        ui.text("> "+input+"_",28,214,1.4f,.8f,1,.9f,1);
        String status=network!=null&&network.structurePending()?network.structureMessage:message;
        ui.text((busy?"Working "+progress+"% | ":"")+status.substring(0,Math.min(status.length(),110)),28,248,1.1f,1,.8f,.4f,1);
        ui.text("Reject occupied destination cells. Placement is independent; one undo per placement.",28,277,1.1f);
        ui.text("City performance and supported geometry/instance limits are not qualified.",28,301,1.1f);
        ui.coordinateScale(1);
    }
    @Override public void close(){closed.set(true);cancellation.set(true);worker.shutdownNow();var completion=ready.getAndSet(null);if(completion!=null&&completion.resource!=null)try{completion.resource.close();}catch(Exception ignored){}}
}
