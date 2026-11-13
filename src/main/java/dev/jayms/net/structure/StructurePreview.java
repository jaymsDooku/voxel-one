package dev.jayms.net.structure;

import dev.jayms.net.Protocol;
import java.io.IOException;
import java.util.function.*;
import static dev.jayms.net.structure.StructurePackage.*;

/** Worker-only conflict query. Reads occupancy, never source-package geometry. */
public final class StructurePreview {
    private StructurePreview(){}
    public static boolean blocked(StructurePackage asset,Position anchor,int rotation,Source snapshot,
                                  BooleanSupplier cancel,IntConsumer progress)throws IOException {
        cancelled(cancel);var box=StructureEngine.footprint(asset,anchor,rotation);
        long endX=(long)box.minX()+box.width()-1,endY=(long)box.minY()+box.height()-1,endZ=(long)box.minZ()+box.depth()-1;
        if(endX>Integer.MAX_VALUE||endY>Integer.MAX_VALUE||endZ>Integer.MAX_VALUE
                ||!new Protocol.Edit(box.minX(),box.minY(),box.minZ(),0).valid()
                ||!new Protocol.Edit((int)endX,(int)endY,(int)endZ,0).valid())throw new IOException("Preview outside world");
        var positions=asset.occupiedPositions();int count=0;boolean blocked=false;
        progress.accept(0);cancelled(cancel);
        for(var position:positions) {
            cancelled(cancel);var p=StructureEngine.rotate(position,asset.bounds.x(),asset.bounds.z(),rotation);
            int x=box.minX()+p.x(),y=box.minY()+p.y(),z=box.minZ()+p.z();
            boolean empty=snapshot.emptyCell(x,y,z);cancelled(cancel);
            if(!empty&&snapshot.cell(x,y,z).occupied()!=0){blocked=true;break;}
            if((++count&127)==0)progress.accept((int)(100L*count/positions.size()));
        }
        cancelled(cancel);progress.accept(100);cancelled(cancel);return blocked;
    }
}
