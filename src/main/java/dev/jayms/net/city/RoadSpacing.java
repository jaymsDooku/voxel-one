package dev.jayms.net.city;

/** Centre-to-centre public-road clearance, in blocks. Read at simulation startup. */
public record RoadSpacing(float pedestrians, float mounted) {
    public RoadSpacing {
        validate(pedestrians);
        validate(mounted);
    }

    private static void validate(float value) {
        if (!Float.isFinite(value) || value < 0 || value > 2)
            throw new IllegalArgumentException("Road spacing must be finite and between 0 and 2 blocks");
    }

    public static RoadSpacing configured() {
        return new RoadSpacing(
                Float.parseFloat(System.getProperty("voxel.road.pedestrianSpacing", "0.8")),
                Float.parseFloat(System.getProperty("voxel.road.mountedSpacing", "1.4")));
    }

    public static void configure(String option, String value) {
        float parsed = Float.parseFloat(value);
        validate(parsed);
        System.setProperty(option.equals("--pedestrian-spacing")
                ? "voxel.road.pedestrianSpacing" : "voxel.road.mountedSpacing", value);
    }
}
