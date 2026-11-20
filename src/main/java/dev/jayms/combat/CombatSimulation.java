package dev.jayms.combat;

import java.util.*;
import dev.jayms.combat.KitProfile.Stack;
import static dev.jayms.combat.KitProfile.*;
import static dev.jayms.combat.CombatEvent.*;

/**
 * Single-thread confined, opt-in authoritative combat service. No wall clock, random source,
 * native graphics, Player, save format or networking dependency. See package-info for contract.
 */
public final class CombatSimulation {
    public static final double HALF_WIDTH = .3, HEIGHT = 1.8, EYE_HEIGHT = 1.62;
    private static final int MAX_EVENTS = 8192;
    public enum Submission { QUEUED, REPLAY, RATE_LIMITED, DEAD, UNKNOWN }
    public record Pose(Vec3 feet, Vec3 velocity, boolean grounded) {
        public Pose { Objects.requireNonNull(feet); Objects.requireNonNull(velocity); }
    }
    public record EffectView(Effect effect, long expiresAt, String source) {}
    public record Snapshot(String id, Pose pose, double health, boolean alive, double hunger,
                           double saturation, int selectedSlot, List<Stack> inventory,
                           List<Armor> armor, List<EffectView> effects, boolean sprinting,
                           boolean guarding, boolean using, double movementMultiplier,
                           long pearlReadyAt, long lastSequence, int life) {}
    private static final class Status {
        final long expiresAt; long nextPulse; final String source;
        Status(long expiry, long next, String source) { expiresAt = expiry; nextPulse = next; this.source = source; }
    }
    private static final class Actor {
        final String id;
        Pose pose; double health, hunger = 20, saturation = 5, exhaustion;
        final ArrayList<Stack> slots = new ArrayList<>();
        final EnumMap<ArmorSlot, Armor> armor = new EnumMap<>(ArmorSlot.class);
        final EnumMap<Effect, Status> effects = new EnumMap<>(Effect.class);
        final ArrayDeque<CombatIntent.Action> inbox = new ArrayDeque<>();
        int selected, received, life = 1, useSlot = -1;
        long sequence, useStart, pearlReadyAt, hurtUntil, lastAttackAt, regenAt;
        double strongest;
        String lastAttacker;
        boolean sprinting, sprintArmed, stopped = true, guarding;
        Actor(String id) { this.id = id; }
        boolean alive() { return health > 0; }
        Item hand() { return slots.get(selected).item(); }
    }
    private static final class Projectile {
        final long id; final Item item; final String source; final int life;
        final long expiresAt; final double damage;
        Vec3 position, velocity;
        Projectile(long id, Item item, Actor a, long expiry, Vec3 direction, double speed, double damage) {
            this.id = id; this.item = item; source = a.id; life = a.life; expiresAt = expiry;
            position = eye(a); velocity = direction.scale(speed); this.damage = damage;
        }
    }
    private final CombatRules rules;
    private final CombatWorld world;
    private final LinkedHashMap<String, Actor> actors = new LinkedHashMap<>();
    private final ArrayList<Projectile> projectiles = new ArrayList<>();
    private final ArrayDeque<CombatEvent> events = new ArrayDeque<>();
    private long tick, nextEvent, nextProjectile, droppedEvents;

    public CombatSimulation(CombatRules rules, CombatWorld world) {
        this.rules = Objects.requireNonNull(rules); this.world = Objects.requireNonNull(world);
    }
    public CombatRules rules() { return rules; }
    public long tick() { return tick; }
    public int projectileCount() { return projectiles.size(); }
    public long droppedEvents() { return droppedEvents; }
    public List<CombatEvent> drainEvents() { var copy = List.copyOf(events); events.clear(); return copy; }
    public void addParticipant(String id, Pose pose, KitProfile kit) {
        if (id == null || id.isBlank() || id.length() > 64 || actors.containsKey(id)
                || actors.size() >= rules.limits().maxParticipants()) throw new IllegalArgumentException("Participant");
        Objects.requireNonNull(pose); validateKit(kit);
        Actor a = new Actor(id); reset(a, pose, kit); actors.put(id, a);
    }
    private void validateKit(KitProfile kit) {
        Objects.requireNonNull(kit);
        if (!rules.harmfulEffects() && kit.slots().stream().anyMatch(s -> harmful(s.item())))
            throw new IllegalArgumentException("Harmful supplies require a Debuff/team profile");
    }
    /** Trusted server movement update, never a direct client position packet. */
    public void setPose(String id, Pose pose) { Actor a = actor(id); if (a.alive()) a.pose = Objects.requireNonNull(pose); }
    /** Trusted movement integration reports distance exhaustion; caller cannot supply negative credit. */
    public void addExhaustion(String id, double amount) {
        if (!Double.isFinite(amount) || amount < 0 || amount > 100) throw new IllegalArgumentException("Exhaustion");
        Actor a = actor(id); if (a.alive()) a.exhaustion = Math.min(100, a.exhaustion + amount);
    }
    /** Recovery is explicit and only for dead participants. Replay high-water marks survive. */
    public void recover(String id, Pose pose, KitProfile kit) {
        Actor a = actor(id); if (a.alive()) throw new IllegalStateException("Still alive");
        Objects.requireNonNull(pose); validateKit(kit);
        projectiles.removeIf(p -> p.source.equals(id)); a.life++;
        reset(a, pose, kit); emit(Type.RECOVER, a, id, pose.feet(), 0, "new-life");
    }
    private void reset(Actor a, Pose pose, KitProfile kit) {
        a.pose = pose; a.health = rules.damage().health(); a.hunger = 20; a.saturation = 5; a.exhaustion = 0;
        a.slots.clear(); a.slots.addAll(kit.slots()); a.armor.clear();
        kit.armor().forEach(v -> a.armor.put(v.slot(), v)); a.effects.clear(); a.inbox.clear();
        a.selected = 0; a.useSlot = -1; a.received = 0; a.sprinting = false; a.sprintArmed = false;
        a.stopped = true; a.guarding = false; a.hurtUntil = 0; a.strongest = 0;
        a.lastAttacker = null; a.pearlReadyAt = tick; a.regenAt = tick + rules.supplies().regenerationTicks();
    }
    public Snapshot snapshot(String id) {
        Actor a = actor(id);
        return new Snapshot(id, a.pose, a.health, a.alive(), a.hunger, a.saturation, a.selected,
                List.copyOf(a.slots), List.copyOf(a.armor.values()),
                a.effects.entrySet().stream().map(e -> new EffectView(e.getKey(), e.getValue().expiresAt, e.getValue().source)).toList(),
                a.sprinting, a.guarding, a.useSlot >= 0,
                (a.effects.containsKey(Effect.SPEED) ? 1.2 : 1) * (a.effects.containsKey(Effect.SLOWNESS) ? .85 : 1),
                a.pearlReadyAt, a.sequence, a.life);
    }
    public Submission submit(String id, CombatIntent intent) {
        Objects.requireNonNull(intent); Actor a = actors.get(id); if (a == null) return Submission.UNKNOWN;
        if (intent.sequence() <= a.sequence) return Submission.REPLAY;
        a.sequence = intent.sequence(); // rejected new attempts cannot be replayed later
        if (!a.alive()) return Submission.DEAD;
        if (a.received >= rules.limits().maxIntentsPerTick()) return Submission.RATE_LIMITED;
        a.received++;
        a.inbox.add(intent.action()); return Submission.QUEUED;
    }
    /** Whole fixed ticks only. Invalid batches do no work; caller retains elapsed-time debt. */
    public void step(int ticks) {
        if (ticks < 0 || ticks > rules.limits().maxStepTicks() || tick > Long.MAX_VALUE - 1000001L - ticks)
            throw new IllegalArgumentException("Step bound");
        for (int i = 0; i < ticks; i++) stepOne();
    }
    private void stepOne() {
        tick++;
        // Registration order is deterministic; server replay must preserve it.
        for (Actor a : actors.values()) {
            expire(a);
            while (a.alive() && !a.inbox.isEmpty()) act(a, a.inbox.remove());
            a.inbox.clear(); a.received = 0;
        }
        for (Actor a : actors.values()) if (a.alive()) move(a);
        for (Iterator<Projectile> it = projectiles.iterator(); it.hasNext();) {
            Projectile p = it.next();
            if (tick >= p.expiresAt) {
                emit(Type.PROJECTILE_EXPIRE, actors.get(p.source), null, p.position, p.id, p.item.name()); it.remove();
            } else if (fly(p)) it.remove();
        }
        for (Actor a : actors.values()) if (a.alive()) sustain(a);
    }
    private void act(Actor a, CombatIntent.Action action) {
        if (action instanceof CombatIntent.Select s) {
            if (s.slot() < a.slots.size() && s.slot() != a.selected) { cancel(a); a.guarding = false; a.selected = s.slot(); }
        } else if (action instanceof CombatIntent.Sprint s) {
            if (!s.active()) { a.sprinting = false; a.stopped = true; a.sprintArmed = false; }
            else if (!a.sprinting && !a.guarding && a.useSlot < 0 && a.hunger > 6) {
                a.sprinting = true; a.sprintArmed = a.stopped; a.stopped = false;
            }
        } else if (action instanceof CombatIntent.Guard g) {
            cancel(a); a.guarding = g.active() && a.hand() == Item.SWORD;
            if (a.guarding) stopSprint(a);
        } else if (action instanceof CombatIntent.Swing s) {
            cancel(a); a.guarding = false; swing(a, s.direction());
        } else if (action instanceof CombatIntent.StartUse u) startUse(a, u.direction());
        else if (action instanceof CombatIntent.ReleaseUse u) release(a, u.direction());
        else if (action instanceof CombatIntent.CancelUse) { cancel(a); a.guarding = false; }
    }
    private void stopSprint(Actor a) { a.sprinting = false; a.sprintArmed = false; }
    private void swing(Actor a, Vec3 direction) {
        emit(Type.SWING, a, null, eye(a), 0, a.hand().name());
        if (a.hand() != Item.SWORD) return;
        Vec3 from = eye(a), to = from.add(direction.scale(rules.damage().reach()));
        double solid = solidFraction(from, to, 0), nearest = solid;
        Actor victim = null;
        for (Actor b : actors.values()) if (b != a && b.alive()) {
            double f = bodyFraction(from, to, b, 0);
            if (f < nearest) { nearest = f; victim = b; }
        }
        if (victim == null) return;
        boolean critical = !a.pose.grounded() && a.pose.velocity().y() < 0 && !a.sprinting;
        double raw = rules.damage().melee() * (critical ? rules.damage().criticalMultiplier() : 1);
        boolean sprintHit = a.sprinting && a.sprintArmed && a.pose.grounded();
        if (damage(victim, a, raw, DamageKind.MELEE, true)) {
            if (victim.alive()) knockback(a, victim, direction, sprintHit);
            if (sprintHit) { stopSprint(a); a.stopped = false; }
            addExhaustion(a.id, .3);
        }
    }
    private void knockback(Actor source, Actor victim, Vec3 direction, boolean sprint) {
        Vec3 horizontal = new Vec3(direction.x(), 0, direction.z());
        if (horizontal.length() < 1e-9) horizontal = new Vec3(1, 0, 0);
        double strength = rules.physics().horizontalKnockback() + (sprint ? rules.physics().sprintBonus() : 0);
        Vec3 old = victim.pose.velocity();
        Vec3 v = horizontal.unit().scale(strength).add(new Vec3(old.x() * .5,
                Math.max(old.y(), rules.physics().verticalKnockback()), old.z() * .5));
        victim.pose = new Pose(victim.pose.feet(), v, false);
        emit(Type.KNOCKBACK, source, victim.id, victim.pose.feet(), strength, sprint ? "sprint" : "normal");
    }
    private void move(Actor a) {
        Vec3 v = a.pose.velocity();
        if (!a.pose.grounded()) v = v.add(new Vec3(0, -rules.physics().gravity(), 0));
        CombatWorld.Motion m = Objects.requireNonNull(world.move(a.pose.feet(), v, HALF_WIDTH, HEIGHT));
        double friction = m.grounded() ? rules.physics().groundFriction() : rules.physics().airFriction();
        a.pose = new Pose(m.feet(), new Vec3(m.velocity().x() * friction,
                m.velocity().y() * rules.physics().verticalDrag(), m.velocity().z() * friction), m.grounded());
    }
    private void startUse(Actor a, Vec3 direction) {
        if (a.useSlot >= 0) return; // repeated press cannot restart draw/drink timer
        Item item = a.hand();
        if (item == Item.PEARL) {
            if (tick < a.pearlReadyAt || a.health <= rules.supplies().pearlCost()) {
                emit(Type.PEARL_REJECT, a, null, a.pose.feet(), 0, "cooldown-or-health"); return;
            }
            if (!launch(a, item, direction, rules.physics().pearlSpeed(), 0)) return;
            consume(a, a.selected); a.pearlReadyAt = tick + rules.supplies().pearlCooldown();
            damage(a, null, rules.supplies().pearlCost(), DamageKind.PEARL, false);
            cancel(a); a.guarding = false; stopSprint(a);
        } else if (item == Item.HEAL_SPLASH || harmful(item)) {
            if (harmful(item) && !rules.harmfulEffects()) return;
            if (!launch(a, item, direction, rules.physics().potionSpeed(), 0)) return;
            consume(a, a.selected); a.guarding = false; stopSprint(a);
        } else if (item == Item.BOW || item == Item.SPEED_DRINK || item == Item.FIRE_RESIST_DRINK
                || (item == Item.FOOD && a.hunger < 20)) {
            a.guarding = false; stopSprint(a); a.useSlot = a.selected; a.useStart = tick;
            emit(Type.USE_START, a, null, eye(a), 0, item.name());
        }
    }
    private boolean launch(Actor a, Item item, Vec3 direction, double speed, double damage) {
        if (projectiles.size() >= rules.limits().maxProjectiles()) return false;
        Projectile p = new Projectile(++nextProjectile, item, a,
                tick + rules.limits().projectileLifeTicks(), direction, speed, damage);
        projectiles.add(p); emit(Type.PROJECTILE_LAUNCH, a, null, p.position, p.id, item.name()); return true;
    }
    private void release(Actor a, Vec3 direction) {
        if (a.useSlot < 0) return;
        if (a.hand() == Item.BOW) {
            int arrow = firstSlot(a, Item.ARROW);
            double draw = Math.min(1, (double)(tick - a.useStart) / rules.damage().bowDrawTicks());
            if (draw >= .1 && arrow >= 0 && launch(a, Item.ARROW, direction,
                    rules.physics().arrowSpeed() * draw, rules.damage().bowMax() * draw * draw)) consume(a, arrow);
        }
        cancel(a);
    }
    private static int firstSlot(Actor a, Item item) {
        for (int i = 0; i < a.slots.size(); i++) if (a.slots.get(i).item() == item) return i;
        return -1;
    }
    private void cancel(Actor a) {
        if (a.useSlot >= 0) { a.useSlot = -1; emit(Type.USE_CANCEL, a, null, a.pose.feet(), 0, "cancel"); }
    }
    private void consume(Actor a, int slot) {
        Stack s = a.slots.get(slot);
        a.slots.set(slot, s.count() == 1 ? Stack.EMPTY : new Stack(s.item(), s.count() - 1));
        emit(Type.CONSUME, a, null, a.pose.feet(), slot, s.item().name());
    }
    private boolean fly(Projectile p) {
        Actor source = actors.get(p.source);
        if (source.life != p.life) return true;
        Vec3 end = p.position.add(p.velocity);
        double nearest = solidFraction(p.position, end, .1); Actor target = null;
        for (Actor a : actors.values()) if (a.alive() && !a.id.equals(p.source)) {
            double f = bodyFraction(p.position, end, a, .1);
            if (f < nearest) { nearest = f; target = a; }
        }
        if (nearest <= 1) {
            Vec3 impact = p.position.add(p.velocity.scale(nearest));
            emit(Type.PROJECTILE_IMPACT, source, target == null ? null : target.id, impact, p.id, p.item.name());
            if (p.item == Item.ARROW && target != null) {
                if (damage(target, source, p.damage, DamageKind.ARROW, true) && target.alive()) knockback(source, target, p.velocity, false);
            } else if (p.item == Item.PEARL) {
                if (source.alive()) pearlLand(source, impact);
            } else if (p.item != Item.ARROW) splash(source, p.item, impact);
            return true;
        }
        p.position = end; p.velocity = p.velocity.add(new Vec3(0, -rules.physics().projectileGravity(), 0)); return false;
    }
    private void splash(Actor source, Item item, Vec3 impact) {
        double radius = rules.supplies().splashRadius();
        for (Actor a : actors.values()) if (a.alive()) {
            Vec3 center = a.pose.feet().add(new Vec3(0, HEIGHT / 2, 0));
            double distance = center.distance(impact), weight = Math.max(0, 1 - distance / radius);
            if (weight <= 0 || solidFraction(impact, center, 0) < 1) continue;
            if (item == Item.HEAL_SPLASH) heal(a, source, rules.supplies().splashHeal() * weight, "splash");
            else if (rules.harmfulEffects()) effect(a, source, item == Item.POISON_SPLASH ? Effect.POISON : Effect.SLOWNESS,
                    Math.max(1, (int)Math.round(rules.supplies().effectTicks() * weight)));
        }
    }
    private void pearlLand(Actor a, Vec3 impact) {
        // Stable bounded search: impact floor, then adjacent vertical levels and cardinal neighbors.
        int[][] offsets = {{0,0}, {1,0}, {-1,0}, {0,1}, {0,-1}};
        int[] levels = {0, 1, -1, 2, -2};
        for (int y : levels) for (int[] xz : offsets) {
            Vec3 candidate = new Vec3(impact.x() + xz[0], Math.floor(impact.y()) + y, impact.z() + xz[1]);
            if (world.safeLanding(candidate, HALF_WIDTH, HEIGHT)) {
                cancel(a); a.pose = new Pose(candidate, Vec3.ZERO, true);
                emit(Type.TELEPORT, a, a.id, candidate, 0, "pearl"); return;
            }
        }
        emit(Type.PEARL_REJECT, a, null, impact, 0, "unsafe-destination");
    }
    private void expire(Actor a) {
        for (Iterator<Map.Entry<Effect, Status>> it = a.effects.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            if (tick >= e.getValue().expiresAt) {
                emit(Type.EFFECT_EXPIRE, a, a.id, a.pose.feet(), 0, e.getKey().name()); it.remove();
            }
        }
    }
    private void effect(Actor a, Actor source, Effect effect, int duration) {
        if ((effect == Effect.POISON || effect == Effect.SLOWNESS) && !rules.harmfulEffects()) return;
        Status old = a.effects.get(effect);
        // Refresh extends duration, never stacks strength or postpones an existing poison pulse.
        long expiry = Math.max(tick + duration, old == null ? 0 : old.expiresAt);
        a.effects.put(effect, new Status(expiry,
                old == null ? tick + rules.supplies().poisonInterval() : old.nextPulse, source.id));
        emit(Type.EFFECT_START, source, a.id, a.pose.feet(), duration, effect.name());
    }
    private void sustain(Actor a) {
        if (a.useSlot >= 0 && a.hand() != Item.BOW && tick - a.useStart >= rules.supplies().useTicks()) {
            Item item = a.hand(); int slot = a.useSlot; a.useSlot = -1; consume(a, slot);
            if (item == Item.FOOD) {
                a.hunger = Math.min(20, a.hunger + rules.supplies().foodGain());
                a.saturation = Math.min(a.hunger, a.saturation + rules.supplies().saturationGain());
            } else effect(a, a, item == Item.SPEED_DRINK ? Effect.SPEED : Effect.FIRE_RESISTANCE, rules.supplies().effectTicks());
        }
        Status poison = a.effects.get(Effect.POISON);
        if (poison != null && tick >= poison.nextPulse) {
            poison.nextPulse += rules.supplies().poisonInterval();
            damage(a, actors.get(poison.source), Math.min(rules.supplies().poisonDamage(), Math.max(0, a.health - 1)), DamageKind.POISON, false);
        }
        while (a.exhaustion >= rules.supplies().exhaustionPerFood()) {
            a.exhaustion -= rules.supplies().exhaustionPerFood();
            if (a.saturation > 0) a.saturation = Math.max(0, a.saturation - 1);
            else a.hunger = Math.max(0, a.hunger - 1);
        }
        if (a.hunger <= 6) stopSprint(a);
        if (tick >= a.regenAt) {
            a.regenAt = tick + rules.supplies().regenerationTicks();
            if (a.hunger >= 18 && a.health < rules.damage().health()) {
                heal(a, a, 1, "food-regeneration"); a.exhaustion = Math.min(100, a.exhaustion + 6);
            }
        }
    }
    private void heal(Actor a, Actor source, double amount, String detail) {
        double actual = Math.min(amount, rules.damage().health() - a.health);
        if (actual > 0) { a.health += actual; emit(Type.HEAL, source, a.id, a.pose.feet(), actual, detail); }
    }
    /** Trusted world damage only; combat damage is generated from server-resolved intents. */
    public void worldDamage(String id, double amount, DamageKind kind) {
        if (kind != DamageKind.FIRE && kind != DamageKind.ENVIRONMENT) throw new IllegalArgumentException("World damage kind");
        if (!Double.isFinite(amount) || amount <= 0 || amount > 1e6) throw new IllegalArgumentException("Damage");
        Actor a = actor(id); if (a.alive()) damage(a, null, amount, kind, false);
    }
    private boolean damage(Actor victim, Actor source, double amount, DamageKind kind, boolean windowed) {
        if (!victim.alive() || amount <= 0 || (kind == DamageKind.FIRE && victim.effects.containsKey(Effect.FIRE_RESISTANCE))) return false;
        double raw = amount;
        if (windowed) {
            if (tick < victim.hurtUntil) {
                amount = Math.max(0, raw - victim.strongest);
                if (amount <= 0) return false;
                victim.strongest = raw;
            } else { victim.strongest = raw; victim.hurtUntil = tick + rules.damage().hurtTicks(); }
            if (victim.guarding && victim.hand() == Item.SWORD) amount *= rules.damage().guardMultiplier();
            double protection = Math.min(.8, victim.armor.values().stream().filter(v -> v.durability() > 0).mapToDouble(Armor::protection).sum());
            amount *= 1 - protection;
        }
        if (kind == DamageKind.POISON) amount = Math.min(amount, Math.max(0, victim.health - 1));
        if (amount <= 0) return false;
        if (windowed) wearArmor(victim, raw);
        double actual = Math.min(victim.health, amount); victim.health -= actual; cancel(victim);
        if (source != null && source != victim) { victim.lastAttacker = source.id; victim.lastAttackAt = tick; }
        emit(Type.DAMAGE, source, victim.id, victim.pose.feet(), actual, kind.name());
        if (windowed) emit(Type.HIT, source, victim.id, victim.pose.feet(), actual, kind.name());
        if (!victim.alive()) die(victim, kind);
        return true;
    }
    private void wearArmor(Actor a, double raw) {
        for (var e : a.armor.entrySet()) {
            Armor old = e.getValue(); if (old.durability() == 0) continue;
            int durability = Math.max(0, old.durability() - Math.max(1, (int)Math.ceil(raw / 4)));
            e.setValue(new Armor(old.slot(), old.protection(), durability));
            if (durability == 0) emit(Type.ARMOR_BREAK, a, a.id, a.pose.feet(), 0, old.slot().name());
        }
    }
    private void die(Actor a, DamageKind kind) {
        cancel(a); stopSprint(a); a.guarding = false; a.inbox.clear();
        a.effects.clear(); a.pose = new Pose(a.pose.feet(), Vec3.ZERO, a.pose.grounded());
        String killer = a.lastAttacker != null && tick - a.lastAttackAt <= rules.damage().attributionTicks() ? a.lastAttacker : null;
        emit(Type.DEATH, killer == null ? null : actors.get(killer), a.id, a.pose.feet(), 0, kind.name());
    }
    private double solidFraction(Vec3 from, Vec3 to, double radius) {
        OptionalDouble f = world.firstSolid(from, to, radius);
        if (f.isEmpty()) return Double.POSITIVE_INFINITY;
        double v = f.getAsDouble();
        if (!Double.isFinite(v) || v < 0 || v > 1) throw new IllegalStateException("Invalid collision fraction");
        return v;
    }
    /** Slab intersection with an expanded axis-aligned participant body; ties use registration order. */
    private static double bodyFraction(Vec3 from, Vec3 to, Actor a, double radius) {
        Vec3 feet = a.pose.feet();
        double[] lo = {feet.x() - HALF_WIDTH - radius, feet.y() - radius, feet.z() - HALF_WIDTH - radius};
        double[] hi = {feet.x() + HALF_WIDTH + radius, feet.y() + HEIGHT + radius, feet.z() + HALF_WIDTH + radius};
        double[] start = {from.x(), from.y(), from.z()}, end = {to.x(), to.y(), to.z()};
        double min = 0, max = 1;
        for (int i = 0; i < 3; i++) {
            double d = end[i] - start[i];
            if (Math.abs(d) < 1e-12) { if (start[i] < lo[i] || start[i] > hi[i]) return Double.POSITIVE_INFINITY; }
            else {
                double t1 = (lo[i] - start[i]) / d, t2 = (hi[i] - start[i]) / d;
                min = Math.max(min, Math.min(t1, t2)); max = Math.min(max, Math.max(t1, t2));
                if (min > max) return Double.POSITIVE_INFINITY;
            }
        }
        return min;
    }
    private Actor actor(String id) {
        Actor a = actors.get(id); if (a == null) throw new IllegalArgumentException("Unknown participant"); return a;
    }
    private static boolean harmful(Item item) { return item == Item.POISON_SPLASH || item == Item.SLOWNESS_SPLASH; }
    private static Vec3 eye(Actor a) { return a.pose.feet().add(new Vec3(0, EYE_HEIGHT, 0)); }
    private void emit(Type type, Actor actor, String target, Vec3 position, double amount, String detail) {
        if (events.size() == MAX_EVENTS) { events.remove(); droppedEvents++; }
        events.add(new CombatEvent(++nextEvent, tick, type, actor == null ? null : actor.id, target, position, amount, detail));
    }
}
