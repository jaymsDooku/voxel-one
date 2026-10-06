package dev.jayms.net.city;

/** Road choices: one voxel per lane, with a painted divider between lanes. */
public final class RoadTypes {
    public static final String[] NAMES = {"Dirt road", "Paved road - 2 lanes", "Paved road - 3 lanes", "Paved road - 4 lanes"};
    public static void validate(int type) {
        if (type < 0 || type >= NAMES.length) throw new IllegalArgumentException("Invalid road type");
    }
    public static int width(int type) { validate(type); return type == 0 ? 3 : 2 * (type + 1) - 1; }
    private RoadTypes() {}
}
