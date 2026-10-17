package dev.jayms;

/** Close every owner-thread resource, then report the first failure with the rest suppressed. */
public final class CloseSequence {
    private Throwable failure;
    public void run(AutoCloseable action) {
        try { action.close(); }
        catch (Throwable error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
    }
    public boolean failed() { return failure != null; }
    public void finish() throws Exception {
        if (failure instanceof Error error) throw error;
        if (failure instanceof Exception error) throw error;
    }
}
