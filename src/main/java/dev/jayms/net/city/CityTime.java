package dev.jayms.net.city;

import java.util.Locale;

/** Calendar and daily schedule derived from the saved, authoritative simulation clock. */
public record CityTime(long day, double hour) {
    public enum Period {
        MORNING("Morning"),
        WORKDAY("Workday"),
        EVENING("Evening"),
        NIGHT("Night");
        public final String label;

        Period(String label) {
            this.label = label;
        }
    }

    public static CityTime at(GameConfig config, double elapsed) {
        double hours =
                config.startHour() + (config.cycle() ? elapsed * 24 / config.daySeconds() : 0);
        return new CityTime(1 + (long) Math.floor(hours / 24), hours % 24);
    }

    public Period period() {
        return hour < 6 || hour >= 22
                ? Period.NIGHT
                : hour < 8 ? Period.MORNING : hour < 17 ? Period.WORKDAY : Period.EVENING;
    }

    public boolean shopsOpen() {
        return hour >= 6 && hour < 22;
    }

    public String label() {
        return String.format(
                Locale.ROOT,
                "Day %d | %02d:%02d | %s",
                day,
                (int) hour,
                (int) (hour % 1 * 60),
                period().label);
    }
}
