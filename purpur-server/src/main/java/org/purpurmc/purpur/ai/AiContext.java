package org.purpurmc.purpur.ai;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.purpurmc.purpur.VoltPurConfig;
import org.purpurmc.purpur.VoltPurModules;
import org.purpurmc.purpur.VoltPurPerformance;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.Collection;
import java.util.Map;

/**
 * VoltPur AI Context (§5.5, §5.7).
 *
 * Generates live system snapshots (MSPT, TPS, chunks, entities, hoppers, heap, modules)
 * to inject into the LLM system prompt. Enforces strict privacy filtering
 * (omits player usernames, IPs, and coordinates unless aiIncludePlayerData is true).
 */
public final class AiContext {

    private AiContext() {}

    /**
     * Builds a comprehensive system snapshot for AI system prompt injection.
     */
    public static String buildSystemSnapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append("You are VoltPur AI Assistant, an embedded performance and administration advisor for VoltPur Minecraft Server.\n");
        sb.append("Analyze the provided live server metrics carefully and provide concise, accurate, and actionable guidance.\n");
        sb.append("Current Server State Snapshot:\n");

        // TPS & MSPT
        try {
            double[] tps = Bukkit.getServer().getTPS();
            sb.append(String.format("- TPS (1m, 5m, 15m): %.2f, %.2f, %.2f\n", tps[0], tps[1], tps[2]));
        } catch (Throwable e) {
            sb.append("- TPS: unavailable\n");
        }

        try {
            long[] ticks = Bukkit.getServer().getTickTimes();
            double mspt = (ticks != null && ticks.length > 0) ? (ticks[0] / 1_000_000.0) : -1.0;
            sb.append(String.format("- MSPT (recent tick avg): %.2f ms (Target: <= 50.0 ms)\n", mspt));
        } catch (Throwable e) {
            sb.append("- MSPT: unavailable\n");
        }

        // Memory Heap
        try {
            Runtime rt = Runtime.getRuntime();
            long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
            long maxMb = rt.maxMemory() / (1024L * 1024L);
            sb.append(String.format("- Heap Memory: %d MB used / %d MB max\n", usedMb, maxMb));
        } catch (Throwable e) {
            sb.append("- Heap Memory: unavailable\n");
        }

        // Worlds, Entities, Chunks
        try {
            int totalEntities = 0;
            int totalChunks = 0;
            for (World w : Bukkit.getWorlds()) {
                int e = 0;
                int c = 0;
                try { e = w.getEntities().size(); } catch (Throwable ignored) {}
                try { c = w.getLoadedChunks().length; } catch (Throwable ignored) {}
                totalEntities += e;
                totalChunks += c;
                sb.append(String.format("  - World '%s': %d entities, %d chunks loaded\n", w.getName(), e, c));
            }
            sb.append(String.format("- Totals: %d entities, %d loaded chunks across %d worlds\n",
                    totalEntities, totalChunks, Bukkit.getWorlds().size()));
        } catch (Throwable e) {
            sb.append("- World counts: unavailable\n");
        }

        // Hoppers
        try {
            int[] hopper = VoltPurPerformance.hopperStats();
            sb.append(String.format("- Hoppers: %d total, %d sleepable (sleep-enabled: %b)\n",
                    hopper[0], hopper[1], VoltPurConfig.hopperSleepEnabled));
        } catch (Throwable e) {
            sb.append("- Hoppers: unavailable\n");
        }

        // Players & Privacy Filter (§5.5)
        try {
            if (Bukkit.getServer() == null) {
                if (VoltPurConfig.aiIncludePlayerData) {
                    sb.append("- Players: 0/0 online (identities included)\n");
                } else {
                    sb.append("- Players: 0/0 online (identities and coordinates redacted for privacy)\n");
                }
            } else {
                Collection<? extends Player> players = Bukkit.getOnlinePlayers();
                int count = players != null ? players.size() : 0;
                int max = Bukkit.getMaxPlayers();
                if (VoltPurConfig.aiIncludePlayerData && players != null) {
                    StringBuilder pList = new StringBuilder();
                    for (Player p : players) {
                        if (pList.length() > 0) pList.append(", ");
                        pList.append(p.getName()).append(" (")
                                .append(p.getWorld().getName()).append(":")
                                .append(p.getLocation().getBlockX()).append(",")
                                .append(p.getLocation().getBlockY()).append(",")
                                .append(p.getLocation().getBlockZ()).append(")");
                    }
                    sb.append(String.format("- Players (%d/%d): [%s]\n", count, max, pList));
                } else {
                    sb.append(String.format("- Players: %d/%d online (identities and coordinates redacted for privacy)\n", count, max));
                }
            }
        } catch (Throwable e) {
            sb.append("- Players: unavailable\n");
        }

        // Active Modules (§5.5)
        try {
            sb.append("- VoltPur Modules: ");
            int active = 0;
            for (Map.Entry<String, VoltPurModules.Status> entry : VoltPurModules.all().entrySet()) {
                if (entry.getValue() == VoltPurModules.Status.ACTIVE) {
                    if (active > 0) sb.append(", ");
                    sb.append(entry.getKey());
                    active++;
                }
            }
            sb.append(String.format(" (%d active)\n", active));
        } catch (Throwable e) {
            sb.append("- VoltPur Modules: unavailable\n");
        }

        return sb.toString();
    }

    /**
     * Builds the specialized prompt for `/ai doctor`.
     */
    public static String buildDoctorQuery() {
        return "Please perform a full performance diagnosis of this Minecraft server based on the snapshot above. " +
                "Evaluate MSPT, TPS, hopper density, entity pressure, and JVM heap. " +
                "List identified bottlenecks and recommend 3-5 concrete, safe configuration or operational actions.";
    }
}
