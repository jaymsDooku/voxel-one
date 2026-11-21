package dev.jayms.combat.client;

import dev.jayms.combat.CombatEngine.Item;
import dev.jayms.combat.CombatEngine.Vec3;
import dev.jayms.combat.practice.PracticeMatch.Fighter;
import dev.jayms.combat.practice.PracticeMatch.Phase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Re-samples a native Practice trace on its original snapshot and render clocks. */
public final class ArenaFrameReplay {
    private ArenaFrameReplay() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Expected input and output CSV paths");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
                || Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("Choose a separate output file for the replay");
        try (var reader = Files.newBufferedReader(input); var writer = Files.newBufferedWriter(output)) {
            String header = reader.readLine();
            if (header == null) throw new IOException("Empty arena frame trace");
            List<String> names = List.of(header.split(",", -1));
            Map<String, Integer> fields = new HashMap<>();
            for (int i = 0; i < names.size(); i++) fields.put(names.get(i), i);
            writer.write(header);
            writer.newLine();
            var timeline = new ArenaMotionTimeline();
            Phase previous = null;
            String line;
            while ((line = reader.readLine()) != null) {
                Row row = new Row(fields, line.split(",", -1));
                long now = row.integer("sampleNs"), tick = row.integer("tick");
                Phase phase = Phase.valueOf(row.get("phase"));
                Fighter local = row.fighter("local"), remote = row.fighter("remote");
                var actors = new ArrayList<Fighter>(2);
                if (local != null) actors.add(local);
                if (remote != null) actors.add(remote);
                timeline.observe(tick, actors, now);
                var frame = timeline.frame(now);
                if (local != null) {
                    boolean respawn = previous == null || phase == Phase.COUNTDOWN && previous != phase;
                    var shown = respawn ? null : timeline.sampleLocal(local.id(), frame.local(), now,
                            false, phase == Phase.FIGHT);
                    Vec3 feet = new Vec3(local.x(), local.y(), local.z());
                    Vec3 displayed = shown == null ? feet : shown.point();
                    row.point("localDisplay", displayed);
                    row.point("localConfirmed", shown == null ? feet : shown.confirmedPoint());
                }
                if (remote != null && !row.get("renderNs").isEmpty()) {
                    var shown = timeline.sample(remote.id(), frame.remote());
                    boolean visible = shown != null && shown.visible();
                    row.point("remoteDisplay", visible ? new Vec3(shown.x(), shown.y(), shown.z()) : null);
                    row.put("remoteTint", visible ? Float.toString(shown.hurt()) : "");
                    row.put("remoteSwingProgress", visible ? Float.toString(shown.swingProgress()) : "");
                }
                writer.write(String.join(",", row.values));
                writer.newLine();
                previous = phase;
            }
        }
    }

    private record Row(Map<String, Integer> fields, String[] values) {
        String get(String name) { return values[fields.get(name)]; }
        long integer(String name) { return Long.parseLong(get(name)); }
        float number(String name) { return Float.parseFloat(get(name)); }
        void put(String name, String value) { values[fields.get(name)] = value; }

        void point(String prefix, Vec3 point) {
            put(prefix + "X", point == null ? "" : Double.toString(point.x()));
            put(prefix + "Y", point == null ? "" : Double.toString(point.y()));
            put(prefix + "Z", point == null ? "" : Double.toString(point.z()));
        }

        Fighter fighter(String p) {
            if (get(p + "Id").isEmpty()) return null;
            float vx = number(p + "Vx"), vy = number(p + "Vy"), vz = number(p + "Vz");
            String swing = get(p + "Swinging");
            return new Fighter((int) integer(p + "Id"), p, number(p + "X"), number(p + "Y"), number(p + "Z"),
                    number(p + "Yaw"), number(p + "Pitch"), number(p + "Health"), Math.hypot(vx, vz) > .12,
                    swing.equals("1") || Boolean.parseBoolean(swing), true, Item.SWORD, (int) integer(p + "Hits"),
                    0, 0, 0, -1, "", vx, vy, vz, integer(p + "HurtTick"), integer(p + "MotionEpoch"),
                    integer(p + "WallTick"), integer(p + "ImpulseTick"), number(p + "ImpulseX"),
                    number(p + "ImpulseY"), number(p + "ImpulseZ"),
                    fields.containsKey(p + "SwingTick") ? integer(p + "SwingTick") : -1);
        }
    }
}
