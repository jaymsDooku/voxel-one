import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Synthetic CLI probe for reviewed terrain/port saves and new road ownership. */
public class RoadShippingCompatibilityProbe {
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]);Files.createDirectories(out);
        Path world=out.resolve("generator3.dat");new LocalGame(world,42).save();
        if(Terrain.CURRENT_VERSION!=3 || new LocalGame(world,99).generatorVersion!=3)throw new AssertionError("Generator 3 save rejected");
        var h=List.of(new Polygon.Point(90,90),new Polygon.Point(110,90));
        var v=List.of(new Polygon.Point(100,80),new Polygon.Point(100,100));
        var owners=RoadOwnership.paint(List.of(),1,3,RoadGeometry.surfaces(h,3),false);
        owners=RoadOwnership.paint(owners,2,1,RoadGeometry.surfaces(v,1),false);
        var roads=RoadOwnership.visible(owners).values().stream().map(c->new CityFrame.Road(c.x(),c.z(),26,c.type())).toList();
        var port=new CityFrame.Building(1,0,SpecialBuildings.PORT,60,27,60,1,0);
        var addresses=new CityAddresses.State(List.of(new CityAddresses.Street(1,"Wide",h),new CityAddresses.Street(2,"Narrow",v)),List.of(new CityAddresses.Address(1,1,2)),owners);
        var frame=new CityFrame(GameConfig.cityGame(),0,roads,List.of(),List.of(port),List.of(),List.of(),CityEconomy.State.empty(),addresses,Agriculture.State.empty());
        var bytes=new ByteArrayOutputStream();frame.write(new DataOutputStream(bytes));
        if(!frame.equals(CityFrame.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())))))throw new AssertionError("Format 13 port/ownership roundtrip");
        Path old=out.resolve("format12.city");
        try(var stream=new DataOutputStream(Files.newOutputStream(old))) {stream.writeInt(0x4349543C);frame.write(stream,12);}
        var loaded=CitySimulation.load(old);
        if(!loaded.buildings().equals(frame.buildings()) || !loaded.roads().equals(frame.roads()))throw new AssertionError("Base format 12 port rejected");
        var terrain=new Terrain(42);
        var ground=new CitySimulation.Ground() {
            public int type(int x,int y,int z){return terrain.block(x,y,z);}
            public boolean occupied(int x,int y,int z,int w,int d){return false;}
            public void apply(List<Protocol.Edit> edits){}
        };
        var restored=new CitySimulation(loaded.config(),ground,terrain,loaded);
        Path saved=out.resolve("format13.city");restored.save(saved);
        try(var stream=new DataInputStream(Files.newInputStream(saved))) {if(stream.readInt()!=0x4349543D)throw new AssertionError("Format 13 magic");}
        if(!CitySimulation.load(saved).buildings().equals(frame.buildings()))throw new AssertionError("Port lost on resave");
        Files.writeString(out.resolve("results.json"),"{\"status\":\"passed\",\"checks\":[\"generator3 synthetic world reload\",\"format12 port type24 and mixed road cells retained\",\"format13 exact port/ownership roundtrip\",\"old12 resave to13 retains port\"]}\n");
        System.out.println("PASS: terrain3, old12 port, format13 mixed ownership and port resave");
    }
}
