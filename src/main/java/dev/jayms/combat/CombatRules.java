package dev.jayms.combat;

import java.util.ArrayList;
import java.util.List;

import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Stack;

/** Tunable values for legacy-feeling combat at 20 ticks per second. */
public record CombatRules(
        boolean debuffs, double reach, int hurtTicks, double swordDamage, double armorReduction,
        double knockback, double sprintKnockback, double lift, double healing, double splashRadius,
        int pearlCooldown, double pearlDamage, int speedDuration, int fireResistanceDuration,
        int poisonDuration, int slownessDuration) {

    public static final CombatRules NO_DEBUFF = new CombatRules(
            false, 3.05, 10, 8.0, 0.68, 0.40, 0.50, 0.40, 8.0, 4.0,
            320, 5.0, 90 * 20, 180 * 20, 8 * 20, 14 * 20);
    public static final CombatRules DEBUFF = new CombatRules(
            true, 3.05, 10, 8.0, 0.68, 0.40, 0.50, 0.40, 8.0, 4.0,
            320, 5.0, 90 * 20, 180 * 20, 8 * 20, 14 * 20);

    public CombatRules {
        if (!valid(reach, 1, 6) || hurtTicks < 1 || hurtTicks > 60
                || !valid(swordDamage, 1, 20) || !valid(armorReduction, 0, .9)
                || !valid(knockback, 0, 1) || !valid(sprintKnockback, 0, 1)
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
