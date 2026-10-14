package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HistoryProtocolCompatibilityTest {
    @TempDir Path temp;
    void header(DataInputStream in,int version)throws IOException {
        assertEquals(Protocol.MAGIC,in.readInt());assertEquals(version,in.readInt());assertEquals(Protocol.LOGIN,in.readUnsignedByte());in.readUTF();in.readUTF();
    }
    void reply(DataOutputStream out,int version,boolean accepted)throws IOException {
        out.writeInt(Protocol.MAGIC);out.writeInt(version);out.writeBoolean(accepted);out.writeUTF(accepted?"Welcome":"Client version mismatch");out.flush();
    }
    @Test void protocol29FallbackKeepsSnapshotAndNoHistoryBytes()throws Exception {
        var tls=SecureTransport.server(temp.resolve("synthetic-tls"));
        try(var listener=tls.context().getServerSocketFactory().createServerSocket(0,2,InetAddress.getLoopbackAddress())) {
            listener.setSoTimeout(10000);
            var served=new FutureTask<Void>(()->{
                try(var first=listener.accept()){first.setSoTimeout(10000);header(new DataInputStream(first.getInputStream()),30);reply(new DataOutputStream(first.getOutputStream()),29,false);}
                try(var socket=listener.accept()) {
                    socket.setSoTimeout(10000);var in=new DataInputStream(socket.getInputStream());var out=new DataOutputStream(socket.getOutputStream());header(in,29);reply(out,29,true);
                    out.writeInt(1);new Protocol.Pose(1,8,20,24,0,0,0,0,false).write(out);out.writeUTF("history");out.writeLong(Terrain.DEFAULT_SEED);out.writeInt(Terrain.CURRENT_VERSION);
                    new ModelLibrary().write(out);new Inventory().write(out);out.writeByte(20);out.writeInt(0);out.writeInt(0);out.writeInt(0);CityFrame.empty(GameConfig.cityGame()).write(out,17);out.flush();
                    assertEquals(Protocol.READY,in.readUnsignedByte());assertEquals(Protocol.CITY_COMMAND,in.readUnsignedByte());assertEquals(CityCommand.ROAD,CityCommand.read(in).kind());out.writeByte(Protocol.CITY_RESULT);out.writeUTF("Synthetic road checked");out.flush();assertEquals(-1,in.read());
                }return null;
            });var thread=new Thread(served);thread.setDaemon(true);thread.start();
            try(var client=new MultiplayerClient("127.0.0.1",listener.getLocalPort(),"history","synthetic-history-password".toCharArray(),false,tls.fingerprint())) {
                assertEquals(29,client.serverProtocol);assertTrue(client.city.config().city());
                assertFalse(client.historyQuery(new HistoryWire.Request(null,1,List.of(),0,1,0,1000)));assertTrue(client.historyError.contains("requires 30"));
                assertTrue(client.cityCommand(new CityCommand(CityCommand.ROAD,0,List.of())));
                long deadline=System.nanoTime()+5_000_000_000L;while(!client.notice().equals("Synthetic road checked")&&System.nanoTime()<deadline){client.poll();Thread.sleep(10);}
                assertEquals("Synthetic road checked",client.notice());
            }
            served.get(10,TimeUnit.SECONDS);thread.join(1000);
        }
    }
    @Test void protocol29ClientRejectedBeforeAuthentication()throws Exception {
        var tls=SecureTransport.server(temp.resolve("synthetic-tls"));var accounts=new AccountStore(temp.resolve("synthetic-accounts"));
        try(var server=new MultiplayerServer("127.0.0.1",0,temp.resolve("synthetic-world"),accounts,tls.context())) {
            var served=new FutureTask<Void>(()->{server.run();return null;});var thread=new Thread(served);thread.start();
            try(var socket=SecureTransport.connect("127.0.0.1",server.port(),tls.fingerprint())) {
                socket.setSoTimeout(5000);var out=new DataOutputStream(socket.getOutputStream());out.writeInt(Protocol.MAGIC);out.writeInt(29);out.flush();
                var in=new DataInputStream(socket.getInputStream());assertEquals(Protocol.MAGIC,in.readInt());assertEquals(30,in.readInt());assertFalse(in.readBoolean());assertTrue(in.readUTF().contains("Client version mismatch"));
            }
            server.close();served.get(5,TimeUnit.SECONDS);thread.join(1000);
        }
    }
}
