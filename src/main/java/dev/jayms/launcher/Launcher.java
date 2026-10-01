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
    private static Path cache() {
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
                new ArrayList<>(List.of(java, "-Duser.home=" + System.getProperty("user.home")));
        if (platform == Platform.MAC_ARM || platform == Platform.MAC_INTEL)
            command.add("-XstartOnFirstThread");
        command.add("-jar");
        command.add(client.toString());
        command.addAll(arguments);
        if (!arguments.contains("--offline") && !arguments.contains("--server"))
            command.addAll(List.of("--server", "198.100.154.156"));
        return command;
    }

    public static void main(String[] args) {
        boolean updateOnly = Arrays.asList(args).contains("--update-only");
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
                    new Updater(cache, Updater.MANIFEST)
                            .update(
                                    platform,
                                    text -> {
                                        System.out.println(text);
                                        SwingUtilities.invokeLater(() -> message.setText(text));
                                    });
            if (updateOnly) System.out.println(result.client());
            else {
                SwingUtilities.invokeLater(() -> message.setText("Starting Voxel One..."));
                List<String> arguments = new ArrayList<>(Arrays.asList(args));
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

    private Launcher() {}
}
