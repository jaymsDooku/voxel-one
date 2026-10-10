package dev.jayms.render;

/** Bounded timestamp pairs. Busy slots are skipped; results are read only when both are ready. */
final class GpuTimestampRing implements AutoCloseable {
    interface Queries {
        int create();
        void stamp(int query);
        boolean available(int query);
        long result(int query);
        void delete(int query);
    }
    private final Queries queries;
    private final int[][] ids = new int[3][2];
    private final boolean[] pending = new boolean[3];
    private int cursor, active = -1;
    private long samples;
    private float last = Float.NaN;

    GpuTimestampRing(Queries queries) {
        this.queries = queries;
        for (int[] pair : ids) for (int i = 0; i < pair.length; i++) pair[i] = queries.create();
    }

    void begin() {
        if (active >= 0) throw new IllegalStateException("Timestamp stage already active");
        // Consume every ready slot without waiting for the GPU.
        for (int i = 0; i < ids.length; i++) {
            int slot = (cursor + i) % ids.length;
            if (pending[slot] && queries.available(ids[slot][0]) && queries.available(ids[slot][1])) {
                last = (queries.result(ids[slot][1]) - queries.result(ids[slot][0])) / 1_000_000f;
                samples++;
                pending[slot] = false;
            }
        }
        int slot = cursor;
        cursor = (cursor + 1) % ids.length;
        if (!pending[slot]) {
            active = slot;
            queries.stamp(ids[slot][0]);
        }
    }

    void end() {
        if (active < 0) return;
        queries.stamp(ids[active][1]);
        pending[active] = true;
        active = -1;
    }

    float milliseconds() { return last; }
    long samples() { return samples; }

    @Override public void close() {
        for (int[] pair : ids) for (int id : pair) queries.delete(id);
        active = -1;
    }
}
