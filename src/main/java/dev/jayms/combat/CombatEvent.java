package dev.jayms.combat;

/** Drain once after each tick. IDs are monotonic per simulation for audiovisual de-duplication. */
public record CombatEvent(long id, long tick, Type type, String actor, String target,
                          Vec3 position, double amount, String detail) {
    public enum Type { SWING, HIT, DAMAGE, HEAL, KNOCKBACK, ARMOR_BREAK, USE_START, USE_CANCEL,
        CONSUME, PROJECTILE_LAUNCH, PROJECTILE_IMPACT, PROJECTILE_EXPIRE, EFFECT_START,
        EFFECT_EXPIRE, TELEPORT, PEARL_REJECT, DEATH, RECOVER }
    public enum Effect { SPEED, FIRE_RESISTANCE, SLOWNESS, POISON }
    public enum DamageKind { MELEE, ARROW, POISON, FIRE, PEARL, ENVIRONMENT }
}
