package dev.jayms.net.structure;

import dev.jayms.net.Protocol;
import dev.jayms.net.city.CityFrame;
import dev.jayms.net.city.CityOccupancy;
import dev.jayms.net.model.SparseVoxelOctree;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/** Live commit-time body reservations. Conservative whole-cell policy, including models. */
public final class StructureOccupancy {
    public static void validate(Map<StructurePackage.Position, SparseVoxelOctree> cells,
                                CityFrame city, Collection<Protocol.Pose> players) throws IOException {
        for (var entry : cells.entrySet()) {
            if (entry.getValue().occupied() == 0) continue;
            var p = entry.getKey();
            if (city != null && CityOccupancy.overlaps(city, p.x(), p.y(), p.z(), 1, 1, 1))
                throw new IOException("Structure would overlap a city entity; nothing changed");
            for (var player : players)
                if (player.x() + .3 > p.x() && player.x() - .3 < (double)p.x() + 1
                        && player.y() + 1.8 > p.y() && player.y() < (double)p.y() + 1
                        && player.z() + .3 > p.z() && player.z() - .3 < (double)p.z() + 1)
                    throw new IOException("Structure would overlap a player; nothing changed");
        }
    }
    private StructureOccupancy() {}
}
