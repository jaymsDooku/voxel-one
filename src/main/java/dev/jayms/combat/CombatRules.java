package dev.jayms.combat;

import java.util.ArrayList;
import java.util.List;

import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Stack;

/** Tunable values for legacy-feeling combat at 20 ticks per second. */
public record CombatRules(
        boolean debuffs, double reach, int hurtTicks, double swordDamage, double armorReduction,
        double knockback, double sprintKnockback, double projectileKnockback, double lift, double healing, double splashRadius,
        int pearlCooldown, double pearlDamage, int speedDuration, int fireResistanceDuration,
        int poisonDuration, int slownessDuration) {

    // Lower Paper's base vertical lift and cap slightly for arena combos before the sprint bonus.
    private static final double ARENA_BASE_LIFT = 0.36;
    public static final int AUTO_CLICK_CPS = 9;
    // Launch speeds in blocks/tick. With this lift and Paper ground/air drag a settled
    // opponent coasts one block after a normal hit and three after an aligned sprint hit.
    private static final double NORMAL_PUSH = .211457;
    private static final double SPRINT_BONUS = .562670 - NORMAL_PUSH;
    private static final double PROJECTILE_PUSH = .40;

    public static final CombatRules NO_DEBUFF = new CombatRules(
            false, 3.0, 10, 8.0, 0.68, NORMAL_PUSH, SPRINT_BONUS, PROJECTILE_PUSH, ARENA_BASE_LIFT, 8.0, 4.0,
            320, 5.0, 90 * 20, 180 * 20, 8 * 20, 14 * 20);
    public static final CombatRules DEBUFF = new CombatRules(
            true, 3.0, 10, 8.0, 0.68, NORMAL_PUSH, SPRINT_BONUS, PROJECTILE_PUSH, ARENA_BASE_LIFT, 8.0, 4.0,
            320, 5.0, 90 * 20, 180 * 20, 8 * 20, 14 * 20);

    /** Existing custom rules use the same base push for melee and projectile hits. */
    public CombatRules(boolean debuffs, double reach, int hurtTicks, double swordDamage, double armorReduction,
                       double knockback, double sprintKnockback, double lift, double healing, double splashRadius,
                       int pearlCooldown, double pearlDamage, int speedDuration, int fireResistanceDuration,
                       int poisonDuration, int slownessDuration) {
        this(debuffs, reach, hurtTicks, swordDamage, armorReduction, knockback, sprintKnockback, knockback,
                lift, healing, splashRadius, pearlCooldown, pearlDamage, speedDuration, fireResistanceDuration,
                poisonDuration, slownessDuration);
    }

    public CombatRules {
        if (!valid(reach, 1, 6) || hurtTicks < 1 || hurtTicks > 60
                || !valid(swordDamage, 1, 20) || !valid(armorReduction, 0, .9)
                || !valid(knockback, 0, 1) || !valid(sprintKnockback, 0, 1) || !valid(projectileKnockback, 0, 1)
                || !valid(lift, 0, 1) || !valid(healing, 0, 20) || !valid(splashRadius, .5, 6)
                || !valid(pearlDamage, 0, 20) || !validDuration(pearlCooldown)
                || !validDuration(speedDuration) || !validDuration(fireResistanceDuration)
                || !validDuration(poisonDuration) || !validDuration(slownessDuration)) {
            throw new IllegalArgumentException("Combat rule out of range");
        }
    }

    public List<Stack> openingKit() {
        var kit = new ArrayList<Stack>(CombatEngine.INVENTORY_SIZE);
        kit.add(new Stack(Item.SWORD, 1));
        kit.add(new Stack(Item.PEARL, 16));
        kit.add(new Stack(Item.FOOD, 32));
        kit.add(new Stack(Item.SPEED, 1));
        kit.add(new Stack(Item.FIRE_RESISTANCE, 1));
        if (debuffs) {
            kit.add(new Stack(Item.POISON, 1));
            kit.add(new Stack(Item.SLOWNESS, 1));
        }
        while (kit.size() < CombatEngine.INVENTORY_SIZE) kit.add(new Stack(Item.HEALING, 1));
        return List.copyOf(kit);
    }

    private static boolean valid(double value, double min, double max) {
        return Double.isFinite(value) && value >= min && value <= max;
    }

    private static boolean validDuration(int ticks) {
        return ticks >= 1 && ticks <= 20 * 60 * 20;
    }
}
