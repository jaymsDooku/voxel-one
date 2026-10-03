package dev.jayms;

/** Cursor deltas for a captured right-button orbit; reset on each new drag. */
final class IsometricOrbitDrag {
    private boolean active, first;
    private double previousX;

    void begin() {
        active = true;
        first = true;
    }

    void end() {
        active = false;
    }

    boolean active() {
        return active;
    }

    void move(double x, float sensitivity, IsometricCamera camera) {
        if (!active || !Double.isFinite(x)) return;
        // Cursor capture may reposition the pointer. Establish a fresh baseline first.
        if (!first) camera.rotateDegrees((float) (x - previousX) * sensitivity);
        previousX = x;
        first = false;
    }
}
