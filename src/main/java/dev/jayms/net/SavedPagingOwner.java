package dev.jayms.net;

import dev.jayms.World;
import java.io.IOException;
import java.util.Objects;

/** Private owner-thread slot for one coherent local/World/source bundle.
 * Reservation precedes source commit. Installation performs field assignments
 * only, then publishes one view pointer. No Main wiring or edit authority. */
final class SavedPagingOwner implements AutoCloseable {
    private final Thread owner=Thread.currentThread();
    private View active;
    private boolean busy,closed,cleanupComplete;

    SavedPagingOwner(LocalGame local,World world)throws IOException {
        Objects.requireNonNull(local);Objects.requireNonNull(world);
        if(world.isClosed()||world.models()!=local.models||world.terrain().seed!=local.seed||world.terrain().version!=local.generatorVersion)
            throw new IOException("Saved paging owner pair mismatch");
        active=new View(this);active.local=local;active.world=world;
    }
    private void owner()throws IOException {
        if(Thread.currentThread()!=owner)throw new IOException("Saved paging slot owner required");
    }
    private void available()throws IOException {
        owner();if(busy||closed||active.world.isClosed()||active.world.models()!=active.local.models
                ||active.world.terrain().seed!=active.local.seed||active.world.terrain().version!=active.local.generatorVersion)
            throw new IOException("Saved paging slot unavailable");
    }
    View view()throws IOException {available();return active;}
    boolean isClosed()throws IOException {owner();return closed;}
    private void current(View view)throws IOException {
        available();if(active!=view)throw new IOException("Saved paging slot view replaced");
    }
    /** Immutable after publication. Held old views cannot supply stale parts. */
    static final class View {
        private final SavedPagingOwner owner;
        private LocalGame local;
        private World world;
        private SavedPagingReplacement.Transfer transfer;
        private View(SavedPagingOwner owner){this.owner=owner;}
        LocalGame local()throws IOException {owner.current(this);return local;}
        World world()throws IOException {owner.current(this);return world;}
        SavedPagingReplacement.Transfer transfer()throws IOException {owner.current(this);return transfer;}
    }
    /** Keep reservation and commit in one guarded owner call. No reservation
     * handle exposes an unguarded mutation path to another thread or source. */
    SavedPagingReplacement.Transfer installFrom(SavedPagingReplacement replacement,java.util.function.BooleanSupplier cancel)throws IOException {
        available();Objects.requireNonNull(replacement);Objects.requireNonNull(cancel);
        if(!replacement.matchesOwner(active.local,active.world))throw new IOException("Saved paging slot source mismatch");
        var next=new View(this);busy=true;
        try {
            var transfer=replacement.take(cancel);var session=transfer.session();
            // No callbacks, allocation, cancellation or fallible guard after take.
            next.local=session.local();next.world=session.world();next.transfer=transfer;active=next;return transfer;
        }finally{busy=false;}
    }
    /** Close only the currently installed bundle. Failed cleanup remains caller
     * owned for explicit retry; superseded Worlds are retired separately. */
    @Override public void close()throws Exception {
        owner();if(busy)throw new IOException("Saved paging slot close reentry");if(cleanupComplete)return;
        closed=true;busy=true;
        try {
            if(active.transfer==null)active.world.close();else active.transfer.close();
            cleanupComplete=true;
        }finally{busy=false;}
    }
}
