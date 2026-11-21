package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Intent;
import dev.jayms.combat.practice.ArenaMatch;
import dev.jayms.combat.practice.PracticeMatch;
import dev.jayms.combat.practice.PracticeMatch.View;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Observe the click batch consumed by each server step without changing arbitration. */
final class MeasuredPracticeMatch implements ArenaMatch {
    private final PracticeMatch match = new PracticeMatch(PracticeMatch.Kit.NO_DEBUFF, false);
    private final List<Intent> clicks = new ArrayList<>();
    private final StringBuilder hits = new StringBuilder("tick,attacker,target,sequence,candidates\n");
    private int primary = -1, opponent = -1;

    public synchronized int join(String name) {
        int id = match.join(name);
        if (name.equals("Aster")) primary = id;
        if (name.equals("Rowan")) opponent = id;
        return id;
    }
    public void leave(int id) { match.leave(id); }
    public void ready(int id) { match.ready(id); }
    public boolean swap(int id, int first, int second) { return match.swap(id, first, second); }
    public boolean quickMove(int id, int source) { return match.quickMove(id, source); }
    public View view(int id) { return match.view(id); }

    public synchronized void input(int id, Intent intent) {
        match.input(id, intent);
        if (id == primary && intent.attack()) clicks.add(intent);
    }

    public synchronized void tick() {
        // Every melee intent is drained by this next tick, including misses.
        if (clicks.isEmpty()) {
            match.tick();
            return;
        }
        var before = NativeFrameTrace.actor(view(primary), primary);
        match.tick();
        var after = view(primary);
        var actor = NativeFrameTrace.actor(after, primary);
        var target = NativeFrameTrace.actor(after, opponent);
        if (before != null && actor != null && target != null && actor.hits() > before.hits()) {
            var click = clicks.get(0);
            long sequence = clicks.size() == 1 && actor.hits() == before.hits() + 1 && target.hurtTick() == after.tick()
                    && click.attackView() != null && click.attackView().targetId() == opponent ? click.sequence() : -1;
            hits.append(after.tick()).append(',').append(primary).append(',').append(opponent).append(',')
                    .append(sequence).append(',').append(clicks.size()).append('\n');
        }
        clicks.clear();
    }

    synchronized void save(Path output) throws IOException {
        Files.writeString(output.resolve("acceptedClicks.csv"), hits);
    }
}
