package org.purpurmc.purpur;

import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * VoltPur Discord Webhook - opt-in (default OFF) notifications.
 *
 * HARDENING CHANGES:
 *  - The webhook URL must be HTTPS and must point at Discord (discord.com or
 *    discordapp.com). Previously any URL was accepted and POSTed to, which turned
 *    a config key into an arbitrary outbound request primitive.
 *  - No empty catch blocks: failures go to VoltPurGuard, so a broken webhook is
 *    visible in /voltpur modules instead of disappearing silently.
 *  - Join/leave detection uses a light 2s main-thread poll (no plugin events
 *    needed) and is rate-limited so a webhook outage cannot spam the console.
 */
public final class VoltPurDiscord {

    private static final String MODULE = "DiscordWebhook";
    private static final Set<String> KNOWN_PLAYERS = new HashSet<>();
    private static final AtomicLong LAST_ERROR_LOG = new AtomicLong(0L);
    private static boolean initialized = false;

    private VoltPurDiscord() {}

    public static void init() {
        if (initialized) return;
        if (!VoltPurConfig.discordEnabled) {
            Bukkit.getLogger().info("[VoltPur-Discord] Disabled (opt-in). Enable modules.discord.enabled to use it.");
            return;
        }
        String url = VoltPurConfig.discordWebhookUrl;
        if (url == null || url.isBlank()) {
            Bukkit.getLogger().info("[VoltPur-Discord] Enabled but modules.discord.webhook-url is empty.");
            return;
        }
        if (!isAllowedWebhook(url)) {
            Bukkit.getLogger().warning("[VoltPur-Discord] Refusing to start: the webhook URL must be HTTPS on "
                    + "discord.com or discordapp.com.");
            return;
        }
        initialized = true;
        VoltPurModules.setRuntime(MODULE, true);
        Bukkit.getLogger().info("[VoltPur-Discord] Active - notifications go to the configured Discord webhook.");

        if (VoltPurConfig.discordAnnounceServer) {
            send("**VoltPur** server started (" + VoltPur.VERSION + ", MC " + VoltPur.MC_VERSION + ").");
            try {
                Runtime.getRuntime().addShutdownHook(
                        new Thread(() -> send("**VoltPur** server stopping."), "VoltPur-Discord-Shutdown"));
            } catch (Throwable t) {
                VoltPurGuard.failure(MODULE, t);
            }
        }

        if (VoltPurConfig.discordAnnouncePlayers) {
            try {
                Bukkit.getScheduler().scheduleSyncRepeatingTask(VoltPurPlugin.get(),
                        () -> VoltPurGuard.run(MODULE, VoltPurDiscord::pollPlayers), 100L, 40L);
            } catch (Throwable t) {
                VoltPurGuard.failure(MODULE, t);
            }
        }
    }

    private static void pollPlayers() {
        Set<String> current = new HashSet<>();
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) current.add(player.getName());
        for (String name : current) {
            if (!KNOWN_PLAYERS.contains(name)) {
                send("**" + name + "** joined the server. (" + current.size() + " online)");
            }
        }
        for (String name : KNOWN_PLAYERS) {
            if (!current.contains(name)) {
                send("**" + name + "** left the server. (" + current.size() + " online)");
            }
        }
        KNOWN_PLAYERS.clear();
        KNOWN_PLAYERS.addAll(current);
    }

    static boolean isAllowedWebhook(String url) {
        try {
            URI uri = URI.create(url.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            if (host == null) return false;
            String lower = host.toLowerCase(Locale.ROOT);
            return lower.equals("discord.com") || lower.endsWith(".discord.com")
                    || lower.equals("discordapp.com") || lower.endsWith(".discordapp.com");
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    /** Fire-and-forget POST on a daemon worker thread. */
    public static void send(String message) {
        final String url = VoltPurConfig.discordWebhookUrl;
        if (url == null || url.isBlank() || !isAllowedWebhook(url)) return;
        Thread worker = new Thread(() -> VoltPurGuard.run(MODULE, () -> post(url, message)), "VoltPur-Discord");
        worker.setDaemon(true);
        worker.start();
    }

    private static void post(String url, String message) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url.trim()).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "VoltPur/2.0");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setDoOutput(true);
            byte[] body = ("{\"content\":\"" + escapeJson(message) + "\"}").getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body);
            }
            int status = connection.getResponseCode();
            if (status >= 400) {
                throw new RuntimeException("webhook returned HTTP " + status);
            }
        } catch (Exception e) {
            long now = System.currentTimeMillis();
            long last = LAST_ERROR_LOG.get();
            if (now - last > 60_000L && LAST_ERROR_LOG.compareAndSet(last, now)) {
                Bukkit.getLogger().warning("[VoltPur-Discord] " + e.getMessage());
            }
            throw new RuntimeException(e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String escapeJson(String raw) {
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
