package dev.jayms.player;

/** Daily-life gestures layered over the shared player rig without affecting actual players. */
public final class CitizenAnimation {
    public static boolean sleeping(String activity) {
        return activity.equals("Sleeping at home");
    }

    public static PlayerAnimation.Pose pose(
            PlayerAnimation.Pose gait, String activity, double seconds, int id) {
        float wave = (float) Math.sin(seconds * 3 + id * .7);
        if (sleeping(activity)) return new PlayerAnimation.Pose(.1f, .1f, 0, 0, 0, 0);
        if (activity.equals("Working in mine")
                || activity.startsWith("Working: ")
                || activity.equals("Building for developer"))
            return new PlayerAnimation.Pose(.2f, .9f + wave * .65f, 0, 0, wave * .08f, 0);
        if (activity.equals("Working in shop"))
            return new PlayerAnimation.Pose(.3f - wave * .15f, .3f + wave * .15f, 0, 0, 0, 0);
        if (activity.equals("Eating at shop"))
            return new PlayerAnimation.Pose(.25f, 1.6f + wave * .12f, 0, 0, 0, 0);
        return gait;
    }

    private CitizenAnimation() {}
}
