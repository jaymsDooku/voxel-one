package dev.jayms.combat;

import java.util.Objects;

/** Immutable tunable profile. All durations are whole 20 Hz ticks. No engine dependencies. */
public record CombatRules(String id, boolean harmfulEffects, Damage damage, Physics physics,
                          Supplies supplies, Limits limits) {
    public static final int TICKS_PER_SECOND = 20;
    public CombatRules {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Profile id");
        Objects.requireNonNull(damage); Objects.requireNonNull(physics);
        Objects.requireNonNull(supplies); Objects.requireNonNull(limits);
    }
    public record Damage(double health, double melee, double reach, int hurtTicks,
                         double criticalMultiplier, double guardMultiplier, double bowMax,
                         int bowDrawTicks, int attributionTicks) {
        public Damage {
            positive(health, melee, reach, criticalMultiplier, bowMax);
            fraction(guardMultiplier); ticks(hurtTicks, bowDrawTicks, attributionTicks);
        }
    }
    public record Physics(double horizontalKnockback, double verticalKnockback,
                          double sprintBonus, double groundFriction, double airFriction,
                          double gravity, double verticalDrag, double projectileGravity,
                          double arrowSpeed, double potionSpeed, double pearlSpeed) {
        public Physics {
            positive(horizontalKnockback, verticalKnockback, sprintBonus, gravity,
                    projectileGravity, arrowSpeed, potionSpeed, pearlSpeed);
            fraction(groundFriction); fraction(airFriction); fraction(verticalDrag);
        }
    }
    public record Supplies(double splashHeal, double splashRadius, int useTicks,
                           int effectTicks, int poisonInterval, double poisonDamage,
                           int pearlCooldown, double pearlCost, int regenerationTicks,
                           double foodGain, double saturationGain, double exhaustionPerFood) {
        public Supplies {
            positive(splashHeal, splashRadius, poisonDamage, pearlCost, foodGain,
                    saturationGain, exhaustionPerFood);
            ticks(useTicks, effectTicks, poisonInterval, pearlCooldown, regenerationTicks);
            if (exhaustionPerFood < .25) throw new IllegalArgumentException("Exhaustion threshold too small");
        }
    }
    public record Limits(int maxParticipants, int maxProjectiles, int maxIntentsPerTick,
                         int maxStepTicks, int projectileLifeTicks) {
        public Limits {
            ticks(maxParticipants, maxProjectiles, maxIntentsPerTick, maxStepTicks, projectileLifeTicks);
            if (maxParticipants > 256 || maxProjectiles > 4096 || maxIntentsPerTick > 64
                    || maxStepTicks > 1200 || projectileLifeTicks > 12000)
                throw new IllegalArgumentException("Resource cap too large");
        }
    }
    public static CombatRules practice() { return initial("practice-p2s2", false); }
    public static CombatRules debuff() { return initial("practice-debuff-p2s2", true); }
    public static CombatRules hcf() { return initial("hcf-initial-p2s2", true); }
    private static CombatRules initial(String id, boolean harmful) {
        return new CombatRules(id, harmful,
                new Damage(20, 6, 3, 10, 1.5, .5, 10, 20, 200),
                new Physics(.35, .4, .15, .6, .91, .08, .98, .03, 2.5, .65, 1.2),
                new Supplies(8, 4, 32, 600, 20, 1, 300, 2, 80, 6, 7.2, 4),
                new Limits(64, 512, 8, 200, 200));
    }
    private static void positive(double... values) {
        for (double v : values) if (!Double.isFinite(v) || v <= 0 || v > 1e6)
            throw new IllegalArgumentException("Expected bounded positive value");
    }
    private static void fraction(double v) {
        if (!Double.isFinite(v) || v < 0 || v > 1) throw new IllegalArgumentException("Expected fraction");
    }
    private static void ticks(int... values) {
        for (int v : values) if (v < 1 || v > 1000000) throw new IllegalArgumentException("Expected bounded ticks");
    }
}
