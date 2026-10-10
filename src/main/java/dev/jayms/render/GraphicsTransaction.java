package dev.jayms.render;

import java.util.function.Consumer;

/** Prepare -> display setup -> prepare targets -> save/swap. Last good renderer survives failures. */
public final class GraphicsTransaction<R extends AutoCloseable> implements AutoCloseable {
    public interface Display {Object capture();void apply(GraphicsProfile profile)throws Exception;void restore(Object state,GraphicsProfile profile)throws Exception;}
    public interface Factory<R>{R prepare(GraphicsProfile effective)throws Exception;}
    public interface Store {void save(GraphicsProfile profile)throws Exception;void beginRisk(GraphicsProfile previous)throws Exception;void endRisk()throws Exception;}
    private final Display display;private final Factory<R> factory;private final Store store;private final Consumer<R> publish;
    private final GraphicsProfile.Capabilities capabilities;
    private R active,previous;
    private GraphicsProfile requested,effective,previousRequested,previousEffective;
    private Object previousDisplay;
    private long deadline;
    private String message="";
    public GraphicsTransaction(R active,GraphicsProfile requested,GraphicsProfile.Capabilities cap,Display display,Factory<R> factory,Store store,Consumer<R> publish){this.active=active;this.requested=requested;capabilities=cap;effective=requested.effective(cap).profile();this.display=display;this.factory=factory;this.store=store;this.publish=publish;}
    public GraphicsProfile requested(){return requested;}public GraphicsProfile effective(){return effective;}
    public boolean pending(){return previous!=null;}public String message(){return message;}
    public int remaining(long now){return pending()?(int)Math.max(0,(deadline-now+999_999_999L)/1_000_000_000L):0;}
    public boolean apply(GraphicsProfile draft,long now){
        if(pending()){message="Confirm or revert the display test first.";return false;}
        Object state=display.capture();R replacement=null;boolean risk=draft.riskyComparedTo(requested),riskStarted=false;
        GraphicsProfile.Effective resolution=draft.effective(capabilities);
        try{
            if(risk){store.beginRisk(requested);riskStarted=true;}
            display.apply(resolution.profile());
            replacement=factory.prepare(resolution.profile());
            if(!risk)store.save(draft);
            R old=active;GraphicsProfile oldRequested=requested,oldEffective=effective;
            active=replacement;requested=draft;effective=resolution.profile();publish.accept(active);
            if(risk){previous=old;previousRequested=oldRequested;previousEffective=oldEffective;previousDisplay=state;deadline=now+15_000_000_000L;message="Keep changes? Revert in 15 seconds.";}
            else{dispose(old);message=resolution.reasons().isEmpty()?"Graphics applied and saved.":String.join(" ",resolution.reasons());}
            return true;
        }catch(Exception e){if(replacement!=null)dispose(replacement);try{display.restore(state,effective);}catch(Exception ignored){}if(riskStarted)try{store.endRisk();}catch(Exception ignored){}message="Graphics apply failed; kept the working settings.";return false;}
    }
    public void confirm(){if(!pending())return;try{store.save(requested);store.endRisk();dispose(previous);previous=null;message="Display confirmed and saved.";}catch(Exception e){revert();message="Could not save display settings; reverted.";}}
    public void tick(long now,boolean focused){if(pending()&&(now>=deadline||!focused))revert();}
    public void revert(){if(!pending())return;R rejected=active;try{display.restore(previousDisplay,previousEffective);}catch(Exception e){message="Display restore failed; retained last good renderer.";}
        active=previous;requested=previousRequested;effective=previousEffective;previous=null;publish.accept(active);dispose(rejected);try{store.endRisk();}catch(Exception ignored){}message="Reverted to last applied graphics.";
    }
    private void dispose(R renderer){try{renderer.close();}catch(Exception e){message="Renderer cleanup failed.";}}
    @Override public void close(){if(pending())revert();}
}
