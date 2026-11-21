package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine;
import dev.jayms.combat.CombatEngine.*;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.arena.BasaltCourtyard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Owns one isolated, first-to-one practice match and its spectators. */
public final class PracticeMatch implements ArenaMatch {
    public static final int TICKS_PER_SECOND = 20;
    public static final int SWING_TICKS = 6;

    public static long restartSwing(long previous, long tick) {
        return previous < 0 || tick - previous >= SWING_TICKS / 2 ? tick : previous;
    }

    public enum Phase { WAITING, COUNTDOWN, FIGHT, RESULT }
    public enum Kit { NO_DEBUFF, DEBUFF }
    public enum Result { NONE, KNOCKOUT, FORFEIT, TIMEOUT, INTERRUPTED, OBJECTIVE }

    public record Fighter(int id, String name, float x, float y, float z, float yaw, float pitch,
                          float health, boolean moving, boolean swinging, boolean ready, Item held,
                          int hits, int combo, int used, int remaining, int side, String role,
                          float vx, float vy, float vz, long hurtTick, long motionEpoch, long lastHorizontalWallTick,
                          long impulseTick, float impulseX, float impulseY, float impulseZ, long swingTick) {
        public Fighter(int id, String name, float x, float y, float z, float yaw, float pitch,
                          float health, boolean moving, boolean swinging, boolean ready, Item held,
                          int hits, int combo, int used, int remaining, int side, String role,
                          float vx, float vy, float vz, long hurtTick, long motionEpoch, long lastHorizontalWallTick,
                          long impulseTick, float impulseX, float impulseY, float impulseZ) {
            this(id, name, x, y, z, yaw, pitch, health, moving, swinging, ready, held, hits, combo,
                    used, remaining, side, role, vx, vy, vz, hurtTick, motionEpoch, lastHorizontalWallTick,
                    impulseTick, impulseX, impulseY, impulseZ, -1);
        }

        public static Fighter fromCombat(PlayerState state, String name, boolean swinging,
                                         boolean ready, int side, String role,
                                         long simulationTick, long matchTickAtSimulationTick) {
            return fromCombat(state, name, swinging, ready, side, role, simulationTick, matchTickAtSimulationTick, -1);
        }

        public static Fighter fromCombat(PlayerState state, String name, boolean swinging,
                                         boolean ready, int side, String role,
                                         long simulationTick, long matchTickAtSimulationTick, long swingTick) {
            Vec3 p = state.feet(), v = state.velocity();
            Stats stats = state.stats();
            // Keep combat cues in match time even while the simulation is frozen at the result screen.
            long hurt = state.hurtTick() < 0 ? -1 : matchTickAtSimulationTick - (simulationTick - state.hurtTick());
            long impulseTick = state.impulseTick() < 0 || state.health() <= 0 ? -1
                    : matchTickAtSimulationTick - (simulationTick - state.impulseTick());
            Vec3 impulse = impulseTick < 0 ? new Vec3(0, 0, 0) : state.hitImpulse();
            return new Fighter(state.id(), name, (float) p.x(), (float) p.y(), (float) p.z(), state.yaw(),
                    state.pitch(), (float) state.health(), Math.hypot(v.x(), v.z()) > .12, swinging, ready,
                    state.inventory().get(state.slot()).item(), stats.hits(), stats.longestCombo(),
                    stats.potsUsed(), stats.potsRemaining(), side, role,
                    (float) v.x(), (float) v.y(), (float) v.z(), hurt, state.motionEpoch(), state.lastHorizontalWallTick(),
                    impulseTick, wireImpulse(impulse.x()), wireImpulse(impulse.y()), wireImpulse(impulse.z()), swingTick);
        }

        private static float wireImpulse(double component) {
            return (float) Math.max(-PracticeWire.MAX_VELOCITY, Math.min(PracticeWire.MAX_VELOCITY, component));
        }
    }
    public record Missile(long id, Item item, float x, float y, float z) { }
    public record View(Phase phase, Kit kit, Result result, int winner, int ticksLeft, int spectatorId,
                       long tick, List<Fighter> fighters, List<Stack> inventory, int food, int cooldown,
                       Map<Effect, Integer> effects, List<Integer> durability, List<Missile> missiles,
                       long lastHit, long lastSplash, List<String> status) { }

    private static final int BOT = 2;
    private static final int COUNTDOWN = 3 * TICKS_PER_SECOND;
    private static final int ROUND = 8 * 60 * TICKS_PER_SECOND;
    private static final int RESULT_DELAY = 2 * TICKS_PER_SECOND;
    private static final int MAX_PEERS = 8;
    private final Map<Integer, Member> members = new LinkedHashMap<>();
    private final Kit kit;
    private final CombatRules rules;
    private final CombatEngine.Arena arena = new CombatEngine.Arena() {
        @Override public boolean solid(int x, int y, int z) { return BasaltCourtyard.solid(x, y, z); }
        @Override public boolean contains(Vec3 feet) { return BasaltCourtyard.contains(feet.x(), feet.y(), feet.z()); }
        @Override public boolean throwableSolid(int x, int y, int z) { return BasaltCourtyard.throwableSolid(x, y, z); }
        @Override public boolean containsThrowable(Vec3 position) {
            return BasaltCourtyard.containsThrowable(position.x(), position.y(), position.z());
        }
    };
    private final CombatEngine engine;
    private final boolean offline;
    private int nextId = 1;
    private long tick, lastSimulationStep, lastSplash = -1;
    private SparringPartner sparringPartner;
    private int ticksLeft, winner;
    private Phase phase = Phase.WAITING;
    private Result result = Result.NONE;

    private static final class Member {
        final String name;
        int side;
        boolean ready;
        long lastHit = -1, lastSwing = -1;
        Member(String name, int side) { this.name = name; this.side = side; }
    }

    public PracticeMatch(Kit kit, boolean offline) {
        this.kit = kit;
        this.offline = offline;
        rules = kit == Kit.DEBUFF ? CombatRules.DEBUFF : CombatRules.NO_DEBUFF;
        engine = new CombatEngine(rules, arena);
        if (offline) {
            join("You");
            join("Sparring partner");
            members.get(BOT).ready = true;
        }
    }

    public synchronized int join(String name) {
        if (members.size() >= MAX_PEERS || name == null || !name.matches("[A-Za-z0-9_ ]{1,16}")
                || name.isBlank() || members.values().stream().anyMatch(m -> m.name.equalsIgnoreCase(name)))
            return 0;
        int id = nextId++;
        int side = phase == Phase.WAITING ? freeSide() : -1;
        members.put(id, new Member(name, side));
        if (side >= 0) spawn(id, side, false);
        return id;
    }

    public synchronized void leave(int id) {
        Member member = members.remove(id);
        if (member == null || member.side < 0) return;
        engine.leave(id);
        if (phase == Phase.FIGHT) finish(otherId(id), Result.FORFEIT);
        else if (phase == Phase.COUNTDOWN) waitForReady();
        if (phase == Phase.WAITING) promoteSpectators();
    }

    public synchronized void ready(int id) {
        Member member = members.get(id);
        if (member == null || member.side < 0) return;
        if (phase == Phase.RESULT && ticksLeft == 0) waitForReady();
        if (phase == Phase.WAITING) member.ready = !member.ready;
        if (phase == Phase.WAITING && members.values().stream().filter(m -> m.side >= 0 && m.ready).count() == 2) {
            for (var entry : members.entrySet())
                if (entry.getValue().side >= 0) spawn(entry.getKey(), entry.getValue().side, true);
            phase = Phase.COUNTDOWN;
            ticksLeft = COUNTDOWN;
        }
    }

    public synchronized boolean swap(int id, int first, int second) {
        Member member = members.get(id);
        if (member == null || member.side < 0 || first < 0 || second < 0 || first >= 36 || second >= 36)
            return false;
        if (phase == Phase.FIGHT) return engine.swapInventory(id, first, second);
        if (phase == Phase.WAITING && !member.ready) {
            engine.swapKit(id, first, second);
            engine.reset(id, feet(member.side), BasaltCourtyard.spawn(member.side).yaw());
            return true;
        }
        return false;
    }

    @Override public synchronized boolean quickMove(int id, int source) {
        Member member = members.get(id);
        if (member == null || member.side < 0) return false;
        if (phase == Phase.FIGHT) return engine.quickMoveInventory(id, source);
        if (phase != Phase.WAITING || member.ready || !engine.quickMoveKit(id, source)) return false;
        engine.reset(id, feet(member.side), BasaltCourtyard.spawn(member.side).yaw());
        return true;
    }

    public synchronized void input(int id, Intent intent) {
        Member member = members.get(id);
        if (member == null || member.side < 0 || phase != Phase.FIGHT) return;
        engine.submit(id, intent);
    }

    public synchronized void tick() {
        tick++;
        switch (phase) {
            case WAITING -> { }
            case COUNTDOWN -> {
                if (--ticksLeft == 0) {
                    phase = Phase.FIGHT;
                    ticksLeft = ROUND;
                }
            }
            case FIGHT -> {
                if (offline) input(BOT, sparringPartner.input(tick, engine.snapshot(BOT), engine.snapshot(1)));
                engine.tick(tick);
                lastSimulationStep = tick;
                for (Event event : engine.drainEvents()) {
                    Member actor = members.get(event.actorId()), target = members.get(event.targetId());
                    if (event.type() == EventType.HIT && target != null) target.lastHit = tick;
                    if (event.type() == EventType.SWING && actor != null) actor.lastSwing = restartSwing(actor.lastSwing, tick);
                    if (event.type() == EventType.SPLASH) lastSplash = tick;
                    if (offline && event.actorId() == BOT && event.type() == EventType.THROW && event.item() == Item.HEALING) {
                        restockBotPotion();
                    }
                }
                var fighters = engine.snapshots();
                var survivors = fighters.stream().filter(f -> f.health() > 0).toList();
                if (survivors.size() < fighters.size()) {
                    finish(survivors.size() == 1 ? survivors.get(0).id() : 0, Result.KNOCKOUT);
                } else if (--ticksLeft == 0) {
                    finish(0, Result.TIMEOUT);
                }
            }
            case RESULT -> {
                if (ticksLeft > 0) ticksLeft--;
                if (ticksLeft == 0 && members.values().stream().noneMatch(m -> m.side >= 0)) waitForReady();
            }
        }
    }

    public synchronized View view(int id) {
        var fighters = new ArrayList<Fighter>(2);
        for (var entry : members.entrySet()) {
            int fighterId = entry.getKey();
            Member member = entry.getValue();
            if (member.side < 0) continue;
            PlayerState f = engine.snapshot(fighterId);
            fighters.add(Fighter.fromCombat(f, member.name, member.lastSwing >= 0 && tick - member.lastSwing < SWING_TICKS,
                    member.ready, -1, "", engine.currentTick(), lastSimulationStep, member.lastSwing));
        }
        Member member = members.get(id);
        PlayerState self = member == null || member.side < 0 ? null : engine.snapshot(id);
        List<Missile> missiles = phase == Phase.FIGHT ? engine.projectiles().stream().limit(64).map(p ->
                new Missile(p.id(), p.item(), (float) p.position().x(), (float) p.position().y(),
                        (float) p.position().z())).toList() : List.of();
        return new View(phase, kit, result, winner, ticksLeft, self == null ? id : 0, tick, List.copyOf(fighters),
                self == null ? List.of() : self.inventory(), self == null ? 0 : self.food(),
                self == null ? 0 : self.pearlCooldownTicks(), self == null ? Map.of() : self.effects(),
                self == null ? List.of() : self.durability(), missiles, member == null ? -1 : member.lastHit,
                lastSplash, List.of());
    }

    private int freeSide() {
        for (int side = 0; side < 2; side++) {
            boolean used = false;
            for (Member m : members.values()) if (m.side == side) used = true;
            if (!used) return side;
        }
        return -1;
    }

    private void promoteSpectators() {
        for (var entry : members.entrySet()) {
            if (entry.getValue().side >= 0) continue;
            int side = freeSide();
            if (side < 0) return;
            entry.getValue().side = side;
            spawn(entry.getKey(), side, false);
        }
    }

    private void waitForReady() {
        phase = Phase.WAITING;
        result = Result.NONE;
        winner = 0;
        ticksLeft = 0;
        lastSplash = -1;
        promoteSpectators();
        for (var entry : members.entrySet()) {
            Member m = entry.getValue();
            if (m.side >= 0) spawn(entry.getKey(), m.side, true);
            m.ready = offline && entry.getKey() == BOT;
        }
    }

    private void finish(int winner, Result result) {
        this.winner = winner;
        this.result = result;
        phase = Phase.RESULT;
        ticksLeft = RESULT_DELAY;
    }

    private int otherId(int id) {
        for (var entry : members.entrySet()) if (entry.getKey() != id && entry.getValue().side >= 0) return entry.getKey();
        return 0;
    }

    private void spawn(int id, int side, boolean reset) {
        Member member = members.get(id);
        member.lastHit = member.lastSwing = -1;
        if (offline && id == BOT) sparringPartner = new SparringPartner(rules, arena, RandomGenerator.getDefault());
        float yaw = BasaltCourtyard.spawn(side).yaw();
        if (reset) engine.reset(id, feet(side), yaw);
        else engine.join(id, feet(side), yaw);
    }

    private static Vec3 feet(int side) {
        var spawn = BasaltCourtyard.spawn(side);
        return new Vec3(spawn.x(), spawn.y(), spawn.z());
    }

    private void restockBotPotion() {
        var bot = engine.snapshot(BOT);
        if (bot.inventory().get(bot.slot()).item() != Item.EMPTY) return;
        for (int slot = CombatEngine.HOTBAR_SIZE; slot < bot.inventory().size(); slot++) {
            if (bot.inventory().get(slot).item() == Item.HEALING) {
                engine.swapInventory(BOT, bot.slot(), slot);
                return;
            }
        }
    }
}
