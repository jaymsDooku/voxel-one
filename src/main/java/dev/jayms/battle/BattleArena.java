package dev.jayms.battle;

import dev.jayms.physics.*;
import java.util.*;
import org.joml.Vector3f;

/** Offline, bounded combat scene. All combatants use the same damage and joint paths. */
public final class BattleArena {
    public static final float STEP = 1f / 120;
    public enum Part { HEAD, TORSO, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG }
    public enum State { READY, STAGGERED, DOWN, OUT }
    public record Rules(float jabDamage, float heavyDamage, float jabImpulse, float heavyImpulse,
                        float knockdownImpulse, float recoverySeconds, float cooldown, float speed) {
        public Rules {
            for (float value : new float[]{jabDamage, heavyDamage, jabImpulse, heavyImpulse,
                    knockdownImpulse, recoverySeconds, cooldown, speed})
                if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException("Positive finite battle rules required");
        }
        public static Rules defaults() { return new Rules(24, 38, 7, 28, 20, 2.2f, .48f, 3.5f); }
    }
    public static final class Limb {
        public final Part part;
        public final RigidBody body;
        public final float maximum;
        public float health;
        public boolean attached = true;
        private Constraints.Joint joint;
        private Limb(Part part, Vector3f position, Vector3f half, float mass, float health) {
            this.part = part; this.body = new RigidBody(position, half, mass);
            this.health = this.maximum = health;
        }
    }
    public static final class Fighter {
        public final EnumMap<Part, Limb> parts = new EnumMap<>(Part.class);
        public State state = State.READY;
        public float yaw, recovery, cooldown;
        public int swings, hits, knockdowns;
        public boolean lastHeavy;
        public final boolean human;
        private Fighter(boolean human) { this.human = human; }
        public Limb part(Part part) { return parts.get(part); }
        public Vector3f position() { return part(Part.TORSO).body.position; }
        public boolean canAttack(boolean right) {
            return state == State.READY && part(right ? Part.RIGHT_ARM : Part.LEFT_ARM).attached;
        }
        public float movementFactor() {
            int legs = (part(Part.LEFT_LEG).attached ? 1 : 0) + (part(Part.RIGHT_LEG).attached ? 1 : 0);
            return legs == 2 ? 1 : legs == 1 ? .45f : .15f;
        }
        public int detached() { return (int) parts.values().stream().filter(p -> !p.attached).count(); }
    }
    /** One swing is consumed even on a miss. Reusing it cannot cause repeated damage. */
    public static final class Swing {
        public final Fighter attacker;
        public final boolean heavy;
        private boolean consumed;
        private Swing(Fighter attacker, boolean heavy) { this.attacker = attacker; this.heavy = heavy; }
    }
    public final Rules rules;
    public PhysicsWorld physics;
    public Fighter player, opponent;
    public int resets;
    public float time;
    public String lastImpact = "Aim at a part. Close to punching range.";
    public BattleArena() { this(Rules.defaults()); }
    public BattleArena(Rules rules) { this.rules = rules; reset(); resets = 0; }

    public void reset() {
        // Replace the whole ephemeral world, including contacts and solver state.
        physics = new PhysicsWorld();
        terrain(new Vector3f(0, -.5f, 0), new Vector3f(12, .5f, 12));
        terrain(new Vector3f(-12.5f, 2, 0), new Vector3f(.5f, 2.5f, 13));
        terrain(new Vector3f(12.5f, 2, 0), new Vector3f(.5f, 2.5f, 13));
        terrain(new Vector3f(0, 2, -12.5f), new Vector3f(12, 2.5f, .5f));
        terrain(new Vector3f(0, 2, 12.5f), new Vector3f(12, 2.5f, .5f));
        player = fighter(true, 0, 3, (float)Math.PI);
        opponent = fighter(false, 0, -3, 0);
        time = 0; resets++;
        lastImpact = "Fresh round. Blue: you. Amber: sparring partner.";
    }
    private void terrain(Vector3f position, Vector3f half) {
        var body = new RigidBody(position, half, 0); body.layer = 1; physics.bodies.add(body);
    }
    private Fighter fighter(boolean human, float x, float z, float yaw) {
        var f = new Fighter(human); f.yaw = yaw;
        add(f, Part.TORSO, new Vector3f(.52f, .6f, .32f), 18, 140);
        add(f, Part.HEAD, new Vector3f(.36f, .35f, .34f), 4, 100);
        add(f, Part.LEFT_ARM, new Vector3f(.21f, .57f, .27f), 3, 64);
        add(f, Part.RIGHT_ARM, new Vector3f(.21f, .57f, .27f), 3, 64);
        add(f, Part.LEFT_LEG, new Vector3f(.23f, .55f, .28f), 6, 80);
        add(f, Part.RIGHT_LEG, new Vector3f(.23f, .55f, .28f), 6, 80);
        pose(f, x, z);
        for (var p : f.parts.values()) if (p.part != Part.TORSO) {
            Vector3f anchor = switch (p.part) {
                case HEAD -> new Vector3f(0, .61f, 0);
                case LEFT_ARM -> new Vector3f(-.54f, .43f, 0);
                case RIGHT_ARM -> new Vector3f(.54f, .43f, 0);
                case LEFT_LEG -> new Vector3f(-.27f, -.61f, 0);
                case RIGHT_LEG -> new Vector3f(.27f, -.61f, 0);
                default -> throw new IllegalStateException();
            };
            Vector3f worldAnchor = f.part(Part.TORSO).body.anchor(anchor);
            Vector3f local = p.body.rotation.transformInverse(worldAnchor.sub(p.body.position));
            p.joint = new Constraints.Hinge(f.part(Part.TORSO).body, p.body, anchor, local, new Vector3f(0, 0, 1));
            physics.joints.add(p.joint);
        }
        return f;
    }
    private void add(Fighter f, Part part, Vector3f half, float mass, float health) {
        var p = new Limb(part, new Vector3f(), half, mass, health);
        p.body.layer = f.human ? 2 : 4;
        // Attached boxes do not collide with their own joints or the other standing rig.
        p.body.mask = 1 | 8;
        f.parts.put(part, p); physics.bodies.add(p.body);
    }
    private Vector3f offset(Part part) {
        return switch (part) {
            case TORSO -> new Vector3f(0, 1.75f, 0);
            case HEAD -> new Vector3f(0, 2.72f, 0);
            case LEFT_ARM -> new Vector3f(-.76f, 1.76f, 0);
            case RIGHT_ARM -> new Vector3f(.76f, 1.76f, 0);
            case LEFT_LEG -> new Vector3f(-.27f, .57f, 0);
            case RIGHT_LEG -> new Vector3f(.27f, .57f, 0);
        };
    }
    private void pose(Fighter f, float x, float z) {
        for (var p : f.parts.values()) if (p.attached) {
            var b = p.body; b.rotation.identity().rotateY(f.yaw);
            b.position.set(b.rotation.transform(offset(p.part))).add(x, 0, z);
            b.velocity.zero(); b.angularVelocity.zero(); b.force.zero(); b.torque.zero();
            b.kinematic = true; b.wake();
        }
    }
    public Swing swing(Fighter f, boolean heavy) {
        if (!f.canAttack(heavy) || f.cooldown > 0) return null;
        f.cooldown = rules.cooldown() * (heavy ? 1.7f : 1);
        f.swings++; f.lastHeavy = heavy; return new Swing(f, heavy);
    }
    /** Resolve the nearest struck opponent box along the camera ray, within melee reach. */
    public boolean strike(Swing swing, Vector3f origin, Vector3f direction) {
        if (swing == null || swing.consumed) return false;
        swing.consumed = true;
        Fighter target = swing.attacker == player ? opponent : player;
        if (target.state == State.OUT || !origin.isFinite() || !direction.isFinite() || direction.lengthSquared() < .0001f) return false;
        var ray = new Vector3f(direction).normalize();
        Limb chosen = null; float nearest = 20;
        for (var p : target.parts.values()) if (p.attached) {
            var hit = p.body.bounds().ray(origin, ray, nearest);
            if (hit != null) { nearest *= hit.time(); chosen = p; }
        }
        if (chosen == null) return false;
        Vector3f contact = new Vector3f(origin).fma(nearest, ray);
        if (contact.distance(swing.attacker.position()) > 3.35f) { lastImpact = "Out of reach. Move closer."; return false; }
        // Impulse travels away from the attacker, rather than down the chase-camera ray.
        Vector3f impulse = new Vector3f(contact).sub(swing.attacker.position()); impulse.y = .18f;
        if (impulse.lengthSquared() < .001f) impulse.z = 1;
        impact(target, chosen.part, contact, impulse.normalize().mul(swing.heavy ? rules.heavyImpulse() : rules.jabImpulse()),
                swing.heavy ? rules.heavyDamage() : rules.jabDamage());
        swing.attacker.hits++; return true;
    }
    public void impact(Fighter target, Part part, Vector3f contact, Vector3f impulse, float damage) {
        if (!contact.isFinite() || !impulse.isFinite() || !Float.isFinite(damage) || damage < 0)
            throw new IllegalArgumentException("Invalid impact");
        var limb = target.part(part);
        if (!limb.attached || target.state == State.OUT) return;
        limb.health = Math.max(0, limb.health - damage);
        boolean strong = impulse.length() >= rules.knockdownImpulse();
        if (strong && target.state != State.DOWN) target.knockdowns++;
        if (target.state != State.DOWN) target.state = strong ? State.DOWN : State.STAGGERED;
        target.recovery = Math.max(target.recovery, strong ? rules.recoverySeconds() : .28f);
        for (var p : target.parts.values()) if (p.attached) { p.body.kinematic = false; p.body.wake(); }
        limb.body.impulseAt(impulse, contact);
        if (limb.health == 0) {
            detach(limb);
            if (part == Part.HEAD || part == Part.TORSO) {
                target.state = State.OUT;
                // A core defeat releases remaining attachments; still only six bodies.
                for (var p : target.parts.values()) detach(p);
            }
        }
        lastImpact = (target.human ? "YOU / " : "PARTNER / ") + part.name().replace('_', ' ') + " " + (int)limb.health + "/" + (int)limb.maximum
                + (target.state == State.OUT ? " - OUT. R: new round" : !limb.attached ? " - DETACHED" : strong ? " - KNOCKDOWN" : " - HIT");
    }
    private void detach(Limb limb) {
        if (!limb.attached) return;
        limb.attached = false; limb.body.kinematic = false; limb.body.layer = 8; limb.body.mask = 1;
        if (limb.joint != null) physics.joints.remove(limb.joint);
    }
    public void step(Vector3f movement, float yaw) {
        time += STEP;
        for (var f : List.of(player, opponent)) {
            f.cooldown = Math.max(0, f.cooldown - STEP);
            if (f.state == State.DOWN || f.state == State.STAGGERED) {
                f.recovery -= STEP;
                if (f.recovery <= 0) {
                    f.state = State.READY;
                    pose(f, clamp(f.position().x), clamp(f.position().z));
                }
            }
        }
        if (player.state == State.READY) {
            player.yaw = yaw; move(player, movement);
        }
        if (opponent.state == State.READY && player.state != State.OUT) {
            Vector3f delta = new Vector3f(player.position()).sub(opponent.position()); delta.y = 0;
            float distance = delta.length();
            opponent.yaw = (float)Math.atan2(delta.x, delta.z);
            if (distance > 2.25f) move(opponent, delta.normalize());
            else {
                move(opponent, new Vector3f());
                boolean heavy = opponent.swings % 3 == 2;
                if (!opponent.canAttack(heavy)) heavy = !heavy;
                var swing = swing(opponent, heavy);
                if (swing != null) {
                    opponent.cooldown = Math.max(1.05f, opponent.cooldown);
                    Part[] targets = {Part.LEFT_ARM, Part.RIGHT_LEG, Part.TORSO, Part.RIGHT_ARM, Part.LEFT_LEG, Part.HEAD};
                    var aim = player.part(targets[(opponent.swings - 1) % targets.length]).body.position;
                    var start = new Vector3f(opponent.position()).add(0, .15f, 0);
                    strike(swing, start, new Vector3f(aim).sub(start));
                }
            }
        }
        physics.step(STEP);
    }
    private float clamp(float value) { return Math.max(-10.7f, Math.min(10.7f, value)); }
    private void move(Fighter f, Vector3f direction) {
        Vector3f d = new Vector3f(direction.x, 0, direction.z);
        if (d.lengthSquared() > 1) d.normalize();
        d.mul(rules.speed() * f.movementFactor() * STEP);
        float x = clamp(f.position().x + d.x), z = clamp(f.position().z + d.z);
        Fighter other = f == player ? opponent : player;
        if (other != null && other.state == State.READY) {
            Vector3f gap = new Vector3f(x - other.position().x, 0, z - other.position().z);
            if (gap.lengthSquared() < 1.65f * 1.65f) {
                if (gap.lengthSquared() < .001f) gap.set(0, 0, f.human ? 1 : -1);
                gap.normalize().mul(1.65f); x = other.position().x + gap.x; z = other.position().z + gap.z;
            }
        }
        pose(f, clamp(x), clamp(z));
    }
}
