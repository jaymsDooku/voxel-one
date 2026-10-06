package dev.jayms.net.city;

import dev.jayms.net.Protocol;

/** Construction and inventory edits reserve bodies of citizens and horses. */
public final class CityOccupancy {
    public static boolean overlaps(CityFrame frame, Protocol.Edit edit) {
        return overlaps(
                frame,
                edit.minX(),
                edit.minY(),
                edit.minZ(),
                edit.size(),
                edit.size(),
                edit.size());
    }

    public static boolean overlaps(
            CityFrame frame, float x, float y, float z, float width, float height, float depth) {
        for (var c : frame.visibleCitizens())
            if (x + width > c.x() - .3f
                    && x < c.x() + .3f
                    && z + depth > c.z() - .3f
                    && z < c.z() + .3f
                    && y + height > c.y()
                    && y < c.y() + 1.8f) return true;
        for (var h : frame.horses())
            if (x + width > h.x() - .7f
                    && x < h.x() + .7f
                    && z + depth > h.z() - 1.2f
                    && z < h.z() + 1.2f
                    && y + height > h.y()
                    && y < h.y() + 2.3f) return true;
        for (var c : frame.agriculture().cows())
            if (x + width > c.x() - .5f
                    && x < c.x() + .5f
                    && z + depth > c.z() - .8f
                    && z < c.z() + .8f
                    && y + height > c.y()
                    && y < c.y() + 1.8f) return true;
        return false;
    }

    private CityOccupancy() {}
}
