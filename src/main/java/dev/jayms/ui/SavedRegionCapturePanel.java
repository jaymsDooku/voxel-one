package dev.jayms.ui;

import dev.jayms.net.*;
import dev.jayms.net.structure.StructurePackage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static dev.jayms.net.structure.StructurePackage.*;

/** Owner-thread controls; saved-file capture/export and cleanup stay on one worker.
 * No World, playable metadata, source mutation or placement authority. */
public final class SavedRegionCapturePanel implements AutoCloseable {
    public enum State {IDLE,RUNNING,SAVED,CANCELLED,FAILED,CLEANUP_FAILED,CLOSING,CLOSED}
    public record Status(State state,String message,int progress,String savedName) {}
    private final Thread owner=Thread.currentThread();
    private final Path source,directory,scratch;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"saved-region-capture");t.setDaemon(true);return t;});
    private volatile Status status=new Status(State.IDLE,"Choose first and second corners, then capture NAME",0,"");
    private final AtomicBoolean cancel=new AtomicBoolean();
    private volatile boolean closing;
    private volatile Path cleanupDirectory;
    // Accessed only on the worker. Retain failed cleanup until an explicit retry.
    private SavedRegionCapture capture;
    private boolean open,queued;
    private Position first,second,anchor;
    private String input="",fingerprint;
    public SavedRegionCapturePanel(Path source,Path directory,Path scratch) {
        this.source=Objects.requireNonNull(source).toAbsolutePath().normalize();this.directory=Objects.requireNonNull(directory).toAbsolutePath().normalize();this.scratch=Objects.requireNonNull(scratch).toAbsolutePath().normalize();
    }
    private void owner(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Saved capture panel owner required");}
    public Status status(){owner();return status;}
    /** Internal cleanup checkpoint only. Never rendered in the viewport. */
    public Path ownedCleanupDirectory(){owner();return cleanupDirectory;}
    /** Bind the validated visible snapshot once; reopening a changed file rejects. */
    public void bindSnapshot(String value) {
        owner();if(closing)throw new IllegalStateException("Capture panel closing");if(value==null||!value.matches("[0-9a-f]{64}")||fingerprint!=null&&!fingerprint.equals(value))throw new IllegalArgumentException("Capture snapshot identity");fingerprint=value;
    }
    public boolean open(){owner();return open;}
    public void toggle(){owner();if(!closing)open=!open;}
    public void character(int value){owner();if(open&&!closing&&input.length()<128&&value>=32&&value<127)input+=(char)value;}
    public void backspace(){owner();if(open&&!input.isEmpty())input=input.substring(0,input.length()-1);}
    public void enter(Position camera){owner();if(!open||closing)return;String command=input.trim();input="";execute(command,camera);}
    private synchronized void publish(Status value){status=value;}
    private synchronized void message(String value){var old=status;publish(new Status(old.state(),value,old.progress(),old.savedName()));}
    /** Same bounded command path used by keyboard input and captured integration. */
    public void execute(String command,Position camera) {
        owner();Objects.requireNonNull(command);Objects.requireNonNull(camera);if(closing)return;
        if(command.length()>128){message("Capture command exceeds input limit");return;}
        // queued is cleared by this owner only after the immutable terminal receipt.
        if(queued&&status.state()!=State.RUNNING&&status.state()!=State.CLOSING)queued=false;
        String[] words=command.trim().split("\\s+");String verb=words[0].toLowerCase(Locale.ROOT);
        if(verb.equals("cancel")){cancel.set(true);if(queued)message("Cancelling before package publication...");return;}
        if(verb.equals("retry")){retryCleanup();return;}
        if(queued||status.state()==State.CLEANUP_FAILED){message(status.state()==State.CLEANUP_FAILED?"Cleanup needs an explicit retry":"Wait for capture, or type cancel");return;}
        try {
            if(Set.of("first","second","anchor").contains(verb)) {
                Position point=words.length==1?camera:words.length==4?new Position(Integer.parseInt(words[1]),Integer.parseInt(words[2]),Integer.parseInt(words[3])):null;
                if(point==null||!new Protocol.Edit(point.x(),point.y(),point.z(),0).valid())throw new IllegalArgumentException();
                switch(verb){case "first"->first=point;case "second"->second=point;default->anchor=point;}
                message("Selected "+verb+" "+point.x()+" / "+point.y()+" / "+point.z());return;
            }
            if(!verb.equals("capture")){message("Use first, second, anchor, capture NAME, cancel or retry");return;}
            if(fingerprint==null){message("Wait for saved source validation before capture");return;}
            String name=command.trim().substring(verb.length()).trim();
            if(first==null||second==null){message("Choose first and second corners before capture");return;}
            if(!name.matches("[A-Za-z0-9 _-]{1,48}")){message("Name: 1-48 letters, digits, spaces, underscores or dashes");return;}
            Position origin=new Position(Math.min(first.x(),second.x()),Math.min(first.y(),second.y()),Math.min(first.z(),second.z()));var a=anchor==null?origin:anchor;
            Bounds bounds=new Bounds(Math.abs(first.x()-second.x())+1,Math.abs(first.y()-second.y())+1,Math.abs(first.z()-second.z())+1,a.x()-origin.x(),a.y()-origin.y(),a.z()-origin.z());
            Path output=directory.resolve(name+".vstr");if(output.equals(source))throw new IllegalArgumentException();
            cancel.set(false);queued=true;publish(new Status(State.RUNNING,"Checking saved construction...",0,""));worker.execute(()->run(name,bounds,origin,output,fingerprint));
        }catch(IllegalArgumentException failure){message("Invalid selection: check world bounds, size and anchor");}
    }
    private void run(String name,Bounds bounds,Position origin,Path output,String expectedFingerprint) {
        State result=State.FAILED;String text="Capture unavailable or exceeds limits";String saved="";
        try {
            if(Files.exists(output)&&Files.isSameFile(source,output))throw new IOException("Capture output aliases source");
            capture=new SavedRegionCapture(source,scratch,name,bounds,origin,expectedFingerprint);
            var asset=capture.capture(cancel::get,p->publish(new Status(State.RUNNING,switch(p.phase()){case OPENING->"Checking saved construction...";case CAPTURING->"Capturing selected construction...";case CLEANING,DONE->"Finishing capture...";},p.percent()*3/4,"")));
            capture.close();capture=null;
            publish(new Status(State.RUNNING,"Saving self-contained structure...",75,""));
            if(Files.exists(output)&&Files.isSameFile(source,output))throw new IOException("Capture output aliases source");
            asset.save(output,cancel::get,p->publish(new Status(State.RUNNING,"Saving self-contained structure...",75+p/4,"")));
            // Atomic replacement won. Late cancellation must not hide a saved file.
            result=State.SAVED;text="Saved "+name+" in the structure library";saved=name;
        }catch(CancellationException failure){result=State.CANCELLED;text="Cancelled; no package published";}
        catch(Exception failure){result=State.FAILED;}
        finally {
            try{if(capture!=null&&!capture.cleanupPending()){capture.close();capture=null;}}
            catch(IOException failure){result=State.FAILED;}
            if(capture!=null){try{cleanupDirectory=capture.ownedDirectory();}catch(IOException ignored){}publish(new Status(State.CLEANUP_FAILED,"Capture cleanup failed; type retry",0,""));}
            else {publish(new Status(closing?State.CLOSED:result,text,result==State.SAVED?100:0,saved));if(closing)worker.shutdown();}
        }
    }
    public void retryCleanup() {
        owner();if(status.state()!=State.CLEANUP_FAILED)return;queued=true;publish(new Status(State.CLOSING,"Retrying capture cleanup...",0,""));
        worker.execute(()->{try{capture.close();capture=null;cleanupDirectory=null;publish(new Status(closing?State.CLOSED:State.FAILED,"Capture not saved; cleanup complete",0,""));if(closing)worker.shutdown();}catch(IOException failure){publish(new Status(State.CLEANUP_FAILED,"Capture cleanup failed; type retry",0,""));}});
    }
    /** Nonblocking close. Observe CLOSED before deleting the caller-owned scratch.
     * Cleanup failure stays visible; a further explicit close retries it once. */
    @Override public void close() {
        owner();closing=true;open=false;cancel.set(true);
        if(status.state()==State.CLEANUP_FAILED){retryCleanup();return;}
        if(queued&&(status.state()==State.RUNNING||status.state()==State.CLOSING))return;
        publish(new Status(State.CLOSED,status.message(),status.progress(),status.savedName()));worker.shutdown();
    }
    public void render(Overlay ui,int width,int height) {
        owner();if(!open)return;var s=status;
        ui.text("Capture saved construction",20,140,1.5f);
        ui.text("first [X Y Z] | second [X Y Z] | anchor [X Y Z]",20,168,1.1f);
        ui.text("capture NAME | cancel | retry | F12 hide",20,190,1.1f);
        ui.text("No coordinates: use the camera cell. Source world stays unchanged.",20,212,1.05f);
        ui.text("First: "+label(first)+" | Second: "+label(second),20,238,1.05f);
        ui.text("Anchor: "+(anchor==null?"minimum corner":label(anchor)),20,258,1.05f);
        ui.text(s.message()+" "+(s.state()==State.RUNNING?s.progress()+"%":""),20,286,1.1f);
        ui.text("> "+input,20,312,1.15f);
    }
    private static String label(Position p){return p==null?"unset":p.x()+" / "+p.y()+" / "+p.z();}
}
