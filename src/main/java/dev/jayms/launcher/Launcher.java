package dev.jayms.launcher;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Small dependency-free desktop bootstrap. The game and user saves live independently. */
public final class Launcher {
    public static Path cache() {
        String local = System.getenv("LOCALAPPDATA");
        if (Platform.current() == Platform.WINDOWS && local != null && !local.isBlank())
            return Path.of(local).resolve("VoxelOne");
        return Path.of(System.getProperty("user.home"), ".voxel-one", "client");
    }

    public static List<String> command(Path client, Platform platform, List<String> arguments) {
        String java =
                Path.of(
                                System.getProperty("java.home"),
                                "bin",
                                platform == Platform.WINDOWS ? "javaw.exe" : "java")
                        .toString();
        List<String> command =
                new ArrayList<>(List.of(java, "-Duser.home=" + System.getProperty("user.home"),
                        "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir")));
        if (platform == Platform.MAC_ARM || platform == Platform.MAC_INTEL)
            command.add("-XstartOnFirstThread");
        command.add("-jar");
        command.add(client.toString());
        command.addAll(arguments);
        if (!arguments.contains("--asset-manager") && !arguments.contains("--offline") && !arguments.contains("--server"))
            command.addAll(List.of("--server", "198.100.154.156"));
        return command;
    }

    public static void main(String[] args) {
        run(args, new Updater(cache(), Updater.MANIFEST));
    }

    /** The updater is explicit so the complete launcher can use a local verified release in tests. */
    public static void run(String[] args, Updater updater) {
        if (!Arrays.asList(args).contains("--update-only") && args.length == 0 && !GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> {
                try { new HubFrame(cache()).setVisible(true); }
                catch (IOException e) { JOptionPane.showMessageDialog(null, e.getMessage(), "Voxel One Hub", JOptionPane.ERROR_MESSAGE); }
            });
            return;
        }
        List<String> selectedArguments = new ArrayList<>(Arrays.asList(args));
        int experienceIndex = selectedArguments.indexOf("--experience");
        if (experienceIndex >= 0) {
            try {
                if (experienceIndex + 1 >= selectedArguments.size()) throw new IllegalArgumentException("--experience requires an ID");
                String id = selectedArguments.remove(experienceIndex + 1); selectedArguments.remove(experienceIndex);
                HubCatalog.Entry entry = new CatalogStore(cache()).cached().catalog().entries().stream()
                        .filter(e -> e.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown experience ID"));
                if (!entry.supported(Platform.current()) || !entry.adapter().equals("native")) throw new IllegalArgumentException("CLI supports native experiences only");
                if (selectedArguments.contains("--game")) throw new IllegalArgumentException("Use either --game or --experience");
                selectedArguments.addAll(0, List.of("--game", entry.target().equals("editor") ? "city" : entry.target()));
                if (entry.target().equals("editor")) selectedArguments.add("--editor");
            } catch (Exception e) { System.err.println(e.getMessage()); System.exit(1); return; }
        }
        args = selectedArguments.toArray(String[]::new);
        boolean updateOnly = Arrays.asList(args).contains("--update-only");
        List<String> launchArguments = new ArrayList<>(Arrays.asList(args));
        if (!updateOnly && !launchArguments.contains("--asset-manager") && !GraphicsEnvironment.isHeadless()) {
            int selected = chooseApplication();
            if (selected < 0) return;
            if (selected == 1) launchArguments.add("--asset-manager");
        }
        JFrame frame = null;
        JLabel message = new JLabel("Checking for Voxel One updates...");
        if (!updateOnly && !GraphicsEnvironment.isHeadless()) {
            frame = new JFrame("Voxel One");
            JPanel panel = new JPanel(new BorderLayout(0, 18));
            panel.setBorder(new EmptyBorder(24, 28, 24, 28));
            JLabel title = new JLabel("VOXEL ONE");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 24));
            JProgressBar progress = new JProgressBar();
            progress.setIndeterminate(true);
            panel.add(title, BorderLayout.NORTH);
            panel.add(message, BorderLayout.CENTER);
            panel.add(progress, BorderLayout.SOUTH);
            frame.setContentPane(panel);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setSize(480, 190);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        }
        try {
            Platform platform = Platform.current();
            Path cache = cache();
            Updater.Result result =
                    updater.update(
                                    platform,
                                    text -> {
                                        System.out.println(text);
                                        SwingUtilities.invokeLater(() -> message.setText(text));
                                    });
            if (updateOnly) System.out.println(result.client());
            else {
                SwingUtilities.invokeLater(() -> message.setText("Starting Voxel One..."));
                List<String> arguments = new ArrayList<>(launchArguments);
                arguments.remove("--update-only");
                Process game =
                        new ProcessBuilder(command(result.client(), platform, arguments))
                                .redirectErrorStream(true)
                                .redirectOutput(
                                        ProcessBuilder.Redirect.appendTo(
                                                cache.resolve("game.log").toFile()))
                                .start();
                if (game.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                        && game.exitValue() != 0)
                    throw new IOException(
                            "Voxel One could not start. See " + cache.resolve("game.log"));
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            if (!GraphicsEnvironment.isHeadless() && !updateOnly)
                JOptionPane.showMessageDialog(
                        frame, e.getMessage(), "Voxel One", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        } finally {
            if (frame != null) frame.dispose();
        }
    }

    public static int chooseApplication() {
        Object[] choices = {"Play Voxel One", "Asset Manager"};
        return JOptionPane.showOptionDialog(null, "Choose what to open", "Voxel One Launcher",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
    }

    private Launcher() {}
}
