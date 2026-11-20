package dev.jayms.combat.faction;

import dev.jayms.combat.CombatEngine;
import dev.jayms.combat.CombatEngine.*;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.arena.BasaltCourtyard;
import dev.jayms.combat.practice.ArenaMatch;
import dev.jayms.combat.practice.PracticeMatch.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.jayms.combat.CombatEngine.TICKS_PER_SECOND;
import static dev.jayms.combat.faction.FactionStore.*;

/** Opt-in, server-owned HCF kit map for two factions and up to two fighters per side. */
public final class FactionMatch implements ArenaMatch {
    public enum Season { OPENING, ACTIVE, ENDED }

    public static final int ABILITY = 6;
    public static final int JOIN_EMBER = 7;
    public static final int JOIN_SLATE = 8;
    public static final int RESUPPLY = 9;
    public static final int RAID = 10;
    public static final int SAFE_LOGOUT = 11;

    private static final int MAX_PEERS = 8;
    private static final int TEAM_CAPACITY = 2;
    private static final int ABILITY_COOLDOWN = 200;
    private static final int RAID_TICKS = 20 * TICKS_PER_SECOND;
    private static final int BARD_MAX_ENERGY = 100;
    private static final int BARD_STRENGTH_COST = 40;
    private static final String[] TEAM_NAMES = {"Ember", "Slate"};
    private static final List<Integer> ARMOR = List.of(200, 200, 200, 200);

    private enum Role {
        FIGHTER("Fighter", .68), BARD("Bard", .39), ARCHER("Archer", .45),
        ROGUE("Rogue", .50), MINER("Miner", .57);

        final String title;
        final double armor;

        Role(String title, double armor) {
            this.title = title;
            this.armor = armor;
        }
    }

    public record Settings(int countdownTicks, int roundTicks, int resultTicks, int captureTicks,
                           int warmupTicks, int tagTicks, int logoutTicks, long deathbanMillis,
                           long dtrFreezeMillis, long dtrRecoveryMillis) {
        public Settings {
            if (countdownTicks < 1 || roundTicks <= countdownTicks || resultTicks < 1 || captureTicks < 1
                    || warmupTicks < 0 || tagTicks < 1 || logoutTicks < 1 || deathbanMillis < 1
                    || dtrFreezeMillis < 1 || dtrRecoveryMillis < 1) {
                throw new IllegalArgumentException("Invalid faction timing");
            }
        }

        public static final Settings DEFAULT = new Settings(60, 9_600, 60, 160, 60, 600, 600,
                300_000, 90_000, 60_000);
    }

    private static final class Seat {
        final String account;
        final String display;
        int side = -1;
        int position = -1;
        boolean online = true;
        boolean ready;
        boolean eliminated;
        Role role = Role.FIGHTER;
        int energy;
        long roleReady, abilityReady, backstabUntil, tagUntil, logoutUntil, interactUntil;
        long lastClientSequence = -1, lastHit = -1, lastSwing = -1;
        long noticeUntil;
        String notice;

        Seat(String account, String display) {
            this.account = account;
            this.display = display;
        }
    }

    private final Path save;
    private final Settings settings;
    private final Clock clock;
    private final CombatEngine engine;
    private final Map<Integer, Seat> seats = new LinkedHashMap<>();
    private final Map<String, Member> accounts;
    private final Map<Integer, Integer> marks = new LinkedHashMap<>();
    private final Team[] teams;
    private int season;
    private Season seasonPhase;
    private Phase phase = Phase.WAITING;
    private Result result = Result.NONE;
    private int nextId = 1;
    private int ticksLeft, winner;
    private int captureSide = -1, captureTicks;
    private int raidTarget = -1;
    private boolean contested, dirty;
    private long tick, lastSimulationStep, serial, lastSplash = -1, retryAt;

    public FactionMatch(Path save) throws IOException {
        this(save, Settings.DEFAULT, Clock.systemUTC());
    }

    public FactionMatch(Path save, Settings settings, Clock clock) throws IOException {
        this.save = save;
        this.settings = java.util.Objects.requireNonNull(settings);
        this.clock = java.util.Objects.requireNonNull(clock);
        State state = FactionStore.read(save);
        season = state.season();
        seasonPhase = state.phase();
        teams = state.teams().toArray(Team[]::new);
        accounts = new LinkedHashMap<>(state.members());
        engine = new CombatEngine(CombatRules.DEBUFF, new CombatEngine.Arena() {
            @Override public boolean solid(int x, int y, int z) { return BasaltCourtyard.solid(x, y, z); }
            @Override public boolean contains(Vec3 feet) {
                return BasaltCourtyard.contains(feet.x(), feet.y(), feet.z());
            }
            @Override public boolean throwableSolid(int x, int y, int z) {
                return BasaltCourtyard.throwableSolid(x, y, z);
            }
            @Override public boolean containsThrowable(Vec3 position) {
                return BasaltCourtyard.containsThrowable(position.x(), position.y(), position.z());
            }
            @Override public boolean canHarm(int attacker, int target) { return mayHarm(attacker, target); }
            @Override public boolean canEnter(int id, Vec3 position) { return mayEnter(id, position); }
        });
        reconcileInterruptedLogouts();
    }

    @Override public synchronized int join(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_ ]{1,16}") || name.isBlank()) return 0;
        String account = name.toLowerCase(Locale.ROOT);
        for (var entry : seats.entrySet()) {
            Seat existing = entry.getValue();
            if (!existing.account.equals(account)) continue;
            if (existing.online || existing.eliminated || banned(account)) return 0;
            existing.online = true;
            existing.lastClientSequence = -1;
            existing.logoutUntil = 0;
            setAccount(existing, existing.side, progress(existing).bannedUntil(), 0);
            notice(existing, "Rejoined your combat position");
            persist();
            return entry.getKey();
        }
        if (seats.size() >= MAX_PEERS || nextId == Integer.MAX_VALUE
                || !accounts.containsKey(account) && accounts.size() >= MAX_ACCOUNTS) return 0;
        int id = nextId++;
        var seat = new Seat(account, name);
        seats.put(id, seat);
        if (phase == Phase.WAITING && !banned(account) && seasonPhase != Season.ENDED) {
            int preferred = progress(seat).team();
            if (preferred < 0 || freePosition(preferred, id) < 0) preferred = smallerTeam();
            moveTeam(id, seat, preferred);
        }
        return id;
    }

    @Override public synchronized void leave(int id) {
        Seat seat = seats.get(id);
        if (seat == null || !seat.online) return;
        seat.online = false;
        if (phase == Phase.FIGHT && seat.side >= 0 && !seat.eliminated && tagged(seat)) {
            engine.clearInput(id);
            seat.logoutUntil = tick + settings.logoutTicks();
            long wallDeadline = now() + settings.logoutTicks() * 50L;
            setAccount(seat, seat.side, progress(seat).bannedUntil(), wallDeadline);
            persist();
            return;
        }
        remove(id);
        if (phase == Phase.COUNTDOWN) returnToLobby();
        if (phase == Phase.FIGHT) checkSurvivors(Result.FORFEIT);
    }

    @Override public synchronized void ready(int id) {
        Seat seat = active(id);
        if (seat == null || seasonPhase == Season.ENDED) return;
        if (phase == Phase.RESULT && ticksLeft == 0) returnToLobby();
        if (phase != Phase.WAITING || !eligible(seat)) return;
        seat.ready = !seat.ready;
        if (!readyTeams()) return;
        for (var entry : seats.entrySet()) {
            Seat member = entry.getValue();
            if (member.side >= 0) spawn(entry.getKey(), member, false);
        }
        phase = Phase.COUNTDOWN;
        ticksLeft = settings.countdownTicks();
    }

    @Override public synchronized boolean swap(int id, int first, int second) {
        Seat seat = active(id);
        if (seat == null) return false;
        if (phase == Phase.FIGHT) return engine.swapInventory(id, first, second);
        if (phase != Phase.WAITING || seat.ready || !engine.swapKit(id, first, second)) return false;
        spawn(id, seat, false);
        return true;
    }

    @Override public synchronized boolean quickMove(int id, int source) {
        Seat seat = active(id);
        if (seat == null) return false;
        if (phase == Phase.FIGHT) return engine.quickMoveInventory(id, source);
        if (phase != Phase.WAITING || seat.ready || !engine.quickMoveKit(id, source)) return false;
        spawn(id, seat, false);
        return true;
    }

    @Override public synchronized void input(int id, Intent intent) {
        Seat seat = active(id);
        boolean combat = phase == Phase.FIGHT;
        if (seat == null || (!combat && !canRaid(seat)) || intent == null
                || intent.sequence() <= seat.lastClientSequence) {
            return;
        }
        var authoritative = new Intent(++serial, intent.yaw(), intent.pitch(), intent.forward(), intent.strafe(),
                intent.jump(), intent.sprint(), combat && intent.attack(), combat && intent.use(), intent.slot(),
                combat ? intent.attackView() : null);
        if (engine.submit(id, authoritative)) seat.lastClientSequence = intent.sequence();
    }

    @Override public synchronized void action(int id, int action) {
        Seat seat = seats.get(id);
        if (seat == null || !seat.online) return;
        if (action == JOIN_EMBER || action == JOIN_SLATE) {
            if (phase == Phase.RESULT && ticksLeft == 0) returnToLobby();
            if (phase == Phase.WAITING && !seat.ready) moveTeam(id, seat, action - JOIN_EMBER);
            return;
        }
        if (seat.side < 0 || seat.eliminated) return;
        if (action >= 1 && action <= Role.values().length && phase == Phase.WAITING && !seat.ready) {
            Role selected = Role.values()[action - 1];
            if (availableRole(seat, selected)) {
                seat.role = selected;
                spawn(id, seat, true);
            } else {
                notice(seat, "Your faction already has a " + selected.title);
            }
            return;
        }
        if (phase != Phase.FIGHT) {
            if (action == RAID && canRaid(seat)) supplies(id, seat, true);
            return;
        }
        switch (action) {
            case ABILITY -> ability(id, seat);
            case RESUPPLY -> supplies(id, seat, false);
            case RAID -> supplies(id, seat, true);
            case SAFE_LOGOUT -> notice(seat, tagged(seat) ? "Combat tagged. Disconnect leaves your fighter behind."
                    : safeTerritory(id, seat) ? "Safe to disconnect" : "Move to your home before requesting safe logout");
            default -> { }
        }
    }

    @Override public synchronized void tick() {
        tick++;
        recoverDtr(now());
        switch (phase) {
            case WAITING -> { }
            case COUNTDOWN -> {
                if (--ticksLeft == 0) {
                    phase = Phase.FIGHT;
                    ticksLeft = settings.roundTicks();
                    if (seasonPhase == Season.OPENING) {
                        seasonPhase = Season.ACTIVE;
                        dirty = true;
                    }
                    for (Seat seat : seats.values()) seat.roleReady = tick + settings.warmupTicks();
                }
            }
            case FIGHT -> fight();
            case RESULT -> tickResult();
        }
        persist();
    }

    @Override public synchronized View view(int id) {
        var fighters = new ArrayList<Fighter>(TEAM_CAPACITY * 2);
        for (var entry : seats.entrySet()) {
            int fighterId = entry.getKey();
            Seat seat = entry.getValue();
            if (seat.side < 0) continue;
            PlayerState fighter = engine.snapshot(fighterId);
            if (fighter == null) continue;
            fighters.add(Fighter.fromCombat(fighter, seat.display, seat.lastSwing >= 0 && tick - seat.lastSwing < 5,
                    seat.ready, seat.side, seat.role.title, engine.currentTick(), lastSimulationStep));
        }
        Seat seat = seats.get(id);
        PlayerState self = seat == null || !seat.online || seat.side < 0 || seat.eliminated
                ? null : engine.snapshot(id);
        List<Missile> missiles = phase == Phase.FIGHT ? engine.projectiles().stream().limit(64).map(p ->
                new Missile(p.id(), p.item(), (float) p.position().x(), (float) p.position().y(),
                        (float) p.position().z())).toList() : List.of();
        return new View(phase, Kit.DEBUFF, result, winner, ticksLeft, self == null ? id : 0, tick,
                List.copyOf(fighters), self == null ? List.of() : self.inventory(), self == null ? 0 : self.food(),
                self == null ? 0 : self.pearlCooldownTicks(), self == null ? Map.of() : self.effects(),
                self == null ? List.of() : self.durability(), missiles, seat == null ? -1 : seat.lastHit,
                lastSplash, status(id, seat));
    }

    /** Owner-only lifecycle hook. A fresh season clears deaths, raid progress and old account assignments. */
    public synchronized void beginSeason() throws IOException {
        requireSeasonBoundary();
        if (season == Integer.MAX_VALUE) throw new IllegalStateException("Faction season limit reached");
        var reset = new State(season + 1, Season.OPENING, List.of(Team.NEW, Team.NEW), Map.of());
        FactionStore.write(save, reset);
        season = reset.season();
        seasonPhase = reset.phase();
        teams[0] = teams[1] = Team.NEW;
        accounts.clear();
        dirty = false;
        returnToLobby();
    }

    /** Owner-only lifecycle hook. It preserves results and suspends subsequent starts. */
    public synchronized void endSeason() throws IOException {
        requireSeasonBoundary();
        FactionStore.write(save, snapshot(Season.ENDED));
        seasonPhase = Season.ENDED;
        dirty = false;
        returnToLobby();
    }

    private void requireSeasonBoundary() {
        if (phase == Phase.COUNTDOWN || phase == Phase.FIGHT || raidOpen()) {
            throw new IllegalStateException("Finish the faction match before switching seasons");
        }
    }

    private void fight() {
        boolean departed = expireLogoutActors();
        for (var entry : seats.entrySet()) passives(entry.getKey(), entry.getValue());
        engine.tick(tick);
        lastSimulationStep = tick;
        boolean killed = false;
        for (var events = engine.drainEvents(); !events.isEmpty(); events = engine.drainEvents()) {
            for (Event event : events) {
                Seat actor = seats.get(event.actorId());
                Seat target = seats.get(event.targetId());
                switch (event.type()) {
                    case SWING -> { if (actor != null) actor.lastSwing = tick; }
                    case HIT -> hit(event, actor, target);
                    case DEBUFF -> tag(actor, target);
                    case SPLASH -> lastSplash = tick;
                    case DEATH -> {
                        if (target != null && !target.eliminated) {
                            death(event.targetId(), actor, target);
                            killed = true;
                        }
                    }
                    default -> { }
                }
            }
        }
        if (killed || departed) checkSurvivors(killed ? Result.KNOCKOUT : Result.FORFEIT);
        if (phase == Phase.FIGHT) controlPoint();
        if (phase == Phase.FIGHT && --ticksLeft == 0) finish(0, Result.TIMEOUT);
    }

    private void hit(Event event, Seat actor, Seat target) {
        if (target == null) return;
        target.lastHit = tick;
        tag(actor, target);
        if (actor == null || !classActive(actor)) return;
        if (actor.role == Role.ARCHER && event.item() == Item.ARROW) {
            if (engine.applyEffect(event.targetId(), Effect.MARK, 200)) marks.put(event.targetId(), event.actorId());
        }
        if (actor.role == Role.ROGUE && event.item() == Item.SWORD && actor.backstabUntil > tick
                && behind(engine.snapshot(event.actorId()), engine.snapshot(event.targetId()))) {
            actor.backstabUntil = 0;
            if (engine.damage(event.actorId(), event.targetId(), 3)) notice(actor, "Backstab landed");
        }
    }

    private static boolean behind(PlayerState attacker, PlayerState target) {
        if (attacker == null || target == null || target.health() <= 0) return false;
        Vec3 offset = attacker.feet().minus(target.feet());
        double length = Math.hypot(offset.x(), offset.z());
        double yaw = Math.toRadians(target.yaw());
        return length > .05 && (Math.cos(yaw) * offset.x() + Math.sin(yaw) * offset.z()) / length < -.55;
    }

    private void tag(Seat actor, Seat target) {
        if (actor == null || target == null || actor.side == target.side) return;
        actor.tagUntil = tick + settings.tagTicks();
        target.tagUntil = tick + settings.tagTicks();
    }

    private void death(int id, Seat killer, Seat victim) {
        victim.eliminated = true;
        victim.ready = false;
        victim.tagUntil = victim.logoutUntil = 0;
        setAccount(victim, victim.side, now() + settings.deathbanMillis(), 0);
        reduceDtr(victim.side, now());
        if (killer != null && killer.side >= 0 && killer.side != victim.side) {
            int potLoot = Math.min(3, engine.snapshot(id).stats().potsRemaining());
            changeSupply(killer.side, potLoot);
        }
        clearMarks(id);
        if (!victim.online) remove(id);
    }

    private void controlPoint() {
        boolean[] onPoint = new boolean[2];
        for (var entry : seats.entrySet()) {
            Seat seat = entry.getValue();
            if (!eligible(seat) || !seat.online) continue;
            Vec3 feet = engine.snapshot(entry.getKey()).feet();
            if (Math.abs(feet.x()) < 2 && Math.abs(feet.z()) < 2 && feet.y() <= BasaltCourtyard.FLOOR_Y + 2.1) {
                onPoint[seat.side] = true;
            }
        }
        contested = onPoint[0] && onPoint[1];
        int holding = onPoint[0] == onPoint[1] ? -1 : (onPoint[0] ? 0 : 1);
        if (holding != captureSide) {
            captureSide = holding;
            captureTicks = 0;
        }
        if (captureSide < 0 || ++captureTicks < settings.captureTicks()) return;
        Team side = teams[captureSide];
        teams[captureSide] = new Team(side.dtr(), side.stash(),
                side.captures() == Integer.MAX_VALUE ? side.captures() : side.captures() + 1, side.nextRecovery());
        dirty = true;
        finish(firstSurvivor(captureSide), Result.OBJECTIVE);
    }

    private void passives(int id, Seat seat) {
        if (!eligible(seat) || !seat.online || !classActive(seat)) return;
        if (seat.role == Role.BARD) {
            seat.energy = Math.min(BARD_MAX_ENERGY, seat.energy + 1);
            if (tick % 10 == 0) buffAllies(id, seat, Effect.REGENERATION, 25);
        } else if (seat.role == Role.ROGUE && tick % 15 == 0) {
            engine.applyEffect(id, Effect.SPEED, 22);
        } else if (seat.role == Role.MINER && tick % 15 == 0 && safeTerritory(id, seat)) {
            engine.applyEffect(id, Effect.SPEED, 22);
        }
    }

    private void ability(int id, Seat seat) {
        if (!classActive(seat) || seat.abilityReady > tick) return;
        switch (seat.role) {
            case BARD -> {
                if (seat.energy < BARD_STRENGTH_COST) return;
                seat.energy -= BARD_STRENGTH_COST;
                buffAllies(id, seat, Effect.STRENGTH, 80);
                notice(seat, "Strength shared with nearby faction members");
            }
            case ROGUE -> {
                seat.backstabUntil = tick + 80;
                notice(seat, "Backstab armed for four seconds");
            }
            case MINER -> {
                if (!safeTerritory(id, seat) || teams[seat.side].stash() == MAX_STASH) return;
                changeSupply(seat.side, 1);
                notice(seat, "One healing reserve added to the faction stash");
            }
            default -> { return; }
        }
        seat.abilityReady = tick + ABILITY_COOLDOWN;
    }

    private void buffAllies(int id, Seat source, Effect effect, int duration) {
        Vec3 center = engine.snapshot(id).feet();
        for (var entry : seats.entrySet()) {
            Seat ally = entry.getValue();
            if (ally.side != source.side || !eligible(ally) || !ally.online) continue;
            if (engine.snapshot(entry.getKey()).feet().minus(center).length() <= 7) {
                engine.applyEffect(entry.getKey(), effect, duration);
            }
        }
    }

    private void supplies(int id, Seat seat, boolean raid) {
        if (seat.interactUntil > tick) return;
        int from = raid ? 1 - seat.side : seat.side;
        Vec3 feet = engine.snapshot(id).feet();
        if (territory(feet) != from || Math.abs(feet.x()) > 3
                || (raid ? teams[from].dtr() > 0 : tagged(seat))) return;
        if (teams[from].stash() == 0 || !engine.grant(id, new Stack(Item.HEALING, 1))) return;
        changeSupply(from, -1);
        seat.interactUntil = tick + 10;
        notice(seat, raid ? "Enemy stash raided for one healing potion" : "One healing potion taken from the stash");
    }

    private void changeSupply(int side, int delta) {
        Team old = teams[side];
        int next = Math.max(0, Math.min(MAX_STASH, old.stash() + delta));
        if (next == old.stash()) return;
        teams[side] = new Team(old.dtr(), next, old.captures(), old.nextRecovery());
        dirty = true;
    }

    private void reduceDtr(int side, long now) {
        Team old = teams[side];
        teams[side] = new Team(Math.max(0, old.dtr() - 1), old.stash(),
                old.captures(), now + settings.dtrFreezeMillis());
        dirty = true;
    }

    private void recoverDtr(long now) {
        for (int side = 0; side < 2; side++) {
            Team old = teams[side];
            final int checking = side;
            if (old.dtr() >= MAX_DTR || now < old.nextRecovery() || raidOpen() && side == raidTarget
                    || seats.values().stream().anyMatch(s -> s.side == checking && tagged(s))) continue;
            int next = (int) Math.min(MAX_DTR, old.dtr() + 1L + (now - old.nextRecovery())
                    / settings.dtrRecoveryMillis());
            long recovery = next == MAX_DTR ? 0
                    : old.nextRecovery() + (next - old.dtr()) * settings.dtrRecoveryMillis();
            teams[side] = new Team(next, old.stash(), old.captures(), recovery);
            dirty = true;
        }
    }

    private void reconcileInterruptedLogouts() throws IOException {
        long now = now();
        for (var entry : accounts.entrySet()) {
            Member old = entry.getValue();
            if (old.logoutUntil() == 0) continue;
            long ban = old.bannedUntil();
            if (now < old.logoutUntil()) {
                ban = Math.max(ban, now + settings.deathbanMillis());
                reduceDtr(old.team(), now);
            }
            entry.setValue(new Member(old.team(), ban, 0));
            dirty = true;
        }
        if (dirty) {
            FactionStore.write(save, snapshot(seasonPhase));
            dirty = false;
        }
    }

    private boolean expireLogoutActors() {
        boolean departed = false;
        for (Iterator<Map.Entry<Integer, Seat>> it = seats.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            Seat seat = entry.getValue();
            if (seat.online || seat.logoutUntil == 0 || seat.logoutUntil > tick) continue;
            setAccount(seat, seat.side, progress(seat).bannedUntil(), 0);
            clearMarks(entry.getKey());
            engine.leave(entry.getKey());
            it.remove();
            departed = true;
        }
        return departed;
    }

    private boolean mayHarm(int attacker, int target) {
        Seat source = seats.get(attacker);
        Seat victim = seats.get(target);
        return phase == Phase.FIGHT && source != null && victim != null && source.side != victim.side
                && !safeTerritory(attacker, source) && !safeTerritory(target, victim);
    }

    private boolean mayEnter(int id, Vec3 destination) {
        int land = territory(destination);
        if (land < 0) return true;
        Seat seat = seats.get(id);
        if (seat == null) return false;
        if (canRaid(seat)) return true;
        if (phase != Phase.FIGHT) return seat.side == land;
        if (seat.side != land && teams[land].dtr() == 0) return true;
        if (seat.side == land && !tagged(seat)) return true;
        PlayerState prior = engine.snapshot(id);
        return prior != null && territory(prior.feet()) == land
                && Math.abs(destination.z()) <= Math.abs(prior.feet().z());
    }

    private boolean safeTerritory(int id, Seat seat) {
        if (seat.side < 0 || tagged(seat) || teams[seat.side].dtr() == 0) return false;
        PlayerState state = engine.snapshot(id);
        return state != null && territory(state.feet()) == seat.side;
    }

    private static int territory(Vec3 feet) {
        return BasaltCourtyard.factionTerritory(feet.z());
    }

    private boolean tagged(Seat seat) { return seat.tagUntil > tick; }

    private boolean classActive(Seat seat) { return !seat.eliminated && seat.roleReady <= tick; }

    private boolean readyTeams() {
        int ember = countSide(0);
        if (ember == 0 || ember != countSide(1)) return false;
        return seats.values().stream().filter(s -> s.side >= 0).allMatch(s -> s.ready && eligible(s));
    }

    private boolean eligible(Seat seat) {
        return seat.side >= 0 && !seat.eliminated && !banned(seat.account);
    }

    private Seat active(int id) {
        Seat seat = seats.get(id);
        return seat != null && seat.online && seat.side >= 0 ? seat : null;
    }

    private boolean availableRole(Seat seat, Role role) {
        return role == Role.FIGHTER || seats.values().stream().noneMatch(
                other -> other != seat && other.side == seat.side && other.role == role);
    }

    private int countSide(int side) {
        return (int) seats.values().stream().filter(s -> s.side == side).count();
    }

    private int smallerTeam() { return countSide(0) <= countSide(1) ? 0 : 1; }

    private int freePosition(int side, int except) {
        for (int position = 0; position < TEAM_CAPACITY; position++) {
            final int choice = position;
            if (seats.entrySet().stream().noneMatch(e ->
                    e.getKey() != except && e.getValue().side == side && e.getValue().position == choice)) {
                return position;
            }
        }
        return -1;
    }

    private void moveTeam(int id, Seat seat, int side) {
        if (banned(seat.account) || seasonPhase == Season.ENDED) return;
        int position = freePosition(side, id);
        if (position < 0) {
            notice(seat, TEAM_NAMES[side] + " is full");
            return;
        }
        boolean changed = seat.side != side;
        seat.side = side;
        seat.position = position;
        if (!availableRole(seat, seat.role)) seat.role = Role.FIGHTER;
        setAccount(seat, side, progress(seat).bannedUntil(), 0);
        spawn(id, seat, changed);
    }

    private void spawn(int id, Seat seat, boolean loadRoleKit) {
        var start = BasaltCourtyard.teamSpawn(seat.side, seat.position);
        var feet = new Vec3(start.x(), start.y(), start.z());
        if (engine.snapshot(id) == null) {
            if (!engine.join(id, feet, start.yaw())) throw new IllegalStateException("Faction spawn is obstructed");
            loadRoleKit = true;
        }
        if (loadRoleKit) {
            var kit = new ArrayList<>(CombatRules.DEBUFF.openingKit());
            if (seat.role == Role.ARCHER) {
                kit.set(5, new Stack(Item.BOW, 1));
                kit.set(6, new Stack(Item.ARROW, 32));
            }
            engine.setKit(id, kit);
        }
        if (!engine.reset(id, feet, start.yaw())) throw new IllegalStateException("Faction spawn is obstructed");
        engine.equipArmor(id, seat.role.armor, ARMOR);
        seat.energy = 0;
        seat.tagUntil = seat.abilityReady = seat.backstabUntil = seat.interactUntil = 0;
        seat.lastHit = seat.lastSwing = -1;
    }

    private void checkSurvivors(Result cause) {
        int ember = firstSurvivor(0);
        int slate = firstSurvivor(1);
        if (ember == 0 || slate == 0) finish(ember + slate, cause);
    }

    private int firstSurvivor(int side) {
        return seats.entrySet().stream().filter(e -> e.getValue().side == side && eligible(e.getValue()))
                .mapToInt(Map.Entry::getKey).findFirst().orElse(0);
    }

    private void finish(int winner, Result cause) {
        this.winner = winner;
        result = cause;
        phase = Phase.RESULT;
        ticksLeft = settings.resultTicks();
        Seat victor = seats.get(winner);
        for (var it = seats.entrySet().iterator(); it.hasNext();) {
            var entry = it.next();
            Seat seat = entry.getValue();
            seat.ready = false;
            seat.tagUntil = 0;
            if (!seat.online) {
                setAccount(seat, seat.side, progress(seat).bannedUntil(), 0);
                engine.leave(entry.getKey());
                it.remove();
            }
        }
        if (cause == Result.KNOCKOUT && victor != null) startRaid(victor.side);
    }

    private void startRaid(int winningSide) {
        int losingSide = 1 - winningSide;
        if (teams[losingSide].dtr() != 0 || seats.values().stream().noneMatch(
                seat -> seat.side == winningSide && seat.online && eligible(seat))) return;
        raidTarget = losingSide;
        ticksLeft = RAID_TICKS;
        engine.clearProjectiles();
        for (var entry : seats.entrySet()) {
            if (!canRaid(entry.getValue())) continue;
            int id = entry.getKey();
            engine.clearInput(id);
            engine.applyEffect(id, Effect.POISON, 0);
            engine.applyEffect(id, Effect.SLOWNESS, 0);
            engine.applyEffect(id, Effect.MARK, 0);
            entry.getValue().lastSwing = -1;
        }
    }

    private void tickResult() {
        if (raidOpen()) {
            if (seats.values().stream().anyMatch(this::canRaid)) {
                engine.tick(tick);
                lastSimulationStep = tick;
                engine.drainEvents();
            } else {
                ticksLeft = 0;
            }
        }
        if (ticksLeft > 0) ticksLeft--;
        if (ticksLeft == 0) {
            raidTarget = -1;
            if (seats.values().stream().noneMatch(s -> s.online && s.side >= 0)) returnToLobby();
        }
    }

    private boolean raidOpen() {
        return phase == Phase.RESULT && raidTarget >= 0 && ticksLeft > 0;
    }

    private boolean canRaid(Seat seat) {
        return raidOpen() && seat.online && seat.side == 1 - raidTarget && eligible(seat);
    }

    private void returnToLobby() {
        phase = Phase.WAITING;
        result = Result.NONE;
        winner = ticksLeft = captureTicks = 0;
        captureSide = raidTarget = -1;
        contested = false;
        lastSplash = -1;
        marks.clear();
        for (var entry : seats.entrySet()) {
            Seat seat = entry.getValue();
            seat.ready = seat.eliminated = false;
            if (seat.side < 0) continue;
            if (banned(seat.account) || seasonPhase == Season.ENDED) {
                seat.side = seat.position = -1;
                engine.leave(entry.getKey());
            } else {
                spawn(entry.getKey(), seat, false);
            }
        }
    }

    private void remove(int id) {
        seats.remove(id);
        clearMarks(id);
        engine.leave(id);
    }

    private void clearMarks(int id) {
        for (var it = marks.entrySet().iterator(); it.hasNext();) {
            var mark = it.next();
            if (mark.getKey() == id || mark.getValue() == id) {
                engine.applyEffect(mark.getKey(), Effect.MARK, 0);
                it.remove();
            }
        }
    }

    private List<String> status(int id, Seat seat) {
        var info = new ArrayList<String>(8);
        info.add("HCF season " + season + " / " + switch (seasonPhase) {
            case OPENING -> "opening";
            case ACTIVE -> "active";
            case ENDED -> "ended";
        });
        info.add(teamStatus(0));
        info.add(teamStatus(1));
        if (phase == Phase.FIGHT || result == Result.OBJECTIVE) {
            info.add(contested ? "Hill contested" : captureSide < 0 ? "Hill open"
                    : TEAM_NAMES[captureSide] + " hill " + seconds(captureTicks) + "/" + seconds(settings.captureTicks()) + "s");
        }
        if (raidOpen()) {
            info.add("Raid " + TEAM_NAMES[raidTarget] + " stash / " + seconds(ticksLeft) + "s"
                    + (seat != null && canRaid(seat) ? " / V at enemy home" : ""));
        }
        if (seat != null && seat.online) {
            long bannedUntil = progress(seat).bannedUntil();
            if (bannedUntil > now()) {
                info.add("Deathban " + ((bannedUntil - now() + 999) / 1_000) + "s");
            } else if (seat.side >= 0) {
                String role = seat.role.title;
                if (phase == Phase.FIGHT && !classActive(seat)) role += " warmup " + seconds(seat.roleReady - tick) + "s";
                if (seat.role == Role.BARD) role += " / " + seat.energy + " energy";
                if (seat.abilityReady > tick) role += " / ability " + seconds(seat.abilityReady - tick) + "s";
                info.add(TEAM_NAMES[seat.side] + " / " + role);
                if (tagged(seat)) info.add("Combat tag " + seconds(seat.tagUntil - tick) + "s");
                else if (safeTerritory(id, seat)) info.add("Protected faction home");
            }
            if (seat.notice != null && seat.noticeUntil > tick && info.size() < 7) info.add(seat.notice);
        }
        if (dirty && retryAt > tick) info.add("Faction storage retry pending");
        return List.copyOf(info);
    }

    private String teamStatus(int side) {
        Team team = teams[side];
        return TEAM_NAMES[side] + " DTR " + team.dtr() + (team.dtr() == 0 ? " RAIDABLE" : "")
                + " / stash " + team.stash() + " / hills " + team.captures();
    }

    private long seconds(long ticks) { return (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND; }

    private void notice(Seat seat, String text) {
        seat.notice = text;
        seat.noticeUntil = tick + 80;
    }

    private Member progress(Seat seat) { return accounts.getOrDefault(seat.account, Member.NEW); }

    private boolean banned(String account) {
        return accounts.getOrDefault(account, Member.NEW).bannedUntil() > now();
    }

    private void setAccount(Seat seat, int side, long ban, long logout) {
        var next = new Member(side, ban, logout);
        if (!next.equals(accounts.put(seat.account, next))) dirty = true;
    }

    private long now() { return Math.max(1, clock.millis()); }

    private State snapshot(Season lifecycle) {
        return new State(season, lifecycle, List.of(teams[0], teams[1]), accounts);
    }

    private void persist() {
        if (!dirty || retryAt > tick) return;
        try {
            FactionStore.write(save, snapshot(seasonPhase));
            dirty = false;
            retryAt = 0;
        } catch (IOException failed) {
            retryAt = tick + TICKS_PER_SECOND;
        }
    }
}
