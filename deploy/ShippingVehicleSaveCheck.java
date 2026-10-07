package dev.jayms;

import dev.jayms.player.*;
import org.joml.Vector3f;
import java.nio.file.*;

/** Check synthetic legacy and cargo save positions without native UI or real profiles. */
public final class ShippingVehicleSaveCheck {
    public static void main(String[] args) throws Exception {
        Path dir=Path.of(args[0]);Files.createDirectories(dir);
        var world=new JeepTest().flat();
        for(int fields : new int[]{4,5}) {
            Path file=dir.resolve("synthetic-"+fields+".jeep");
            Files.writeString(file,"0 1.01 0 -90"+(fields==5 ? " CONTAINER" : "")+"\n");
            var expected=fields==5 ? CargoVehicle.CONTAINER : CargoVehicle.JEEP;
            var vehicle=Jeep.load(file,world,new Vector3f(10,1,10));
            if(!vehicle.position().equals(new Vector3f(0,1.01f,0)) || vehicle.yaw()!=-90 || vehicle.type()!=expected)
                throw new AssertionError(fields+"-field save respawned or lost body");
            vehicle.save(file);
            if(!Files.readString(file).trim().endsWith(expected.name()))throw new AssertionError("Body not persisted");
            var restored=Jeep.load(file,world,new Vector3f(10,1,10));
            if(!restored.position().equals(vehicle.position()) || restored.yaw()!=vehicle.yaw() || restored.type()!=expected)
                throw new AssertionError("Roundtrip changed vehicle");
        }
        Files.writeString(Path.of(args[1]),"{\"status\":\"passed\",\"workflow\":\"Synthetic vehicle save CLI; browser UI does not apply to disk compatibility\",\"expected\":\"Both formats retain position (0,1.01,0) and yaw -90; four fields default to JEEP, five fields retain CONTAINER; canonical save and reload retain body\",\"observed\":\"All assertions passed\",\"profile\":\"isolated synthetic files under target/shipping-vehicle-save-check\"}\n");
        System.out.println("PASS: four/five-field position, yaw, body and save/reload");
    }
}
