package org.purpurmc.purpur.command;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.VoltPurModules;
import org.purpurmc.purpur.VoltPurHardware;
import org.purpurmc.purpur.VoltPurConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Collections;

/**
 * VoltPur PAdmin WebUI.
 *
 * SECURITY (hardening per audit):
 *  - Off by default (modules.padmin.enabled must be true).
 *  - Binds ONLY to 127.0.0.1 (localhost), never 0.0.0.0.
 *  - Requires Basic Auth (user + non-empty password).
 *  - /api/status is protected by the same authenticator.
 */
public class PAdminCommand extends Command {
    private static HttpServer server;
    private static boolean serverRunning = false;

    public PAdminCommand(String name) {
        super(name);
        this.description = "VoltPur PAdmin WebUI (secure, localhost, auth)";
        this.usageMessage = "/padmin";
        this.setPermission("voltpur.admin.padmin");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.hasPermission("voltpur.admin.padmin")) {
                player.sendMessage("No permission");
                return true;
            }
        }
        startWebServer();
        if (!serverRunning) {
            sender.sendMessage("§cPAdmin could not start (disabled, no password, or port in use).");
            sender.sendMessage("§cEnable it: modules.padmin.enabled=true and set modules.padmin.password in voltpur.yml.");
            return true;
        }
        sender.sendMessage("§aVoltPur PAdmin WebUI started at http://127.0.0.1:" + VoltPurConfig.padminPort);
        sender.sendMessage("§7Secure: localhost-only + Basic Auth (user: " + VoltPurConfig.padminUser + ")");
        return true;
    }

    public static void startWebServer() {
        if (serverRunning) return;
        // Security: never expose if disabled or if no password set.
        if (!VoltPurConfig.padminEnabled) {
            Bukkit.getLogger().info("[VoltPur-PAdmin] Disabled (modules.padmin.enabled=false).");
            return;
        }
        if (VoltPurConfig.padminPassword == null || VoltPurConfig.padminPassword.isEmpty()) {
            Bukkit.getLogger().warning("[VoltPur-PAdmin] Refusing to start: no password set (modules.padmin.password).");
            return;
        }
        try {
            final int port = VoltPurConfig.padminPort;
            // Bind to loopback only (never 0.0.0.0).
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            final String user = VoltPurConfig.padminUser == null ? "admin" : VoltPurConfig.padminUser;
            final String pass = VoltPurConfig.padminPassword;
            server.createContext("/", exchange -> {
                // Apply Basic Auth to all contexts (main + api).
                if (!authorize(exchange, user, pass)) {
                    exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"padmin\"");
                    exchange.sendResponseHeaders(401, -1);
                    exchange.close();
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                if (path.startsWith("/api/")) handleStatus(exchange);
                else handleMain(exchange);
            });
            server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
            server.start();
            serverRunning = true;
            Bukkit.getLogger().info("[VoltPur-PAdmin] WebUI at http://127.0.0.1:" + port + " (localhost, auth required).");
        } catch (Exception e) {
            Bukkit.getLogger().warning("[VoltPur-PAdmin] Failed: " + e.getMessage());
            serverRunning = false;
        }
    }

    private static boolean authorize(HttpExchange exchange, String user, String pass) {
        try {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header == null || !header.startsWith("Basic ")) return false;
            String decoded = new String(java.util.Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
            int idx = decoded.indexOf(':');
            if (idx < 0) return false;
            String u = decoded.substring(0, idx);
            String p = decoded.substring(idx + 1);
            return java.util.Objects.equals(u, user) && java.util.Objects.equals(p, pass);
        } catch (Exception e) {
            return false;
        }
    }

    private static void handleMain(HttpExchange exchange) throws IOException {
        StringBuilder mods = new StringBuilder();
        for (String name : VoltPurModules.all().keySet()) {
            mods.append("<li>").append(VoltPurModules.line(name)).append("</li>");
        }
        String html = "<html><head><title>VoltPur PAdmin</title>"
            + "<style>body{font-family:sans-serif;background:#0f0f1a;color:#e0e0e0;padding:20px}"
            + "h1{color:#9b59b6}.ok{color:#6fbf73}.warn{color:#d8b95c}.plan{color:#d88c5c}</style></head>"
            + "<body><h1>[VoltPur] VoltPur PAdmin</h1>"
            + "<p>Version: " + VoltPur.VERSION + " (MC " + VoltPur.MC_VERSION + ")</p>"
            + "<p>Real modules: " + VoltPurModules.activeCount() + "/" + VoltPurModules.totalCount() + " ACTIVE</p>"
            + "<p>Worlds: " + Bukkit.getWorlds().size() + " | Players: " + Bukkit.getOnlinePlayers().size() + "</p>"
            + "<hr><h3>Modules (honest status)</h3><ul>" + mods + "</ul>"
            + "<hr><p>API: /api/status (JSON, auth required)</p></body></html>";
        send(exchange, html, "text/html; charset=utf-8");
    }

    private static void handleStatus(HttpExchange exchange) throws IOException {
        StringBuilder modsJson = new StringBuilder();
        for (String name : VoltPurModules.all().keySet()) {
            modsJson.append("{\"name\":\"").append(name)
                    .append("\",\"status\":\"").append(VoltPurModules.all().get(name)).append("\"},");
        }
        if (modsJson.length() > 0) modsJson.setLength(modsJson.length() - 1);
        VoltPurHardware.detect();
        String json = "{\"version\":\"" + VoltPur.VERSION + "\","
            + "\"mc\":\"" + VoltPur.MC_VERSION + "\","
            + "\"activeModules\":" + VoltPurModules.activeCount() + ","
            + "\"totalModules\":" + VoltPurModules.totalCount() + ","
            + "\"worlds\":" + Bukkit.getWorlds().size() + ","
            + "\"players\":" + Bukkit.getOnlinePlayers().size() + ","
            + "\"os\":\"" + VoltPurHardware.getOsName() + "\","
            + "\"cores\":" + VoltPurHardware.getCores() + ","
            + "\"ramMB\":" + VoltPurHardware.getPhysicalRamMB() + ","
            + "\"modules\":[" + modsJson + "]}";
        send(exchange, json, "application/json; charset=utf-8");
    }

    private static void send(HttpExchange exchange, String body, String contentType) throws IOException {
        byte[] resp = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, resp.length);
        exchange.getResponseBody().write(resp);
        exchange.getResponseBody().close();
    }
}
