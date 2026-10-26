package org.purpurmc.purpur;

import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * VoltPur Discord Webhook - opt-in (default OFF).
 *
 * Sends server start/stop and player join/leave notifications to a Discord
 * webhook URL. Implemented with pure HTTP + a light sync poll (no Bukkit event
 * API needed), so it runs even on a server with 0 plugins - using the shared
 * scheduling owner from {@link VoltPurPlugin}. Configure in voltpur.yml under
 * modules.discord.*.
 */
public final class VoltPurDiscord {
    private static boolean initialized = false;
    private static final Set<String> known = new HashSet<>();

    private VoltPurDiscord() {}

    public static void init() {
        if (initialized) return;
        if (!VoltPurConfig.discordEnabled) return;
        if (VoltPurConfig.discordWebhookUrl == null || VoltPurConfig.discordWebhookUrl.trim().isEmpty()) {
            Bukkit.getLogger().info("[VoltPur-Discord] Enabled but webhook-url is empty - set modules.discord.webhook-url in voltpur.yml.");
            return;
        }
        initialized = true;
        Bukkit.getLogger().info("[VoltPur-Discord] Active - notifications will be sent to the configured webhook.");

        if (VoltPurConfig.discordAnnounceServer) {
            send("\u2705 **VoltPur** server started (" + VoltPur.VERSION + ", MC " + VoltPur.MC_VERSION + ").");
            try {
                Runtime.getRuntime().addShutdownHook(new Thread(
                        () -> send("\u26D4 **VoltPur** server stopping."), "VoltPur-Discord-Shutdown"));
            } catch (Throwable ignored) {}
        }

        if (VoltPurConfig.discordAnnouncePlayers) {
            // Poll the online-player set on the main thread (join/leave detection),
            // owned by the shared internal plugin so it runs without any plugin.
            try {
                org.bukkit.plugin.Plugin p = VoltPurPlugin.get();
                if (p != null) {
                    Bukkit.getScheduler().scheduleSyncRepeatingTask(p, VoltPurDiscord::pollPlayers, 100L, 40L); // ~every 2s
                }
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[VoltPur-Discord] Could not schedule player poll: " + t.getMessage());
            }
        }
    }

    private static void pollPlayers() {
        try {
            Set<String> current = new HashSet<>();
            for (org.bukkit.entity.Player pl : Bukkit.getOnlinePlayers()) current.add(pl.getName());
            for (String name : current) {
                if (!known.contains(name)) send("\u2795 **" + name + "** joined the server. (" + current.size() + " online)");
            }
            for (String name : known) {
                if (!current.contains(name)) send("\u2796 **" + name + "** left the server. (" + current.size() + " online)");
            }
            known.clear();
            known.addAll(current);
        } catch (Throwable ignored) {}
    }

    /** Fire-and-forget webhook POST on a worker thread. */
    public static void send(String message) {
        final String url = VoltPurConfig.discordWebhookUrl;
        if (url == null || url.trim().isEmpty()) return;
        new Thread(() -> {
            try {
                byte[] body = ("{\"content\":\"" + escape(message) + "\"}").getBytes(StandardCharsets.UTF_8);
                HttpURLConnection conn = (HttpURLConnection) URI.create(url.trim()).toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "VoltPur/1.0");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) { os.write(body); }
                int code = conn.getResponseCode(); // Discord returns 204 on success
                if (code >= 400) Bukkit.getLogger().warning("[VoltPur-Discord] Webhook returned HTTP " + code);
                conn.disconnect();
            } catch (Exception e) {
                Bukkit.getLogger().warning("[VoltPur-Discord] Webhook failed: " + e.getMessage());
            }
        }, "VoltPur-Discord").start();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
