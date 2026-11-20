package dev.jayms.player;

/** Standing dimensions in blocks, using the Paper 1.7.10 player bounds and eye height. */
public final class PlayerDimensions {
    public static final double WIDTH = .60;
    public static final double RADIUS = WIDTH / 2;
    public static final double HEIGHT = 1.80;
    public static final double EYE_HEIGHT = 1.62;
    public static final float NAMEPLATE_HEIGHT = (float) HEIGHT + .13f;

    private PlayerDimensions() {}
}
