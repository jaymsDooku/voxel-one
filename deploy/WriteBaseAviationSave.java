package dev.jayms;

import dev.jayms.net.city.*;
import java.nio.file.*;
import java.util.*;

/** Compile against reviewed aviation base, not current game classes. Synthetic data only. */
public final class WriteBaseAviationSave {
    public static void main(String[] args) throws Exception {
        var fixture=AviationTest.fixture(); var city=fixture.city();
        if(!AviationTest.command(city,AviationTest.permit(50)).startsWith("Permitted")) throw new AssertionError("Origin airport");
        if(!AviationTest.command(city,AviationTest.permit(130)).startsWith("Permitted")) throw new AssertionError("Destination airport");
        var airports=city.frame().buildings();
        if(!AviationTest.command(city,new CityCommand(CityCommand.RUNWAY,airports.get(0).id(),List.of())).contains("2 runways")) throw new AssertionError("Runway expansion");
        int citizen=city.frame().citizens().get(0).id();
        if(!AviationTest.command(city,new CityCommand(CityCommand.FLIGHT,airports.get(1).id(),List.of(),0,citizen)).startsWith("Flight booked")) throw new AssertionError("Base flight");
        Path save=Path.of(args[0]); city.save(save);
        Files.writeString(save.resolveSibling("base-v12-aviation-state.txt"),city.frame().aviation().toString()+"\n");
        System.out.println("PASS: reviewed base wrote synthetic v12 airports, expanded runway and booked flight");
    }
}
