package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine;
import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.practice.PracticeMatch.Missile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Interpolates confirmed flights and stops at the confirmed block impact. */
final class PracticeArrowTimeline {
    private static final long TICK_NANOS = 50_000_000;

    record Arrow(float x, float y, float z, float vx, float vy, float vz, boolean critical, boolean embedded,
                 boolean explosive, float blastProgress) {}

    private record Flight(Missile before, Missile after, long blastTick) {
        Arrow at(float fraction, long tick) {
            var to = after;
            var from = before;
            if (to.blast()) {
                float progress = Math.min(1, (tick - blastTick + fraction) / CombatEngine.ARROW_BLAST_TICKS);
                return new Arrow(to.x(), to.y(), to.z(), 0, 0, 0, false, true, true, progress);
            }
            float vx = to.vx(), vy = to.vy(), vz = to.vz();
            // Older servers report only positions. They can still show the observed direction of flight.
            if (vx == 0 && vy == 0 && vz == 0) {
                vx = to.x() - from.x(); vy = to.y() - from.y(); vz = to.z() - from.z();
            }
            return new Arrow(
                    from.x() + (to.x() - from.x()) * fraction,
                    from.y() + (to.y() - from.y()) * fraction,
                    from.z() + (to.z() - from.z()) * fraction,
                    vx, vy, vz, to.critical(), to.embedded(), to.item() == Item.EXPLOSIVE_ARROW, -1);
        }
    }

    private Map<Long, Flight> active = Map.of();
    private long tick = -1, receivedAt;

    void observe(long snapshotTick, List<Missile> missiles, long now) {
        if (snapshotTick == tick) return;
        boolean adjacent = snapshotTick == tick + 1;
        Map<Long, Flight> next = new HashMap<>();
        for (var missile : missiles) {
            if (!missile.item().isArrow()) continue;
            Flight previous = active.get(missile.id());
            long blastTick = missile.blast() && previous != null && previous.after().blast()
                    ? previous.blastTick() : snapshotTick;
            Missile before = adjacent && previous != null ? previous.after() : missile;
            next.put(missile.id(), new Flight(before, missile, blastTick));
        }
        active = next;
        tick = snapshotTick;
        receivedAt = now;
    }

    List<Arrow> sample(long now) {
        float fraction = Math.max(0, Math.min(1, (now - receivedAt) / (float) TICK_NANOS));
        return active.isEmpty() ? List.of() : active.values().stream().map(flight -> flight.at(fraction, tick)).toList();
    }
}
