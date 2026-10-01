package dev.jayms.net;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.*;

/** TLS with an explicit SHA-256 certificate pin (self-signed VPS certificates supported). */
public final class SecureTransport {
    public record ServerIdentity(SSLContext context, String fingerprint) {}

    public static ServerIdentity server(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path store = directory.resolve("server.p12"),
                passwordFile = directory.resolve("server.password");
        if (!Files.exists(store)) {
            byte[] bytes = new byte[24];
            new SecureRandom().nextBytes(bytes);
            Files.writeString(passwordFile, Base64.getEncoder().encodeToString(bytes));
            AccountStore.privateFile(passwordFile);
            String executable =
                    Path.of(
                                    System.getProperty("java.home"),
                                    "bin",
                                    System.getProperty("os.name").startsWith("Windows")
                                            ? "keytool.exe"
                                            : "keytool")
                            .toString();
            Process p =
                    new ProcessBuilder(
                                    executable,
                                    "-genkeypair",
                                    "-alias",
                                    "server",
                                    "-keyalg",
                                    "RSA",
                                    "-keysize",
                                    "2048",
                                    "-validity",
                                    "3650",
                                    "-dname",
                                    "CN=Voxel One",
                                    "-storetype",
                                    "PKCS12",
                                    "-keystore",
                                    store.toAbsolutePath().toString(),
                                    "-storepass:file",
                                    passwordFile.toAbsolutePath().toString())
                            .redirectErrorStream(true)
                            .start();
            if (!p.waitFor(30, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("Certificate generation timed out");
            }
            if (p.exitValue() != 0)
                throw new IOException(
                        "Certificate generation failed: "
                                + new String(p.getInputStream().readAllBytes()));
            AccountStore.privateFile(store);
        }
        char[] password = Files.readString(passwordFile).trim().toCharArray();
        try {
            KeyStore keys = KeyStore.getInstance("PKCS12");
            try (var in = Files.newInputStream(store)) {
                keys.load(in, password);
            }
            KeyManagerFactory factory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(keys, password);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(factory.getKeyManagers(), null, new SecureRandom());
            return new ServerIdentity(
                    context, fingerprint((X509Certificate) keys.getCertificate("server")));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static String fingerprint(X509Certificate cert) throws CertificateException {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException(e);
        }
    }

    private static SSLContext clientContext(String pin) throws IOException {
        try {
            TrustManager trust =
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] c, String a)
                                throws CertificateException {
                            throw new CertificateException("Not a server context");
                        }

                        public void checkServerTrusted(X509Certificate[] c, String a)
                                throws CertificateException {
                            if (c.length == 0) throw new CertificateException("No certificate");
                            c[0].checkValidity();
                            if (pin != null
                                    && !MessageDigest.isEqual(
                                            pin.toLowerCase(Locale.ROOT)
                                                    .replace(":", "")
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .US_ASCII),
                                            fingerprint(c[0])
                                                    .getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .US_ASCII)))
                                throw new CertificateException(
                                        "Server certificate changed or fingerprint does not match");
                        }
                    };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {trust}, new SecureRandom());
            return context;
        } catch (GeneralSecurityException e) {
            throw new IOException("TLS unavailable", e);
        }
    }

    public static SSLSocket connect(String host, int port, String pin) throws IOException {
        if (pin == null || !pin.replace(":", "").matches("(?i)[0-9a-f]{64}"))
            throw new IOException("A trusted server fingerprint is required");
        return socket(host, port, pin);
    }

    private static SSLSocket socket(String host, int port, String pin) throws IOException {
        SSLSocket socket = (SSLSocket) clientContext(pin).getSocketFactory().createSocket();
        try {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(15000);
            socket.setTcpNoDelay(true);
            socket.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
            socket.startHandshake();
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /**
     * Inspection sends no account credentials; UI must confirm the returned fingerprint before
     * login.
     */
    public static String inspect(String host, int port) throws IOException {
        try (SSLSocket socket = socket(host, port, null)) {
            return fingerprint((X509Certificate) socket.getSession().getPeerCertificates()[0]);
        } catch (CertificateException e) {
            throw new IOException(e);
        }
    }

    private SecureTransport() {}
}
