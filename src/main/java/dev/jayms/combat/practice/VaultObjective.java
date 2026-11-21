package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.arena.VaultBattlefield;
import dev.jayms.combat.practice.PracticeMatch.Phase;
import dev.jayms.combat.practice.PracticeMatch.Result;
import dev.jayms.combat.practice.PracticeMatch.VaultStage;

import java.util.List;

import static dev.jayms.combat.practice.PracticeMatch.TICKS_PER_SECOND;
import static dev.jayms.combat.practice.PracticeMatch.VaultStage.*;

/** Tracks the two siege objectives against the server's simulation ticks. */
final class VaultObjective {
    static final int DEFENDERS = 0, ATTACKERS = 1;
    static final int RING_SECONDS = 5, CORE_SECONDS = 8;
    private static final String RING_LABEL = "Outer Ring";
    private static final String CORE_LABEL = "Vault Core";
    private static final String CAPTURED_LABEL = "Vault Captured";
    private static final String INSTRUCTIONS = "Attackers hold the outer ring, then the core. Defenders contest.";
    private enum Occupancy { VACANT, CONTESTED, SECURING }

    private VaultStage stage = OUTER_RING;
    private Occupancy occupancy = Occupancy.VACANT;
    private int held;
    private List<String> fightStatus;

    VaultStage stage() { return stage; }

    boolean inActiveZone(Vec3 feet) {
        return switch (stage) {
            case OUTER_RING -> VaultBattlefield.inBreachZone(feet);
            case VAULT_CORE -> VaultBattlefield.inCoreZone(feet);
            case CAPTURED -> false;
        };
    }

    /** Returns true when attackers secure the core. Vacant and contested ticks preserve progress. */
    boolean advance(boolean attackersPresent, boolean defendersPresent) {
        if (stage == CAPTURED) return false;
        Occupancy next = !attackersPresent ? Occupancy.VACANT
                : defendersPresent ? Occupancy.CONTESTED : Occupancy.SECURING;
        if (occupancy != next) fightStatus = null;
        occupancy = next;
        if (occupancy != Occupancy.SECURING) return false;

        if (++held % (TICKS_PER_SECOND / 10) == 0) fightStatus = null;
        if (held < secondsRequired() * TICKS_PER_SECOND) return false;
        if (stage == VAULT_CORE) {
            stage = CAPTURED;
            return true;
        }
        stage = VAULT_CORE;
        occupancy = Occupancy.VACANT;
        held = 0;
        fightStatus = null;
        return false;
    }

    void reset() {
        stage = OUTER_RING;
        occupancy = Occupancy.VACANT;
        held = 0;
        fightStatus = null;
    }

    private int secondsRequired() { return stage == OUTER_RING ? RING_SECONDS : CORE_SECONDS; }

    List<String> status(Phase phase, Result result) {
        return switch (phase) {
            case WAITING, COUNTDOWN -> List.of(RING_LABEL, INSTRUCTIONS, "Defenders win if time expires.");
            case FIGHT -> fightStatus();
            case RESULT -> List.of(label(), switch (result) {
                case OBJECTIVE -> "Attackers secured the ring and captured the vault core.";
                case TIMEOUT -> "Defenders held the vault until time expired.";
                case FORFEIT -> "A team withdrew from the siege.";
                case KNOCKOUT -> "The siege ended by team elimination.";
                default -> "The siege ended.";
            });
        };
    }

    private List<String> fightStatus() {
        if (fightStatus != null) return fightStatus;
        int tenths = held * 10 / TICKS_PER_SECOND;
        String action = switch (occupancy) {
            case VACANT -> "Waiting for attackers";
            case CONTESTED -> "Contested";
            case SECURING -> "Securing";
        };
        String progress = action + " - " + tenths / 10 + "." + tenths % 10 + " / " + secondsRequired() + " s";
        String next = stage == OUTER_RING ? "Secure the ring to activate the vault core." : "Outer ring secured.";
        fightStatus = List.of(label(), progress, next);
        return fightStatus;
    }

    private String label() {
        return switch (stage) {
            case OUTER_RING -> RING_LABEL;
            case VAULT_CORE -> CORE_LABEL;
            case CAPTURED -> CAPTURED_LABEL;
        };
    }

}
