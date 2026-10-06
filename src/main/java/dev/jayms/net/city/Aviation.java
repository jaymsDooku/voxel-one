package dev.jayms.net.city;

import dev.jayms.net.*;
import java.io.*;
import java.util.*;

/** Bounded airport traffic. A booked jet reserves a runway at both ends until arrival. */
public final class Aviation {
    public static final int WIDTH = 36, MAX_RUNWAYS = 3, MAX_AIRPORTS = 8, RUNWAY_SPACING = 16;
    public static final double AIRPORT_COST = 2000, RUNWAY_COST = 1000;
    public static int depth(int runways) { return 10 + RUNWAY_SPACING * runways; }
    public static int runways(CityFrame.Building b) { return b.capacity() / 8; }
    public record Flight(int id, int origin, int destination, int citizen, int originRunway, int destinationRunway, int stage, double clock) {
        public Flight stage(int value) { return new Flight(id, origin, destination, citizen, originRunway, destinationRunway, value, 0); }
        public Flight tick(double dt) { return new Flight(id, origin, destination, citizen, originRunway, destinationRunway, stage, clock + dt); }
    }
    public record State(List<Flight> flights) {
        public State { flights = List.copyOf(flights); }
        public static State empty() { return new State(List.of()); }
    }
    public record Plane(int id, float x, float y, float z, float yaw, float pitch, boolean airborne) {}
    public static double duration(CityFrame.Building a, CityFrame.Building b) {
        return 12 + Math.hypot(a.x() - b.x(), a.z() - b.z()) / 12;
    }
    public static List<Plane> planes(CityFrame frame) {
        var result = new ArrayList<Plane>();
        for (var f : frame.aviation().flights()) {
            var a = frame.buildings().stream().filter(b -> b.id() == f.origin()).findFirst().orElse(null);
            var b = frame.buildings().stream().filter(v -> v.id() == f.destination()).findFirst().orElse(null);
            if (a == null || b == null) continue;
            double t = f.stage() < 2 ? 0 : f.stage() == 3 ? 1 : Math.min(1, f.clock() / duration(a, b));
            var p = FlightPath.sample(a.x() + 8, a.y() + 1, a.z() + 18 + RUNWAY_SPACING * f.originRunway(),
                    b.x() + 28, b.y() + 1, b.z() + 18 + RUNWAY_SPACING * f.destinationRunway(), t);
            boolean airborne = f.stage() == 2;
            result.add(new Plane(f.id(), p.x(), p.y(), p.z(), airborne ? p.yaw() : 0,
                    airborne ? p.pitch() : 0, airborne));
        }
        // Park an idle passenger jet at each airport with no departing traffic.
        for (var b : frame.buildings()) if (b.type() == SpecialBuildings.AIRPORT
                && frame.aviation().flights().stream().noneMatch(f -> f.origin() == b.id() || f.destination() == b.id()))
            result.add(new Plane(-b.id(), b.x() + 10, b.y() + 1, b.z() + 18, 0, 0, false));
        return List.copyOf(result);
    }
    public static List<Protocol.Edit> blueprint(int x, int y, int z, int runways) {
        var edits = new ArrayList<Protocol.Edit>();
        // Terminal: standard walkable central aisle and glass departures hall.
        edits.addAll(StructureBlueprint.generate(1, x, y, z));
        for (int dx = 6; dx < 14; dx++) for (int dz = 0; dz < 7; dz++) {
            edits.add(new Protocol.Edit(x + dx, y, z + dz, Blocks.STONE));
            for (int dy = 1; dy <= 4; dy++) {
                int block = dy == 4 ? Blocks.STONE : dx == 13 || dz == 0 || dz == 6 ? Blocks.GLASS : 0;
                edits.add(new Protocol.Edit(x + dx, y + dy, z + dz, block));
            }
        }
        // Control tower overlooks the apron.
        for (int dx = 17; dx < 20; dx++) for (int dz = 1; dz < 4; dz++)
            for (int dy = 0; dy <= 8; dy++)
                edits.add(new Protocol.Edit(x + dx, y + dy, z + dz,
                        dy == 7 ? Blocks.GLASS : Blocks.STONE));
        for (int dx = 0; dx < WIDTH; dx++) for (int dz = 7; dz < depth(runways); dz++)
            edits.add(new Protocol.Edit(x + dx, y, z + dz, Blocks.STONE));
        for (int r = 0; r < runways; r++) {
            int start = 12 + r * RUNWAY_SPACING;
            for (int dx = 1; dx < WIDTH - 1; dx++) for (int dz = 0; dz < 12; dz++) {
                int block = (dz == 6 && dx % 4 < 2 || (dx < 4 || dx > WIDTH - 5) && dz % 2 == 1)
                        ? Blocks.PLANKS : Blocks.STONE;
                edits.add(new Protocol.Edit(x + dx, y, z + start + dz, block));
            }
            for (int dx = 2; dx < WIDTH; dx += 6) for (int dz : new int[]{start, start + 11})
                edits.add(Protocol.Edit.at(x + dx, y + 1, z + dz, Blocks.piece(Blocks.LED, 2), 2).withColor(0x75beff));
        }
        return edits;
    }
    public static void write(DataOutput out, State state) throws IOException {
        out.writeInt(state.flights().size());
        for (var f : state.flights()) {
            out.writeInt(f.id()); out.writeInt(f.origin()); out.writeInt(f.destination());
            out.writeInt(f.citizen()); out.writeByte(f.originRunway()); out.writeByte(f.destinationRunway()); out.writeByte(f.stage()); out.writeDouble(f.clock());
        }
    }
    public static State read(DataInput in, List<CityFrame.Building> buildings, List<CityFrame.Citizen> citizens) throws IOException {
        int n = in.readInt(); if (n < 0 || n > MAX_AIRPORTS * MAX_RUNWAYS) throw new IOException("Invalid flight count");
        var flights = new ArrayList<Flight>(); var people = new HashSet<Integer>(); var ids = new HashSet<Integer>();
        for (int i = 0; i < n; i++) {
            var f = new Flight(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte(), in.readDouble());
            if (f.id() < 1 || !ids.add(f.id()) || !people.add(f.citizen()) || f.origin() == f.destination()
                    || f.stage() > 3 || !Double.isFinite(f.clock()) || f.clock() < 0 || f.clock() > 180
                    || citizens.stream().noneMatch(c -> c.id() == f.citizen())) throw new IOException("Invalid flight");
            for (int id : new int[]{f.origin(), f.destination()})
                if (buildings.stream().noneMatch(b -> b.id() == id && b.type() == SpecialBuildings.AIRPORT)) throw new IOException("Missing airport");
            flights.add(f);
        }
        var lanes = new HashSet<String>();
        for (var f : flights) {
            for (int endpoint : new int[]{0,1}) {
                int airport = endpoint == 0 ? f.origin() : f.destination();
                int lane = endpoint == 0 ? f.originRunway() : f.destinationRunway();
                var b = buildings.stream().filter(v -> v.id() == airport).findFirst().orElseThrow();
                if (lane >= runways(b) || !lanes.add(airport + ":" + lane)) throw new IOException("Invalid reserved runway");
            }
        }
        for (var b : buildings) if (b.type() == SpecialBuildings.AIRPORT && flights.stream()
                .filter(f -> f.origin() == b.id() || f.destination() == b.id()).count() > runways(b))
            throw new IOException("Airport traffic exceeds runway capacity");
        return new State(flights);
    }
    private Aviation() {}
}
