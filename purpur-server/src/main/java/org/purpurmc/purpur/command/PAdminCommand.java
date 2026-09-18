package org.purpurmc.purpur.command;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.VoltPurGuard;
import org.purpurmc.purpur.VoltPurHardware;
import org.purpurmc.purpur.VoltPurModules;
import org.purpurmc.purpur.VoltPurPlugin;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VoltPur PAdmin WebUI - diagnostics only, and safe by construction.
 *
 * SECURITY CONTRACT (enforced here, not just documented):
 *  1. OFF unless modules.padmin.enabled=true AND modules.padmin.password is set.
 *  2. Binds to the LOOPBACK address only (127.0.0.1 / ::1) - never 0.0.0.0.
 *  3. Every request (page + /api/status) requires HTTP Basic Auth; password
 *     comparison is constant-time (MessageDigest.isEqual).
 *  4. Read-only: no endpoint mutates server state.
 *  5. Bukkit objects (worlds, players, TPS) are NEVER read from the HTTP worker
 *     thread. A 1-second snapshot is built on the main thread and served from an
 *     immutable record, because Paper's API is not thread-safe off-thread.
 *  6. Bounded worker pool, GET-only, no-store responses, HTML escaped output.
 */
public class PAdminCommand extends Command {

    private static volatile HttpServer server;
    private static volatile boolean running = false;
    private static volatile Snapshot snapshot = Snapshot.empty();
    private static int refreshTaskId = -1;
    private static final int MAX_WORKER_THREADS = 2;

    /** Immutable main-thread snapshot; the only data the HTTP layer may read. */
    private record Snapshot(String version, String mc, int activeModules, int totalModules,
                            List<String> moduleLines, List<String> worldLines, int worlds, int players,
                            String os, String arch, int cores, long ramMB, double[] tps) {
        static Snapshot empty() {
            return new Snapshot(VoltPur.VERSION, VoltPur.mcVersion(), 0, 0, List.of(), List.of(),
                    0, 0, "?", "?", 0, -1L, new double[]{0d, 0d, 0d});
        }
    }

    public PAdminCommand(String name) {
        super(name);
        this.description = "VoltPur PAdmin WebUI (loopback-only, authenticated, read-only)";
        this.usageMessage = "/padmin [start|stop|status]";
        this.setPermission("voltpur.admin.padmin");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String option : List.of("start", "stop", "status")) {
                if (option.startsWith(args[0].toLowerCase())) out.add(option);
            }
            return out;
        }
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player) && args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            if (stopWebServer()) sender.sendMessage("§aVoltPur PAdmin WebUI stopped.");
            else sender.sendMessage("§7VoltPur PAdmin WebUI was not running.");
            return true;
        }

        if (sender instanceof Player player && !player.hasPermission("voltpur.admin.padmin")) {
            sender.sendMessage("§cNo permission (voltpur.admin.padmin).");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            if (stopWebServer()) sender.sendMessage("§aVoltPur PAdmin WebUI stopped.");
            else sender.sendMessage("§7VoltPur PAdmin WebUI was not running.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§6VoltPur PAdmin: " + (running ? "§aRUNNING" : "§7stopped")
                    + " §7| configured port: " + VoltPurConfig.padminPort
                    + " §7| enabled: " + VoltPurConfig.padminEnabled
                    + " §7| password set: " + (!VoltPurConfig.padminPassword.isEmpty()));
            return true;
        }

        if (!VoltPurConfig.padminEnabled) {
            sender.sendMessage("§cPAdmin is disabled. Set modules.padmin.enabled=true in voltpur.yml first.");
            sender.sendMessage("§7It listens on loopback only and always requires Basic Auth.");
            return true;
        }
        if (VoltPurConfig.padminPassword.isEmpty()) {
            sender.sendMessage("§cPAdmin refuses to start without a password.");
            sender.sendMessage("§7Set modules.padmin.password (or ${ENV_VAR}) in voltpur.yml.");
            return true;
        }

        if (!startWebServer()) {
            sender.sendMessage("§cPAdmin failed to start - check the server log for the reason.");
            return true;
        }
        sender.sendMessage("§aVoltPur PAdmin WebUI: §fhttp://127.0.0.1:" + VoltPurConfig.padminPort
                + " §7(user: " + VoltPurConfig.padminUser + ", Basic Auth required)");
        sender.sendMessage("§7Loopback only: reach it through an SSH tunnel, never expose the port publicly. §7Read-only.");
        return true;
    }

    /** Starts the local-only WebUI. Returns true when it is running. */
    public static boolean startWebServer() {
        if (running) return true;
        if (!VoltPurConfig.padminEnabled) {
            Bukkit.getLogger().info("[VoltPur-PAdmin] Not started: modules.padmin.enabled=false.");
            return false;
        }
        if (VoltPurConfig.padminPassword.isEmpty()) {
            Bukkit.getLogger().warning("[VoltPur-PAdmin] Not started: modules.padmin.password is empty.");
            return false;
        }
        try {
            final String user = Objects.requireNonNullElse(VoltPurConfig.padminUser, "admin");
            final String pass = VoltPurConfig.padminPassword;
            final int port = VoltPurConfig.padminPort;

            HttpServer local = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
            local.createContext("/", exchange -> {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.getResponseHeaders().set("Allow", "GET");
                    exchange.sendResponseHeaders(405, -1);
                    exchange.close();
                    return;
                }
                if (!authorized(exchange, user, pass)) {
                    exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"voltpur-padmin\"");
                    exchange.sendResponseHeaders(401, -1);
                    exchange.close();
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                if (path.startsWith("/api/status")) handleStatus(exchange);
                else if (path.equals("/") || path.startsWith("/index")) handleMain(exchange);
                else {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                }
            });
            local.setExecutor(pool());
            local.start();
            server = local;
            running = true;
            VoltPurModules.setRuntime("PAdminWebUI", true);
            startSnapshotTask();
            Bukkit.getLogger().info("[VoltPur-PAdmin] WebUI on http://127.0.0.1:" + port + " (loopback, Basic Auth, read-only).");
            return true;
        } catch (Exception e) {
            running = false;
            server = null;
            Bukkit.getLogger().warning("[VoltPur-PAdmin] Failed to start: " + e.getMessage());
            return false;
        }
    }

    /** Stops the WebUI if it is running. Returns true when it actually stopped. */
    public static boolean stopWebServer() {
        if (!running || server == null) return false;
        try {
            server.stop(0);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[VoltPur-PAdmin] Stop failed: " + t.getMessage());
        }
        server = null;
        running = false;
        VoltPurModules.setRuntime("PAdminWebUI", false);
        stopSnapshotTask();
        return true;
    }

    private static ExecutorService pool() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "VoltPur-PAdmin-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newFixedThreadPool(MAX_WORKER_THREADS, factory);
    }

    private static boolean authorized(HttpExchange exchange, String user, String pass) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Basic ")) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) return false;
            String givenUser = decoded.substring(0, separator);
            String givenPass = decoded.substring(separator + 1);
            // Constant-time comparison for the secret; the user name is not a secret.
            return givenUser.equals(user) && MessageDigest.isEqual(
                    givenPass.getBytes(StandardCharsets.UTF_8), pass.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException badBase64) {
            return false;
        }
    }

    /** Builds the snapshot on the MAIN thread once per second (the only Bukkit access). */
    private static void startSnapshotTask() {
        if (refreshTaskId != -1) return;
        try {
            refreshTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(VoltPurPlugin.get(), () -> {
                VoltPurGuard.run("PAdminSnapshot", () -> {
                    List<String> modules = new ArrayList<>();
                    for (String name : VoltPurModules.all().keySet()) {
                        modules.add(VoltPurModules.line(name) + "  [" + VoltPurGuard.healthLine(name) + "]");
                    }
                    List<String> worlds = new ArrayList<>();
                    for (World world : Bukkit.getWorlds()) {
                        worlds.add(world.getName() + " (" + world.getEnvironment() + ")");
                    }
                    double[] tps = Bukkit.getServer().getTPS();
                    snapshot = new Snapshot(
                            VoltPur.VERSION, VoltPur.mcVersion(),
                            VoltPurModules.activeCount(), VoltPurModules.totalCount(),
                            List.copyOf(modules), List.copyOf(worlds),
                            Bukkit.getWorlds().size(), Bukkit.getOnlinePlayers().size(),
                            VoltPurHardware.getOsName(), VoltPurHardware.getOsArch(),
                            VoltPurHardware.getCores(), VoltPurHardware.getPhysicalRamMB(),
                            new double[]{tps[0], tps[1], tps[2]});
                });
            }, 20L, 20L);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[VoltPur-PAdmin] Snapshot task could not be scheduled: " + t.getMessage());
        }
    }

    private static void stopSnapshotTask() {
        if (refreshTaskId == -1) return;
        try {
            Bukkit.getScheduler().cancelTask(refreshTaskId);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[VoltPur-PAdmin] Snapshot task could not be cancelled: " + t.getMessage());
        }
        refreshTaskId = -1;
    }

    private static void handleMain(HttpExchange exchange) throws IOException {
        Snapshot snap = snapshot;
        StringBuilder modules = new StringBuilder();
        for (String line : snap.moduleLines()) {
            modules.append("<li>").append(escapeHtml(line)).append("</li>");
        }
        StringBuilder worlds = new StringBuilder();
        for (String line : snap.worldLines()) {
            worlds.append("<li>").append(escapeHtml(line)).append("</li>");
        }
        String html = "<!doctype html><html><head><meta charset='utf-8'><title>VoltPur PAdmin</title>"
                + "<style>body{font-family:system-ui,sans-serif;background:#0f0f1a;color:#e6e6e6;padding:24px}"
                + "h1{color:#9b59b6}code{background:#1b1b2b;padding:2px 6px;border-radius:4px}</style></head><body>"
                + "<h1>⚡ VoltPur PAdmin</h1>"
                + "<p>Version <code>" + escapeHtml(snap.version()) + "</code> · MC <code>" + escapeHtml(snap.mc()) + "</code></p>"
                + "<p>Modules: <b>" + snap.activeModules() + "</b> implemented / " + snap.totalModules() + " tracked</p>"
                + "<p>Worlds: " + snap.worlds() + " · Players: " + snap.players() + "</p>"
                + "<p>TPS: " + String.format("%.2f / %.2f / %.2f", snap.tps()[0], snap.tps()[1], snap.tps()[2]) + "</p>"
                + "<p>Host: " + escapeHtml(snap.os()) + " " + escapeHtml(snap.arch()) + " · cores " + snap.cores()
                + " · RAM " + (snap.ramMB() > 0 ? snap.ramMB() + " MB" : "unreported") + "</p>"
                + "<hr><h3>Loaded worlds</h3><ul>" + worlds + "</ul>"
                + "<hr><h3>Modules (implementation + live health)</h3><ul>" + modules + "</ul>"
                + "<hr><p>Read-only page. Health values come from VoltPurGuard: <code>runs</code>, <code>fails</code>, <code>last</code>.</p>"
                + "<p>API: <a href='/api/status'>/api/status</a> (JSON, same auth)</p>"
                + "</body></html>";
        send(exchange, html, "text/html; charset=utf-8");
    }

    private static void handleStatus(HttpExchange exchange) throws IOException {
        Snapshot snap = snapshot;
        StringBuilder modulesJson = new StringBuilder();
        for (String name : VoltPurModules.all().keySet()) {
            if (modulesJson.length() > 0) modulesJson.append(',');
            modulesJson.append("{\"name\":\"").append(jsonEscape(name))
                    .append("\",\"status\":\"").append(VoltPurModules.all().get(name))
                    .append("\",\"optIn\":").append(VoltPurModules.isOptIn(name))
                    .append(",\"health\":\"").append(jsonEscape(VoltPurGuard.healthLine(name))).append("\"}");
        }
        String json = "{"
                + "\"version\":\"" + VoltPur.VERSION + "\","
                + "\"mc\":\"" + VoltPur.mcVersion() + "\","
                + "\"activeModules\":" + snap.activeModules() + ","
                + "\"totalModules\":" + snap.totalModules() + ","
                + "\"worlds\":" + snap.worlds() + ","
                + "\"players\":" + snap.players() + ","
                + "\"tps\":[" + snap.tps()[0] + "," + snap.tps()[1] + "," + snap.tps()[2] + "],"
                + "\"os\":\"" + jsonEscape(snap.os()) + "\","
                + "\"arch\":\"" + jsonEscape(snap.arch()) + "\","
                + "\"cores\":" + snap.cores() + ","
                + "\"ramMB\":" + snap.ramMB() + ","
                + "\"modules\":[" + modulesJson + "]}";
        send(exchange, json, "application/json; charset=utf-8");
    }

    private static void send(HttpExchange exchange, String body, String contentType) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(200, payload.length);
        try (var out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private static String escapeHtml(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String jsonEscape(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
