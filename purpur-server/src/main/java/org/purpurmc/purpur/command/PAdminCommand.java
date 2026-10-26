package org.purpurmc.purpur.command;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.VoltPurHardware;
import org.purpurmc.purpur.VoltPurModules;
import org.purpurmc.purpur.util.VoltPurLogger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * VoltPur PAdmin WebUI Command (§7.1, S1, S2).
 *
 * Security hardening:
 * - Binds strictly to 127.0.0.1 (localhost only).
 * - Honors voltpur.yml padmin configuration (enabled, user, password, port).
 * - Enforces HTTP Basic Authentication across all endpoints.
 * - Refuses startup if padmin is disabled or password is blank.
 */
public class PAdminCommand extends Command {
    private static HttpServer server;
    private static boolean serverRunning = false;
    private static final String BIND_HOST = "127.0.0.1";

    public PAdminCommand(String name) {
        super(name);
        this.description = "VoltPur PAdmin WebUI";
        this.usageMessage = "/padmin [start|stop|status]";
        this.setPermission("voltpur.admin.padmin");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            return Arrays.asList("start", "stop", "status").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.hasPermission("voltpur.admin.padmin")) {
                player.sendMessage("§c[VoltPur] No permission for /padmin");
                return true;
            }
        }

        String sub = args.length > 0 ? args[0].toLowerCase() : "start";

        if (sub.equals("status")) {
            if (serverRunning && server != null) {
                int port = getPort();
                sender.sendMessage("§a[VoltPur] PAdmin WebUI is running on http://" + BIND_HOST + ":" + port);
                sender.sendMessage("§7Security: Bound to 127.0.0.1 (localhost only) | Basic Auth active (user: " + VoltPurConfig.padminUser + ")");
            } else {
                sender.sendMessage("§e[VoltPur] PAdmin WebUI is currently stopped.");
                sender.sendMessage("§7Config: enabled=" + VoltPurConfig.padminEnabled + ", port=" + getPort() + ", user=" + VoltPurConfig.padminUser);
            }
            return true;
        }

        if (sub.equals("stop")) {
            if (!serverRunning) {
                sender.sendMessage("§e[VoltPur] PAdmin WebUI is not running.");
                return true;
            }
            stopWebServer();
            sender.sendMessage("§a[VoltPur] PAdmin WebUI stopped.");
            return true;
        }

        if (sub.equals("start")) {
            if (serverRunning) {
                int port = getPort();
                sender.sendMessage("§e[VoltPur] PAdmin WebUI is already running at http://" + BIND_HOST + ":" + port);
                return true;
            }
            boolean started = startWebServer();
            if (started) {
                int port = getPort();
                sender.sendMessage("§a[VoltPur] PAdmin WebUI started securely at http://" + BIND_HOST + ":" + port);
                sender.sendMessage("§7Security: Bound strictly to 127.0.0.1 (localhost only) with Basic Authentication.");
                sender.sendMessage("§7Access via SSH tunnel or local reverse proxy using user '" + VoltPurConfig.padminUser + "'.");
            } else {
                sender.sendMessage("§c[VoltPur] PAdmin WebUI refused to start.");
                if (!VoltPurConfig.padminEnabled) {
                    sender.sendMessage("§cReason: modules.padmin.enabled is false in voltpur.yml.");
                } else if (VoltPurConfig.padminPassword == null || VoltPurConfig.padminPassword.trim().isEmpty()) {
                    sender.sendMessage("§cReason: modules.padmin.password is empty in voltpur.yml. A password is required for security.");
                } else {
                    sender.sendMessage("§cCheck console logs for details.");
                }
            }
            return true;
        }

        sender.sendMessage("§cUsage: " + usageMessage);
        return false;
    }

    private static int getPort() {
        return VoltPurConfig.padminPort > 0 ? VoltPurConfig.padminPort : 25567;
    }

    public static synchronized boolean startWebServer() {
        if (serverRunning) return true;

        if (!VoltPurConfig.padminEnabled) {
            Bukkit.getLogger().warning("[VoltPur-PAdmin] WebUI refused to start: modules.padmin.enabled is false in voltpur.yml.");
            return false;
        }

        if (VoltPurConfig.padminPassword == null || VoltPurConfig.padminPassword.trim().isEmpty()) {
            Bukkit.getLogger().warning("[VoltPur-PAdmin] WebUI refused to start: modules.padmin.password is empty in voltpur.yml for safety.");
            return false;
        }

        int port = getPort();
        try {
            // Bind strictly to 127.0.0.1 (never 0.0.0.0)
            server = HttpServer.create(new InetSocketAddress(BIND_HOST, port), 0);
            server.createContext("/", exchange -> {
                if (!checkAuthentication(exchange)) return;
                handleMain(exchange);
            });
            server.createContext("/api/status", exchange -> {
                if (!checkAuthentication(exchange)) return;
                handleStatus(exchange);
            });
            server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
            server.start();
            serverRunning = true;
            Bukkit.getLogger().info("[VoltPur-PAdmin] WebUI started on http://" + BIND_HOST + ":" + port + " (auth enabled, localhost only)");
            VoltPurLogger.recordSuccess("PAdminWebUI");
            return true;
        } catch (Exception e) {
            VoltPurLogger.error("PAdminWebUI", "Server startup failed", e);
            return false;
        }
    }

    public static synchronized void stopWebServer() {
        if (server != null) {
            try {
                server.stop(0);
            } catch (Exception ignored) {}
            server = null;
        }
        serverRunning = false;
        Bukkit.getLogger().info("[VoltPur-PAdmin] WebUI stopped.");
    }

    private static boolean checkAuthentication(HttpExchange exchange) throws IOException {
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String b64 = auth.substring(6).trim();
                byte[] decoded = Base64.getDecoder().decode(b64);
                String creds = new String(decoded, StandardCharsets.UTF_8);
                int colon = creds.indexOf(':');
                if (colon != -1) {
                    String user = creds.substring(0, colon);
                    String pass = creds.substring(colon + 1);
                    String expectedUser = VoltPurConfig.padminUser != null ? VoltPurConfig.padminUser : "admin";
                    String expectedPass = VoltPurConfig.padminPassword != null ? VoltPurConfig.padminPassword : "";
                    if (!expectedPass.isEmpty() && expectedUser.equals(user) && expectedPass.equals(pass)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"VoltPur PAdmin\", charset=\"UTF-8\"");
        byte[] resp = "401 Unauthorized: Valid VoltPur PAdmin credentials required.\n".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(401, resp.length);
        exchange.getResponseBody().write(resp);
        exchange.getResponseBody().close();
        return false;
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
            + "<p>Security: Bound to 127.0.0.1 (Localhost) | Basic Auth Enabled</p>"
            + "<p>Worlds: " + Bukkit.getWorlds().size() + " | Players: " + Bukkit.getOnlinePlayers().size() + "</p>"
            + "<hr><h3>Modules (honest status)</h3><ul>" + mods + "</ul>"
            + "<hr><p>API: <a href='/api/status'>/api/status</a> (JSON)</p></body></html>";
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
