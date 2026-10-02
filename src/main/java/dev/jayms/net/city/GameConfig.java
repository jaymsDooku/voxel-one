package dev.jayms.net.city;

import java.io.*;

/** Game-owned clock configuration. Sandbox keeps its familiar fixed daylight. */
public record GameConfig(boolean city, boolean cycle, double daySeconds, double startHour) {
    public static GameConfig sandbox() {
        return new GameConfig(false, false, 1200, 10);
    }

    public static GameConfig cityGame() {
        return new GameConfig(true, true, 1200, 8);
    }

    public GameConfig {
        if (!Double.isFinite(daySeconds)
                || daySeconds < 60
                || daySeconds > 86400
                || !Double.isFinite(startHour)
                || startHour < 0
                || startHour >= 24)
            throw new IllegalArgumentException("Invalid day/night settings");
    }

    public double hour(double elapsed) {
        return (startHour + (cycle ? elapsed * 24 / daySeconds : 0)) % 24;
    }

    public void write(DataOutput out) throws IOException {
        out.writeBoolean(city);
        out.writeBoolean(cycle);
        out.writeDouble(daySeconds);
        out.writeDouble(startHour);
    }

    public static GameConfig read(DataInput in) throws IOException {
        try {
            return new GameConfig(
                    in.readBoolean(), in.readBoolean(), in.readDouble(), in.readDouble());
        } catch (IllegalArgumentException e) {
            throw new IOException(e);
        }
    }
}
