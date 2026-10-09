package dev.jayms.net.city;

import java.io.*;
import dev.jayms.net.atmosphere.AtmosphereConfig;

/** Game-owned clock configuration. Sandbox keeps its familiar fixed daylight. */
public record GameConfig(boolean city, boolean cycle, double daySeconds, double startHour, AtmosphereConfig atmosphere) {
    public GameConfig(boolean city, boolean cycle, double daySeconds, double startHour) {
        this(city,cycle,daySeconds,startHour,AtmosphereConfig.earth());
    }
    public static GameConfig sandbox() {
        return new GameConfig(false, false, 1200, 10);
    }

    public static GameConfig cityGame() {
        return new GameConfig(true, true, 1200, 8);
    }

    public GameConfig {
        java.util.Objects.requireNonNull(atmosphere);
        if (!Double.isFinite(daySeconds)
                || daySeconds < 60
                || daySeconds > 86400
                || !Double.isFinite(startHour)
                || startHour < 0
                || startHour >= 24)
            throw new IllegalArgumentException("Invalid day/night settings");
    }

    public double hour(double elapsed) {
        return time(elapsed).hour();
    }

    public CityTime time(double elapsed) {
        return CityTime.at(this, elapsed);
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

    /** Only the versioned city envelope may add profile bytes. Legacy clock bytes stay unchanged. */
    public void write(DataOutput out,int cityVersion)throws IOException {
        if(cityVersion<17 && !atmosphere.equals(AtmosphereConfig.earth()))
            throw new IOException("Custom atmosphere requires city snapshot version 17");
        write(out);
        if(cityVersion>=17)atmosphere.write(out);
    }
    public static GameConfig read(DataInput in,int cityVersion)throws IOException {
        GameConfig clock=read(in);
        return cityVersion<17 ? clock : new GameConfig(clock.city,clock.cycle,clock.daySeconds,clock.startHour,AtmosphereConfig.read(in));
    }
}
