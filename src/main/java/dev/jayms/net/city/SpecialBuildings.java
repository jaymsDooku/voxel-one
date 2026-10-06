package dev.jayms.net.city;

/** Permit catalog. Types 4..18 and 20..22 encode kind and level; 19 is the exchange. For these buildings only,
 * zone stores ownership (0 city, -1 citizen, -2 company), and stock stores owner ID.
 * Airport 23 is city-owned; capacity stores eight times the runway count.
 * Keeping this metadata in the bounded building record preserves snapshot layout.
 */
public final class SpecialBuildings {
    public static final int EXCHANGE = 19, AIRPORT = 23;
    public static final String[] NAMES = {"Administration", "Primary school", "Secondary school", "University", "Police station", "Technical college"};
    public static boolean special(int type) { return type >= 4 && type <= 23; }
    public static int type(int kind, int level) {
        if (kind < 0 || kind >= 6 || level < 1 || level > 3) throw new IllegalArgumentException("Invalid building or level");
        return kind == 5 ? 19 + level : 4 + kind * 3 + level - 1;
    }
    public static int kind(int type) { return type == AIRPORT ? 6 : type >= 20 ? 5 : (type - 4) / 3; }
    public static int level(int type) { if (type == EXCHANGE || type == AIRPORT) return 1; return type >= 20 ? type - 19 : (type - 4) % 3 + 1; }
    public static String name(int type) {
        if (type == EXCHANGE) return "Stock exchange";
        if (type == AIRPORT) return "Airport";
        if (!special(type)) throw new IllegalArgumentException("Invalid special building");
        if ((type - 4) / 3 == 0) return new String[]{"Parish hall", "Town hall", "City hall"}[level(type)-1];
        return NAMES[kind(type)] + " level " + level(type);
    }
    private SpecialBuildings() {}
}
