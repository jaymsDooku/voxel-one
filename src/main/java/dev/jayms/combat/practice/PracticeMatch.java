package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine;
import dev.jayms.combat.CombatEngine.*;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.arena.PracticeTerrain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Owns one isolated practice match and its spectators. */
public final class PracticeMatch implements ArenaMatch {
    public static final int TICKS_PER_SECOND = 20;
    public static final int SWING_TICKS = 6;
    public static final int CANCEL_BOW_ACTION = 240;
    static final int MAX_VISIBLE_MISSILES = 64;

    public static long restartSwing(long previous, long tick) {
        return previous < 0 || tick - previous >= SWING_TICKS / 2 ? tick : previous;
    }

    public enum Phase { WAITING, COUNTDOWN, FIGHT, RESULT }
    public enum Kit {
        NO_DEBUFF(CombatRules.NO_DEBUFF, "No Debuff", 1),
        DEBUFF(CombatRules.DEBUFF, "Debuff", 1),
        KNOCKBACK_LAB(CombatRules.KNOCKBACK_LAB, "Knockback Lab", 1),
        TEAMFIGHTING_LAB(CombatRules.NO_DEBUFF, "Teamfighting Lab", 2),
        TEAMFIGHTING_30V30(CombatRules.NO_DEBUFF, "Teamfighting Lab 30v30", 30),
        VAULT_BATTLES(CombatRules.NO_DEBUFF, "Vault Battles", 30);

        private final CombatRules rules;
        private final String title;
        private final int teamSize;

        Kit(CombatRules rules, String title, int teamSize) {
            this.rules = rules;
            this.title = title;
            this.teamSize = teamSize;
        }

        public CombatRules rules() { return rules; }
        public String title() { return title; }
        public int teamSize() { return teamSize; }
        public int fighterCapacity() { return 2 * teamSize; }
        public boolean teamBased() { return teamSize > 1; }
        public String teamName(int side) {
            if (this == VAULT_BATTLES) return side == VaultObjective.DEFENDERS ? "Defenders" : "Attackers";
            return side == 0 ? "Ember" : "Slate";
        }

        public PracticeTerrain terrain() {
            return switch (this) {
                case TEAMFIGHTING_30V30 -> PracticeTerrain.BATTLEFIELD;
                case VAULT_BATTLES -> PracticeTerrain.VAULT_BATTLEFIELD;
                default -> PracticeTerrain.COURTYARD;
            };
        }
    }
    public enum Result { NONE, KNOCKOUT, FORFEIT, TIMEOUT, INTERRUPTED, OBJECTIVE }
    public enum VaultStage { OUTER_RING, VAULT_CORE, CAPTURED }

    public record Fighter(int id, String name, float x, float y, float z, float yaw, float pitch,
                          float health, boolean moving, boolean swinging, boolean ready, Item held,
                          int hits, int combo, int used, int remaining, int side, String role,
                          float vx, float vy, float vz, long hurtTick, long motionEpoch, long lastHorizontalWallTick,
                          long impulseTick, float impulseX, float impulseY, float impulseZ, long swingTick,
                          int bowDrawTicks, int bowDurability) {
        public Fighter(int id, String name, float x, float y, float z, float yaw, float pitch,
                       float health, boolean moving, boolean swinging, boolean ready, Item held,
                       int hits, int combo, int used, int remaining, int side, String role,
                       float vx, float vy, float vz, long hurtTick, long motionEpoch, long lastHorizontalWallTick,
                       long impulseTick, float impulseX, float impulseY, float impulseZ, long swingTick) {
            this(id, name, x, y, z, yaw, pitch, health, moving, swinging, ready, held, hits, combo,
                    used, remaining, side, role, vx, vy, vz, hurtTick, motionEpoch, lastHorizontalWallTick,
                    impulseTick, impulseX, impulseY, impulseZ, swingTick, 0, 0);
        }

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
                    impulseTick, wireImpulse(impulse.x()), wireImpulse(impulse.y()), wireImpulse(impulse.z()), swingTick,
                    state.bowDrawTicks(), state.bowDurability());
        }

        private static float wireImpulse(double component) {
            return (float) Math.max(-PracticeWire.MAX_VELOCITY, Math.min(PracticeWire.MAX_VELOCITY, component));
        }
    }
    public record Missile(long id, Item item, float x, float y, float z,
                          float vx, float vy, float vz, boolean critical, boolean embedded) {
        public Missile(long id, Item item, float x, float y, float z) {
            this(id, item, x, y, z, 0, 0, 0, false, false);
        }

        public static List<Missile> visible(List<ProjectileState> projectiles) {
            if (projectiles.size() <= MAX_VISIBLE_MISSILES) {
                return projectiles.stream().map(Missile::fromCombat).toList();
            }
            // Keep live throws and flying arrows visible when a volley leaves many arrows in the terrain.
            return projectiles.stream()
                    .sorted(Comparator.comparingInt(Missile::visibilityPriority)
                            .thenComparing(Comparator.comparingLong(ProjectileState::id).reversed()))
                    .limit(MAX_VISIBLE_MISSILES).map(Missile::fromCombat).toList();
        }

        private static int visibilityPriority(ProjectileState projectile) {
            if (projectile.item() != Item.ARROW) return 0;
            return projectile.embedded() ? 2 : 1;
        }

        private static Missile fromCombat(ProjectileState projectile) {
            Vec3 p = projectile.position();
            if (projectile.item() != Item.ARROW) {
                return new Missile(projectile.id(), projectile.item(), (float) p.x(), (float) p.y(), (float) p.z());
            }
            Vec3 v = projectile.velocity();
            return new Missile(projectile.id(), projectile.item(), (float) p.x(), (float) p.y(), (float) p.z(),
                    (float) v.x(), (float) v.y(), (float) v.z(), projectile.critical(), projectile.embedded());
        }
    }
    public record View(Phase phase, Kit kit, Result result, int winner, int ticksLeft, int spectatorId,
                       long tick, List<Fighter> fighters, List<Stack> inventory, int food, int cooldown,
                       Map<Effect, Integer> effects, List<Integer> durability, List<Missile> missiles,
                       long lastHit, long lastSplash, List<String> status, VaultStage vaultStage) {
        public View(Phase phase, Kit kit, Result result, int winner, int ticksLeft, int spectatorId,
                    long tick, List<Fighter> fighters, List<Stack> inventory, int food, int cooldown,
                    Map<Effect, Integer> effects, List<Integer> durability, List<Missile> missiles,
                    long lastHit, long lastSplash, List<String> status) {
            this(phase, kit, result, winner, ticksLeft, spectatorId, tick, fighters, inventory, food, cooldown,
                    effects, durability, missiles, lastHit, lastSplash, status, null);
        }

        /** Team modes publish the winning side plus one in place of a fighter ID. */
        public int winningTeam() { return kit.teamBased() ? winner - 1 : -1; }
    }

    private static final int COUNTDOWN = 3 * TICKS_PER_SECOND;
    private static final int ROUND = 8 * 60 * TICKS_PER_SECOND;
    private static final int RESULT_DELAY = 2 * TICKS_PER_SECOND;
    private final Map<Integer, Member> members = new LinkedHashMap<>();
    private final Kit kit;
    private final int fighterCapacity;
    private final CombatRules rules;
    private final PracticeTerrain terrain;
    private final CombatEngine.Arena arena = new CombatEngine.Arena() {
        @Override public boolean solid(int x, int y, int z) { return terrain.solid(x, y, z); }
        @Override public boolean contains(Vec3 feet) { return terrain.contains(feet); }
        @Override public boolean throwableSolid(int x, int y, int z) { return terrain.throwableSolid(x, y, z); }
        @Override public boolean containsThrowable(Vec3 position) { return terrain.containsThrowable(position); }
        @Override public boolean canHarm(int attacker, int target) {
            return !kit.teamBased() || opposingTeams(attacker, target);
        }
    };
    private final CombatEngine engine;
    private final boolean offline;
    private final VaultObjective vault;
    private final List<Stack> vaultLoadout;
    private List<Fighter> fighterViews;
    private List<Missile> missileViews;
    private int nextId = 1;
    private long tick, lastSimulationStep, lastSplash = -1;
    private int ticksLeft, winner;
    private Phase phase = Phase.WAITING;
    private Result result = Result.NONE;

    private static final class Member {
        final String name;
        final boolean automated;
        int seat, target;
        SparringPartner bot;
        VaultBot vaultBot;
        boolean ready;
        long lastHit = -1, lastSwing = -1;
        Member(String name, int seat, boolean automated) {
            this.name = name;
            this.seat = seat;
            this.automated = automated;
            ready = automated;
        }
        int team() { return seat % 2; }
    }

    public PracticeMatch(Kit kit, boolean offline) {
        this.kit = kit;
        fighterCapacity = kit.fighterCapacity();
        this.offline = offline;
        rules = kit.rules();
        terrain = kit.terrain();
        vault = kit == Kit.VAULT_BATTLES ? new VaultObjective() : null;
        if (vault != null) {
            var loadout = new ArrayList<>(rules.openingKit());
            loadout.set(CombatEngine.HOTBAR_SIZE - 1, new Stack(Item.BOW, 1));
            loadout.set(CombatEngine.INVENTORY_SIZE - 1, new Stack(Item.ARROW, 32));
            vaultLoadout = List.copyOf(loadout);
        } else {
            vaultLoadout = List.of();
        }
        engine = new CombatEngine(rules, arena);
        if (offline) {
            int localSeat = vault == null ? 0 : VaultObjective.ATTACKERS;
            addMember("You", false, localSeat);
            if (kit.teamSize() == 30) {
                for (int seat = 0; seat < fighterCapacity; seat++) {
                    if (seat == localSeat) continue;
                    addMember(kit.teamName(seat % 2) + " " + (seat / 2 + 1), true, seat);
                }
            } else if (kit.teamBased()) {
                addMember("Rowan", true);
                addMember("Aster", true);
                addMember("Briar", true);
            } else {
                addMember("Sparring partner", true);
            }
        }
    }

    public Kit kit() { return kit; }
    int peerCapacity() { return Math.max(8, fighterCapacity + 4); }

    public synchronized int join(String name) {
        return addMember(name, false);
    }

    private int addMember(String name, boolean automated) {
        int seat = phase == Phase.WAITING ? freeSeat() : -1;
        return addMember(name, automated, seat);
    }

    private int addMember(String name, boolean automated, int seat) {
        if (members.size() >= peerCapacity() || nextId == Integer.MAX_VALUE
                || name == null || !name.matches("[A-Za-z0-9_ ]{1,16}")
                || name.isBlank() || members.values().stream().anyMatch(m -> m.name.equalsIgnoreCase(name)))
            return 0;
        int id = nextId++;
        members.put(id, new Member(name, seat, automated));
        if (seat >= 0) spawn(id, false);
        return id;
    }

    public synchronized void leave(int id) {
        Member member = members.remove(id);
        if (member == null || member.seat < 0) return;
        fighterViews = null;
        missileViews = null;
        engine.leave(id);
        if (phase == Phase.FIGHT) finishIfTeamEliminated(Result.FORFEIT);
        else if (phase == Phase.COUNTDOWN) waitForReady();
        if (phase == Phase.WAITING) promoteSpectators();
    }

    public synchronized void ready(int id) {
        Member member = members.get(id);
        if (member == null || member.seat < 0 || member.automated) return;
        if (phase == Phase.RESULT && ticksLeft == 0) waitForReady();
        if (phase == Phase.WAITING) {
            member.ready = !member.ready;
            fighterViews = null;
        }
        if (phase == Phase.WAITING
                && members.values().stream().filter(m -> m.seat >= 0 && m.ready).count() == fighterCapacity) {
            for (var entry : members.entrySet())
                if (entry.getValue().seat >= 0) spawn(entry.getKey(), true);
            phase = Phase.COUNTDOWN;
            ticksLeft = COUNTDOWN;
        }
    }

    public synchronized boolean swap(int id, int first, int second) {
        Member member = members.get(id);
        if (member == null || member.seat < 0 || first < 0 || second < 0 || first >= 36 || second >= 36)
            return false;
        if (phase == Phase.FIGHT) {
            fighterViews = null;
            return engine.swapInventory(id, first, second);
        }
        if (phase == Phase.WAITING && !member.ready) {
            engine.swapKit(id, first, second);
            spawn(id, true);
            return true;
        }
        return false;
    }

    @Override public synchronized boolean quickMove(int id, int source) {
        Member member = members.get(id);
        if (member == null || member.seat < 0) return false;
        if (phase == Phase.FIGHT) {
            fighterViews = null;
            return engine.quickMoveInventory(id, source);
        }
        if (phase != Phase.WAITING || member.ready || !engine.quickMoveKit(id, source)) return false;
        spawn(id, true);
        return true;
    }

    public synchronized void input(int id, Intent intent) {
        Member member = members.get(id);
        if (member == null || member.seat < 0 || phase != Phase.FIGHT) return;
        engine.submit(id, intent);
    }

    @Override public synchronized void action(int id, int action) {
        if (action != CANCEL_BOW_ACTION || phase != Phase.FIGHT) return;
        Member member = members.get(id);
        if (member == null || member.seat < 0) return;
        engine.cancelBow(id);
        fighterViews = null;
    }

    public synchronized void tick() {
        tick++;
        fighterViews = null;
        missileViews = null;
        switch (phase) {
            case WAITING -> { }
            case COUNTDOWN -> {
                if (--ticksLeft == 0) {
                    phase = Phase.FIGHT;
                    ticksLeft = rules.knockbackOnly() ? 0 : ROUND;
                }
            }
            case FIGHT -> {
                if (offline) driveBots();
                engine.tick(tick);
                lastSimulationStep = tick;
                for (Event event : engine.drainEvents()) {
                    Member actor = members.get(event.actorId()), target = members.get(event.targetId());
                    if (event.type() == EventType.HIT && target != null) target.lastHit = tick;
                    if (event.type() == EventType.SWING && actor != null) actor.lastSwing = restartSwing(actor.lastSwing, tick);
                    if (event.type() == EventType.SPLASH) lastSplash = tick;
                    if (actor != null && actor.automated && event.type() == EventType.THROW && event.item() == Item.HEALING) {
                        restockBotPotion(event.actorId());
                    }
                }
                if (!rules.knockbackOnly()) updateRoundResult();
            }
            case RESULT -> {
                if (ticksLeft > 0) ticksLeft--;
                if (ticksLeft == 0 && members.values().stream().noneMatch(m -> m.seat >= 0)) waitForReady();
            }
        }
    }

    private void updateRoundResult() {
        var fighters = engine.snapshots();
        if (finishIfTeamEliminated(fighters, Result.KNOCKOUT)) return;
        if (vault != null) {
            boolean attackersPresent = false, defendersPresent = false;
            for (PlayerState fighter : fighters) {
                if (fighter.health() <= 0 || !vault.inActiveZone(fighter.feet())) continue;
                if (members.get(fighter.id()).team() == VaultObjective.ATTACKERS) attackersPresent = true;
                else defendersPresent = true;
            }
            if (vault.advance(attackersPresent, defendersPresent)) {
                finish(VaultObjective.ATTACKERS + 1, Result.OBJECTIVE);
                return;
            }
        }
        if (--ticksLeft == 0) {
            finish(vault == null ? 0 : VaultObjective.DEFENDERS + 1, Result.TIMEOUT);
        }
    }

    private boolean finishIfTeamEliminated(Result cause) {
        return finishIfTeamEliminated(engine.snapshots(), cause);
    }

    private boolean finishIfTeamEliminated(List<PlayerState> fighters, Result cause) {
        int[] survivors = new int[2];
        for (PlayerState fighter : fighters) {
            if (fighter.health() > 0) survivors[members.get(fighter.id()).team()] = fighter.id();
        }
        if (survivors[0] != 0 && survivors[1] != 0) return false;
        int survivingId = Math.max(survivors[0], survivors[1]);
        int winner = kit.teamBased() && survivingId != 0 ? members.get(survivingId).team() + 1 : survivingId;
        finish(winner, cause);
        return true;
    }

    private List<Fighter> fighters() {
        if (fighterViews != null) return fighterViews;
        var fighters = new ArrayList<Fighter>(fighterCapacity);
        for (var entry : members.entrySet()) {
            int fighterId = entry.getKey();
            Member member = entry.getValue();
            if (member.seat < 0) continue;
            PlayerState f = engine.snapshot(fighterId);
            fighters.add(Fighter.fromCombat(f, member.name, member.lastSwing >= 0 && tick - member.lastSwing < SWING_TICKS,
                    member.ready, kit.teamBased() ? member.team() : -1,
                    vault == null ? "" : kit.teamName(member.team()),
                    engine.currentTick(), lastSimulationStep, member.lastSwing));
        }
        fighterViews = List.copyOf(fighters);
        return fighterViews;
    }

    public synchronized View view(int id) {
        Member member = members.get(id);
        PlayerState self = member == null || member.seat < 0 ? null : engine.snapshot(id);
        if (kit.teamBased() && phase == Phase.FIGHT && self != null && self.health() <= 0) self = null;
        if (missileViews == null) {
            missileViews = phase == Phase.FIGHT ? Missile.visible(engine.projectiles()) : List.of();
        }
        return new View(phase, kit, result, winner, ticksLeft, self == null ? id : 0, tick, fighters(),
                self == null ? List.of() : self.inventory(), self == null ? 0 : self.food(),
                self == null ? 0 : self.pearlCooldownTicks(), self == null ? Map.of() : self.effects(),
                self == null ? List.of() : self.durability(), missileViews, member == null ? -1 : member.lastHit,
                lastSplash, vault == null ? List.of() : vault.status(phase, result),
                vault == null ? null : vault.stage());
    }

    private int freeSeat() {
        for (int seat = 0; seat < fighterCapacity; seat++) {
            boolean used = false;
            for (Member m : members.values()) if (m.seat == seat) used = true;
            if (!used) return seat;
        }
        return -1;
    }

    private void promoteSpectators() {
        for (var entry : members.entrySet()) {
            if (entry.getValue().seat >= 0) continue;
            int seat = freeSeat();
            if (seat < 0) return;
            entry.getValue().seat = seat;
            spawn(entry.getKey(), false);
        }
    }

    private void waitForReady() {
        phase = Phase.WAITING;
        result = Result.NONE;
        winner = 0;
        ticksLeft = 0;
        lastSplash = -1;
        if (vault != null) vault.reset();
        promoteSpectators();
        for (var entry : members.entrySet()) {
            Member m = entry.getValue();
            if (m.seat >= 0) spawn(entry.getKey(), true);
            m.ready = m.automated;
        }
    }

    private void finish(int winner, Result result) {
        this.winner = winner;
        this.result = result;
        phase = Phase.RESULT;
        ticksLeft = RESULT_DELAY;
    }

    private void spawn(int id, boolean reset) {
        fighterViews = null;
        missileViews = null;
        Member member = members.get(id);
        member.lastHit = member.lastSwing = -1;
        member.target = 0;
        var spawn = terrain.spawn(member.team(), member.seat / 2);
        if (member.automated) {
            member.bot = new SparringPartner(rules, arena, RandomGenerator.getDefault());
            if (vault != null) member.vaultBot = new VaultBot(member.team(), member.seat / 2, arena, rules);
        }
        var feet = new Vec3(spawn.x(), spawn.y(), spawn.z());
        if (reset) {
            engine.reset(id, feet, spawn.yaw());
        } else if (engine.join(id, feet, spawn.yaw()) && !vaultLoadout.isEmpty()) {
            engine.setKit(id, vaultLoadout);
            engine.reset(id, feet, spawn.yaw());
        }
    }

    private boolean opposingTeams(int attacker, int target) {
        Member first = members.get(attacker), second = members.get(target);
        return first != null && second != null && first.seat >= 0 && second.seat >= 0 && first.team() != second.team();
    }

    private void driveBots() {
        var fighters = engine.snapshots();
        for (PlayerState bot : fighters) {
            Member member = members.get(bot.id());
            if (!member.automated || bot.health() <= 0) continue;
            PlayerState opponent = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (PlayerState candidate : fighters) {
                if (candidate.health() <= 0 || !opposingTeams(bot.id(), candidate.id())) continue;
                // Keep chasing a selected opponent until another target is clearly closer.
                var offset = candidate.feet().minus(bot.feet());
                double distance = offset.length();
                double priority = distance - (candidate.id() == member.target ? 1 : 0);
                if (priority >= bestDistance || member.vaultBot != null
                        && !member.vaultBot.canEngage(bot, offset, distance)) continue;
                bestDistance = priority;
                opponent = candidate;
            }
            if (member.vaultBot != null) {
                input(bot.id(), member.vaultBot.input(tick, bot, opponent, vault.stage(), member.bot));
                member.target = opponent == null ? 0 : opponent.id();
                continue;
            }
            if (opponent == null) continue;
            member.target = opponent.id();
            input(bot.id(), member.bot.input(tick, bot, opponent));
        }
    }

    private void restockBotPotion(int id) {
        var bot = engine.snapshot(id);
        if (bot.inventory().get(bot.slot()).item() != Item.EMPTY) return;
        for (int slot = CombatEngine.HOTBAR_SIZE; slot < bot.inventory().size(); slot++) {
            if (bot.inventory().get(slot).item() == Item.HEALING) {
                engine.swapInventory(id, bot.slot(), slot);
                return;
            }
        }
    }
}
