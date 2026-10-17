import dev.jayms.launcher.*;
import dev.jayms.render.GraphicsProfile;
import java.nio.file.*;
import javax.swing.*;

/** Real public pinned release install through the Hub. Synthetic profile; no account data. */
public final class ImmutableReleasePlaytest {
    public static void main(String[] args) throws Exception {
        Path profile = Path.of(args[0]).toAbsolutePath();
        if (Files.exists(profile)) throw new IllegalArgumentException("Fresh profile required");
        Files.createDirectories(profile);
        System.setProperty("user.home", profile.toString());
        Files.createDirectories(profile.resolve(".voxel-one"));
        GraphicsProfile.preset(GraphicsProfile.Preset.LOW).save(profile.resolve(".voxel-one/graphics.properties"));
        HubWorkflowPlaytest.evidence = Path.of(args[1]);
        Files.createDirectories(HubWorkflowPlaytest.evidence);
        var entry = HubCatalog.bundled().entries().get(0);
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    HubWorkflowPlaytest.frame = new HubFrame(profile);
                    HubWorkflowPlaytest.frame.setSize(900,600);
                    HubWorkflowPlaytest.frame.setVisible(true);
                    HubWorkflowPlaytest.frame.detail(entry);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            HubWorkflowPlaytest.click("Play / Install");
            for (int i=0; i<900 && !HubWorkflowPlaytest.textPresent("Game started."); i++) Thread.sleep(200);
            if (!HubWorkflowPlaytest.textPresent("Game started.")) throw new AssertionError("Fresh pinned install did not launch");
            var installed = new Updater(profile, entry.clientManifest()).installed(Platform.current());
            entry.requireClient(installed);
            if (!entry.source().equals(installed.revision())) throw new AssertionError("Wrong immutable release source");
            SwingUtilities.invokeAndWait(() -> {
                for (var component : HubPlaytest.descendants(HubWorkflowPlaytest.frame))
                    if (component instanceof JTextArea area) area.setCaretPosition(area.getDocument().getLength());
            });
            HubWorkflowPlaytest.capture("hub-immutable-fresh-install");
            HubWorkflowPlaytest.focusGame();
            Thread.sleep(3000);
            if (ProcessHandle.current().descendants().noneMatch(ProcessHandle::isAlive)) throw new AssertionError("Native game exited");
            System.out.println("Playtest: fresh Hub public immutable install and City window passed; source=" + installed.revision());
        } finally {
            ProcessHandle.current().descendants().forEach(ProcessHandle::destroy);
            if (HubWorkflowPlaytest.frame != null) SwingUtilities.invokeAndWait(() -> HubWorkflowPlaytest.frame.dispose());
        }
    }
}
