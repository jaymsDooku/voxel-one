import dev.jayms.launcher.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Runs the actual Swing shell with an isolated synthetic profile and real display. */
public class HubPlaytest {
    static HubFrame frame;
    static Path evidence;
    static void capture(String name) throws Exception {
        Thread.sleep(450);
        Rectangle bounds = frame.getBounds();
        BufferedImage image = new Robot().createScreenCapture(bounds);
        ImageIO.write(image, "png", evidence.resolve(name + ".png").toFile());
    }
    static java.util.List<Component> descendants(Container c) {
        java.util.List<Component> all = new ArrayList<>();
        for (Component child : c.getComponents()) { all.add(child); if (child instanceof Container cc) all.addAll(descendants(cc)); }
        return all;
    }
    public static void main(String[] args) throws Exception {
        evidence = Path.of(args[0]); Files.createDirectories(evidence);
        Path profile = Path.of(args[1]); Files.createDirectories(profile);
        System.setProperty("user.home", profile.toAbsolutePath().toString());
        Files.deleteIfExists(profile.resolve("catalog.properties"));
        Properties prefs = new Properties(); prefs.setProperty("updates", "false");
        try (var out = Files.newOutputStream(profile.resolve("hub.properties"))) { prefs.store(out, "Synthetic playtest"); }
        SwingUtilities.invokeAndWait(() -> { try { frame = new HubFrame(profile); frame.setVisible(true); } catch (Exception e) { throw new RuntimeException(e); } });
        capture("hub-home-1280");
        SwingUtilities.invokeAndWait(() -> frame.showPage("Games")); capture("hub-library-1280");
        SwingUtilities.invokeAndWait(() -> { for (Component c : descendants(frame)) if (c instanceof JTextField f && "Search games".equals(f.getAccessibleContext().getAccessibleName())) f.setText("no-such-game"); });
        capture("hub-search-empty");
        HubCatalog catalog = HubCatalog.bundled();
        SwingUtilities.invokeAndWait(() -> frame.detail(catalog.entries().get(0))); capture("hub-city-detail");
        SwingUtilities.invokeAndWait(() -> frame.showPage("Engine & Tools")); capture("hub-engine-tools");
        SwingUtilities.invokeAndWait(() -> { frame.setSize(900,600); frame.showPage("Downloads"); }); capture("hub-downloads-900");
        SwingUtilities.invokeAndWait(() -> frame.showPage("Settings / Help"));
        SwingUtilities.invokeAndWait(() -> { for (Component c : descendants(frame)) if (c instanceof JCheckBox box && box.getText().equals("Light theme")) box.doClick(); });
        capture("hub-settings-light-900");
        // Use actual keyboard navigation across pages and inspect focus.
        Robot robot = new Robot(); robot.keyPress(java.awt.event.KeyEvent.VK_ALT); robot.keyPress(java.awt.event.KeyEvent.VK_2); robot.keyRelease(java.awt.event.KeyEvent.VK_2); robot.keyRelease(java.awt.event.KeyEvent.VK_ALT); Thread.sleep(300);
        robot.keyPress(java.awt.event.KeyEvent.VK_TAB); robot.keyRelease(java.awt.event.KeyEvent.VK_TAB); capture("hub-keyboard-focus-900");
        SwingUtilities.invokeAndWait(() -> frame.refresh()); Thread.sleep(17000); capture("hub-catalog-network-state");
        SwingUtilities.invokeAndWait(() -> frame.dispose());
        String source;
        try (var in = HubCatalog.class.getResourceAsStream("catalog.properties")) { source = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1); }
        String extra = source.substring(source.indexOf("sandbox.title="), source.indexOf("space-invaders.title=")).replace("sandbox.", "new-game.").replace("new-game.title=Voxel One Sandbox", "new-game.title=Synthetic Catalog Game");
        Files.writeString(profile.resolve("catalog.properties"), source.replace("entries=city,sandbox,space-invaders,engine-editor", "entries=city,sandbox,space-invaders,engine-editor,new-game")+extra);
        SwingUtilities.invokeAndWait(() -> { try { frame = new HubFrame(profile); frame.setSize(900,600); frame.setVisible(true); frame.showPage("Games"); } catch(Exception e) { throw new RuntimeException(e); } });
        SwingUtilities.invokeAndWait(() -> {
            boolean found=false;
            for(Component c:descendants(frame)) {
                if(c instanceof JLabel l && l.getText().equals("Synthetic Catalog Game")) found=true;
                if(c instanceof JScrollPane scroll) scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum());
            }
            if(!found)throw new AssertionError("Synthetic catalog game missing from actual library");
        });
        capture("hub-synthetic-catalog-game"); SwingUtilities.invokeAndWait(() -> frame.dispose());
        Files.writeString(profile.resolve("catalog.properties"), "format=1\nchannel=playtest\nversion=synthetic-empty\nentries=\n");
        SwingUtilities.invokeAndWait(() -> { try { frame=new HubFrame(profile); frame.setSize(900,600);frame.setVisible(true); } catch(Exception e) { throw new RuntimeException(e); } });
        capture("hub-empty-catalog"); SwingUtilities.invokeAndWait(() -> frame.dispose());
        System.out.println("Playtest: home, library, search empty edge, city detail, engine tools, downloads, light theme, 900x600, keyboard and catalog refresh exercised.");
    }
}
