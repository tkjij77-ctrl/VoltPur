package org.purpurmc.purpur;

import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.Executors;

/**
 * VoltPur ResourcePackHTTP - opt-in local pack server (default OFF).
 *
 * HARDENING CHANGES:
 *  1. STREAMING: the old handler did Files.readAllBytes() per request, so ten
 *     players downloading a 90 MB pack meant ~900 MB of heap and an OOM risk.
 *     Now the file is streamed and the response length is declared up front.
 *  2. UNGUESSABLE PATH: the pack is served at /pack-&lt;token&gt;.zip where the token
 *     is a random 64-bit hex string stored in voltpur.yml, so the endpoint is not
 *     a public open file server.
 *  3. HONEST GUIDANCE: prints the SHA-1 the client needs for resource-pack-sha1
 *     and states clearly that modern clients require an HTTPS URL, i.e. this local
 *     HTTP server must sit behind your panel/proxy (or host the pack externally).
 */
public final class VoltPurResourcePack {

    private static final String MODULE = "ResourcePackHTTP";
    private static HttpServer server;
    private static boolean running = false;

    private VoltPurResourcePack() {}

    public static void init() {
        if (running) return;
        if (!VoltPurConfig.resourcePackEnabled) {
            Bukkit.getLogger().info("[VoltPur-ResourcePack] Disabled (opt-in). Enable modules.resource-pack.enabled to use it.");
            return;
        }
        File file = new File(VoltPurConfig.resourcePackFile);
        if (!file.isFile()) {
            Bukkit.getLogger().info("[VoltPur-ResourcePack] Enabled but the file is missing: " + file.getPath()
                    + " - place the zip and restart.");
            return;
        }
        try {
            String token = VoltPurConfig.resourcePackToken;
            if (token == null || token.isBlank()) {
                Bukkit.getLogger().warning("[VoltPur-ResourcePack] No token generated - refusing to serve an open endpoint. "
                        + "Set modules.resource-pack.token in voltpur.yml.");
                return;
            }
            int port = VoltPurConfig.resourcePackPort;
            HttpServer local = HttpServer.create(new InetSocketAddress(port), 0);
            local.createContext("/pack-" + token + ".zip", exchange -> VoltPurGuard.run(MODULE, () -> {
                try {
                    serve(exchange, file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));
            local.setExecutor(Executors.newFixedThreadPool(4));
            local.start();
            server = local;
            running = true;
            VoltPurModules.setRuntime(MODULE, true);

            String sha1 = sha1(file);
            Bukkit.getLogger().info("[VoltPur-ResourcePack] Serving " + file.getName() + " ("
                    + (file.length() / 1024 / 1024) + " MB) on port " + port + " at path /pack-"
                    + token.substring(0, Math.min(6, token.length())) + "…zip");
            Bukkit.getLogger().info("[VoltPur-ResourcePack] IMPORTANT: clients need an HTTPS URL. Expose this port through "
                    + "your panel/reverse proxy, then set:");
            Bukkit.getLogger().info("[VoltPur-ResourcePack]   resource-pack=https://<your-domain>/pack-" + token + ".zip");
            Bukkit.getLogger().info("[VoltPur-ResourcePack]   resource-pack-sha1=" + sha1);
        } catch (Throwable t) {
            VoltPurGuard.failure(MODULE, t);
            Bukkit.getLogger().warning("[VoltPur-ResourcePack] Failed to start: " + t.getMessage());
        }
    }

    private static void serve(com.sun.net.httpserver.HttpExchange exchange, File file) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        if (!file.isFile()) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        long size = file.length();
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(200, size);
        try (InputStream in = Files.newInputStream(file.toPath());
             OutputStream out = exchange.getResponseBody()) {
            in.transferTo(out); // streamed, never buffered in heap
        }
    }

    private static String sha1(File file) {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            VoltPurGuard.failure(MODULE, e);
            return "unavailable";
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean stop() {
        if (!running || server == null) return false;
        server.stop(0);
        server = null;
        running = false;
        return true;
    }
}
