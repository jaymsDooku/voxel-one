package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Item;

/** Camera-only attack and equip timelines. The match still owns combat and inventory. */
final class PracticeHandAnimation {
    private static final long SWING_NANOS = 300_000_000L;
    private static final long EQUIP_LEG_NANOS = 125_000_000L;
    private static final long USE_NANOS = 160_000_000L;

    record Pose(Item item, float swing, float equipDrop, boolean blocking, float walkPhase, float walkAmount) {}

    private Item selected, displayed;
    private Item swingItem, useItem;
    private long updatedAt, swingAt, useAt, loweredFor;
    private long gaitAt;
    private double walkPhase, walkAmount;

    void reset(Item item, long now) {
        selected = displayed = item;
        swingItem = useItem = null;
        updatedAt = now;
        loweredFor = 0;
        gaitAt = now;
        walkPhase = walkAmount = 0;
    }

    void select(Item item, long now) {
        if (displayed == null) {
            reset(item, now);
            return;
        }
        long elapsed = Math.max(0, now - updatedAt);
        if (elapsed > 0) updatedAt = now;
        if (displayed != selected) {
            long untilSwap = EQUIP_LEG_NANOS - loweredFor;
            if (elapsed >= untilSwap) {
                displayed = selected;
                loweredFor = Math.max(0, EQUIP_LEG_NANOS - (elapsed - untilSwap));
            } else {
                loweredFor += elapsed;
            }
        } else {
            loweredFor = Math.max(0, loweredFor - elapsed);
        }
        selected = item;
    }

    void attack(Item item, boolean using, long now) {
        select(item, now);
        if (using && holdsToUse(item)) return;
        if (swingItem == null || now - swingAt >= SWING_NANOS / 2) {
            swingAt = now;
            swingItem = item;
        }
    }

    void use(Item item, long now) {
        select(item, now);
        if (item == Item.EMPTY || item == Item.SWORD || item == Item.ARROW) return;
        useItem = item;
        useAt = now;
    }

    private static boolean holdsToUse(Item item) {
        return switch (item) {
            case SWORD, SPEED, FIRE_RESISTANCE, FOOD, BOW -> true;
            default -> false;
        };
    }

    Pose pose(Item item, boolean using, boolean moving, long now) {
        select(item, now);
        double seconds = Math.max(0, now - gaitAt) / 1e9;
        if (seconds > 0) gaitAt = now;
        if (moving) walkPhase = (walkPhase + 11 * seconds) % (2 * Math.PI);
        double target = moving ? 1 : 0;
        walkAmount = target + (walkAmount - target) * Math.exp(-14 * seconds);
        boolean blocking = using && selected == Item.SWORD && displayed == selected;
        float swing = !blocking && swingItem == displayed
                ? Math.max(0, Math.min(1, (now - swingAt) / (float) SWING_NANOS))
                : 1;
        float drop = loweredFor / (float) EQUIP_LEG_NANOS;
        // Move the current item for a use edge without changing the equip or swing clocks.
        if (useItem == selected && displayed == selected) {
            float recovery = Math.max(0, 1 - Math.max(0, now - useAt) / (float) USE_NANOS);
            drop = Math.max(drop, .17f * recovery * recovery);
        }
        return new Pose(displayed, swing, drop, blocking,
                (float) walkPhase, (float) walkAmount);
    }
}
