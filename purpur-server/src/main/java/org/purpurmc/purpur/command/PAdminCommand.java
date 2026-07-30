
package org.purpurmc.purpur.command;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;
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
        if (sender instanceof Player player) {
            if (!player.hasPermission("voltpur.admin.padmin")) {
                player.sendMessage("No permission");
                return true;
            }
        }
        startWebServer();
        String ip = "localhost";
        try { ip = java.net.InetAddress.getLocalHost().getHostAddress(); } catch (Exception e) {}
        sender.sendMessage("§aVoltPur PAdmin WebUI started at http://" + ip + ":" + PORT);
        sender.sendMessage("§7Features: per-world plugin isolation, world management");
        return true;
    }

    public static void startWebServer() {
        if (serverRunning) return;
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/", PAdminCommand::handleMain);
            server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
            server.start();
            serverRunning = true;
            Bukkit.getLogger().info("[VoltPur] PAdmin WebUI at http://localhost:" + PORT);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[VoltPur] PAdmin failed: " + e.getMessage());
        }
    }

    private static void handleMain(HttpExchange exchange) throws IOException {
        String html = "<html><head><title>VoltPur PAdmin</title></head><body style='font-family:sans-serif;background:#0f0f1a;color:#e0e0e0;padding:20px'><h1 style='color:#9b59b6'>[VoltPur] VoltPur PAdmin</h1><p>Version: 26.2-VoltPur</p><p>Modules: 21</p><p>plugin-pro/ folder: ENABLED</p><p>Worlds: "+Bukkit.getWorlds().size()+"</p><p>Players: "+Bukkit.getOnlinePlayers().size()+"</p><hr><p>API: /api/status (coming soon)</p></body></html>";
        byte[] resp = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, resp.length);
        exchange.getResponseBody().write(resp);
        exchange.getResponseBody().close();
    }
}
