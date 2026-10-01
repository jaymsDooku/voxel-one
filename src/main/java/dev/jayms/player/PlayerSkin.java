package dev.jayms.player;

import static dev.jayms.player.PlayerAnimation.*;

/** The outer skin layer extends half a skin pixel beyond the base head. */
public final class PlayerSkin {
    public static final float OUTER_INFLATION = PIXEL / 2;
    public static final float HAIR_WIDTH = HEAD + 2 * OUTER_INFLATION;
    public static final float HAIR_BOTTOM = 6 * PIXEL;
    public static final float HAIR_HEIGHT = 2 * PIXEL + OUTER_INFLATION;

    private PlayerSkin() {}
}
