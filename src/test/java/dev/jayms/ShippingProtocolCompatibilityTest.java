package dev.jayms;

import dev.jayms.net.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLServerSocket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Shipping must reject regional-only protocol 21 before exchanging incompatible snapshots. */
class ShippingProtocolCompatibilityTest {
    @TempDir Path temp;

    @Test void regionalOnlyClientIsRejectedBeforeAuthentication() throws Exception {
        assertTrue(Protocol.VERSION>21);
        var identity=SecureTransport.server(temp.resolve("synthetic-tls"));
        var accounts=new AccountStore(temp.resolve("synthetic-accounts"));
        try(var server=new MultiplayerServer("127.0.0.1",0,temp.resolve("synthetic-world.dat"),accounts,identity.context())) {
            var failure=new AtomicReference<Throwable>();
            var worker=new Thread(() -> { try { server.run(); } catch(Throwable e) { failure.set(e); } });
            worker.start();
            try(var socket=SecureTransport.connect("127.0.0.1",server.port(),identity.fingerprint())) {
                socket.setSoTimeout(5000);
                var out=new DataOutputStream(socket.getOutputStream());
                out.writeInt(Protocol.MAGIC);out.writeInt(21);out.flush();
                var in=new DataInputStream(socket.getInputStream());
                assertEquals(Protocol.MAGIC,in.readInt());assertEquals(Protocol.VERSION,in.readInt());
                assertFalse(in.readBoolean());assertTrue(in.readUTF().contains("Client version mismatch"));
            } finally {
                server.close();worker.join(5000);assertFalse(worker.isAlive());assertNull(failure.get());
            }
        }
    }

    @Test void roadOwnershipProtocol24IsRejectedBeforeAuthentication() throws Exception {
        assertEquals(26,Protocol.VERSION);
        var identity=SecureTransport.server(temp.resolve("synthetic-tls"));
        var accounts=new AccountStore(temp.resolve("synthetic-accounts"));
        try(var server=new MultiplayerServer("127.0.0.1",0,temp.resolve("synthetic-world.dat"),accounts,identity.context())) {
            var failure=new AtomicReference<Throwable>();
            var worker=new Thread(() -> { try { server.run(); } catch(Throwable e) { failure.set(e); } });
            worker.start();
            try(var socket=SecureTransport.connect("127.0.0.1",server.port(),identity.fingerprint())) {
                socket.setSoTimeout(5000);
                var out=new DataOutputStream(socket.getOutputStream());
                out.writeInt(Protocol.MAGIC);out.writeInt(24);out.flush();
                var in=new DataInputStream(socket.getInputStream());
                assertEquals(Protocol.MAGIC,in.readInt());assertEquals(Protocol.VERSION,in.readInt());
                assertFalse(in.readBoolean());assertTrue(in.readUTF().contains("Client version mismatch"));
            } finally {
                server.close();worker.join(5000);assertFalse(worker.isAlive());assertNull(failure.get());
            }
        }
    }

    @Test void shippingClientRejectsRegionalOnlyServerWithoutDowngrading() throws Exception {
        assertTrue(Protocol.VERSION>21);
        var identity=SecureTransport.server(temp.resolve("synthetic-tls"));
        try(var listener=(SSLServerSocket)identity.context().getServerSocketFactory()
                .createServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            listener.setSoTimeout(5000);
            var worker=Executors.newSingleThreadExecutor();
            try {
                var served=worker.submit(() -> {
                    try(var socket=listener.accept()) {
                        socket.setSoTimeout(5000);
                        var in=new DataInputStream(socket.getInputStream());
                        assertEquals(Protocol.MAGIC,in.readInt());assertEquals(Protocol.VERSION,in.readInt());
                        in.readByte();in.readUTF();in.readUTF();
                        var out=new DataOutputStream(socket.getOutputStream());
                        out.writeInt(Protocol.MAGIC);out.writeInt(21);out.writeBoolean(false);
                        out.writeUTF("Update required");out.flush();
                    }
                    return true;
                });
                char[] synthetic="synthetic-only-test-secret".toCharArray();
                try {
                    var error=assertThrows(IOException.class,() -> new MultiplayerClient("127.0.0.1",listener.getLocalPort(),
                            "Synthetic",synthetic,false,identity.fingerprint()));
                    assertTrue(error.getMessage().contains("client "+Protocol.VERSION+", server 21"));
                    assertTrue(error.getMessage().contains("server needs an update"));
                } finally { java.util.Arrays.fill(synthetic,'\0'); }
                assertTrue(served.get(5,TimeUnit.SECONDS));
            } finally { worker.shutdownNow(); }
        }
    }
}
