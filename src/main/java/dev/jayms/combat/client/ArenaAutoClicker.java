package dev.jayms.combat.client;

import static dev.jayms.combat.CombatRules.AUTO_CLICK_CPS;

/** Drives ordinary attack edges on a nine-click-per-second clock. */
final class ArenaAutoClicker {
    private static final long SECOND = 1_000_000_000L;
    private boolean enabled;
    private long started, lastSlot = -1;

    void toggle() {
        enabled = !enabled;
        lastSlot = -1;
    }

    boolean enabled() { return enabled; }

    boolean poll(long now, boolean canAttack) {
        if (!enabled || !canAttack) {
            lastSlot = -1;
            return false;
        }
        if (lastSlot < 0) started = now;
        long elapsed = now - started;
        long slot = elapsed / SECOND * AUTO_CLICK_CPS + elapsed % SECOND * AUTO_CLICK_CPS / SECOND;
        if (slot == lastSlot) return false;
        // A late render frame can submit one current swing, never a backlog.
        lastSlot = slot;
        return true;
    }
}
