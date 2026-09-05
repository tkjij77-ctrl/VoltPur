package org.purpurmc.purpur.command;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.purpurmc.purpur.VoltPur;
import org.purpurmc.purpur.VoltPurModules;
import org.purpurmc.purpur.VoltPurHardware;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Collections;

public class PAdminCommand extends Command {
    private static HttpServer server;
    private static boolean serverRunning = false;
    private static final int PORT = 25567;

    public PAdminCommand(String name) {
        super(name);
        this.description = "VoltPur PAdmin WebUI";
        this.usageMessage = "/padmin";
        this.setPermission("voltpur.admin.padmin");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        return Collections.emptyList();
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage("WebUI at http://localhost:" + PORT);
            startWebServer();
            return true;
        }
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (!player.hasPermission("voltpur.admin.padmin")) {
                player.sendMessage("No permission");
                return true;
            }
        }
        startWebServer();
        String ip = "localhost";
        try { ip = java.net.InetAddress.getLocalHost().getHostAddress(); } catch (Exception e) {}
        sender.sendMessage("§aVoltPur PAdmin WebUI started at http://"+ip+":" + PORT);
        sender.sendMessage("§7Live data: modules, hardware, worlds, players");
        return true;
    }

    public static void startWebServer() {
        if (serverRunning) return;
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/", PAdminCommand::handleMain);
            server.createContext("/api/status", PAdminCommand::handleStatus);
            server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
            server.start();
            serverRunning = true;
            Bukkit.getLogger().info("[VoltPur] PAdmin WebUI at http://localhost:" + PORT);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[VoltPur] PAdmin failed: " + e.getMessage());
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
            + "<p>plugin-pro/: priority Paper plugins — scanned before plugins/; deep bytecode changes require a Java agent</p>
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
