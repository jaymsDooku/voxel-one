package dev.jayms.player;

/** Parked body choices share the vehicle's driving, collision and save workflow. */
public enum CargoVehicle {
    JEEP("Jeep", 1.1f, 1.9f, 2.5f),
    CONTAINER("Container truck", 1.3f, 5.2f, 3.7f),
    TANKER("Liquid tanker", 1.3f, 4.6f, 3.5f),
    VAN("Delivery van", 1.05f, 2.5f, 2.8f),
    LORRY("Goods lorry", 1.2f, 3.4f, 3.3f);

    public final String label;
    public final float halfWidth, halfLength, height;
    CargoVehicle(String label, float halfWidth, float halfLength, float height) {
        this.label=label; this.halfWidth=halfWidth; this.halfLength=halfLength; this.height=height;
    }
}
