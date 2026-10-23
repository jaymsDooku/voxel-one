package dev.jayms.net;

import java.io.IOException;
import java.nio.file.*;

/** Save publication policy. Structure transactions require atomic replacement. */
final class WorldSavePublication {
    private WorldSavePublication() {}
    @FunctionalInterface interface Move {
        void apply(Path source, Path target, CopyOption... options) throws IOException;
    }
    static void replace(Path source, Path target, boolean requireAtomic) throws IOException {
        replace(source, target, requireAtomic, Files::move);
    }
    static void replace(Path source, Path target, boolean requireAtomic, Move move) throws IOException {
        try {
            move.apply(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            if (requireAtomic) throw unsupported;
            move.apply(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
