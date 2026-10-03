package dev.jayms.net.city;

/** Permit catalog. Types 4..18 encode kind and level. For these buildings only,
 * zone stores ownership (0 city, -1 citizen, -2 company), and stock stores owner ID.
 * Keeping this metadata in the bounded building record preserves snapshot layout.
 */
public final class SpecialBuildings {
    public static final String[] NAMES = {"Administration", "Primary school", "Secondary school", "University", "Police station"};
    public static boolean special(int type) { return type >= 4 && type <= 18; }
    public static int type(int kind, int level) {
        if (kind < 0 || kind >= 5 || level < 1 || level > 3) throw new IllegalArgumentException("Invalid building or level");
        return 4 + kind * 3 + level - 1;
    }
    public static int kind(int type) { return (type - 4) / 3; }
    public static int level(int type) { return (type - 4) % 3 + 1; }
    public static String name(int type) {
        if (!special(type)) throw new IllegalArgumentException("Invalid special building");
        if ((type - 4) / 3 == 0) return new String[]{"Parish hall", "Town hall", "City hall"}[level(type)-1];
        return NAMES[(type - 4) / 3] + " level " + level(type);
    }
    private SpecialBuildings() {}
}
