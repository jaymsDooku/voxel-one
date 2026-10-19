package dev.jayms.launcher;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Desktop library. Network, verification and process startup stay off Swing's event thread. */
public final class HubFrame extends JFrame {
    private final Path cache;
    private Path installCache;
    private String availableRevision;
    private boolean clientOffline;
    private final Properties settings = new Properties();
    private final CatalogStore store;
    private final URI clientManifest;
    @FunctionalInterface interface Browser { void open(URI uri) throws Exception; }
    private final Browser browser;
    private HubCatalog catalog;
    private String catalogStatus;
    private final JPanel content = new JPanel(new BorderLayout(18, 18));
    private final JLabel status = new JLabel("Ready");
    private final JTextArea transfer = new JTextArea("No active download.", 8, 40);
    private final JButton cancel = new JButton("Cancel");
    private SwingWorker<?, ?> task;
    private volatile Updater activeUpdater;
    private String page = "Home";
    private HubCatalog.Entry selected;
    private boolean light;
    private Updater.Result installed;
    private final Platform platform = Platform.current();

    public HubFrame(Path cache) throws IOException { this(cache, Updater.MANIFEST); }
    public HubFrame(Path cache, URI clientManifest) throws IOException {
        this(cache, clientManifest, new CatalogStore(cache), uri -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IOException("Default browser unavailable");
            Desktop.getDesktop().browse(uri);
        });
    }
    HubFrame(Path cache, URI clientManifest, CatalogStore store, Browser browser) throws IOException {
        super("Voxel One Hub");
        try { UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel()); } catch (UnsupportedLookAndFeelException ignored) {}
        this.cache = cache; this.clientManifest = clientManifest; this.store = store; this.browser = browser;
        Path preferences = cache.resolve("hub.properties");
        if (Files.isRegularFile(preferences)) try (InputStream in = Files.newInputStream(preferences)) { settings.load(in); }
        if (!settings.containsKey("server")) {
            Path legacy = Path.of(System.getProperty("user.home"), ".voxel-one", "connections.properties");
            try (InputStream in = Files.newInputStream(legacy)) {
                byte[] bytes = in.readNBytes(4097);
                if (bytes.length <= 4096) {
                    Properties hints = new Properties(); hints.load(new ByteArrayInputStream(bytes));
                    String host = hints.getProperty("host", "");
                    if (host.length() <= 253 && host.matches("[a-zA-Z0-9.:-]+")) settings.setProperty("server", host);
                }
            } catch (IOException | IllegalArgumentException ignored) { }
        }
        installCache = Path.of(settings.getProperty("install", cache.toAbsolutePath().toString())).toAbsolutePath().normalize();
        light = settings.getProperty("theme", "dark").equals("light");
        theme();
        var snapshot = store.cached(); catalog = snapshot.catalog(); catalogStatus = snapshot.status();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(900,usable.width),Math.min(600,usable.height)));
        setSize(Math.min(1280,usable.width),Math.min(800,usable.height));
        setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout(24, 20)); root.setBorder(new EmptyBorder(22, 22, 18, 22));
        JPanel nav = new JPanel(); nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS)); nav.setPreferredSize(new Dimension(175, 0));
        JLabel brand = new JLabel("VOXEL ONE"); brand.setFont(brand.getFont().deriveFont(Font.BOLD, 21));
        nav.add(brand); nav.add(Box.createVerticalStrut(6)); nav.add(new JLabel("Your worlds. One hub.")); nav.add(Box.createVerticalStrut(35));
        String[] pages = {"Home", "Games", "Engine & Tools", "Downloads", "Settings / Help"};
        for (int i = 0; i < pages.length; i++) {
            final String destination = pages[i]; JButton button = button(destination, () -> showPage(destination));
            button.setMaximumSize(new Dimension(175, 44)); button.setAlignmentX(Component.LEFT_ALIGNMENT);
            nav.add(button); nav.add(Box.createVerticalStrut(10));
            getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, InputEvent.ALT_DOWN_MASK), destination);
            getRootPane().getActionMap().put(destination, new AbstractAction() { public void actionPerformed(ActionEvent e) { showPage(destination); } });
        }
        JScrollPane navigation=new JScrollPane(nav,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        navigation.setBorder(null); navigation.setPreferredSize(new Dimension(175,0));
        root.add(navigation, BorderLayout.WEST); root.add(content, BorderLayout.CENTER); root.add(status, BorderLayout.SOUTH);
        setContentPane(root); SwingUtilities.updateComponentTreeUI(this); transfer.setEditable(false); transfer.setLineWrap(true); transfer.setWrapStyleWord(true);
        cancel.addActionListener(e -> { if (activeUpdater != null) activeUpdater.cancel(); if (task != null) task.cancel(true); status.setText("Cancelling… verified installations will be kept."); });
        cancel.setEnabled(false); showPage("Home");
        addWindowListener(new WindowAdapter() { public void windowOpened(WindowEvent e) { if (selected == null) showPage(page); } public void windowClosed(WindowEvent e) { if (activeUpdater != null) activeUpdater.cancel(); if (task != null) task.cancel(true); } });
        loadInstalled();
        if (Boolean.parseBoolean(settings.getProperty("updates", "true"))) { refresh(); checkClient(); }
    }
    private void theme() {
        Color bg = light ? new Color(240, 244, 248) : new Color(17, 24, 38);
        Color surface = light ? Color.WHITE : new Color(29, 40, 57);
        Color fg = light ? new Color(22, 35, 50) : new Color(232, 241, 249);
        for (String key : List.of("Panel.background", "Viewport.background", "ScrollPane.background")) UIManager.put(key, new javax.swing.plaf.ColorUIResource(bg));
        for (String key : List.of("Label.foreground", "Button.foreground", "CheckBox.foreground", "TextArea.foreground", "TextField.foreground", "ComboBox.foreground", "TextField.caretForeground")) UIManager.put(key, new javax.swing.plaf.ColorUIResource(fg));
        for (String key : List.of("Button.background", "TextArea.background", "TextField.background", "ComboBox.background", "CheckBox.background")) UIManager.put(key, new javax.swing.plaf.ColorUIResource(surface));
        UIManager.put("Button.gradient", List.of(0f, 0f, surface, surface, surface));
        UIManager.put("CheckBox.gradient", List.of(0f, 0f, surface, surface, surface));
        UIManager.put("ToggleButton.gradient", List.of(0f, 0f, surface, surface, surface));
        Color focus = light ? new Color(0, 100, 115) : new Color(46, 208, 225);
        UIManager.put("Button.focus", focus); UIManager.put("CheckBox.focus", focus); UIManager.put("Label.font", new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        UIManager.put("Button.font", new Font(Font.SANS_SERIF, Font.BOLD, 14));
    }
    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text); b.setMargin(new Insets(11, 14, 11, 14)); b.addActionListener(e -> action.run());
        b.setAlignmentX(Component.LEFT_ALIGNMENT); b.getAccessibleContext().setAccessibleName(text); return b;
    }
    private JLabel title(String text, int size) { JLabel label = new JLabel(); label.putClientProperty("html.disable", Boolean.TRUE); label.setText(text); label.setAlignmentX(Component.LEFT_ALIGNMENT); label.setOpaque(false); label.setFont(label.getFont().deriveFont(Font.BOLD, size)); return label; }
    private JTextArea prose(String text) { JTextArea area = new JTextArea(Math.max(1, (int)text.lines().count() + text.length()/60), 40);
        ((javax.swing.text.DefaultCaret)area.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        area.setText(text); area.setMinimumSize(new Dimension(0, 0)); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setOpaque(false); area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15)); area.setAlignmentX(Component.LEFT_ALIGNMENT); area.setBorder(new EmptyBorder(8, 0, 12, 0)); return area; }
    public void showPage(String name) {
        page = name; selected = null; content.removeAll(); content.add(title(name, 30), BorderLayout.NORTH);
        switch (name) {
            case "Home", "Games" -> library(name.equals("Home"));
            case "Engine & Tools" -> tools();
            case "Downloads" -> downloads();
            default -> preferences();
        }
        applyColors(getContentPane()); content.revalidate(); content.repaint();
    }
    private void applyColors(Component component) {
        Color bg = light ? new Color(240, 244, 248) : new Color(17, 24, 38);
        Color fg = light ? new Color(22, 35, 50) : new Color(232, 241, 249);
        Color surface = light ? Color.WHITE : new Color(29, 40, 57);
        component.setForeground(fg);
        component.setBackground(component instanceof AbstractButton || component instanceof javax.swing.text.JTextComponent ? surface : bg);
        if (component instanceof Container container) for (Component child : container.getComponents()) applyColors(child);
    }
    private JPanel column() { JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); return p; }
    private void library(boolean home) {
        JPanel body = new JPanel(new BorderLayout(0, 18));
        JPanel header = column();
        if (home) {
            header.add(title("Make something extraordinary.", 25));
            header.add(prose("Build a living city, explore the sandbox, or take flight among the stars."));
            String last = settings.getProperty("last", "city");
            catalog.entries().stream().filter(e -> e.id().equals(last)).findFirst().ifPresent(e -> header.add(button("Continue playing · " + e.title(), () -> detail(e))));
        }
        JTextField search = new JTextField(); search.getAccessibleContext().setAccessibleName("Search games");
        if (!home) { header.add(new JLabel("Search games")); header.add(search); }
        header.add(prose(catalogStatus)); body.add(header, BorderLayout.NORTH);
        JPanel cards = new CardsPanel();
        Runnable populate = () -> {
            cards.removeAll(); String query = search.getText().toLowerCase(Locale.ROOT);
            for (var entry : catalog.entries()) {
                if (entry.id().equals("engine-editor") || !(entry.title() + " " + entry.summary()).toLowerCase(Locale.ROOT).contains(query)) continue;
                cards.add(card(entry));
            }
            if (cards.getComponentCount() == 0) cards.add(prose(catalog.entries().isEmpty() ? "No published games are available. Refresh the library later." : "No games match your search."));
            cards.revalidate(); cards.repaint();
        };
        search.getDocument().addDocumentListener(new DocumentListener() { public void insertUpdate(DocumentEvent e) { populate.run(); } public void removeUpdate(DocumentEvent e) { populate.run(); } public void changedUpdate(DocumentEvent e) { populate.run(); } });
        populate.run(); JScrollPane scroll = new JScrollPane(cards); scroll.setBorder(null); scroll.getVerticalScrollBar().setUnitIncrement(24); body.add(scroll);
        if (home) {
            String news = catalog.entries().isEmpty() ? "No published games are available. Installed clients and saves are kept." : catalog.entries().get(0).notes();
            if (news.length() > 160) news = news.substring(0, 157) + "…";
            body.add(prose("What's new\n" + news), BorderLayout.SOUTH);
        }
        content.add(body);
    }
    private JPanel card(HubCatalog.Entry e) {
        JPanel card = new JPanel(new BorderLayout(8, 8)); card.setBorder(new EmptyBorder(12, 12, 12, 12));
        card.add(new Cover(e.artwork(), e.title()), BorderLayout.NORTH);
        JPanel text = column(); text.add(title(e.title(), 21));
        JTextArea summary=prose(e.summary());summary.setRows(3);text.add(summary);
        text.add(new JLabel(availability(e)));
        card.add(text); card.add(button("View game →", () -> detail(e)), BorderLayout.SOUTH); return card;
    }
    private String availability(HubCatalog.Entry e) {
        if (e.adapter().equals("browser") && e.source().equals("unpublished")) return "Temporarily unavailable · web publication unverified";
        if (!e.supported(platform)) return "Unsupported platform or launch target";
        if (e.adapter().equals("browser")) return "Web game · network required";
        if (e.adapter().equals("docs")) return "Documentation";
        if (installed == null) return clientOffline ? "Temporarily unavailable · no installed client" : "Desktop · Install to play";
        if (!e.clientSources().contains(installed.revision())) return "Desktop · Compatible client required";
        if (clientOffline) return "Desktop · Offline cached";
        if (availableRevision != null && !e.clientSources().contains(availableRevision)) return "Desktop · Ready to play · catalog refresh needed";
        if (availableRevision != null && !availableRevision.equals(installed.revision())) return "Desktop · Update available";
        return "Desktop · Ready to play";
    }
    public void detail(HubCatalog.Entry e) {
        selected = e; content.removeAll(); content.add(title(e.title(), 30), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(18, 18)); body.add(new Cover(e.artwork(), e.title()), BorderLayout.NORTH);
        JPanel info = column();
        String previewKey=Set.of("city","sandbox").contains(e.target())?e.target():e.id().equals("space-invaders")?"space-invaders":null;
        if (previewKey!=null) {
            var resource = HubFrame.class.getResource(previewKey+"-gameplay.png");
            if (resource != null) {
                Image image = new ImageIcon(resource).getImage().getScaledInstance(480, 270, Image.SCALE_SMOOTH);
                JLabel preview = new JLabel(new ImageIcon(image)); preview.setAlignmentX(Component.LEFT_ALIGNMENT);
                preview.getAccessibleContext().setAccessibleName(e.title()+" gameplay screenshot"); info.add(preview);
            }
        }
        info.add(prose(e.summary())); info.add(title(availability(e), 18));
        info.add(prose("Controls & modes\n" + e.controls() + "\n\nRelease notes\n" + e.notes()));
        info.add(prose("Available build: " + e.build() + "\nAvailable engine: " + (availableRevision == null ? "Not checked / unavailable" : availableRevision) + "\nInstalled engine: " + (installed == null ? "Not installed" : installed.revision())
                + "\nSource: " + e.source() + "\nCatalog: " + catalog.version() + " · Hub: 1"));
        body.add(new JScrollPane(info));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        boolean nativeGame = e.adapter().equals("native");
        JCheckBox offline = new JCheckBox("Play offline", true); JTextField host = new JTextField(settings.getProperty("server", "198.100.154.156"), 16);
        JTextField port = new JTextField(settings.getProperty(e.id()+".port", e.target().equals("sandbox") ? "25565" : "25566"), 5);
        host.getAccessibleContext().setAccessibleName("Server"); port.getAccessibleContext().setAccessibleName("Port");
        if (nativeGame) { actions.add(offline); actions.add(new JLabel("Server")); actions.add(host); actions.add(new JLabel("Port")); actions.add(port); }
        JButton play = button(nativeGame ? "Play / Install" : "Open in browser", () -> {
            try { launch(e, offline.isSelected(), host.getText().trim(), portNumber(port.getText().trim(), offline.isSelected())); }
            catch (IllegalArgumentException ex) { status.setText("Enter a valid server and port (1–65535)."); }
        }); play.setEnabled(e.supported(platform)); actions.add(play); body.add(actions, BorderLayout.SOUTH);
        content.add(body); applyColors(getContentPane()); content.revalidate(); content.repaint();
    }
    private void tools() {
        JPanel body = column(); body.add(title("The engine behind your worlds", 24));
        body.add(prose("Installed engine: " + (installed == null ? "Not installed" : installed.revision()) + "\nAvailable engine: " + (availableRevision == null ? "Not checked / unavailable" : availableRevision) + ""));
        body.add(prose("Shipped tools\nScene and voxel modelling, lights, materials, and cameras are available in the engine workspace. Terrain, biomes, horizon and reflections can be explored in the sandbox. Use the editor's on-screen help."));
        catalog.entries().stream().filter(e -> e.target().equals("editor") || e.target().equals("sandbox")).forEach(e -> body.add(button("Open " + e.title(), () -> detail(e))));
        body.add(button("Engine guide", () -> browse("https://github.com/jaymsDooku/voxel-one/blob/master/ENGINE_EDITOR.txt")));
        content.add(body);
    }
    private void downloads() {
        JPanel body = new JPanel(new BorderLayout(12, 12)); body.add(new JScrollPane(transfer));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT)); buttons.add(cancel);
        buttons.add(button("Retry / Repair client", () -> install(null, List.of()))); buttons.add(button("Refresh library", this::refresh));
        body.add(buttons, BorderLayout.SOUTH); content.add(body);
    }
    private void preferences() {
        JPanel body = column(); body.add(prose("Client install location\n" + installCache + "\n\nGame saves, controls, username and server trust remain in your existing .voxel-one folder. Cache cleanup does not remove saves."));
        body.add(button("Choose client install folder", () -> {
            if (task != null && !task.isDone()) { status.setText("Wait for the current download to finish or cancel it."); return; }
            JFileChooser chooser = new JFileChooser(installCache.toFile()); chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                Path target = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
                if (!Files.isDirectory(target) || !Files.isWritable(target)) { status.setText("Choose a writable folder."); return; }
                installCache = target; settings.setProperty("install", target.toString()); save(); installed = null; loadInstalled(); showPage(page);
            }
        }));
        JCheckBox updates = new JCheckBox("Check library at startup and client before Play", Boolean.parseBoolean(settings.getProperty("updates", "true")));
        updates.addActionListener(e -> { settings.setProperty("updates", Boolean.toString(updates.isSelected())); save(); }); body.add(updates);
        JCheckBox close = new JCheckBox("Close hub after starting a game", Boolean.parseBoolean(settings.getProperty("close", "false")));
        close.addActionListener(e -> { settings.setProperty("close", Boolean.toString(close.isSelected())); save(); }); body.add(close);
        JCheckBox theme = new JCheckBox("Light theme", light); theme.addActionListener(e -> { light = theme.isSelected(); settings.setProperty("theme", light ? "light" : "dark"); save(); theme(); SwingUtilities.updateComponentTreeUI(this); showPage(page); }); body.add(theme);
        body.add(prose("Reduced motion: always on. No animated backgrounds or transitions.\nKeyboard: Tab moves focus; Enter/Space activates buttons; Alt+1…5 opens pages.\nDiagnostics: platform " + platform.id() + ", Java " + Runtime.version().feature() + ".\nPasswords stay in the existing game sign-in window. Browser sign-in stays in the browser."));
        body.add(button("Refresh library", this::refresh)); content.add(new JScrollPane(body));
    }
    private void save() {
        try { Files.createDirectories(cache); Path temp = Files.createTempFile(cache, "preferences-", ".tmp");
            try { try (OutputStream out = Files.newOutputStream(temp)) { settings.store(out, "Hub preferences; no account credentials"); }
                Files.move(temp, cache.resolve("hub.properties"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException e) { status.setText("Preferences could not be saved."); }
    }
    private void loadInstalled() {
        new SwingWorker<Updater.Result, Void>() {
            protected Updater.Result doInBackground() { return new Updater(installCache, clientManifest).installed(platform); }
            protected void done() { try { installed = get(); if (selected == null) showPage(page); } catch (Exception ignored) {} }
        }.execute();
    }
    private void checkClient() {
        new SwingWorker<Updater.Available, Void>() {
            protected Updater.Available doInBackground() throws Exception { return new Updater(installCache, clientManifest).available(platform); }
            protected void done() {
                try { availableRevision = get().revision(); clientOffline = false; }
                catch (Exception e) { clientOffline = true; }
                if (selected == null) showPage(page);
            }
        }.execute();
    }
    public void refresh() {
        new SwingWorker<CatalogStore.Snapshot, Void>() {
            protected CatalogStore.Snapshot doInBackground() throws Exception { return store.refresh(); }
            protected void done() { try { var result = get(); catalog = result.catalog(); catalogStatus = result.status(); }
                catch (Exception e) { catalogStatus = "Offline cached / bundled library · catalog service unavailable"; }
                status.setText(catalogStatus); if (selected == null) showPage(page);
            }
        }.execute();
    }
    private void browse(String url) {
        if (!HubCatalog.safeWeb(url)) { status.setText("Unsupported web target"); return; }
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { browser.open(URI.create(url)); return null; }
            protected void done() { try { get(); status.setText("Opened in your browser."); } catch (Exception e) { status.setText("Could not open the default browser. Try again from the website."); } }
        }.execute();
    }
    private static int portNumber(String value, boolean offline) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { if (offline) return 0; throw e; }
    }
    private void launch(HubCatalog.Entry entry, boolean offline, String host, int port) {
        if (task != null && !task.isDone()) { status.setText("A client operation is already running."); return; }
        String id = entry.id();
        boolean updates = Boolean.parseBoolean(settings.getProperty("updates", "true"));
        HubCatalog current = catalog;
        String currentStatus = catalogStatus;
        status.setText("Checking published library…");
        task = new SwingWorker<CatalogStore.Snapshot, Void>() {
            protected CatalogStore.Snapshot doInBackground() throws Exception {
                if (!updates) return new CatalogStore.Snapshot(current, currentStatus);
                try { return store.refresh(); }
                catch (IOException unavailable) {
                    var cached = store.cached();
                    return new CatalogStore.Snapshot(cached.catalog(), "Offline cached / bundled library · catalog service unavailable");
                }
            }
            protected void done() {
                try {
                    var snapshot = get(); catalog = snapshot.catalog(); catalogStatus = snapshot.status();
                    // Never execute the entry captured by an old card/detail button.
                    var fresh = catalog.entries().stream().filter(e -> e.id().equals(id)).findFirst();
                    if (fresh.isEmpty() || !fresh.get().supported(platform)) {
                        status.setText("This experience is no longer available for this platform. Refresh the library."); return;
                    }
                    if (selected != null && selected.id().equals(id)) detail(fresh.get());
                    launchResolved(fresh.get(), offline, host, port);
                } catch (java.util.concurrent.CancellationException ignored) {
                    status.setText("Cancelled");
                } catch (Exception failure) {
                    status.setText("Could not check the library or launch this experience. Try again.");
                }
            }
        }; task.execute();
    }
    private void launchResolved(HubCatalog.Entry entry, boolean offline, String host, int port) {
        if (!entry.supported(platform)) return;
        settings.setProperty("last", entry.id()); save();
        if (!entry.adapter().equals("native")) { browse(entry.target()); return; }
        List<String> args = entry.arguments(offline, host, port);
        if (host.length() <= 253 && host.matches("[a-zA-Z0-9.:-]+") && port >= 1 && port <= 65535) {
            settings.setProperty("server", host); settings.setProperty(entry.id()+".port", Integer.toString(port)); save();
        }
        install(entry, args);
    }
    private void install(HubCatalog.Entry entry, List<String> args) {
        if (task != null && !task.isDone()) { status.setText("A client operation is already running."); return; }
        transfer.setText("Checking shared client…\n"); cancel.setEnabled(true); showPage("Downloads");
        task = new SwingWorker<Updater.Result, String>() {
            protected Updater.Result doInBackground() throws Exception {
                Updater updater = new Updater(installCache, entry != null && clientManifest.equals(Updater.MANIFEST) ? entry.clientManifest() : clientManifest); activeUpdater = updater;
                Updater.Result result = Boolean.parseBoolean(settings.getProperty("updates", "true")) || entry == null ? null : updater.installed(platform);
                if (result == null) result = updater.update(platform, this::publish, entry == null ? null : entry.clientSources());
                if (isCancelled()) throw new java.util.concurrent.CancellationException();
                if (entry != null) {
                    entry.requireClient(result);
                    Process process = new ProcessBuilder(Launcher.command(result.client(), platform, args)).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    if (process.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS) && process.exitValue() != 0) throw new IOException("Game could not start. Check graphics support and client/server compatibility.");
                }
                return result;
            }
            protected void process(List<String> messages) { for (String message : messages) transfer.append(message + "\n"); status.setText(messages.get(messages.size() - 1)); }
            protected void done() {
                cancel.setEnabled(false); activeUpdater = null;
                try { installed = get(); clientOffline = !installed.warning().isBlank(); if (!clientOffline) availableRevision = installed.revision(); transfer.append(entry == null ? "Client ready.\n" : "Game started.\n"); status.setText(installed.warning().isBlank() ? "Ready to play" : installed.warning()); if (entry != null && Boolean.parseBoolean(settings.getProperty("close", "false"))) dispose(); }
                catch (java.util.concurrent.CancellationException e) { transfer.append("Cancelled. Previous verified client kept.\n"); status.setText("Cancelled"); }
                catch (Exception e) { transfer.append("Operation failed. Retry to repair; previous verified client kept.\n"); Throwable cause = e.getCause() == null ? e : e.getCause();
                    String message = cause.getMessage();
                    status.setText(message != null && message.contains("Catalog/client mismatch")
                            ? "Catalog/client mismatch. Refresh the catalog or install a compatible client." : "Could not install or start the client.");
                    if (message != null && message.contains("Catalog/client mismatch")) transfer.append("Catalog/client mismatch. No incompatible client was launched.\n"); }
            }
        }; task.execute();
    }
    private static final class CardsPanel extends JPanel implements Scrollable {
        CardsPanel() { super(new GridLayout(0, 2, 18, 18)); }
        private void columns() { ((GridLayout)getLayout()).setColumns(getWidth()<900?1:2); }
        public Dimension getPreferredSize() { columns();return super.getPreferredSize(); }
        public void doLayout() { columns();super.doLayout(); }
        public Dimension getPreferredScrollableViewportSize() { return new Dimension(700, 420); }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return 160; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
    /** Original vector-like voxel cover art, drawn locally before any network request. */
    static final class Cover extends JComponent {
        private final String kind, text;
        Cover(String kind, String text) { this.kind = kind; this.text = text; setPreferredSize(new Dimension(340, 165)); getAccessibleContext(); }
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(); g.setColor(new Color(24, 40, 66)); g.fillRoundRect(0, 0, w, h, 18, 18);
            g.setColor(new Color(45, 207, 222));
            if (kind.equals("space-invaders")) {
                for (int i = 0; i < 35; i++) g.fillRect((i * 83 + 21) % Math.max(w, 1), (i * 37 + 11) % Math.max(h, 1), 2, 2);
                int[][] alien = {{0,0,1,0,0,0,1,0},{0,1,1,1,1,1,1,0},{1,1,0,1,1,0,1,1},{1,1,1,1,1,1,1,1},{0,1,0,0,0,0,1,0}};
                for (int y = 0; y < alien.length; y++) for (int x = 0; x < 8; x++) if (alien[y][x] == 1) g.fillRect(w/2 - 64 + x*16, 27+y*16, 13, 13);
            } else if (kind.equals("sandbox")) {
                for (int row=0;row<4;row++) for(int col=0;col<5;col++) {
                    int x=w/2+(col-row)*24-35, y=48+(col+row)*11;
                    int height=18+((row*7+col*11)%3)*13;
                    g.setColor(new Color(119, 205, 138)); g.fillPolygon(new int[]{x,x+24,x+48,x+24},new int[]{y-height,y-height-12,y-height,y-height+12},4);
                    g.setColor(new Color(44, 120, 117)); g.fillPolygon(new int[]{x,x+24,x+24,x},new int[]{y-height,y-height+12,y+12,y},4);
                    g.setColor(new Color(61, 157, 150)); g.fillPolygon(new int[]{x+24,x+48,x+48,x+24},new int[]{y-height+12,y-height,y,y+12},4);
                }
            } else {
                for (int i = 0; i < 12; i++) {
                    int x = 15 + i * Math.max(w/13, 14), height = kind.equals("city") ? 25 + (i*47)%80 : 20 + (i*23)%45;
                    g.setColor(new Color(38+i*5, 105+i*6, 128+i*7)); g.fillRect(x, h-35-height, Math.max(w/15, 12), height);
                    g.setColor(new Color(103, 234, 229)); for (int y = h-35-height+8; y < h-42; y += 14) g.fillRect(x+5, y, 5, 5);
                }
            }
            g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14)); g.drawString(text.toUpperCase(Locale.ROOT), 18, h-15); g.dispose();
        }
    }
}
