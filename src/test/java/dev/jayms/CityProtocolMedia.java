package dev.jayms;

import dev.jayms.net.*;
import dev.jayms.net.city.*;
import dev.jayms.net.model.ModelLibrary;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Captures the actual login UI and game against a synthetic pinned TLS peer. */
public final class CityProtocolMedia {
    private static void fields(Container c, java.util.List<JTextField> fields,
                               java.util.List<JButton> buttons) {
        for (Component child : c.getComponents()) {
            if (child instanceof JTextField f) fields.add(f);
            if (child instanceof JButton b) buttons.add(b);
            if (child instanceof Container nested) fields(nested, fields, buttons);
        }
    }
    public static void main(String[] args) throws Exception {
        int version = Integer.parseInt(args[0]);
        Path root = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(root);
        System.setProperty("user.home", Files.createTempDirectory(root, "home-" + version + "-").toString());
        var identity = SecureTransport.server(root.resolve("tls-" + version));
        var listener = identity.context().getServerSocketFactory()
                .createServerSocket(0, 2, InetAddress.getLoopbackAddress());
        listener.setSoTimeout(15000);
        Thread peer = new Thread(() -> {
            try {
                for (int attempt = 0; attempt < (version == 14 ? 2 : 1); attempt++) {
                    try (var socket = listener.accept()) {
                        var in = new DataInputStream(socket.getInputStream());
                        var out = new DataOutputStream(socket.getOutputStream());
                        if (in.readInt() != Protocol.MAGIC) throw new IOException("Bad magic");
                        int requested = in.readInt();
                        if (requested != (attempt == 0 ? Protocol.VERSION : 14))
                            throw new IOException("Unexpected requested version");
                        if (in.readUnsignedByte() != Protocol.LOGIN) throw new IOException("Not login");
                        in.readUTF(); in.readUTF(); // synthetic fixture only; never print secrets
                        out.writeInt(Protocol.MAGIC); out.writeInt(version);
                        boolean accepted = version == 14 && attempt == 1;
                        out.writeBoolean(accepted); out.writeUTF(accepted ? "Welcome tester" : "Version mismatch");
                        out.flush();
                        if (!accepted) continue;
                        out.writeInt(1);
                        new Protocol.Pose(1, 8, 20, 24, 0, 0, 0, 0, false).write(out);
                        out.writeUTF("tester"); out.writeLong(Terrain.DEFAULT_SEED);
                        new ModelLibrary().write(out); new Inventory().write(out);
                        out.writeByte(20); out.writeInt(0); out.writeInt(0); out.writeInt(0);
                        CityFrame.empty(GameConfig.cityGame()).write(out, 6); out.flush();
                        if (in.readUnsignedByte() != Protocol.READY) throw new IOException("Not ready");
                        System.out.println("Pinned protocol-14 login and snapshot accepted");
                        while (in.read() != -1) { /* keep synthetic peer alive */ }
                    }
                }
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        peer.setDaemon(true); peer.start();
        Thread driver = new Thread(() -> {
            try {
                JFrame[] frame = {null};
                for (int i = 0; i < 100 && frame[0] == null; i++) {
                    SwingUtilities.invokeAndWait(() -> {
                        for (Window w : Window.getWindows())
                            if (w instanceof JFrame f && f.isShowing()) frame[0] = f;
                    });
                    Thread.sleep(100);
                }
                if (frame[0] == null) throw new IOException("Login UI unavailable");
                SwingUtilities.invokeAndWait(() -> {
                    var inputs = new ArrayList<JTextField>(); var buttons = new ArrayList<JButton>();
                    fields(frame[0], inputs, buttons);
                    inputs.get(2).setText("tester"); inputs.get(3).setText("synthetic-password");
                    buttons.stream().filter(b -> b.getText().equals("Sign in")).findFirst().orElseThrow().doClick();
                    inputs.get(3).setText("");
                });
                Thread.sleep(version == 14 ? 35000 : 3000);
                if (version != 14) SwingUtilities.invokeAndWait(() -> {
                    frame[0].setSize(1100, 440); frame[0].setLocationRelativeTo(null);
                });
                if (version == 14 && frame[0].isShowing()) throw new IOException("Login did not complete");
                if (version == 14) {
                    Robot robot = new Robot();
                    robot.mouseMove(437, 708); robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                    robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                    Thread.sleep(5000);
                }
                ImageIO.write(new Robot().createScreenCapture(new Rectangle(0, 0, 1280, 900)), "png",
                        Path.of(args[2]).toFile());
                System.out.println("Captured real implementation image for protocol " + version);
                System.exit(0);
            } catch (Exception e) { e.printStackTrace(); System.exit(1); }
        });
        driver.setDaemon(true); driver.start();
        Main.main(new String[] {"--game", "city", "--server", "127.0.0.1", "--port",
                Integer.toString(listener.getLocalPort()), "--fingerprint", identity.fingerprint()});
    }
}
