package org.purpurmc.purpur;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;

/**
 * VoltPur Resource Pack HTTP - opt-in (default OFF).
 *
 * Serves a local resource-pack zip over HTTP so it can be pushed to players via
 * the server.properties `resource-pack` setting. Uses the built-in java.net
 * HttpServer (same approach as PAdmin WebUI), so it works without any plugin.
 * Configure in voltpur.yml under modules.resource-pack.*.
 */
public final class VoltPurResourcePack {
    private static HttpServer server;
    private static boolean running = false;

    private VoltPurResourcePack() {}

    public static void init() {
        if (running) return;
        if (!VoltPurConfig.resourcePackEnabled) return;
        File file = new File(VoltPurConfig.resourcePackFile);
        if (!file.isFile()) {
            Bukkit.getLogger().info("[VoltPur-ResourcePack] Enabled but file not found: " + file.getPath()
                    + " - place the zip and restart.");
            return;
        }
        try {
            int port = VoltPurConfig.resourcePackPort;
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/pack.zip", VoltPurResourcePack::serve);
            server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
            server.start();
            running = true;
            String ip = "localhost";
            try { ip = java.net.InetAddress.getLocalHost().getHostAddress(); } catch (Exception ignored) {}
            String url = "http://" + ip + ":" + port + "/pack.zip";
            Bukkit.getLogger().info("[VoltPur-ResourcePack] Active - serving " + file.getName() + " at " + url);
            Bukkit.getLogger().info("[VoltPur-ResourcePack] Set resource-pack=" + url + " in server.properties to push it to players.");
        } catch (Exception e) {
            Bukkit.getLogger().warning("[VoltPur-ResourcePack] Failed to start: " + e.getMessage());
        }
    }

    private static void serve(HttpExchange ex) {
        try {
            File file = new File(VoltPurConfig.resourcePackFile);
            if (!file.isFile()) { ex.sendResponseHeaders(404, -1); ex.close(); return; }
            byte[] data = Files.readAllBytes(file.toPath());
            ex.getResponseHeaders().set("Content-Type", "application/zip");
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        } catch (Exception e) {
            try { ex.close(); } catch (Exception ignored) {}
        }
    }
}
