package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Arena;
import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.CombatEngine.PlayerState;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.CombatGeometry;
import dev.jayms.combat.CombatRules;
import dev.jayms.combat.arena.VaultBattlefield;
import dev.jayms.combat.practice.PracticeMatch.VaultStage;
import dev.jayms.player.PlayerDimensions;

import java.util.List;

/** Moves a practice bot through its squad's approach until it meets an opponent. */
final class VaultBot {
    private final Arena arena;
    private final boolean attacker, holder;
    private final int lane;
    private final double engageRange;
    private List<Vec3> route = List.of();
    private List<Vec3> escapeRoute = List.of();
    private int waypoint;
    private int escapeWaypoint;
    private double escapeBestDistance;
    private long escapeProgressTick;
    private VaultStage stage;

    VaultBot(int team, int seat, Arena arena, CombatRules rules) {
        this.arena = arena;
        attacker = team == VaultObjective.ATTACKERS;
        holder = !attacker && (seat / 3) % 3 == 0;
        lane = VaultBattlefield.laneForSpawn(seat);
        engageRange = rules.reach() + (holder ? .6 : 1.6);
    }

    boolean canEngage(PlayerState bot, Vec3 offset, double distance) {
        return distance <= engageRange && !Double.isFinite(CombatGeometry.firstBlock(
                arena, bot.feet().add(0, PlayerDimensions.EYE_HEIGHT, 0), offset));
    }

    Intent input(long tick, PlayerState bot, PlayerState opponent, VaultStage activeStage, SparringPartner sparring) {
        if (stage != activeStage) selectRoute(activeStage);
        float guardYaw = attacker ? 90 : -90;
        Intent escape = escapeInput(tick, bot, sparring, guardYaw);
        if (escape != null) return escape;
        while (waypoint < route.size() - 1 && reached(bot.feet(), route.get(waypoint))) waypoint++;
        if (opponent != null) {
            Intent combat = sparring.input(tick, bot, opponent);
            if (holder && combat.slot() == 0 && onObjective(bot.feet()) && !onObjective(opponent.feet())) {
                // Hold the zone while facing and trading with an enemy at its edge.
                return new Intent(tick, combat.yaw(), combat.pitch(), 0, 0, false, false,
                        combat.attack(), false, combat.slot());
            }
            return combat;
        }
        return sparring.navigate(tick, bot, route.get(waypoint), waypoint == route.size() - 1, guardYaw);
    }

    private boolean onObjective(Vec3 feet) {
        return stage == VaultStage.VAULT_CORE ? VaultBattlefield.inCoreZone(feet) : VaultBattlefield.inBreachZone(feet);
    }

    private Intent escapeInput(long tick, PlayerState bot, SparringPartner sparring, float guardYaw) {
        Vec3 feet = bot.feet();
        boolean inTrench = bot.grounded() && feet.y() < VaultBattlefield.FLOOR_Y + .5;
        if (escapeRoute.isEmpty() && inTrench) planEscape(feet);
        if (escapeRoute.isEmpty()) return null;

        while (escapeWaypoint < escapeRoute.size() && reached(feet, escapeRoute.get(escapeWaypoint))) {
            escapeWaypoint++;
            escapeBestDistance = Double.POSITIVE_INFINITY;
        }
        if (escapeWaypoint == escapeRoute.size()) {
            Vec3 rally = escapeRoute.get(escapeWaypoint - 1);
            waypoint = attacker ? Math.max(0, route.indexOf(rally)) : 0;
            escapeRoute = List.of();
            return null;
        }

        Vec3 goal = escapeRoute.get(escapeWaypoint);
        double distance = feet.minus(goal).length();
        if (distance < escapeBestDistance - .15) {
            escapeBestDistance = distance;
            escapeProgressTick = tick;
        } else if (inTrench && tick - escapeProgressTick >= 2 * PracticeMatch.TICKS_PER_SECOND) {
            // A new hit can displace the bot into a different trench while it is rejoining its lane.
            planEscape(feet);
            if (escapeRoute.isEmpty()) return null;
            goal = escapeRoute.get(0);
        }
        return sparring.navigate(tick, bot, goal, false, guardYaw);
    }

    private void planEscape(Vec3 feet) {
        escapeRoute = VaultBattlefield.escapeRoute(feet, lane);
        escapeWaypoint = 0;
        escapeBestDistance = Double.POSITIVE_INFINITY;
    }

    private void selectRoute(VaultStage activeStage) {
        boolean core = activeStage == VaultStage.VAULT_CORE;
        List<Vec3> next = attacker ? VaultBattlefield.attackRoute(lane, core)
                : VaultBattlefield.defendRoute(lane, holder, core);
        // Keep reached approach waypoints when the objective advances.
        int shared = 0;
        while (shared < waypoint && shared < next.size() - 1 && route.get(shared).equals(next.get(shared))) shared++;
        // A fighter can be off the approach in a capture zone. Rejoin it before crossing the next gate.
        if (shared > 0 && !route.get(shared).equals(next.get(shared))) shared--;
        route = next;
        waypoint = shared;
        stage = activeStage;
    }

    private static boolean reached(Vec3 feet, Vec3 goal) {
        Vec3 delta = goal.minus(feet);
        return Math.abs(delta.y()) < .8 && Math.hypot(delta.x(), delta.z()) < .8;
    }
}
