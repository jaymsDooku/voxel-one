package dev.jayms.combat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

import static dev.jayms.combat.CombatGeometry.*;

/** Single-threaded authoritative simulation. Call tick every 50 ms on the owning match thread. */
public final class CombatEngine {
    public static final int TICKS_PER_SECOND = 20;
    public static final int INVENTORY_SIZE = 36, HOTBAR_SIZE = 9;
    public static final double MAX_HEALTH = 20;
    private static final int MAX_ACTORS = 32, MAX_EVENTS = 512, INPUT_TIMEOUT = 12;
    private static final int MAX_REWIND_TICKS = 6, MAX_CLICKS_PER_ACTOR_PER_TICK = 16;
    private static final int ARMOR_DURABILITY = 200;
    // The arena uses diamond piece proportions for its custom full-set reductions, in NMS inventory order.
    private static final int[] ARMOR_POINTS = {3, 6, 8, 3}; // boots, leggings, chestplate, helmet
    private static final int ITEM_USE_TICKS = 32, PROJECTILE_LIFETIME = 100, MAX_EFFECT_TICKS = 24_000;
    private static final double HIT_MOTION_RETENTION = .5, SPRINT_HIT_RETENTION = .6, SPRINT_HIT_LIFT = .10;
    private static final double USE_MOVEMENT_SCALE = .2;

    public interface Arena {
        boolean solid(int x, int y, int z);
        boolean contains(Vec3 feet);
        default boolean throwableSolid(int x, int y, int z) { return solid(x, y, z); }
        default boolean containsThrowable(Vec3 position) { return contains(position); }
        default boolean throwableInWater(Vec3 position) { return false; }
        default boolean canHarm(int attackerId, int targetId) { return true; }
        default boolean canEnter(int playerId, Vec3 feet) { return true; }
    }

    public record Vec3(double x, double y, double z) {
        public Vec3 add(double dx, double dy, double dz) { return new Vec3(x + dx, y + dy, z + dz); }
        public Vec3 add(Vec3 other) { return add(other.x, other.y, other.z); }
        public Vec3 minus(Vec3 other) { return add(-other.x, -other.y, -other.z); }
        public Vec3 scale(double factor) { return new Vec3(x * factor, y * factor, z * factor); }
        public double length() { return Math.sqrt(x * x + y * y + z * z); }
        double component(int axis) {
            return switch (axis) {
                case 0 -> x;
                case 1 -> y;
                case 2 -> z;
                default -> throw new IllegalArgumentException("Invalid axis");
            };
        }
        boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }

    public enum Item { EMPTY, SWORD, HEALING, SPEED, FIRE_RESISTANCE, PEARL, FOOD, POISON, SLOWNESS, BOW, ARROW }
    public enum Effect { SPEED, FIRE_RESISTANCE, POISON, SLOWNESS, STRENGTH, RESISTANCE, MARK, REGENERATION }
    public enum EventType { SWING, HIT, THROW, SPLASH, DRINK, TELEPORT, DEATH, REJECTED_PEARL, ARMOR_BREAK, DEBUFF }

    public record Stack(Item item, int count) {
        public Stack {
            if (item == null || count < 0 || count > 64 || (item == Item.EMPTY) != (count == 0)
                    || count > 1 && item != Item.FOOD && item != Item.PEARL && item != Item.ARROW) {
                throw new IllegalArgumentException("Invalid combat stack");
            }
        }
    }

    /** The client identifies the displayed target and sample times. The server owns every position. */
    public record AttackView(int targetId, PoseView attacker, PoseView target) {
        public AttackView {
            if (targetId < 0 || attacker == null || target == null || Math.abs(attacker.tick() - target.tick()) > 3)
                throw new IllegalArgumentException("Invalid attack view");
        }

        public AttackView(int targetId, double attackerTick, double targetTick) {
            this(targetId, new PoseView((long) Math.floor(attackerTick), (long) Math.ceil(attackerTick), attackerTick),
                    new PoseView((long) Math.floor(targetTick), (long) Math.ceil(targetTick), targetTick));
        }
    }

    public record PoseView(long first, long last, double tick) {
        public PoseView {
            if (first < 0 || last < first || last - first > 3 || !Double.isFinite(tick) || tick < first || tick > last)
                throw new IllegalArgumentException("Invalid displayed pose");
        }
    }

    /** Attack is a discrete click and use is held. Older inputs omit the displayed target. */
    public record Intent(long sequence, float yaw, float pitch, float forward, float strafe,
                         boolean jump, boolean sprint, boolean attack, boolean use, int slot, AttackView attackView) {
        public Intent(long sequence, float yaw, float pitch, float forward, float strafe,
                      boolean jump, boolean sprint, boolean attack, boolean use, int slot) {
            this(sequence, yaw, pitch, forward, strafe, jump, sprint, attack, use, slot, null);
        }
    }
    public record Stats(int hits, int longestCombo, int potsUsed, int potsRemaining) {}
    public record ProjectileState(long id, int ownerId, Item item, Vec3 position) {}
    public record Event(EventType type, int actorId, int targetId, Item item, Vec3 position, double amount) {}
    /** Velocity describes the last step. A hit impulse is the carried motion for the next step. */
    public record PlayerState(int id, Vec3 feet, Vec3 velocity, float yaw, float pitch, boolean grounded,
                              boolean sprinting, double health, int food, int slot, List<Stack> inventory,
                              List<Integer> durability, Map<Effect, Integer> effects, int pearlCooldownTicks,
                              Stats stats, int killerId, long sequence, long hurtTick, long motionEpoch,
                              long lastHorizontalWallTick, long impulseTick, Vec3 hitImpulse) {}

    private static final Stack EMPTY = new Stack(Item.EMPTY, 0);
    private static final Vec3 ZERO = new Vec3(0, 0, 0);
    private final CombatRules rules;
    private final Arena arena;
    private final RandomGenerator combatRandom;
    private final Map<Integer, Actor> actors = new TreeMap<>();
    private final ArrayDeque<Click> pendingMelee = new ArrayDeque<>();
    private final List<Projectile> shots = new ArrayList<>();
    private final ArrayDeque<Event> events = new ArrayDeque<>();
    private long currentTick, arenaTick, nextProjectile;

    public CombatEngine(CombatRules rules, Arena arena) {
        this(rules, arena, RandomGenerator.getDefault());
    }

    CombatEngine(CombatRules rules, Arena arena, RandomGenerator combatRandom) {
        this.rules = Objects.requireNonNull(rules);
        this.arena = Objects.requireNonNull(arena);
        this.combatRandom = Objects.requireNonNull(combatRandom);
    }

    public boolean join(int playerId, Vec3 feet, float yaw) {
        if (playerId <= 0 || actors.containsKey(playerId) || actors.size() >= MAX_ACTORS
                || !spawnValid(playerId, feet, yaw)) {
            return false;
        }
        Actor actor = new Actor(playerId, feet, yaw, rules.openingKit(), rules.armorReduction());
        settleAtSpawn(actor);
        actors.put(playerId, actor);
        return true;
    }

    public void leave(int playerId) {
        Actor actor = actors.remove(playerId);
        if (actor != null) clearClicks(actor);
        shots.removeIf(shot -> shot.ownerId == playerId);
    }

    public boolean reset(int playerId, Vec3 feet, float yaw) {
        Actor old = actors.get(playerId);
        if (old == null || !spawnValid(playerId, feet, yaw)) return false;
        Actor replacement = new Actor(playerId, feet, yaw, old.kit, old.startingArmorReduction);
        settleAtSpawn(replacement);
        replacement.sequence = old.sequence;
        replacement.motionEpoch = old.motionEpoch + 1;
        clearClicks(old);
        actors.put(playerId, replacement);
        shots.removeIf(shot -> shot.ownerId == playerId);
        return true;
    }

    private boolean spawnValid(int playerId, Vec3 feet, float yaw) {
        return feet != null && feet.finite() && Float.isFinite(yaw) && free(arena, feet)
                && arena.canEnter(playerId, feet);
    }

    public boolean submit(int playerId, Intent intent) {
        Actor actor = actors.get(playerId);
        if (actor == null || !actor.alive() || intent == null || intent.sequence() <= actor.sequence
                || !Float.isFinite(intent.yaw()) || !Float.isFinite(intent.pitch())
                || !Float.isFinite(intent.forward()) || !Float.isFinite(intent.strafe())
                || Math.abs(intent.forward()) > 1 || Math.abs(intent.strafe()) > 1
                || intent.slot() < 0 || intent.slot() >= HOTBAR_SIZE) return false;
        actor.sequence = intent.sequence();
        actor.lastInputTick = currentTick;
        actor.intent = intent;
        if (!intent.sprint() || intent.forward() <= 0) actor.lastSprintReleaseSequence = intent.sequence();
        if (intent.attack() && actor.queuedClicks < MAX_CLICKS_PER_ACTOR_PER_TICK) {
            // A later packet cannot raise or lower a prior block.
            var blocking = new IdentityHashMap<Actor, Boolean>();
            for (Actor other : actors.values()) {
                if (other != actor && other.alive()) {
                    Intent held = other.intent;
                    blocking.put(other, held != null && currentTick + 1 - other.lastInputTick <= INPUT_TIMEOUT
                            && held.use() && other.inventory.get(held.slot()).item() == Item.SWORD);
                }
            }
            Item selected = actor.inventory.get(intent.slot()).item();
            boolean usingItem = actor.useTicks > 0 && actor.slot == intent.slot() && intent.use();
            pendingMelee.addLast(new Click(actor, intent, selected, usingItem, actor.lastSprintReleaseSequence, blocking));
            actor.queuedClicks++;
        }
        if (intent.use() && !actor.submittedUse) actor.usePressed = true;
        if (!intent.use() && actor.submittedUse) actor.useReleased = true;
        actor.submittedUse = intent.use();
        return true;
    }

    /** Drops pending/held actions when control is detached while the actor remains in the arena. */
    public void clearInput(int playerId) {
        Actor actor = actors.get(playerId);
        if (actor == null) return;
        actor.intent = null;
        clearClicks(actor);
        actor.useHeld = false;
        actor.submittedUse = false;
        actor.usePressed = false;
        actor.useReleased = false;
        actor.cancelUse();
    }

    private void clearClicks(Actor actor) {
        pendingMelee.removeIf(click -> click.attacker() == actor);
        actor.queuedClicks = 0;
    }

    /** Reorders trusted server kit items for future rounds. Call at a match boundary. */
    public boolean swapKit(int playerId, int first, int second) {
        Actor actor = actors.get(playerId);
        if (actor == null || !validSlot(first) || !validSlot(second)) return false;
        Collections.swap(actor.kit, first, second);
        return true;
    }

    /** Replaces the next round's loadout. Match or role configuration supplies the kit. */
    public boolean setKit(int playerId, List<Stack> kit) {
        Actor actor = actors.get(playerId);
        if (actor == null || kit == null || kit.size() != INVENTORY_SIZE
                || kit.stream().anyMatch(stack -> stack == null || isDebuff(stack.item()) && !rules.debuffs())) {
            return false;
        }
        actor.kit = new ArrayList<>(kit);
        return true;
    }

    public List<Stack> kit(int playerId) {
        Actor actor = actors.get(playerId);
        return actor == null ? List.of() : List.copyOf(actor.kit);
    }

    /** Moves a server-owned stack without introducing or duplicating an item. */
    public boolean swapInventory(int playerId, int first, int second) {
        Actor actor = actors.get(playerId);
        if (actor == null || !actor.alive() || !validSlot(first) || !validSlot(second)) return false;
        if (first != second && (first == actor.slot || second == actor.slot)) actor.cancelUse();
        Collections.swap(actor.inventory, first, second);
        return true;
    }

    public boolean quickMoveKit(int playerId, int source) {
        Actor actor = actors.get(playerId);
        return actor != null && quickMove(actor.kit, source);
    }

    public boolean quickMoveInventory(int playerId, int source) {
        Actor actor = actors.get(playerId);
        if (actor == null || !actor.alive()) return false;
        Stack held = actor.inventory.get(actor.slot);
        if (!quickMove(actor.inventory, source)) return false;
        if (!held.equals(actor.inventory.get(actor.slot))) actor.cancelUse();
        return true;
    }

    /** Merge into existing stacks first, then use the first empty slot in the other section. */
    private static boolean quickMove(List<Stack> slots, int source) {
        if (!validSlot(source)) return false;
        Stack stack = slots.get(source);
        if (stack.item() == Item.EMPTY) return false;
        int start = source < HOTBAR_SIZE ? HOTBAR_SIZE : 0;
        int end = source < HOTBAR_SIZE ? INVENTORY_SIZE : HOTBAR_SIZE;
        int empty = -1, remaining = stack.count();
        for (int i = start; i < end && remaining > 0; i++) {
            Stack destination = slots.get(i);
            if (destination.item() == Item.EMPTY && empty < 0) empty = i;
            if (destination.item() == stack.item() && stackable(stack.item())) {
                int moved = Math.min(remaining, 64 - destination.count());
                if (moved > 0) slots.set(i, new Stack(stack.item(), destination.count() + moved));
                remaining -= moved;
            }
        }
        if (remaining > 0 && empty >= 0) {
            slots.set(empty, new Stack(stack.item(), remaining));
            remaining = 0;
        }
        if (remaining == stack.count()) return false;
        slots.set(source, remaining == 0 ? EMPTY : new Stack(stack.item(), remaining));
        return true;
    }

    private static boolean stackable(Item item) {
        return item == Item.FOOD || item == Item.PEARL || item == Item.ARROW;
    }

    /** Grants a whole trusted stack or leaves inventory unchanged if it cannot fit. */
    public boolean grant(int playerId, Stack stack) {
        Actor actor = actors.get(playerId);
        if (actor == null || !actor.alive() || stack == null || stack.item() == Item.EMPTY
                || (isDebuff(stack.item()) && !rules.debuffs())) return false;
        int emptySlot = -1;
        boolean stackable = stackable(stack.item());
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            Stack held = actor.inventory.get(slot);
            if (held.item() == Item.EMPTY && emptySlot == -1) emptySlot = slot;
            if (stackable && held.item() == stack.item() && held.count() + stack.count() <= 64) {
                actor.inventory.set(slot, new Stack(stack.item(), held.count() + stack.count()));
                return true;
            }
        }
        if (emptySlot == -1) return false;
        actor.inventory.set(emptySlot, stack);
        return true;
    }

    private static boolean validSlot(int slot) { return slot >= 0 && slot < INVENTORY_SIZE; }

    public boolean applyEffect(int playerId, Effect effect, int ticks) {
        Actor actor = actors.get(playerId);
        if (actor == null || !actor.alive() || effect == null || ticks < 0 || ticks > MAX_EFFECT_TICKS) return false;
        if (ticks == 0) actor.effects.remove(effect);
        else actor.effects.merge(effect, ticks, Math::max);
        return true;
    }

    public boolean equipArmor(int playerId, List<Integer> durability) {
        Actor actor = actors.get(playerId);
        return actor != null && equipArmor(playerId, actor.startingArmorReduction, durability);
    }

    public boolean equipArmor(int playerId, double reduction, List<Integer> durability) {
        Actor actor = actors.get(playerId);
        if (actor == null || !actor.alive() || !Double.isFinite(reduction) || reduction < 0 || reduction > .9
                || durability == null || durability.size() != 4
                || durability.stream().anyMatch(value -> value == null || value < 0 || value > 1000)) return false;
        actor.armorReduction = reduction;
        for (int i = 0; i < actor.armor.length; i++) actor.armor[i] = durability.get(i);
        return true;
    }

    public boolean damage(int sourceId, int targetId, double amount) {
        Actor target = actors.get(targetId);
        if (target == null || !target.alive() || !Double.isFinite(amount) || amount <= 0 || amount > MAX_HEALTH
                || sourceId != targetId && sourceId != 0 && !arena.canHarm(sourceId, targetId)) return false;
        applyDamage(target, sourceId, amount);
        return true;
    }

    /** Environmental fire is reduced by the player's current fire-resistance effect. */
    public boolean burn(int playerId, double amount) {
        Actor actor = actors.get(playerId);
        return actor != null && !actor.has(Effect.FIRE_RESISTANCE) && damage(0, playerId, amount);
    }

    public PlayerState snapshot(int playerId) {
        Actor actor = actors.get(playerId);
        return actor == null ? null : actor.snapshot();
    }

    public List<PlayerState> snapshots() { return actors.values().stream().map(Actor::snapshot).toList(); }

    public List<ProjectileState> projectiles() {
        return shots.stream().map(shot -> new ProjectileState(shot.id, shot.ownerId, shot.item, shot.position)).toList();
    }

    /** Ends in-flight effects at a match phase boundary without resetting participants. */
    public void clearProjectiles() { shots.clear(); }

    public List<Event> drainEvents() {
        var drained = List.copyOf(events);
        events.clear();
        return drained;
    }

    public long currentTick() { return currentTick; }

    public void tick() { tick(currentTick + 1); }

    public void tick(long matchTick) {
        arenaTick = matchTick;
        currentTick++;
        for (Actor actor : actors.values()) {
            if (actor.alive()) tickEffects(actor);
        }
        // Resolve received swings before either fighter can walk farther through the contact.
        // Both eyes still describe the same step, even when two live clicks trade.
        for (Click click : pendingMelee) {
            Actor actor = click.attacker();
            if (actor.alive()) melee(actor, click);
            actor.queuedClicks--;
        }
        pendingMelee.clear();
        for (Actor actor : actors.values()) {
            if (actor.alive()) {
                move(actor);
                // This launch has already advanced the feet. The local camera previews the next step.
                if (actor.impulseTick == currentTick) actor.hitImpulse = actor.motion;
            }
        }
        for (Actor actor : actors.values()) {
            if (actor.alive()) use(actor);
            actor.usePressed = false;
            actor.useReleased = false;
        }
        for (Iterator<Projectile> iterator = shots.iterator(); iterator.hasNext();) {
            if (advance(iterator.next())) iterator.remove();
        }
        for (Actor actor : actors.values()) {
            if (actor.alive()) {
                actor.history.addLast(new Pose(arenaTick, actor.feet));
                if (actor.history.size() > MAX_REWIND_TICKS + 1) actor.history.removeFirst();
            }
        }
    }

    private void tickEffects(Actor actor) {
        if (actor.hurtTicks > 0) actor.hurtTicks--;
        int poison = actor.effects.getOrDefault(Effect.POISON, 0);
        if (poison > 0 && poison % 25 == 0 && actor.health > 1) applyUnarmoredDamage(actor, 0, 1);
        int regen = actor.effects.getOrDefault(Effect.REGENERATION, 0);
        if (regen > 0 && regen % 50 == 0) heal(actor, 1);
        actor.effects.replaceAll((effect, ticks) -> ticks - 1);
        actor.effects.values().removeIf(ticks -> ticks <= 0);
        if (actor.pearlCooldown > 0) actor.pearlCooldown--;
        if (actor.food >= 18 && actor.health < MAX_HEALTH && ++actor.foodRegeneration >= 80) {
            heal(actor, 1);
            actor.exhaustion += 3;
            actor.foodRegeneration = 0;
        } else if (actor.food < 18 || actor.health >= MAX_HEALTH) {
            actor.foodRegeneration = 0;
        }
        while (actor.exhaustion >= 4) {
            actor.exhaustion -= 4;
            if (actor.saturation >= 1) actor.saturation--;
            else if (actor.food > 0) actor.food--;
        }
        if (actor.food == 0 && currentTick % 80 == 0) applyNonlethalDamage(actor);
    }

    private void applyNonlethalDamage(Actor actor) {
        if (actor.health <= 1) return;
        actor.health = Math.max(1, actor.health - 1);
        actor.hurtTick = currentTick;
    }

    private void move(Actor actor) {
        Intent intent = actor.intent;
        boolean fresh = intent != null && currentTick - actor.lastInputTick <= INPUT_TIMEOUT;
        float forward = fresh ? intent.forward() : 0, strafe = fresh ? intent.strafe() : 0;
        int slot = fresh ? intent.slot() : actor.slot;
        if (slot != actor.slot) {
            actor.cancelUse();
            actor.slot = slot;
        }
        if (fresh) {
            actor.yaw = intent.yaw() % 360;
            actor.pitch = Math.max(-89, Math.min(89, intent.pitch()));
        } else {
            actor.cancelUse();
            actor.submittedUse = false;
        }
        actor.useHeld = fresh && intent.use();
        Item selected = actor.selected();
        boolean using = actor.useHeld && (selected == Item.SWORD || actor.useItem == selected && selected != Item.EMPTY
                || actor.usePressed && startsTimedUse(selected, actor.food));
        actor.sprinting = fresh && intent.sprint() && forward > 0 && actor.food > 6 && !using && actor.sprintReady();
        double speedMultiplier = 1;
        if (actor.has(Effect.SPEED)) speedMultiplier *= 1.20;
        if (actor.has(Effect.SLOWNESS)) speedMultiplier *= .85;
        var input = new CombatMovement.Input(actor.yaw, forward, strafe, fresh && intent.jump(),
                actor.sprinting, speedMultiplier, using ? USE_MOVEMENT_SCALE : 1);
        var state = new CombatMovement.State(actor.feet, actor.motion, actor.grounded, actor.jumpCooldown);
        var step = CombatMovement.step(arena, actor.id, state, input);
        actor.feet = step.state().feet();
        actor.motion = step.state().motion();
        actor.grounded = step.state().grounded();
        actor.jumpCooldown = step.state().jumpCooldown();
        actor.velocity = step.displacement();
        if (step.jumped()) actor.exhaustion += actor.sprinting ? .2 : .05;
        if (actor.sprinting) actor.exhaustion += Math.hypot(actor.velocity.x, actor.velocity.z) * .10;
        actor.hitHorizontalWall = step.hitHorizontalWall();
        if (actor.hitHorizontalWall) {
            actor.sprinting = false;
            actor.lastHorizontalWallTick = arenaTick;
        }
        if (actor.grounded) {
            applyUnarmoredDamage(actor, 0, Math.ceil(actor.fallDistance - 3));
            actor.fallDistance = 0;
        } else if (actor.velocity.y < 0) {
            actor.fallDistance -= actor.velocity.y;
        }
    }

    private void melee(Actor attacker, Click click) {
        Intent input = click.input();
        Item selected = click.selected();
        Item weapon = selected == Item.SWORD ? Item.SWORD : Item.EMPTY;
        if (input.use() && weapon == Item.SWORD || click.usingItem()) return;
        AttackView shown = input.attackView();
        Vec3 origin = attacker.feet.add(0, EYE_HEIGHT, 0);
        emit(EventType.SWING, attacker.id, 0, weapon, origin, 0);
        if (shown != null) {
            if (shown.targetId() == 0) return;
            Vec3 past = historicalFeet(attacker, shown.attacker());
            if (past == null) return;
            origin = past.add(0, EYE_HEIGHT, 0);
        }
        Vec3 aim = direction(input.yaw(), input.pitch()).scale(rules.reach());
        Actor target = null;
        double nearest = firstBlock(arena, origin, aim);
        for (Actor other : actors.values()) {
            if (!other.alive() || !click.blocking().containsKey(other)) continue;
            Vec3 feet = shown == null ? other.feet : historicalFeet(other, shown.target());
            if (feet == null) continue;
            double hit = firstMeleeBody(origin, aim, feet);
            if (hit < nearest) {
                target = other;
                nearest = hit;
            }
        }
        if (target == null || shown != null && target.id != shown.targetId()
                || !withinMeleePacketRange(arena, attacker.feet, target.feet)) return;
        boolean startingUse = input.use() && startsTimedUse(selected, attacker.food);
        boolean sprinting = input.sprint() && input.forward() > 0 && attacker.food > 6
                && !startingUse && !attacker.hitHorizontalWall;
        double damage = weapon == Item.SWORD ? rules.swordDamage() : 1;
        if (attacker.has(Effect.STRENGTH)) damage *= 2.3;
        if (!attacker.grounded && attacker.fallDistance > 0) damage *= 1.5;
        boolean sprintHit = sprinting && (attacker.lastSprintHitSequence < 0 || click.sprintReleaseSequence() > attacker.lastSprintHitSequence);
        Vec3 sprintPush = sprintHit ? direction(input.yaw(), 0).scale(rules.sprintKnockback()).add(0, SPRINT_HIT_LIFT, 0) : ZERO;
        if (strike(attacker, target, damage, rules.knockback(), sprintPush, weapon, click.blocking().get(target)) && sprintHit) {
            attacker.lastSprintHitSequence = input.sequence();
            Vec3 motion = attacker.motion;
            attacker.motion = new Vec3(motion.x * SPRINT_HIT_RETENTION, motion.y, motion.z * SPRINT_HIT_RETENTION);
            attacker.sprinting = false;
            if (attacker.impulseTick == currentTick) attacker.hitImpulse = attacker.motion;
        }
    }

    private Vec3 historicalFeet(Actor actor, PoseView view) {
        if (view.first() < arenaTick - MAX_REWIND_TICKS || view.last() >= arenaTick) return null;
        Vec3 first = null;
        for (Pose pose : actor.history) {
            if (pose.tick() == view.first()) first = pose.feet();
            if (pose.tick() == view.last() && first != null) {
                long span = view.last() - view.first();
                if (span == 0) return first;
                var path = posePath(arena, first, pose.feet(), span);
                return path == null ? null : path.at((view.tick() - view.first()) / span);
            }
        }
        return null;
    }

    private boolean strike(Actor attacker, Actor target, double rawDamage, double basePush, Vec3 sprintPush,
                           Item weapon, boolean blocking) {
        if (!target.alive() || !arena.canHarm(attacker.id, target.id)) return false;
        boolean fullHit = target.hurtTicks == 0;
        double acceptedDamage = acceptRawDamage(target, rawDamage);
        if (acceptedDamage <= 0) return false;

        double armorDamage = blocking ? (acceptedDamage + 1) * .5 : acceptedDamage;
        int armorPoints = 0;
        for (int i = 0; i < target.armor.length; i++) {
            if (target.armor[i] > 0) armorPoints += ARMOR_POINTS[i];
        }
        double mitigation = target.armorReduction * armorPoints / 20;
        double dealt = armorDamage * (1 - mitigation);
        if (target.has(Effect.RESISTANCE)) dealt *= .8;
        if (target.has(Effect.MARK)) dealt *= 1.12;

        if (fullHit) beginHitMotion(target, knockbackDirection(attacker, target), basePush);
        // A stronger hit inside the hurt window can still deliver the sprint bonus.
        if (sprintPush.y > 0) {
            target.motion = target.motion.add(sprintPush);
            target.impulseTick = currentTick;
            target.hitImpulse = target.motion;
        }
        if (currentTick - attacker.lastHit > 26 || attacker.lastComboTarget != target.id) attacker.combo = 0;
        attacker.lastHit = currentTick;
        attacker.lastComboTarget = target.id;
        attacker.longestCombo = Math.max(attacker.longestCombo, ++attacker.combo);
        attacker.hits++;
        target.combo = 0;
        if (weapon != Item.ARROW) attacker.exhaustion += .3;
        target.exhaustion += .3;
        int armorWear = Math.max(1, (int) (armorDamage / 4));
        for (int i = 0; i < target.armor.length; i++) {
            if (target.armor[i] <= 0) continue;
            target.armor[i] = Math.max(0, target.armor[i] - armorWear);
            if (target.armor[i] == 0) emit(EventType.ARMOR_BREAK, target.id, 0, Item.EMPTY, target.feet, i);
        }
        emit(EventType.HIT, attacker.id, target.id, weapon, target.feet.add(0, EYE_HEIGHT * .65, 0), dealt);
        applyDamage(target, attacker.id, dealt, fullHit);
        return true;
    }

    private Vec3 knockbackDirection(Actor attacker, Actor target) {
        double dx = attacker.feet.x - target.feet.x, dz = attacker.feet.z - target.feet.z;
        // EntityLiving.damageEntity samples a new horizontal offset when the centers coincide.
        // Only the separate sprint bonus uses the attacking player's facing.
        while (dx * dx + dz * dz < 1e-4) {
            dx = (combatRandom.nextDouble() - combatRandom.nextDouble()) * .01;
            dz = (combatRandom.nextDouble() - combatRandom.nextDouble()) * .01;
        }
        double distance = Math.hypot(dx, dz);
        return new Vec3(-dx / distance, 0, -dz / distance);
    }

    private void beginHitMotion(Actor target, Vec3 away, double basePush) {
        Vec3 motion = target.motion.scale(HIT_MOTION_RETENTION).add(away.scale(basePush));
        target.motion = new Vec3(motion.x, Math.min(rules.lift(), motion.y + rules.lift()), motion.z);
        target.impulseTick = currentTick;
        target.hitImpulse = target.motion;
    }

    private double acceptRawDamage(Actor target, double amount) {
        if (target.hurtTicks == 0) {
            target.hurtTicks = rules.hurtTicks();
            target.lastDamage = amount;
            return amount;
        }
        // Only a larger raw hit gets through during Paper's protected half of the hurt window.
        double difference = amount - target.lastDamage;
        if (difference > 0) target.lastDamage = amount;
        return difference;
    }

    private void applyUnarmoredDamage(Actor target, int sourceId, double amount) {
        if (amount <= 0) return;
        boolean fullHit = target.hurtTicks == 0;
        double damage = acceptRawDamage(target, amount);
        if (target.has(Effect.RESISTANCE)) damage *= .8;
        applyDamage(target, sourceId, damage, fullHit);
    }

    private void applyDamage(Actor target, int sourceId, double amount) {
        applyDamage(target, sourceId, amount, true);
    }

    private void applyDamage(Actor target, int sourceId, double amount, boolean showHurt) {
        if (!target.alive() || amount <= 0) return;
        double health = Math.max(0, target.health - amount);
        if (health == target.health) return;
        target.health = health;
        if (showHurt) target.hurtTick = currentTick;
        if (sourceId > 0 && sourceId != target.id) {
            target.recentAttacker = sourceId;
            target.lastDamageTick = currentTick;
        }
        if (target.alive()) return;
        target.killerId = sourceId > 0 && sourceId != target.id ? sourceId
                : currentTick - target.lastDamageTick <= 100 ? target.recentAttacker : 0;
        target.cancelUse();
        target.effects.clear();
        emit(EventType.DEATH, target.killerId, target.id, Item.EMPTY, target.feet, amount);
    }

    private void use(Actor actor) {
        Item selected = actor.selected();
        if (actor.useReleased) {
            if (selected == Item.BOW && actor.useItem == Item.BOW && actor.useTicks >= 3 && consumeArrow(actor)) {
                launchArrow(actor, Math.min(2.55, actor.useTicks * .1275) + .15);
            }
            actor.cancelUse();
        }
        if (actor.usePressed) {
            if (selected == Item.HEALING || (isDebuff(selected) && rules.debuffs())) {
                if (selected == Item.HEALING) actor.potsUsed++;
                actor.consume(actor.slot);
                launchThrowable(actor, selected);
            } else if (selected == Item.PEARL && actor.pearlCooldown == 0) {
                actor.consume(actor.slot);
                actor.pearlCooldown = rules.pearlCooldown();
                launchThrowable(actor, selected);
            } else if (actor.useHeld && startsTimedUse(selected, actor.food)) {
                actor.useItem = selected;
                actor.useTicks = 0;
            }
        }
        if (actor.useHeld && actor.useItem != Item.EMPTY && actor.useItem == selected) {
            actor.useTicks = Math.min(ITEM_USE_TICKS, actor.useTicks + 1);
            if (actor.useTicks == ITEM_USE_TICKS && selected != Item.BOW) finishConsumable(actor, selected);
        }
    }

    public static boolean startsTimedUse(Item item, int food) {
        return item == Item.SPEED || item == Item.FIRE_RESISTANCE || item == Item.BOW
                || item == Item.FOOD && food < 20;
    }

    private void finishConsumable(Actor actor, Item item) {
        actor.consume(actor.slot);
        actor.cancelUse();
        switch (item) {
            case SPEED -> applyEffect(actor.id, Effect.SPEED, rules.speedDuration());
            case FIRE_RESISTANCE -> applyEffect(actor.id, Effect.FIRE_RESISTANCE, rules.fireResistanceDuration());
            case FOOD -> {
                actor.food = Math.min(20, actor.food + 8);
                actor.saturation = Math.min(actor.food, actor.saturation + 12);
            }
            default -> throw new IllegalStateException("Unexpected timed consumable " + item);
        }
        emit(EventType.DRINK, actor.id, actor.id, item, actor.feet, 0);
    }

    private static boolean consumeArrow(Actor actor) {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (actor.inventory.get(slot).item() == Item.ARROW) {
                actor.consume(slot);
                return true;
            }
        }
        return false;
    }

    private void launchArrow(Actor actor, double speed) {
        Vec3 aim = direction(actor.yaw, actor.pitch);
        Vec3 position = actor.feet.add(0, EYE_HEIGHT - .12, 0);
        Vec3 velocity = aim.scale(speed).add(actor.velocity.x * .35, 0, actor.velocity.z * .35);
        launch(actor, Item.ARROW, position, velocity);
    }

    private void launchThrowable(Actor actor, Item item) {
        var launch = ThrowablePhysics.launch(actor.feet, actor.yaw, actor.pitch, item, combatRandom);
        launch(actor, item, launch.position(), launch.velocity());
    }

    private void launch(Actor actor, Item item, Vec3 position, Vec3 velocity) {
        shots.add(new Projectile(++nextProjectile, actor.id, item, position, velocity));
        emit(EventType.THROW, actor.id, 0, item, position, 0);
    }

    private boolean advance(Projectile shot) {
        Actor owner = actors.get(shot.ownerId);
        if (owner == null || !owner.alive() || shot.age++ >= PROJECTILE_LIFETIME) return true;
        boolean arrow = shot.item == Item.ARROW;
        double impact = arrow ? firstBlock(arena, shot.position, shot.velocity)
                : firstThrowableBlock(arena, shot.position, shot.velocity);
        Actor target = null;
        for (Actor actor : actors.values()) {
            if (!actor.alive() || (actor == owner && shot.age < (arrow ? 4 : 5))) continue;
            double bodyHit = arrow ? firstBody(shot.position, shot.velocity, actor.feet)
                    : firstThrowableBody(shot.position, shot.velocity, actor.feet);
            if (bodyHit < impact || (!arrow && target == null && bodyHit <= 1 && bodyHit == impact)) {
                impact = bodyHit;
                target = actor;
            }
        }
        if (impact <= 1) {
            Vec3 hit = shot.position.add(shot.velocity.scale(impact));
            switch (shot.item) {
                case PEARL -> {
                    pearlHit(owner, target, hit);
                    landPearl(owner, shot.position, hit, shot.velocity);
                }
                case ARROW -> {
                    if (target != null && target != owner) {
                        strike(owner, target, Math.max(2, shot.velocity.length() * 3.2), rules.projectileKnockback(), ZERO, Item.ARROW,
                                target.useHeld && target.selected() == Item.SWORD);
                    }
                }
                default -> splash(owner, shot.item, shot.position, target);
            }
            return true;
        }
        Vec3 next = shot.position.add(shot.velocity);
        if (!(arrow ? arena.contains(next) : arena.containsThrowable(next))) {
            if (shot.item == Item.PEARL) emit(EventType.REJECTED_PEARL, owner.id, owner.id, Item.PEARL, next, 0);
            return true;
        }
        shot.velocity = arrow ? shot.velocity.scale(.985).add(0, -.05, 0)
                : ThrowablePhysics.nextVelocity(shot.item, shot.velocity, arena.throwableInWater(shot.position));
        shot.position = next;
        return false;
    }

    private void pearlHit(Actor owner, Actor target, Vec3 contact) {
        if (target == null || target == owner || target.hurtTicks != 0 || !arena.canHarm(owner.id, target.id)) return;
        // Paper's zero-damage hit still causes a fresh hurt reaction and attributes it to the thrower.
        acceptRawDamage(target, 0);
        target.hurtTick = currentTick;
        target.recentAttacker = owner.id;
        target.lastDamageTick = currentTick;
        beginHitMotion(target, knockbackDirection(owner, target), rules.projectileKnockback());
        emit(EventType.HIT, owner.id, target.id, Item.PEARL, contact, 0);
    }

    private void splash(Actor owner, Item item, Vec3 center, Actor directTarget) {
        double radius = rules.splashRadius();
        emit(EventType.SPLASH, owner.id, 0, item, center, radius);
        for (Actor actor : actors.values()) {
            if (!actor.alive() || (item != Item.HEALING && actor != owner && !arena.canHarm(owner.id, actor.id))) {
                continue;
            }
            // The radius covers horizontal candidates. Vertically, Paper grows the 0.25-block
            // projectile box by two blocks and requires it to overlap the target's body.
            if (actor.feet.y + BODY_HEIGHT <= center.y - 2 || actor.feet.y >= center.y + 2.25) continue;
            double distance = actor.feet.minus(center).length();
            if (distance >= radius) continue;
            double scale = actor == directTarget ? 1 : 1 - distance / radius;
            if (item == Item.HEALING) {
                heal(actor, Math.round(rules.healing() * scale));
            } else {
                Effect effect = item == Item.POISON ? Effect.POISON : Effect.SLOWNESS;
                int duration = item == Item.POISON ? rules.poisonDuration() : rules.slownessDuration();
                int effectiveDuration = (int) Math.round(duration * scale);
                if (effectiveDuration > TICKS_PER_SECOND
                        && applyEffect(actor.id, effect, effectiveDuration) && actor != owner) {
                    emit(EventType.DEBUFF, owner.id, actor.id, item, actor.feet, effectiveDuration);
                }
            }
        }
    }

    private void landPearl(Actor owner, Vec3 previousPosition, Vec3 impact, Vec3 velocity) {
        // Paper teleports to the projectile's position at the start of the collision tick.
        // Check the actual contact too so an impact across a claim edge cannot fall back outside it.
        Vec3 feet = null;
        if (arena.contains(impact) && arena.canEnter(owner.id, impact)
                && arena.contains(previousPosition) && arena.canEnter(owner.id, previousPosition)) {
            feet = pearlDestination(owner, previousPosition, velocity);
        }
        if (feet == null) {
            emit(EventType.REJECTED_PEARL, owner.id, owner.id, Item.PEARL, impact, 0);
            return;
        }
        owner.feet = feet;
        owner.velocity = ZERO;
        owner.fallDistance = 0;
        settleAtSpawn(owner);
        owner.hitHorizontalWall = false;
        owner.lastHorizontalWallTick = -1;
        owner.jumpCooldown = 0;
        owner.impulseTick = -1;
        owner.hitImpulse = ZERO;
        owner.history.clear();
        owner.motionEpoch++;
        emit(EventType.TELEPORT, owner.id, owner.id, Item.PEARL, feet, rules.pearlDamage());
        applyUnarmoredDamage(owner, owner.id, rules.pearlDamage());
    }

    private void settleAtSpawn(Actor actor) {
        actor.grounded = supported(arena, actor.feet);
        actor.motion = actor.grounded ? CombatMovement.RESTING_ON_GROUND : ZERO;
    }

    private Vec3 pearlDestination(Actor owner, Vec3 previousPosition, Vec3 velocity) {
        if (free(arena, previousPosition) && !occupied(owner, previousPosition)) return previousPosition;
        double length = velocity.length();
        if (length < .001) return null;
        Vec3 back = velocity.scale(-.16 / length);
        for (int step = 1; step <= 6; step++) {
            Vec3 offset = back.scale(step);
            Vec3 feet = previousPosition.add(offset);
            if (!arena.contains(feet) || !arena.canEnter(owner.id, feet)
                    || firstBlock(arena, previousPosition, offset) <= 1) break;
            if (free(arena, feet) && !occupied(owner, feet)) return feet;
        }
        return null;
    }

    private boolean occupied(Actor owner, Vec3 feet) {
        for (Actor other : actors.values()) {
            if (other == owner || !other.alive()) continue;
            Vec3 relative = other.feet.minus(feet);
            if (Math.abs(relative.x) < BODY_RADIUS * 2 && Math.abs(relative.y) < BODY_HEIGHT
                    && Math.abs(relative.z) < BODY_RADIUS * 2) return true;
        }
        return false;
    }

    private static boolean isDebuff(Item item) { return item == Item.POISON || item == Item.SLOWNESS; }
    private static void heal(Actor actor, double amount) { actor.health = Math.min(MAX_HEALTH, actor.health + amount); }

    private void emit(EventType type, int actor, int target, Item item, Vec3 position, double amount) {
        if (events.size() == MAX_EVENTS) events.removeFirst();
        events.addLast(new Event(type, actor, target, item, position, amount));
    }

    private static final class Actor {
        final int id;
        final double startingArmorReduction;
        final List<Stack> inventory;
        final int[] armor = {ARMOR_DURABILITY, ARMOR_DURABILITY, ARMOR_DURABILITY, ARMOR_DURABILITY};
        final EnumMap<Effect, Integer> effects = new EnumMap<>(Effect.class);
        List<Stack> kit;
        Vec3 feet, motion = ZERO, velocity = ZERO;
        Vec3 hitImpulse = ZERO;
        final ArrayDeque<Pose> history = new ArrayDeque<>();
        Intent intent;
        int queuedClicks;
        Item useItem = Item.EMPTY;
        long sequence = -1, lastInputTick, lastHit, lastDamageTick, hurtTick = -1, motionEpoch;
        long impulseTick = -1;
        long lastSprintReleaseSequence = -1, lastSprintHitSequence = -1, lastHorizontalWallTick = -1;
        float yaw, pitch;
        boolean grounded, sprinting, hitHorizontalWall;
        boolean useHeld, submittedUse, usePressed, useReleased;
        int food = 20, foodRegeneration, slot, useTicks, pearlCooldown, hurtTicks, recentAttacker, killerId;
        int hits, combo, longestCombo, lastComboTarget, potsUsed, jumpCooldown;
        double health = MAX_HEALTH, saturation = 6, exhaustion, fallDistance, lastDamage, armorReduction;

        Actor(int id, Vec3 feet, float yaw, List<Stack> kit, double armorReduction) {
            this.id = id;
            this.feet = feet;
            this.yaw = yaw % 360;
            this.kit = new ArrayList<>(kit);
            inventory = new ArrayList<>(kit);
            this.armorReduction = armorReduction;
            startingArmorReduction = armorReduction;
        }

        boolean alive() { return health > 0; }
        boolean sprintReady() { return lastSprintHitSequence < 0 || lastSprintReleaseSequence > lastSprintHitSequence; }
        boolean has(Effect effect) { return effects.containsKey(effect); }
        Item selected() { return inventory.get(slot).item(); }
        void cancelUse() { useItem = Item.EMPTY; useTicks = 0; }

        void consume(int index) {
            Stack stack = inventory.get(index);
            inventory.set(index, stack.count() > 1 ? new Stack(stack.item(), stack.count() - 1) : EMPTY);
        }

        PlayerState snapshot() {
            int potsRemaining = (int) inventory.stream().filter(stack -> stack.item() == Item.HEALING).count();
            return new PlayerState(id, feet, velocity, yaw, pitch, grounded, sprinting, health, food, slot,
                    List.copyOf(inventory), List.of(armor[0], armor[1], armor[2], armor[3]), Map.copyOf(effects),
                    pearlCooldown, new Stats(hits, longestCombo, potsUsed, potsRemaining), killerId, sequence,
                    hurtTick, motionEpoch, lastHorizontalWallTick, alive() ? impulseTick : -1,
                    alive() ? hitImpulse : ZERO);
        }
    }

    private record Click(Actor attacker, Intent input, Item selected, boolean usingItem,
                         long sprintReleaseSequence, Map<Actor, Boolean> blocking) {}
    private record Pose(long tick, Vec3 feet) {}

    private static final class Projectile {
        final long id;
        final int ownerId;
        final Item item;
        Vec3 position, velocity;
        int age;

        Projectile(long id, int ownerId, Item item, Vec3 position, Vec3 velocity) {
            this.id = id;
            this.ownerId = ownerId;
            this.item = item;
            this.position = position;
            this.velocity = velocity;
        }
    }
}
