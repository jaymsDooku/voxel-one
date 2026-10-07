package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShippingAviationCompatibilityTest {
    @TempDir Path temp;

    @Test void baseFormatTwelveEmptySaveLoads() throws Exception {
        var frame=CityFrame.empty(GameConfig.cityGame());
        var path=temp.resolve("base12.city");
        try(var out=new DataOutputStream(Files.newOutputStream(path))) {
            out.writeInt(0x4349543C); frame.write(out,12);
        }
        assertEquals(frame,CitySimulation.load(path));
    }

    @Test void aviationOnlyClientIsRejectedBeforeAuthentication() throws Exception {
        var identity=SecureTransport.server(temp.resolve("synthetic-tls"));
        var accounts=new AccountStore(temp.resolve("synthetic-accounts"));
        try(var server=new MultiplayerServer("127.0.0.1",0,temp.resolve("synthetic-world"),accounts,identity.context())) {
            var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            var worker=new Thread(() -> {try {server.run();} catch(Throwable e) {failure.set(e);}});
            worker.start();
            try(var socket=SecureTransport.connect("127.0.0.1",server.port(),identity.fingerprint())) {
                socket.setSoTimeout(5000);
                var out=new DataOutputStream(socket.getOutputStream());
                out.writeInt(Protocol.MAGIC);out.writeInt(22);out.flush();
                var in=new DataInputStream(socket.getInputStream());
                assertEquals(Protocol.MAGIC,in.readInt());assertEquals(23,in.readInt());
                assertFalse(in.readBoolean());assertTrue(in.readUTF().contains("Client version mismatch"));
            } finally {server.close();worker.join(5000);assertFalse(worker.isAlive());assertNull(failure.get());}
        }
    }

    @Test void aviationSnapshotConsumesEntirePacketAndKeepsCommands() throws Exception {
        var fixture=AviationTest.fixture();var city=fixture.city();
        AviationTest.command(city,AviationTest.permit(50));
        AviationTest.command(city,AviationTest.permit(130));
        var frame=city.frame();
        int airport=frame.buildings().get(1).id(), citizen=frame.citizens().get(0).id();
        assertTrue(AviationTest.command(city,new CityCommand(CityCommand.FLIGHT,airport,List.of(),0,citizen)).startsWith("Flight booked"));
        var bytes=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(bytes)) { city.frame().write(out);out.writeInt(Protocol.CITY_STATE); }
        try(var in=new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(city.frame(),CityFrame.read(in));
            assertEquals(Protocol.CITY_STATE,in.readInt());assertEquals(0,in.available());
        }
        var path=temp.resolve("aviation12.city");city.save(path);
        assertEquals(city.frame(),CitySimulation.load(path));
        assertEquals(23,SpecialBuildings.AIRPORT);assertEquals(24,SpecialBuildings.PORT);
        assertEquals(10,CityCommand.RUNWAY);assertEquals(11,CityCommand.FLIGHT);
        assertTrue(Protocol.VERSION>22);
    }
}
