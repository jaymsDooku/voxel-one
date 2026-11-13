package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.structure.StructurePackage.Position;
import dev.jayms.ui.StructureMenu;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StructureMenuAnchorTest {
    @TempDir Path directory;
    @Test void invalidCoordinatesRetainTargetAndDoNotStartPreview() {
        try(var menu=new StructureMenu(directory)) {
            menu.execute("at 20 128 26",null,null,null);var before=menu.target;
            for(var text:new String[]{"at -2147483648 128 0","at 2147483647 128 0",
                    "at 0 128 -2147483648","at 0 128 2147483647",
                    "at 0 "+(Protocol.MIN_BUILD_Y-1)+" 0","at 0 "+(Protocol.MAX_BUILD_Y+1)+" 0",
                    "at "+(Terrain.LIMIT+1)+" 128 0","at 0 128 "+(-Terrain.LIMIT-1)}) {
                menu.execute(text,null,null,null);
                assertEquals(before,menu.target);assertFalse(menu.busy);
                assertEquals("Placement anchor outside world",menu.message);
            }
        }
    }
    @Test void validWorldEdgesAndNegativeCoordinatesRemainAccepted() {
        try(var menu=new StructureMenu(directory)) {
            for(var point:new Position[]{new Position(-Terrain.LIMIT,Protocol.MIN_BUILD_Y,Terrain.LIMIT),
                    new Position(Terrain.LIMIT,Protocol.MAX_BUILD_Y,-Terrain.LIMIT),new Position(-20,128,-26)}) {
                menu.execute("at "+point.x()+" "+point.y()+" "+point.z(),null,null,null);
                assertEquals(point,menu.target);assertFalse(menu.busy);
            }
        }
    }
}
