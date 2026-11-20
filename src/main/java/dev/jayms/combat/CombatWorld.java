package dev.jayms.combat;

import java.util.OptionalDouble;

/**
 * Trusted server collision seam. Never implement these queries using client assertions.
 * Fractions are in [0,1], including an initial overlap at zero. Return the FIRST solid
 * collision of the whole swept sphere, not an endpoint sample. Equal solid/entity hits
 * favor the solid. Queries must be deterministic for the duration of a simulation tick.
 */
public interface CombatWorld {
    OptionalDouble firstSolid(Vec3 from, Vec3 to, double radius);

    /** Includes whole body collision, world bounds, support and hazardous terrain. */
    boolean safeLanding(Vec3 feet, double halfWidth, double height);

    /** Sweeps the entire body and resolves walls/floor/ceiling; no tunneling allowed. */
    Motion move(Vec3 feet, Vec3 velocity, double halfWidth, double height);

    record Motion(Vec3 feet, Vec3 velocity, boolean grounded) {
        public Motion {
            java.util.Objects.requireNonNull(feet);
            java.util.Objects.requireNonNull(velocity);
        }
    }
}
