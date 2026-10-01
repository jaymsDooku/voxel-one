package dev.jayms.ui;

import dev.jayms.net.MultiplayerClient;
import dev.jayms.net.SecureTransport;

import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import javax.swing.*;

/**
 * Login and registration happen before opening the game, without blocking the Swing event thread.
 */
public final class LoginDialog {
    public static MultiplayerClient open(String initialHost, int initialPort, String explicitPin)
            throws Exception {
        CompletableFuture<MultiplayerClient> result = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> show(initialHost, initialPort, explicitPin, result));
        return result.get();
    }

    private static void show(
            String initialHost,
            int initialPort,
            String explicitPin,
            CompletableFuture<MultiplayerClient> result) {
        Properties settings = new Properties();
        Path file = Controls.directory().resolve("connections.properties");
        try {
            if (Files.exists(file))
                try (var in = Files.newInputStream(file)) {
                    settings.load(in);
                }
        } catch (IOException ignored) {
        }
        JFrame frame = new JFrame("Voxel One - Play");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(
                new java.awt.event.WindowAdapter() {
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        result.completeExceptionally(
                                new java.util.concurrent.CancellationException());
                    }
                });
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(7, 7, 7, 7);
        c.fill = GridBagConstraints.HORIZONTAL;
        JTextField host =
                new JTextField(
                        initialHost == null
                                ? settings.getProperty("host", "198.100.154.156")
                                : initialHost,
                        22);
        JTextField port = new JTextField(Integer.toString(initialPort), 6);
        JTextField username = new JTextField(settings.getProperty("username", "jayms"), 20);
        JPasswordField password = new JPasswordField(20);
        JLabel status = new JLabel("Sign in, create an account, or play offline.");
        String[] labels = {"Server", "Port", "Username", "Password"};
        JComponent[] fields = {host, port, username, password};
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        JLabel heading = new JLabel("VOXEL ONE");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24));
        panel.add(heading, c);
        c.gridwidth = 1;
        for (int i = 0; i < fields.length; i++) {
            c.gridy = i + 1;
            c.gridx = 0;
            panel.add(new JLabel(labels[i]), c);
            c.gridx = 1;
            panel.add(fields[i], c);
        }
        JButton login = new JButton("Sign in"),
                register = new JButton("Create account"),
                offline = new JButton("Play offline");
        JPanel buttons = new JPanel();
        buttons.add(login);
        buttons.add(register);
        buttons.add(offline);
        c.gridx = 0;
        c.gridy = 5;
        c.gridwidth = 2;
        panel.add(buttons, c);
        c.gridy = 6;
        panel.add(status, c);
        JLabel hint = new JLabel("Usernames: 3-16 letters/numbers/_ | Passwords: 10+ characters");
        hint.setFont(hint.getFont().deriveFont(11f));
        c.gridy = 7;
        panel.add(hint, c);
        Runnable[] connect = new Runnable[2];
        for (int action = 0; action < 2; action++) {
            boolean create = action == 1;
            connect[action] =
                    () -> {
                        String address = host.getText().trim(), name = username.getText().trim();
                        int number;
                        try {
                            number = Integer.parseInt(port.getText().trim());
                            if (number < 1 || number > 65535) throw new NumberFormatException();
                        } catch (NumberFormatException e) {
                            status.setText("Enter a port from 1 to 65535.");
                            return;
                        }
                        if (!dev.jayms.net.AccountStore.validName(name)) {
                            status.setText("Choose a username with 3-16 letters, numbers or _.");
                            return;
                        }
                        char[] secret = password.getPassword();
                        if (secret.length < 10 || secret.length > 128) {
                            Arrays.fill(secret, '\0');
                            status.setText("Password must contain 10-128 characters.");
                            return;
                        }
                        login.setEnabled(false);
                        register.setEnabled(false);
                        offline.setEnabled(false);
                        status.setText("Connecting securely...");
                        new SwingWorker<MultiplayerClient, Void>() {
                            protected MultiplayerClient doInBackground() throws Exception {
                                try {
                                    String key = "pin." + address + "." + number;
                                    String pin =
                                            explicitPin == null
                                                    ? settings.getProperty(key)
                                                    : explicitPin;
                                    if (pin == null) {
                                        String observed = SecureTransport.inspect(address, number);
                                        boolean[] approved = {false};
                                        SwingUtilities.invokeAndWait(
                                                () ->
                                                        approved[0] =
                                                                JOptionPane.showConfirmDialog(
                                                                                frame,
                                                                                "First connection"
                                                                                    + " to "
                                                                                        + address
                                                                                        + ":"
                                                                                        + number
                                                                                        + "\n"
                                                                                        + "Server"
                                                                                        + " certificate"
                                                                                        + " SHA-256:\n"
                                                                                        + observed
                                                                                        + "\n\n"
                                                                                        + "Compare"
                                                                                        + " this"
                                                                                        + " with"
                                                                                        + " the server"
                                                                                        + " owner's"
                                                                                        + " fingerprint.\n"
                                                                                        + "Trust"
                                                                                        + " this"
                                                                                        + " server"
                                                                                        + " and remember"
                                                                                        + " its certificate?",
                                                                                "Trust server",
                                                                                JOptionPane
                                                                                        .YES_NO_OPTION,
                                                                                JOptionPane
                                                                                        .QUESTION_MESSAGE)
                                                                        == JOptionPane.YES_OPTION);
                                        if (!approved[0])
                                            throw new IOException("Server was not trusted.");
                                        pin = observed;
                                        settings.setProperty(key, pin);
                                    }
                                    MultiplayerClient client =
                                            new MultiplayerClient(
                                                    address, number, name, secret, create, pin);
                                    try {
                                        settings.setProperty("host", address);
                                        settings.setProperty("username", name);
                                        Files.createDirectories(file.getParent());
                                        try (var out = Files.newOutputStream(file)) {
                                            settings.store(
                                                    out,
                                                    "Server identities and username; no passwords");
                                        }
                                    } catch (IOException e) {
                                        client.close();
                                        throw e;
                                    }
                                    return client;
                                } finally {
                                    Arrays.fill(secret, '\0');
                                }
                            }

                            protected void done() {
                                try {
                                    MultiplayerClient client = get();
                                    if (!result.complete(client)) client.close();
                                    password.setText("");
                                    frame.dispose();
                                } catch (Exception e) {
                                    Throwable cause = e.getCause() == null ? e : e.getCause();
                                    status.setText(
                                            cause.getMessage() == null
                                                    ? "Connection failed"
                                                    : cause.getMessage());
                                    login.setEnabled(true);
                                    register.setEnabled(true);
                                    offline.setEnabled(true);
                                }
                            }
                        }.execute();
                    };
        }
        login.addActionListener(e -> connect[0].run());
        register.addActionListener(e -> connect[1].run());
        offline.addActionListener(
                e -> {
                    result.complete(null);
                    frame.dispose();
                });
        frame.getRootPane().setDefaultButton(login);
        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private LoginDialog() {}
}
