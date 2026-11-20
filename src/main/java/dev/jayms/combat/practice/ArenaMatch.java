package dev.jayms.combat.practice;

import dev.jayms.combat.CombatEngine.Intent;

/** A server-owned arena session with one clock and bounded player intents. */
public interface ArenaMatch {
    int join(String name);
    void leave(int id);
    void ready(int id);
    boolean swap(int id, int first, int second);
    boolean quickMove(int id, int source);
    void input(int id, Intent intent);
    void tick();
    PracticeMatch.View view(int id);
    default void action(int id, int action) { }
}
