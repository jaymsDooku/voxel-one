package dev.jayms.combat.client;

/** Sprint key behavior based on the classic ToggleSneak tap and hold controls. */
final class ArenaSprintToggle {
    private static final long HOLD_NANOS = 300_000_000;
    private boolean enabled, pressed, currentRun, enabledBeforePress;
    private long pressedAt, wallTickAtRelease;

    void press(long now) {
        if (pressed) return;
        enabledBeforePress = enabled;
        enabled = !requested();
        pressed = true;
        currentRun = false;
        pressedAt = now;
    }

    void release(long now, boolean canContinueRun, long wallTick) {
        if (!pressed) return;
        pressed = false;
        if (now - pressedAt > HOLD_NANOS) {
            currentRun = enabled && canContinueRun;
            enabled = false;
            wallTickAtRelease = wallTick;
        }
    }

    void stopRun() { currentRun = false; }

    boolean updateRun(boolean canContinueRun, long wallTick) {
        if (!currentRun || canContinueRun && wallTick <= wallTickAtRelease) return false;
        stopRun();
        return true;
    }

    /** Keep a completed toggle across UI changes and discard any unfinished key gesture. */
    void suspend() {
        if (pressed) enabled = enabledBeforePress;
        pressed = false;
        stopRun();
    }

    boolean requested() { return enabled || currentRun; }
    boolean continuingRun() { return currentRun; }

    String status() {
        if (currentRun) return "Sprint (Current run)";
        if (!enabled) return "";
        return pressed ? "Sprint (Key held)" : "Sprint (Toggled)";
    }
}
