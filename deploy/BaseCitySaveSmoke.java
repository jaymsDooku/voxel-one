package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;

/** Synthetic base-writer / current-reader compatibility probe. */
public class BaseCitySaveSmoke {
    public static void main(String[] args) throws Exception {
        Path file=Path.of(args[1]);
        if (args[0].equals("write")) {
            var ground=new CityTest.Ground();
            var city=new CityTest().simulation(ground);
            for (int type=1;type<=3;type++) {
                int z=90+type*12;
                String result=city.command(new CityCommand(CityCommand.ROAD,type,
                    List.of(new Polygon.Point(90,z),new Polygon.Point(100,z))),1,null);
                if (!result.contains("built")) throw new AssertionError(result);
            }
            city.save(file);
        } else {
            try (var input=new DataInputStream(Files.newInputStream(file))) {
                if(input.readInt()!=0x4349543A) throw new AssertionError("Base save must be version 10");
            }
            var state=CitySimulation.load(file);
            for(int type=1;type<=3;type++) {
                int z=90+type*12, expected=type;
                var roads=state.roads().stream().filter(r->r.x()==95 && Math.abs(r.z()-z)<=4).toList();
                if(roads.size()!=RoadTypes.width(type) || roads.stream().anyMatch(r->r.type()!=expected))
                    throw new AssertionError("Base paved road type "+type);
            }
            for(int material=187;material<=189;material++)
                if(!Blocks.valid(material)) throw new AssertionError("Material "+material);
            if(Protocol.VERSION!=20) throw new AssertionError("Protocol 20 required");
            var ground=new CityTest.Ground();
            var restored=new CitySimulation(state.config(),ground,ground.terrain,state);
            if(!state.roads().equals(restored.frame().roads())) throw new AssertionError("Restore typed roads");
            Path roundtrip=file.resolveSibling("roundtrip-city.dat");
            restored.save(roundtrip);
            if(!state.roads().equals(CitySimulation.load(roundtrip).roads())) throw new AssertionError("Resave typed roads");
        }
        System.out.println("PASS: "+args[0]+" synthetic version-10 city save");
    }
}
