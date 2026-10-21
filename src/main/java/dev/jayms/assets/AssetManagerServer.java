package dev.jayms.assets;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** Single-user desktop service. No remote account or public hosting is involved. */
public final class AssetManagerServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final AssetCatalog catalog;
    private final Path profile;
    private final String token;
    public AssetManagerServer(Path profile) throws IOException {
        this.profile = profile;
        catalog = new AssetCatalog(profile);
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        token = HexFormat.of().formatHex(random);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 8);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }
    public URI browserURI() { return URI.create(origin()+"/#"+token); }
    public String origin() { return "http://127.0.0.1:"+server.getAddress().getPort(); }
    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var headers = exchange.getResponseHeaders();
            headers.set("Cache-Control", "no-store");
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
            if (!Objects.equals(exchange.getRequestHeaders().getFirst("Host"), "127.0.0.1:"+server.getAddress().getPort())) {
                send(exchange, 403, "text/plain", "Invalid host".getBytes(StandardCharsets.UTF_8)); return;
            }
            String path = exchange.getRequestURI().getPath();
            if (!path.startsWith("/api/")) {
                String resource = switch(path) { case "/" -> "index.html"; case "/app.js" -> "app.js"; case "/style.css" -> "style.css"; default -> null; };
                if (!exchange.getRequestMethod().equals("GET") || resource == null) { reply(exchange, 404, "Page not found"); return; }
                try (var in = AssetManagerServer.class.getResourceAsStream("/asset-manager/"+resource)) {
                    if (in == null) { reply(exchange, 500, "Asset Manager resources unavailable"); return; }
                    send(exchange, 200, resource.endsWith("js")?"text/javascript":resource.endsWith("css")?"text/css":"text/html", in.readAllBytes());
                }
                return;
            }
            if (!Objects.equals(exchange.getRequestHeaders().getFirst("Authorization"), "Bearer "+token)) { reply(exchange, 401, "Reopen Asset Manager from the Launcher"); return; }
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && !origin.equals(origin())) { reply(exchange, 403, "Invalid origin"); return; }
            try {
                var query = query(exchange.getRequestURI().getRawQuery());
                String method = exchange.getRequestMethod();
                if (path.equals("/api/catalog") && method.equals("GET")) {
                    json(exchange, "["+String.join(",", catalog.search(query.get("q")).stream().map(AssetManagerServer::summary).toList())+"]");
                } else if (path.equals("/api/model") && method.equals("GET")) {
                    var asset = catalog.get(query.get("id")); var tree = asset.model().voxels();
                    var voxels = new ArrayList<String>();
                    for(int y=0;y<tree.size();y++) for(int z=0;z<tree.size();z++) for(int x=0;x<tree.size();x++) {
                        int color=tree.get(x,y,z); if(color!=0) voxels.add("["+x+","+y+","+z+","+(color&0xffffff)+"]");
                    }
                    json(exchange, "{\"asset\":"+summary(asset)+",\"voxels\":["+String.join(",",voxels)+"]}");
                } else if (path.equals("/api/download") && method.equals("GET")) {
                    var asset = catalog.get(query.get("id")); var bytes = new ByteArrayOutputStream();
                    try (var out = new DataOutputStream(bytes)) { asset.model().write(out); }
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"model.vxm\"");
                    send(exchange, 200, "application/octet-stream", bytes.toByteArray());
                } else if (path.equals("/api/upload") && method.equals("POST")) {
                    byte[] bytes = exchange.getRequestBody().readNBytes(200005);
                    if (bytes.length>200004) throw new IOException("Model exceeds 200004-byte upload limit");
                    json(exchange, summary(catalog.upload(query.get("filename"),query.get("description"),query.get("author"),bytes)));
                } else if (path.equals("/api/import") && method.equals("POST")) {
                    json(exchange, "{\"message\":"+quote(catalog.addToGame(query.get("id"),query.get("game"),profile))+"}");
                } else reply(exchange, 405, "Unsupported action or method");
            } catch (IOException | IllegalArgumentException e) {
                reply(exchange, 400, e.getMessage()==null?"Invalid or truncated voxel model":e.getMessage());
            }
        }
    }
    private static Map<String,String> query(String raw) {
        var result = new HashMap<String,String>();
        if(raw!=null) for(String part:raw.split("&")) { var pair=part.split("=",2); result.put(URLDecoder.decode(pair[0],StandardCharsets.UTF_8),pair.length==2?URLDecoder.decode(pair[1],StandardCharsets.UTF_8):""); }
        return result;
    }
    private static String summary(AssetCatalog.Asset a) {
        return "{\"id\":"+quote(a.id())+",\"name\":"+quote(a.model().name())+",\"description\":"+quote(a.description())+",\"author\":"+quote(a.author())+",\"size\":"+a.model().voxels().size()+",\"occupied\":"+a.model().voxels().occupied()+",\"seed\":"+a.seed()+"}";
    }
    private static String quote(String s) {
        StringBuilder b=new StringBuilder("\"");
        for(char c:s.toCharArray()) { if(c=='"'||c=='\\') b.append('\\').append(c); else if(c<32) b.append(String.format("\\u%04x",(int)c)); else b.append(c); }
        return b.append('"').toString();
    }
    private static void json(HttpExchange e,String body) throws IOException { send(e,200,"application/json",body.getBytes(StandardCharsets.UTF_8)); }
    private static void reply(HttpExchange e,int code,String message) throws IOException { send(e,code,"application/json",("{\"error\":"+quote(message)+"}").getBytes(StandardCharsets.UTF_8)); }
    private static void send(HttpExchange e,int code,String type,byte[] bytes) throws IOException { e.getResponseHeaders().set("Content-Type",type+"; charset=utf-8"); e.sendResponseHeaders(code,bytes.length); e.getResponseBody().write(bytes); }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
    public static void open(Path profile) throws Exception {
        var service = new AssetManagerServer(profile);
        Runtime.getRuntime().addShutdownHook(new Thread(service::close));
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var frame = new javax.swing.JFrame("Voxel One Asset Manager");
            var panel = new javax.swing.JPanel(new java.awt.BorderLayout(12,12));
            panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(20,20,20,20));
            panel.add(new javax.swing.JLabel("Asset Manager is running. Close this window to stop it."),java.awt.BorderLayout.CENTER);
            var open = new javax.swing.JButton("Open Asset Manager");
            open.addActionListener(event -> browse(service.browserURI(),frame));
            panel.add(open,java.awt.BorderLayout.SOUTH);
            frame.setContentPane(panel); frame.pack(); frame.setLocationRelativeTo(null);
            frame.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosed(java.awt.event.WindowEvent event) { service.close(); } });
            frame.setVisible(true); browse(service.browserURI(),frame);
        });
    }
    private static void browse(URI uri,javax.swing.JFrame frame) {
        try {
            String browser = System.getenv("BROWSER");
            if (browser != null && !browser.isBlank()) {
                // BROWSER names one executable, never a shell expression or command template.
                if (!Files.isExecutable(Path.of(browser))) throw new IOException("Invalid browser executable");
                var process = new ProcessBuilder(browser, uri.toString())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD).start();
                if (process.waitFor(1500, TimeUnit.MILLISECONDS) && process.exitValue() != 0)
                    throw new IOException("Browser could not start");
            } else java.awt.Desktop.getDesktop().browse(uri);
        }
        catch (Exception e) { javax.swing.JOptionPane.showMessageDialog(frame,"Could not open your browser. Install or choose a default browser, then click Open Asset Manager."); }
    }
}
