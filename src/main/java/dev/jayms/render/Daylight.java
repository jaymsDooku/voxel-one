package dev.jayms.render;

import dev.jayms.net.city.GameConfig;

import org.joml.Vector3f;

/** Continuous solar clock with twilight and a dim moonlit night. */
public record Daylight(Vector3f sun, float intensity, float ambient) {
    public static Daylight at(GameConfig config, double elapsed) {
        double angle = (config.hour(elapsed) - 6) / 24 * Math.PI * 2;
        float height = (float) Math.sin(angle);
        Vector3f sun = new Vector3f((float) Math.cos(angle), height, -.35f).normalize();
        float strength = Math.max(0, Math.min(1, (height + .05f) * 1.6f));
        return new Daylight(
                sun, strength, .06f + .94f * Math.max(0, Math.min(1, (height + .18f) * 2)));
    }
}
